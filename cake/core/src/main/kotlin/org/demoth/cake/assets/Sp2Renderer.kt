package org.demoth.cake.assets

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Defines
import org.demoth.cake.ClientEntity

/**
 * Draws `.sp2` entities as camera-facing textured quads.
 *
 * Geometry/material are cached per (texture, translucent) style.
 */
class Sp2Renderer : Disposable {
    private val right = Vector3()
    private val up = Vector3()
    private val forward = Vector3()
    private val translation = Vector3()
    private val transformValues = FloatArray(16)

    private val renderables = mutableMapOf<RenderKey, Renderable>()

    fun render(modelBatch: ModelBatch, entity: ClientEntity, camera: Camera, lerpFraction: Float) {
        val sp2Asset = entity.spriteAsset ?: return
        if (sp2Asset.frames.isEmpty()) {
            return
        }

        val frameIndex = Math.floorMod(entity.current.frame, sp2Asset.frames.size)
        val frame = sp2Asset.frames[frameIndex]

        val x = entity.prev.origin[0] + (entity.current.origin[0] - entity.prev.origin[0]) * lerpFraction
        val y = entity.prev.origin[1] + (entity.current.origin[1] - entity.prev.origin[1]) * lerpFraction
        val z = entity.prev.origin[2] + (entity.current.origin[2] - entity.prev.origin[2]) * lerpFraction

        forward.set(camera.direction).nor()
        up.set(camera.up).nor()
        right.set(forward).crs(up).nor()

        // Match legacy sprite anchor semantics:
        // entity origin corresponds to (origin_x, origin_y) point inside the frame.
        translation.set(x, y, z)
            .mulAdd(right, -frame.originX.toFloat())
            .mulAdd(up, -frame.originY.toFloat())

        val translucent = (entity.current.renderfx and Defines.RF_TRANSLUCENT) != 0
        val renderable = getOrCreateRenderable(frame.texture, translucent)
        transformValues[Matrix4.M00] = right.x * frame.width
        transformValues[Matrix4.M10] = right.y * frame.width
        transformValues[Matrix4.M20] = right.z * frame.width
        transformValues[Matrix4.M30] = 0f
        transformValues[Matrix4.M01] = up.x * frame.height
        transformValues[Matrix4.M11] = up.y * frame.height
        transformValues[Matrix4.M21] = up.z * frame.height
        transformValues[Matrix4.M31] = 0f
        transformValues[Matrix4.M02] = forward.x
        transformValues[Matrix4.M12] = forward.y
        transformValues[Matrix4.M22] = forward.z
        transformValues[Matrix4.M32] = 0f
        transformValues[Matrix4.M03] = translation.x
        transformValues[Matrix4.M13] = translation.y
        transformValues[Matrix4.M23] = translation.z
        transformValues[Matrix4.M33] = 1f
        renderable.instance.transform.set(transformValues)
        if (renderable.blending != null) {
            renderable.blending.opacity = 1f
        }

        modelBatch.render(renderable.instance)
    }

    override fun dispose() {
        renderables.values.forEach { it.model.dispose() }
        renderables.clear()
    }

    private fun getOrCreateRenderable(texture: Texture, translucent: Boolean): Renderable {
        val key = RenderKey(texture, translucent)
        return renderables.getOrPut(key) {
            val materialAttributes = mutableListOf(
                TextureAttribute.createDiffuse(texture),
                IntAttribute.createCullFace(GL20.GL_NONE),
                DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, !translucent),
            )
            if (translucent) {
                materialAttributes += BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f)
            } else {
                materialAttributes += FloatAttribute.createAlphaTest(0.5f)
            }
            val blending = materialAttributes.filterIsInstance<BlendingAttribute>().firstOrNull()
            val material = Material(*materialAttributes.toTypedArray())
            val model = buildUnitQuad(material)
            Renderable(
                model = model,
                instance = ModelInstance(model),
                blending = blending,
            )
        }
    }

    private fun buildUnitQuad(material: Material): Model {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val part = modelBuilder.part(
            "sp2_quad",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )
        val v0 = VertexInfo().setPos(0f, 0f, 0f).setUV(0f, 1f)
        val v1 = VertexInfo().setPos(0f, 1f, 0f).setUV(0f, 0f)
        val v2 = VertexInfo().setPos(1f, 1f, 0f).setUV(1f, 0f)
        val v3 = VertexInfo().setPos(1f, 0f, 0f).setUV(1f, 1f)
        part.rect(v0, v1, v2, v3)
        return modelBuilder.end()
    }

    private data class RenderKey(
        val texture: Texture,
        val translucent: Boolean,
    )

    private data class Renderable(
        val model: Model,
        val instance: ModelInstance,
        val blending: BlendingAttribute?,
    )
}
