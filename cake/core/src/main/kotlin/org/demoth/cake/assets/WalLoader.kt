package org.demoth.cake.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.TextureData.TextureDataType
import com.badlogic.gdx.utils.Array
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

class WalLoader(resolver: FileHandleResolver) : SynchronousAssetLoader<Texture, WalLoader.Parameters>(resolver) {

    class Parameters : AssetLoaderParameters<Texture>() {
        var externalPalette: IntArray? = null
        var paletteAssetPath: String = "q2palette.bin"
        var minFilter: Texture.TextureFilter? = null
        var magFilter: Texture.TextureFilter? = null
        var wrapU: Texture.TextureWrap? = null
        var wrapV: Texture.TextureWrap? = null
    }

    override fun load(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): Texture {
        val wal = WAL(file.readBytes())
        val palette = parameter?.externalPalette
            ?: manager.get(parameter?.paletteAssetPath ?: "q2palette.bin", Any::class.java) as IntArray
        val pixmap = fromWal(wal, palette)
        val texture = Texture(WalTextureData(pixmap))
        val minFilter = parameter?.minFilter
        val magFilter = parameter?.magFilter
        if (minFilter != null || magFilter != null) {
            texture.setFilter(minFilter ?: texture.minFilter, magFilter ?: texture.magFilter)
        }
        val wrapU = parameter?.wrapU
        val wrapV = parameter?.wrapV
        if (wrapU != null || wrapV != null) {
            texture.setWrap(wrapU ?: texture.uWrap, wrapV ?: texture.vWrap)
        }
        return texture
    }

    override fun getDependencies(
        fileName: String,
        file: FileHandle?,
        parameter: Parameters?
    ): Array<AssetDescriptor<*>>? {
        if (parameter?.externalPalette != null) {
            return null
        }
        val palettePath = parameter?.paletteAssetPath ?: "q2palette.bin"
        return Array<AssetDescriptor<*>>(1).apply {
            add(AssetDescriptor(palettePath, Any::class.java))
        }
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
            val intPixelValue = wal.imageData[offset++].toInt()
            val colorIndex = 0xFF and intPixelValue // unsigned
            val color = palette[colorIndex]
            // split color packed RGBA8888 into separate components for debug
            val r = color shr 24 and 0xFF
            val g = color shr 16 and 0xFF
            val b = color shr 8 and 0xFF
            val a = color and 0xFF
            pixmap.drawPixel(x, y, color)
        }
    }
    return pixmap
}
