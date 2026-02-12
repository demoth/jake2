package org.demoth.cake.stages

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutCoordinateMapperTest {
    @Test
    fun imageCoordinatesConvertFromTopLeftToBottomLeft() {
        val screenHeight = 480

        assertEquals(456, LayoutCoordinateMapper.imageY(idTech2Y = 0, imageHeight = 24, screenHeight = screenHeight))
        assertEquals(0, LayoutCoordinateMapper.imageY(idTech2Y = 456, imageHeight = 24, screenHeight = screenHeight))
    }

    @Test
    fun textCoordinatesConvertFromTopLeftToBottomLeftBaseline() {
        val screenHeight = 480

        assertEquals(480, LayoutCoordinateMapper.textY(idTech2Y = 0, screenHeight = screenHeight))
        assertEquals(24, LayoutCoordinateMapper.textY(idTech2Y = 456, screenHeight = screenHeight))
    }

    @Test
    fun bottomAnchoredLayoutCoordinateRemainsBottomAnchoredAfterTextureOriginTransform() {
        val screenHeight = 600
        val ybOffset = -24
        val idTech2Y = screenHeight + ybOffset // legacy yb semantics

        val gdxY = LayoutCoordinateMapper.imageY(idTech2Y, imageHeight = 24, screenHeight = screenHeight)
        assertEquals(0, gdxY)
    }
}
