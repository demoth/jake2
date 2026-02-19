package org.demoth.cake.stages.ingame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Model
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
import org.demoth.cake.assets.BspWorldRenderData
import java.util.IdentityHashMap
import kotlin.math.floor

private data class SurfaceMaterialBinding(
    val meshPartId: String,
    val textureFlags: Int,
    val lightStyleContributions: List<BspLightStyleContributionRecord>,
)

/**
 * Applies material-side BSP surface effects for world model surfaces:
 * - `SURF_FLOWING` UV scrolling,
 * - `SURF_TRANS33` / `SURF_TRANS66` blending state,
 * - lightstyle-driven baked lightmap modulation.
 */
class BspWorldSurfaceMaterialController(
    worldRenderData: BspWorldRenderData,
    modelInstance: ModelInstance,
) {
    private val bindings = worldRenderData.surfaces.map { surface ->
        SurfaceMaterialBinding(
            meshPartId = surface.meshPartId,
            textureFlags = surface.textureFlags,
            lightStyleContributions = surface.lightStyleContributions,
        )
    }
    private val nodePartsById = collectNodePartsById(modelInstance)

    fun update(currentTimeMs: Int, lightStyleResolver: (Int) -> Float) {
        applySurfaceMaterialState(
            bindings = bindings,
            nodePartsById = nodePartsById,
            currentTimeMs = currentTimeMs,
            lightStyleResolver = lightStyleResolver,
        )
    }
}

/**
 * Applies the same BSP surface material effects for inline brush models (`*1`, `*2`, ...).
 */
class BspInlineSurfaceMaterialController(
    inlineRenderData: List<BspInlineModelRenderData>,
) {
    private val bindingsByModelIndex = inlineRenderData.associate { model ->
        model.modelIndex to model.parts.map { part ->
            SurfaceMaterialBinding(
                meshPartId = part.meshPartId,
                textureFlags = part.textureFlags,
                lightStyleContributions = part.lightStyleContributions,
            )
        }
    }

    private val modelNodePartsCache = IdentityHashMap<Model, Map<String, NodePart>>()

    fun update(
        modelInstance: ModelInstance,
        inlineModelIndex: Int,
        currentTimeMs: Int,
        lightStyleResolver: (Int) -> Float,
    ) {
        val bindings = bindingsByModelIndex[inlineModelIndex] ?: return
        val nodePartsById = modelNodePartsCache.getOrPut(modelInstance.model) {
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
        applySurfaceLightstyles(nodePart, binding.lightStyleContributions, lightStyleResolver)
    }
}

private fun applySurfaceTransparency(nodePart: NodePart, textureFlags: Int) {
    val trans33 = (textureFlags and Defines.SURF_TRANS33) != 0
    val trans66 = (textureFlags and Defines.SURF_TRANS66) != 0
    if (!trans33 && !trans66) {
        return
    }

    val opacity = if (trans33) 0.33f else 0.66f
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

private fun applySurfaceFlowing(nodePart: NodePart, textureFlags: Int, currentTimeMs: Int) {
    if ((textureFlags and Defines.SURF_FLOWING) == 0) {
        return
    }
    val diffuse = nodePart.material.get(TextureAttribute.Diffuse) as? TextureAttribute ?: return
    val textureWidth = diffuse.textureDescription.texture.width.coerceAtLeast(1).toFloat()
    diffuse.offsetU = computeFlowingOffsetU(currentTimeMs, textureWidth)
}

private fun computeFlowingOffsetU(currentTimeMs: Int, textureWidth: Float): Float {
    val timeSeconds = currentTimeMs / 1000f
    var scroll = -64f * ((timeSeconds / 40f) - floor(timeSeconds / 40f))
    if (scroll == 0f) {
        scroll = -64f
    }
    return scroll / textureWidth
}

private fun applySurfaceLightstyles(
    nodePart: NodePart,
    contributions: List<BspLightStyleContributionRecord>,
    lightStyleResolver: (Int) -> Float,
) {
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
