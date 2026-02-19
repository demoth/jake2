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
}
