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
import jake2.qcommon.Com
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

/** Generated engine placeholder; each caller owns its texture through its usual asset lifecycle. */
internal fun createMissingTexture(): Texture {
    val pixels = Pixmap(64, 64, Pixmap.Format.RGBA8888)
    for (y in 0 until 64) for (x in 0 until 64) {
        pixels.drawPixel(x, y, if ((x / 16 + y / 16) % 2 == 0) 0xFF00FFFF.toInt() else 0x181818FF)
    }
    return Texture(CakeTextureData(pixels))
}

/** Missing files are recoverable; malformed files still report their decoding errors. */
private fun resolveTextureFile(resolver: FileHandleResolver, path: String): FileHandle? = try {
    resolver.resolve(path)?.takeIf { it.exists() }
} catch (_: MissingResourceException) {
    null
}

class WalLoader(private val textureResolver: FileHandleResolver) : SynchronousAssetLoader<Texture, WalLoader.Parameters>(textureResolver) {

    override fun resolve(fileName: String): FileHandle? = resolveTextureFile(textureResolver, fileName)

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
        file: FileHandle?,
        parameter: Parameters?
    ): Texture {
        val texture = if (file == null || !file.exists()) {
            Com.Warn("Missing texture '$fileName'; using engine checkerboard.")
            createMissingTexture()
        } else {
            val wal = WAL(file.readBytes())
            val palette = parameter?.externalPalette
                ?: manager.get(parameter?.paletteAssetPath ?: "q2palette.bin", Any::class.java) as IntArray
            Texture(CakeTextureData(fromWal(wal, palette)))
        }
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
        if (file == null || !file.exists() || parameter?.externalPalette != null) {
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

class PcxLoader(private val textureResolver: FileHandleResolver) : SynchronousAssetLoader<Texture, PcxLoader.Parameters>(textureResolver) {

    override fun resolve(fileName: String): FileHandle? = resolveTextureFile(textureResolver, fileName)

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
        file: FileHandle?,
        parameter: Parameters?
    ): Texture {
        val texture = if (file == null || !file.exists()) {
            Com.Warn("Missing texture '$fileName'; using engine checkerboard.")
            createMissingTexture()
        } else {
            Texture(CakeTextureData(fromPCX(PCX(file.readBytes()), parameter?.externalPalette)))
        }
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
    val palette = externalPalette ?: pcx.colors
    var offset = 0
    for (y in 0 until pcx.height) {
        for (x in 0 until pcx.width) {
            val pixelIndex = offset++
            val colorIndex = 0xFF and pcx.imageData[pixelIndex].toInt() // unsigned
            val color = resolvePcxColor(colorIndex, pixelIndex, pcx.width, pcx.imageData, palette)
            pixmap.drawPixel(x, y, color)
        }
    }
    return pixmap
}

private const val TRANSPARENT_COLOR_INDEX = 255
private const val RGB_MASK_RGBA8888 = 0xFFFFFF00.toInt()

/**
 * Mirrors legacy behavior:
 * - palette index 255 is transparent
 * - RGB for transparent pixels is borrowed from a non-transparent neighbor to reduce fringes
 */
internal fun resolvePcxColor(
    colorIndex: Int,
    pixelIndex: Int,
    width: Int,
    imageData: ByteArray,
    palette: IntArray,
): Int {
    if (colorIndex != TRANSPARENT_COLOR_INDEX) {
        return palette[colorIndex]
    }

    var neighborIndex = 0
    if (pixelIndex >= width && (imageData[pixelIndex - width].toInt() and 0xFF) != TRANSPARENT_COLOR_INDEX) {
        neighborIndex = imageData[pixelIndex - width].toInt() and 0xFF
    } else if (pixelIndex < imageData.size - width && (imageData[pixelIndex + width].toInt() and 0xFF) != TRANSPARENT_COLOR_INDEX) {
        neighborIndex = imageData[pixelIndex + width].toInt() and 0xFF
    } else if (pixelIndex > 0 && (imageData[pixelIndex - 1].toInt() and 0xFF) != TRANSPARENT_COLOR_INDEX) {
        neighborIndex = imageData[pixelIndex - 1].toInt() and 0xFF
    } else if (pixelIndex < imageData.size - 1 && (imageData[pixelIndex + 1].toInt() and 0xFF) != TRANSPARENT_COLOR_INDEX) {
        neighborIndex = imageData[pixelIndex + 1].toInt() and 0xFF
    }

    return palette[neighborIndex] and RGB_MASK_RGBA8888
}
