package org.demoth.cake

import java.io.File

/**
 * Responsible for finding resources, in paks or on the filesystem.
 */
// todo: cache? move to streams instead?
// todo: support gameNames - be able to locate mod resources (fallback to baseq2 or smth else)
class ResourceLocator(val baseDir: String, var gameName: String) {

    /**
     * [mapName] already has 'maps/' prefix
     */
    fun loadMap(mapName: String): File {
        return File("$baseDir/$gameName/$mapName")
    }

    fun loadModel(modelName: String): File? {
        if (modelName.isEmpty()) {
            // todo: throw error?
            return null
        } else if (modelName.startsWith("#")) {
            // TODO: handle view models separately
            return null
        } else {
            // /models/ is already part of the value (in contrast to sounds)
            return File("$baseDir/$gameName/$modelName")
        }
    }
}
