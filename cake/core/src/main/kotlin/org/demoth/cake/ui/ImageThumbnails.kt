package org.demoth.cake.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.vfs.VfsEntry
import org.demoth.cake.assets.PcxLoader
import org.demoth.cake.assets.WalLoader
import org.demoth.cake.assets.getLoaded

/** Folder-scoped textures owned by the browser host, independent of shared AssetManager references. */
class ImageThumbnails(
    private val assets: AssetManager,
    private val resolver: FileHandleResolver,
) : Disposable {
    private val textures = mutableMapOf<String, Texture>()
    private val failures = mutableSetOf<String>()

    fun load(entry: VfsEntry): Texture? {
        val path = entry.normalizedPath
        textures[path]?.let { return it }
        if (path in failures) return null
        return try {
            val file = requireNotNull(resolver.resolve(path)) { "Missing profile image: $path" }
            // Direct loader calls keep malformed images out of the shared manager's task queue.
            val texture = when (path.substringAfterLast('.')) {
                "pcx" -> PcxLoader(resolver).load(assets, path, file, null)
                "wal" -> WalLoader(resolver).load(assets, path, file, WalLoader.Parameters(
                    externalPalette = assets.getLoaded<Any>("q2palette.bin") as IntArray))
                else -> Texture(file)
            }
            textures[path] = texture
            texture
        } catch (error: Exception) {
            failures += path
            Gdx.app.error("Content browser", "Could not thumbnail ${entry.displayPath}", error)
            null
        }
    }

    fun clear() {
        textures.values.forEach { it.dispose() }
        textures.clear()
        failures.clear()
    }

    override fun dispose() = clear()
}
