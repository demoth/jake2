package org.demoth.cake.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import java.io.File
import java.util.ArrayDeque

/**
 * Responsible for finding resources, in paks or on the filesystem.
 */
// todo: cache? move to streams instead?
// todo: get rid of File(...) usages where possible
@Deprecated("Migrate to AssetManager locator logic")
class GameResourceLocator(private val baseDir: String) : ResourceLocator {

    // todo: support other gameNames - be able to locate mod resources (fallback to baseq2 or smth else)
    var gameName: String = "baseq2"

    override fun findModelPath(modelName: String): String? {
        if (modelName.isEmpty()) {
            // todo: throw error?
            return null
        } else if (modelName.startsWith("#")) {
            // TODO: handle view models separately
            return null
        } else {
            val file = File("$baseDir/$gameName/$modelName")
            return if (file.exists()) file.absolutePath else null
        }
    }

    override fun findSkinPath(skinName: String): String? {
        val file = File("$baseDir/$gameName/$skinName")
        return if (file.exists()) file.absolutePath else null
    }

}
