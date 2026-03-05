package org.demoth.cake.stages.ingame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.CM
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.util.Math3D
import ktx.app.KtxScreen
import ktx.graphics.use
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.*
import org.demoth.cake.assets.*
import org.demoth.cake.audio.CakeAudioSystem
import org.demoth.cake.audio.FireAndForgetCakeAudioSystem
import org.demoth.cake.audio.ListenerState
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
    data class SaveMetadataDefaults(
        val mapPath: String?,
        val title: String?,
    )

    /**
     * Top-level presentation routing mode selected by server data.
     */
    private enum class PresentationMode {
        WORLD,
        CINEMATIC,
    }

    /**
     * Per-mode render runtime contract used by [render].
     */
    private interface PresentationRuntime {
        fun render(delta: Float)
    }

    private var precached: Boolean = false
    private var presentationMode = PresentationMode.WORLD
    private val worldPresentationRuntime: PresentationRuntime = WorldPresentationRuntime()
    private val cinematicPresentationRuntime: PresentationRuntime = CinematicPresentationRuntime()
    private var presentationRuntime: PresentationRuntime = worldPresentationRuntime

    private val modelBatch: ModelBatch
    private val bspLightmapShader: BspLightmapShader

    private val collisionModel = CM()
    private val prediction by lazy { ClientPrediction(collisionModel, entityManager, gameConfig) }
    private val dynamicLightSystem = DynamicLightSystem()
    private val audioSystem: CakeAudioSystem = FireAndForgetCakeAudioSystem(
        currentTimeMsProvider = { Globals.curtime },
        entityOriginProvider = { entityIndex -> entityManager.getEntityOrigin(entityIndex) },
        localPlayerEntityIndexProvider = {
            val playerIndex = gameConfig.playerConfiguration.playerIndex
            if (playerIndex >= 0) playerIndex + 1 else null
        },
    )

    private val camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

    var deltaTime: Float = 0f

    private val gameConfig = GameConfiguration(assetManager)
    private val cinematicController = CinematicPresentationController(assetManager, gameConfig)
    private val worldPresentationRenderer = WorldPresentationRenderer()
    private val hudOverlayRenderer by lazy { HudOverlayRenderer(spriteBatch, gameConfig) }

    private val entityManager = ClientEntityManager()
    private val effectsSystem = ClientEffectsSystem(
        assetManager = assetManager,
        entityManager = entityManager,
        audioSystem = audioSystem,
        cameraProvider = { camera },
        dynamicLightSystem = dynamicLightSystem,
    )
    private val soundMessageHandler = IngameSoundMessageHandler(
        gameConfig = gameConfig,
        entityManager = entityManager,
        effectsSystem = effectsSystem,
        audioSystem = audioSystem,
        dynamicLightSystem = dynamicLightSystem,
    )
    private val replicatedEntityEffectCollector = ReplicatedEntityEffectCollector(
        entityManager = entityManager,
        effectsSystem = effectsSystem,
        dynamicLightSystem = dynamicLightSystem,
    )
    private val environment = Environment()

    // game state
    private var gameName: String = ""
    private var spawnCount = 0

    private val beamRenderer = BeamRenderer(assetManager)
    private val sp2Renderer = Sp2Renderer()
    private var worldVisibilityMaskTracker: BspWorldVisibilityMaskTracker? = null
    private var inlineTextureAnimationController: BspInlineTextureAnimationController? = null
    private var inlineSurfaceMaterialController: BspInlineSurfaceMaterialController? = null
    private var worldBatchRenderer: BspWorldBatchRenderer? = null
    private var entityLightSampler: BspEntityLightSampler? = null
    private val lightStyleValues = FloatArray(Defines.MAX_LIGHTSTYLES) { 1f }
    private var lastLightStyleTick: Int = Int.MIN_VALUE
    private var lastBatchDebugLogTimeMs: Int = Int.MIN_VALUE

    /**
     * id of the player in the game. can be used to determine if the entity is the current player
     */
    private var levelString: String = ""

    private val spriteBatch = SpriteBatch()

    // Initialized on ServerDataMessage, then reused for this screen lifetime.
    private var hud: Hud? = null


    // interpolation factor between two server frames, between 0 and 1
    private var lerpFrac: Float = 0f
    private var skyRotationDegreesPerSecond: Float = 0f
    private var skyRotationAngleDegrees: Float = 0f
    private val skyRotationAxis = Vector3()
    private val postProcessBatch = SpriteBatch()
    private val postProcessShader: ShaderProgram
    private var sceneFrameBuffer: FrameBuffer? = null
    private var sceneFrameRegion: TextureRegion? = null

    /**
     * Returns best-effort save metadata fields derived from the active configstrings.
     */
    fun saveMetadataDefaults(): SaveMetadataDefaults {
        val mapPath = gameConfig.getMapName()?.takeUnless { it.isBlank() }
        val title = gameConfig.getConfigValue(Defines.CS_NAME)?.takeUnless { it.isBlank() }
            ?: levelString.takeUnless { it.isBlank() }
        return SaveMetadataDefaults(mapPath = mapPath, title = title)
    }

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

        bspLightmapShader = BspLightmapShader(assetManager).apply { init() }
        val md2Shader = initializeMd2Shader(assetManager)
        modelBatch = ModelBatch(Md2ShaderProvider(md2Shader, bspLightmapShader))
        postProcessShader = createPostProcessShader()
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
        refreshSkyRotation()
    }

    private fun refreshSkyRotation() {
        skyRotationDegreesPerSecond = gameConfig.getConfigValue(Defines.CS_SKYROTATE)?.toFloatOrNull() ?: 0f
        skyRotationAxis.set(parseSkyAxis(gameConfig.getConfigValue(Defines.CS_SKYAXIS)))
    }

    private fun parseSkyAxis(axisValue: String?): Vector3 {
        if (axisValue.isNullOrBlank()) {
            return Vector3.Zero
        }

        val tokens = axisValue.trim().split(Regex("\\s+"))
        val x = tokens.getOrNull(0)?.toFloatOrNull() ?: 0f
        val y = tokens.getOrNull(1)?.toFloatOrNull() ?: 0f
        val z = tokens.getOrNull(2)?.toFloatOrNull() ?: 0f
        return Vector3(x, y, z)
    }

    private fun applySkyTransform(skyModelInstance: ModelInstance) {
        skyModelInstance.transform.idt()
        if (skyRotationDegreesPerSecond != 0f && !skyRotationAxis.isZero) {
            skyModelInstance.transform.rotate(skyRotationAxis, skyRotationAngleDegrees)
        }
        skyModelInstance.transform.setTranslation(camera.position)
    }

    private fun advanceSkyRotation(deltaSeconds: Float) {
        if (skyRotationDegreesPerSecond == 0f || skyRotationAxis.isZero) {
            return
        }
        skyRotationAngleDegrees += deltaSeconds * skyRotationDegreesPerSecond
        skyRotationAngleDegrees %= 360f
    }

    /**
     * Ensures a color+depth framebuffer matching current window size exists.
     *
     * This is the backbone for gameplay postprocessing and captures the whole
     * `Game3dScreen` frame (world/entities/particles/HUD) before compositing to backbuffer.
     */
    private fun ensureSceneFrameBuffer() {
        val width = Gdx.graphics.width.coerceAtLeast(1)
        val height = Gdx.graphics.height.coerceAtLeast(1)
        val current = sceneFrameBuffer
        if (current != null && current.width == width && current.height == height) {
            return
        }

        current?.dispose()
        sceneFrameBuffer = null
        sceneFrameRegion = null

        try {
            val recreated = FrameBuffer(Pixmap.Format.RGBA8888, width, height, true)
            sceneFrameBuffer = recreated
            sceneFrameRegion = TextureRegion(recreated.colorBufferTexture).apply {
                // LibGDX FBO color textures are upside-down in screen space.
                flip(false, true)
            }
        } catch (e: Exception) {
            Com.Warn("Failed to initialize scene framebuffer, falling back to direct render: ${e.message}\n")
            sceneFrameBuffer = null
            sceneFrameRegion = null
        }
    }

    private fun presentSceneFrame() {
        val region = sceneFrameRegion ?: return
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        postProcessBatch.projectionMatrix.setToOrtho2D(0f, 0f, width, height)
        val blend = entityManager.currentFrame.playerstate.blend
        val rdFlags = entityManager.currentFrame.playerstate.rdflags
        val underwaterPostEnabled = (rdFlags and Defines.RDF_UNDERWATER) != 0 &&
            RenderTuningCvars.underwaterWarpEnabled()
        val previousShader = postProcessBatch.shader
        postProcessBatch.shader = postProcessShader
        postProcessBatch.use {
            postProcessShader.setUniform4fv("u_blendColor", blend, 0, 4)
            postProcessShader.setUniformf("u_vignetteEnabled", if (RenderTuningCvars.postVignetteEnabled()) 1f else 0f)
            postProcessShader.setUniformf("u_vignetteStrength", RenderTuningCvars.postVignetteStrength())
            postProcessShader.setUniformf("u_underwaterEnabled", if (underwaterPostEnabled) 1f else 0f)
            postProcessShader.setUniformf("u_timeSeconds", Globals.curtime * 0.001f)
            it.draw(region, 0f, 0f, width, height)
        }
        postProcessBatch.shader = previousShader
    }

    private fun createPostProcessShader(): ShaderProgram {
        val vertexSource: String = assetManager.getLoaded(postProcessVertexShader)
        val fragmentSource: String = assetManager.getLoaded(postProcessFragmentShader)
        val shader = ShaderProgram(
            vertexSource,
            fragmentSource,
        )
        if (!shader.isCompiled) {
            throw GdxRuntimeException("Failed to compile postprocess shader: ${shader.log}")
        }
        return shader
    }

    override fun render(delta: Float) {
        presentationRuntime.render(delta)
    }

    /**
     * Returns whether this screen can produce a meaningful frame right now.
     *
     * World mode requires precache; cinematic mode can render without world precache.
     */
    fun canRenderPresentationFrame(): Boolean {
        return presentationMode == PresentationMode.CINEMATIC || precached
    }

    /**
     * Captures the latest scene framebuffer content as a pixmap.
     *
     * Intended for short-lived transition backdrops owned by outer runtime (`Cake`).
     * Caller owns and must dispose the returned pixmap.
     */
    fun captureScenePixmapForTransition(): Pixmap? {
        val frameBuffer = sceneFrameBuffer ?: return null
        frameBuffer.begin()
        return try {
            Pixmap.createFromFrameBuffer(0, 0, frameBuffer.width, frameBuffer.height)
        } finally {
            frameBuffer.end()
        }
    }

    /**
     * Delegates gameplay-world rendering to [WorldPresentationRenderer].
     */
    private fun renderWorldPresentation(delta: Float) {
        if (!precached)
            return

        ensureSceneFrameBuffer()
        worldPresentationRenderer.render(
            WorldPresentationRenderer.FrameContext(
                delta = delta,
                currentTimeMs = Globals.curtime,
                sceneFrameBuffer = sceneFrameBuffer,
                camera = camera,
                modelBatch = modelBatch,
                entityManager = entityManager,
                inputManager = inputManager,
                gameConfig = gameConfig,
                prediction = prediction,
                audioSystem = audioSystem,
                effectsSystem = effectsSystem,
                replicatedEntityEffectCollector = replicatedEntityEffectCollector,
                dynamicLightSystem = dynamicLightSystem,
                bspLightmapShader = bspLightmapShader,
                worldBatchRenderer = worldBatchRenderer,
                beamRenderer = beamRenderer,
                sp2Renderer = sp2Renderer,
                hudOverlayRenderer = hudOverlayRenderer,
                hud = hud,
                onSetLerpFraction = { lerpFrac = it },
                onUpdatePlayerView = ::updatePlayerView,
                onSyncEntityLoopSounds = ::syncEntityLoopSounds,
                onUpdateWorldVisibility = {
                    worldVisibilityMaskTracker?.update(camera.position, entityManager.currentFrame.areabits)
                },
                onRefreshLightStyles = ::refreshLightStyles,
                onAdvanceSkyRotation = ::advanceSkyRotation,
                onWorldVisibilityMaskSnapshot = ::worldVisibilityMask,
                onApplySkyTransform = ::applySkyTransform,
                onIsTranslucentModelPassEntity = ::isTranslucentModelPassEntity,
                onRenderModelEntity = ::renderModelEntity,
                onLightStyleResolver = ::lightStyleValue,
                onLogBatchDiagnostics = ::logBatchDiagnostics,
                onPresentSceneFrame = ::presentSceneFrame,
            )
        )
    }

    /**
     * Runtime split placeholder for cinematic/picture mode.
     *
     * This keeps server-message and networking ownership in `Game3dScreen`/`Cake` while delegating
     * cinematic media ownership and stepping to [CinematicPresentationController].
     */
    private fun renderCinematicPresentation(delta: Float) {
        // Cinematic mode is full-screen and starts from a black canvas.
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glDepthRangef(0f, 1f)

        cinematicController.render(
            currentTimeMs = Globals.curtime,
            spriteBatch = spriteBatch,
            screenWidth = Gdx.graphics.width.toFloat(),
            screenHeight = Gdx.graphics.height.toFloat(),
        )

        audioSystem.beginFrame(
            ListenerState(
                position = camera.position,
                forward = camera.direction,
                up = camera.up,
            )
        )
        // Future cinematic decoders may emit looped sounds; keep explicit loop state clean for now.
        audioSystem.syncEntityLoopingSounds(emptyList())
        hudOverlayRenderer.render(
            hud = hud,
            delta = delta,
            screenWidth = Gdx.graphics.width,
            screenHeight = Gdx.graphics.height,
            gameplayHudState = null,
        )
        audioSystem.endFrame()
    }

    private inner class WorldPresentationRuntime : PresentationRuntime {
        override fun render(delta: Float) {
            renderWorldPresentation(delta)
        }
    }

    private inner class CinematicPresentationRuntime : PresentationRuntime {
        override fun render(delta: Float) {
            renderCinematicPresentation(delta)
        }
    }

    private fun logBatchDiagnostics(frameDiagnostics: WorldPresentationRenderer.WorldFrameDiagnostics) {
        if (!RenderTuningCvars.bspBatchDebugEnabled()) {
            lastBatchDebugLogTimeMs = Int.MIN_VALUE
            return
        }
        if (lastBatchDebugLogTimeMs != Int.MIN_VALUE &&
            frameDiagnostics.currentTimeMs - lastBatchDebugLogTimeMs < BATCH_DEBUG_LOG_INTERVAL_MS
        ) {
            return
        }
        lastBatchDebugLogTimeMs = frameDiagnostics.currentTimeMs
        val opaqueWorldStats = worldBatchRenderer?.lastOpaqueStats
        val translucentWorldStats = worldBatchRenderer?.lastTranslucentStats
        val particleStats = effectsSystem.debugParticleRenderStats()
        Com.Printf(
            "bsp_batch_debug t=${frameDiagnostics.currentTimeMs} " +
                "worldOpaque(vis=${opaqueWorldStats?.visibleSurfaceCount ?: 0},grp=${opaqueWorldStats?.groupedSurfaceCount ?: 0},dc=${opaqueWorldStats?.drawCalls ?: 0}) " +
                "worldTrans(vis=${translucentWorldStats?.visibleSurfaceCount ?: 0},grp=${translucentWorldStats?.groupedSurfaceCount ?: 0},dc=${translucentWorldStats?.drawCalls ?: 0}) " +
                "models(vis=${frameDiagnostics.visibleEntities},opq=${frameDiagnostics.renderedOpaqueModels},trans=${frameDiagnostics.renderedTranslucentModels},depth=${frameDiagnostics.renderedDepthHackModels},level=${frameDiagnostics.levelEntityRendered}) " +
                "sprites(vis=${frameDiagnostics.visibleSprites},opq=${frameDiagnostics.renderedOpaqueSprites},trans=${frameDiagnostics.renderedTranslucentSprites}) " +
                "beams(vis=${frameDiagnostics.visibleBeams},draw=${frameDiagnostics.renderedBeams}) " +
                "particles(live=${effectsSystem.debugLiveParticleCount()},sub=${particleStats.submittedParticles},dc=${particleStats.drawCalls}) " +
                "effects(active=${effectsSystem.debugActiveEffectCount()})\n"
        )
    }

    private fun worldVisibilityMask(): BooleanArray {
        worldVisibilityMaskTracker?.let { return it.visibleSurfaceMaskSnapshot() }
        return BooleanArray(0)
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
            // Shells are currently represented as a Fresnel rim highlight (single-pass approximation).
            userData.highlightEnabled = 1f
            userData.highlightRed = red
            userData.highlightGreen = green
            userData.highlightBlue = blue
            userData.highlightStrength = 0.9f
            userData.highlightRimPower = 2.5f
        } else {
            userData.highlightEnabled = 0f
            userData.highlightRed = 0f
            userData.highlightGreen = 0f
            userData.highlightBlue = 0f
            userData.highlightStrength = 0f
            userData.highlightRimPower = 2.5f
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
            val scale = 0.1f * sin(interpolatedClientTimeSeconds() * 7f)
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

    /**
     * Legacy-equivalent continuous client render time in seconds.
     *
     * Legacy glow pulse (`RF_GLOW`) uses `sin(r_newrefdef.time * 7)` where time advances every render
     * frame (not just on server snapshots).
     */
    private fun interpolatedClientTimeSeconds(): Float {
        val serverTimeMs = entityManager.currentFrame.servertime.toFloat()
        val clientTimeMs = serverTimeMs - (1f - lerpFrac.coerceIn(0f, 1f)) * 100f
        return clientTimeMs * 0.001f
    }

    private fun setMd2ShadeVector(userData: Md2CustomData, yawDegrees: Float) {
        val shadeVector = computeMd2ShadeVector(yawDegrees = yawDegrees)
        userData.shadeVectorX = shadeVector.x
        userData.shadeVectorY = shadeVector.y
        userData.shadeVectorZ = shadeVector.z
    }

    override fun dispose() {
        sceneFrameBuffer?.dispose()
        sceneFrameBuffer = null
        sceneFrameRegion = null
        postProcessShader.dispose()
        postProcessBatch.dispose()
        cinematicController.dispose()
        worldBatchRenderer?.dispose()
        worldBatchRenderer = null
        worldVisibilityMaskTracker = null
        inlineTextureAnimationController = null
        inlineSurfaceMaterialController = null
        entityLightSampler = null
        audioSystem.dispose()
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
     * - Quake2 BSP `model 0` (static world) is rendered by [BspWorldBatchRenderer].
     * - Legacy per-face world `ModelInstance` path is no longer used.
     *
     * todo: make resource loading asynchronous
     */
    fun precache() {
        if (precached) {
            Com.Warn("precache called for an already-precached Game3dScreen")
            return
        }
        prediction.reset()
        worldBatchRenderer?.dispose()
        worldBatchRenderer = null

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

        collisionModel.CM_LoadMapFile(bspMap.mapData, mapName, IntArray(1) {0})
        worldVisibilityMaskTracker = BspWorldVisibilityMaskTracker(
            worldRenderData = bspMap.worldRenderData,
            collisionModel = collisionModel,
        )
        worldBatchRenderer = BspWorldBatchRenderer(
            worldRenderData = bspMap.worldRenderData,
            worldBatchData = bspMap.worldBatchData,
            lightmapAtlasPages = bspMap.lightmapAtlasPages,
            assetManager = assetManager,
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

    fun stopAudio() {
        audioSystem.stopAll()
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

        if (msg.playerNumber == -1) {
            presentationMode = PresentationMode.CINEMATIC
            presentationRuntime = cinematicPresentationRuntime
            cinematicController.begin(levelString, Globals.curtime)
        } else {
            presentationMode = PresentationMode.WORLD
            presentationRuntime = worldPresentationRuntime
            cinematicController.end()
        }

        // ServerDataMessage is the authoritative game/mod style switch point.
        // Cake recreates Game3dScreen for each fresh serverdata sequence, so one HUD per screen is expected.
        hud?.dispose() // defensive: avoid leaking style resources if serverdata is unexpectedly repeated.
        val gameUiStyle = GameUiStyleFactory.create(gameName, assetManager, Scene2DSkin.defaultSkin)
        hud = Hud(spriteBatch, gameUiStyle, GameConfigLayoutDataProvider(gameConfig))
    }

    /**
     * Returns one-shot `nextserver` command when cinematic skip criteria are met.
     *
     * Legacy cross-reference:
     * - q2pro `src/client/keys.c`: button-style keydown in cinematic triggers `SCR_FinishCinematic()`.
     * - q2pro `src/client/cin.c`: `SCR_FinishCinematic` sends `nextserver <servercount>`.
     * - q2pro `src/client/cin.c`: EOF also triggers `SCR_FinishCinematic`.
     * - Jake2/Yamagi only allow skip after a short guard delay to avoid accidental abort.
     * - Server expects `nextserver <spawncount>` while in `SS_CINEMATIC` / `SS_PIC`.
     */
    fun pollCinematicSkipCommand(currentTimeMs: Int): StringCmdMessage? {
        if (presentationMode != PresentationMode.CINEMATIC) {
            return null
        }
        return cinematicController.pollSkipCommand(
            currentTimeMs = currentTimeMs,
            spawnCount = spawnCount,
            hasImmediateAction = inputManager.hasActiveImmediateAction(),
        )
    }

    override fun processConfigStringMessage(msg: ConfigStringMessage) {
        gameConfig.applyConfigString(msg.index, msg.config, loadResource = precached)

        when (msg.index) {
            Defines.CS_SKY -> refreshSkyBox()
            Defines.CS_SKYROTATE, Defines.CS_SKYAXIS -> refreshSkyRotation()
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
            soundMessageHandler.playEntityEventSounds()
            // Cross-reference: old `CL_ents.parsePacketEntities` calls `CL_pred.CheckPredictionError`
            // once a valid frame has been fully reconstructed.
            prediction.onServerFrameParsed(entityManager.currentFrame)
        }
        return validMessage
    }

    override fun processSoundMessage(msg: SoundMessage) {
        soundMessageHandler.processSoundMessage(msg)
    }

    /**
     * Handles `MZ_*` weapon events (sound + one-shot muzzle dynamic light + special login/logout burst).
     *
     * Legacy counterpart: `client/CL_fx.ParseMuzzleFlash`.
     */
    override fun processWeaponSoundMessage(msg: WeaponSoundMessage) {
        soundMessageHandler.processWeaponSoundMessage(msg)
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

    private fun syncEntityLoopSounds() {
        soundMessageHandler.syncEntityLoopSounds()
    }

    companion object {
        private const val BATCH_DEBUG_LOG_INTERVAL_MS = 1000
    }
}
