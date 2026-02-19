package org.demoth.cake.stages.ingame

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.CM
import org.demoth.cake.assets.BspWorldRenderData

/**
 * Controls per-surface visibility for the world model (BSP model 0).
 *
 * Inputs:
 * - decoded world topology ([BspWorldRenderData]) with leaf -> surface mapping.
 * - collision model PVS queries ([CM_PointLeafnum], [CM_LeafCluster], [CM_ClusterPVS]).
 * - server-provided areabits from the current client frame.
 *
 * Legacy counterpart:
 * `client/render/fast/Surf.R_MarkLeaves` + `R_RecursiveWorldNode` (PVS + areabits gates).
 */
class BspWorldVisibilityController(
    private val worldRenderData: BspWorldRenderData,
    private val modelInstance: ModelInstance,
    private val collisionModel: CM,
) {
    private val surfaceNodeParts: Array<NodePart?> = Array(worldRenderData.surfaces.size) { null }
    private val visibleSurfaceScratch = BooleanArray(worldRenderData.surfaces.size)
    private val pointScratch = FloatArray(3)

    init {
        indexWorldSurfaceNodeParts()
        // Start visible to avoid a blank frame before first visibility update.
        setAllVisible()
    }

    fun update(viewOrigin: Vector3, areaBits: ByteArray) {
        if (surfaceNodeParts.isEmpty()) {
            return
        }
        pointScratch[0] = viewOrigin.x
        pointScratch[1] = viewOrigin.y
        pointScratch[2] = viewOrigin.z

        val viewLeaf = collisionModel.CM_PointLeafnum(pointScratch)
        if (viewLeaf < 0) {
            setAllVisible()
            return
        }
        val viewCluster = collisionModel.CM_LeafCluster(viewLeaf)
        if (viewCluster < 0) {
            setAllVisible()
            return
        }
        val pvsBits = collisionModel.CM_ClusterPVS(viewCluster)
        if (pvsBits.isEmpty()) {
            setAllVisible()
            return
        }

        visibleSurfaceScratch.fill(false)
        val applyAreaBits = areaBits.any { it.toInt() != 0 }
        worldRenderData.leaves.forEach { leaf ->
            if (leaf.cluster < 0 || !isBitSet(pvsBits, leaf.cluster)) {
                return@forEach
            }
            if (applyAreaBits && !isBitSet(areaBits, leaf.area)) {
                return@forEach
            }
            leaf.surfaceIndices.forEach { surfaceIndex ->
                if (surfaceIndex in visibleSurfaceScratch.indices) {
                    visibleSurfaceScratch[surfaceIndex] = true
                }
            }
        }
        applyVisibleMask()
    }

    private fun setAllVisible() {
        surfaceNodeParts.forEach { part -> part?.enabled = true }
    }

    private fun applyVisibleMask() {
        visibleSurfaceScratch.forEachIndexed { index, visible ->
            surfaceNodeParts[index]?.enabled = visible
        }
    }

    private fun indexWorldSurfaceNodeParts() {
        val nodePartsById = HashMap<String, NodePart>()
        modelInstance.nodes.forEach { node ->
            collectNodeParts(node, nodePartsById)
        }
        worldRenderData.surfaces.forEachIndexed { surfaceIndex, surface ->
            surfaceNodeParts[surfaceIndex] = nodePartsById[surface.meshPartId]
        }
    }

    private fun collectNodeParts(node: Node, outById: MutableMap<String, NodePart>) {
        node.parts.forEach { part ->
            outById.putIfAbsent(part.meshPart.id, part)
        }
        node.children.forEach { child ->
            collectNodeParts(child, outById)
        }
    }
}

private fun isBitSet(bits: ByteArray, bitIndex: Int): Boolean {
    if (bitIndex < 0) {
        return false
    }
    val byteIndex = bitIndex ushr 3
    if (byteIndex !in bits.indices) {
        return false
    }
    return (bits[byteIndex].toInt() and (1 shl (bitIndex and 7))) != 0
}
