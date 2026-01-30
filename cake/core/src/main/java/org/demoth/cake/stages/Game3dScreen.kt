package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.math.MathUtils.degRad
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.*
import jake2.qcommon.Defines.*
import jake2.qcommon.exec.Cmd
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.*
import ktx.app.KtxScreen
import ktx.graphics.use
import org.demoth.cake.*
import org.demoth.cake.assets.BspLoader
import org.demoth.cake.assets.Md2ModelLoader
import org.demoth.cake.assets.SkyLoader
import org.demoth.cake.assets.createModel
import org.demoth.cake.assets.fromPCX
import org.demoth.cake.assets.getLoaded
import org.demoth.cake.modelviewer.*
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Represents the 3d screen where the game is actually happening.
 * This class is responsible for drawing 3d models, hud, process inputs and play sounds.
 * Also, it is responsible for loading/disposing of the required resources
 */
class Game3dScreen(
    val assetManager: AssetManager,
    val inputManager: InputManager = InputManager(),
    val renderState: RenderState = RenderState()
) : KtxScreen, ServerMessageProcessor, InputProcessor by inputManager {
    private var precached: Boolean = false

    private val modelBatch: ModelBatch
    private val collisionModel = CM()
    private val locator = GameResourceLocator(System.getProperty("basedir"))

    private val camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

    var deltaTime: Float = 0f

    private val gameConfig = GameConfiguration()

    private val entityManager = ClientEntityManager()
    private val environment = Environment()

    // game state
    private var gameName: String = "baseq2"
    private var spawnCount = 0

    private var skyBox: ModelInstance? = null

    /**
     * id of the player in the game. can be used to determine if the entity is the current player
     */
    private var levelString: String = ""

    private val spriteBatch = SpriteBatch()
    private val layoutExecutor = LayoutExecutor(spriteBatch)


    // interpolation factor between two server frames
    private var lerpFrac: Float = 0f

    // todo: make proper player loader
    private val playerModelPath = "players/male/tris.md2"
    private val playerSkinPath = "players/male/grunt.pcx"

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

        // todo: move this to a dedicated render class
        // force replace because the command lambdas capture the render state. fixme: make proper disposable approach
        Cmd.AddCommand("toggle_skybox", true) {
            renderState.drawSkybox = !renderState.drawSkybox
        }
        Cmd.AddCommand("toggle_level", true) {
            renderState.drawLevel = !renderState.drawLevel
        }
        Cmd.AddCommand("toggle_entities", true) {
            renderState.drawEntities = !renderState.drawEntities
        }

        modelBatch = ModelBatch(Md2ShaderProvider(initializeMd2Shader()))
    }

    // fixme: make a free internal md2 model specifically for the shader initialization, don't use q2 resources
    private fun initializeMd2Shader(): Md2Shader {
        val md2 = Md2ModelLoader(locator, assetManager)
            .loadMd2ModelData("models/monsters/berserk/tris.md2", null, 0)!!
        val model = createModel(md2.mesh, md2.material)
        val md2Instance = ModelInstance(model)

        md2Instance.userData = Md2CustomData(
            0,
            if (md2.frames > 1) 1 else 0,
            0f,
            md2.frames
        )

        val tempRenderable = Renderable()
        val md2Shader = Md2Shader(
            md2Instance.getRenderable(tempRenderable), // may not be obvious, but it's required for the shader initialization, the renderable is not used after that
            DefaultShader.Config(
                assetManager.get(vatShader),
                null, // use default fragment shader
            )
        )
        md2Shader.init()
        return md2Shader
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
        lerpFrac = (renderState.lerpAcc / serverFrameTime).coerceIn(0f, 1f)

        updatePlayerCamera(lerpFrac)
        //updatePlayerGun(lerpFrac)

        modelBatch.begin(camera)

        if (renderState.drawSkybox)
            skyBox?.let {
                Gdx.gl.glDepthMask(false)
                // TODO: rotate skybox: skyBox.transform.setToRotation(...)
                it.transform.setTranslation(camera.position) // follow the camera
                modelBatch.render(skyBox)
                Gdx.gl.glDepthMask(true)
            }

        entityManager.visibleEntities.forEach {

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

            (it.modelInstance.userData as? Md2CustomData)?.let { userData ->
                userData.interpolation = lerpFrac
            }
            modelBatch.render(it.modelInstance, environment)
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
                serverFrame = entityManager.currentFrame.serverframe,
                stats = entityManager.currentFrame.playerstate.stats,
                screenWidth = Gdx.graphics.width,
                screenHeight = Gdx.graphics.height,
                gameConfig = gameConfig
            )

            // draw additional layout, like help or score
            // SRC.DrawLayout
            if ((entityManager.currentFrame.playerstate.stats[STAT_LAYOUTS].toInt() and 1) != 0) {
                layoutExecutor.executeLayoutString(
                    layout = gameConfig.layout,
                    serverFrame = entityManager.currentFrame.serverframe,
                    stats = entityManager.currentFrame.playerstate.stats,
                    screenWidth = Gdx.graphics.width,
                    screenHeight = Gdx.graphics.height,
                    gameConfig = gameConfig
                )
            }
            // draw additional layout, like help or score
            // CL_inv.DrawInventory
            if ((entityManager.currentFrame.playerstate.stats[STAT_LAYOUTS].toInt() and 2) != 0) {
                layoutExecutor.drawInventory(
                    playerstate = entityManager.currentFrame.playerstate,
                    screenWidth = Gdx.graphics.width,
                    screenHeight = Gdx.graphics.height,
                    gameConfig = gameConfig
                )
            }

        }
        renderState.lerpAcc += delta
    }

    override fun dispose() {
        spriteBatch.dispose()
        modelBatch.dispose()
        gameConfig.disposeUnmanagedResources()
        skyBox?.model?.dispose()
        renderState.dispose() // fixme: what else should be disposed? move skybox into the renderState?
    }

    /**
     * Load resources into the memory, that are referenced in the config strings or assumed always required (like weapon sounds)
     * todo: move to assetManager, make resource loading asynchronous
     */
    fun precache() {
        // load resources referenced in the config strings

        // load the level
        val mapName = gameConfig.getMapName()!! // fixme: disconnect with an error if is null
        val mapPath = "${System.getProperty("basedir")}/$gameName/$mapName"
        assetManager.load(mapPath, ByteArray::class.java)
        assetManager.finishLoadingAsset<ByteArray>(mapPath)
        val bsp = assetManager.get(mapPath, ByteArray::class.java)
        val brushModels = BspLoader(locator, assetManager).loadBspModels(bsp)

        // load inline bmodels
        brushModels.forEachIndexed { index, model ->
            val configString = gameConfig[CS_MODELS + index + 1]
            check(configString != null) { "Missing brush model for ${gameConfig[CS_MODELS + index + 1]?.value}" }
            if (index != 0)
                check(configString.value == "*$index") { "Wrong config string value for inline model" }
            configString.resource = model
        }

        // the level will not come as a entity, it is expected to be all the time, so we can instantiate it right away
        renderState.levelModel = ClientEntity("level").apply {
            modelInstance = ModelInstance(brushModels.first())
        }

        collisionModel.CM_LoadMapFile(bsp, mapName, IntArray(1) {0})

        // load md2 models
        // index of md2 models in the config string
        val startIndex = CS_MODELS + 1 + brushModels.size
        for (i in startIndex .. MAX_MODELS) {
            gameConfig[i]?.let { config ->
                config.value.let {
                    val md2 = Md2ModelLoader(locator, assetManager).loadMd2ModelData(it, skinIndex = 0)
                    if (md2 != null) {
                        config.resource = createModel(md2.mesh, md2.material)
                    } else {
                        println("Failed to load MD2 model data for config ${config.value}")
                    }
                }
            }
        }

        // temporary: load one fixed player model
        val playerModelData = Md2ModelLoader(locator, assetManager).loadMd2ModelData(
            modelName = playerModelPath,
            playerSkin = playerSkinPath,
            skinIndex = 0,
        )!!
        renderState.playerModel = createModel(playerModelData.mesh, playerModelData.material)

        gameConfig.getSounds().forEach { config ->
            if (config != null) {
                locator.findSoundPath(config.value)?.let { soundPath ->
                    config.resource = assetManager.getLoaded<Sound>(soundPath)
                }
            }
        }

        gameConfig.getImages().forEach { config ->
            if (config != null) {
                val texturePath = locator.findImagePath("${config.value}.pcx", "pics")
                if (texturePath != null) {
                    val textureFile = assetManager.getLoaded<ByteArray>(texturePath)
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
                locator.findSoundPath(path)?.let { soundPath ->
                    weaponSounds[index] = assetManager.getLoaded<Sound>(soundPath)
                }
            }
        }

        precached = true
    }

    fun gatherInput(outgoingSequence: Int): MoveMessage {
        return inputManager.gatherInput(outgoingSequence, deltaTime, entityManager.currentFrame)
    }

    /**
     * CL_CalcViewValues
     * Updates camera transformation according to player input and player info
     */
    private fun updatePlayerCamera(lerp: Float) {
        val currentFrame = entityManager.currentFrame
        val previousFrame = entityManager.previousFrame
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
        inputManager.updateAngles()

        // calculate where the camera should look at on this frame
        val direction: Vector3 = if (currentState.pmove.pm_type == PM_NORMAL || currentState.pmove.pm_type == PM_SPECTATOR) {
            // calculate the camera direction based on local angles + kick angle.
            // don't need to interpolate rotation because it is locally based
            val kickAnglePitch = lerpAngle(previousState.kick_angles[PITCH], currentState.kick_angles[PITCH], lerp)
            val kickAngleYaw = lerpAngle(previousState.kick_angles[YAW], currentState.kick_angles[YAW], lerp)
            quakeForward(
                inputManager.localPitch + kickAnglePitch,
                inputManager.localYaw + kickAngleYaw
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
        renderState.gun?.prev?.origin = floatArrayOf(
            oldGunOffsetX + oldX,
            oldGunOffsetY + oldY,
            oldGunOffsetZ + oldZ,
        )
        renderState.gun?.current?.origin = floatArrayOf(
            currentGunOffsetX + newX,
            currentGunOffsetY + newY,
            currentGunOffsetZ + newZ,
        )

        // todo: fix pitch
        renderState.gun?.current?.angles = floatArrayOf(0f, inputManager.localYaw, 0f)
        renderState.gun?.prev?.angles = floatArrayOf(0f, inputManager.localYaw, 0f)
        (renderState.gun?.modelInstance?.userData as? Md2CustomData)?.let { userData ->
            userData.interpolation = lerpFrac
        }
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
        renderState.playerNumber = msg.playerNumber
        spawnCount = msg.spawnCount

        locator.gameName = gameName
    }

    override fun processConfigStringMessage(msg: ConfigStringMessage) {
        gameConfig.disposeUnmanagedResource(gameConfig[msg.index]) // todo: check if it can even happen?
        gameConfig[msg.index] = Config(msg.config)
    }

    override fun processBaselineMessage(msg: SpawnBaselineMessage) {
        entityManager.processBaselineMessage(msg)
    }

    /**
     * CL_ParsePlayerstate
     * todo: move to common?
     */
    override fun processPlayerInfoMessage(msg: PlayerInfoMessage) {
        entityManager.processPlayerInfoMessage(msg)
    }

    /**
     * Also compute the previous frame
     */
    override fun processServerFrameHeader(msg: FrameHeaderMessage) {
        entityManager.processServerFrameHeader(msg)
    }

    override fun processPacketEntitiesMessage(msg: PacketEntitiesMessage): Boolean {
        val validMessage = entityManager.processPacketEntitiesMessage(msg)
        if (validMessage) {
            entityManager.computeVisibleEntities(renderState, gameConfig)
        }
        return validMessage
    }

    override fun processSoundMessage(msg: SoundMessage) {
        val config = gameConfig[Defines.CS_SOUNDS + msg.soundIndex]
        val sound = config?.resource as? Sound
        if (sound != null) {
            // msg.volume should already be in [0,1]: byte / 255f
            val volume = (msg.volume * calculateSoundAttenuation(msg)).coerceIn(0f, 1f)
            if (volume > 0f) {
                sound.play(volume)
            }
        } else {
            Com.Warn("sound ${msg.soundIndex} not found")
        }
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
            Com.Warn("weapon sound $weaponType not found")
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

    private fun calculateSoundAttenuation(msg: SoundMessage): Float {
        if (msg.attenuation <= 0f) {
            return 1f
        }

        val soundOrigin = when {
            msg.origin != null -> Vector3(msg.origin[0], msg.origin[1], msg.origin[2])
            msg.entityIndex > 0 -> entityManager.getEntitySoundOrigin(msg.entityIndex)
            else -> null
        }

        if (soundOrigin == null) {
            return 1f
        }

        val distance = soundOrigin.dst(camera.position)
        val rolloff = if (msg.attenuation == ATTN_STATIC.toFloat()) msg.attenuation * 2f else msg.attenuation
        val referenceDistance = 200f
        if (distance <= referenceDistance) {
            return 1f
        }
        return (referenceDistance / (referenceDistance + rolloff * (distance - referenceDistance))).coerceIn(0f, 1f)
    }


}
