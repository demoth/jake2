package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Defines.*
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.usercmd_t
import jake2.qcommon.util.Math3D
import ktx.app.KtxScreen
import org.demoth.cake.ClientEntity
import org.demoth.cake.ClientFrame
import org.demoth.cake.ServerMessageProcessor
import org.demoth.cake.modelviewer.BspLoader
import org.demoth.cake.modelviewer.Md2ModelLoader
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
class Game3dScreen : KtxScreen, InputProcessor, ServerMessageProcessor {
    private var precached: Boolean = false

    // model instances to be drawn - updated on every server frame
    private val models = ArrayList<ClientEntity>()
    private val modelBatch: ModelBatch
    private var levelModel: ClientEntity? = null

    private val camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

    var deltaTime: Float = 0f

    // todo: extract into a separate class, will be useful for the model viewer
    private val cameraInputController = object : CameraInputController(camera) {
        private val tmpV1 = Vector3()
        init {
            translateUnits = 500f
        }

        override fun process(deltaX: Float, deltaY: Float, button: Int): Boolean {
            if (button == rotateButton) {
                // PITCH: rotate around local "right" axis
                tmpV1.set(camera.direction).crs(camera.up).nor()
                camera.rotateAround(target, tmpV1, deltaY * rotateAngle)

                // YAW: rotate around global Z (which we've set as up)
                camera.rotateAround(target, Vector3.Z, -deltaX * rotateAngle)
                if (autoUpdate) camera.update()
            } else super.process(deltaX, deltaY, button)
            return true
        }
    }

    /**
     * Store all configuration related to the current map.
     * Updated from server
     */
    val configStrings = Array<Config?>(MAX_CONFIGSTRINGS) { Config("") } // fixme: decide if nullable or blank value

    private val userCommands = Array(CMD_BACKUP) { usercmd_t() }
    private val environment = Environment()

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
    // entity states - updated during processing of [PacketEntitiesMessage]
    private val cl_parse_entities = Array(Defines.MAX_PARSE_ENTITIES) { entity_state_t(null) }

    private var lerpFrac: Float = 0f

    init {
        camera.position.set(0f, 0f, 0f);
        camera.near = 1f
        camera.far = 4096f
        camera.up.set(0f, 0f, 1f) // make z up
        camera.direction.set(0f, 1f, 0f) // make y forward

        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.2f, 0.8f))

        // create camera
        camera.update()
        modelBatch = ModelBatch()
    }

    override fun render(delta: Float) {
        if (!precached)
            return



        modelBatch.begin(camera)
        models.forEach {

            // apply client side effects
            if (it.current.effects and EF_ROTATE != 0) {
                // rotate the model Instance, should to 180 degrees in 1 second
                it.modelInstance.transform.rotate(Vector3.Z, deltaTime * 180f)
            }

            modelBatch.render(it.modelInstance, environment);
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
        val brushModels = BspLoader("$basedir/$gameName/").loadBspModels(mapFile)
        brushModels.forEachIndexed { index, model ->
            val configString = configStrings[CS_MODELS + index + 1]
            check(configString != null) { "Missing brush model for ${configStrings[CS_MODELS + index + 1]?.value}" }
            if (index != 0)
                check(configString.value == "*$index") { "Wrong config string value for inline model" }
            configString.resource = model
        }

        // the level will not come as a entity, it is expected to be all the time, so we can instantiate it right away
        levelModel = ClientEntity().apply {
            modelInstance = ModelInstance(brushModels.first())
        }

        // load md2 models
        // index of md2 models in the config string
        val startIndex = CS_MODELS + brushModels.size // +1 and -1
        for (i in 1 until MAX_MODELS) {
            configStrings[startIndex + i]?.let { s ->
                if (s.value.isNotEmpty()) {
                    if (s.value.startsWith("#")) {
                        // TODO: handle view models separately
                    } else {
                        // /models/ is already part of the value (in contrast to sounds)
                        val file = File("$basedir/$gameName/${s.value}")
                        println("Model for $s exists: ${file.exists()}")
                        s.resource = Md2ModelLoader().loadMd2Model(file)
                    }
                }
            }
        }

        // load sounds starting from CS_SOUNDS until MAX_SOUNDS
        for (i in 1 until MAX_SOUNDS) {
            configStrings[CS_SOUNDS + i]?.let {s ->
                if (s.value.isNotEmpty()) {
                    if (s.value.startsWith("*")) { // skip sexed sounds for now
                        // TODO: implement male/female/cyborg sounds
                    } else {
                        println("precache sound ${s.value}: ")
                        val soundPath = "$basedir/$gameName/sound/${s.value}"
                        if (File(soundPath).exists()) {
                            s.resource = Gdx.audio.newSound(Gdx.files.absolute(soundPath))
                        } else {
                            // TODO: Find sound case insensitive
                            println("TODO: Find sound case insensitive: ${s.value}") //
                        }
                    }
                }
            }
        }
        precached = true
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
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            cmd.forwardmove = -100 // todo: calculate based on client prediction
        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            cmd.sidemove = -100 // todo: calculate based on client prediction
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            cmd.sidemove = 100 // todo: calculate based on client prediction
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
        currentFrame.parse_entities = parse_entities // save ring buffer head
        currentFrame.num_entities = 0

        // delta from the entities present in oldframe
        val oldFrame = previousFrame
        var entityIdOldFrame = 99999
        var oldstate: entity_state_t? = null

        if (oldFrame != null) {
            oldstate = cl_parse_entities[oldFrame.parse_entities and (Defines.MAX_PARSE_ENTITIES - 1)]
            entityIdOldFrame = oldstate.number
        } else {
            // uncompressed frame: no delta required
        }
        var oldindex = 0
        msg.updates.forEach { update ->
            // while we haven't reached entities in the new frame
            val entityIdNewFrame = update.header.number
            while (entityIdOldFrame < entityIdNewFrame) {
                // one or more entities from the old packet are unchanged,
                // copy them to the new frame
                DeltaEntity(currentFrame, entityIdOldFrame, oldstate!!, null)
                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    entityIdOldFrame = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    entityIdOldFrame = oldstate!!.number
                }
            }

            // oldnum is either 99999 or has reached newnum value
            if ((update.header.flags and Defines.U_REMOVE) != 0) {
                // the entity present in oldframe is not in the current frame
                // fixme: assert entityIdOldFrame == u.header.number
                // otherwise we are removing (=not including it in the new frame) an entity that wasn't there O_o

                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    entityIdOldFrame = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    entityIdOldFrame = oldstate!!.number
                }
                return@forEach //
            }

            if (entityIdOldFrame == entityIdNewFrame) {
                // delta from previous state

                DeltaEntity(currentFrame, entityIdNewFrame, oldstate!!, update)
                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    entityIdOldFrame = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    entityIdOldFrame = oldstate!!.number
                }

            } else if (entityIdOldFrame > entityIdNewFrame) {
                // delta from baseline
                DeltaEntity(currentFrame, entityIdNewFrame, clientEntities[entityIdNewFrame].baseline, update)
            }
        }

        /*
         any remaining entities in the old frame are copied over,
         one or more entities from the old packet are unchanged
        */
        while (entityIdOldFrame != 99999) {
            DeltaEntity(currentFrame, entityIdOldFrame, oldstate!!, null);
            // fixme: same piece of code asdf123
            oldindex++
            if (oldindex >= previousFrame!!.num_entities) {
                entityIdOldFrame = 99999
            } else {
                oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                entityIdOldFrame = oldstate!!.number
            }

        }

        // save the frame off in the backup array for later delta comparisons
        frames[currentFrame.serverframe and Defines.UPDATE_MASK].set(currentFrame)

        // if valid: todo: FireEntityEvents, CL_pred.CheckPredictionError

        // getting a valid frame message ends the connection process
        return currentFrame.valid
    }

    override fun processSoundMessage(msg: SoundMessage) {
        val config = configStrings[Defines.CS_SOUNDS + msg.soundIndex]
        val sound = config?.resource as? Sound
        println("Playing sound ${msg.soundIndex} (${config?.value}")
        sound?.play() // todo: use msg.volume, attenuation, etc
    }

    /**
     * Update entity based on the delta [update] received from server and it's previous state.
     */
    private fun DeltaEntity(frame: ClientFrame, newnum: Int, old: entity_state_t, update: EntityUpdate?) {
        val entity: ClientEntity = clientEntities[newnum]
        // parse_entities now points to the last state from last frame
        val newState: entity_state_t = cl_parse_entities[parse_entities and (Defines.MAX_PARSE_ENTITIES - 1)]
        parse_entities++ // we will need this for the next frame
        frame.num_entities++

        newState.set(old)
        newState.number = newnum
        newState.event = 0
        Math3D.VectorCopy(old.origin, newState.old_origin)

        if (update != null) {
            newState.setByFlags(update.newState, update.header.flags)
        }

        // some data changes will force no lerping
        if (newState.modelindex != entity.current.modelindex
            || newState.modelindex2 != entity.current.modelindex2
            || newState.modelindex3 != entity.current.modelindex3
            || newState.modelindex4 != entity.current.modelindex4
            || abs(newState.origin[0] - entity.current.origin[0]) > 512
            || abs(newState.origin[1] - entity.current.origin[1]) > 512
            || abs(newState.origin[2] - entity.current.origin[2]) > 512
            || newState.event == Defines.EV_PLAYER_TELEPORT
            || newState.event == Defines.EV_OTHER_TELEPORT) {
            entity.serverframe = -99
        }

        if (entity.serverframe == currentFrame.serverframe - 1) { // shuffle the last state to previous
            // Copy !
            entity.prev.set(entity.current)
        } else {
            // wasn't in last update, so initialize some things
            entity.trailcount = 1024 // for diminishing rocket / grenade trails
            // duplicate the current state so lerping doesn't hurt anything
            entity.prev.set(newState)
            if (newState.event == Defines.EV_OTHER_TELEPORT) {
                Math3D.VectorCopy(newState.origin, entity.prev.origin)
                Math3D.VectorCopy(newState.origin, entity.lerp_origin)
            } else {
                Math3D.VectorCopy(newState.old_origin, entity.prev.origin)
                Math3D.VectorCopy(newState.old_origin, entity.lerp_origin)
            }
        }
        entity.serverframe = currentFrame.serverframe
        // Copy !
        entity.current.set(newState) // fixme: use assignment instead of copying fields?
    }

    // create/modify model instances
    // apply entity transform to the model instance
    // AddPacketEntities
    fun postReceive() {
        models.clear()
        // todo: put to a persistent client entities list?
        models += ClientEntity().apply { modelInstance = createGrid(16f, 8) }
        models += ClientEntity().apply { modelInstance = createOriginArrows(16f) }
        if (levelModel != null) {
            models += levelModel!!
        }

        // entities in the current frame
        // draw client entities, check jake2.client.CL_ents#AddPacketEntities
        (0..<currentFrame.num_entities).forEach { // todo: clientEntities.forEach {...
            val s1 = cl_parse_entities[currentFrame.parse_entities + it and (MAX_PARSE_ENTITIES - 1)]
            val cent = clientEntities[s1.number]
            // if modelIndex != 0, grab a model from the corresponding config string and make a model instance from it
            // fixme: shouldn't be done on every from for every entity.
            // Store in the entity_state_t?
            if (cent.modelInstance == null) {
                val modelIndex = s1.modelindex
                if (modelIndex != 0) {
                    val model = configStrings[CS_MODELS + modelIndex]?.resource as? Model
                    if (model != null) {
                        cent.modelInstance = ModelInstance(model)
                        // todo: apply entity transform to the model instance
                    }
                }
            }
            val modelInstance = cent.modelInstance
            if (modelInstance != null) {
                val origin = s1.origin
                // set the model instance position as origin
                // transform the translation vector from q2 to libgdx
                modelInstance.transform.setTranslation(origin[0], origin[1], origin[2])
                // todo: apply rotation
                // val angles = s1.angles
                // modelInstance.transform.rotate(angles[0], angles[1], angles[2])
                models += cent
            }
        }

        // todo
    }

    // client only movement: temporary
    override fun keyDown(keycode: Int): Boolean {
        return cameraInputController.keyDown(keycode)
    }

    override fun keyUp(keycode: Int): Boolean {
        return cameraInputController.keyUp(keycode)
    }

    override fun keyTyped(character: Char): Boolean {
        return cameraInputController.keyTyped(character)
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return cameraInputController.touchDown(screenX, screenY, pointer, button)
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return cameraInputController.touchUp(screenX, screenY, pointer, button)
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return cameraInputController.touchCancelled(screenX, screenY, pointer, button)
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return cameraInputController.touchDragged(screenX, screenY, pointer)
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return cameraInputController.mouseMoved(screenX, screenY)
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return cameraInputController.scrolled(amountX, amountY)
    }
}

private val basedir = System.getProperty("basedir")
