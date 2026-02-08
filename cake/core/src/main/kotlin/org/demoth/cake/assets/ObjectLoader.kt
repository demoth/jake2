package org.demoth.cake.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array
import java.io.ObjectInputStream

/**
 * Loads Java-serialized objects via AssetManager.
 *
 * This loader uses Java ObjectInputStream, so it must only be used with trusted data sources.
 * Deserialization runs in [loadAsync], with [loadSync] returning the cached result.
 */
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

    /**
     * Deserialize one object from file content.
     *
     * Security note: do not point this at untrusted user-controlled files.
     */
    private fun readObjectFromFile(file: FileHandle): Any {
        return ObjectInputStream(file.read()).use { it.readObject() as Any }
    }

    // no dependencies
    override fun getDependencies(
        fileName: String, file: FileHandle, parameter: Parameters?
    ): Array<AssetDescriptor<*>>? = null
}
