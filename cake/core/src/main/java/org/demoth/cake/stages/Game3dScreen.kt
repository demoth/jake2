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
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Defines.BUTTON_ATTACK
import jake2.qcommon.Defines.CMD_BACKUP
import jake2.qcommon.Defines.CS_MODELS
import jake2.qcommon.Defines.CS_SOUNDS
import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.Defines.MAX_EDICTS
import jake2.qcommon.Defines.MAX_SOUNDS
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.ConfigStringMessage
import jake2.qcommon.network.messages.server.EntityUpdate
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import jake2.qcommon.network.messages.server.PlayerInfoMessage
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.network.messages.server.SoundMessage
import jake2.qcommon.network.messages.server.SpawnBaselineMessage
import jake2.qcommon.usercmd_t
import jake2.qcommon.util.Math3D
import ktx.app.KtxInputAdapter
import ktx.app.KtxScreen
import org.demoth.cake.ClientEntity
import org.demoth.cake.ClientFrame
import org.demoth.cake.ServerMessageProcessor
import org.demoth.cake.modelviewer.BspLoader
import org.demoth.cake.modelviewer.createGrid
import org.demoth.cake.modelviewer.createOriginArrows
import java.io.File
import kotlin.experimental.or
import kotlin.math.abs

data class Config(var value: String, var resource: Disposable? = null)

/**
 * Represents the 3d screen where the game is actually happening.
 * This class is responsible for drawing 3d models, hud, process inputs and play sounds.
 * Also, it is responsible for loading/disposing of the required resources
 */
class Game3dScreen : KtxScreen, KtxInputAdapter, ServerMessageProcessor {
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
    val environment = Environment()

    // game state
    private var gameName: String = "baseq2"
    private var spawnCount = 0
    private var playercount = 1
    private var levelString: String = ""
    private val clientEntities = Array(MAX_EDICTS) { ClientEntity() }

    private var previousFrame: ClientFrame? = ClientFrame() // the frame that we will delta from (for PlayerInfo & PacketEntities)
    private val currentFrame = ClientFrame() // latest frame information received from the server
    private var surpressCount = 0 // number of messages rate supressed
    private val frames: Array<ClientFrame> = Array(Defines.UPDATE_BACKUP) { ClientFrame() }
    private var time: Int = 0 // this is the time value that the client is rendering at.  always <= cls.realtime
    private var parse_entities: Int = 0 // index (not anded off) into cl_parse_entities[]
    private val cl_parse_entities = Array<entity_state_t>(Defines.MAX_PARSE_ENTITIES) { entity_state_t(null) }

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
            currentFrame.serverframe,
            userCommands[oldestCmdIndex],
            userCommands[oldCmdIndex],
            userCommands[cmdIndex],
            outgoingSequence
        )
    }


    /**
     * CL_ParseServerData
     */
    override fun processServerDataMessage(msg: ServerDataMessage) {
        gameName = msg.gameName.ifBlank { "baseq2" }
        levelString = msg.levelString
        playercount = msg.playerNumber
        spawnCount = msg.spawnCount
    }

    override fun processConfigStringMessage(msg: ConfigStringMessage) {
        configStrings[msg.index]!!.value = msg.config
    }

    override fun processBaselineMessage(msg: SpawnBaselineMessage) {
        clientEntities[msg.entityState.number].baseline.set(msg.entityState)
    }

    /**
     * CL_ParsePlayerstate
     */
    override fun processPlayerInfoMessage(msg: PlayerInfoMessage) {
        val state = currentFrame.playerstate

        // clear to old value before delta parsing
        val deltaFrame = previousFrame
        if (deltaFrame == null) {
            state.clear()
        } else {
            state.set(deltaFrame.playerstate)
        }

        //
        // parse the pmove_state_t
        //
        if ((msg.deltaFlags and Defines.PS_M_TYPE) != 0)
            state.pmove.pm_type = msg.currentState.pmove.pm_type;

//        if (ClientGlobals.cl.attractloop)
//            state.pmove.pm_type = Defines.PM_FREEZE; // demo playback

        if ((msg.deltaFlags and Defines.PS_M_ORIGIN) != 0)
            state.pmove.origin = msg.currentState.pmove.origin;
        if ((msg.deltaFlags and Defines.PS_M_VELOCITY) != 0)
            state.pmove.velocity = msg.currentState.pmove.velocity;
        if ((msg.deltaFlags and Defines.PS_M_TIME) != 0)
            state.pmove.pm_time = msg.currentState.pmove.pm_time;
        if ((msg.deltaFlags and Defines.PS_M_FLAGS) != 0)
            state.pmove.pm_flags = msg.currentState.pmove.pm_flags;
        if ((msg.deltaFlags and Defines.PS_M_GRAVITY) != 0)
            state.pmove.gravity = msg.currentState.pmove.gravity;
        if ((msg.deltaFlags and Defines.PS_M_DELTA_ANGLES) != 0)
            state.pmove.delta_angles = msg.currentState.pmove.delta_angles;
        //
        // parse the rest of the player_state_t
        //
        if ((msg.deltaFlags and Defines.PS_VIEWOFFSET) != 0)
            state.viewoffset = msg.currentState.viewoffset;
        if ((msg.deltaFlags and Defines.PS_VIEWANGLES) != 0)
            state.viewangles = msg.currentState.viewangles;
        if ((msg.deltaFlags and Defines.PS_KICKANGLES) != 0)
            state.kick_angles = msg.currentState.kick_angles;
        if ((msg.deltaFlags and Defines.PS_WEAPONINDEX) != 0)
            state.gunindex = msg.currentState.gunindex;
        if ((msg.deltaFlags and Defines.PS_WEAPONFRAME) != 0) {
            state.gunframe = msg.currentState.gunframe;
            state.gunoffset = msg.currentState.gunoffset;
            state.gunangles = msg.currentState.gunangles;
        }
        if ((msg.deltaFlags and Defines.PS_BLEND) != 0)
            state.blend = msg.currentState.blend;
        if ((msg.deltaFlags and Defines.PS_FOV) != 0)
            state.fov = msg.currentState.fov;
        if ((msg.deltaFlags and Defines.PS_RDFLAGS) != 0)
            state.rdflags = msg.currentState.rdflags;

        // copy only changed stats
        for (i in (0..<Defines.MAX_STATS)) {
            if ((msg.statbits and (1 shl i)) != 0) {
                state.stats[i] = msg.currentState.stats[i];
            }
        }
    }

    /**
     * Also compute the previous frame
     */
    override fun processServerFrameHeader(msg: FrameHeaderMessage) {
        // update current frame
        currentFrame.reset()
        currentFrame.serverframe = msg.frameNumber
        currentFrame.deltaframe = msg.lastFrame
        currentFrame.servertime = currentFrame.serverframe * 100
        surpressCount = msg.suppressCount

        // If the frame is delta compressed from data that we
        // no longer have available, we must suck up the rest of
        // the frame, but not use it, then ask for a non-compressed
        // message

        // determine delta frame:
        val deltaFrame: ClientFrame?
        if (currentFrame.deltaframe <= 0) {
            // uncompressed frame, don't need a delta frame
            currentFrame.valid = true // uncompressed frame
            deltaFrame = null
        } else {
            deltaFrame = frames[currentFrame.deltaframe and Defines.UPDATE_MASK]
            if (!deltaFrame.valid) { // should never happen
                Com.Printf("Delta from invalid frame (not supposed to happen!).\n")
            }
            if (deltaFrame.serverframe != currentFrame.deltaframe) {
                // The frame that the server did the delta from is too old, so we can't reconstruct it properly.
                Com.Printf("Delta frame too old.\n")
            } else if (parse_entities - deltaFrame.parse_entities > Defines.MAX_PARSE_ENTITIES - 128) {
                Com.Printf("Delta parse_entities too old.\n")
            } else {
                currentFrame.valid = true  // valid delta parse
            }
        }
        previousFrame = deltaFrame

        // clamp time
        time = time.coerceIn(currentFrame.servertime - 100, currentFrame.servertime)

        // read areabits
        System.arraycopy(msg.areaBits, 0, currentFrame.areabits, 0, msg.areaBits.size);
    }

    /**
     * CL_ParsePacketEntities
     * todo: fix nullability issues, remove !! unsafe dereferences, check duplicate fragments
     */
    override fun processPacketEntitiesMessage(msg: PacketEntitiesMessage): Boolean {
        currentFrame.parse_entities = parse_entities
        currentFrame.num_entities = 0

        // delta from the entities present in oldframe
        val oldFrame = previousFrame
        var oldnum = 99999
        var oldstate: entity_state_t? = null

        if (oldFrame != null) {
            oldstate = cl_parse_entities[oldFrame.parse_entities and (Defines.MAX_PARSE_ENTITIES - 1)]
            oldnum = oldstate.number
        } else {
            // uncompressed frame: no delta required
        }
        var oldindex = 0
        msg.updates.forEach { update ->
            // while we haven't reached entities in the new frame
            val newEntityIndex = update.header.number
            while (oldnum < newEntityIndex) {
                // one or more entities from the old packet are unchanged,
                // copy them to the new frame
                DeltaEntity(currentFrame, oldnum, oldstate!!, null)
                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    oldnum = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    oldnum = oldstate!!.number
                }
            }

            // oldnum is either 99999 or has reached newnum value
            if ((update.header.flags and Defines.U_REMOVE) != 0) {
                // the entity present in oldframe is not in the current frame
                // fixme: assert oldnum == u.header.number
                // otherwise we are removing (=not including it in the new frame) an entity that wasn't there O_o

                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    oldnum = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    oldnum = oldstate!!.number
                }
                return@forEach
            }

            if (oldnum == newEntityIndex) {
                // delta from previous state

                DeltaEntity(currentFrame, newEntityIndex, oldstate!!, update)
                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    oldnum = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    oldnum = oldstate!!.number
                }

            } else if (oldnum > newEntityIndex) {
                // delta from baseline
                DeltaEntity(currentFrame, newEntityIndex, clientEntities[newEntityIndex].baseline, update)
            }
        }

        /*
         any remaining entities in the old frame are copied over,
         one or more entities from the old packet are unchanged
        */
        while (oldnum != 99999) {
            DeltaEntity(currentFrame, oldnum, oldstate!!, null);
            // fixme: same piece of code asdf123
            oldindex++
            if (oldindex >= previousFrame!!.num_entities) {
                oldnum = 99999
            } else {
                oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                oldnum = oldstate!!.number
            }

        }

        // save the frame off in the backup array for later delta comparisons
        frames[currentFrame.serverframe and Defines.UPDATE_MASK].set(currentFrame)

        // if valid: todo: FireEntityEvents, CL_pred.CheckPredictionError

        // getting a valid frame message ends the connection process
        return currentFrame.valid
    }

    override fun processSoundMessage(msg: SoundMessage) {
        val sound = configStrings[msg.soundIndex]?.resource as? Sound
        println("Playing sound ${msg.soundIndex} ${sound}")
        sound?.play() // todo: use msg.volume, attenuation, etc
    }

    private fun DeltaEntity(frame: ClientFrame, newnum: Int, old: entity_state_t, update: EntityUpdate?) {
        val ent: ClientEntity = clientEntities[newnum]
        val state: entity_state_t = cl_parse_entities[parse_entities and (Defines.MAX_PARSE_ENTITIES - 1)]
        parse_entities++
        frame.num_entities++

        state.set(old)
        state.number = newnum
        state.event = 0
        Math3D.VectorCopy(old.origin, state.old_origin)

        if (update != null) {
            state.setByFlags(update.newState, update.header.flags)
        }

        // some data changes will force no lerping
        if (state.modelindex != ent.current.modelindex
            || state.modelindex2 != ent.current.modelindex2
            || state.modelindex3 != ent.current.modelindex3
            || state.modelindex4 != ent.current.modelindex4
            || abs(state.origin[0] - ent.current.origin[0]) > 512
            || abs(state.origin[1] - ent.current.origin[1]) > 512
            || abs(state.origin[2] - ent.current.origin[2]) > 512
            || state.event == Defines.EV_PLAYER_TELEPORT
            || state.event == Defines.EV_OTHER_TELEPORT) {
            ent.serverframe = -99
        }

        if (ent.serverframe != currentFrame.serverframe - 1) {
            // wasn't in last update, so initialize some things
            ent.trailcount = 1024 // for diminishing rocket / grenade trails
            // duplicate the current state so lerping doesn't hurt anything
            ent.prev.set(state)
            if (state.event == Defines.EV_OTHER_TELEPORT) {
                Math3D.VectorCopy(state.origin, ent.prev.origin)
                Math3D.VectorCopy(state.origin, ent.lerp_origin)
            } else {
                Math3D.VectorCopy(state.old_origin, ent.prev.origin)
                Math3D.VectorCopy(state.old_origin, ent.lerp_origin)
            }
        } else { // shuffle the last state to previous
            // Copy !
            ent.prev.set(ent.current)
        }
        ent.serverframe = currentFrame.serverframe
        // Copy !
        ent.current.set(state)
    }
}

private val basedir = System.getProperty("basedir")
