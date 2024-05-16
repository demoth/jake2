package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.TextureData.TextureDataType
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.filesystem.PCX_PALETTE_SIZE
import jake2.qcommon.filesystem.PCX_PALLETE_OFFSET

class PCXTextureData(private var pixmap: Pixmap) : TextureData {

    override fun getType(): TextureDataType {
        return TextureDataType.Pixmap
    }

    override fun isPrepared(): Boolean {
        return true
    }

    override fun prepare() {
    }

    override fun consumePixmap(): Pixmap {
        return pixmap
    }

    override fun disposePixmap(): Boolean {
        return true
    }

    override fun consumeCustomData(target: Int) {
        throw UnsupportedOperationException("This TextureData implementation does not support custom data")
    }

    override fun getWidth(): Int {
        return pixmap.width
    }

    override fun getHeight(): Int {
        return pixmap.height
    }

    override fun getFormat(): Pixmap.Format {
        return pixmap.format
    }

    override fun useMipMaps(): Boolean {
        return false
    }

    override fun isManaged(): Boolean {
        return true
    }
}

// Run-Length Encoding
private const val RLE_UPPER = 0xC0 // top 2 bits indicate a RLE compression
private const val RLE_LOWER = 0x3F // last 6 bits is the number of the same pixels

internal fun fromPCX(pcx: PCX): Pixmap {
    val width = pcx.xmax - pcx.xmin + 1
    val height = pcx.ymax - pcx.ymin + 1

    val imageData = ByteArray(pcx.bytes_per_line * height)
    var index = 0
    while (index < imageData.size) {
        val data = pcx.data.get().toInt() and 0xFF // unsigned
        if ((data and RLE_UPPER) == RLE_UPPER) {
            val runLength = data and RLE_LOWER
            val nextByte = pcx.data.get().toInt() and 0xFF
            // write runLength pixels
            repeat(runLength) {
                imageData[index++] = nextByte.toByte()
            }
        } else {
            // write one pixel
            imageData[index++] = data.toByte()
        }
    }

    // read the palette
    // skip to byte -769
    val palette = ByteArray(PCX_PALETTE_SIZE)
    pcx.data.position(pcx.data.limit() - PCX_PALLETE_OFFSET)
    pcx.data.get(palette)

    // read colors
    val colors = IntArray(256)
    for (i in 0..255) {
        val r: Int = 0xFF and palette.get(i * 3).toInt()
        val g: Int = 0xFF and palette.get(i * 3 + 1).toInt()
        val b: Int = 0xFF and palette.get(i * 3 + 2).toInt()
        colors[i] = (r shl 24) or (g shl 16) or (b shl 8) or 0xFF // alpha
    }
    val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
    var offset = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val colorIndex = 0xFF and imageData[offset++].toInt() // unsigned
            pixmap.drawPixel(x, y, colors[colorIndex])
        }
    }
    return pixmap
}
