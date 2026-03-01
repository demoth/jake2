package org.demoth.cake.stages.ingame

import org.demoth.cake.assets.BspWorldRenderData

/**
 * Computes per-surface world visibility from BSP PVS/areabits metadata.
 *
 * Legacy counterpart:
 * `client/render/fast/Surf.R_MarkLeaves` + `R_RecursiveWorldNode` (PVS + areabits gates).
 */
internal fun computeVisibleSurfaceMask(
    worldRenderData: BspWorldRenderData,
    pvsBits: ByteArray,
    areaBits: ByteArray,
): BooleanArray {
    val visibleSurfaceMask = BooleanArray(worldRenderData.surfaces.size)
    val applyAreaBits = areaBits.any { it.toInt() != 0 }

    worldRenderData.leaves.forEach { leaf ->
        if (leaf.cluster < 0 || !isBitSet(pvsBits, leaf.cluster)) {
            return@forEach
        }
        if (applyAreaBits && !isBitSet(areaBits, leaf.area)) {
            return@forEach
        }
        leaf.surfaceIndices.forEach { surfaceIndex ->
            if (surfaceIndex in visibleSurfaceMask.indices) {
                visibleSurfaceMask[surfaceIndex] = true
            }
        }
    }
    return visibleSurfaceMask
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
