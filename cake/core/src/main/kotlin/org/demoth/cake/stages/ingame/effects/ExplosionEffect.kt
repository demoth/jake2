package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.math.Vector3f
import org.demoth.cake.stages.ingame.DynamicLightSystem

internal data class ExplosionLight(val radius: Float, val color: Vector3f)

/** Shared animation/light owner; also preserves an explosion's light when optional visual assets are absent. */
internal open class ExplosionEffect(
    type: ExplosionType, spawnTimeMs: Int, frameDurationMs: Int, firstFrame: Int, frameCount: Int,
    position: Vector3, startsTranslucent: Boolean = false, baseSkin: Int? = null,
    private val light: ExplosionLight? = null,
) : ClientTransientEffect {
    protected val position = Vector3(position)
    private val animation = ExplosionAnimation(type, spawnTimeMs, frameDurationMs, firstFrame, frameCount, startsTranslucent, baseSkin)
    protected var frame: ExplosionFrame? = null
        private set

    override fun update(nowMs: Int, deltaSeconds: Float): Boolean {
        frame = animation.sample(nowMs)
        return frame != null
    }

    override fun collectDynamicLights(lights: DynamicLightSystem) {
        val light = light ?: return
        val frame = frame ?: return
        lights.addFrameLight(position, light.radius * frame.alpha, light.color.x, light.color.y, light.color.z)
    }

    override fun render(modelBatch: ModelBatch) = Unit
}
