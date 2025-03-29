package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.MathUtils.degRad
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import jake2.qcommon.*
import jake2.qcommon.Defines.*
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.util.Math3D
import ktx.app.KtxScreen
import ktx.graphics.use
import org.demoth.cake.*
import org.demoth.cake.modelviewer.*
import org.demoth.cake.stages.ClientCommands.*
import java.util.*
import kotlin.experimental.or
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Represents the 3d screen where the game is actually happening.
 * This class is responsible for drawing 3d models, hud, process inputs and play sounds.
 * Also, it is responsible for loading/disposing of the required resources
 */
class Game3dScreen : KtxScreen, InputProcessor, ServerMessageProcessor {
    private var precached: Boolean = false

    // model instances to be drawn - updated on every server frame
    private val visibleEntities = ArrayList<ClientEntity>()
    private val modelBatch: ModelBatch
    private var levelModel: ClientEntity? = null
    private var drawLevel = true
    private var drawEntities = true
    private val collisionModel = CM()
    private val locator = GameResourceLocator(System.getProperty("basedir"))

    private val camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

    private val cameraKeyboardRotationSpeed = 140f // degrees per second

    // the angle that the player spawned with
    private var initialYaw: Float? = null
    private var initialPitch: Float? = null

    // local camera angle
    private var localYaw: Float = 0f
    private var localPitch: Float = 0f

    var deltaTime: Float = 0f

    private val gameConfig = GameConfiguration()

    private val userCommands = Array(CMD_BACKUP) { usercmd_t() }
    private val environment = Environment()

    // game state
    private var gameName: String = "baseq2"
    private var spawnCount = 0

    private var skyBox: ModelInstance? = null
    private var drawSkybox = true

    /**
     * id of the player in the game. can be used to determine if the entity is the current player
     */
    private var playerNumber = 1
    private var levelString: String = ""
    private val clientEntities = Array(MAX_EDICTS) { ClientEntity("") }
    private var gun: ClientEntity? = null

    private var previousFrame: ClientFrame? = ClientFrame() // the frame that we will delta from (for PlayerInfo & PacketEntities)
    private val currentFrame = ClientFrame() // latest frame information received from the server
    private var surpressCount = 0 // number of messages rate supressed
    private val frames: Array<ClientFrame> = Array(Defines.UPDATE_BACKUP) { ClientFrame() }
    private var time: Int = 0 // this is the time value that the client is rendering at.  always <= cls.realtime
    private val spriteBatch = SpriteBatch()
    private val skin = Skin(Gdx.files.internal("ui/uiskin.json"))
    private val layoutExecutor = LayoutExecutor(spriteBatch, skin)

    private var parse_entities: Int = 0 // index (not anded off) into cl_parse_entities[]
    // entity states - updated during processing of [PacketEntitiesMessage]
    private val cl_parse_entities = Array(Defines.MAX_PARSE_ENTITIES) { entity_state_t(null) }

    // interpolation factor between two server frames
    private var lerpFrac: Float = 0f
    private var lerpAcc: Float = 0f

    // todo: make proper player loader
    private val playerModelPath = "players/male/tris.md2"
    private val playerSkinPath = "players/male/grunt.pcx"
    private lateinit var playerModel: Model

    private val clientSpeed: Short = 100 // todo: cvar


    // todo: think about designing an extendable client side effect system
    private val weaponSounds: HashMap<Int, Sound> = hashMapOf()
    private val weaponSoundPaths = mapOf(
        MZ_BLASTER to "weapons/blastf1a.wav",
        MZ_MACHINEGUN to "weapons/machgf1b.wav", // todo: random
        MZ_SHOTGUN to "weapons/shotgf1b.wav",
        MZ_CHAINGUN1 to "weapons/machgf1b.wav",
        MZ_CHAINGUN2 to "weapons/machgf1b.wav",
        MZ_CHAINGUN3 to "weapons/machgf1b.wav",
        MZ_RAILGUN to "weapons/railgf1a.wav",
        MZ_ROCKET to "weapons/rocklf1a.wav",
        MZ_GRENADE to "weapons/grenlf1a.wav",
        MZ_LOGIN to "weapons/grenlf1a.wav",
        MZ_LOGOUT to "weapons/grenlf1a.wav",
        MZ_RESPAWN to "weapons/grenlf1a.wav",
        MZ_BFG to "weapons/bfg__f1y.wav",
        MZ_SSHOTGUN to "weapons/sshotf1b.wav",
        MZ_HYPERBLASTER to "weapons/hyprbf1a.wav",
        MZ_ITEMRESPAWN to null,
        MZ_IONRIPPER to "weapons/rippfire.wav",
        MZ_BLUEHYPERBLASTER to "weapons/hyprbf1a.wav",
        MZ_PHALANX to "weapons/plasshot.wav",
        MZ_ETF_RIFLE to "weapons/nail1.wav",
        MZ_UNUSED to null,
        MZ_SHOTGUN2 to "weapons/shotg2.wav",
        MZ_HEATBEAM to null,
        MZ_BLASTER2 to "weapons/blastf1a.wav",
        MZ_TRACKER to "weapons/disint2.wav",
        MZ_NUKE1 to null,
        MZ_NUKE2 to null,
        MZ_NUKE4 to null,
        MZ_NUKE8 to null,
    )

    // mappings for input command: which are sent on every client update frame
    private val mouseSensitivity = 1f // todo cvar
    private var mouseWasMoved = false

    private val inputKeyMappings: MutableMap<Int, ClientCommands> = mutableMapOf(
        Input.Keys.W to in_forward,
        Input.Keys.S to in_back,
        Input.Keys.A to in_moveleft,
        Input.Keys.D to in_moveright,
        Input.Keys.SPACE to in_moveup,
        Input.Keys.C to in_movedown,
        Input.Keys.LEFT to in_left,
        Input.Keys.RIGHT to in_right,
        Input.Keys.UP to in_lookup,
        Input.Keys.DOWN to in_lookdown,
        Input.Keys.CONTROL_LEFT to in_attack,
    )

    // default.cfg
    // input mapping for string commands - sent on demand
    private val inputBindings: MutableMap<Int, String> = mutableMapOf(
        Input.Keys.NUM_1 to "use blaster",
        Input.Keys.NUM_2 to "use shotgun",
        Input.Keys.NUM_3 to "use sshotgun",
        Input.Keys.NUM_4 to "use machinegun",
        Input.Keys.NUM_5 to "use chaingun",
        Input.Keys.NUM_6 to "use grenade launcher",
        Input.Keys.NUM_7 to "use rocket launcher",
        Input.Keys.NUM_8 to "use hyperblaster",
        Input.Keys.NUM_9 to "use railgun",
        Input.Keys.NUM_0 to "use bfg",
        Input.Keys.G to "use grenades",
        Input.Keys.TAB to "inven",
        Input.Keys.F2 to "cmd help"
    )

    // todo: track time
    private val commandsState: EnumMap<ClientCommands, Boolean> = EnumMap(ClientCommands::class.java)
    private var previousX = 0f
    private var previousY = 0f
    private var deltaX = 0f
    private var deltaY = 0f
    private val sensitivity = 25f

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

        ClientCommands.entries.forEach { commandsState[it] = false }
    }

    private fun lerpAngle(from: Float, to: Float, fraction: Float): Float {
        var delta = to - from
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        return from + delta * fraction
    }

    override fun render(delta: Float) {
        if (!precached)
            return

        val serverFrameTime = 1f/10f // 10Hz server updates
        lerpFrac = (lerpAcc / serverFrameTime).coerceIn(0f, 1f)

        updatePlayerCamera(lerpFrac)
        //updatePlayerGun(lerpFrac)

        modelBatch.begin(camera)

        if (drawSkybox)
            skyBox?.let {
                Gdx.gl.glDepthMask(false)
                // TODO: rotate skybox: skyBox.transform.setToRotation(...)
                it.transform.setTranslation(camera.position) // follow the camera
                modelBatch.render(skyBox)
                Gdx.gl.glDepthMask(true)
            }

        visibleEntities.forEach {

            // apply client side effects
            if (it.current.effects and EF_ROTATE != 0) {
                // rotate the model Instance, should to 180 degrees in 1 second
                it.modelInstance.transform.rotate(Vector3.Z, deltaTime * 180f)
            } else {
                val pitch = lerpAngle(it.prev.angles[PITCH], it.current.angles[PITCH], lerpFrac)
                val yaw = lerpAngle(it.prev.angles[YAW], it.current.angles[YAW], lerpFrac)
                // todo: roll

                it.modelInstance.transform.setToRotation(Vector3.X, pitch)
                it.modelInstance.transform.rotate(Vector3.Z, yaw)

            }

            // interpolate position
            val x = it.prev.origin[0] + (it.current.origin[0] - it.prev.origin[0]) * lerpFrac
            val y = it.prev.origin[1] + (it.current.origin[1] - it.prev.origin[1]) * lerpFrac
            val z = it.prev.origin[2] + (it.current.origin[2] - it.prev.origin[2]) * lerpFrac

            it.modelInstance.transform.setTranslation(x, y, z)

            modelBatch.render(it.modelInstance, environment);
        }
        modelBatch.end()

        // draw hud
        spriteBatch.use {
            layoutExecutor.drawCrosshair(
                screenWidth = Gdx.graphics.width,
                screenHeight = Gdx.graphics.height,
            )

            layoutExecutor.executeLayoutString(
                layout = gameConfig.getStatusBarLayout(),
                serverFrame = currentFrame.serverframe,
                stats = currentFrame.playerstate.stats,
                screenWidth = Gdx.graphics.width,
                screenHeight = Gdx.graphics.height,
                gameConfig = gameConfig
            )

            // draw additional layout, like help or score
            // SRC.DrawLayout
            if ((currentFrame.playerstate.stats[STAT_LAYOUTS].toInt() and 1) != 0) {
                layoutExecutor.executeLayoutString(
                    layout = gameConfig.layout,
                    serverFrame = currentFrame.serverframe,
                    stats = currentFrame.playerstate.stats,
                    screenWidth = Gdx.graphics.width,
                    screenHeight = Gdx.graphics.height,
                    gameConfig = gameConfig
                )
            }
            // draw additional layout, like help or score
            // CL_inv.DrawInventory
            if ((currentFrame.playerstate.stats[STAT_LAYOUTS].toInt() and 2) != 0) {
                layoutExecutor.drawInventory(
                    playerstate = currentFrame.playerstate,
                    screenWidth = Gdx.graphics.width,
                    screenHeight = Gdx.graphics.height,
                    gameConfig = gameConfig
                )
            }

        }
        lerpAcc += delta
    }

    override fun dispose() {
        spriteBatch.dispose()
        modelBatch.dispose()
        // should we dispose the model instances first?
        gameConfig.dispose()
    }

    /**
     * Load resources into the memory, that are referenced in the config strings or assumed always required (like weapon sounds)
     * todo: move to assetManager, make resource loading asynchronous
     */
    fun precache() {
        // load resources referenced in the config strings

        // load the level
        val mapName = gameConfig.getMapName() // fixme: disconnect with an error if is null
        val bsp = locator.findMap(mapName!!) // todo: cache
        val brushModels = BspLoader(locator).loadBspModels(bsp)

        // load inline bmodels
        brushModels.forEachIndexed { index, model ->
            val configString = gameConfig[CS_MODELS + index + 1]
            check(configString != null) { "Missing brush model for ${gameConfig[CS_MODELS + index + 1]?.value}" }
            if (index != 0)
                check(configString.value == "*$index") { "Wrong config string value for inline model" }
            configString.resource = model
        }

        // the level will not come as a entity, it is expected to be all the time, so we can instantiate it right away
        levelModel = ClientEntity("level").apply {
            modelInstance = ModelInstance(brushModels.first())
        }

        collisionModel.CM_LoadMapFile(bsp, mapName, IntArray(1) {0})

        // load md2 models
        // index of md2 models in the config string
        val startIndex = CS_MODELS + 1 + brushModels.size
        for (i in startIndex .. MAX_MODELS) {
            gameConfig[i]?.let { config ->
                config.value.let {
                    config.resource = Md2ModelLoader(locator).loadMd2Model(it, skinIndex = 0, frameIndex = 0)
                }
            }
        }

        // temporary: load one fixed player model
        playerModel = Md2ModelLoader(locator).loadMd2Model(
            modelName = playerModelPath,
            playerSkin = playerSkinPath,
            skinIndex = 0,
            frameIndex = 0
        )!!

        gameConfig.getSounds().forEach { config ->
            if (config != null) {
                val soundFile = locator.findSound(config.value)
                if (soundFile != null) {
                    config.resource = Gdx.audio.newSound(soundFile)
                }
            }
        }

        gameConfig.getImages().forEach { config ->
            if (config != null) {
                val textureFile = locator.findImage("${config.value}.pcx", "pics")
                if (textureFile != null) {
                    config.resource = Texture(fromPCX(PCX(textureFile)))
                }
            }
        }


        gameConfig.getSkyname()?.let { skyName ->
            skyBox = SkyLoader(locator).load(skyName)
        }


        // these are expected to be loaded
        weaponSoundPaths.forEach { (index, path) ->
            if (path != null) {
                locator.findSound(path)?.let {
                    weaponSounds[index] = Gdx.audio.newSound(it)
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

        if (commandsState[in_attack] == true) {
            cmd.buttons = cmd.buttons or BUTTON_ATTACK.toByte()
        }

        if (commandsState[in_forward] == true) {
            cmd.forwardmove = clientSpeed;
        }

        if (commandsState[in_back] == true) {
            cmd.forwardmove = (-clientSpeed).toShort()
        }

        if (commandsState[in_moveleft] == true) {
            cmd.sidemove = (-clientSpeed).toShort()
        }

        if (commandsState[in_moveright] == true) {
            cmd.sidemove = clientSpeed
        }

        if (commandsState[in_moveup] == true) {
            cmd.upmove = clientSpeed
        }

        if (commandsState[in_movedown] == true) {
            cmd.upmove = (-clientSpeed).toShort()
        }

        // degrees
        // If we haven't initialized yet, do so
        if (initialYaw == null) {
            initialYaw = currentFrame.playerstate.viewangles[YAW]
            localYaw = initialYaw!!
        }

        if (initialPitch == null) {
            initialPitch = currentFrame.playerstate.viewangles[PITCH]
            localPitch = initialPitch!!
        }

        // update camera direction right on the client side and sent to the server
        if (commandsState[in_left] == true || commandsState[in_right] == true) {
            var delta = deltaTime * cameraKeyboardRotationSpeed
            if (commandsState[in_right] == true) {
                delta *= -1
            }

            localYaw += delta

        }


        // set the angles
        cmd.angles[PITCH] = Math3D.ANGLE2SHORT(localPitch - initialPitch!!).toShort()
        cmd.angles[YAW] = Math3D.ANGLE2SHORT(localYaw - initialYaw!!).toShort()
        cmd.angles[ROLL] = 0

        cmd.msec = 16 // todo: calculate based on time between client frames (actually between "sending" frames)
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
     * CL_CalcViewValues
     * Updates camera transformation according to player input and player info
     */
    private fun updatePlayerCamera(lerp: Float) {
        val currentState = currentFrame.playerstate
        val newX = currentState.viewoffset[0] + (currentState.pmove.origin[0]) * 0.125f
        val newY = currentState.viewoffset[1] + (currentState.pmove.origin[1]) * 0.125f
        val newZ = currentState.viewoffset[2] + (currentState.pmove.origin[2]) * 0.125f

        val previousState = previousFrame?.playerstate ?: currentState
        // todo: check if previousFrame.serverframe +1 = currentFrame.serverFrame
        // todo: check if previousFrame.valid

        var oldX = previousState.viewoffset[0] + (previousState.pmove.origin[0]) * 0.125f
        var oldY = previousState.viewoffset[1] + (previousState.pmove.origin[1]) * 0.125f
        var oldZ = previousState.viewoffset[2] + (previousState.pmove.origin[2]) * 0.125f

        // from CL_ents.java:
        // see if the player entity was teleported this frame
        if (abs(oldX - newX) > 256 * 8
            || abs(oldY - newY) > 256 * 8
            || abs(oldZ - newZ) > 256 * 8)
        {
            // don't interpolate
            // fixme: what if teleportation was for a short distance?
            oldX = newX
            oldY = newY
            oldZ = newZ
        }

        val interpolatedX = oldX + (newX - oldX) * lerp
        val interpolatedY = oldY + (newY - oldY) * lerp
        val interpolatedZ = oldZ + (newZ - oldZ) * lerp

        // todo: think about - smooth out stair climbing - is it really needed?

        camera.position.set(
            interpolatedX,
            interpolatedY,
            interpolatedZ
        )

        // process mouse movement
        if (mouseWasMoved) {
            localYaw -= deltaX
            localPitch += deltaY

            // wrap yaw
            if (localYaw <= -180f) localYaw += 360f
            if (localYaw >= 180f) localYaw -= 360f

            // first wrap the pitch
            if (localPitch <= -180f) localPitch += 360f
            if (localPitch >= 180f) localPitch -= 360f

            // clamp pitch
            if (localPitch >= 89f) localPitch = 89f
            if (localPitch <= -89f) localPitch = -89f


            mouseWasMoved = false
        }

        // calculate where the camera should look at on this frame
        val direction: Vector3 = if (currentState.pmove.pm_type == PM_NORMAL || currentState.pmove.pm_type == PM_SPECTATOR) {
            // calculate the camera direction based on local angles + kick angle.
            // don't need to interpolate rotation because it is locally based
            val kickAnglePitch = lerpAngle(previousState.kick_angles[PITCH], currentState.kick_angles[PITCH], lerp)
            val kickAngleYaw = lerpAngle(previousState.kick_angles[YAW], currentState.kick_angles[YAW], lerp)
            quakeForward(
                localPitch + kickAnglePitch,
                localYaw + kickAngleYaw
            )


        } else {
            // no camera controls for PM_DEAD PM_GIB PM_FREEZE, just interpolate server values
            quakeForward(
                lerpAngle(previousState.viewangles[PITCH], currentState.viewangles[PITCH], lerp),
                lerpAngle(previousState.viewangles[YAW], currentState.viewangles[YAW], lerp),
            )
        }

        camera.direction.set(direction)
        camera.update()

        // update the gun position
        val currentGunOffsetX = currentState.gunoffset[0]
        val currentGunOffsetY = currentState.gunoffset[1]
        val currentGunOffsetZ = currentState.gunoffset[2]
        val oldGunOffsetX = previousState.gunoffset[0]
        val oldGunOffsetY = previousState.gunoffset[1]
        val oldGunOffsetZ = previousState.gunoffset[2]

        // set the previous and current state: interpolation will happen with all other client entities
        // ideally, we want the gun to follow the camera direction.
        // The problem is that gun position/rotation is partially local (localYaw) and partially replicated (gun offset)
        // todo: think of a better approach
        gun?.prev?.origin = floatArrayOf(
            oldGunOffsetX + oldX,
            oldGunOffsetY + oldY,
            oldGunOffsetZ + oldZ,
        )
        gun?.current?.origin = floatArrayOf(
            currentGunOffsetX + newX,
            currentGunOffsetY + newY,
            currentGunOffsetZ + newZ,
        )
        // not using pitch because the gun will not be visible at all.
        // FIXME: change after md2 animation is implemented
        gun?.current?.angles = floatArrayOf(0f, localYaw, 0f)
        gun?.prev?.angles = floatArrayOf(0f, localYaw, 0f)
    }

    private fun quakeForward(pitchDeg: Float, yawDeg: Float): Vector3 {
        val pitch = pitchDeg * degRad
        val yaw = yawDeg * degRad
        // roll not used in forward direction

        val cp = cos(pitch)
        val sp = sin(pitch)
        val cy = cos(yaw)
        val sy = sin(yaw)

        return Vector3(
            cp * cy,
            cp * sy,
            -sp
        )
    }


    /**
     * CL_ParseServerData
     */
    override fun processServerDataMessage(msg: ServerDataMessage) {
        gameName = msg.gameName.ifBlank { "baseq2" }
        levelString = msg.levelString
        playerNumber = msg.playerNumber
        spawnCount = msg.spawnCount

        locator.gameName = gameName
    }

    override fun processConfigStringMessage(msg: ConfigStringMessage) {
        gameConfig[msg.index]?.resource?.dispose() // todo: check if it can even happen?
        gameConfig[msg.index] = Config(msg.config)
    }

    override fun processBaselineMessage(msg: SpawnBaselineMessage) {
        clientEntities[msg.entityState.number].baseline.set(msg.entityState)
    }

    /**
     * CL_ParsePlayerstate
     * todo: move to common?
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
        val config = gameConfig[Defines.CS_SOUNDS + msg.soundIndex]
        val sound = config?.resource as? Sound // else warning?
        println("Playing sound ${msg.soundIndex} (${config?.value})")
        sound?.play() // todo: use msg.volume, attenuation, etc
    }

    override fun processWeaponSoundMessage(msg: WeaponSoundMessage) {
        // weapon type is stored in last 7 bits of the msg.type
        val weaponType = msg.type and 0x7F
        // the silenced flag is stored in the first bit
        val silenced = (msg.type and 0x80) != 0
        val sound = weaponSounds[weaponType]
        if (sound != null) {
            sound.play(if (silenced) 0.2f else 1f)
        } else {
            // todo: warning!
        }
    }

    override fun processLayoutMessage(msg: LayoutMessage) {
         gameConfig.layout = msg.layout
    }

    override fun processInventoryMessage(msg: InventoryMessage) {
        for (i in 0..<MAX_ITEMS) {
            gameConfig.inventory[i] = msg.inventory[i]
        }
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
    // AddPacketEntities
    // update visible entities based on informatino from server
    // todo: this methon handles both instancing and visibility, maybe split it?
    fun postReceive() {
        lerpAcc = 0f // reset lerp between server frames

        visibleEntities.clear()
        // todo: put to a persistent client entities list?
        visibleEntities += ClientEntity("grid").apply { modelInstance = createGrid(16f, 8) }
        visibleEntities += ClientEntity("origin").apply { modelInstance = createOriginArrows(16f) }
        if (levelModel != null && drawLevel) {
            // todo: use area visibility to draw only part of the map (visible clusters)
            visibleEntities += levelModel!!
        }

        // entities in the current frame
        // draw client entities, check jake2.client.CL_ents#AddPacketEntities
        (0..<currentFrame.num_entities).forEach { // todo: clientEntities.forEach {...
            val newState = cl_parse_entities[currentFrame.parse_entities + it and (MAX_PARSE_ENTITIES - 1)]
            val entity = clientEntities[newState.number]

            // instantiate model if not yet done
            if (entity.modelInstance == null) {
                val modelIndex = newState.modelindex
                if (modelIndex == 255) { // this is a player
                    // fixme: how to get which model/skin does the player have?
                    entity.modelInstance = ModelInstance(playerModel)
                    entity.name = "player"
                } else if (modelIndex != 0) {
                    val modelConfig = gameConfig[CS_MODELS + modelIndex]
                    val model = modelConfig?.resource as? Model
                    if (model != null) {
                        entity.name = modelConfig.value
                        entity.modelInstance = ModelInstance(model)
                    }
                }
            }

            // update the model instance
            if (entity.modelInstance != null
                && newState.number != playerNumber + 1 // do not draw ourselves
                && drawEntities
            ) {
                visibleEntities += entity
            }
        }

        // update player gun model
        if (gun == null) {
            // try to load the model
            val model = gameConfig[CS_MODELS + currentFrame.playerstate.gunindex]?.resource as? Model
            if (model != null) {
                gun = ClientEntity("gun").apply {
                    modelInstance = ModelInstance(model)
                }
            }
        }
        gun?.let {
            visibleEntities += it
        }
    }

    // todo: delegate to a separate class
    override fun keyDown(keycode: Int): Boolean {
        if (inputKeyMappings[keycode] != null) {
            commandsState[inputKeyMappings[keycode]] = true
            return true
        } else {
            return false
        }
    }

    override fun keyUp(keycode: Int): Boolean {
        if (inputKeyMappings[keycode] != null) {
            commandsState[inputKeyMappings[keycode]] = false
            return true
        } else if (inputBindings[keycode] != null) {
            val cmd = inputBindings[keycode]
            if (cmd != null) {
                println("Executing command: $cmd")
                Cbuf.AddText(cmd)
            }
            return true
        }

        when(keycode) {
            Input.Keys.F5 -> {
                drawSkybox = !drawSkybox
                println("Draw skybox: $drawSkybox")
                return true
            }
            Input.Keys.F6 -> {
                drawLevel = !drawLevel
                println("Draw level: $drawLevel")
                return true
            }
            Input.Keys.F7 -> {
                drawEntities = !drawEntities
                println("Draw entities: $drawEntities")
                return true
            }

        }
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.LEFT) {
            commandsState[in_attack] = true
            return true
        }
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.LEFT) {
            commandsState[in_attack] = false
            return true
        }
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return processCameraRotation(screenX, screenY)
    }


    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return processCameraRotation(screenX, screenY)
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }

    private fun processCameraRotation(screenX: Int, screenY: Int): Boolean {
        deltaX = sensitivity * (screenX - previousX) / Gdx.graphics.width
        deltaY = sensitivity * (screenY - previousY) / Gdx.graphics.height
        previousX = screenX.toFloat()
        previousY = screenY.toFloat()
        mouseWasMoved = true
        return true // consume the event
    }
}

enum class ClientCommands {
    in_moveup,
    in_movedown,
    in_left,
    in_right,
    in_forward,
    in_back,
    in_lookup,
    in_lookdown,
    in_strafe,
    in_moveleft,
    in_moveright,
    in_speed,
    in_attack,
    in_use,
    in_klook,
}