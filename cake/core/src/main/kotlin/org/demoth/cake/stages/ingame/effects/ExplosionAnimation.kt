package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g2d.Animation

internal data class ExplosionFrame(val previousFrame: Int, val currentFrame: Int, val interpolation: Float,
                                   val alpha: Float, val skin: Int?, val translucent: Boolean)

/** Shared MD2/sprite timing and presentation rules from CL_AddExplosions, using libGDX frame selection. */
internal class ExplosionAnimation(
    private val type: ExplosionType,
    private val spawnTimeMs: Int,
    private val frameDurationMs: Int,
    private val firstFrame: Int,
    private val frameCount: Int,
    private val startsTranslucent: Boolean = false,
    private val baseSkin: Int? = null,
) {
    init { require(frameDurationMs > 0 && frameCount > 0) }
    // Use frame units to avoid .1-second float rounding delaying exact millisecond frame boundaries.
    private val animation = Animation(1f, *Array(frameCount) { it })

    fun sample(nowMs: Int): ExplosionFrame? {
        val age = (nowMs.toLong() - spawnTimeMs).coerceAtLeast(0)
        val index = animation.getKeyFrameIndex(age.toFloat() / frameDurationMs)
        if (index >= if (type == ExplosionType.FLASH) 1 else frameCount - 1) return null
        val fraction = (age % frameDurationMs).toFloat() / frameDurationMs
        val alpha = when (type) {
            ExplosionType.MISC -> 1f - (index + fraction) / (frameCount - 1)
            ExplosionType.FLASH, ExplosionType.MFLASH -> 1f
            ExplosionType.POLY -> (16f - index) / 16f
            ExplosionType.POLY2 -> (5f - index) / 5f
        }.coerceIn(0f, 1f)
        val skin = when (type) {
            ExplosionType.POLY -> if (index < 10) index / 2 else if (index < 13) 5 else 6
            ExplosionType.POLY2 -> 0
            else -> baseSkin
        }
        val translucent = startsTranslucent || type == ExplosionType.POLY2 || (type == ExplosionType.POLY && index >= 10)
        return ExplosionFrame(firstFrame + index, firstFrame + (index + 1).coerceAtMost(frameCount - 1),
            fraction, alpha, skin, translucent)
    }
}
