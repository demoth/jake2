package org.demoth.cake.stages.ingame

import com.badlogic.gdx.math.Vector3
import jake2.qcommon.CM
import org.demoth.cake.assets.BspLightStyleContributionRecord
import org.demoth.cake.assets.BspWorldRenderData

/**
 * Approximate world-light sampling for alias/sprite entities.
 *
 * Why this exists:
 * Cake's BSP shader applies per-texel lightmaps on brush surfaces, but MD2 entities need a CPU-side
 * light probe similar to Quake2's `R_LightPoint`/`GL3_LightPoint`.
 *
 * Current strategy:
 * - map world leaf -> averaged per-style light contributions derived from BSP face lightmaps,
 * - evaluate `CS_LIGHTS` style values at render time,
 * - add dynamic-light contribution from [DynamicLightSystem].
 *
 * This intentionally trades precision for a stable and cheap per-entity light color.
 */
class BspEntityLightSampler(
    worldRenderData: BspWorldRenderData,
    private val collisionModel: CM,
) {
    private val leafContributions: Array<LeafStyleContributions> = Array(worldRenderData.leaves.size) { leafIndex ->
        buildLeafContributions(worldRenderData, leafIndex)
    }
    private val pointScratch = FloatArray(3)

    /**
     * Samples lighting for one world-space point.
     */
    fun sample(
        worldPoint: Vector3,
        styleResolver: (Int) -> Float,
        dynamicLightSystem: DynamicLightSystem?,
    ): Vector3 {
        pointScratch[0] = worldPoint.x
        pointScratch[1] = worldPoint.y
        pointScratch[2] = worldPoint.z

        val leafIndex = collisionModel.CM_PointLeafnum(pointScratch)
        val sampled = if (leafIndex in leafContributions.indices) {
            leafContributions[leafIndex].evaluate(styleResolver)
        } else {
            Vector3()
        }

        if (sampled.isZero(0.001f)) {
            // keep entities readable when the point has no usable baked-light samples.
            sampled.set(FALLBACK_MIN_LIGHT, FALLBACK_MIN_LIGHT, FALLBACK_MIN_LIGHT)
        }

        dynamicLightSystem?.sampleAt(worldPoint)?.let { dynamicContribution ->
            sampled.add(dynamicContribution)
        }
        return sampled
    }

    private fun buildLeafContributions(
        worldRenderData: BspWorldRenderData,
        leafIndex: Int,
    ): LeafStyleContributions {
        val leaf = worldRenderData.leaves[leafIndex]
        val byStyle = LinkedHashMap<Int, AccumulatedStyleLight>()
        leaf.surfaceIndices.forEach { surfaceIndex ->
            val contributions = worldRenderData.surfaces
                .getOrNull(surfaceIndex)
                ?.lightStyleContributions
                .orEmpty()
            contributions.forEach { contribution ->
                val acc = byStyle.getOrPut(contribution.styleIndex) { AccumulatedStyleLight() }
                acc.add(contribution)
            }
        }
        if (byStyle.isEmpty()) {
            return LeafStyleContributions.EMPTY
        }
        val styleIndices = IntArray(byStyle.size)
        val reds = FloatArray(byStyle.size)
        val greens = FloatArray(byStyle.size)
        val blues = FloatArray(byStyle.size)
        byStyle.entries.forEachIndexed { index, entry ->
            styleIndices[index] = entry.key
            reds[index] = entry.value.redAverage()
            greens[index] = entry.value.greenAverage()
            blues[index] = entry.value.blueAverage()
        }
        return LeafStyleContributions(styleIndices, reds, greens, blues)
    }

    private class AccumulatedStyleLight {
        var red: Float = 0f
        var green: Float = 0f
        var blue: Float = 0f
        var count: Int = 0

        fun add(contribution: BspLightStyleContributionRecord) {
            red += contribution.red
            green += contribution.green
            blue += contribution.blue
            count++
        }

        fun redAverage(): Float = if (count > 0) red / count else 0f
        fun greenAverage(): Float = if (count > 0) green / count else 0f
        fun blueAverage(): Float = if (count > 0) blue / count else 0f
    }

    private class LeafStyleContributions(
        private val styleIndices: IntArray,
        private val reds: FloatArray,
        private val greens: FloatArray,
        private val blues: FloatArray,
    ) {
        fun evaluate(styleResolver: (Int) -> Float): Vector3 {
            val sampled = Vector3()
            styleIndices.indices.forEach { index ->
                val styleWeight = styleResolver(styleIndices[index])
                sampled.x += reds[index] * styleWeight
                sampled.y += greens[index] * styleWeight
                sampled.z += blues[index] * styleWeight
            }
            return sampled
        }

        companion object {
            val EMPTY = LeafStyleContributions(IntArray(0), FloatArray(0), FloatArray(0), FloatArray(0))
        }
    }

    companion object {
        private const val FALLBACK_MIN_LIGHT = 0.1f
    }
}
