package org.demoth.cake.ui

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array

object ConcharsFontLoader {
    const val GRID_SIZE = 16
    const val GLYPH_COUNT = GRID_SIZE * GRID_SIZE

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
    ): List<AtlasCell> {
        require(textureWidth % GRID_SIZE == 0) {
            "Unexpected conchars width: $textureWidth, expected a multiple of $GRID_SIZE"
        }
        require(textureHeight % GRID_SIZE == 0) {
            "Unexpected conchars height: $textureHeight, expected a multiple of $GRID_SIZE"
        }
        val cellWidthPx = textureWidth / GRID_SIZE
        val cellHeightPx = textureHeight / GRID_SIZE
        require(cellWidthPx == cellHeightPx) {
            "Unexpected conchars aspect ratio: cellWidth=$cellWidthPx cellHeight=$cellHeightPx"
        }
        val cellSizePx = cellWidthPx

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
    ): BitmapFont {
        val cells = mapCells(texture.width, texture.height)
        val cellSizePx = cells.first().width
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
