package org.demoth.cake

interface ResourceLocator {

    fun findMap(mapName: String): ByteArray
    fun findModel(modelName: String): ByteArray?
    fun findSound(soundName: String): ByteArrayFileHandle?
    fun findImage(imageName: String, location: String = "textures"): ByteArray?

    /**
     * skin name should contain the file extension
     */
    fun findSkin(skinName: String): ByteArray
    fun findSky(skyName: String): ByteArray
}