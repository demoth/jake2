package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Defines.CS_MODELS
import jake2.qcommon.Defines.CS_SOUNDS
import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.Defines.MAX_SOUNDS
import jake2.qcommon.network.messages.server.ConfigStringMessage
import ktx.app.KtxScreen
import java.io.File

data class Config(var value: String, var resource: Disposable? = null)

/**
 * Represents the 3d screen where the game is actually happening.
 * This class is responsible for drawing 3d models, hud, process inputs and play sounds.
 * Also, it is responsible for loading/disposing of the required resources
 */
class Game3dScreen(val cam: Camera? = null) : KtxScreen {
    val models: MutableMap<Int, ModelInstance> = mutableMapOf()
    val modelBatch: ModelBatch

    /**
     * Store all configuration related to the current map.
     * Updated from server
     */
    val configStrings = Array<Config?>(MAX_CONFIGSTRINGS) { Config("") }


    init {
        // create camera
        cam?.update()
        modelBatch = ModelBatch()
    }

    override fun render(delta: Float) {
        if (cam != null) {
            modelBatch.begin(cam)
            models.forEach { (_, model) ->
                modelBatch.render(model);
            }
            modelBatch.end()
        }
    }

    override fun dispose() {
        modelBatch.dispose()
        // clear the config strings
        for (i in 0 until MAX_CONFIGSTRINGS) {configStrings[i] = Config("")}
    }

    fun updateConfig(msg: ConfigStringMessage) {
        configStrings[msg.index]!!.value = msg.config
    }

    /**
     * Load resources referenced in the config strings into the memory
     */
    fun precache() {
        // load resources referenced in the config strings
        val configStrings = configStrings
        val mapName = configStrings[CS_MODELS + 1]

        // load sounds starting from CS_SOUNDS until MAX_SOUNDS
        for (i in 1 until MAX_SOUNDS) {
            configStrings[CS_SOUNDS + i]?.let {s ->
                if (s.value.isNotEmpty() && !s.value.startsWith("*")) { // skip sexed sounds for now
                    println("precache sound ${s.value}: ")
                    val soundPath = "$basedir/baseq2/sound/${s.value}"
                    if (File(soundPath).exists()) {
                        s.resource = Gdx.audio.newSound(Gdx.files.absolute(soundPath))
                    } else {
                        println("TODO: Find sound case insensitive: ${s.value}") //
                    }
                }
            }
        }
    }
}

private val basedir = System.getProperty("basedir")
