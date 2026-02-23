package org.demoth.cake.stages.ingame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.CM
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.util.Lib
import ktx.app.KtxScreen
import ktx.graphics.use
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.*
import org.demoth.cake.assets.*
import org.demoth.cake.audio.SpatialSoundAttenuation
import org.demoth.cake.input.InputManager
import org.demoth.cake.stages.ingame.effects.ClientEffectsSystem
import org.demoth.cake.stages.ingame.hud.GameConfigLayoutDataProvider
import org.demoth.cake.stages.ingame.hud.Hud
import org.demoth.cake.ui.GameUiStyleFactory
import kotlin.math.abs
import kotlin.math.sin

/**
 * Represents the 3d screen where the game is actually happening.
 * This class is responsible for drawing 3d models, hud, process inputs and play sounds.
 * Also, it is responsible for loading/disposing of the required resources
 */
class Game3dScreen(
    private val assetManager: AssetManager,
    private val inputManager: InputManager,
) : KtxScreen, ServerMessageProcessor, InputProcessor by inputManager {
    private var precached: Boolean = false

    private val modelBatch: ModelBatch
    private val bspLightmapShader: BspLightmapShader

    private val collisionModel = CM()
    private val prediction by lazy { ClientPrediction(collisionModel, entityManager, gameConfig) }
    private val dynamicLightSystem = DynamicLightSystem()

    private val camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

    var deltaTime: Float = 0f

    private val gameConfig = GameConfiguration(assetManager)

    private val entityManager = ClientEntityManager()
    private val effectsSystem = ClientEffectsSystem(
        assetManager = assetManager,
        entityManager = entityManager,
        listenerPositionProvider = { camera.position },
        cameraProvider = { camera },
        dynamicLightSystem = dynamicLightSystem,
    )
    private val environment = Environment()

    // game state
    private var gameName: String = ""
    private var spawnCount = 0

    private val beamRenderer = BeamRenderer(assetManager)
    private val sp2Renderer = Sp2Renderer()
    private var worldVisibilityController: BspWorldVisibilityController? = null
    private var worldTextureAnimationController: BspWorldTextureAnimationController? = null
    private var inlineTextureAnimationController: BspInlineTextureAnimationController? = null
    private var worldSurfaceMaterialController: BspWorldSurfaceMaterialController? = null
    private var inlineSurfaceMaterialController: BspInlineSurfaceMaterialController? = null
    private var entityLightSampler: BspEntityLightSampler? = null
    private val lightStyleValues = FloatArray(Defines.MAX_LIGHTSTYLES) { 1f }
    private var lastLightStyleTick: Int = Int.MIN_VALUE

    /**
     * id of the player in the game. can be used to determine if the entity is the current player
     */
    private var levelString: String = ""

    private val spriteBatch = SpriteBatch()

    // Initialized on ServerDataMessage, then reused for this screen lifetime.
    private var hud: Hud? = null


    // interpolation factor between two server frames, between 0 and 1
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

        bspLightmapShader = BspLightmapShader().apply { init() }
        val md2Shader = initializeMd2Shader(assetManager)
        modelBatch = ModelBatch(Md2ShaderProvider(md2Shader, bspLightmapShader))
    }

    // todo: a lot of hacks/quirks - explain & document
    private fun applyIdTech2EntityRotation(entity: ClientEntity, pitch: Float, yaw: Float, roll: Float) {
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
        // Cross-reference: old frame order in `CL.Frame()` calls `CL_pred.PredictMovement()`
        // before calculating render view (`CL_ents.CalcViewValues`).
        prediction.predictMovement(entityManager.currentFrame, inputManager, gameConfig.playerConfiguration.playerIndex)

        updatePlayerView(lerpFrac)
        worldVisibilityController?.update(camera.position, entityManager.currentFrame.areabits)
        worldTextureAnimationController?.update(Globals.curtime)
        refreshLightStyles(Globals.curtime)
        worldSurfaceMaterialController?.update(Globals.curtime, ::lightStyleValue)
        effectsSystem.update(delta, entityManager.currentFrame.serverframe)
        dynamicLightSystem.beginFrame(Globals.curtime, delta)
        collectEntityEffectDynamicLights()
        bspLightmapShader.setDynamicLights(dynamicLightSystem.visibleLightsForShader())

        // render entities
        val lateDepthHackEntities = mutableListOf<ClientEntity>()
        modelBatch.use(camera) { modelBatch ->
            if (entityManager.rDrawSky?.value != 0f)
                entityManager.skyEntity?.modelInstance?.let { skyModelInstance ->
                    Gdx.gl.glDepthMask(false)
                    // TODO: rotate skybox: skyBox.transform.setToRotation(...)
                    skyModelInstance.transform.setTranslation(camera.position) // follow the camera
                    modelBatch.render(skyModelInstance)
                    Gdx.gl.glDepthMask(true)
                }

            entityManager.visibleEntities.forEach {
                if (!isTranslucentModelPassEntity(it)) {
                    if (it.depthHack) {
                        lateDepthHackEntities += it
                    } else {
                        renderModelEntity(modelBatch, it)
                    }
                }
            }
            entityManager.visibleEntities.forEach {
                if (isTranslucentModelPassEntity(it)) {
                    if (it.depthHack) {
                        lateDepthHackEntities += it
                    } else {
                        renderModelEntity(modelBatch, it)
                    }
                }
            }
            entityManager.visibleBeams.forEach {
                beamRenderer.render(modelBatch, it, entityManager.currentFrame.serverframe)
            }
            // Preserve legacy draw ordering: opaque first, translucent second.
            // Do not merge into a naive single pass; mixed ordering breaks alpha/depth results.
            entityManager.visibleSprites.forEach {
                if ((it.resolvedRenderFx and Defines.RF_TRANSLUCENT) == 0) {
                    sp2Renderer.render(modelBatch, it, camera, lerpFrac)
                }
            }
            entityManager.visibleSprites.forEach {
                if ((it.resolvedRenderFx and Defines.RF_TRANSLUCENT) != 0) {
                    sp2Renderer.render(modelBatch, it, camera, lerpFrac)
                }
            }
            effectsSystem.render(modelBatch)
            entityManager.lerpAcc += delta

        }
        if (lateDepthHackEntities.isNotEmpty()) {
            modelBatch.use(camera) { modelBatch ->
                lateDepthHackEntities.forEach { renderModelEntity(modelBatch, it) }
            }
        }

        // draw hud
        spriteBatch.use {
            hud?.update(delta, Gdx.graphics.width, Gdx.graphics.height)

            hud?.drawCrosshair(
                screenWidth = Gdx.graphics.width,
                screenHeight = Gdx.graphics.height,
            )

            hud?.executeLayout(
                layout = gameConfig.getStatusBarLayout(),
                serverFrame = entityManager.currentFrame.serverframe,
                stats = entityManager.currentFrame.playerstate.stats,
                screenWidth = Gdx.graphics.width,
                screenHeight = Gdx.graphics.height,
            )

            // draw additional layout, like help or score
            // SRC.DrawLayout
            if ((entityManager.currentFrame.playerstate.stats[Defines.STAT_LAYOUTS].toInt() and 1) != 0) {
                hud?.executeLayout(
                    layout = gameConfig.layout,
                    serverFrame = entityManager.currentFrame.serverframe,
                    stats = entityManager.currentFrame.playerstate.stats,
                    screenWidth = Gdx.graphics.width,
                    screenHeight = Gdx.graphics.height,
                )
            }
            // draw additional layout, like help or score
            // CL_inv.DrawInventory
            if ((entityManager.currentFrame.playerstate.stats[Defines.STAT_LAYOUTS].toInt() and 2) != 0) {
                hud?.drawInventory(
                    playerstate = entityManager.currentFrame.playerstate,
                    screenWidth = Gdx.graphics.width,
                    screenHeight = Gdx.graphics.height,
                    gameConfig = gameConfig
                )
            }
        }
    }

    // Preserve legacy ordering: opaque model entities first, translucent model entities second.
    // Legacy counterpart: `client/CL_ents.AddPacketEntities` + renderer alpha passes in
    // `client/render/fast/Surf.R_DrawAlphaSurfaces`.
    //
    // Inline brush models with SURF_TRANS surfaces must also be treated as translucent-pass
    // entities even if RF_TRANSLUCENT is not set on entity flags.
    fun isTranslucentModelPassEntity(entity: ClientEntity): Boolean {
        if ((entity.resolvedRenderFx and Defines.RF_TRANSLUCENT) != 0) {
            return true
        }
        val inlineModelIndex = parseInlineModelIndex(entity.name) ?: return false
        return inlineSurfaceMaterialController?.hasTranslucentParts(inlineModelIndex) == true
    }

    /**
     * Renders one model-backed entity with interpolation + alpha/material state.
     *
     * Legacy counterparts:
     * `client/CL_ents.AddPacketEntities` (resolved renderfx/alpha) and
     * `client/V.AddEntity` consumption by renderer.
     */
    private fun renderModelEntity(modelBatch: ModelBatch, entity: ClientEntity) {
        var entityYaw: Float
        // apply client side effects
        if (entity.current.effects and Defines.EF_ROTATE != 0) {
            // rotate the model Instance, should to 180 degrees in 1 second
            entity.modelInstance.transform.rotate(Vector3.Z, deltaTime * 180f)
            entityYaw = entity.current.angles[Defines.YAW]
        } else {
            val pitch = lerpAngle(entity.prev.angles[Defines.PITCH], entity.current.angles[Defines.PITCH], lerpFrac)
            val yaw = lerpAngle(entity.prev.angles[Defines.YAW], entity.current.angles[Defines.YAW], lerpFrac)
            val roll = lerpAngle(entity.prev.angles[Defines.ROLL], entity.current.angles[Defines.ROLL], lerpFrac)

            applyIdTech2EntityRotation(entity, pitch, yaw, roll)
            entityYaw = yaw
        }

        // interpolate position
        val x = entity.prev.origin[0] + (entity.current.origin[0] - entity.prev.origin[0]) * lerpFrac
        val y = entity.prev.origin[1] + (entity.current.origin[1] - entity.prev.origin[1]) * lerpFrac
        val z = entity.prev.origin[2] + (entity.current.origin[2] - entity.prev.origin[2]) * lerpFrac

        entity.modelInstance.transform.setTranslation(x, y, z)

        val translucent = (entity.resolvedRenderFx and Defines.RF_TRANSLUCENT) != 0
        val opacity = if (translucent) entity.alpha else 1f
        val isBrushModelEntity = entity.name == "level" || entity.name.startsWith("*")
        // Preserve per-surface blend/light/flowing material state on brush models.
        // Only apply entity-wide opacity overrides when explicitly translucent.
        if (!isBrushModelEntity || translucent) {
            applyModelOpacity(entity.modelInstance, opacity, forceTranslucent = translucent)
        }
        if (entity.depthHack) {
            entity.modelInstance.materials.forEach { material ->
                val depth = material.get(DepthTestAttribute.Type) as? DepthTestAttribute
                if (depth == null) {
                    // Quake-style weapon depth hack: keep depth test/write for self-occlusion,
                    // but compress depth range so view-model stays in front of world geometry.
                    // Legacy counterparts:
                    // - `client/CL_ents.AddViewWeapon` sets `RF_DEPTHHACK`.
                    // - `client/render/fast/Mesh` applies `glDepthRange(gldepthmin, gldepthmin + 0.3 * ...)`.
                    material.set(DepthTestAttribute(GL20.GL_LEQUAL, 0f, 0.3f, !translucent))
                } else {
                    depth.depthFunc = GL20.GL_LEQUAL
                    depth.depthRangeNear = 0f
                    depth.depthRangeFar = 0.3f
                    depth.depthMask = !translucent
                }
            }
        }

        parseInlineModelIndex(entity.name)?.let { inlineModelIndex ->
            inlineTextureAnimationController?.update(
                modelInstance = entity.modelInstance,
                inlineModelIndex = inlineModelIndex,
                entityFrame = entity.resolvedFrame,
            )
            inlineSurfaceMaterialController?.update(
                modelInstance = entity.modelInstance,
                inlineModelIndex = inlineModelIndex,
                currentTimeMs = Globals.curtime,
                lightStyleResolver = ::lightStyleValue,
            )
        }

        (entity.modelInstance.userData as? Md2CustomData)?.let { userData ->
            userData.interpolation = lerpFrac
            applyMd2EntityLighting(entity, x, y, z, entityYaw, userData)
        }
        modelBatch.render(entity.modelInstance, environment)
    }

    private fun parseInlineModelIndex(entityName: String): Int? {
        if (!entityName.startsWith("*")) {
            return null
        }
        val modelIndex = entityName.drop(1).toIntOrNull() ?: return null
        return modelIndex.takeIf { it > 0 }
    }

    /**
     * Computes per-entity MD2 lighting tint.
     *
     * Legacy counterpart:
     * `client/render/fast/Mesh` alias model light path.
     */
    private fun applyMd2EntityLighting(
        entity: ClientEntity,
        x: Float,
        y: Float,
        z: Float,
        yawDegrees: Float,
        userData: Md2CustomData,
    ) {
        val renderFx = entity.resolvedRenderFx
        val shellFlags = Defines.RF_SHELL_HALF_DAM or Defines.RF_SHELL_DOUBLE or
            Defines.RF_SHELL_RED or Defines.RF_SHELL_GREEN or Defines.RF_SHELL_BLUE

        if ((renderFx and shellFlags) != 0) {
            var red = 0f
            var green = 0f
            var blue = 0f
            if ((renderFx and Defines.RF_SHELL_HALF_DAM) != 0) {
                red = 0.56f
                green = 0.59f
                blue = 0.45f
            }
            if ((renderFx and Defines.RF_SHELL_DOUBLE) != 0) {
                red = 0.9f
                green = 0.7f
                blue = 0f
            }
            if ((renderFx and Defines.RF_SHELL_RED) != 0) red = 1f
            if ((renderFx and Defines.RF_SHELL_GREEN) != 0) green = 1f
            if ((renderFx and Defines.RF_SHELL_BLUE) != 0) blue = 1f
            userData.lightRed = red
            userData.lightGreen = green
            userData.lightBlue = blue
            setMd2ShadeVector(userData, yawDegrees)
            return
        }

        val sampled = if ((renderFx and Defines.RF_FULLBRIGHT) != 0) {
            Vector3(1f, 1f, 1f)
        } else {
            entityLightSampler?.sample(Vector3(x, y, z), ::lightStyleValue, dynamicLightSystem)
                ?: Vector3(1f, 1f, 1f)
        }

        if ((renderFx and Defines.RF_MINLIGHT) != 0 && maxOf(sampled.x, sampled.y, sampled.z) <= 0.1f) {
            sampled.set(0.1f, 0.1f, 0.1f)
        }

        if ((renderFx and Defines.RF_GLOW) != 0) {
            val scale = 0.1f * sin(entityManager.time / 1000f * 7f)
            val minRed = sampled.x * 0.8f
            val minGreen = sampled.y * 0.8f
            val minBlue = sampled.z * 0.8f
            sampled.x = maxOf(minRed, sampled.x + scale)
            sampled.y = maxOf(minGreen, sampled.y + scale)
            sampled.z = maxOf(minBlue, sampled.z + scale)
        }

        userData.lightRed = sampled.x
        userData.lightGreen = sampled.y
        userData.lightBlue = sampled.z
        setMd2ShadeVector(userData, yawDegrees)
    }

    private fun setMd2ShadeVector(userData: Md2CustomData, yawDegrees: Float) {
        val shadeVector = computeMd2ShadeVector(
            yawDegrees = yawDegrees,
            legacyQuantized = RenderTuningCvars.legacyMd2ShadedotsEnabled(),
        )
        userData.shadeVectorX = shadeVector.x
        userData.shadeVectorY = shadeVector.y
        userData.shadeVectorZ = shadeVector.z
    }

    override fun dispose() {
        worldVisibilityController = null
        worldTextureAnimationController = null
        inlineTextureAnimationController = null
        worldSurfaceMaterialController = null
        inlineSurfaceMaterialController = null
        entityLightSampler = null
        hud?.dispose()
        beamRenderer.dispose()
        sp2Renderer.dispose()
        effectsSystem.dispose()
        spriteBatch.dispose()
        modelBatch.dispose()
        entityManager.dispose()
        prediction.reset()
    }

    fun unloadConfigAssets() {
        gameConfig.unloadAssets()
    }

    /**
     * Load resources into the memory, that are referenced in the config strings or assumed always required (like weapon sounds)
     *
     * World model terminology (Quake2 -> libGDX):
     * - Quake2 BSP `model 0` (static world) -> one libGDX [com.badlogic.gdx.graphics.g3d.Model].
     * - Runtime world entity (`levelEntity`) -> [ModelInstance] of that model.
     * - Quake2 world surfaces/faces -> libGDX mesh parts (`com.badlogic.gdx.graphics.g3d.model.NodePart.meshPart.id == surface_<faceIndex>`).
     * - Visibility/animation controllers mutate `com.badlogic.gdx.graphics.g3d.model.NodePart.enabled` and diffuse texture on that world [ModelInstance].
     *
     * todo: make resource loading asynchronous
     */
    fun precache() {
        if (precached) {
            Com.Warn("precache called for an already-precached Game3dScreen")
            return
        }
        prediction.reset()

        // load resources referenced in the config strings

        // load the level
        val mapName = requireNotNull(gameConfig.getMapName()) {
            "Missing map config string at index ${Defines.CS_MODELS + 1}"
        }
        val bspMap = gameConfig.loadMapAsset()
        val brushModels = bspMap.models

        // load inline bmodels
        brushModels.forEachIndexed { index, model ->
            val configString = gameConfig[Defines.CS_MODELS + index + 1]
            check(configString != null) { "Missing brush model for ${gameConfig[Defines.CS_MODELS + index + 1]?.value}" }
            if (index != 0)
                check(configString.value == "*$index") { "Wrong config string value for inline model" }
            configString.resource = model
        }

        // The world model is not replicated as a normal packet entity.
        // Keep one persistent ModelInstance that controllers mutate each frame.
        entityManager.levelEntity = ClientEntity("level").apply {
            modelInstance = ModelInstance(brushModels.first())
        }

        collisionModel.CM_LoadMapFile(bspMap.mapData, mapName, IntArray(1) {0})
        worldVisibilityController = BspWorldVisibilityController(
            worldRenderData = bspMap.worldRenderData,
            modelInstance = entityManager.levelEntity!!.modelInstance,
            collisionModel = collisionModel,
        )
        worldTextureAnimationController = BspWorldTextureAnimationController(
            worldRenderData = bspMap.worldRenderData,
            modelInstance = entityManager.levelEntity!!.modelInstance,
            assetManager = assetManager,
        )
        worldSurfaceMaterialController = BspWorldSurfaceMaterialController(
            worldRenderData = bspMap.worldRenderData,
            modelInstance = entityManager.levelEntity!!.modelInstance,
        )
        inlineTextureAnimationController = BspInlineTextureAnimationController(
            inlineRenderData = bspMap.inlineRenderData,
            textureInfos = bspMap.worldRenderData.textureInfos,
            assetManager = assetManager,
        )
        inlineSurfaceMaterialController = BspInlineSurfaceMaterialController(
            inlineRenderData = bspMap.inlineRenderData,
        )
        entityLightSampler = BspEntityLightSampler(
            worldRenderData = bspMap.worldRenderData,
            collisionModel = collisionModel,
        )

        // after world + inline brush models, only non-inline model paths are expected.
        val startIndex = Defines.CS_MODELS + 1 + brushModels.size
        gameConfig.loadAssets(startIndex)
        effectsSystem.precache()
        refreshSkyBox()

        precached = true
    }

    private fun refreshLightStyles(currentTimeMs: Int) {
        val tick = currentTimeMs / 100
        if (tick == lastLightStyleTick) {
            return
        }
        repeat(Defines.MAX_LIGHTSTYLES) { styleIndex ->
            val pattern = gameConfig.getConfigValue(Defines.CS_LIGHTS + styleIndex).orEmpty()
            lightStyleValues[styleIndex] = evaluateLightStylePattern(pattern, tick)
        }
        lastLightStyleTick = tick
    }

    private fun lightStyleValue(styleIndex: Int): Float {
        if (styleIndex !in lightStyleValues.indices) {
            return 1f
        }
        return lightStyleValues[styleIndex]
    }

    private fun evaluateLightStylePattern(pattern: String, tick: Int): Float {
        if (pattern.isEmpty()) {
            return 1f
        }
        val char = pattern[Math.floorMod(tick, pattern.length)]
        return ((char.code - 'a'.code).toFloat() / ('m'.code - 'a'.code)).coerceAtLeast(0f)
    }

    fun gatherInput(outgoingSequence: Int): MoveMessage {
        return inputManager.gatherInput(outgoingSequence, deltaTime, entityManager.currentFrame)
    }

    /**
     * Clears held gameplay input state when focus/context changes.
     *
     * This prevents stuck movement/fire after switching between game/menu/console processors.
     */
    fun clearInputState() {
        inputManager.clearInputState()
    }

    fun updatePredictionNetworkState(incomingAcknowledged: Int, outgoingSequence: Int, currentTimeMs: Int) {
        // Quirk: prediction must observe fresh netchan ack/sequence from packet headers before
        // replaying movement or computing error correction.
        prediction.updateNetworkState(incomingAcknowledged, outgoingSequence, currentTimeMs)
    }

    /**
     * CL_CalcViewValues
     * Updates camera transformation according to player input and player info
     * Updates player gun position/rotation
     *
     * Cross-reference (old client): `CL_ents.CalcViewValues`.
     */
    private fun updatePlayerView(lerp: Float) {
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
            // Cross-reference: same teleport discontinuity guard in `CL_ents.CalcViewValues`.
            // fixme: what if teleportation was for a short distance?
            oldX = newX
            oldY = newY
            oldZ = newZ
        }

        val predictionEnabled = (currentState.pmove.pm_flags.toInt() and Defines.PMF_NO_PREDICTION) == 0
        val interpolatedX: Float
        val interpolatedY: Float
        val interpolatedZ: Float
        if (predictionEnabled) {
            // Old client path: predicted origin + interpolated viewoffset - smoothed prediction error.
            // Cross-reference: `CL_ents.CalcViewValues` predicted vieworg branch.
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
        if (currentState.pmove.pm_type < Defines.PM_DEAD && predictionEnabled) {
            // Old client path: use predicted view angles when locally controlling movement.
            // Cross-reference: `CL_ents.CalcViewValues` predicted viewangles branch.
            baseViewPitch = prediction.predictedAngles[Defines.PITCH]
            baseViewYaw = prediction.predictedAngles[Defines.YAW]
            baseViewRoll = prediction.predictedAngles[Defines.ROLL]
        } else {
            // No local prediction for PM_DEAD / PM_GIB / PM_FREEZE.
            baseViewPitch =
                lerpAngle(previousState.viewangles[Defines.PITCH], currentState.viewangles[Defines.PITCH], lerp)
            baseViewYaw = lerpAngle(previousState.viewangles[Defines.YAW], currentState.viewangles[Defines.YAW], lerp)
            baseViewRoll =
                lerpAngle(previousState.viewangles[Defines.ROLL], currentState.viewangles[Defines.ROLL], lerp)
        }

        val viewPitch = baseViewPitch + lerpAngle(
            previousState.kick_angles[Defines.PITCH],
            currentState.kick_angles[Defines.PITCH],
            lerp
        )
        val viewYaw = baseViewYaw + lerpAngle(
            previousState.kick_angles[Defines.YAW],
            currentState.kick_angles[Defines.YAW],
            lerp
        )
        val viewRoll = baseViewRoll + lerpAngle(
            previousState.kick_angles[Defines.ROLL],
            currentState.kick_angles[Defines.ROLL],
            lerp
        )
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
            // Quirk: view weapon should track predicted camera origin, not interpolated pmove origin,
            // otherwise the gun lags/jitters while world view uses prediction.
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

        val oldGunAnglePitch = previousState.gunangles[Defines.PITCH]
        val oldGunAngleYaw = previousState.gunangles[Defines.YAW]
        val oldGunAngleRoll = previousState.gunangles[Defines.ROLL]
        val currentGunAnglePitch = currentState.gunangles[Defines.PITCH]
        val currentGunAngleYaw = currentState.gunangles[Defines.YAW]
        val currentGunAngleRoll = currentState.gunangles[Defines.ROLL]

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
        // player slot used by prediction/entity visibility/HUD highlighting.
        gameConfig.playerConfiguration.playerIndex = msg.playerNumber
        spawnCount = msg.spawnCount

        // ServerDataMessage is the authoritative game/mod style switch point.
        // Cake recreates Game3dScreen for each fresh serverdata sequence, so one HUD per screen is expected.
        hud?.dispose() // defensive: avoid leaking style resources if serverdata is unexpectedly repeated.
        val gameUiStyle = GameUiStyleFactory.create(gameName, assetManager, Scene2DSkin.defaultSkin)
        hud = Hud(spriteBatch, gameUiStyle, GameConfigLayoutDataProvider(gameConfig))
    }

    override fun processConfigStringMessage(msg: ConfigStringMessage) {
        gameConfig.applyConfigString(msg.index, msg.config, loadResource = precached)

        if (msg.index == Defines.CS_SKY) {
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
            // Old-client counterpart: `CL_fx.EntityEvent` runs after packet entities are reconstructed.
            playEntityEventSounds()
            // Cross-reference: old `CL_ents.parsePacketEntities` calls `CL_pred.CheckPredictionError`
            // once a valid frame has been fully reconstructed.
            prediction.onServerFrameParsed(entityManager.currentFrame)
        }
        return validMessage
    }

    override fun processSoundMessage(msg: SoundMessage) {
        val sound = gameConfig.getSound(msg.soundIndex, msg.entityIndex)
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

    /**
     * Play client-side entity event sounds derived from reconstructed packet entities.
     *
     * Supported events currently mirror the subset implemented in this module:
     * - `EV_FOOTSTEP` -> random `player/step1..4.wav`
     * - `EV_FALLSHORT` -> `player/land1.wav`
     * - `EV_FALL` -> variation-specific `fall2.wav`
     * - `EV_FALLFAR` -> variation-specific `fall1.wav`
     *
     * Non-goals (for now): item-respawn and teleport event sounds.
     */
    private fun playEntityEventSounds() {
        entityManager.forEachCurrentEntityState { state ->
            when (state.event) {
                Defines.EV_FOOTSTEP -> {
                    val stepIndex = (Lib.rand().toInt() and 3) + 1
                    val sound = gameConfig.getNamedSound("player/step$stepIndex.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(sound, state.index)
                }
                Defines.EV_FALLSHORT -> {
                    val sound = gameConfig.getNamedSound("player/land1.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(sound, state.index)
                }
                Defines.EV_FALL -> {
                    val sound = gameConfig.playerConfiguration.getPlayerSound(state.index, "fall2.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(sound, state.index)
                }
                Defines.EV_FALLFAR -> {
                    val sound = gameConfig.playerConfiguration.getPlayerSound(state.index, "fall1.wav") ?: return@forEachCurrentEntityState
                    playEntityEventSound(sound, state.index)
                }
            }
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

    override fun processMuzzleFlash2Message(msg: MuzzleFlash2Message) {
        effectsSystem.processMuzzleFlash2Message(msg)
    }

    override fun processTempEntityMessage(msg: TEMessage) {
        effectsSystem.processTempEntityMessage(msg)
    }

    override fun processPrintMessage(msg: PrintMessage) {
        // Legacy behavior echoes server print messages to the console too.
        Com.Printf(msg.text)
        hud?.showPrintMessage(msg.level, msg.text)
    }

    override fun processPrintCenterMessage(msg: PrintCenterMessage) {
        // Legacy behavior echoes center-print text to the console too.
        Com.Printf("${msg.text}\n")
        hud?.showCenterPrint(msg.text)
    }

    override fun processLayoutMessage(msg: LayoutMessage) {
         gameConfig.layout = msg.layout
    }

    override fun processInventoryMessage(msg: InventoryMessage) {
        for (i in 0..<Defines.MAX_ITEMS) {
            gameConfig.playerConfiguration.inventory[i] = msg.inventory[i]
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
        return SpatialSoundAttenuation.calculate(soundOrigin, camera.position, msg.attenuation)
    }

    /**
     * Adds per-frame dynamic lights driven by replicated entity effect flags (`EF_*`).
     *
     * Legacy counterpart:
     * `client/CL_ents.AddPacketEntities` (`V.AddLight` branches).
     */
    private fun collectEntityEffectDynamicLights() {
        entityManager.forEachCurrentEntityState { state ->
            val origin = entityManager.getEntitySoundOrigin(state.index) ?: return@forEachCurrentEntityState
            val effects = state.effects
            when {
                (effects and Defines.EF_ROCKET) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 200f, 1f, 1f, 0f)
                }
                (effects and Defines.EF_BLASTER) != 0 -> {
                    if ((effects and Defines.EF_TRACKER) != 0) {
                        dynamicLightSystem.addFrameLight(origin, 200f, 0f, 1f, 0f)
                    } else {
                        dynamicLightSystem.addFrameLight(origin, 200f, 1f, 1f, 0f)
                    }
                }
                (effects and Defines.EF_HYPERBLASTER) != 0 -> {
                    if ((effects and Defines.EF_TRACKER) != 0) {
                        dynamicLightSystem.addFrameLight(origin, 200f, 0f, 1f, 0f)
                    } else {
                        dynamicLightSystem.addFrameLight(origin, 200f, 1f, 1f, 0f)
                    }
                }
                (effects and Defines.EF_BFG) != 0 -> {
                    val radius = if ((effects and Defines.EF_ANIM_ALLFAST) != 0) {
                        200f
                    } else {
                        BFG_LIGHT_RAMP[state.frame % BFG_LIGHT_RAMP.size].toFloat()
                    }
                    dynamicLightSystem.addFrameLight(origin, radius, 0f, 1f, 0f)
                }
                (effects and Defines.EF_TRAP) != 0 -> {
                    val radius = 100f + Globals.rnd.nextInt(100)
                    dynamicLightSystem.addFrameLight(origin, radius, 1f, 0.8f, 0.1f)
                }
                (effects and Defines.EF_FLAG1) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 225f, 1f, 0.1f, 0.1f)
                }
                (effects and Defines.EF_FLAG2) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 225f, 0.1f, 0.1f, 1f)
                }
                (effects and Defines.EF_TAGTRAIL) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 225f, 1f, 1f, 0f)
                }
                (effects and Defines.EF_TRACKERTRAIL) != 0 -> {
                    if ((effects and Defines.EF_TRACKER) != 0) {
                        val radius = 50f + 500f * (sin(Globals.curtime / 500f) + 1f)
                        dynamicLightSystem.addFrameLight(origin, radius, -1f, -1f, -1f)
                    } else {
                        dynamicLightSystem.addFrameLight(origin, 155f, -1f, -1f, -1f)
                    }
                }
                (effects and Defines.EF_TRACKER) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 200f, -1f, -1f, -1f)
                }
                (effects and Defines.EF_IONRIPPER) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 100f, 1f, 0.5f, 0.5f)
                }
                (effects and Defines.EF_BLUEHYPERBLASTER) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 200f, 0f, 0f, 1f)
                }
                (effects and Defines.EF_PLASMA) != 0 -> {
                    dynamicLightSystem.addFrameLight(origin, 130f, 1f, 0.5f, 0.5f)
                }
            }
        }
    }

    /**
     * Play an event sound using legacy-normal attenuation from the emitting entity origin.
     */
    private fun playEntityEventSound(sound: com.badlogic.gdx.audio.Sound, entityIndex: Int) {
        val attenuation = Defines.ATTN_NORM.toFloat()
        val soundOrigin = entityManager.getEntitySoundOrigin(entityIndex)
        val attenuationScale = if (soundOrigin == null) {
            1f
        } else {
            SpatialSoundAttenuation.calculate(soundOrigin, camera.position, attenuation)
        }
        val volume = attenuationScale.coerceIn(0f, 1f)
        if (volume > 0f) {
            sound.play(volume)
        }
    }

    companion object {
        private val BFG_LIGHT_RAMP = intArrayOf(300, 400, 600, 300, 150, 75)
    }
}
