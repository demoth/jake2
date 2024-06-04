package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Defines
import jake2.qcommon.Defines.BUTTON_ATTACK
import jake2.qcommon.Defines.CMD_BACKUP
import jake2.qcommon.Defines.CS_MODELS
import jake2.qcommon.Defines.CS_SOUNDS
import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.Defines.MAX_SOUNDS
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.ConfigStringMessage
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.usercmd_t
import ktx.app.KtxScreen
import java.io.File
import kotlin.experimental.or

data class Config(var value: String, var resource: Disposable? = null)

/**
 * Represents the 3d screen where the game is actually happening.
 * This class is responsible for drawing 3d models, hud, process inputs and play sounds.
 * Also, it is responsible for loading/disposing of the required resources
 */
class Game3dScreen(var cam: Camera) : KtxScreen {
    val models: MutableMap<Int, ModelInstance> = mutableMapOf()
    val modelBatch: ModelBatch

    /**
     * Store all configuration related to the current map.
     * Updated from server
     */
    val configStrings = Array<Config?>(MAX_CONFIGSTRINGS) { Config("") }

    private val userCommands = Array(CMD_BACKUP) { usercmd_t() }
    private var serverFrame: Int = 0

    // game state
    private var gameName: String = "baseq2"
    private var spawnCount = 0
    private var playercount = 1
    private var levelString: String = ""
    private var refresh_prepped: Boolean = false

    init {
        // create camera
        cam.update()
        modelBatch = ModelBatch()
    }

    override fun render(delta: Float) {
        modelBatch.begin(cam)
        models.forEach { (_, model) ->
            modelBatch.render(model);
        }
        modelBatch.end()

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

    fun gatherInput(outgoingSequence: Int): MoveMessage {
        // assemble the inputs and commands, then transmit them
        val cmdIndex: Int = outgoingSequence and (Defines.CMD_BACKUP - 1)
        val oldCmdIndex: Int = (outgoingSequence - 1) and (Defines.CMD_BACKUP - 1)
        val oldestCmdIndex: Int = (outgoingSequence - 2) and (Defines.CMD_BACKUP - 1)

        val cmd = userCommands[cmdIndex]
        cmd.clear()

        // todo: implement proper input mapping
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            cmd.buttons = cmd.buttons or BUTTON_ATTACK.toByte()
        }

        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            cmd.forwardmove = 100 // todo: calculate based on client prediction
        }
        cmd.msec = 16 // todo: calculate
        // deliver the message
        return MoveMessage(
            false, // todo
            serverFrame,
            userCommands[oldestCmdIndex],
            userCommands[oldCmdIndex],
            userCommands[cmdIndex],
            outgoingSequence
        )

    }

    fun parseServerFrameHeader(message: FrameHeaderMessage) {
        serverFrame = message.frameNumber
    }

    /*
     * ================== CL_ParseServerData ==================
     */
    fun parseServerDataMessage(msg: ServerDataMessage) {
        gameName = msg.gameName.ifBlank { "baseq2" }
        levelString = msg.levelString
        playercount = msg.playerNumber
        spawnCount = msg.spawnCount

        refresh_prepped = false // force reloading of all "refresher" (visual) resources, most importantly the level

    }
}

private val basedir = System.getProperty("basedir")
