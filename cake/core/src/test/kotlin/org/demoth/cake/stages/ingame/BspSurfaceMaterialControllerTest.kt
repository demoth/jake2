package org.demoth.cake.stages.ingame

import org.junit.Assert.assertEquals
import org.junit.Test

class BspSurfaceMaterialControllerTest {

    @Test
    fun computeFlowingOffsetUMatchesLegacyFastRendererEquation() {
        assertEquals(-64f, computeFlowingOffsetU(0), 0.0001f)
        assertEquals(-1.6f, computeFlowingOffsetU(1_000), 0.0001f)
        assertEquals(-32f, computeFlowingOffsetU(20_000), 0.0001f)
        assertEquals(-64f, computeFlowingOffsetU(40_000), 0.0001f)
    }

    @Test
    fun computeLightmapStyleWeightsUsesUpToFourStyleSlots() {
        val weights = computeLightmapStyleWeights(
            lightMapStyles = byteArrayOf(5, 7, 9, (-1).toByte()),
            primaryLightStyleIndex = 5,
            lightStyleResolver = { style ->
                when (style) {
                    5 -> 0.25f
                    7 -> 0.5f
                    9 -> 1.5f
                    else -> 0f
                }
            },
        )

        assertEquals(0.25f, weights[0], 0.0001f)
        assertEquals(0.5f, weights[1], 0.0001f)
        assertEquals(1.5f, weights[2], 0.0001f)
        assertEquals(0f, weights[3], 0.0001f)
    }
}
