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
import org.demoth.cake.ClientEntity
import kotlin.collections.plusAssign

/**
 * Isolated renderer for RF_BEAM entities. Keeps beam-specific caches and temporary vectors out
 * of [org.demoth.cake.stages.ingame.Game3dScreen].
 */
class BeamRenderer(
    private val assetManager: AssetManager
) : Disposable {
    // Cached by (paletteIndex + translucency) to avoid rebuilding identical beam meshes each frame.
    private val beamRenderables = mutableMapOf<Int, BeamRenderable>()
    // Quake 2 global palette in RGBA8888 format, loaded from q2palette.bin.
    private val q2Palette: IntArray by lazy { assetManager.get("q2palette.bin", Any::class.java) as IntArray }
    // Temporary vectors/quaternion reused each frame to avoid allocations in beam rendering.
    private val beamStart = Vector3()
    private val beamEnd = Vector3()
    private val beamDirection = Vector3()
    private val beamMidpoint = Vector3()
    private val beamScale = Vector3()
    private val beamRotation = Quaternion()

    /**
     * Renders a Quake-style beam entity (RF_BEAM) as procedural cylinder geometry.
     *
     * Beam semantics are taken from network entity state:
     * - start point = `origin`
     * - end point = `old_origin`
     * - diameter = `frame`
     * - color bytes = packed in `skinnum` (legacy beam color hack)
     */
    fun render(modelBatch: ModelBatch, entity: ClientEntity) {
        val state = entity.current
        beamStart.set(state.origin[0], state.origin[1], state.origin[2])
        beamEnd.set(state.old_origin[0], state.old_origin[1], state.old_origin[2])
        beamDirection.set(beamEnd).sub(beamStart)
        val length = beamDirection.len()
        if (length <= 0.001f) {
            return
        }

        beamDirection.scl(1f / length)
        beamRotation.setFromCross(Vector3.Y, beamDirection)

        // In Quake 2, RF_BEAM frame stores the beam diameter.
        val radius = state.frame.coerceAtLeast(1) * 0.5f
        beamMidpoint.set(beamStart).add(beamEnd).scl(0.5f)
        beamScale.set(radius, length, radius)

        // select one of 4 packed beam color bytes at random each draw.
        val colorShift = Globals.rnd.nextInt(4) * 8
        val paletteIndex = (state.skinnum ushr colorShift) and 0xFF
        val translucent = (state.renderfx and Defines.RF_TRANSLUCENT) != 0
        val beamRenderable = getOrCreateBeamRenderable(paletteIndex, translucent)

        beamRenderable.instance.transform.set(beamMidpoint, beamRotation, beamScale)
        modelBatch.render(beamRenderable.instance)
    }

    override fun dispose() {
        beamRenderables.values.forEach { it.model.dispose() }
        beamRenderables.clear()
    }

    /**
     * Returns a cached beam renderable for a color/translucency pair, creating it on first use.
     *
     * The base mesh is a unit cylinder aligned with Y axis:
     * radius=1, height=1.
     * It is transformed per-entity in [render].
     */
    private fun getOrCreateBeamRenderable(paletteIndex: Int, translucent: Boolean): BeamRenderable {
        // pack translucency in bit 8, palette index in bits [0..7]
        val key = paletteIndex or (if (translucent) 1 shl 8 else 0)
        return beamRenderables.getOrPut(key) {
            val rgba8888 = q2Palette[paletteIndex]
            // Match legacy d_8to24table RGB extraction layout used by beam renderers.
            val legacyD8To24 =
                ((rgba8888 ushr 24) and 0xFF) or
                    (((rgba8888 ushr 16) and 0xFF) shl 8) or
                    (((rgba8888 ushr 8) and 0xFF) shl 16)
            val color = Color(
                (legacyD8To24 and 0xFF) / 255f,
                ((legacyD8To24 ushr 8) and 0xFF) / 255f,
                ((legacyD8To24 ushr 16) and 0xFF) / 255f,
                1f
            )
            val alpha = if (translucent) 0.3f else 1f
            val materialAttributes = mutableListOf(
                ColorAttribute.createDiffuse(color),
                ColorAttribute(ColorAttribute.Emissive, color.r, color.g, color.b, 1f),
                DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false),
                IntAttribute.createCullFace(GL20.GL_NONE),
            )
            if (translucent) {
                materialAttributes += BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha)
            }
            val material = Material(*materialAttributes.toTypedArray())
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()
            modelBuilder.part(
                "beam",
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
                material
            ).cylinder(2f, 1f, 2f, 6, 0f, 360f)
            val model = modelBuilder.end()
            BeamRenderable(model, ModelInstance(model))
        }
    }

    /**
     * Pair of reusable model + instance used to render beams of one visual style.
     */
    private data class BeamRenderable(
        val model: Model,
        val instance: ModelInstance
    )
}
