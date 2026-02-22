package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.M_Flash
import jake2.qcommon.network.messages.server.BeamOffsetTEMessage
import jake2.qcommon.network.messages.server.BeamTEMessage
import jake2.qcommon.network.messages.server.MuzzleFlash2Message
import jake2.qcommon.network.messages.server.PointDirectionTEMessage
import jake2.qcommon.network.messages.server.PointTEMessage
import jake2.qcommon.network.messages.server.SplashTEMessage
import jake2.qcommon.network.messages.server.TEMessage
import jake2.qcommon.network.messages.server.TrailTEMessage
import jake2.qcommon.util.Math3D
import org.demoth.cake.audio.SpatialSoundAttenuation
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.assets.Sp2Renderer
import org.demoth.cake.createModelInstance
import org.demoth.cake.stages.ingame.ClientEntityManager
import org.demoth.cake.stages.ingame.DynamicLightSystem

/**
 * Runtime owner for non-replicated client-side effects produced by server effect messages.
 *
 * Scope:
 * - `TEMessage` hierarchy handling.
 * - `MuzzleFlash2Message` handling.
 * - Effect-local asset ownership via [EffectAssetCatalog].
 * - Transient MD2/`.sp2` effect instances, particle bursts, and effect-driven dynamic lights.
 *
 * Non-goals:
 * - Replicated entity state reconstruction (owned by `ClientEntityManager`).
 * - Generic world entity drawing (owned by `Game3dScreen`).
 *
 * Lifecycle:
 * - Construct once per `Game3dScreen`.
 * - Call [precache] after config/map precache.
 * - Call [update]/[render] every frame on render thread.
 * - Call [dispose] when screen is disposed.
 *
 * Constructor contract:
 * - [listenerPositionProvider] is used for positional sound attenuation.
 * - [cameraProvider] is required for billboard sprite effects (`.sp2`).
 *
 * Legacy counterparts:
 * - `client/CL_fx.ParseMuzzleFlash2`
 * - `client/CL_tent.ParseTEnt`
 */
class ClientEffectsSystem(
    assetManager: AssetManager,
    private val entityManager: ClientEntityManager,
    private val listenerPositionProvider: () -> Vector3,
    private val cameraProvider: () -> Camera,
    private val dynamicLightSystem: DynamicLightSystem? = null,
) : Disposable {
    private val assetCatalog = EffectAssetCatalog(assetManager)
    private val spriteRenderer = Sp2Renderer()
    private val particleSystem = EffectParticleSystem()
    // Invariant: only effects owned by this system are stored here and disposed by this system.
    private val activeEffects = mutableListOf<ClientTransientEffect>()

    /**
     * Loads effect-only assets not guaranteed to come from configstrings.
     *
     * Call once during gameplay precache after base assets are available.
     */
    fun precache() {
        assetCatalog.precache()
    }

    /**
     * Handles monster muzzle flashes (`svc_muzzleflash2`) using replicated entity pose and hardcoded
     * [M_Flash.monster_flash_offset] indices.
     */
    fun processMuzzleFlash2Message(msg: MuzzleFlash2Message) {
        if (msg.entityIndex !in 1 until Defines.MAX_EDICTS) {
            Com.Warn("Ignoring MuzzleFlash2Message with invalid entity index ${msg.entityIndex}")
            return
        }
        if (msg.flashType <= 0 || msg.flashType >= M_Flash.monster_flash_offset.size) {
            Com.Warn("Ignoring MuzzleFlash2Message with invalid flash type ${msg.flashType}")
            return
        }

        val muzzleOrigin = computeMuzzleOrigin(msg.entityIndex, msg.flashType) ?: return
        val profile = MuzzleFlash2Profiles.resolve(msg.flashType) ?: return

        if (profile.spawnSmokeAndFlash) {
            spawnSmokeAndFlash(muzzleOrigin)
        }
        val muzzleLight = profile.dynamicLight
        spawnDynamicLight(
            origin = muzzleOrigin,
            radius = muzzleLight.radius + Globals.rnd.nextInt(32),
            red = muzzleLight.red,
            green = muzzleLight.green,
            blue = muzzleLight.blue,
            key = msg.entityIndex,
            lifetimeMs = muzzleLight.lifetimeMs,
        )

        playRandomSound(profile.soundPaths, muzzleOrigin, profile.attenuation)
    }

    /**
     * Handles temp-entity messages (`svc_temp_entity`) already decoded into typed message classes.
     *
     * Unknown styles inside known message types are currently ignored silently by style branch fall-through.
     */
    fun processTempEntityMessage(msg: TEMessage) {
        when (msg) {
            is BeamOffsetTEMessage -> handleBeamOffsetMessage(msg)
            is BeamTEMessage -> handleBeamMessage(msg)
            is PointDirectionTEMessage -> handlePointDirectionMessage(msg)
            is TrailTEMessage -> handleTrailMessage(msg)
            is SplashTEMessage -> handleSplashMessage(msg)
            is PointTEMessage -> handlePointMessage(msg)
            else -> Com.Warn("Unhandled temp entity message class: ${msg.javaClass.simpleName}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(deltaSeconds: Float, _serverFrame: Int) {
        val now = Globals.curtime
        particleSystem.update(deltaSeconds, now)
        val iterator = activeEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            if (!effect.update(now, deltaSeconds)) {
                effect.dispose()
                iterator.remove()
            }
        }
    }

    /**
     * Renders all active transient effects.
     *
     * Ordering invariant: caller should invoke this after world entity rendering when overlays/trails
     * are expected to appear on top.
     */
    fun render(modelBatch: ModelBatch) {
        particleSystem.render(modelBatch)
        activeEffects.forEach { effect ->
            effect.render(modelBatch)
        }
    }

    override fun dispose() {
        activeEffects.forEach { it.dispose() }
        activeEffects.clear()
        particleSystem.dispose()
        spriteRenderer.dispose()
        assetCatalog.dispose()
    }

    private fun handlePointDirectionMessage(msg: PointDirectionTEMessage) {
        val position = toVector3(msg.position) ?: return
        when (msg.style) {
            Defines.TE_GUNSHOT,
            Defines.TE_BULLET_SPARKS -> {
                emitImpactParticles(
                    origin = position,
                    direction = msg.direction,
                    count = if (msg.style == Defines.TE_GUNSHOT) 40 else 8,
                    color = Color(0.95f, 0.9f, 0.65f, 1f),
                )
                spawnSmokeAndFlash(position)
                playRandomSound(RICHOCHET_SOUNDS, position)
            }

            Defines.TE_SCREEN_SPARKS,
            Defines.TE_SHIELD_SPARKS,
            Defines.TE_HEATBEAM_SPARKS,
            Defines.TE_HEATBEAM_STEAM,
            Defines.TE_ELECTRIC_SPARKS -> {
                emitImpactParticles(
                    origin = position,
                    direction = msg.direction,
                    count = 40,
                    color = Color(0.85f, 0.9f, 1f, 1f),
                )
                playEffectSound("sound/weapons/lashit.wav", position)
            }

            Defines.TE_SHOTGUN -> {
                emitImpactParticles(
                    origin = position,
                    direction = msg.direction,
                    count = 20,
                    color = Color(0.92f, 0.9f, 0.75f, 1f),
                )
                spawnSmokeAndFlash(position)
            }

            Defines.TE_BLASTER -> {
                spawnBlasterImpact(position, msg.direction, skinIndex = 0)
                spawnDynamicLight(position, 150f, 1f, 1f, 0f)
                playEffectSound("sound/weapons/lashit.wav", position)
            }

            Defines.TE_BLASTER2 -> {
                spawnBlasterImpact(position, msg.direction, skinIndex = 1)
                spawnDynamicLight(position, 150f, 0f, 1f, 0f)
                playEffectSound("sound/weapons/lashit.wav", position)
            }

            Defines.TE_FLECHETTE -> {
                spawnBlasterImpact(position, msg.direction, skinIndex = 2)
                spawnDynamicLight(position, 150f, 0.19f, 0.41f, 0.75f)
                playEffectSound("sound/weapons/lashit.wav", position)
            }
        }
    }

    private fun handleTrailMessage(msg: TrailTEMessage) {
        val start = toVector3(msg.position) ?: return
        val end = toVector3(msg.destination) ?: return
        when (msg.style) {
            Defines.TE_RAILTRAIL -> {
                activeEffects += LineBeamEffect(
                    start = start,
                    end = end,
                    color = Color(0.08f, 0.18f, 1f, 1f),
                    spawnTimeMs = Globals.curtime,
                    durationMs = 140,
                    radius = 0.9f,
                    alpha = 0.85f,
                )
                playEffectSound("sound/weapons/railgf1a.wav", end)
            }

            Defines.TE_BFG_LASER -> {
                activeEffects += LineBeamEffect(
                    start = start,
                    end = end,
                    color = Color(0.2f, 1f, 0.25f, 1f),
                    spawnTimeMs = Globals.curtime,
                    durationMs = 110,
                    radius = 0.8f,
                    alpha = 0.35f,
                )
            }
        }
    }

    private fun handleSplashMessage(msg: SplashTEMessage) {
        val position = toVector3(msg.position) ?: return
        when (msg.style) {
            Defines.TE_SPLASH -> {
                val splashColor = if (msg.param == Defines.SPLASH_SPARKS) {
                    Color(1f, 0.85f, 0.4f, 1f)
                } else {
                    Color(0.45f, 0.6f, 1f, 1f)
                }
                emitImpactParticles(
                    origin = position,
                    direction = msg.direction,
                    count = msg.count.coerceIn(4, 64),
                    color = splashColor,
                )
                if (msg.param == Defines.SPLASH_SPARKS) {
                    playRandomSound(SPARK_SOUNDS, position, Defines.ATTN_STATIC.toFloat())
                }
            }

            Defines.TE_WELDING_SPARKS -> {
                spawnAnimatedModelEffect(
                    modelPath = "models/objects/flash/tris.md2",
                    position = position,
                    firstFrame = 0,
                    frameCount = 2,
                    frameDurationMs = 80,
                )
                spawnDynamicLight(position, (100 + Globals.rnd.nextInt(75)).toFloat(), 1f, 1f, 0.3f)
            }
        }
    }

    private fun handlePointMessage(msg: PointTEMessage) {
        val position = toVector3(msg.position) ?: return
        when (msg.style) {
            Defines.TE_EXPLOSION2,
            Defines.TE_GRENADE_EXPLOSION,
            Defines.TE_GRENADE_EXPLOSION_WATER -> {
                emitExplosionParticles(position, count = 128, color = Color(1f, 0.55f, 0.25f, 1f))
                spawnDynamicLight(position, 350f, 1f, 0.5f, 0.5f)
                spawnExplosionModel(
                    modelPath = "models/objects/r_explode/tris.md2",
                    position = position,
                    firstFrame = 30,
                    frameCount = 19,
                )
                if (msg.style == Defines.TE_GRENADE_EXPLOSION_WATER) {
                    playEffectSound("sound/weapons/xpld_wat.wav", position)
                } else {
                    playEffectSound("sound/weapons/grenlx1a.wav", position)
                }
            }

            Defines.TE_PLASMA_EXPLOSION -> {
                emitExplosionParticles(position, count = 96, color = Color(1f, 0.45f, 0.35f, 1f))
                spawnDynamicLight(position, 350f, 1f, 0.5f, 0.5f)
                spawnExplosionModel(
                    modelPath = "models/objects/r_explode/tris.md2",
                    position = position,
                    firstFrame = if (Globals.rnd.nextFloat() < 0.5f) 15 else 0,
                    frameCount = 15,
                )
                playEffectSound("sound/weapons/rocklx1a.wav", position)
            }

            Defines.TE_EXPLOSION1,
            Defines.TE_EXPLOSION1_BIG,
            Defines.TE_ROCKET_EXPLOSION,
            Defines.TE_ROCKET_EXPLOSION_WATER,
            Defines.TE_EXPLOSION1_NP -> {
                emitExplosionParticles(
                    position,
                    count = if (msg.style == Defines.TE_EXPLOSION1_BIG) 196 else 112,
                    color = Color(1f, 0.52f, 0.22f, 1f),
                )
                spawnDynamicLight(position, 350f, 1f, 0.5f, 0.5f)
                spawnExplosionModel(
                    modelPath = if (msg.style == Defines.TE_EXPLOSION1_BIG) {
                        "models/objects/r_explode2/tris.md2"
                    } else {
                        "models/objects/r_explode/tris.md2"
                    },
                    position = position,
                    firstFrame = if (Globals.rnd.nextFloat() < 0.5f) 15 else 0,
                    frameCount = 15,
                )
                if (msg.style == Defines.TE_ROCKET_EXPLOSION_WATER) {
                    playEffectSound("sound/weapons/xpld_wat.wav", position)
                } else {
                    playEffectSound("sound/weapons/rocklx1a.wav", position)
                }
            }

            Defines.TE_BFG_EXPLOSION -> {
                spawnDynamicLight(position, 350f, 0f, 1f, 0f)
                spawnAnimatedSpriteEffect(
                    spritePath = "sprites/s_bfg2.sp2",
                    position = position,
                    firstFrame = 1,
                    frameCount = 4,
                    frameDurationMs = 100,
                    renderFx = Defines.RF_TRANSLUCENT,
                    alpha = 0.30f,
                )
            }

            Defines.TE_BOSSTPORT -> {
                playEffectSound(
                    soundPath = "sound/misc/bigtele.wav",
                    origin = position,
                    attenuation = Defines.ATTN_NONE.toFloat(),
                )
            }

            Defines.TE_PLAIN_EXPLOSION -> {
                emitExplosionParticles(position, count = 96, color = Color(1f, 0.52f, 0.22f, 1f))
                spawnDynamicLight(position, 350f, 1f, 0.5f, 0.5f)
                spawnExplosionModel(
                    modelPath = "models/objects/r_explode/tris.md2",
                    position = position,
                    firstFrame = if (Globals.rnd.nextFloat() < 0.5f) 15 else 0,
                    frameCount = 15,
                )
                playEffectSound("sound/weapons/rocklx1a.wav", position)
            }

            Defines.TE_CHAINFIST_SMOKE -> {
                emitImpactParticles(
                    origin = position,
                    direction = floatArrayOf(0f, 0f, 1f),
                    count = 28,
                    color = Color(0.65f, 0.65f, 0.65f, 1f),
                )
                spawnAnimatedModelEffect(
                    modelPath = "models/objects/smoke/tris.md2",
                    position = position,
                    firstFrame = 0,
                    frameCount = 4,
                    frameDurationMs = 100,
                )
            }

            Defines.TE_TRACKER_EXPLOSION -> {
                spawnDynamicLight(position, 150f, -1f, -1f, -1f, lifetimeMs = 100)
                playEffectSound("sound/weapons/disrupthit.wav", position)
            }
        }
    }

    private fun handleBeamMessage(msg: BeamTEMessage) {
        if (msg.style == Defines.TE_PARASITE_ATTACK || msg.style == Defines.TE_MEDIC_CABLE_ATTACK) {
            spawnSegmentBeam(
                modelPath = "models/monsters/parasite/segment/tris.md2",
                start = msg.origin,
                end = msg.destination,
                offset = null,
                durationMs = 200,
            )
        }
    }

    private fun handleBeamOffsetMessage(msg: BeamOffsetTEMessage) {
        if (msg.style == Defines.TE_GRAPPLE_CABLE) {
            spawnSegmentBeam(
                modelPath = "models/ctf/segment/tris.md2",
                start = msg.origin,
                end = msg.destination,
                offset = msg.offset,
                durationMs = 200,
            )
        }
    }

    private fun computeMuzzleOrigin(entityIndex: Int, flashType: Int): Vector3? {
        val origin = entityManager.getEntitySoundOrigin(entityIndex) ?: return null
        val angles = entityManager.getEntityAngles(entityIndex) ?: return null
        val flashOffset = M_Flash.monster_flash_offset[flashType]

        val forward = FloatArray(3)
        val right = FloatArray(3)
        Math3D.AngleVectors(angles, forward, right, null)

        return Vector3(
            origin.x + forward[0] * flashOffset[0] + right[0] * flashOffset[1],
            origin.y + forward[1] * flashOffset[0] + right[1] * flashOffset[1],
            origin.z + forward[2] * flashOffset[0] + right[2] * flashOffset[1] + flashOffset[2],
        )
    }

    private fun spawnSmokeAndFlash(origin: Vector3) {
        // Legacy counterpart: `client/CL_tent.SmokeAndFlash`.
        // Smoke uses translucent fade-out (old `ex_misc` path), flash remains short opaque/fullbright.
        spawnAnimatedModelEffect(
            modelPath = "models/objects/smoke/tris.md2",
            position = origin,
            firstFrame = 0,
            frameCount = 4,
            frameDurationMs = 100,
            startAlpha = 1f,
            endAlpha = 0f,
            translucent = true,
        )
        spawnAnimatedModelEffect(
            modelPath = "models/objects/flash/tris.md2",
            position = origin,
            firstFrame = 0,
            frameCount = 2,
            frameDurationMs = 80,
        )
    }

    private fun spawnBlasterImpact(position: Vector3, direction: FloatArray?, skinIndex: Int) {
        val (pitch, yaw) = computeDirectionAngles(direction) ?: return
        spawnAnimatedModelEffect(
            modelPath = "models/objects/explode/tris.md2",
            position = position,
            firstFrame = 0,
            frameCount = 4,
            frameDurationMs = 100,
            pitchDeg = pitch,
            yawDeg = yaw,
            rollDeg = 0f,
            skinIndex = skinIndex,
        )
    }

    private fun spawnExplosionModel(
        modelPath: String,
        position: Vector3,
        firstFrame: Int,
        frameCount: Int,
    ) {
        spawnAnimatedModelEffect(
            modelPath = modelPath,
            position = position,
            firstFrame = firstFrame,
            frameCount = frameCount,
            frameDurationMs = 100,
            yawDeg = Globals.rnd.nextInt(360).toFloat(),
        )
    }

    private fun spawnSegmentBeam(
        modelPath: String,
        start: FloatArray?,
        end: FloatArray?,
        offset: FloatArray?,
        durationMs: Int,
    ) {
        val startVector = toVector3(start) ?: return
        val endVector = toVector3(end) ?: return
        val offsetVector = toVector3(offset) ?: Vector3.Zero

        val beamStart = Vector3(startVector).add(offsetVector)
        val beamDirection = Vector3(endVector).sub(beamStart)
        val beamLength = beamDirection.len()
        if (beamLength <= 0.001f) {
            return
        }

        beamDirection.scl(1f / beamLength)
        val (pitch, yaw) = computeDirectionAngles(beamDirection) ?: return

        val segmentStep = 30f
        var travelled = 0f
        while (travelled <= beamLength) {
            val segmentPosition = Vector3(beamStart).mulAdd(beamDirection, travelled)
            spawnAnimatedModelEffect(
                modelPath = modelPath,
                position = segmentPosition,
                firstFrame = 0,
                frameCount = 1,
                frameDurationMs = durationMs,
                pitchDeg = pitch,
                yawDeg = yaw,
                rollDeg = Globals.rnd.nextInt(360).toFloat(),
            )
            travelled += segmentStep
        }
    }

    private fun spawnAnimatedModelEffect(
        modelPath: String,
        position: Vector3,
        firstFrame: Int,
        frameCount: Int,
        frameDurationMs: Int,
        pitchDeg: Float = 0f,
        yawDeg: Float = 0f,
        rollDeg: Float = 0f,
        skinIndex: Int? = null,
        startAlpha: Float = 1f,
        endAlpha: Float = 1f,
        translucent: Boolean = false,
    ) {
        // Legacy counterpart: `client/CL_tent` explosion/muzzle model entities.
        val md2 = assetCatalog.getModel(modelPath) ?: return
        val instance = createModelInstance(md2.model)
        if (skinIndex != null) {
            (instance.userData as? Md2CustomData)?.skinIndex = skinIndex
        }
        activeEffects += AnimatedModelEffect(
            modelInstance = instance,
            spawnTimeMs = Globals.curtime,
            frameDurationMs = frameDurationMs,
            firstFrame = firstFrame,
            frameCount = frameCount,
            position = Vector3(position),
            pitchDeg = pitchDeg,
            yawDeg = yawDeg,
            rollDeg = rollDeg,
            startAlpha = startAlpha,
            endAlpha = endAlpha,
            translucent = translucent,
        )
    }

    private fun spawnAnimatedSpriteEffect(
        spritePath: String,
        position: Vector3,
        firstFrame: Int,
        frameCount: Int,
        frameDurationMs: Int,
        renderFx: Int = 0,
        alpha: Float = 1f,
    ) {
        val sprite = assetCatalog.getSprite(spritePath) ?: return
        activeEffects += AnimatedSpriteEffect(
            spriteRenderer = spriteRenderer,
            cameraProvider = cameraProvider,
            sprite = sprite,
            spawnTimeMs = Globals.curtime,
            frameDurationMs = frameDurationMs,
            firstFrame = firstFrame,
            frameCount = frameCount,
            position = Vector3(position),
            renderFx = renderFx,
            alpha = alpha,
        )
    }

    private fun playRandomSound(
        paths: List<String>,
        origin: Vector3,
        attenuation: Float = Defines.ATTN_NORM.toFloat(),
    ) {
        pickRandom(paths)?.let { soundPath ->
            playEffectSound(soundPath, origin, attenuation = attenuation)
        }
    }

    private fun playEffectSound(
        soundPath: String,
        origin: Vector3,
        volume: Float = 1f,
        attenuation: Float = Defines.ATTN_NORM.toFloat(),
    ) {
        val sound = assetCatalog.getSound(soundPath) ?: return
        val listener = listenerPositionProvider()
        val gain = (volume * SpatialSoundAttenuation.calculate(origin, listener, attenuation)).coerceIn(0f, 1f)
        if (gain > 0f) {
            sound.play(gain)
        }
    }

    private fun spawnDynamicLight(
        origin: Vector3,
        radius: Float,
        red: Float,
        green: Float,
        blue: Float,
        key: Int = 0,
        lifetimeMs: Int = 0,
        decayPerSecond: Float = 0f,
    ) {
        dynamicLightSystem?.spawnTransientLight(
            key = key,
            origin = origin,
            radius = radius,
            red = red,
            green = green,
            blue = blue,
            lifetimeMs = lifetimeMs,
            decayPerSecond = decayPerSecond,
            currentTimeMs = Globals.curtime,
        )
    }

    private fun emitImpactParticles(
        origin: Vector3,
        direction: FloatArray?,
        count: Int,
        color: Color,
    ) {
        particleSystem.emitBurst(
            origin = origin,
            direction = direction,
            count = count,
            color = color,
            speedMin = 35f,
            speedMax = 170f,
            spread = 0.85f,
            gravity = -320f,
            startAlpha = 0.95f,
            endAlpha = 0f,
            sizeMin = 0.4f,
            sizeMax = 1.3f,
            lifetimeMinMs = 160,
            lifetimeMaxMs = 520,
        )
    }

    private fun emitExplosionParticles(
        origin: Vector3,
        count: Int,
        color: Color,
    ) {
        particleSystem.emitBurst(
            origin = origin,
            direction = floatArrayOf(0f, 0f, 1f),
            count = count,
            color = color,
            speedMin = 60f,
            speedMax = 320f,
            spread = 1.2f,
            gravity = -210f,
            startAlpha = 1f,
            endAlpha = 0f,
            sizeMin = 0.6f,
            sizeMax = 2f,
            lifetimeMinMs = 240,
            lifetimeMaxMs = 900,
        )
    }

    private fun toVector3(value: FloatArray?): Vector3? {
        if (value == null || value.size < 3) {
            return null
        }
        return Vector3(value[0], value[1], value[2])
    }

    private fun computeDirectionAngles(direction: FloatArray?): Pair<Float, Float>? {
        if (direction == null || direction.size < 3) {
            return null
        }
        val angles = FloatArray(3)
        Math3D.vectoangles(direction, angles)
        return angles[Defines.PITCH] to angles[Defines.YAW]
    }

    private fun computeDirectionAngles(direction: Vector3): Pair<Float, Float>? {
        return computeDirectionAngles(floatArrayOf(direction.x, direction.y, direction.z))
    }

    private fun pickRandom(paths: List<String>): String? {
        if (paths.isEmpty()) {
            return null
        }
        return paths[Globals.rnd.nextInt(paths.size)]
    }
}

private val RICHOCHET_SOUNDS = listOf(
    "sound/world/ric1.wav",
    "sound/world/ric2.wav",
    "sound/world/ric3.wav",
)

private val SPARK_SOUNDS = listOf(
    "sound/world/spark5.wav",
    "sound/world/spark6.wav",
    "sound/world/spark7.wav",
)
