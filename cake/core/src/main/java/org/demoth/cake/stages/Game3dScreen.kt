package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Defines
import jake2.qcommon.Defines.BUTTON_ATTACK
import jake2.qcommon.Defines.CMD_BACKUP
import jake2.qcommon.Defines.CS_MODELS
import jake2.qcommon.Defines.CS_SOUNDS
import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.Defines.MAX_EDICTS
import jake2.qcommon.Defines.MAX_SOUNDS
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.ConfigStringMessage
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import jake2.qcommon.network.messages.server.PlayerInfoMessage
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.network.messages.server.SoundMessage
import jake2.qcommon.network.messages.server.SpawnBaselineMessage
import jake2.qcommon.usercmd_t
import ktx.app.KtxInputAdapter
import ktx.app.KtxScreen
import org.demoth.cake.ClientEntity
import org.demoth.cake.modelviewer.BspLoader
import org.demoth.cake.modelviewer.createGrid
import org.demoth.cake.modelviewer.createOriginArrows
import java.io.File
import kotlin.experimental.or

data class Config(var value: String, var resource: Disposable? = null)

/**
 * Represents the 3d screen where the game is actually happening.
 * This class is responsible for drawing 3d models, hud, process inputs and play sounds.
 * Also, it is responsible for loading/disposing of the required resources
 */
class Game3dScreen : KtxScreen, KtxInputAdapter {
    // enitity id -> model
    val models: MutableMap<Int, ModelInstance> = mutableMapOf()
    val modelBatch: ModelBatch

    val camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    val cameraInputController = CameraInputController(camera)

    /**
     * Store all configuration related to the current map.
     * Updated from server
     */
    val configStrings = Array<Config?>(MAX_CONFIGSTRINGS) { Config("") }

    private val userCommands = Array(CMD_BACKUP) { usercmd_t() }
    private var serverFrame: Int = 0
    val environment = Environment()

    // game state
    private var gameName: String = "baseq2"
    private var spawnCount = 0
    private var playercount = 1
    private var levelString: String = ""
    private val clientEntities = Array(MAX_EDICTS) { ClientEntity() }

    init {
        camera.position.set(0f, 64f, 0f);
        camera.lookAt(64f, 32f, 64f);
        camera.near = 1f
        camera.far = 4096f

        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))

        // create camera
        camera.update()
        modelBatch = ModelBatch()
        models[100] = createGrid(16f, 8)
        models[101] = createOriginArrows(16f)
    }

    override fun render(delta: Float) {
        modelBatch.begin(camera)
        models.forEach { (_, model) ->
            modelBatch.render(model, environment);
        }
        modelBatch.end()
        cameraInputController.update()
    }

    override fun dispose() {
        modelBatch.dispose()
        // clear the config strings
        configStrings.forEach { it?.resource?.dispose() }
    }

//    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
//        return cameraInputController.mouseMoved(screenX, screenY)
//    }
//
//    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
//        return cameraInputController.touchDragged(screenX, screenY, pointer)
//    }
//
    fun updateConfig(msg: ConfigStringMessage) {
        configStrings[msg.index]!!.value = msg.config
    }

    /**
     * Load resources referenced in the config strings into the memory
     */
    fun precache() {
        // load resources referenced in the config strings
        val configStrings = configStrings

        // load the level and inline bmodels
        val mapName = configStrings[CS_MODELS + 1]?.value
        // mapName already has 'maps/' prefix
        val mapFile = File("$basedir/$gameName/$mapName") // todo: cache
        val brushModels = BspLoader("$basedir/$gameName/").loadBspModelTextured(mapFile)
        models[0] = brushModels.first()
        // todo: use other models

        // load sounds starting from CS_SOUNDS until MAX_SOUNDS
        for (i in 1 until MAX_SOUNDS) {
            configStrings[CS_SOUNDS + i]?.let {s ->
                if (s.value.isNotEmpty() && !s.value.startsWith("*")) { // skip sexed sounds for now
                    println("precache sound ${s.value}: ")
                    val soundPath = "$basedir/$gameName/sound/${s.value}"
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
        val cmdIndex: Int = outgoingSequence and (userCommands.size - 1)
        val oldCmdIndex: Int = (outgoingSequence - 1) and (userCommands.size - 1)
        val oldestCmdIndex: Int = (outgoingSequence - 2) and (userCommands.size - 1)

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
     * CL_ParseServerData
     */
    fun parseServerDataMessage(msg: ServerDataMessage) {
        gameName = msg.gameName.ifBlank { "baseq2" }
        levelString = msg.levelString
        playercount = msg.playerNumber
        spawnCount = msg.spawnCount
    }

    fun playSound(msg: SoundMessage) {
        val sound = configStrings[msg.soundIndex]?.resource as? Sound
        println("Playing sound ${msg.soundIndex} ${sound}")
        sound?.play() // todo: use msg.volume, attenuation, etc

    }

    fun parseEntities(msg: PacketEntitiesMessage) {
        // todo:
    }

    fun parseBaseline(msg: SpawnBaselineMessage) {
        clientEntities[msg.entityState.number].baseline.set(msg.entityState)
    }

    fun parsePlayerInfo(msg: PlayerInfoMessage) {
        if ((msg.deltaFlags and Defines.PS_M_ORIGIN) != 0) {
            msg.currentState?.pmove?.origin?.let { pos ->
                camera.position.set(
                    pos[0].toFloat(),
                    pos[2].toFloat(),
                    pos[1].toFloat()
                )
            }
        }
    }
}

private val basedir = System.getProperty("basedir")
