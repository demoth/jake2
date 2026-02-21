package org.demoth.cake.stages.ingame

import jake2.qcommon.Defines
import org.demoth.cake.assets.BspInlineModelPartRecord
import org.demoth.cake.assets.BspInlineModelRenderData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun isBspSurfaceTranslucentMatchesSurfFlags() {
        assertTrue(isBspSurfaceTranslucent(Defines.SURF_TRANS33))
        assertTrue(isBspSurfaceTranslucent(Defines.SURF_TRANS66))
        assertTrue(isBspSurfaceTranslucent(Defines.SURF_FLOWING or Defines.SURF_TRANS33))
        assertFalse(isBspSurfaceTranslucent(Defines.SURF_FLOWING))
    }

    @Test
    fun inlineControllerTracksModelIndicesWithTranslucentParts() {
        val controller = BspInlineSurfaceMaterialController(
            inlineRenderData = listOf(
                BspInlineModelRenderData(
                    modelIndex = 1,
                    parts = listOf(
                        BspInlineModelPartRecord(
                            modelIndex = 1,
                            meshPartId = "inline_1_part_0",
                            textureInfoIndex = 10,
                            textureName = "e1u1/wndow0_3",
                            textureFlags = Defines.SURF_TRANS33,
                            textureAnimationNext = 0,
                        )
                    )
                ),
                BspInlineModelRenderData(
                    modelIndex = 2,
                    parts = listOf(
                        BspInlineModelPartRecord(
                            modelIndex = 2,
                            meshPartId = "inline_2_part_0",
                            textureInfoIndex = 20,
                            textureName = "e1u1/wall0",
                            textureFlags = 0,
                            textureAnimationNext = 0,
                        )
                    )
                ),
            )
        )

        assertTrue(controller.hasTranslucentParts(1))
        assertFalse(controller.hasTranslucentParts(2))
        assertFalse(controller.hasTranslucentParts(99))
    }
}
