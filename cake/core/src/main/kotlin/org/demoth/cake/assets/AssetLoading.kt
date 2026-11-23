package org.demoth.cake.assets

import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.utils.GdxRuntimeException

/**
 * Get an asset ensuring it's loaded. Blocking operation.
 */
inline fun <reified T> AssetManager.getLoaded(path: String): T {
    if (!isLoaded(path, T::class.java)) {
        load(path, T::class.java)
        finishLoadingAsset<T>(path)
    }
    return get(path, T::class.java)
}

/**
 * Get an asset ensuring it's loaded. Blocking operation.
 */
inline fun <reified T> AssetManager.getLoaded(path: String, parameter: AssetLoaderParameters<T>): T {
    if (!isLoaded(path, T::class.java)) {
        load(path, T::class.java, parameter)
        finishLoadingAsset<T>(path)
    }
    return get(path, T::class.java)
}

/** Attribute built-in loader I/O and decoder failures to the dependency that was actually loading. */
internal fun AssetManager.reportResourceReadFailures() {
    setErrorListener { asset, cause ->
        // LibGDX wraps decoder I/O failures in GdxRuntimeException, including Pixmap decode failures.
        if (cause.findResourceFailure() != null) throw GdxRuntimeException(cause)
        readResource(asset.fileName) { throw GdxRuntimeException(cause) }
    }
}
