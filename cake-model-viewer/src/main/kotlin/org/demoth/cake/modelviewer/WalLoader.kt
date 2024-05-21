package org.demoth.cake.modelviewer

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.TextureData.TextureDataType
import jake2.qcommon.filesystem.WAL

// fixme: Same as PCXTextureData?
class WalTextureData(private var pixmap: Pixmap) : TextureData {

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
 * @param palette RBBA8888 format, used instead of [pcx.colors] if provided
 */
internal fun fromWal(wal: WAL, palette: IntArray): Pixmap {
    val pixmap = Pixmap(wal.width, wal.height, Pixmap.Format.RGBA8888)
    var offset = 0
    for (y in 0 until wal.height) {
        for (x in 0 until wal.width) {
            val colorIndex = 0xFF and wal.imageData[offset++].toInt() // unsigned
            val color = palette[colorIndex]
            pixmap.drawPixel(x, y, color)
        }
    }
    return pixmap
}
