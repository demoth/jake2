package org.demoth.cake.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.filesystem.qfiles
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashSet

/**
 * Runtime metadata + resolved frame textures for a Quake2 `.sp2` sprite model.
 *
 * Texture lifecycle is still owned by AssetManager dependencies.
 */
class Sp2Asset(
    val frames: List<Frame>
) : Disposable {
    data class Frame(
        val width: Int,
        val height: Int,
        val originX: Int,
        val originY: Int,
        val imagePath: String,
        val texture: Texture,
    )

    override fun dispose() {
        // Frame textures are AssetManager-owned dependencies.
    }
}

/**
 * Loads `.sp2` sprite definitions and resolves referenced frame textures.
 */
class Sp2Loader(resolver: FileHandleResolver) : SynchronousAssetLoader<Sp2Asset, AssetLoaderParameters<Sp2Asset>>(resolver) {
    override fun getDependencies(
        fileName: String,
        file: FileHandle?,
        parameter: AssetLoaderParameters<Sp2Asset>?
    ): Array<AssetDescriptor<*>>? {
        if (file == null) {
            return null
        }

        val sprite = readSp2(file.readBytes(), fileName)
        val imagePaths = LinkedHashSet<String>()
        sprite.frames.forEach { frame ->
            if (frame.imageFileName.isNotBlank()) {
                imagePaths += frame.imageFileName
            }
        }
        if (imagePaths.isEmpty()) {
            return null
        }

        return Array<AssetDescriptor<*>>(imagePaths.size).apply {
            imagePaths.forEach { path ->
                add(AssetDescriptor(path, Texture::class.java))
            }
        }
    }

    override fun load(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: AssetLoaderParameters<Sp2Asset>?
    ): Sp2Asset {
        val sprite = readSp2(file.readBytes(), fileName)
        val frames = sprite.frames.map { frame ->
            Sp2Asset.Frame(
                width = frame.width,
                height = frame.height,
                originX = frame.origin_x,
                originY = frame.origin_y,
                imagePath = frame.imageFileName,
                texture = manager.get(frame.imageFileName, Texture::class.java),
            )
        }
        return Sp2Asset(frames)
    }

    private fun readSp2(data: ByteArray, fileName: String): qfiles.Sp2Sprite {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return qfiles.Sp2Sprite(buffer, fileName)
    }
}
