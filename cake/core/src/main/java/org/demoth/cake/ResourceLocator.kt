package org.demoth.cake

/**
 * Abstracts the way how the resources are located (how name is resolved) and loaded.
 * Two implementations exist for Game and ModelViewer
 */
interface ResourceLocator {

    fun findModel(modelName: String): ByteArray?
    fun findSound(soundName: String): ByteArrayFileHandle?
    fun findImage(imageName: String, location: String = "textures"): ByteArray?
    fun findImagePath(imageName: String, location: String = "textures"): String?

    /**
     * skin name should contain the file extension
     */
    fun findSkin(skinName: String): ByteArray
    fun findSky(skyName: String): ByteArray
}
