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
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.LightningTEMessage
import jake2.qcommon.network.messages.server.FlashlightTEMessage
import jake2.qcommon.network.messages.server.ForceWallTEMessage
import jake2.qcommon.network.messages.server.SteamTEMessage
import jake2.qcommon.network.messages.server.WidowBeamOutTEMessage
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
import org.demoth.cake.audio.CakeAudioSystem
import org.demoth.cake.audio.SoundPlaybackRequest
import org.demoth.cake.assets.md2.Md2CustomData
import org.demoth.cake.assets.Sp2Renderer
import org.demoth.cake.createModelInstance
import org.demoth.cake.stages.ingame.ClientEntityManager
import org.demoth.cake.stages.ingame.DynamicLightSystem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
 * - Call [update]/[renderParticles]/[render] every frame on render thread.
 * - Call [dispose] when screen is disposed.
 *
 * Constructor contract:
 * - [audioSystem] owns gameplay/effect sound dispatch.
 * - [cameraProvider] is required for billboard sprite effects (`.sp2`).
 *
 * Legacy counterparts:
 * - `client/CL_fx.ParseMuzzleFlash2`
 * - `client/CL_tent.ParseTEnt`
 */
class ClientEffectsSystem(
    private val assetManager: AssetManager,
    private val entityManager: ClientEntityManager,
    private val audioSystem: CakeAudioSystem,
    private val cameraProvider: () -> Camera,
    private val dynamicLightSystem: DynamicLightSystem? = null,
    private val currentTimeMsProvider: () -> Int = { Globals.curtime },
    private val localPlayerEntityIndexProvider: () -> Int? = { null },
    private val gunOffsetProvider: () -> Vector3 = { Vector3.Zero },
    private val handednessProvider: () -> Int = { 0 },
) : Disposable {
    private val assetCatalog = EffectAssetCatalog(assetManager)
    private val spriteRenderer = Sp2Renderer()
    private val particleSystem = EffectParticleSystem(assetManager, currentTimeMsProvider)
    private val q2Palette: IntArray? by lazy {
        if (assetManager.isLoaded("q2palette.bin", Any::class.java)) {
            assetManager.get("q2palette.bin", Any::class.java) as? IntArray
        } else {
            null
        }
    }
    private val steamEffects = SteamEffects(particleSystem.runtime, paletteColor = { index ->
        resolvePaletteColor(index, fallback = STEAM_PARTICLE_FALLBACK)
    })
    private val beamModels = BeamModelRenderer { path -> assetCatalog.getModel(path)?.model }
    private val beamEffects = BeamEffects(playSound = { request ->
        assetCatalog.getSound(request.path)?.let { sound ->
            audioSystem.play(SoundPlaybackRequest(sound = sound, entityIndex = request.entityIndex,
                channel = request.channel, attenuation = Defines.ATTN_NORM.toFloat()))
        }
    })
    private val heatbeamEffects = HeatbeamEffects(HeatbeamParticles(particleSystem.runtime, paletteColor = { index ->
        resolvePaletteColor(index, fallback = HEATBEAM_PARTICLE_FALLBACK)
    }))
    private val expansionEffects = ExpansionEffects(particleSystem.runtime,
        paletteColor = { index -> resolvePaletteColor(index, fallback = STEAM_PARTICLE_FALLBACK) },
        lights = dynamicLightSystem,
        playBubbleSound = { position -> playEffectSound("sound/weapons/lashit.wav", toVector3(position)) })
    private val shellEffects = ShellEffects(particleSystem.runtime, paletteColor = { index ->
        resolvePaletteColor(index, fallback = EXPLOSION_PARTICLE_FALLBACK)
    })
    private val pointParticles = PointParticles(particleSystem.runtime, paletteColor = { index ->
        resolvePaletteColor(index, fallback = IMPACT_PARTICLE_COLOR_FALLBACK)
    })
    private val impactEffects = ImpactEffects(particleSystem.runtime,
        paletteColor = { index -> resolvePaletteColor(index, fallback = IMPACT_PARTICLE_COLOR_FALLBACK) },
        playSound = { request -> playEffectSound(request.path, toVector3(request.origin), attenuation = request.attenuation) })
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
     * The codec validates subtype/layout combinations. Family controllers provide models, particles,
     * lights and sounds through the screen-owned presentation runtimes.
     */
    fun processTempEntityMessage(msg: TEMessage) {
        when (msg) {
            is BeamTEMessage -> {
                val now = currentTimeMsProvider()
                if (!heatbeamEffects.process(msg, now)) beamEffects.process(msg, now)
            }
            is BeamOffsetTEMessage, is LightningTEMessage -> beamEffects.process(msg, currentTimeMsProvider())
            is PointDirectionTEMessage -> handlePointDirectionMessage(msg)
            is TrailTEMessage -> handleTrailMessage(msg)
            is SplashTEMessage -> handleSplashMessage(msg)
            is PointTEMessage -> handlePointMessage(msg)
            is SteamTEMessage -> steamEffects.process(msg, currentTimeMsProvider())
            // Flashlight and force-wall messages feed the shared light and particle runtimes.
            is FlashlightTEMessage, is ForceWallTEMessage -> expansionEffects.process(msg, currentTimeMsProvider())
            is WidowBeamOutTEMessage -> shellEffects.process(msg, currentTimeMsProvider())
        }
    }

    /**
     * Emits visual particles for `EV_ITEM_RESPAWN`.
     *
     * Sound playback for this event is owned by `Game3dScreen` to keep packet/entity-event channel
     * semantics centralized in one place.
     */
    fun emitItemRespawnEvent(entityIndex: Int) {
        val origin = entityManager.getEntityOrigin(entityIndex) ?: return
        emitItemRespawnParticles(origin)
    }

    /**
     * Emits visual particles for `EV_PLAYER_TELEPORT`.
     *
     * Sound playback for this event is owned by `Game3dScreen` to keep packet/entity-event channel
     * semantics centralized in one place.
     */
    fun emitPlayerTeleportEvent(entityIndex: Int) {
        val origin = entityManager.getEntityOrigin(entityIndex) ?: return
        emitTeleportEffectParticles(origin)
    }

    /**
     * Emits special muzzleflash particle burst for `MZ_LOGIN`, `MZ_LOGOUT`, `MZ_RESPAWN`.
     *
     * Legacy counterpart: `client/CL_fx.LogoutEffect`.
     */
    fun emitLoginLogoutRespawnEvent(entityIndex: Int, weaponType: Int) {
        val origin = entityManager.getEntityOrigin(entityIndex) ?: return
        emitLoginLogoutRespawnParticles(origin, weaponType)
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(deltaSeconds: Float, _serverFrame: Int) {
        val now = currentTimeMsProvider()
        particleSystem.update(now)
        steamEffects.update(now)
        shellEffects.update(now)
        beamModels.beginFrame()
        val camera = cameraProvider()
        val localPlayer = localPlayerEntityIndexProvider()
        beamEffects.submit(now, localPlayer, camera.position, beamModels)
        heatbeamEffects.submit(now, localPlayer, camera, gunOffsetProvider(), handednessProvider(), beamModels)
        val iterator = activeEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            if (!effect.update(now, deltaSeconds)) {
                effect.dispose()
                iterator.remove()
            }
        }
    }

    /** Adds animation-owned lights after the light system's frame reset. */
    fun collectDynamicLights() {
        val lights = dynamicLightSystem ?: return
        activeEffects.forEach { it.collectDynamicLights(lights) }
    }

    /**
     * Renders model/sprite transient effects via `ModelBatch`.
     *
     * Important:
     * - particle rendering is intentionally excluded from this pass;
     *   use [renderParticles] for the dedicated particle renderer path.
     */
    fun render(modelBatch: ModelBatch) {
        beamModels.render(modelBatch)
        activeEffects.forEach { effect ->
            effect.render(modelBatch)
        }
    }

    /**
     * Renders the transient particle pass using the dedicated particle renderer.
     *
     * This pass is separate from `ModelBatch` by design:
     * - avoids one-renderable-per-particle submission overhead,
     * - keeps particle draw-call count bounded by renderer buckets.
     */
    fun renderParticles(camera: Camera) {
        particleSystem.render(camera)
    }

    fun debugLiveParticleCount(): Int = particleSystem.liveParticleCount()

    fun debugParticleRenderStats(): ParticleRenderer.Stats = particleSystem.lastRenderStats()

    fun debugActiveEffectCount(): Int = activeEffects.size + beamEffects.activeCount + heatbeamEffects.activeCount + steamEffects.activeSustainCount + shellEffects.activeSustainCount

    override fun dispose() {
        shellEffects.clear()
        heatbeamEffects.clear()
        beamEffects.clear()
        beamModels.dispose()
        steamEffects.clear()
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
        val position = toVector3(msg.position)
        val now = currentTimeMsProvider()
        if (steamEffects.processImpact(msg, now)) {
            playEffectSound("sound/weapons/lashit.wav", position)
            return
        }
        impactEffects.process(msg, now)
        when (msg.style) {
            Defines.TE_GUNSHOT, Defines.TE_BULLET_SPARKS, Defines.TE_SHOTGUN -> spawnSmokeAndFlash(position)
            Defines.TE_BLASTER -> {
                spawnBlasterImpact(position, msg.direction.toArray(), skinIndex = 0)
            }
            Defines.TE_BLASTER2 -> {
                spawnBlasterImpact(position, msg.direction.toArray(), skinIndex = 1)
            }
            Defines.TE_FLECHETTE -> {
                spawnBlasterImpact(position, msg.direction.toArray(), skinIndex = 2)
            }
        }
    }

    private fun handleTrailMessage(msg: TrailTEMessage) {
        val now = currentTimeMsProvider()
        if (expansionEffects.process(msg, now) || impactEffects.process(msg, now)) return
        val start = toVector3(msg.position)
        val end = toVector3(msg.destination)
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
                    gravity = 0f,
                    sizeMin = 0.28f,
                    sizeMax = 0.75f,
                    lifetimeMinMs = 700,
                    lifetimeMaxMs = 1400,
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
                    speedMax = 0f,
                    spread = 0f,
                    gravity = 0f,
                    sizeMin = 0.22f,
                    sizeMax = 0.55f,
                    lifetimeMinMs = 10000,
                    lifetimeMaxMs = 10000,
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
                    spawnTimeMs = currentTimeMsProvider(),
                    durationMs = 110,
                    radius = 0.8f,
                    alpha = 0.35f,
                )
            }
        }
    }

    private fun handleSplashMessage(msg: SplashTEMessage) {
        impactEffects.process(msg, currentTimeMsProvider())
        if (msg.style == Defines.TE_WELDING_SPARKS) {
            val position = toVector3(msg.position)
            spawnLegacyExplosionEffect(
                type = ExplosionType.FLASH,
                modelPath = "models/objects/flash/tris.md2",
                position = position,
                firstFrame = 0,
                frameCount = 2,
                light = ExplosionLight((100 + Globals.rnd.nextInt(75)).toFloat(), Vector3f(1f, 1f, .3f)),
            )
        }
    }

    private fun handlePointMessage(msg: PointTEMessage) {
        val now = currentTimeMsProvider()
        if (shellEffects.process(msg, now)) return
        pointParticles.process(msg, now)
        impactEffects.process(msg, now)
        val position = toVector3(msg.position)
        when (msg.style) {
            Defines.TE_EXPLOSION2,
            Defines.TE_GRENADE_EXPLOSION,
            Defines.TE_GRENADE_EXPLOSION_WATER -> {
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
                spawnExplosionSpriteEffect(
                    type = ExplosionType.POLY,
                    spritePath = "sprites/s_bfg2.sp2",
                    position = position,
                    firstFrame = 0,
                    frameCount = 4,
                    startsTranslucent = true,
                    light = ExplosionLight(350f, Vector3f(0f, 1f, 0f)),
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
                spawnExplosionModel(
                    modelPath = "models/objects/r_explode/tris.md2",
                    position = position,
                    firstFrame = if (Globals.rnd.nextFloat() < 0.5f) 15 else 0,
                    frameCount = 15,
                )
                playEffectSound("sound/weapons/rocklx1a.wav", position)
            }

            Defines.TE_CHAINFIST_SMOKE -> {
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
            startsTranslucent = skinIndex != 0,
            fullBright = skinIndex != 0,
            light = ExplosionLight(150f, when (skinIndex) {
                0 -> Vector3f(1f, 1f, 0f)
                1 -> Vector3f(0f, 1f, 0f)
                else -> Vector3f(.19f, .41f, .75f)
            }),
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
            light = ExplosionLight(350f, Vector3f(1f, .5f, .5f)),
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
        light: ExplosionLight? = null,
    ) {
        val md2 = assetCatalog.getModel(modelPath)
        if (md2 == null) {
            if (light != null) activeEffects += ExplosionEffect(type, currentTimeMsProvider(), 100, firstFrame,
                frameCount, position, startsTranslucent, skinIndex, light)
            return
        }
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
            spawnTimeMs = currentTimeMsProvider(),
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
            light = light,
        )
    }

    private fun spawnExplosionSpriteEffect(
        type: ExplosionType,
        spritePath: String,
        position: Vector3,
        firstFrame: Int,
        frameCount: Int,
        startsTranslucent: Boolean = false,
        light: ExplosionLight? = null,
    ) {
        val sprite = assetCatalog.getSprite(spritePath)
        if (sprite == null) {
            if (light != null) activeEffects += ExplosionEffect(type, currentTimeMsProvider(), 100, firstFrame,
                frameCount, position, startsTranslucent, light = light)
            return
        }
        activeEffects += ExplosionSpriteEffect(
            spriteRenderer = spriteRenderer,
            cameraProvider = cameraProvider,
            sprite = sprite,
            type = type,
            spawnTimeMs = currentTimeMsProvider(),
            frameDurationMs = 100,
            firstFrame = firstFrame,
            frameCount = frameCount,
            position = Vector3(position),
            startsTranslucent = startsTranslucent,
            light = light,
        )
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
            spawnTimeMs = currentTimeMsProvider(),
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
            spawnTimeMs = currentTimeMsProvider(),
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
        audioSystem.play(
            SoundPlaybackRequest(
                sound = sound,
                baseVolume = volume,
                attenuation = attenuation,
                origin = origin,
            )
        )
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
            currentTimeMs = currentTimeMsProvider(),
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

    private fun emitBfgBigExplosionParticles(origin: Vector3) {
        emitPaletteOmniBurst(
            origin = origin,
            count = 256,
            colorIndexProvider = { 0xD0 + Globals.rnd.nextInt(8) },
            fallbackColor = Color(0.22f, 1f, 0.35f, 1f),
            speedMin = 20f,
            speedMax = 220f,
            spread = 0.15f,
            gravity = -80f,
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
            val spiralColorIndex = RAIL_TRAIL_SPIRAL_COLOR_BASE + Globals.rnd.nextInt(RAIL_TRAIL_SPIRAL_COLOR_VARIANTS)
            particleSystem.emitBurst(
                origin = spawnOrigin,
                direction = floatArrayOf(radial.x, radial.y, radial.z),
                count = 1,
                color = resolvePaletteColor(spiralColorIndex, fallback = RAIL_TRAIL_SPIRAL_COLOR_FALLBACK),
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
            val coreColorIndex = RAIL_TRAIL_CORE_COLOR_BASE + Globals.rnd.nextInt(RAIL_TRAIL_CORE_COLOR_VARIANTS)
            val randomDir = randomUnitDirection()
            particleSystem.emitBurst(
                origin = spawnOrigin,
                direction = floatArrayOf(randomDir.x, randomDir.y, randomDir.z),
                count = 1,
                color = resolvePaletteColor(coreColorIndex, fallback = RAIL_TRAIL_CORE_COLOR_FALLBACK),
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
            gravity = -80f,
            sizeMin = 0.34f,
            sizeMax = 1.15f,
            lifetimeMinMs = 300,
            lifetimeMaxMs = 460,
            originJitter = 18f,
        )
    }

    private fun emitItemRespawnParticles(origin: Vector3) {
        emitPaletteOmniBurst(
            origin = origin,
            count = 64,
            colorIndexProvider = { 0xD4 + Globals.rnd.nextInt(4) },
            fallbackColor = Color(0.45f, 0.95f, 0.45f, 1f),
            speedMin = 0f,
            speedMax = 14f,
            spread = 1f,
            gravity = -64f,
            sizeMin = 0.28f,
            sizeMax = 0.82f,
            lifetimeMinMs = 900,
            lifetimeMaxMs = 1300,
            originJitter = 8f,
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

    private fun emitLoginLogoutRespawnParticles(origin: Vector3, weaponType: Int) {
        val (baseColor, fallbackColor) = when (weaponType) {
            Defines.MZ_LOGIN -> LOGIN_EVENT_COLOR_BASE to LOGIN_EVENT_COLOR_FALLBACK
            Defines.MZ_LOGOUT -> LOGOUT_EVENT_COLOR_BASE to LOGOUT_EVENT_COLOR_FALLBACK
            else -> RESPAWN_EVENT_COLOR_BASE to RESPAWN_EVENT_COLOR_FALLBACK
        }

        repeat(LOGIN_LOGOUT_RESPAWN_PARTICLE_COUNT) {
            val velocityX = randomRange(-LOGIN_LOGOUT_RESPAWN_VELOCITY_RANGE, LOGIN_LOGOUT_RESPAWN_VELOCITY_RANGE)
            val velocityY = randomRange(-LOGIN_LOGOUT_RESPAWN_VELOCITY_RANGE, LOGIN_LOGOUT_RESPAWN_VELOCITY_RANGE)
            val velocityZ = randomRange(-LOGIN_LOGOUT_RESPAWN_VELOCITY_RANGE, LOGIN_LOGOUT_RESPAWN_VELOCITY_RANGE)
            val speed = sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ).coerceAtLeast(0.001f)

            val spawnOrigin = Vector3(
                origin.x + randomRange(-LOGIN_LOGOUT_RESPAWN_XY_JITTER, LOGIN_LOGOUT_RESPAWN_XY_JITTER),
                origin.y + randomRange(-LOGIN_LOGOUT_RESPAWN_XY_JITTER, LOGIN_LOGOUT_RESPAWN_XY_JITTER),
                origin.z + randomRange(LOGIN_LOGOUT_RESPAWN_Z_JITTER_MIN, LOGIN_LOGOUT_RESPAWN_Z_JITTER_MAX),
            )

            particleSystem.emitBurst(
                origin = spawnOrigin,
                direction = floatArrayOf(velocityX, velocityY, velocityZ),
                count = 1,
                color = resolvePaletteColor(baseColor + Globals.rnd.nextInt(8), fallback = fallbackColor),
                speedMin = speed,
                speedMax = speed,
                spread = 0f,
                gravity = LOGIN_LOGOUT_RESPAWN_GRAVITY,
                startAlpha = 1f,
                endAlpha = 0f,
                sizeMin = LOGIN_LOGOUT_RESPAWN_SIZE_MIN,
                sizeMax = LOGIN_LOGOUT_RESPAWN_SIZE_MAX,
                lifetimeMinMs = LOGIN_LOGOUT_RESPAWN_LIFETIME_MIN_MS,
                lifetimeMaxMs = LOGIN_LOGOUT_RESPAWN_LIFETIME_MAX_MS,
            )
        }
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

    private fun toVector3(value: Vector3f): Vector3 = Vector3(value.x, value.y, value.z)

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
private const val RAIL_TRAIL_SPIRAL_COLOR_BASE = 0x74
private const val RAIL_TRAIL_SPIRAL_COLOR_VARIANTS = 8
private val RAIL_TRAIL_SPIRAL_COLOR_FALLBACK = Color(0.22f, 0.52f, 1f, 1f)

private const val RAIL_TRAIL_CORE_STEP = 0.75f
private const val RAIL_TRAIL_CORE_JITTER = 3f
private const val RAIL_TRAIL_CORE_SPEED_MAX = 3f
private const val RAIL_TRAIL_CORE_SIZE_MIN = 0.18f
private const val RAIL_TRAIL_CORE_SIZE_MAX = 0.34f
private const val RAIL_TRAIL_CORE_LIFETIME_MIN_MS = 600
private const val RAIL_TRAIL_CORE_LIFETIME_MAX_MS = 820
private const val RAIL_TRAIL_CORE_COLOR_BASE = 0x00
private const val RAIL_TRAIL_CORE_COLOR_VARIANTS = 16
private val RAIL_TRAIL_CORE_COLOR_FALLBACK = Color(0.94f, 0.94f, 0.94f, 1f)

private val HEATBEAM_PARTICLE_FALLBACK = Color(1f, .7f, .2f, 1f)
private val STEAM_PARTICLE_FALLBACK = Color(.75f, .75f, .75f, 1f)

private val IMPACT_PARTICLE_COLOR_FALLBACK = Color(0.95f, 0.9f, 0.65f, 1f)
private val EXPLOSION_PARTICLE_FALLBACK = Color(1f, 0.52f, 0.22f, 1f)

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

private const val LOGIN_LOGOUT_RESPAWN_PARTICLE_COUNT = 500
private const val LOGIN_LOGOUT_RESPAWN_XY_JITTER = 16f
private const val LOGIN_LOGOUT_RESPAWN_Z_JITTER_MIN = -24f
private const val LOGIN_LOGOUT_RESPAWN_Z_JITTER_MAX = 32f
private const val LOGIN_LOGOUT_RESPAWN_VELOCITY_RANGE = 20f
private const val LOGIN_LOGOUT_RESPAWN_GRAVITY = -40f
private const val LOGIN_LOGOUT_RESPAWN_SIZE_MIN = 0.22f
private const val LOGIN_LOGOUT_RESPAWN_SIZE_MAX = 0.58f
private const val LOGIN_LOGOUT_RESPAWN_LIFETIME_MIN_MS = 1000
private const val LOGIN_LOGOUT_RESPAWN_LIFETIME_MAX_MS = 1300

private const val LOGIN_EVENT_COLOR_BASE = 0xD0
private const val LOGOUT_EVENT_COLOR_BASE = 0x40
private const val RESPAWN_EVENT_COLOR_BASE = 0xE0
private val LOGIN_EVENT_COLOR_FALLBACK = Color(0.35f, 0.92f, 0.45f, 1f)
private val LOGOUT_EVENT_COLOR_FALLBACK = Color(0.95f, 0.25f, 0.2f, 1f)
private val RESPAWN_EVENT_COLOR_FALLBACK = Color(1f, 0.9f, 0.3f, 1f)
