package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import org.demoth.cake.ClientEntity
import org.demoth.cake.applyModelOpacity
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.assets.Sp2Asset
import org.demoth.cake.assets.Sp2Renderer
import kotlin.math.floor

/**
 * Legacy/Yamagi temp-explosion renderer behavior with enum-based type semantics.
 *
 * Cross-reference:
 * - Jake2: `client/CL_tent.AddExplosions` switch over `explosion_t.type`.
 * - Yamagi: `cl_tempentities.c` switch over `explosion_t.type`.
 */
enum class ExplosionType {
    MISC,
    FLASH,
    MFLASH,
    POLY,
    POLY2,
}

class ExplosionMd2Effect(
    private val modelInstance: ModelInstance,
    private val type: ExplosionType,
    private val spawnTimeMs: Int,
    private val frameDurationMs: Int,
    private val firstFrame: Int,
    private val frameCount: Int,
    private val position: Vector3,
    private val pitchDeg: Float = 0f,
    private val yawDeg: Float = 0f,
    private val rollDeg: Float = 0f,
    private val baseSkinIndex: Int? = null,
    private val fullBright: Boolean = false,
    private val startsTranslucent: Boolean = false,
) : ClientTransientEffect {
    override fun update(nowMs: Int, deltaSeconds: Float): Boolean {
        val elapsedMs = nowMs - spawnTimeMs
        if (elapsedMs < 0) {
            return true
        }

        val durationMs = frameDurationMs.coerceAtLeast(1)
        val frameFloat = elapsedMs.toFloat() / durationMs.toFloat()
        val frameIndex = floor(frameFloat).toInt()
        val nextFrameIndex = (frameIndex + 1).coerceAtMost(frameCount - 1)
        val interpolation = (frameFloat - floor(frameFloat)).coerceIn(0f, 1f)

        var skinIndex = baseSkinIndex
        var forceTranslucent = startsTranslucent
        val rawAlpha = when (type) {
            ExplosionType.MISC -> {
                if (frameIndex >= frameCount - 1) {
                    return false
                }
                val denom = (frameCount - 1).coerceAtLeast(1).toFloat()
                1f - (frameFloat / denom)
            }

            ExplosionType.FLASH -> {
                if (frameIndex >= 1) {
                    return false
                }
                1f
            }

            ExplosionType.MFLASH -> {
                if (frameIndex >= frameCount - 1) {
                    return false
                }
                1f
            }

            ExplosionType.POLY -> {
                if (frameIndex >= frameCount - 1) {
                    return false
                }
                val alpha = (16f - frameIndex.toFloat()) / 16f
                if (frameIndex < 10) {
                    skinIndex = (frameIndex shr 1).coerceAtLeast(0)
                } else {
                    forceTranslucent = true
                    skinIndex = if (frameIndex < 13) 5 else 6
                }
                alpha
            }

            ExplosionType.POLY2 -> {
                if (frameIndex >= frameCount - 1) {
                    return false
                }
                forceTranslucent = true
                skinIndex = 0
                (5f - frameIndex.toFloat()) / 5f
            }
        }.coerceIn(0f, 1f)

        (modelInstance.userData as? Md2CustomData)?.let { userData ->
            userData.frame1 = firstFrame + frameIndex.coerceAtMost(frameCount - 1)
            userData.frame2 = firstFrame + nextFrameIndex
            userData.interpolation = interpolation
            if (skinIndex != null) {
                userData.skinIndex = skinIndex
            }
            if (fullBright) {
                // Fullbright path intentionally bypasses directional alias shading.
                userData.lightRed = 1f
                userData.lightGreen = 1f
                userData.lightBlue = 1f
                userData.shadeVectorX = 0f
                userData.shadeVectorY = 0f
                userData.shadeVectorZ = 0f
            }
        }

        modelInstance.transform.idt()
        if (yawDeg != 0f) {
            modelInstance.transform.rotate(Vector3.Z, yawDeg)
        }
        if (pitchDeg != 0f) {
            modelInstance.transform.rotate(Vector3.Y, pitchDeg)
        }
        if (rollDeg != 0f) {
            modelInstance.transform.rotate(Vector3.X, -rollDeg)
        }
        modelInstance.transform.setTranslation(position)

        // Alpha only affects legacy opaque explosions after they become translucent.
        // Yamagi GL3 alias path scales translucent model alpha by 0.666.
        val effectiveAlpha = if (forceTranslucent) rawAlpha * 0.666f else 1f
        applyModelOpacity(modelInstance, effectiveAlpha.coerceIn(0f, 1f), forceTranslucent = forceTranslucent)
        return true
    }

    override fun render(modelBatch: ModelBatch) {
        modelBatch.render(modelInstance)
    }
}

/**
 * Legacy explosion-type state machine for sprite-backed (`.sp2`) temp entities.
 *
 * Cross-reference:
 * - Jake2: `client/CL_tent.AddExplosions` (`ex_poly` on `sprites/s_bfg2.sp2`).
 * - Yamagi: `cl_tempentities.c` (`CL_AddExplosions`).
 */
class ExplosionSpriteEffect(
    private val spriteRenderer: Sp2Renderer,
    private val cameraProvider: () -> Camera,
    private val sprite: Sp2Asset,
    private val type: ExplosionType,
    private val spawnTimeMs: Int,
    private val frameDurationMs: Int,
    private val firstFrame: Int,
    private val frameCount: Int,
    position: Vector3,
    private val startsTranslucent: Boolean = false,
) : ClientTransientEffect {
    private val entity = ClientEntity("sp2_explosion").apply {
        spriteAsset = sprite
        prev.origin = floatArrayOf(position.x, position.y, position.z)
        current.origin = floatArrayOf(position.x, position.y, position.z)
        resolvedFrame = firstFrame
        resolvedRenderFx = if (startsTranslucent) Defines.RF_TRANSLUCENT else 0
        alpha = 1f
    }

    override fun update(nowMs: Int, deltaSeconds: Float): Boolean {
        if (frameCount <= 0 || frameDurationMs <= 0 || sprite.frames.isEmpty()) {
            return false
        }
        val elapsedMs = nowMs - spawnTimeMs
        if (elapsedMs < 0) {
            return true
        }

        val frameFloat = elapsedMs.toFloat() / frameDurationMs.toFloat()
        val frameIndex = floor(frameFloat).toInt()

        var forceTranslucent = startsTranslucent
        val rawAlpha = when (type) {
            ExplosionType.MISC -> {
                if (frameIndex >= frameCount - 1) {
                    return false
                }
                val denom = (frameCount - 1).coerceAtLeast(1).toFloat()
                1f - (frameFloat / denom)
            }

            ExplosionType.FLASH -> {
                if (frameIndex >= 1) {
                    return false
                }
                1f
            }

            ExplosionType.MFLASH -> {
                if (frameIndex >= frameCount - 1) {
                    return false
                }
                1f
            }

            ExplosionType.POLY -> {
                if (frameIndex >= frameCount - 1) {
                    return false
                }
                if (frameIndex >= 10) {
                    forceTranslucent = true
                }
                (16f - frameIndex.toFloat()) / 16f
            }

            ExplosionType.POLY2 -> {
                if (frameIndex >= frameCount - 1) {
                    return false
                }
                forceTranslucent = true
                (5f - frameIndex.toFloat()) / 5f
            }
        }.coerceIn(0f, 1f)

        entity.resolvedFrame = firstFrame + frameIndex.coerceAtMost(frameCount - 1)
        entity.resolvedRenderFx = if (forceTranslucent) Defines.RF_TRANSLUCENT else 0
        entity.alpha = if (forceTranslucent) rawAlpha else 1f
        return true
    }

    override fun render(modelBatch: ModelBatch) {
        spriteRenderer.render(modelBatch, entity, cameraProvider(), 1f)
    }
}
