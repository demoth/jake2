package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import org.demoth.cake.assets.Md2CustomData
import kotlin.math.floor

/**
 * Time-bound effect rendered from a model instance, optionally animated using MD2 frame interpolation.
 */
class AnimatedModelEffect(
    private val modelInstance: ModelInstance,
    private val spawnTimeMs: Int,
    private val frameDurationMs: Int,
    private val firstFrame: Int,
    private val frameCount: Int,
    private val position: Vector3,
    private val yawDeg: Float = 0f,
) : ClientTransientEffect {
    override fun update(nowMs: Int, deltaSeconds: Float): Boolean {
        val elapsedMs = nowMs - spawnTimeMs
        if (elapsedMs < 0) {
            return true
        }

        val maxDurationMs = frameDurationMs * frameCount
        if (elapsedMs >= maxDurationMs) {
            return false
        }

        val frameFloat = elapsedMs.toFloat() / frameDurationMs.toFloat()
        val frameIndex = floor(frameFloat).toInt().coerceIn(0, frameCount - 1)
        val nextFrameIndex = (frameIndex + 1).coerceAtMost(frameCount - 1)
        val interpolation = (frameFloat - floor(frameFloat)).coerceIn(0f, 1f)

        (modelInstance.userData as? Md2CustomData)?.let { userData ->
            userData.frame1 = firstFrame + frameIndex
            userData.frame2 = firstFrame + nextFrameIndex
            userData.interpolation = interpolation
        }

        modelInstance.transform.idt()
        if (yawDeg != 0f) {
            modelInstance.transform.rotate(Vector3.Z, yawDeg)
        }
        modelInstance.transform.setTranslation(position)
        return true
    }

    override fun render(modelBatch: ModelBatch) {
        modelBatch.render(modelInstance)
    }
}
