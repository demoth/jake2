package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import org.demoth.cake.applyModelOpacity
import org.demoth.cake.assets.Md2CustomData
import kotlin.math.floor

/**
 * Legacy-style `ex_poly` explosion effect.
 *
 * Cross-reference:
 * - Jake2: `client/CL_tent.AddExplosions` (`ex_poly` branch).
 * - Yamagi: `cl_tempentities.c` (`CL_AddTEnts`, `ex_poly` branch).
 *
 * Behavior parity:
 * - frame progression by 100 ms ticks,
 * - skin progression by frame index (`0..4`, then `5/6`),
 * - translucent only on late frames (`f >= 10`) with alpha ramp.
 */
class ExplosionPolyEffect(
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

        val frameFloat = elapsedMs.toFloat() / frameDurationMs.toFloat()
        val frameIndex = floor(frameFloat).toInt()
        if (frameIndex >= frameCount - 1) {
            return false
        }
        val nextFrameIndex = (frameIndex + 1).coerceAtMost(frameCount - 1)
        val interpolation = (frameFloat - floor(frameFloat)).coerceIn(0f, 1f)

        (modelInstance.userData as? Md2CustomData)?.let { userData ->
            userData.frame1 = firstFrame + frameIndex
            userData.frame2 = firstFrame + nextFrameIndex
            userData.interpolation = interpolation
            userData.skinIndex = if (frameIndex < 10) {
                (frameIndex shr 1).coerceAtLeast(0)
            } else {
                if (frameIndex < 13) 5 else 6
            }
        }

        val translucent = frameIndex >= 10
        // GL3 alias path scales translucent alpha by 0.666.
        val alpha = if (translucent) ((16f - frameIndex.toFloat()) / 16f) * 0.666f else 1f

        modelInstance.transform.idt()
        if (yawDeg != 0f) {
            modelInstance.transform.rotate(Vector3.Z, yawDeg)
        }
        modelInstance.transform.setTranslation(position)
        applyModelOpacity(modelInstance, alpha, forceTranslucent = translucent)
        return true
    }

    override fun render(modelBatch: ModelBatch) {
        modelBatch.render(modelInstance)
    }
}

