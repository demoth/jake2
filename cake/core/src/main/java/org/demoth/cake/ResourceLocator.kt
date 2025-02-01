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

    fun loadSound(soundName: String): File? {
        if (soundName.isEmpty()) {
            return null
        } else if (soundName.startsWith("*")) {
            // TODO: implement male/female/cyborg sounds
            return null
        } else {
            val soundFile = File("$baseDir/$gameName/sound/$soundName")
            if (soundFile.exists()) {
                return soundFile
            } else {
                val soundDir = File("$baseDir/$gameName/sound")
                // Search for the file ignoring case sensitivity, todo: make an index?
                val matchingFile = soundDir.walkTopDown().find { file ->
                    file.isFile && file.name.equals(soundName.substring(soundName.lastIndexOf('/') + 1), ignoreCase = true)
                }

                if (matchingFile != null) {
                    return matchingFile
                }
                println("ERROR! could now find file: $soundName")
                return null
            }
        }
    }

    fun loadTexture(textureName: String): File {
        val file = File("$baseDir/$gameName/textures/$textureName.wal")
        return if (file.exists()) {
            file
        } else {
            println("Warn: $textureName was found by lowercase name")
            File("$baseDir/$gameName/textures/${textureName.lowercase()}.wal")
        }
    }
}
