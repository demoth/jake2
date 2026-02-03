package org.demoth.cake

/**
 * Abstracts the way how the resources are located (how name is resolved) and loaded.
 * Two implementations exist for Game and ModelViewer
 */
// todo: reimplement as a FileHandleResolver
interface ResourceLocator {

    fun findModelPath(modelName: String): String?
    fun findSoundPath(soundName: String): String?
    fun findImagePath(imageName: String, location: String = "textures"): String?

    /**
     * skin name should contain the file extension
     */
    fun findSkinPath(skinName: String): String?
    fun findSky(skyName: String): ByteArray
}
