package org.demoth.cake.stages.ingame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import jake2.qcommon.Defines
import org.demoth.cake.assets.BspInlineModelRenderData
import org.demoth.cake.assets.BspLightStyleContributionRecord
import org.demoth.cake.assets.BspLightmapTextureAttribute
import java.util.IdentityHashMap
import kotlin.math.floor

internal const val LEGACY_ALPHA_TRANS33 = 85f / 255f
internal const val LEGACY_ALPHA_TRANS66 = 170f / 255f

/**
 * Resolves classic Quake2 translucent surface alpha from `SURF_TRANS*` flags.
 *
 * Reference parity:
 * - Yamagi GL3 `GL3_DrawAlphaSurfaces`: `0.333f` for `SURF_TRANS33`, `0.666f` for `SURF_TRANS66`.
 * - Q2PRO `color_for_surface`: `85/255` and `170/255` alpha values.
 */
internal fun legacySurfaceOpacity(textureFlags: Int): Float =
    when {
        (textureFlags and Defines.SURF_TRANS33) != 0 -> LEGACY_ALPHA_TRANS33
        (textureFlags and Defines.SURF_TRANS66) != 0 -> LEGACY_ALPHA_TRANS66
        else -> 1f
    }

private data class SurfaceMaterialBinding(
    val meshPartId: String,
    val textureFlags: Int,
    val lightMapStyles: ByteArray,
    val primaryLightStyleIndex: Int?,
    val lightStyleContributions: List<BspLightStyleContributionRecord>,
)

/**
 * Applies BSP surface material effects for inline brush models (`*1`, `*2`, ...).
 */
class BspInlineSurfaceMaterialController(
    inlineRenderData: List<BspInlineModelRenderData>,
) {
    private val translucentModelIndices = inlineRenderData
        .filter { model -> model.parts.any { part -> isBspSurfaceTranslucent(part.textureFlags) } }
        .map { it.modelIndex }
        .toSet()

    private val bindingsByModelIndex = inlineRenderData.associate { model ->
        model.modelIndex to model.parts.map { part ->
            SurfaceMaterialBinding(
                meshPartId = part.meshPartId,
                textureFlags = part.textureFlags,
                lightMapStyles = part.lightMapStyles,
                primaryLightStyleIndex = part.primaryLightStyleIndex,
                lightStyleContributions = part.lightStyleContributions,
            )
        }
    }

    private val modelNodePartsCache = IdentityHashMap<ModelInstance, Map<String, NodePart>>()

    /**
     * Returns true when the inline model contains at least one translucent BSP surface
     * (`SURF_TRANS33` or `SURF_TRANS66`).
     *
     * Used by render-pass classification: brush models with translucent parts are rendered in alpha pass
     * regardless of entity `RF_TRANSLUCENT`.
     */
    fun hasTranslucentParts(inlineModelIndex: Int): Boolean = inlineModelIndex in translucentModelIndices

    /**
     * Updates material state for one inline brush model instance.
     */
    fun update(
        modelInstance: ModelInstance,
        inlineModelIndex: Int,
        currentTimeMs: Int,
        lightStyleResolver: (Int) -> Float,
    ) {
        val bindings = bindingsByModelIndex[inlineModelIndex] ?: return
        val nodePartsById = modelNodePartsCache.getOrPut(modelInstance) {
            collectNodePartsById(modelInstance)
        }
        applySurfaceMaterialState(
            bindings = bindings,
            nodePartsById = nodePartsById,
            currentTimeMs = currentTimeMs,
            lightStyleResolver = lightStyleResolver,
        )
    }
}

private fun applySurfaceMaterialState(
    bindings: List<SurfaceMaterialBinding>,
    nodePartsById: Map<String, NodePart>,
    currentTimeMs: Int,
    lightStyleResolver: (Int) -> Float,
) {
    bindings.forEach { binding ->
        val nodePart = nodePartsById[binding.meshPartId] ?: return@forEach
        applySurfaceTransparency(nodePart, binding.textureFlags)
        applySurfaceFlowing(nodePart, binding.textureFlags, currentTimeMs)
        applySurfaceLightstyles(
            nodePart = nodePart,
            lightMapStyles = binding.lightMapStyles,
            primaryLightStyleIndex = binding.primaryLightStyleIndex,
            contributions = binding.lightStyleContributions,
            lightStyleResolver = lightStyleResolver,
        )
    }
}

private fun applySurfaceTransparency(nodePart: NodePart, textureFlags: Int) {
    // Legacy references:
    // - `client/render/fast/Surf.R_RecursiveWorldNode` and `Surf.R_DrawInlineBModel` enqueue
    //   `SURF_TRANS33/SURF_TRANS66` faces into alpha chain.
    // - `client/render/fast/Surf.R_DrawAlphaSurfaces` applies 0.33/0.66 alpha and draws a dedicated pass.
    //
    // Behavior difference:
    // Cake mutates per-surface material state in-place (blend + depthMask=false) instead of maintaining
    // a separate translucent chain pass.
    val trans33 = (textureFlags and Defines.SURF_TRANS33) != 0
    val trans66 = (textureFlags and Defines.SURF_TRANS66) != 0
    if (!trans33 && !trans66) {
        return
    }

    val opacity = legacySurfaceOpacity(textureFlags)
    val blending = nodePart.material.get(BlendingAttribute.Type) as? BlendingAttribute
    if (blending == null) {
        nodePart.material.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, opacity))
    } else {
        blending.sourceFunction = GL20.GL_SRC_ALPHA
        blending.destFunction = GL20.GL_ONE_MINUS_SRC_ALPHA
        blending.opacity = opacity
    }

    val depth = nodePart.material.get(DepthTestAttribute.Type) as? DepthTestAttribute
    if (depth == null) {
        nodePart.material.set(DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false))
    } else {
        depth.depthMask = false
    }
}

internal fun isBspSurfaceTranslucent(textureFlags: Int): Boolean =
    (textureFlags and (Defines.SURF_TRANS33 or Defines.SURF_TRANS66)) != 0

private fun applySurfaceFlowing(nodePart: NodePart, textureFlags: Int, currentTimeMs: Int) {
    // Legacy counterpart: `client/render/fast/Surf.DrawGLFlowingPoly`.
    // The scroll function is intentionally the same time-based sawtooth.
    if ((textureFlags and Defines.SURF_FLOWING) == 0) {
        return
    }
    val diffuse = nodePart.material.get(TextureAttribute.Diffuse) as? TextureAttribute ?: return
    diffuse.offsetU = computeFlowingOffsetU(currentTimeMs)
}

internal fun computeFlowingOffsetU(currentTimeMs: Int): Float {
    val timeSeconds = currentTimeMs / 1000f
    // Legacy fast renderer counterpart (`Surf.DrawGLFlowingPoly`):
    // scroll = -64 * ( (refdef.time / 40.0) - floor(refdef.time / 40.0) )
    var scroll = -64f * ((timeSeconds / 40f) - floor(timeSeconds / 40f))
    if (scroll == 0f) {
        scroll = -64f
    }
    return scroll
}

private fun applySurfaceLightstyles(
    nodePart: NodePart,
    lightMapStyles: ByteArray,
    primaryLightStyleIndex: Int?,
    contributions: List<BspLightStyleContributionRecord>,
    lightStyleResolver: (Int) -> Float,
) {
    // Legacy references:
    // - `client/render/fast/Light.R_BuildLightMap` combines up to 4 style slots per face.
    // - `client/render/fast/Surf.GL_RenderLightmappedPoly` uploads/uses updated lightmaps.
    if (nodePart.material.has(BspLightmapTextureAttribute.Type)) {
        // Encode style slot weights into Diffuse color channels for BspLightmapShader:
        // r/g/b/a => slot 0/1/2/3.
        val (w0, w1, w2, w3) = computeLightmapStyleWeights(
            lightMapStyles = lightMapStyles,
            primaryLightStyleIndex = primaryLightStyleIndex,
            lightStyleResolver = lightStyleResolver,
        )
        val lightmapTint = nodePart.material.get(ColorAttribute.Diffuse) as? ColorAttribute
        if (lightmapTint == null) {
            nodePart.material.set(
                ColorAttribute(
                    ColorAttribute.Diffuse,
                    Color(w0, w1, w2, w3)
                )
            )
        } else {
            lightmapTint.color.set(w0, w1, w2, w3)
        }
        return
    }

    var red = 1f
    var green = 1f
    var blue = 1f

    if (contributions.isNotEmpty()) {
        red = 0f
        green = 0f
        blue = 0f
        contributions.forEach { contribution ->
            val scale = lightStyleResolver(contribution.styleIndex)
            red += contribution.red * scale
            green += contribution.green * scale
            blue += contribution.blue * scale
        }
    }

    val diffuseColor = nodePart.material.get(ColorAttribute.Diffuse) as? ColorAttribute
    if (diffuseColor == null) {
        nodePart.material.set(ColorAttribute(ColorAttribute.Diffuse, Color(red, green, blue, 1f)))
    } else {
        diffuseColor.color.set(red, green, blue, 1f)
    }
}

internal fun computeLightmapStyleWeights(
    lightMapStyles: ByteArray,
    primaryLightStyleIndex: Int?,
    lightStyleResolver: (Int) -> Float,
): FloatArray {
    // Legacy reference:
    // `client/render/fast/Light.R_BuildLightMap` style-slot convention and
    // `qcommon/Defines.MAXLIGHTMAPS` (=4), with 255 terminator in face styles array.
    val styles = lightMapStyles
        .map { it.toInt() and 0xFF }
        .takeWhile { it != 255 }
        .take(4)
    val w0 = styles.getOrNull(0)?.let(lightStyleResolver) ?: primaryLightStyleIndex?.let(lightStyleResolver) ?: 1f
    val w1 = styles.getOrNull(1)?.let(lightStyleResolver) ?: 0f
    val w2 = styles.getOrNull(2)?.let(lightStyleResolver) ?: 0f
    val w3 = styles.getOrNull(3)?.let(lightStyleResolver) ?: 0f
    return floatArrayOf(w0, w1, w2, w3)
}

private fun collectNodePartsById(modelInstance: ModelInstance): Map<String, NodePart> {
    val nodePartsById = HashMap<String, NodePart>()
    modelInstance.nodes.forEach { node ->
        collectNodeParts(node, nodePartsById)
    }
    return nodePartsById
}

private fun collectNodeParts(node: Node, outById: MutableMap<String, NodePart>) {
    node.parts.forEach { part ->
        outById.putIfAbsent(part.meshPart.id, part)
    }
    node.children.forEach { child ->
        collectNodeParts(child, outById)
    }
}
