package org.demoth.cake

import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Defines.*
import jake2.qcommon.exec.Cmd

/**
 * Store all configuration related to the current map.
 * Updated from server.
 *
 * tothink: This could be moved to common and reused on the server side?
 */
data class Config(
    val value: String,
    var resource: Disposable? = null
)

// may not be great to have a big array of nulls, but configs are accessed by the index (maybe use map int->config?)
class GameConfiguration(size: Int = MAX_CONFIGSTRINGS) {

    init {
        Cmd.AddCommand("print_configs") {
            configStrings.forEachIndexed { i, c ->
                if (c != null) {
                    Com.Printf("ConfigString[$i] = ${c.value}, loaded = ${c.resource != null}\n")
                }
            }
        }

    }



    /**
     * Layout for score and help screens.
     * For some reason, it is not managed by the config strings.
     */
    var layout: String = ""

    val inventory: IntArray = IntArray(Defines.MAX_ITEMS) { 0 }

    private val configStrings = Array<Config?>(size) { null }

    operator fun get(index: Int) = configStrings[index]

    operator fun set(index: Int, config: Config) {
        configStrings[index] = config
    }

    /**
     * an array slice starting from CS_MODELS up to CS_MODELS + MAX_MODELS
     */
    fun getModels(): Array<Config?> {
        // todo: avoid copying
        return configStrings.sliceArray(CS_MODELS + 1 .. CS_MODELS + MAX_MODELS)
    }

    fun getMapName(): String? {
        return configStrings[CS_MODELS + 1]?.value
    }

    /**
     * an array slice starting from CS_SOUNDS up to CS_SOUNDS + MAX_SOUNDS
     */
    fun getSounds(): Array<Config?> {
        // todo: avoid copying
        return configStrings.sliceArray(CS_SOUNDS + 1 .. CS_SOUNDS + MAX_SOUNDS)
    }

    fun getImages(): Array<Config?> {
        // todo: avoid copying
        return configStrings.sliceArray(CS_IMAGES + 1 .. CS_IMAGES + MAX_IMAGES)
    }

    fun dispose() {
        configStrings.forEach { it?.resource?.dispose() }
    }

    fun getStatusBarLayout(): String? {
        return configStrings[CS_STATUSBAR]?.value
    }

    fun getSkyname(): String? {
        return configStrings[CS_SKY]?.value
    }
}