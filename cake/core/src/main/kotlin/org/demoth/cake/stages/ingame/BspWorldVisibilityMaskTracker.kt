package org.demoth.cake.stages.ingame

import com.badlogic.gdx.math.Vector3
import jake2.qcommon.CM
import org.demoth.cake.assets.BspWorldRenderData

/**
 * Batch-mode world visibility tracker that computes only a surface visibility mask.
 *
 * Unlike [BspWorldVisibilityController], this tracker does not mutate world `NodePart.enabled`
 * state and is safe to use once world rendering no longer goes through legacy per-face model parts.
 */
class BspWorldVisibilityMaskTracker(
    private val worldRenderData: BspWorldRenderData,
    private val collisionModel: CM,
) {
    private val visibleSurfaceScratch = BooleanArray(worldRenderData.surfaces.size)
    private val pointScratch = FloatArray(3)

    init {
        visibleSurfaceScratch.fill(true)
    }

    fun update(viewOrigin: Vector3, areaBits: ByteArray) {
        if (visibleSurfaceScratch.isEmpty()) {
            return
        }
        pointScratch[0] = viewOrigin.x
        pointScratch[1] = viewOrigin.y
        pointScratch[2] = viewOrigin.z

        val viewLeaf = collisionModel.CM_PointLeafnum(pointScratch)
        if (viewLeaf < 0) {
            visibleSurfaceScratch.fill(true)
            return
        }
        val viewCluster = collisionModel.CM_LeafCluster(viewLeaf)
        if (viewCluster < 0) {
            visibleSurfaceScratch.fill(true)
            return
        }
        val pvsBits = collisionModel.CM_ClusterPVS(viewCluster)
        if (pvsBits.isEmpty()) {
            visibleSurfaceScratch.fill(true)
            return
        }

        val visibleMask = computeVisibleSurfaceMask(worldRenderData, pvsBits, areaBits)
        visibleSurfaceScratch.fill(false)
        val count = minOf(visibleSurfaceScratch.size, visibleMask.size)
        repeat(count) { index ->
            visibleSurfaceScratch[index] = visibleMask[index]
        }
    }

    fun visibleSurfaceMaskSnapshot(): BooleanArray = visibleSurfaceScratch.copyOf()
}
