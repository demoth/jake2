package org.demoth.cake

import java.io.File

class ModelViewerResourceLocator(val currentDirectory: String) : ResourceLocator {

    override fun findMap(mapName: String): ByteArray {
        TODO("Not yet implemented")
    }

    /**
     * Expect the full model file path
     */
    override fun findModel(modelName: String): ByteArray? {
        return File(modelName).readBytes()
    }

    override fun findSound(soundName: String): ByteArrayFileHandle? {
        TODO("Not yet implemented")
    }

    override fun findImage(imageName: String, location: String): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun findSkin(skinName: String): ByteArray {
        val fileName = skinName.substring(skinName.lastIndexOf('/') + 1)
        return File("$currentDirectory/$fileName").readBytes()
    }

    override fun findSky(skyName: String): ByteArray {
        TODO("Not yet implemented")
    }
}