package org.demoth.cake

import java.io.File

class ModelViewerResourceLocator(val currentDirectory: String) : ResourceLocator {

    /**
     * Expect the full model file path
     */
    override fun findModelPath(modelName: String): String? {
        val file = File(modelName)
        return if (file.exists()) file.absolutePath else null
    }

    override fun findSound(soundName: String): ByteArrayFileHandle? {
        TODO("Not yet implemented")
    }

    override fun findImagePath(imageName: String, location: String): String? {
        val file = File("$currentDirectory/$imageName")
        return if (file.exists()) file.absolutePath else null
    }

    override fun findSkin(skinName: String): ByteArray {
        val fileName = skinName.substring(skinName.lastIndexOf('/') + 1)
        return File("$currentDirectory/$fileName").readBytes()
    }

    override fun findSkinPath(skinName: String): String? {
        val fileName = skinName.substring(skinName.lastIndexOf('/') + 1)
        val file = File("$currentDirectory/$fileName")
        return if (file.exists()) file.absolutePath else null
    }

    override fun findSky(skyName: String): ByteArray {
        TODO("Not yet implemented")
    }
}
