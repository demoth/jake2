package org.demoth.cake.viewer

import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.utils.Disposable

/**
 * Owns one loaded root's AssetManager reference and any directly created preview resources.
 * Dependencies remain managed by AssetManager. Close before changing the resolver context.
 * A failed load still requires close, followed by the host's AssetManager error recovery.
 */
internal class PreviewResources(private val assets: AssetManager) : AutoCloseable {
    var path: String? = null
        private set
    private var acquired = false
    private val owned = mutableListOf<Disposable>()

    fun <T> load(path: String, type: Class<T>, parameters: AssetLoaderParameters<T>? = null): T {
        check(this.path == null) { "Close the previous preview before loading another resource" }
        this.path = path
        assets.load(path, type, parameters)
        // finishLoadingAsset can return before a queued reference increment for a cached asset.
        assets.finishLoading()
        acquired = true
        return assets.get(path, type)
    }

    fun <T : Disposable> own(resource: T): T = resource.also { owned += it }

    override fun close() {
        owned.asReversed().forEach { it.dispose() }
        owned.clear()
        path?.let { if (acquired && assets.isLoaded(it)) assets.unload(it) }
        path = null
        acquired = false
    }
}
