package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Defines
import org.demoth.cake.ClientEntity
import org.demoth.cake.applyModelOpacity
import org.demoth.cake.assets.md2.Md2CustomData
import org.demoth.cake.assets.Sp2Asset
import org.demoth.cake.assets.Sp2Renderer

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

internal class ExplosionMd2Effect(
    private val modelInstance: ModelInstance,
    type: ExplosionType, spawnTimeMs: Int, frameDurationMs: Int, firstFrame: Int, frameCount: Int,
    position: Vector3,
    private val pitchDeg: Float = 0f,
    private val yawDeg: Float = 0f,
    private val rollDeg: Float = 0f,
    baseSkinIndex: Int? = null,
    private val fullBright: Boolean = false,
    startsTranslucent: Boolean = false,
    light: ExplosionLight? = null,
) : ExplosionEffect(type, spawnTimeMs, frameDurationMs, firstFrame, frameCount, position, startsTranslucent, baseSkinIndex, light) {
    override fun update(nowMs: Int, deltaSeconds: Float): Boolean {
        if (!super.update(nowMs, deltaSeconds)) return false
        val frame = checkNotNull(frame)
        (modelInstance.userData as? Md2CustomData)?.let { userData ->
            userData.frame1 = frame.previousFrame
            userData.frame2 = frame.currentFrame
            userData.interpolation = frame.interpolation
            frame.skin?.let { userData.skinIndex = it }
            if (fullBright) {
                // Fullbright path intentionally bypasses directional alias shading.
                userData.lightRed = 1f; userData.lightGreen = 1f; userData.lightBlue = 1f
                userData.shadeVectorX = 0f; userData.shadeVectorY = 0f; userData.shadeVectorZ = 0f
            }
        }
        modelInstance.transform.idt()
        if (yawDeg != 0f) modelInstance.transform.rotate(Vector3.Z, yawDeg)
        if (pitchDeg != 0f) modelInstance.transform.rotate(Vector3.Y, pitchDeg)
        if (rollDeg != 0f) modelInstance.transform.rotate(Vector3.X, -rollDeg)
        modelInstance.transform.setTranslation(position)

        // Alpha affects opaque explosions only once they become translucent.
        // Yamagi's alias renderer additionally scales alpha by 0.666; libGDX materials use the animation alpha directly.
        applyModelOpacity(modelInstance, if (frame.translucent) frame.alpha else 1f, forceTranslucent = frame.translucent)
        return true
    }

    override fun render(modelBatch: ModelBatch) { modelBatch.render(modelInstance) }
}

/**
 * Legacy explosion-type state machine for sprite-backed (`.sp2`) temp entities.
 *
 * Cross-reference:
 * - Jake2: `client/CL_tent.AddExplosions` (`ex_poly` on `sprites/s_bfg2.sp2`).
 * - Yamagi: `cl_tempentities.c` (`CL_AddExplosions`).
 */
internal class ExplosionSpriteEffect(
    private val spriteRenderer: Sp2Renderer,
    private val cameraProvider: () -> Camera,
    private val sprite: Sp2Asset,
    type: ExplosionType, spawnTimeMs: Int, frameDurationMs: Int, firstFrame: Int, frameCount: Int,
    position: Vector3,
    startsTranslucent: Boolean = false,
    light: ExplosionLight? = null,
) : ExplosionEffect(type, spawnTimeMs, frameDurationMs, firstFrame, frameCount, position, startsTranslucent, light = light) {
    private val entity = ClientEntity("sp2_explosion").apply {
        spriteAsset = sprite
        prev.origin = floatArrayOf(position.x, position.y, position.z)
        current.origin = floatArrayOf(position.x, position.y, position.z)
        resolvedFrame = firstFrame
        resolvedRenderFx = if (startsTranslucent) Defines.RF_TRANSLUCENT else 0
        alpha = 1f
    }

    override fun update(nowMs: Int, deltaSeconds: Float): Boolean {
        if (!super.update(nowMs, deltaSeconds) || sprite.frames.isEmpty()) return false
        val frame = checkNotNull(frame)
        entity.resolvedFrame = frame.currentFrame
        entity.resolvedRenderFx = if (frame.translucent) Defines.RF_TRANSLUCENT else 0
        entity.alpha = if (frame.translucent) frame.alpha else 1f
        return true
    }

    override fun render(modelBatch: ModelBatch) { spriteRenderer.render(modelBatch, entity, cameraProvider(), 1f) }
}
