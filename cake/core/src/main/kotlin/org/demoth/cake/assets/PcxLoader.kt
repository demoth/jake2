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

class PcxLoader(
    resolver: FileHandleResolver
) : SynchronousAssetLoader<Texture, PcxLoader.Parameters>(resolver) {

    class Parameters : AssetLoaderParameters<Texture>() {
        var externalPalette: IntArray? = null
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
        val pcx = PCX(file.readBytes())
        val pixmap = fromPCX(pcx, parameter?.externalPalette)
        val texture = Texture(PCXTextureData(pixmap))
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
        file: FileHandle,
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
            val color = if (externalPalette != null) externalPalette[colorIndex] else pcx.colors[colorIndex]
            pixmap.drawPixel(x, y, color)
        }
    }
    return pixmap
}
