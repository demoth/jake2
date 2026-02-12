package org.demoth.cake.assets

import org.junit.Assert.assertEquals
import org.junit.Test

class PcxTransparencyTest {
    @Test
    fun nonTransparentIndexUsesPaletteColorAsIs() {
        val palette = IntArray(256)
        palette[7] = 0x112233FF.toInt()

        val color = resolvePcxColor(
            colorIndex = 7,
            pixelIndex = 0,
            width = 1,
            imageData = byteArrayOf(7),
            palette = palette,
        )

        assertEquals(0x112233FF.toInt(), color)
    }

    @Test
    fun transparentIndexUsesNeighborRgbAndZeroAlpha() {
        val palette = IntArray(256)
        palette[0] = 0x010203FF
        palette[2] = 0xAABBCCFF.toInt()

        // 2x2 image:
        // 255 2
        // 255 255
        val imageData = byteArrayOf(
            255.toByte(), 2,
            255.toByte(), 255.toByte(),
        )

        val color = resolvePcxColor(
            colorIndex = 255,
            pixelIndex = 0,
            width = 2,
            imageData = imageData,
            palette = palette,
        )

        assertEquals(0xAABBCC00.toInt(), color)
    }

    @Test
    fun transparentIndexFallsBackToPaletteZeroRgbWhenNoNeighborExists() {
        val palette = IntArray(256)
        palette[0] = 0xDEADBEFF.toInt()
        val imageData = byteArrayOf(255.toByte())

        val color = resolvePcxColor(
            colorIndex = 255,
            pixelIndex = 0,
            width = 1,
            imageData = imageData,
            palette = palette,
        )

        assertEquals(0xDEADBE00.toInt(), color)
    }
}
