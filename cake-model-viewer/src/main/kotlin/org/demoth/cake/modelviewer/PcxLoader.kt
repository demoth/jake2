package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.TextureData.TextureDataType
import jake2.qcommon.filesystem.PCX

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

/**
 * @param externalPalette RBBA8888 format, used instexd of [pcx.colors] if provided
 */
internal fun fromPCX(pcx: PCX, externalPalette: IntArray? = null): Pixmap {
    val pixmap = Pixmap(pcx.width, pcx.height, Pixmap.Format.RGBA8888)
    var offset = 0
    for (y in 0 until pcx.height) {
        for (x in 0 until pcx.width) {
            val colorIndex = 0xFF and pcx.imageData[offset++].toInt() // unsigned
            val color = if (externalPalette != null) externalPalette[colorIndex] else pcx.colors[colorIndex]
            pixmap.drawPixel(x, y, color)
        }
    }
    return pixmap
}
