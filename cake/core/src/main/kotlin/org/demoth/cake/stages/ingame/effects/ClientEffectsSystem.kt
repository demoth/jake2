package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.M_Flash
import jake2.qcommon.network.messages.server.MuzzleFlash2Message
import jake2.qcommon.network.messages.server.TEMessage
import jake2.qcommon.util.Math3D
import org.demoth.cake.GameConfiguration
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
    private val assetManager: AssetManager,
    private val entityManager: ClientEntityManager,
    private val gameConfig: GameConfiguration,
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

        pickRandom(profile.soundPaths)?.let { soundPath ->
            playEffectSound(soundPath, muzzleOrigin, attenuation = profile.attenuation)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun processTempEntityMessage(_msg: TEMessage) {
        // implemented in follow-up commits
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

    private fun spawnAnimatedModelEffect(
        modelPath: String,
        position: Vector3,
        firstFrame: Int,
        frameCount: Int,
        frameDurationMs: Int,
    ) {
        val md2 = assetCatalog.getModel(modelPath) ?: return
        val instance = createModelInstance(md2.model)
        activeEffects += AnimatedModelEffect(
            modelInstance = instance,
            spawnTimeMs = Globals.curtime,
            frameDurationMs = frameDurationMs,
            firstFrame = firstFrame,
            frameCount = frameCount,
            position = Vector3(position),
        )
    }

    private fun playEffectSound(
        soundPath: String,
        origin: Vector3,
        volume: Float = 1f,
        attenuation: Float = Defines.ATTN_NORM.toFloat(),
    ) {
        val sound = assetCatalog.getSound(soundPath) ?: return
        val listener = listenerPositionProvider()
        val gain = (volume * calculateAttenuation(origin, listener, attenuation)).coerceIn(0f, 1f)
        if (gain > 0f) {
            sound.play(gain)
        }
    }

    private fun calculateAttenuation(origin: Vector3, listener: Vector3, attenuation: Float): Float {
        if (attenuation <= 0f) {
            return 1f
        }
        val distance = origin.dst(listener)
        val rolloff = if (attenuation == Defines.ATTN_STATIC.toFloat()) attenuation * 2f else attenuation
        val referenceDistance = 200f
        if (distance <= referenceDistance) {
            return 1f
        }
        return (referenceDistance / (referenceDistance + rolloff * (distance - referenceDistance))).coerceIn(0f, 1f)
    }

    private fun pickRandom(paths: List<String>): String? {
        if (paths.isEmpty()) {
            return null
        }
        return paths[Globals.rnd.nextInt(paths.size)]
    }
}
