package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

/**
 * Short-lived beam segment rendered as procedural cylinder geometry.
 */
class LineBeamEffect(
    start: Vector3,
    end: Vector3,
    color: Color,
    private val spawnTimeMs: Int,
    private val durationMs: Int,
    radius: Float = 0.75f,
    alpha: Float = 1f,
) : ClientTransientEffect {
    private val model: Model
    private val instance: ModelInstance

    init {
        val direction = Vector3(end).sub(start)
        val length = direction.len().coerceAtLeast(0.001f)
        direction.scl(1f / length)

        val materialAttributes = mutableListOf(
            ColorAttribute.createDiffuse(color),
            ColorAttribute(ColorAttribute.Emissive, color.r, color.g, color.b, 1f),
            DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false),
            IntAttribute.createCullFace(GL20.GL_NONE),
        )
        if (alpha < 1f) {
            materialAttributes += BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha)
        }

        val material = Material(*materialAttributes.toTypedArray())
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        modelBuilder.part(
            "beam",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            material,
        ).cylinder(2f, 1f, 2f, 6, 0f, 360f)
        model = modelBuilder.end()
        instance = ModelInstance(model)

        val midpoint = Vector3(start).add(end).scl(0.5f)
        val rotation = Quaternion().setFromCross(Vector3.Y, direction)
        val scale = Vector3(radius, length, radius)
        instance.transform.set(midpoint, rotation, scale)
    }

    override fun update(nowMs: Int, deltaSeconds: Float): Boolean {
        return nowMs - spawnTimeMs < durationMs
    }

    override fun render(modelBatch: ModelBatch) {
        modelBatch.render(instance)
    }

    override fun dispose() {
        model.dispose()
    }
}
