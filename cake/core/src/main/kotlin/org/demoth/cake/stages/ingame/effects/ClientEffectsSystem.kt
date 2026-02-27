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
import org.demoth.cake.ClientEntity
import org.demoth.cake.audio.SpatialSoundAttenuation
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.assets.Sp2Renderer
import org.demoth.cake.createModelInstance
import org.demoth.cake.stages.ingame.ClientEntityManager
import org.demoth.cake.stages.ingame.DynamicLightSystem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
    private val assetManager: AssetManager,
    private val entityManager: ClientEntityManager,
    private val listenerPositionProvider: () -> Vector3,
    private val cameraProvider: () -> Camera,
    private val dynamicLightSystem: DynamicLightSystem? = null,
) : Disposable {
    private val assetCatalog = EffectAssetCatalog(assetManager)
    private val spriteRenderer = Sp2Renderer()
    private val particleSystem = EffectParticleSystem()
    private val q2Palette: IntArray? by lazy {
        if (assetManager.isLoaded("q2palette.bin", Any::class.java)) {
            assetManager.get("q2palette.bin", Any::class.java) as? IntArray
        } else {
            null
        }
    }
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
        activeEffects.forEach { effect ->
            effect.render(modelBatch)
        }
    }

    /**
     * Renders the transient particle pass using the dedicated particle renderer.
     *
     * This pass is separate from ModelBatch to keep particle draw-call count bounded.
     */
    fun renderParticles(camera: Camera) {
        particleSystem.render(camera)
    }

    override fun dispose() {
        activeEffects.forEach { it.dispose() }
        activeEffects.clear()
        particleSystem.dispose()
        spriteRenderer.dispose()
        assetCatalog.dispose()
    }

    /**
     * Emits replicated projectile trails driven by entity `EF_*` flags.
     *
     * Counterpart: `client/CL_ents.AddPacketEntities` trail branches.
     */
    fun emitReplicatedEntityTrail(entity: ClientEntity, effects: Int, endX: Float, endY: Float, endZ: Float) {
        val startX = entity.lerp_origin[0]
        val startY = entity.lerp_origin[1]
        val startZ = entity.lerp_origin[2]

        when {
            (effects and Defines.EF_ROCKET) != 0 -> {
                emitRocketTrail(startX, startY, startZ, endX, endY, endZ, entity)
            }
            (effects and Defines.EF_BLASTER) != 0 -> {
                if ((effects and Defines.EF_TRACKER) != 0) {
                    emitBlasterTrail(
                        startX = startX,
                        startY = startY,
                        startZ = startZ,
                        endX = endX,
                        endY = endY,
                        endZ = endZ,
                        paletteIndex = BLASTER2_TRAIL_COLOR_INDEX,
                        fallback = BLASTER2_TRAIL_COLOR_FALLBACK,
                    )
                } else {
                    emitBlasterTrail(
                        startX = startX,
                        startY = startY,
                        startZ = startZ,
                        endX = endX,
                        endY = endY,
                        endZ = endZ,
                        paletteIndex = BLASTER_TRAIL_COLOR_INDEX,
                        fallback = BLASTER_TRAIL_COLOR_FALLBACK,
                    )
                }
            }
            (effects and Defines.EF_GRENADE) != 0 -> {
                emitDiminishingTrail(startX, startY, startZ, endX, endY, endZ, entity, effects)
            }
        }
    }

    private fun handlePointDirectionMessage(msg: PointDirectionTEMessage) {
        val position = toVector3(msg.position) ?: return
        when (msg.style) {
            Defines.TE_BLOOD,
            Defines.TE_MOREBLOOD,
            Defines.TE_GREENBLOOD -> {
                emitImpactParticles(
                    origin = position,
                    direction = msg.direction,
                    count = when (msg.style) {
                        Defines.TE_MOREBLOOD -> 250
                        Defines.TE_GREENBLOOD -> 30
                        else -> 60
                    },
                    color = when (msg.style) {
                        Defines.TE_GREENBLOOD -> Color(0.55f, 0.95f, 0.45f, 1f)
                        else -> Color(0.84f, 0.12f, 0.12f, 1f)
                    },
                )
            }

            Defines.TE_GUNSHOT,
            Defines.TE_SPARKS,
            Defines.TE_BULLET_SPARKS -> {
                emitImpactParticles(
                    origin = position,
                    direction = msg.direction,
                    count = when (msg.style) {
                        Defines.TE_GUNSHOT -> 40
                        Defines.TE_SPARKS -> 6
                        else -> 8
                    },
                    color = if (msg.style == Defines.TE_GUNSHOT) {
                        Color(0.95f, 0.9f, 0.65f, 1f)
                    } else {
                        Color(1f, 0.88f, 0.45f, 1f)
                    },
                )
                if (msg.style != Defines.TE_SPARKS) {
                    spawnSmokeAndFlash(position)
                    playRandomSound(RICHOCHET_SOUNDS, position)
                }
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
            Defines.TE_BUBBLETRAIL -> {
                emitSegmentTrailParticles(
                    start = start,
                    end = end,
                    spacing = 32f,
                    maxSteps = 128,
                    direction = floatArrayOf(0f, 0f, 1f),
                    countPerStep = 1,
                    colorProvider = {
                        val colorIndex = 4 + (Globals.rnd.nextInt(8))
                        resolvePaletteColor(colorIndex, fallback = Color(0.55f, 0.7f, 1f, 1f))
                    },
                    speedMin = 0f,
                    speedMax = 8f,
                    spread = 0.35f,
                    gravity = 45f,
                    sizeMin = 0.28f,
                    sizeMax = 0.75f,
                    lifetimeMinMs = 700,
                    lifetimeMaxMs = 1400,
                )
            }

            Defines.TE_BLUEHYPERBLASTER -> {
                emitImpactParticles(
                    origin = start,
                    direction = msg.destination,
                    count = 40,
                    color = Color(0.25f, 0.55f, 1f, 1f),
                )
            }

            Defines.TE_DEBUGTRAIL -> {
                emitSegmentTrailParticles(
                    start = start,
                    end = end,
                    spacing = 3f,
                    maxSteps = 256,
                    direction = floatArrayOf(0f, 0f, 0f),
                    countPerStep = 1,
                    colorProvider = {
                        val colorIndex = 0x74 + Globals.rnd.nextInt(8)
                        resolvePaletteColor(colorIndex, fallback = Color(0.15f, 0.95f, 0.45f, 1f))
                    },
                    speedMin = 0f,
                    speedMax = 2f,
                    spread = 0.2f,
                    gravity = 0f,
                    sizeMin = 0.22f,
                    sizeMax = 0.55f,
                    lifetimeMinMs = 3500,
                    lifetimeMaxMs = 5200,
                )
            }

            Defines.TE_RAILTRAIL -> {
                emitRailTrailParticles(start, end)
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

            Defines.TE_LASER_SPARKS -> {
                emitLegacyPaletteParticles(
                    origin = position,
                    direction = msg.direction,
                    count = msg.count,
                    paletteIndex = msg.param,
                    gravity = -320f,
                )
            }

            Defines.TE_WELDING_SPARKS -> {
                emitLegacyPaletteParticles(
                    origin = position,
                    direction = msg.direction,
                    count = msg.count,
                    paletteIndex = msg.param,
                    gravity = -320f,
                )
                spawnLegacyExplosionEffect(
                    type = ExplosionType.FLASH,
                    modelPath = "models/objects/flash/tris.md2",
                    position = position,
                    firstFrame = 0,
                    frameCount = 2,
                )
                spawnDynamicLight(position, (100 + Globals.rnd.nextInt(75)).toFloat(), 1f, 1f, 0.3f)
            }

            Defines.TE_TUNNEL_SPARKS -> {
                emitLegacyPaletteParticles(
                    origin = position,
                    direction = msg.direction,
                    count = msg.count,
                    paletteIndex = msg.param,
                    gravity = 320f,
                )
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
                spawnExplosionSpriteEffect(
                    type = ExplosionType.POLY,
                    spritePath = "sprites/s_bfg2.sp2",
                    position = position,
                    firstFrame = 1,
                    frameCount = 4,
                    startsTranslucent = true,
                )
            }

            Defines.TE_BFG_BIGEXPLOSION -> {
                emitBfgBigExplosionParticles(position)
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

            Defines.TE_TELEPORT_EFFECT,
            Defines.TE_DBALL_GOAL -> {
                emitTeleportEffectParticles(position)
            }

            Defines.TE_WIDOWSPLASH -> {
                emitWidowSplashParticles(position)
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
        val origin = entityManager.getEntityOrigin(entityIndex) ?: return null
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
        spawnLegacyExplosionEffect(
            type = ExplosionType.MISC,
            modelPath = "models/objects/smoke/tris.md2",
            position = origin,
            firstFrame = 0,
            frameCount = 4,
            startsTranslucent = true,
        )
        spawnLegacyExplosionEffect(
            type = ExplosionType.FLASH,
            modelPath = "models/objects/flash/tris.md2",
            position = origin,
            firstFrame = 0,
            frameCount = 2,
            fullBright = true,
        )
    }

    private fun spawnBlasterImpact(position: Vector3, direction: FloatArray?, skinIndex: Int) {
        val (pitch, yaw) = computeDirectionAngles(direction) ?: return
        spawnLegacyExplosionEffect(
            type = ExplosionType.MISC,
            modelPath = "models/objects/explode/tris.md2",
            position = position,
            firstFrame = 0,
            frameCount = 4,
            pitchDeg = pitch,
            yawDeg = yaw,
            skinIndex = skinIndex,
            startsTranslucent = true,
            fullBright = true,
        )
    }

    private fun spawnExplosionModel(
        modelPath: String,
        position: Vector3,
        firstFrame: Int,
        frameCount: Int,
    ) {
        spawnLegacyExplosionEffect(
            type = ExplosionType.POLY,
            modelPath = modelPath,
            position = position,
            firstFrame = firstFrame,
            frameCount = frameCount,
            yawDeg = Globals.rnd.nextInt(360).toFloat(),
            fullBright = true,
        )
    }

    private fun spawnLegacyExplosionEffect(
        type: ExplosionType,
        modelPath: String,
        position: Vector3,
        firstFrame: Int,
        frameCount: Int,
        pitchDeg: Float = 0f,
        yawDeg: Float = 0f,
        rollDeg: Float = 0f,
        skinIndex: Int? = null,
        startsTranslucent: Boolean = false,
        fullBright: Boolean = false,
    ) {
        val md2 = assetCatalog.getModel(modelPath) ?: return
        val instance = createModelInstance(md2.model)
        (instance.userData as? Md2CustomData)?.let { userData ->
            if (skinIndex != null) {
                userData.skinIndex = skinIndex
            }
            if (fullBright) {
                userData.lightRed = 1f
                userData.lightGreen = 1f
                userData.lightBlue = 1f
                userData.shadeVectorX = 0f
                userData.shadeVectorY = 0f
                userData.shadeVectorZ = 0f
            }
        }
        activeEffects += ExplosionMd2Effect(
            modelInstance = instance,
            type = type,
            spawnTimeMs = Globals.curtime,
            frameDurationMs = 100,
            firstFrame = firstFrame,
            frameCount = frameCount,
            position = Vector3(position),
            pitchDeg = pitchDeg,
            yawDeg = yawDeg,
            rollDeg = rollDeg,
            baseSkinIndex = skinIndex,
            fullBright = fullBright,
            startsTranslucent = startsTranslucent,
        )
    }

    private fun spawnExplosionSpriteEffect(
        type: ExplosionType,
        spritePath: String,
        position: Vector3,
        firstFrame: Int,
        frameCount: Int,
        startsTranslucent: Boolean = false,
    ) {
        val sprite = assetCatalog.getSprite(spritePath) ?: return
        activeEffects += ExplosionSpriteEffect(
            spriteRenderer = spriteRenderer,
            cameraProvider = cameraProvider,
            sprite = sprite,
            type = type,
            spawnTimeMs = Globals.curtime,
            frameDurationMs = 100,
            firstFrame = firstFrame,
            frameCount = frameCount,
            position = Vector3(position),
            startsTranslucent = startsTranslucent,
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
        (instance.userData as? Md2CustomData)?.let { userData ->
            if (skinIndex != null) {
                userData.skinIndex = skinIndex
            }
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

    private fun emitBlasterTrail(
        startX: Float,
        startY: Float,
        startZ: Float,
        endX: Float,
        endY: Float,
        endZ: Float,
        paletteIndex: Int,
        fallback: Color,
    ) {
        val delta = Vector3(endX - startX, endY - startY, endZ - startZ)
        val length = delta.len()
        if (length <= 0.001f) {
            return
        }
        val step = BLASTER_TRAIL_STEP
        val direction = delta.scl(1f / length)
        var travelled = 0f
        var samples = 0
        while (travelled <= length && samples < ENTITY_TRAIL_MAX_SAMPLES) {
            val color = resolvePaletteColor(paletteIndex, fallback = fallback)
            particleSystem.emitBurst(
                origin = Vector3(
                    startX + direction.x * travelled + randomRange(-BLASTER_TRAIL_ORIGIN_JITTER, BLASTER_TRAIL_ORIGIN_JITTER),
                    startY + direction.y * travelled + randomRange(-BLASTER_TRAIL_ORIGIN_JITTER, BLASTER_TRAIL_ORIGIN_JITTER),
                    startZ + direction.z * travelled + randomRange(-BLASTER_TRAIL_ORIGIN_JITTER, BLASTER_TRAIL_ORIGIN_JITTER),
                ),
                direction = floatArrayOf(0f, 0f, 1f),
                count = 1,
                color = color,
                speedMin = BLASTER_TRAIL_SPEED_MIN,
                speedMax = BLASTER_TRAIL_SPEED_MAX,
                spread = BLASTER_TRAIL_SPREAD,
                gravity = BLASTER_TRAIL_GRAVITY,
                startAlpha = 1f,
                endAlpha = 0f,
                sizeMin = BLASTER_TRAIL_SIZE_MIN,
                sizeMax = BLASTER_TRAIL_SIZE_MAX,
                lifetimeMinMs = BLASTER_TRAIL_LIFETIME_MIN_MS,
                lifetimeMaxMs = BLASTER_TRAIL_LIFETIME_MAX_MS,
            )
            travelled += step
            samples++
        }
    }

    private fun emitDiminishingTrail(
        startX: Float,
        startY: Float,
        startZ: Float,
        endX: Float,
        endY: Float,
        endZ: Float,
        entity: ClientEntity,
        flags: Int,
    ) {
        val delta = Vector3(endX - startX, endY - startY, endZ - startZ)
        val length = delta.len()
        if (length <= 0.001f) {
            return
        }
        val (originScale, velocityScale) = when {
            entity.trailcount > 900 -> DIMINISH_TRAIL_HEAVY_ORIGIN_SCALE to DIMINISH_TRAIL_HEAVY_VELOCITY_SCALE
            entity.trailcount > 800 -> DIMINISH_TRAIL_MEDIUM_ORIGIN_SCALE to DIMINISH_TRAIL_MEDIUM_VELOCITY_SCALE
            else -> DIMINISH_TRAIL_LIGHT_ORIGIN_SCALE to DIMINISH_TRAIL_LIGHT_VELOCITY_SCALE
        }

        val direction = delta.scl(1f / length)
        var travelled = 0f
        var samples = 0
        while (travelled <= length && samples < ENTITY_TRAIL_MAX_SAMPLES) {
            if (Globals.rnd.nextInt(1024) < entity.trailcount) {
                val (paletteIndex, fallbackColor, gravity, lifetimeMinMs, lifetimeMaxMs) = when {
                    (flags and Defines.EF_GIB) != 0 -> {
                        val colorIndex = GIB_TRAIL_COLOR_BASE + Globals.rnd.nextInt(8)
                        ParticleTrailSpec(
                            colorIndex = colorIndex,
                            fallbackColor = GIB_TRAIL_COLOR_FALLBACK,
                            gravity = GIB_TRAIL_GRAVITY,
                            lifetimeMinMs = GIB_TRAIL_LIFETIME_MIN_MS,
                            lifetimeMaxMs = GIB_TRAIL_LIFETIME_MAX_MS,
                        )
                    }
                    (flags and Defines.EF_GREENGIB) != 0 -> {
                        val colorIndex = GREEN_GIB_TRAIL_COLOR_BASE + Globals.rnd.nextInt(8)
                        ParticleTrailSpec(
                            colorIndex = colorIndex,
                            fallbackColor = GREEN_GIB_TRAIL_COLOR_FALLBACK,
                            gravity = GIB_TRAIL_GRAVITY,
                            lifetimeMinMs = GIB_TRAIL_LIFETIME_MIN_MS,
                            lifetimeMaxMs = GIB_TRAIL_LIFETIME_MAX_MS,
                        )
                    }
                    else -> {
                        val colorIndex = SMOKE_TRAIL_COLOR_BASE + Globals.rnd.nextInt(8)
                        ParticleTrailSpec(
                            colorIndex = colorIndex,
                            fallbackColor = SMOKE_TRAIL_COLOR_FALLBACK,
                            gravity = SMOKE_TRAIL_GRAVITY,
                            lifetimeMinMs = SMOKE_TRAIL_LIFETIME_MIN_MS,
                            lifetimeMaxMs = SMOKE_TRAIL_LIFETIME_MAX_MS,
                        )
                    }
                }
                val randomDirection = randomUnitDirection()
                particleSystem.emitBurst(
                    origin = Vector3(
                        startX + direction.x * travelled + randomRange(-originScale, originScale),
                        startY + direction.y * travelled + randomRange(-originScale, originScale),
                        startZ + direction.z * travelled + randomRange(-originScale, originScale),
                    ),
                    direction = floatArrayOf(randomDirection.x, randomDirection.y, randomDirection.z),
                    count = 1,
                    color = resolvePaletteColor(paletteIndex, fallback = fallbackColor),
                    speedMin = 0f,
                    speedMax = velocityScale,
                    spread = 0f,
                    gravity = gravity,
                    startAlpha = 1f,
                    endAlpha = 0f,
                    sizeMin = DIMINISH_TRAIL_SIZE_MIN,
                    sizeMax = DIMINISH_TRAIL_SIZE_MAX,
                    lifetimeMinMs = lifetimeMinMs,
                    lifetimeMaxMs = lifetimeMaxMs,
                )
            }

            entity.trailcount = (entity.trailcount - DIMINISH_TRAIL_COUNT_DECAY_PER_SAMPLE).coerceAtLeast(DIMINISH_TRAIL_COUNT_MIN)
            travelled += DIMINISH_TRAIL_STEP
            samples++
        }
    }

    private fun emitRocketTrail(
        startX: Float,
        startY: Float,
        startZ: Float,
        endX: Float,
        endY: Float,
        endZ: Float,
        entity: ClientEntity,
    ) {
        emitDiminishingTrail(
            startX = startX,
            startY = startY,
            startZ = startZ,
            endX = endX,
            endY = endY,
            endZ = endZ,
            entity = entity,
            flags = Defines.EF_ROCKET,
        )

        val delta = Vector3(endX - startX, endY - startY, endZ - startZ)
        val length = delta.len()
        if (length <= 0.001f) {
            return
        }
        val direction = delta.scl(1f / length)
        var travelled = 0f
        var samples = 0
        while (travelled <= length && samples < ENTITY_TRAIL_MAX_SAMPLES) {
            if (Globals.rnd.nextInt(ROCKET_FIRE_SPAWN_CHANCE_DIVISOR) == 0) {
                val colorIndex = ROCKET_FIRE_COLOR_BASE + Globals.rnd.nextInt(ROCKET_FIRE_COLOR_VARIANTS)
                val randomDirection = randomUnitDirection()
                particleSystem.emitBurst(
                    origin = Vector3(
                        startX + direction.x * travelled + randomRange(-ROCKET_FIRE_ORIGIN_JITTER, ROCKET_FIRE_ORIGIN_JITTER),
                        startY + direction.y * travelled + randomRange(-ROCKET_FIRE_ORIGIN_JITTER, ROCKET_FIRE_ORIGIN_JITTER),
                        startZ + direction.z * travelled + randomRange(-ROCKET_FIRE_ORIGIN_JITTER, ROCKET_FIRE_ORIGIN_JITTER),
                    ),
                    direction = floatArrayOf(randomDirection.x, randomDirection.y, randomDirection.z),
                    count = 1,
                    color = resolvePaletteColor(colorIndex, fallback = ROCKET_FIRE_COLOR_FALLBACK),
                    speedMin = ROCKET_FIRE_SPEED_MIN,
                    speedMax = ROCKET_FIRE_SPEED_MAX,
                    spread = 0f,
                    gravity = ROCKET_FIRE_GRAVITY,
                    startAlpha = 1f,
                    endAlpha = 0f,
                    sizeMin = ROCKET_FIRE_SIZE_MIN,
                    sizeMax = ROCKET_FIRE_SIZE_MAX,
                    lifetimeMinMs = ROCKET_FIRE_LIFETIME_MIN_MS,
                    lifetimeMaxMs = ROCKET_FIRE_LIFETIME_MAX_MS,
                )
            }
            travelled += ROCKET_FIRE_STEP
            samples++
        }
    }

    private fun emitLegacyPaletteParticles(
        origin: Vector3,
        direction: FloatArray?,
        count: Int,
        paletteIndex: Int,
        gravity: Float,
    ) {
        particleSystem.emitBurst(
            origin = origin,
            direction = direction,
            count = count.coerceIn(1, 128),
            color = resolvePaletteColor(paletteIndex, fallback = Color(1f, 0.85f, 0.4f, 1f)),
            speedMin = 8f,
            speedMax = 48f,
            spread = 0.55f,
            gravity = gravity,
            startAlpha = 1f,
            endAlpha = 0f,
            sizeMin = 0.3f,
            sizeMax = 1f,
            lifetimeMinMs = 180,
            lifetimeMaxMs = 560,
        )
    }

    private fun emitBfgBigExplosionParticles(origin: Vector3) {
        emitPaletteOmniBurst(
            origin = origin,
            count = 256,
            colorIndexProvider = { 0xD0 + Globals.rnd.nextInt(8) },
            fallbackColor = Color(0.22f, 1f, 0.35f, 1f),
            speedMin = 20f,
            speedMax = 220f,
            spread = 0.15f,
            gravity = -320f,
            sizeMin = 0.38f,
            sizeMax = 1.2f,
            lifetimeMinMs = 580,
            lifetimeMaxMs = 980,
            originJitter = 16f,
        )
    }

    private fun emitRailTrailParticles(start: Vector3, end: Vector3) {
        val delta = Vector3(end).sub(start)
        val length = delta.len()
        if (length <= 0.001f) {
            return
        }

        val forward = delta.scl(1f / length)
        val (right, up) = buildNormalBasis(forward)

        val move = Vector3(start)
        val spiralSamples = length.toInt().coerceIn(1, RAIL_TRAIL_MAX_SAMPLES)
        for (sample in 0 until spiralSamples) {
            val phase = sample * RAIL_TRAIL_SPIRAL_PHASE_STEP
            val radial = Vector3(right).scl(cos(phase)).mulAdd(up, sin(phase))
            val spawnOrigin = Vector3(move).mulAdd(radial, RAIL_TRAIL_SPIRAL_RADIUS)
            val spiralColor = Color(
                (RAIL_TRAIL_SPIRAL_COLOR.r + randomRange(-RAIL_TRAIL_SPIRAL_COLOR_VARIATION, RAIL_TRAIL_SPIRAL_COLOR_VARIATION)).coerceIn(0f, 1f),
                (RAIL_TRAIL_SPIRAL_COLOR.g + randomRange(-RAIL_TRAIL_SPIRAL_COLOR_VARIATION, RAIL_TRAIL_SPIRAL_COLOR_VARIATION)).coerceIn(0f, 1f),
                (RAIL_TRAIL_SPIRAL_COLOR.b + randomRange(-RAIL_TRAIL_SPIRAL_COLOR_VARIATION, RAIL_TRAIL_SPIRAL_COLOR_VARIATION)).coerceIn(0f, 1f),
                1f,
            )
            particleSystem.emitBurst(
                origin = spawnOrigin,
                direction = floatArrayOf(radial.x, radial.y, radial.z),
                count = 1,
                color = spiralColor,
                speedMin = RAIL_TRAIL_SPIRAL_SPEED,
                speedMax = RAIL_TRAIL_SPIRAL_SPEED,
                spread = 0f,
                gravity = 0f,
                startAlpha = 1f,
                endAlpha = 0f,
                sizeMin = RAIL_TRAIL_SPIRAL_SIZE_MIN,
                sizeMax = RAIL_TRAIL_SPIRAL_SIZE_MAX,
                lifetimeMinMs = RAIL_TRAIL_SPIRAL_LIFETIME_MIN_MS,
                lifetimeMaxMs = RAIL_TRAIL_SPIRAL_LIFETIME_MAX_MS,
            )
            move.mulAdd(forward, RAIL_TRAIL_SPIRAL_STEP)
        }

        val dec = RAIL_TRAIL_CORE_STEP
        val step = Vector3(forward).scl(dec)
        val coreMove = Vector3(start)
        var remaining = length
        var coreSamples = 0
        while (remaining > 0f && coreSamples < RAIL_TRAIL_MAX_SAMPLES) {
            remaining -= dec
            val spawnOrigin = Vector3(coreMove).add(
                randomRange(-RAIL_TRAIL_CORE_JITTER, RAIL_TRAIL_CORE_JITTER),
                randomRange(-RAIL_TRAIL_CORE_JITTER, RAIL_TRAIL_CORE_JITTER),
                randomRange(-RAIL_TRAIL_CORE_JITTER, RAIL_TRAIL_CORE_JITTER),
            )
            val white = (RAIL_TRAIL_CORE_WHITE_BASE + randomRange(-RAIL_TRAIL_CORE_WHITE_VARIATION, RAIL_TRAIL_CORE_WHITE_VARIATION)).coerceIn(0f, 1f)
            val randomDir = randomUnitDirection()
            particleSystem.emitBurst(
                origin = spawnOrigin,
                direction = floatArrayOf(randomDir.x, randomDir.y, randomDir.z),
                count = 1,
                color = Color(white, white, white, 1f),
                speedMin = 0f,
                speedMax = RAIL_TRAIL_CORE_SPEED_MAX,
                spread = 0f,
                gravity = 0f,
                startAlpha = 1f,
                endAlpha = 0f,
                sizeMin = RAIL_TRAIL_CORE_SIZE_MIN,
                sizeMax = RAIL_TRAIL_CORE_SIZE_MAX,
                lifetimeMinMs = RAIL_TRAIL_CORE_LIFETIME_MIN_MS,
                lifetimeMaxMs = RAIL_TRAIL_CORE_LIFETIME_MAX_MS,
            )
            coreMove.add(step)
            coreSamples++
        }
    }

    private fun emitTeleportEffectParticles(origin: Vector3) {
        emitPaletteOmniBurst(
            origin = origin,
            count = 384,
            colorIndexProvider = { 7 + Globals.rnd.nextInt(8) },
            fallbackColor = Color(0.68f, 0.55f, 1f, 1f),
            speedMin = 50f,
            speedMax = 113f,
            spread = 0.55f,
            gravity = -320f,
            sizeMin = 0.34f,
            sizeMax = 1.15f,
            lifetimeMinMs = 300,
            lifetimeMaxMs = 460,
            originJitter = 18f,
        )
    }

    private fun emitWidowSplashParticles(origin: Vector3) {
        val colorTable = intArrayOf(2 * 8, 13 * 8, 21 * 8, 18 * 8)
        emitPaletteOmniBurst(
            origin = origin,
            count = 256,
            colorIndexProvider = { colorTable[Globals.rnd.nextInt(colorTable.size)] },
            fallbackColor = Color(0.4f, 0.85f, 0.75f, 1f),
            speedMin = 28f,
            speedMax = 52f,
            spread = 0.25f,
            gravity = 0f,
            sizeMin = 0.38f,
            sizeMax = 1.15f,
            lifetimeMinMs = 580,
            lifetimeMaxMs = 980,
            originJitter = 0f,
            radialSpawnOffset = 45f,
        )
    }

    private fun resolvePaletteColor(index: Int, fallback: Color): Color {
        val palette = q2Palette ?: return Color(fallback)
        if (palette.isEmpty()) {
            return Color(fallback)
        }
        val paletteIndex = (index and 0xFF).coerceIn(0, palette.lastIndex)
        val rgba8888 = palette[paletteIndex]
        return Color(
            ((rgba8888 ushr 24) and 0xFF) / 255f,
            ((rgba8888 ushr 16) and 0xFF) / 255f,
            ((rgba8888 ushr 8) and 0xFF) / 255f,
            1f,
        )
    }

    private fun emitPaletteOmniBurst(
        origin: Vector3,
        count: Int,
        colorIndexProvider: () -> Int,
        fallbackColor: Color,
        speedMin: Float,
        speedMax: Float,
        spread: Float,
        gravity: Float,
        sizeMin: Float,
        sizeMax: Float,
        lifetimeMinMs: Int,
        lifetimeMaxMs: Int,
        originJitter: Float,
        radialSpawnOffset: Float = 0f,
    ) {
        val safeCount = count.coerceIn(0, 512)
        repeat(safeCount) {
            val direction = randomUnitDirection()
            val spawnOrigin = Vector3(origin)
            if (originJitter > 0f) {
                spawnOrigin.add(
                    Globals.rnd.nextFloat() * originJitter * 2f - originJitter,
                    Globals.rnd.nextFloat() * originJitter * 2f - originJitter,
                    Globals.rnd.nextFloat() * originJitter * 2f - originJitter,
                )
            }
            if (radialSpawnOffset > 0f) {
                spawnOrigin.mulAdd(direction, radialSpawnOffset)
            }
            particleSystem.emitBurst(
                origin = spawnOrigin,
                direction = floatArrayOf(direction.x, direction.y, direction.z),
                count = 1,
                color = resolvePaletteColor(colorIndexProvider(), fallback = fallbackColor),
                speedMin = speedMin,
                speedMax = speedMax,
                spread = spread,
                gravity = gravity,
                startAlpha = 1f,
                endAlpha = 0f,
                sizeMin = sizeMin,
                sizeMax = sizeMax,
                lifetimeMinMs = lifetimeMinMs,
                lifetimeMaxMs = lifetimeMaxMs,
            )
        }
    }

    private fun randomUnitDirection(): Vector3 {
        var x: Float
        var y: Float
        var z: Float
        do {
            x = Globals.rnd.nextFloat() * 2f - 1f
            y = Globals.rnd.nextFloat() * 2f - 1f
            z = Globals.rnd.nextFloat() * 2f - 1f
        } while (x * x + y * y + z * z < 0.0001f)
        return Vector3(x, y, z).nor()
    }

    private fun buildNormalBasis(forward: Vector3): Pair<Vector3, Vector3> {
        val anchor = if (abs(forward.z) < 0.999f) {
            Vector3.Z
        } else {
            Vector3.X
        }
        val right = Vector3(forward).crs(anchor).nor()
        val up = Vector3(right).crs(forward).nor()
        return right to up
    }

    private fun randomRange(min: Float, max: Float): Float {
        if (min >= max) {
            return min
        }
        return min + Globals.rnd.nextFloat() * (max - min)
    }

    private fun emitSegmentTrailParticles(
        start: Vector3,
        end: Vector3,
        spacing: Float,
        maxSteps: Int,
        direction: FloatArray,
        countPerStep: Int,
        colorProvider: () -> Color,
        speedMin: Float,
        speedMax: Float,
        spread: Float,
        gravity: Float,
        sizeMin: Float,
        sizeMax: Float,
        lifetimeMinMs: Int,
        lifetimeMaxMs: Int,
    ) {
        val delta = Vector3(end).sub(start)
        val length = delta.len()
        if (length <= 0.001f) {
            return
        }

        val step = spacing.coerceAtLeast(0.1f)
        val directionNorm = delta.scl(1f / length)
        var travelled = 0f
        var steps = 0
        while (travelled <= length && steps < maxSteps) {
            val samplePoint = Vector3(start).mulAdd(directionNorm, travelled)
            particleSystem.emitBurst(
                origin = samplePoint,
                direction = direction,
                count = countPerStep,
                color = colorProvider(),
                speedMin = speedMin,
                speedMax = speedMax,
                spread = spread,
                gravity = gravity,
                startAlpha = 1f,
                endAlpha = 0f,
                sizeMin = sizeMin,
                sizeMax = sizeMax,
                lifetimeMinMs = lifetimeMinMs,
                lifetimeMaxMs = lifetimeMaxMs,
            )
            travelled += step
            steps++
        }
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

// Visual target: Quake2 rail trail (blue spiral with bright white center).
private const val RAIL_TRAIL_MAX_SAMPLES = 4096
private const val RAIL_TRAIL_SPIRAL_PHASE_STEP = 0.1f
private const val RAIL_TRAIL_SPIRAL_RADIUS = 3f
private const val RAIL_TRAIL_SPIRAL_STEP = 1f
private const val RAIL_TRAIL_SPIRAL_SPEED = 6f
private const val RAIL_TRAIL_SPIRAL_SIZE_MIN = 0.22f
private const val RAIL_TRAIL_SPIRAL_SIZE_MAX = 0.42f
private const val RAIL_TRAIL_SPIRAL_LIFETIME_MIN_MS = 900
private const val RAIL_TRAIL_SPIRAL_LIFETIME_MAX_MS = 1200
private const val RAIL_TRAIL_SPIRAL_COLOR_VARIATION = 0.05f
private val RAIL_TRAIL_SPIRAL_COLOR = Color(0.22f, 0.52f, 1f, 1f)

private const val RAIL_TRAIL_CORE_STEP = 0.75f
private const val RAIL_TRAIL_CORE_JITTER = 3f
private const val RAIL_TRAIL_CORE_SPEED_MAX = 3f
private const val RAIL_TRAIL_CORE_SIZE_MIN = 0.18f
private const val RAIL_TRAIL_CORE_SIZE_MAX = 0.34f
private const val RAIL_TRAIL_CORE_LIFETIME_MIN_MS = 600
private const val RAIL_TRAIL_CORE_LIFETIME_MAX_MS = 820
private const val RAIL_TRAIL_CORE_WHITE_BASE = 0.94f

private data class ParticleTrailSpec(
    val colorIndex: Int,
    val fallbackColor: Color,
    val gravity: Float,
    val lifetimeMinMs: Int,
    val lifetimeMaxMs: Int,
)

// Replicated entity trail constants (`EF_ROCKET`, `EF_BLASTER`, `EF_GRENADE`).
private const val ENTITY_TRAIL_MAX_SAMPLES = 4096

private const val BLASTER_TRAIL_STEP = 5f
private const val BLASTER_TRAIL_ORIGIN_JITTER = 1f
private const val BLASTER_TRAIL_SPEED_MIN = 0f
private const val BLASTER_TRAIL_SPEED_MAX = 5f
private const val BLASTER_TRAIL_SPREAD = 1f
private const val BLASTER_TRAIL_GRAVITY = 0f
private const val BLASTER_TRAIL_SIZE_MIN = 0.18f
private const val BLASTER_TRAIL_SIZE_MAX = 0.42f
private const val BLASTER_TRAIL_LIFETIME_MIN_MS = 300
private const val BLASTER_TRAIL_LIFETIME_MAX_MS = 500
private const val BLASTER_TRAIL_COLOR_INDEX = 0xE0
private const val BLASTER2_TRAIL_COLOR_INDEX = 0xD0
private val BLASTER_TRAIL_COLOR_FALLBACK = Color(1f, 0.87f, 0.35f, 1f)
private val BLASTER2_TRAIL_COLOR_FALLBACK = Color(0.3f, 0.95f, 0.3f, 1f)

private const val DIMINISH_TRAIL_STEP = 0.5f
private const val DIMINISH_TRAIL_HEAVY_ORIGIN_SCALE = 4f
private const val DIMINISH_TRAIL_MEDIUM_ORIGIN_SCALE = 2f
private const val DIMINISH_TRAIL_LIGHT_ORIGIN_SCALE = 1f
private const val DIMINISH_TRAIL_HEAVY_VELOCITY_SCALE = 15f
private const val DIMINISH_TRAIL_MEDIUM_VELOCITY_SCALE = 10f
private const val DIMINISH_TRAIL_LIGHT_VELOCITY_SCALE = 5f
private const val DIMINISH_TRAIL_COUNT_DECAY_PER_SAMPLE = 5
private const val DIMINISH_TRAIL_COUNT_MIN = 100
private const val DIMINISH_TRAIL_SIZE_MIN = 0.18f
private const val DIMINISH_TRAIL_SIZE_MAX = 0.48f

private const val GIB_TRAIL_COLOR_BASE = 0xE8
private const val GREEN_GIB_TRAIL_COLOR_BASE = 0xDB
private const val SMOKE_TRAIL_COLOR_BASE = 4
private const val GIB_TRAIL_GRAVITY = -40f
private const val SMOKE_TRAIL_GRAVITY = 20f
private const val GIB_TRAIL_LIFETIME_MIN_MS = 1000
private const val GIB_TRAIL_LIFETIME_MAX_MS = 1400
private const val SMOKE_TRAIL_LIFETIME_MIN_MS = 1000
private const val SMOKE_TRAIL_LIFETIME_MAX_MS = 1200
private val GIB_TRAIL_COLOR_FALLBACK = Color(0.86f, 0.18f, 0.16f, 1f)
private val GREEN_GIB_TRAIL_COLOR_FALLBACK = Color(0.45f, 0.88f, 0.33f, 1f)
private val SMOKE_TRAIL_COLOR_FALLBACK = Color(0.58f, 0.58f, 0.58f, 1f)

private const val ROCKET_FIRE_STEP = 1f
private const val ROCKET_FIRE_SPAWN_CHANCE_DIVISOR = 8
private const val ROCKET_FIRE_COLOR_BASE = 0xDC
private const val ROCKET_FIRE_COLOR_VARIANTS = 4
private const val ROCKET_FIRE_ORIGIN_JITTER = 5f
private const val ROCKET_FIRE_SPEED_MIN = 0f
private const val ROCKET_FIRE_SPEED_MAX = 20f
private const val ROCKET_FIRE_GRAVITY = -40f
private const val ROCKET_FIRE_SIZE_MIN = 0.2f
private const val ROCKET_FIRE_SIZE_MAX = 0.55f
private const val ROCKET_FIRE_LIFETIME_MIN_MS = 1000
private const val ROCKET_FIRE_LIFETIME_MAX_MS = 1200
private val ROCKET_FIRE_COLOR_FALLBACK = Color(1f, 0.52f, 0.18f, 1f)
private const val RAIL_TRAIL_CORE_WHITE_VARIATION = 0.06f

private val SPARK_SOUNDS = listOf(
    "sound/world/spark5.wav",
    "sound/world/spark6.wav",
    "sound/world/spark7.wav",
)
