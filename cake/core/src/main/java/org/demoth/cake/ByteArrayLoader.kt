package org.demoth.cake

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array

class ByteArrayLoader(resolver: FileHandleResolver) :
    AsynchronousAssetLoader<ByteArray, ByteArrayLoader.Parameters>(resolver) {

    class Parameters : AssetLoaderParameters<ByteArray>()

    private var cache: ByteArray? = null

    override fun loadAsync(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ) {
        // run on AssetManager’s loader thread
        cache = file.readBytes()
    }

    override fun loadSync(
        manager: AssetManager,
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): ByteArray {
        val result = cache ?: file.readBytes()
        cache = null
        return result
    }

    override fun getDependencies(
        fileName: String,
        file: FileHandle,
        parameter: Parameters?
    ): Array<AssetDescriptor<*>>? = null
}
