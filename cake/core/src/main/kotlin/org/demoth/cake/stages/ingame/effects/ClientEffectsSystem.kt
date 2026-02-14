package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.assets.AssetManager
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
import org.demoth.cake.createModelInstance
import org.demoth.cake.stages.ingame.ClientEntityManager

/**
 * Owns client-side transient effects produced by server effect messages.
 *
 * Scope:
 * - TEMessage hierarchy
 * - MuzzleFlash2Message
 *
 * Non-goals:
 * - replicated world/entity state (owned by [ClientEntityManager])
 */
class ClientEffectsSystem(
    assetManager: AssetManager,
    private val entityManager: ClientEntityManager,
    private val listenerPositionProvider: () -> Vector3,
) : Disposable {
    private val assetCatalog = EffectAssetCatalog(assetManager)
    private val activeEffects = mutableListOf<ClientTransientEffect>()

    fun precache() {
        assetCatalog.precache()
    }

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

        playRandomSound(profile.soundPaths, muzzleOrigin, profile.attenuation)
    }

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
        val iterator = activeEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            if (!effect.update(now, deltaSeconds)) {
                effect.dispose()
                iterator.remove()
            }
        }
    }

    fun render(modelBatch: ModelBatch) {
        activeEffects.forEach { effect ->
            effect.render(modelBatch)
        }
    }

    override fun dispose() {
        activeEffects.forEach { it.dispose() }
        activeEffects.clear()
        assetCatalog.dispose()
    }

    private fun handlePointDirectionMessage(msg: PointDirectionTEMessage) {
        val position = toVector3(msg.position) ?: return
        when (msg.style) {
            Defines.TE_GUNSHOT,
            Defines.TE_BULLET_SPARKS -> {
                spawnSmokeAndFlash(position)
                playRandomSound(RICHOCHET_SOUNDS, position)
            }

            Defines.TE_SCREEN_SPARKS,
            Defines.TE_SHIELD_SPARKS,
            Defines.TE_HEATBEAM_SPARKS,
            Defines.TE_HEATBEAM_STEAM,
            Defines.TE_ELECTRIC_SPARKS -> {
                playEffectSound("sound/weapons/lashit.wav", position)
            }

            Defines.TE_SHOTGUN -> {
                spawnSmokeAndFlash(position)
            }

            Defines.TE_BLASTER -> {
                spawnBlasterImpact(position, msg.direction, skinIndex = 0)
                playEffectSound("sound/weapons/lashit.wav", position)
            }

            Defines.TE_BLASTER2 -> {
                spawnBlasterImpact(position, msg.direction, skinIndex = 1)
                playEffectSound("sound/weapons/lashit.wav", position)
            }

            Defines.TE_FLECHETTE -> {
                spawnBlasterImpact(position, msg.direction, skinIndex = 2)
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
            }
        }
    }

    private fun handlePointMessage(msg: PointTEMessage) {
        val position = toVector3(msg.position) ?: return
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
                spawnExplosionModel(
                    modelPath = "models/objects/r_explode/tris.md2",
                    position = position,
                    firstFrame = 0,
                    frameCount = 4,
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
        spawnAnimatedModelEffect(
            modelPath = "models/objects/smoke/tris.md2",
            position = origin,
            firstFrame = 0,
            frameCount = 4,
            frameDurationMs = 100,
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
    ) {
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
