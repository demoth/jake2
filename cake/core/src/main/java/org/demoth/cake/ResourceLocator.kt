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

    fun findSound(soundName: String): ByteArrayFileHandle? {
        if (soundName.isEmpty()) {
            return null
        } else if (soundName.startsWith("*")) {
            // TODO: implement male/female/cyborg sounds
            return null
        } else {
            val soundFile = File("$baseDir/$gameName/sound/$soundName")
            if (soundFile.exists()) {
                return ByteArrayFileHandle(soundFile.readBytes(), soundName)
            } else {
                val soundDir = File("$baseDir/$gameName/sound")
                // Search for the file ignoring case sensitivity, todo: make an index?
                val matchingFile = soundDir.walkTopDown().find { file ->
                    file.isFile && file.name.equals(soundName.substring(soundName.lastIndexOf('/') + 1), ignoreCase = true)
                }

                if (matchingFile != null) {
                    return ByteArrayFileHandle(matchingFile.readBytes(), soundName)
                }
                println("ERROR! could now find file: $soundName")
                return null
            }
        }
    }

    /**
     * [imageName] should contain the file extension
     */
    fun findImage(imageName: String, location: String = "textures"): ByteArray? {
        val file = File("$baseDir/$gameName/$location/$imageName")
        return if (file.exists()) {
            file.readBytes()
        } else {
            // fixme: use proper case insensitive search
            val lowercaseFile = File("$baseDir/$gameName/textures/${imageName.lowercase()}.wal")
            if (lowercaseFile.exists()) {
                println("Warn: $imageName was found by lowercase name")
                lowercaseFile.readBytes()
            } else {
                null
            }
        }
    }

    fun findSkin(skinName: String): ByteArray {
        return File("$baseDir/$gameName/$skinName").readBytes()
    }
}
