package org.demoth.cake

import java.io.File

/**
 * Responsible for finding resources, in paks or on the filesystem.
 */
// todo: cache? move to streams instead?
class ResourceLocator(private val baseDir: String) {

    // todo: support other gameNames - be able to locate mod resources (fallback to baseq2 or smth else)
    var gameName: String = "baseq2"

    /**
     * [mapName] already has 'maps/' prefix
     */
    fun findMap(mapName: String): ByteArray {
        return File("$baseDir/$gameName/$mapName").readBytes()
    }

    fun findModel(modelName: String): ByteArray? {
        if (modelName.isEmpty()) {
            // todo: throw error?
            return null
        } else if (modelName.startsWith("#")) {
            // TODO: handle view models separately
            return null
        } else {
            // /models/ is already part of the value (in contrast to sounds)
            return File("$baseDir/$gameName/$modelName").readBytes()
        }
    }

    fun findSound(soundName: String): ByteArray? {
        if (soundName.isEmpty()) {
            return null
        } else if (soundName.startsWith("*")) {
            // TODO: implement male/female/cyborg sounds
            return null
        } else {
            val soundFile = File("$baseDir/$gameName/sound/$soundName")
            if (soundFile.exists()) {
                return soundFile.readBytes()
            } else {
                val soundDir = File("$baseDir/$gameName/sound")
                // Search for the file ignoring case sensitivity, todo: make an index?
                val matchingFile = soundDir.walkTopDown().find { file ->
                    file.isFile && file.name.equals(soundName.substring(soundName.lastIndexOf('/') + 1), ignoreCase = true)
                }

                if (matchingFile != null) {
                    return matchingFile.readBytes()
                }
                println("ERROR! could now find file: $soundName")
                return null
            }
        }
    }

    fun findTexture(textureName: String): ByteArray {
        val file = File("$baseDir/$gameName/textures/$textureName.wal")
        return if (file.exists()) {
            file.readBytes()
        } else {
            println("Warn: $textureName was found by lowercase name")
            // fixme: use proper case insensitive search
            File("$baseDir/$gameName/textures/${textureName.lowercase()}.wal").readBytes()
        }
    }

    fun findSkin(skinName: String): ByteArray {
        return File("$baseDir/$gameName/$skinName").readBytes()
    }
}
