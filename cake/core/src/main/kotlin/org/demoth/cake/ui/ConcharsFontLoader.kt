package org.demoth.cake.ui

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array

object ConcharsFontLoader {
    const val GRID_SIZE = 16
    const val GLYPH_COUNT = GRID_SIZE * GRID_SIZE
    const val CELL_SIZE_PX = 16

    data class AtlasCell(
        val index: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    /**
     * Maps 256 glyph cells in row-major order on a fixed 16x16 grid.
     */
    fun mapCells(
        textureWidth: Int,
        textureHeight: Int,
        cellSizePx: Int = CELL_SIZE_PX,
    ): List<AtlasCell> {
        val expectedWidth = GRID_SIZE * cellSizePx
        val expectedHeight = GRID_SIZE * cellSizePx
        require(textureWidth == expectedWidth) {
            "Unexpected conchars width: $textureWidth, expected: $expectedWidth"
        }
        require(textureHeight == expectedHeight) {
            "Unexpected conchars height: $textureHeight, expected: $expectedHeight"
        }

        return List(GLYPH_COUNT) { index ->
            val row = index / GRID_SIZE
            val col = index % GRID_SIZE
            AtlasCell(
                index = index,
                x = col * cellSizePx,
                y = row * cellSizePx,
                width = cellSizePx,
                height = cellSizePx,
            )
        }
    }

    fun createBitmapFont(
        texture: Texture,
        cellSizePx: Int = CELL_SIZE_PX,
    ): BitmapFont {
        val cells = mapCells(texture.width, texture.height, cellSizePx)
        val data = BitmapFont.BitmapFontData()
        val pageRegion = TextureRegion(texture)
        data.lineHeight = cellSizePx.toFloat()
        data.capHeight = cellSizePx.toFloat()
        data.ascent = -cellSizePx.toFloat()
        data.xHeight = cellSizePx.toFloat()
        data.down = -cellSizePx.toFloat()
        data.spaceXadvance = cellSizePx.toFloat()
        data.markupEnabled = false

        for (cell in cells) {
            val glyph = BitmapFont.Glyph()
            glyph.id = cell.index
            glyph.srcX = cell.x
            glyph.srcY = cell.y
            glyph.width = cell.width
            glyph.height = cell.height
            glyph.xadvance = cell.width
            glyph.xoffset = 0
            glyph.yoffset = 0
            glyph.page = 0
            data.setGlyph(cell.index, glyph)
            data.setGlyphRegion(glyph, pageRegion)
        }

        val regions = Array<TextureRegion>(1)
        regions.add(pageRegion)
        return BitmapFont(data, regions, false)
    }
}
