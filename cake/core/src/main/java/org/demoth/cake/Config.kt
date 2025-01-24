package org.demoth.cake

import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
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
        return configStrings.sliceArray(CS_MODELS + 1 until CS_MODELS + MAX_MODELS)
    }

    /**
     * an array slice starting from CS_SOUNDS up to CS_SOUNDS + MAX_SOUNDS
     */
    fun getSounds(): Array<Config?> {
        // todo: avoid copying
        return configStrings.sliceArray(CS_SOUNDS + 1 until CS_SOUNDS + MAX_SOUNDS)
    }

    fun dispose() {
        configStrings.forEach { it?.resource?.dispose() }
    }
}