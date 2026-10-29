package org.demoth.cake.assets

import com.badlogic.gdx.assets.AssetManager
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
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.math.Vector3f
import org.demoth.cake.ClientEntity

/**
 * Isolated renderer for RF_BEAM entities and temporary palette lasers. Keeps beam-specific caches
 * and temporary vectors out of [org.demoth.cake.stages.ingame.Game3dScreen].
 * Shared libGDX cylinder models are owned here and disposed with the screen, rather than per laser.
 */
class BeamRenderer internal constructor(
    private val paletteColor: (Int) -> Color,
    private val createModel: (Material) -> Model,
) : Disposable {
    constructor(assetManager: AssetManager) : this(
        paletteColor = { index ->
            val palette = assetManager.get("q2palette.bin", Any::class.java) as IntArray
            // RGBA8888 palette bytes yield the same RGB as the reference d_8to24table beam path.
            Color(palette[index and 255]).apply { a = 1f }
        },
        createModel = ::createCylinder,
    )

    private data class Style(val paletteIndex: Int, val alpha: Float)
    /** Reusable model and instance for one palette/opacity style. */
    private data class BeamRenderable(val model: Model, val instance: ModelInstance)
    private val beamRenderables = mutableMapOf<Style, BeamRenderable>()
    private val direction = Vector3()
    private val midpoint = Vector3()
    private val scale = Vector3()
    private val rotation = Quaternion()

    /**
     * Beam semantics from network entity state:
     * origin/old_origin are endpoints, frame is diameter, and skinnum packs four palette colors.
     * These are the legacy RF_BEAM conventions consumed by CL_ents.AddPacketEntities.
     */
    fun render(modelBatch: ModelBatch, entity: ClientEntity) {
        val state = entity.current
        val colorShift = Globals.rnd.nextInt(4) * 8
        val paletteIndex = (state.skinnum ushr colorShift) and 255
        val alpha = if (state.renderfx and Defines.RF_TRANSLUCENT != 0) .3f else 1f
        prepare(state.origin[0], state.origin[1], state.origin[2], state.old_origin[0], state.old_origin[1], state.old_origin[2],
            paletteIndex, state.frame.coerceAtLeast(1).toFloat(), alpha)?.let { modelBatch.render(it) }
    }

    fun render(modelBatch: ModelBatch, start: Vector3f, end: Vector3f, paletteIndex: Int, diameter: Float, alpha: Float) {
        prepare(start, end, paletteIndex, diameter, alpha)?.let { modelBatch.render(it) }
    }

    /** Returned instances are borrowed: ModelBatch snapshots their transform when submitted. */
    internal fun prepare(start: Vector3f, end: Vector3f, paletteIndex: Int, diameter: Float, alpha: Float): ModelInstance? =
        prepare(start.x, start.y, start.z, end.x, end.y, end.z, paletteIndex, diameter, alpha)

    private fun prepare(sx: Float, sy: Float, sz: Float, ex: Float, ey: Float, ez: Float,
                        paletteIndex: Int, diameter: Float, alpha: Float): ModelInstance? {
        direction.set(ex - sx, ey - sy, ez - sz)
        val length = direction.len()
        if (length <= .001f || diameter <= 0f || alpha <= 0f) return null
        direction.scl(1f / length)
        rotation.setFromCross(Vector3.Y, direction)
        midpoint.set((sx + ex) * .5f, (sy + ey) * .5f, (sz + ez) * .5f)
        scale.set(diameter * .5f, length, diameter * .5f)
        val renderable = beamRenderables.getOrPut(Style(paletteIndex and 255, alpha.coerceAtMost(1f))) {
            val color = paletteColor(paletteIndex and 255)
            val material = Material(ColorAttribute.createDiffuse(color),
                ColorAttribute(ColorAttribute.Emissive, color.r, color.g, color.b, 1f),
                DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false), IntAttribute.createCullFace(GL20.GL_NONE))
            if (alpha < 1f) material.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha))
            val model = createModel(material)
            BeamRenderable(model, ModelInstance(model))
        }
        renderable.instance.transform.set(midpoint, rotation, scale)
        return renderable.instance
    }

    override fun dispose() {
        beamRenderables.values.forEach { it.model.dispose() }
        beamRenderables.clear()
    }

    companion object {
        /** Unit-radius, unit-height Y cylinder, transformed per draw using libGDX geometry and matrices. */
        @Suppress("DEPRECATION")
        private fun createCylinder(material: Material): Model = ModelBuilder().apply {
            begin()
            part("beam", GL20.GL_TRIANGLES, (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(), material)
                .cylinder(2f, 1f, 2f, 6, 0f, 360f)
        }.end()
    }
}
