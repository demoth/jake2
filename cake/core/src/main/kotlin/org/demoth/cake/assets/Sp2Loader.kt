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
 * Loaded `.sp2` sprite asset used by runtime billboard rendering.
 *
 * Purpose:
 * - Keep only render-relevant frame metadata and resolved textures.
 *
 * Ownership/Lifecycle:
 * - Created by [Sp2Loader] via AssetManager.
 * - Frame textures are AssetManager dependencies and are not disposed here.
 *
 * Invariants:
 * - [frames] order matches on-disk frame order.
 * - [Frame.imagePath] points to the texture path used to resolve [Frame.texture].
 *
 * Legacy counterpart:
 * - `client/render/fast/Model.Mod_LoadSpriteModel` keeps per-frame image handles for `SPRITE` models.
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
 * AssetManager loader for Quake2 `.sp2` files.
 *
 * Threading/Timing:
 * - Synchronous loader; called on AssetManager load path.
 *
 * Behavior:
 * - Parses sprite header/frames from qcommon `qfiles.Sp2Sprite`.
 * - Declares unique frame image dependencies (`.pcx`) in [getDependencies].
 * - Produces [Sp2Asset] with resolved textures in [load].
 *
 * Constraint:
 * - Missing frame textures fail at asset load time (manager.get).
 *
 * Extension point:
 * - If future `.sp2` metadata is needed at runtime, extend [Sp2Asset.Frame] first.
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
