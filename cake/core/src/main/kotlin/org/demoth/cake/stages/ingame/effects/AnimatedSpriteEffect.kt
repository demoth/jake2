package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.math.Vector3
import org.demoth.cake.ClientEntity
import org.demoth.cake.assets.Sp2Asset
import org.demoth.cake.assets.Sp2Renderer
import kotlin.math.floor

/**
 * Time-bound `.sp2` sprite effect rendered as a camera-facing billboard.
 *
 * Behavior:
 * - Advances frame by fixed [frameDurationMs].
 * - Expires after `frameCount * frameDurationMs`.
 * - Reuses [ClientEntity] sprite fields consumed by [Sp2Renderer].
 *
 * Constraints:
 * - `frameCount > 0`, `frameDurationMs > 0`, and non-empty sprite frames are required.
 * - Position is static for the effect lifetime (no motion interpolation).
 */
class AnimatedSpriteEffect(
    private val spriteRenderer: Sp2Renderer,
    private val cameraProvider: () -> Camera,
    private val sprite: Sp2Asset,
    private val spawnTimeMs: Int,
    private val frameDurationMs: Int,
    private val firstFrame: Int,
    private val frameCount: Int,
    position: Vector3,
    renderFx: Int,
    alpha: Float,
) : ClientTransientEffect {
    private val entity = ClientEntity("sp2_effect").apply {
        spriteAsset = sprite
        prev.origin = floatArrayOf(position.x, position.y, position.z)
        current.origin = floatArrayOf(position.x, position.y, position.z)
        resolvedFrame = firstFrame
        resolvedRenderFx = renderFx
        this.alpha = alpha
    }

    override fun update(nowMs: Int, deltaSeconds: Float): Boolean {
        if (frameCount <= 0 || frameDurationMs <= 0 || sprite.frames.isEmpty()) {
            return false
        }
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
        entity.resolvedFrame = firstFrame + frameIndex
        return true
    }

    override fun render(modelBatch: ModelBatch) {
        spriteRenderer.render(modelBatch, entity, cameraProvider(), 1f)
    }
}
