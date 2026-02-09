package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.*
import jake2.qcommon.Defines.*
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.*
import ktx.app.KtxScreen
import ktx.graphics.use
import org.demoth.cake.*
import org.demoth.cake.assets.BeamRenderer
import org.demoth.cake.assets.BspMapAsset
import org.demoth.cake.assets.Md2Asset
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.assets.Md2Loader
import org.demoth.cake.assets.Md2Shader
import org.demoth.cake.assets.Md2ShaderProvider
import org.demoth.cake.assets.SkyLoader
import org.demoth.cake.assets.getLoaded
import java.util.*
import kotlin.math.abs

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

    private val camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

    var deltaTime: Float = 0f

    private val gameConfig = GameConfiguration()

    private val entityManager = ClientEntityManager()
    private val environment = Environment()

    // game state
    private var gameName: String = "baseq2"
    private var spawnCount = 0

    private var skyBox: ModelInstance? = null
    private val loadedMd2AssetPaths: MutableSet<String> = mutableSetOf()
    private val beamRenderer = BeamRenderer(assetManager)

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
        // create camera
        camera.position.set(0f, 0f, 0f);
        camera.near = 1f
        camera.far = 4096f
        camera.up.set(0f, 0f, 1f) // make z up
        camera.direction.set(0f, 1f, 0f) // make y forward
        camera.update()

        // todo: infer from the level?
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.2f, 0.8f))

        modelBatch = ModelBatch(Md2ShaderProvider(initializeMd2Shader()))
    }

    // fixme: make a free internal md2 model specifically for the shader initialization, don't use q2 resources
    private fun initializeMd2Shader(): Md2Shader {
        val md2Path = "models/monsters/berserk/tris.md2"
        val md2Asset = assetManager.getLoaded<Md2Asset>(md2Path)
        loadedMd2AssetPaths += md2Path
        val md2Instance = createModelInstance(md2Asset.model)

        val tempRenderable = Renderable()
        val md2Shader = Md2Shader(
            md2Instance.getRenderable(tempRenderable), // may not be obvious, but it's required for the shader initialization, the renderable is not used after that
            DefaultShader.Config(
                assetManager.get(md2VatShader),
                assetManager.get(md2FragmentShader),
            )
        )
        md2Shader.init()
        return md2Shader
    }


    // todo: a lot of hacks/quirks - explain & document
    private fun applyQuakeEntityRotation(entity: ClientEntity, pitch: Float, yaw: Float, roll: Float) {
        val isMd2Model = entity.modelInstance.userData is Md2CustomData
        val isBrushModel = entity.name.startsWith("*")
        // old renderer quirks:
        // - alias models (md2): effective pitch sign is positive
        // - inline brush models: effective pitch and roll signs are positive
        val pitchForModel = if (isMd2Model || isBrushModel) pitch else -pitch  // sigh, see Mesh.java GL_DrawAliasFrameLerp
        val rollForModel = if (isBrushModel) roll else -roll

        // Match legacy entity rotation order:
        // yaw around Z, pitch around Y, roll around X.
        entity.modelInstance.transform.idt()
        entity.modelInstance.transform.rotate(Vector3.Z, yaw)
        entity.modelInstance.transform.rotate(Vector3.Y, pitchForModel)
        entity.modelInstance.transform.rotate(Vector3.X, rollForModel)
    }

    private fun loadModelConfigResource(config: Config): Boolean {
        val modelPath = config.value
        if (modelPath.isBlank() || modelPath.startsWith("*") || modelPath.startsWith("#")) {
            return false
        }
        if (!modelPath.endsWith(".md2", ignoreCase = true)) {
            // sprite models (.sp2) and others are handled by dedicated render paths.
            return false
        }
        if (assetManager.fileHandleResolver.resolve(modelPath) == null) {
            return false
        } // todo: warning if not found!
        val md2Asset = assetManager.getLoaded<Md2Asset>(modelPath)
        loadedMd2AssetPaths += modelPath
        config.resource = md2Asset.model
        config.managedByAssetManager = true
        return true
    }

    private fun loadSoundConfigResource(config: Config): Boolean {
        val soundPath = config.value
        if (soundPath.isBlank() || soundPath.startsWith("*")) {
            return false
        }
        val assetPath = "sound/$soundPath"
        if (assetManager.fileHandleResolver.resolve(assetPath) == null) {
            return false
        } // todo: warning if not found!
        config.resource = assetManager.getLoaded<Sound>(assetPath)
        config.managedByAssetManager = true
        return true
    }

    private fun loadImageConfigResource(config: Config): Boolean {
        val imageName = config.value
        if (imageName.isBlank()) {
            return false
        }
        val assetPath = "pics/$imageName.pcx"
        if (assetManager.fileHandleResolver.resolve(assetPath) == null) {
            return false
        } // todo: warning if not found!
        config.resource = assetManager.getLoaded<Texture>(assetPath)
        config.managedByAssetManager = true
        return true
    }

    private fun loadSkyConfigResource(config: Config): Boolean {
        val skyName = config.value
        if (skyName.isBlank()) {
            skyBox = null
            return false
        }
        val skyAssetPath = SkyLoader.assetPath(skyName)
        if (assetManager.fileHandleResolver.resolve(skyAssetPath) == null) {
            return false
        }
        skyBox = ModelInstance(assetManager.getLoaded<Model>(skyAssetPath))
        return true
    }

    private fun loadConfigResource(index: Int, config: Config): Boolean {
        return when (index) {
            in (CS_MODELS + 1)..<(CS_MODELS + MAX_MODELS) -> loadModelConfigResource(config)
            in (CS_SOUNDS + 1)..<(CS_SOUNDS + MAX_SOUNDS) -> loadSoundConfigResource(config)
            in (CS_IMAGES + 1)..<(CS_IMAGES + MAX_IMAGES) -> loadImageConfigResource(config)
            CS_SKY -> loadSkyConfigResource(config)
            else -> false
        }
    }

    override fun render(delta: Float) {
        if (!precached)
            return

        val serverFrameTime = 1f/10f // 10Hz server updates
        lerpFrac = (renderState.lerpAcc / serverFrameTime).coerceIn(0f, 1f)

        updatePlayerCamera(lerpFrac)

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
                val roll = lerpAngle(it.prev.angles[ROLL], it.current.angles[ROLL], lerpFrac)

                applyQuakeEntityRotation(it, pitch, yaw, roll)

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
        entityManager.visibleBeams.forEach {
            beamRenderer.render(modelBatch, it, entityManager.currentFrame.serverframe)
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
        beamRenderer.dispose()
        spriteBatch.dispose()
        modelBatch.dispose()
        gameConfig.disposeUnmanagedResources()
        renderState.dispose()
        // todo: implement a clear and reusable approach for such resources that need to be unloaded
        loadedMd2AssetPaths.forEach { md2Path ->
            if (assetManager.isLoaded(md2Path, Md2Asset::class.java)) {
                assetManager.unload(md2Path)
            }
        }
        loadedMd2AssetPaths.clear()
        gameConfig.getSkyname()?.let { skyName ->
            val skyAssetPath = SkyLoader.assetPath(skyName)
            if (assetManager.isLoaded(skyAssetPath, Model::class.java)) {
                assetManager.unload(skyAssetPath)
            }
        }
        gameConfig.getMapName()?.let { mapAssetPath ->
            if (assetManager.isLoaded(mapAssetPath, BspMapAsset::class.java)) {
                assetManager.unload(mapAssetPath)
            }
        }
    }

    /**
     * Load resources into the memory, that are referenced in the config strings or assumed always required (like weapon sounds)
     * todo: make resource loading asynchronous
     */
    fun precache() {
        // load resources referenced in the config strings

        // load the level
        val mapName = gameConfig.getMapName()!! // fixme: disconnect with an error if is null
        val bspMap = assetManager.getLoaded<BspMapAsset>(mapName)
        val brushModels = bspMap.models

        // load inline bmodels
        brushModels.forEachIndexed { index, model ->
            val configString = gameConfig[CS_MODELS + index + 1]
            check(configString != null) { "Missing brush model for ${gameConfig[CS_MODELS + index + 1]?.value}" }
            if (index != 0)
                check(configString.value == "*$index") { "Wrong config string value for inline model" }
            configString.resource = model
            configString.managedByAssetManager = true
        }

        // the level will not come as a entity, it is expected to be all the time, so we can instantiate it right away
        renderState.levelModel = ClientEntity("level").apply {
            modelInstance = ModelInstance(brushModels.first())
        }

        collisionModel.CM_LoadMapFile(bspMap.mapData, mapName, IntArray(1) {0})

        // load model resources referenced in config strings
        // after world + inline brush models, only non-inline model paths are expected.
        val startIndex = CS_MODELS + 1 + brushModels.size
        val endIndex = CS_MODELS + MAX_MODELS - 1
        for (i in startIndex..endIndex) {
            val config = gameConfig[i] ?: continue
            if (!loadConfigResource(i, config)) {
                val modelPath = config.value
                if (modelPath.isNotBlank() && !modelPath.startsWith("*") && !modelPath.startsWith("#")) {
                    Com.Warn("Failed to load model data for config $modelPath")
                }
            }
        }

        // temporary: load one fixed player model
        val playerMd2Asset = assetManager.getLoaded<Md2Asset>(
            playerModelPath,
            Md2Loader.Parameters(playerSkinPath),
        )
        loadedMd2AssetPaths += playerModelPath
        renderState.playerModel = playerMd2Asset.model

        for (i in (CS_SOUNDS + 1)..<(CS_SOUNDS + MAX_SOUNDS)) {
            val config = gameConfig[i] ?: continue
            loadConfigResource(i, config)
        }

        for (i in (CS_IMAGES + 1)..<(CS_IMAGES + MAX_IMAGES)) {
            val config = gameConfig[i] ?: continue
            loadConfigResource(i, config)
        }

        gameConfig[CS_SKY]?.let { loadConfigResource(CS_SKY, it) }

        // these are expected to be loaded
        weaponSoundPaths.forEach { (index, path) ->
            if (path != null) {
                val soundLocation = "sound/${path}"
                if (assetManager.fileHandleResolver.resolve(soundLocation) != null) {
                    weaponSounds[index] = assetManager.getLoaded<Sound>(soundLocation)
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

        camera.position.set(interpolatedX, interpolatedY, interpolatedZ)

        // process mouse movement
        inputManager.updateAngles()

        val oldViewPitch: Float
        val oldViewYaw: Float
        val oldViewRoll: Float
        val currentViewPitch: Float
        val currentViewYaw: Float
        val currentViewRoll: Float
        if (currentState.pmove.pm_type == PM_NORMAL || currentState.pmove.pm_type == PM_SPECTATOR) {
            // same as old client: locally controlled view + replicated kick angles
            oldViewPitch = inputManager.localPitch + previousState.kick_angles[PITCH]
            oldViewYaw = inputManager.localYaw + previousState.kick_angles[YAW]
            oldViewRoll = previousState.kick_angles[ROLL]
            currentViewPitch = inputManager.localPitch + currentState.kick_angles[PITCH]
            currentViewYaw = inputManager.localYaw + currentState.kick_angles[YAW]
            currentViewRoll = currentState.kick_angles[ROLL]
        } else {
            // no local camera controls for PM_DEAD / PM_GIB / PM_FREEZE
            oldViewPitch = previousState.viewangles[PITCH] + previousState.kick_angles[PITCH]
            oldViewYaw = previousState.viewangles[YAW] + previousState.kick_angles[YAW]
            oldViewRoll = previousState.viewangles[ROLL] + previousState.kick_angles[ROLL]
            currentViewPitch = currentState.viewangles[PITCH] + currentState.kick_angles[PITCH]
            currentViewYaw = currentState.viewangles[YAW] + currentState.kick_angles[YAW]
            currentViewRoll = currentState.viewangles[ROLL] + currentState.kick_angles[ROLL]
        }

        val viewPitch = lerpAngle(oldViewPitch, currentViewPitch, lerp)
        val viewYaw = lerpAngle(oldViewYaw, currentViewYaw, lerp)
        val viewRoll = lerpAngle(oldViewRoll, currentViewRoll, lerp)
        val (forward, up) = toForwardUp(viewPitch, viewYaw, viewRoll)

        camera.direction.set(forward)
        camera.up.set(up)
        camera.update()

        // update the gun position
        val currentGunOffsetX = currentState.gunoffset[0]
        val currentGunOffsetY = currentState.gunoffset[1]
        val currentGunOffsetZ = currentState.gunoffset[2]
        val oldGunOffsetX = previousState.gunoffset[0]
        val oldGunOffsetY = previousState.gunoffset[1]
        val oldGunOffsetZ = previousState.gunoffset[2]

        // set the previous and current state: interpolation will happen with all other client entities
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

        val oldGunAnglePitch = previousState.gunangles[PITCH]
        val oldGunAngleYaw = previousState.gunangles[YAW]
        val oldGunAngleRoll = previousState.gunangles[ROLL]
        val currentGunAnglePitch = currentState.gunangles[PITCH]
        val currentGunAngleYaw = currentState.gunangles[YAW]
        val currentGunAngleRoll = currentState.gunangles[ROLL]

        // old client behavior: gun angles are view angles + replicated gun angles
        renderState.gun?.prev?.angles = floatArrayOf(
            oldViewPitch + oldGunAnglePitch,
            oldViewYaw + oldGunAngleYaw,
            oldViewRoll + oldGunAngleRoll
        )
        renderState.gun?.current?.angles = floatArrayOf(
            currentViewPitch + currentGunAnglePitch,
            currentViewYaw + currentGunAngleYaw,
            currentViewRoll + currentGunAngleRoll
        )
        (renderState.gun?.modelInstance?.userData as? Md2CustomData)?.let { userData ->
            userData.interpolation = lerpFrac
        }
    }

    // region SERVER MESSAGE PARSING

    /**
     * CL_ParseServerData
     */
    override fun processServerDataMessage(msg: ServerDataMessage) {
        gameName = msg.gameName.ifBlank { "baseq2" }
        levelString = msg.levelString
        renderState.playerNumber = msg.playerNumber
        spawnCount = msg.spawnCount
    }

    override fun processConfigStringMessage(msg: ConfigStringMessage) {
        gameConfig.disposeUnmanagedResource(gameConfig[msg.index]) // todo: check if it can even happen?
        val config = Config(msg.config)
        gameConfig[msg.index] = config

        // Resource-bearing configstrings can arrive/refresh during active gameplay.
        when (msg.index) {
            in (CS_MODELS + 1)..<(CS_MODELS + MAX_MODELS),
            in (CS_SOUNDS + 1)..<(CS_SOUNDS + MAX_SOUNDS),
            in (CS_IMAGES + 1)..<(CS_IMAGES + MAX_IMAGES),
            CS_SKY -> loadConfigResource(msg.index, config)
        }
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
        val config = gameConfig[CS_SOUNDS + msg.soundIndex]
        val sound = config?.resource as? Sound
        if (sound != null) {
            // msg.volume should already be in [0,1]: byte / 255f
            val volume = (msg.volume * calculateSoundAttenuation(msg)).coerceIn(0f, 1f)
            if (volume > 0f) {
                sound.play(volume)
            }
        } else {
            Com.Warn("sound ${msg.soundIndex} (${config?.value}) not found")
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

    // endregion

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
