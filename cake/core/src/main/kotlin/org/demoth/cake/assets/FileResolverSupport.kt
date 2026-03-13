package org.demoth.cake.assets

import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle

internal fun FileHandleResolver.tryResolveRaw(path: String): FileHandle? {
    return when (this) {
        is CakeFileResolver -> tryResolve(path)
        else -> resolve(path)
    }
}
