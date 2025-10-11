package org.demoth.cake

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array

class StringLoader(resolver: FileHandleResolver) : AsynchronousAssetLoader<String, StringLoader.Parameters>(resolver) {

    class Parameters : AssetLoaderParameters<String>()

    private var cache: String? = null

    override fun loadAsync(
        manager: AssetManager, fileName: String, file: FileHandle, parameter: Parameters?
    ) {
        // run on AssetManager’s loader thread
        cache = file.readString("UTF-8")
    }

    override fun loadSync(
        manager: AssetManager, fileName: String, file: FileHandle, parameter: Parameters?
    ): String {
        val result = cache ?: file.readString("UTF-8")
        cache = null
        return result
    }

    override fun getDependencies(
        fileName: String, file: FileHandle, parameter: Parameters?
    ): Array<AssetDescriptor<*>>? = null
}
