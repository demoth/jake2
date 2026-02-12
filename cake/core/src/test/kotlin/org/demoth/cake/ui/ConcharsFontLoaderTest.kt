package org.demoth.cake.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ConcharsFontLoaderTest {
    @Test
    fun mapsAllGlyphCellsInRowMajorOrder() {
        val cells = ConcharsFontLoader.mapCells(128, 128)

        assertEquals(256, cells.size)
        assertEquals(0, cells.first().x)
        assertEquals(0, cells.first().y)
        assertEquals(8, cells.first().width)
        assertEquals(8, cells.first().height)

        assertEquals(120, cells[15].x)
        assertEquals(0, cells[15].y)

        assertEquals(0, cells[16].x)
        assertEquals(8, cells[16].y)

        assertEquals(120, cells.last().x)
        assertEquals(120, cells.last().y)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonGridMultipleAtlasSize() {
        ConcharsFontLoader.mapCells(130, 128)
    }
}
