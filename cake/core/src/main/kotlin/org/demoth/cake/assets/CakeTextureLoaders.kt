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
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.filesystem.WAL

/**
 * Common TextureData for PCX and WAL texture formats
 */
class CakeTextureData(private var pixmap: Pixmap) : TextureData {

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

    data class Parameters(
        val externalPalette: IntArray? = null,
        val paletteAssetPath: String = "q2palette.bin",
        val minFilter: Texture.TextureFilter? = null,
        val magFilter: Texture.TextureFilter? = null,
        val wrapU: Texture.TextureWrap? = null,
        val wrapV: Texture.TextureWrap? = null,
    ) : AssetLoaderParameters<Texture>()

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
        val texture = Texture(CakeTextureData(pixmap))
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
 * @param [palette] wal file relies on external palette
 */
internal fun fromWal(wal: WAL, palette: IntArray): Pixmap {
    val pixmap = Pixmap(wal.width, wal.height, Pixmap.Format.RGBA8888)
    var offset = 0
    for (y in 0 until wal.height) {
        for (x in 0 until wal.width) {
            val intPixelValue = wal.imageData[offset++].toInt()
            val colorIndex = 0xFF and intPixelValue // unsigned
            val color = palette[colorIndex]
            pixmap.drawPixel(x, y, color)
        }
    }
    return pixmap
}

class PcxLoader(resolver: FileHandleResolver) : SynchronousAssetLoader<Texture, PcxLoader.Parameters>(resolver) {

    data class Parameters(
        val externalPalette: IntArray? = null,
        val minFilter: Texture.TextureFilter? = null,
        val magFilter: Texture.TextureFilter? = null,
        val wrapU: Texture.TextureWrap? = null,
        val wrapV: Texture.TextureWrap? = null,
    ) : AssetLoaderParameters<Texture>()

    override fun load(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): Texture {
        val pcx = PCX(file.readBytes())
        val pixmap = fromPCX(pcx, parameter?.externalPalette)
        val texture = Texture(CakeTextureData(pixmap))
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

    // todo: define palette as a dependency
    override fun getDependencies(
        fileName: String,
        file: FileHandle?,
        parameter: Parameters?
    ): Array<AssetDescriptor<*>>? = null
}

/**
 * @param externalPalette RBBA8888 format, used instead of [pcx.colors] if provided
 */
internal fun fromPCX(pcx: PCX, externalPalette: IntArray? = null): Pixmap {
    val pixmap = Pixmap(pcx.width, pcx.height, Pixmap.Format.RGBA8888)
    var offset = 0
    for (y in 0 until pcx.height) {
        for (x in 0 until pcx.width) {
            val colorIndex = 0xFF and pcx.imageData[offset++].toInt() // unsigned
            val color = if (externalPalette != null)
                externalPalette[colorIndex]
            else
                pcx.colors[colorIndex]
            pixmap.drawPixel(x, y, color)
        }
    }
    return pixmap
}
