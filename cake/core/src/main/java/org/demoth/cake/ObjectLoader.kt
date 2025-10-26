package org.demoth.cake

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array
import java.io.ObjectInputStream

class ObjectLoader(resolver: FileHandleResolver) : AsynchronousAssetLoader<Any, ObjectLoader.Parameters>(resolver) {

    class Parameters : AssetLoaderParameters<Any>()

    private var cache: Any? = null

    override fun loadAsync(
        manager: AssetManager, fileName: String, file: FileHandle, parameter: Parameters?
    ) {
        // run on AssetManager’s loader thread
        cache = readObjectFromFile(file)
    }

    override fun loadSync(
        manager: AssetManager, fileName: String, file: FileHandle, parameter: Parameters?
    ): Any {
        val result: Any = cache ?: readObjectFromFile(file)
        cache = null
        return result
    }

    private fun readObjectFromFile(file: FileHandle): Any {
        return ObjectInputStream(file.read()).use { it.readObject() as Any }
    }

    // no dependencies
    override fun getDependencies(
        fileName: String, file: FileHandle, parameter: Parameters?
    ): Array<AssetDescriptor<*>>? = null
}
