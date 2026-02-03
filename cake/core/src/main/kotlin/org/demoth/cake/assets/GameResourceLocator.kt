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

    override fun findSoundPath(soundName: String): String? {
        if (soundName.isEmpty()) {
            return null
        } else if (soundName.startsWith("*")) {
            // TODO: implement male/female/cyborg sounds
            return null
        } else {
            val soundPath = "$baseDir/$gameName/sound/$soundName"
            val soundFile = Gdx.files.absolute(soundPath)
            if (soundFile.exists()) {
                return soundFile.path()
            }

            val soundDir = Gdx.files.absolute("$baseDir/$gameName/sound")
            val targetName = soundName.substring(soundName.lastIndexOf('/') + 1)
            val matchingFile = findFileCaseInsensitive(soundDir, targetName)
            if (matchingFile != null) {
                return matchingFile.path()
            }
            println("ERROR! could now find file: $soundName")
            return null
        }
    }

    override fun findImagePath(imageName: String, location: String): String? {
        val file = File("$baseDir/$gameName/$location/$imageName")
        return if (file.exists()) {
            file.absolutePath
        } else {
            // fixme: use proper case insensitive search
            val lowercaseFile = File("$baseDir/$gameName/textures/${imageName.lowercase()}")
            if (lowercaseFile.exists()) {
                println("Warn: $imageName was found by lowercase name")
                lowercaseFile.absolutePath
            } else {
                null
            }
        }
    }

    override fun findSkinPath(skinName: String): String? {
        val file = File("$baseDir/$gameName/$skinName")
        return if (file.exists()) file.absolutePath else null
    }

    override fun findSky(skyName: String): ByteArray {
        return File("$baseDir/$gameName/env/$skyName.pcx").readBytes()
    }

    private fun findFileCaseInsensitive(dir: FileHandle, targetName: String): FileHandle? {
        if (!dir.exists() || !dir.isDirectory) {
            return null
        }
        val stack = ArrayDeque<FileHandle>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            current.list().forEach { child ->
                if (child.isDirectory) {
                    stack.add(child)
                } else if (child.name().equals(targetName, ignoreCase = true)) {
                    return child
                }
            }
        }
        return null
    }
}
