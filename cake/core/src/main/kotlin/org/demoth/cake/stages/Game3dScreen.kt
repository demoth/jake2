package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
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
import org.demoth.cake.assets.Md2Asset
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.assets.Md2Shader
import org.demoth.cake.assets.Md2ShaderProvider
import org.demoth.cake.assets.getLoaded
import kotlin.math.abs

/**
 * Represents the 3d screen where the game is actually happening.
 * This class is responsible for drawing 3d models, hud, process inputs and play sounds.
 * Also, it is responsible for loading/disposing of the required resources
 */
class Game3dScreen(
    private val assetManager: AssetManager,
    private val inputManager: InputManager = InputManager(),
) : KtxScreen, ServerMessageProcessor, InputProcessor by inputManager {
    private var precached: Boolean = false

    private val modelBatch: ModelBatch
    private val collisionModel = CM()
    private val prediction by lazy { ClientPrediction(collisionModel, entityManager, gameConfig) }

    private val camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

    var deltaTime: Float = 0f

    private val gameConfig = GameConfiguration(assetManager)

    private val entityManager = ClientEntityManager()
    private val environment = Environment()

    // game state
    private var gameName: String = "baseq2"
    private var spawnCount = 0

    private val beamRenderer = BeamRenderer(assetManager)

    /**
     * id of the player in the game. can be used to determine if the entity is the current player
     */
    private var levelString: String = ""

    private val spriteBatch = SpriteBatch()
    private val layoutExecutor = LayoutExecutor(spriteBatch)


    // interpolation factor between two server frames
    private var lerpFrac: Float = 0f

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
        assetManager.unload(md2Path)
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

    private fun refreshSkyBox() {
        entityManager.setSkyModel(gameConfig.getSkyModel())
    }

    override fun render(delta: Float) {
        if (!precached)
            return

        val serverFrameTime = 1f/10f // 10Hz server updates
        lerpFrac = (entityManager.lerpAcc / serverFrameTime).coerceIn(0f, 1f)
        prediction.predictMovement(entityManager.currentFrame, inputManager)

        updatePlayerCamera(lerpFrac)

        modelBatch.begin(camera)

        if (entityManager.drawSkybox)
            entityManager.skyEntity?.modelInstance?.let { skyModelInstance ->
                Gdx.gl.glDepthMask(false)
                // TODO: rotate skybox: skyBox.transform.setToRotation(...)
                skyModelInstance.transform.setTranslation(camera.position) // follow the camera
                modelBatch.render(skyModelInstance)
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
        entityManager.lerpAcc += delta
    }

    override fun dispose() {
        beamRenderer.dispose()
        spriteBatch.dispose()
        modelBatch.dispose()
        entityManager.dispose()
    }

    fun unloadConfigAssets() {
        gameConfig.unloadAssets()
    }

    /**
     * Load resources into the memory, that are referenced in the config strings or assumed always required (like weapon sounds)
     * todo: make resource loading asynchronous
     */
    fun precache() {
        if (precached) {
            Com.Warn("precache called for an already-precached Game3dScreen")
            return
        }

        // load resources referenced in the config strings

        // load the level
        val mapName = requireNotNull(gameConfig.getMapName()) {
            "Missing map config string at index ${CS_MODELS + 1}"
        }
        val bspMap = gameConfig.loadMapAsset()
        val brushModels = bspMap.models

        // load inline bmodels
        brushModels.forEachIndexed { index, model ->
            val configString = gameConfig[CS_MODELS + index + 1]
            check(configString != null) { "Missing brush model for ${gameConfig[CS_MODELS + index + 1]?.value}" }
            if (index != 0)
                check(configString.value == "*$index") { "Wrong config string value for inline model" }
            configString.resource = model
        }

        // the level will not come as a entity, it is expected to be all the time, so we can instantiate it right away
        entityManager.levelEntity = ClientEntity("level").apply {
            modelInstance = ModelInstance(brushModels.first())
        }

        collisionModel.CM_LoadMapFile(bspMap.mapData, mapName, IntArray(1) {0})

        // after world + inline brush models, only non-inline model paths are expected.
        val startIndex = CS_MODELS + 1 + brushModels.size
        gameConfig.loadAssets(startIndex)
        refreshSkyBox()

        precached = true
    }

    fun gatherInput(outgoingSequence: Int): MoveMessage {
        return inputManager.gatherInput(outgoingSequence, deltaTime, entityManager.currentFrame)
    }

    fun resetInputLookReference() {
        inputManager.resetMouseLookReference()
    }

    fun updatePredictionNetworkState(incomingAcknowledged: Int, outgoingSequence: Int, currentTimeMs: Int) {
        prediction.updateNetworkState(incomingAcknowledged, outgoingSequence, currentTimeMs)
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

        val predictionEnabled = (currentState.pmove.pm_flags.toInt() and PMF_NO_PREDICTION) == 0
        val interpolatedX: Float
        val interpolatedY: Float
        val interpolatedZ: Float
        if (predictionEnabled) {
            // Old client path: predicted origin + interpolated viewoffset - smoothed prediction error.
            val backLerp = 1f - lerp
            val viewOffsetX = previousState.viewoffset[0] + lerp * (currentState.viewoffset[0] - previousState.viewoffset[0])
            val viewOffsetY = previousState.viewoffset[1] + lerp * (currentState.viewoffset[1] - previousState.viewoffset[1])
            val viewOffsetZ = previousState.viewoffset[2] + lerp * (currentState.viewoffset[2] - previousState.viewoffset[2])

            interpolatedX = prediction.predictedOrigin[0] + viewOffsetX - backLerp * prediction.predictionError[0]
            interpolatedY = prediction.predictedOrigin[1] + viewOffsetY - backLerp * prediction.predictionError[1]
            interpolatedZ = prediction.predictedOrigin[2] + viewOffsetZ - backLerp * prediction.predictionError[2] -
                prediction.smoothedStepOffset(Globals.curtime)
        } else {
            interpolatedX = oldX + (newX - oldX) * lerp
            interpolatedY = oldY + (newY - oldY) * lerp
            interpolatedZ = oldZ + (newZ - oldZ) * lerp
        }

        camera.position.set(interpolatedX, interpolatedY, interpolatedZ)

        val baseViewPitch: Float
        val baseViewYaw: Float
        val baseViewRoll: Float
        if (currentState.pmove.pm_type < PM_DEAD && predictionEnabled) {
            // Old client path: use predicted view angles when locally controlling movement.
            baseViewPitch = prediction.predictedAngles[PITCH]
            baseViewYaw = prediction.predictedAngles[YAW]
            baseViewRoll = prediction.predictedAngles[ROLL]
        } else {
            // No local prediction for PM_DEAD / PM_GIB / PM_FREEZE.
            baseViewPitch = lerpAngle(previousState.viewangles[PITCH], currentState.viewangles[PITCH], lerp)
            baseViewYaw = lerpAngle(previousState.viewangles[YAW], currentState.viewangles[YAW], lerp)
            baseViewRoll = lerpAngle(previousState.viewangles[ROLL], currentState.viewangles[ROLL], lerp)
        }

        val viewPitch = baseViewPitch + lerpAngle(previousState.kick_angles[PITCH], currentState.kick_angles[PITCH], lerp)
        val viewYaw = baseViewYaw + lerpAngle(previousState.kick_angles[YAW], currentState.kick_angles[YAW], lerp)
        val viewRoll = baseViewRoll + lerpAngle(previousState.kick_angles[ROLL], currentState.kick_angles[ROLL], lerp)
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
        if (predictionEnabled) {
            val baseX = camera.position.x
            val baseY = camera.position.y
            val baseZ = camera.position.z
            entityManager.viewGun?.prev?.origin = floatArrayOf(
                oldGunOffsetX + baseX,
                oldGunOffsetY + baseY,
                oldGunOffsetZ + baseZ,
            )
            entityManager.viewGun?.current?.origin = floatArrayOf(
                currentGunOffsetX + baseX,
                currentGunOffsetY + baseY,
                currentGunOffsetZ + baseZ,
            )
        } else {
            entityManager.viewGun?.prev?.origin = floatArrayOf(
                oldGunOffsetX + oldX,
                oldGunOffsetY + oldY,
                oldGunOffsetZ + oldZ,
            )
            entityManager.viewGun?.current?.origin = floatArrayOf(
                currentGunOffsetX + newX,
                currentGunOffsetY + newY,
                currentGunOffsetZ + newZ,
            )
        }

        val oldGunAnglePitch = previousState.gunangles[PITCH]
        val oldGunAngleYaw = previousState.gunangles[YAW]
        val oldGunAngleRoll = previousState.gunangles[ROLL]
        val currentGunAnglePitch = currentState.gunangles[PITCH]
        val currentGunAngleYaw = currentState.gunangles[YAW]
        val currentGunAngleRoll = currentState.gunangles[ROLL]

        // old client behavior: gun angles are view angles + replicated gun angles
        entityManager.viewGun?.prev?.angles = floatArrayOf(
            viewPitch + oldGunAnglePitch,
            viewYaw + oldGunAngleYaw,
            viewRoll + oldGunAngleRoll
        )
        entityManager.viewGun?.current?.angles = floatArrayOf(
            viewPitch + currentGunAnglePitch,
            viewYaw + currentGunAngleYaw,
            viewRoll + currentGunAngleRoll
        )
        (entityManager.viewGun?.modelInstance?.userData as? Md2CustomData)?.let { userData ->
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
        entityManager.playerNumber = msg.playerNumber
        spawnCount = msg.spawnCount
    }

    override fun processConfigStringMessage(msg: ConfigStringMessage) {
        gameConfig.applyConfigString(msg.index, msg.config, loadResource = precached)

        if (msg.index == CS_SKY) {
            refreshSkyBox()
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
            entityManager.computeVisibleEntities(gameConfig)
            prediction.onServerFrameParsed(entityManager.currentFrame)
        }
        return validMessage
    }

    override fun processSoundMessage(msg: SoundMessage) {
        val sound = gameConfig.getSound(msg.soundIndex)
        if (sound != null) {
            // msg.volume should already be in [0,1]: byte / 255f
            val volume = (msg.volume * calculateSoundAttenuation(msg)).coerceIn(0f, 1f)
            if (volume > 0f) {
                sound.play(volume)
            }
        } else {
            Com.Warn("sound ${msg.soundIndex} (${gameConfig.getSoundPath(msg.soundIndex)}) not found")
        }
    }

    override fun processWeaponSoundMessage(msg: WeaponSoundMessage) {
        // weapon type is stored in last 7 bits of the msg.type
        val weaponType = msg.type and 0x7F
        // the silenced flag is stored in the first bit
        val silenced = (msg.type and 0x80) != 0
        val sound = gameConfig.getWeaponSound(weaponType)
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
