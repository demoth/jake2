package org.demoth.cake.stages.ingame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import org.demoth.cake.ClientEntity
import org.demoth.cake.GameConfiguration
import jake2.qcommon.Defines
import org.demoth.cake.audio.CakeAudioSystem
import org.demoth.cake.audio.ListenerState
import org.demoth.cake.assets.BeamRenderer
import org.demoth.cake.assets.BspLightmapShader
import org.demoth.cake.assets.Sp2Renderer
import org.demoth.cake.input.InputManager
import org.demoth.cake.stages.ingame.effects.ClientEffectsSystem
import org.demoth.cake.stages.ingame.hud.Hud

/**
 * Orchestrates one gameplay-world presentation frame.
 *
 * This class preserves legacy pass ordering while keeping orchestration out of [Game3dScreen]:
 * prediction/view -> world visibility/lightstyle -> world passes -> entity/sprite/beam passes ->
 * translucent world/effects -> HUD -> postprocess present.
 */
internal class WorldPresentationRenderer {

    /**
     * Frame-level diagnostics emitted by the caller-specific logger.
     */
    data class WorldFrameDiagnostics(
        val currentTimeMs: Int,
        val visibleEntities: Int,
        val renderedOpaqueModels: Int,
        val renderedTranslucentModels: Int,
        val renderedDepthHackModels: Int,
        val visibleSprites: Int,
        val renderedOpaqueSprites: Int,
        val renderedTranslucentSprites: Int,
        val visibleBeams: Int,
        val renderedBeams: Int,
        val levelEntityRendered: Boolean,
    )

    /**
     * Runtime dependencies and callbacks required to render one world frame.
     *
     * The callback set deliberately keeps ownership in [Game3dScreen] for systems not yet extracted.
     */
    data class FrameContext(
        val delta: Float,
        val currentTimeMs: Int,
        val sceneFrameBuffer: FrameBuffer?,
        val camera: PerspectiveCamera,
        val modelBatch: ModelBatch,
        val entityManager: ClientEntityManager,
        val inputManager: InputManager,
        val gameConfig: GameConfiguration,
        val prediction: ClientPrediction,
        val audioSystem: CakeAudioSystem,
        val effectsSystem: ClientEffectsSystem,
        val replicatedEntityEffectCollector: ReplicatedEntityEffectCollector,
        val dynamicLightSystem: DynamicLightSystem,
        val bspLightmapShader: BspLightmapShader,
        val worldBatchRenderer: BspWorldBatchRenderer?,
        val beamRenderer: BeamRenderer,
        val sp2Renderer: Sp2Renderer,
        val hudOverlayRenderer: HudOverlayRenderer,
        val hud: Hud?,
        val onSetLerpFraction: (Float) -> Unit,
        val onUpdatePlayerView: (Float) -> Unit,
        val onSyncEntityLoopSounds: () -> Unit,
        val onUpdateWorldVisibility: () -> Unit,
        val onRefreshLightStyles: (Int) -> Unit,
        val onAdvanceSkyRotation: (Float) -> Unit,
        val onWorldVisibilityMaskSnapshot: () -> BooleanArray,
        val onApplySkyTransform: (ModelInstance) -> Unit,
        val onIsTranslucentModelPassEntity: (ClientEntity) -> Boolean,
        val onRenderModelEntity: (ModelBatch, ClientEntity) -> Unit,
        val onLightStyleResolver: (Int) -> Float,
        val onLogBatchDiagnostics: (WorldFrameDiagnostics) -> Unit,
        val onPresentSceneFrame: () -> Unit,
    )

    /**
     * Renders one world frame using strict pass ordering parity.
     */
    fun render(context: FrameContext) {
        val frameBuffer = context.sceneFrameBuffer
        frameBuffer?.begin()

        // Keep depth/color buffers deterministic per frame.
        // This avoids stale depth values leaking between passes when custom world batching is enabled.
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        // Reset depth range defensively before any pass submission.
        Gdx.gl.glDepthRangef(0f, 1f)

        val serverFrameTime = 1f / 10f // 10Hz server updates
        val lerpFrac = (context.entityManager.lerpAcc / serverFrameTime).coerceIn(0f, 1f)
        context.onSetLerpFraction(lerpFrac)

        // Cross-reference: old frame order in `CL.Frame()` calls `CL_pred.PredictMovement()`
        // before calculating render view (`CL_ents.CalcViewValues`).
        context.prediction.predictMovement(
            context.entityManager.currentFrame,
            context.inputManager,
            context.gameConfig.playerConfiguration.playerIndex,
        )

        context.onUpdatePlayerView(lerpFrac)
        context.audioSystem.beginFrame(
            ListenerState(
                position = context.camera.position,
                forward = context.camera.direction,
                up = context.camera.up,
            )
        )
        context.onSyncEntityLoopSounds()
        context.onUpdateWorldVisibility()
        context.onRefreshLightStyles(context.currentTimeMs)
        context.onAdvanceSkyRotation(context.delta)
        context.effectsSystem.update(context.delta, context.entityManager.currentFrame.serverframe)
        context.replicatedEntityEffectCollector.collectTrails(lerpFrac)
        context.dynamicLightSystem.beginFrame(context.currentTimeMs, context.delta)
        context.replicatedEntityEffectCollector.collectDynamicLights(
            lerpFrac = lerpFrac,
            currentTimeMs = context.currentTimeMs,
        )
        val shaderDynamicLights = context.dynamicLightSystem.visibleLightsForShader()
        context.bspLightmapShader.setDynamicLights(shaderDynamicLights)
        val visibleWorldSurfaceMask = context.onWorldVisibilityMaskSnapshot()
        context.worldBatchRenderer?.render(
            camera = context.camera,
            visibleSurfaceMask = visibleWorldSurfaceMask,
            currentTimeMs = context.currentTimeMs,
            lightStyleResolver = context.onLightStyleResolver,
            dynamicLights = shaderDynamicLights,
        )

        // Render entities.
        val lateDepthHackEntities = mutableListOf<ClientEntity>()
        var renderedOpaqueModels = 0
        var renderedTranslucentModels = 0
        var renderedDepthHackModels = 0
        var renderedBeams = 0
        var renderedOpaqueSprites = 0
        var renderedTranslucentSprites = 0
        var levelEntityRendered = false

        context.modelBatch.begin(context.camera)
        try {
            if (context.entityManager.rDrawSky?.value != 0f) {
                context.entityManager.skyEntity?.modelInstance?.let { skyModelInstance ->
                    Gdx.gl.glDepthMask(false)
                    context.onApplySkyTransform(skyModelInstance)
                    context.modelBatch.render(skyModelInstance)
                    Gdx.gl.glDepthMask(true)
                }
            }

            context.entityManager.visibleEntities.forEach {
                if (it.name == "level") {
                    return@forEach
                }
                if (!context.onIsTranslucentModelPassEntity(it)) {
                    if (it.depthHack) {
                        lateDepthHackEntities += it
                    } else {
                        context.onRenderModelEntity(context.modelBatch, it)
                        renderedOpaqueModels++
                        if (it.name == "level") {
                            levelEntityRendered = true
                        }
                    }
                }
            }
            context.entityManager.visibleEntities.forEach {
                if (it.name == "level") {
                    return@forEach
                }
                if (context.onIsTranslucentModelPassEntity(it)) {
                    if (it.depthHack) {
                        lateDepthHackEntities += it
                    } else {
                        context.onRenderModelEntity(context.modelBatch, it)
                        renderedTranslucentModels++
                        if (it.name == "level") {
                            levelEntityRendered = true
                        }
                    }
                }
            }
            context.entityManager.visibleBeams.forEach {
                context.beamRenderer.render(context.modelBatch, it)
                renderedBeams++
            }
            // Preserve legacy draw ordering: opaque first, translucent second.
            // Do not merge into a naive single pass; mixed ordering breaks alpha/depth results.
            context.entityManager.visibleSprites.forEach {
                if ((it.resolvedRenderFx and Defines.RF_TRANSLUCENT) == 0) {
                    context.sp2Renderer.render(context.modelBatch, it, context.camera, lerpFrac)
                    renderedOpaqueSprites++
                }
            }
            context.entityManager.visibleSprites.forEach {
                if ((it.resolvedRenderFx and Defines.RF_TRANSLUCENT) != 0) {
                    context.sp2Renderer.render(context.modelBatch, it, context.camera, lerpFrac)
                    renderedTranslucentSprites++
                }
            }
            context.entityManager.lerpAcc += context.delta
        } finally {
            context.modelBatch.end()
        }

        context.worldBatchRenderer?.renderTranslucent(
            camera = context.camera,
            visibleSurfaceMask = visibleWorldSurfaceMask,
            currentTimeMs = context.currentTimeMs,
        )
        context.effectsSystem.renderParticles(context.camera)
        context.modelBatch.begin(context.camera)
        try {
            context.effectsSystem.render(context.modelBatch)
        } finally {
            context.modelBatch.end()
        }
        if (lateDepthHackEntities.isNotEmpty()) {
            context.modelBatch.begin(context.camera)
            try {
                lateDepthHackEntities.forEach {
                    context.onRenderModelEntity(context.modelBatch, it)
                    renderedDepthHackModels++
                    if (it.name == "level") {
                        levelEntityRendered = true
                    }
                }
            } finally {
                context.modelBatch.end()
            }
        }

        context.onLogBatchDiagnostics(
            WorldFrameDiagnostics(
                currentTimeMs = context.currentTimeMs,
                visibleEntities = context.entityManager.visibleEntities.size,
                renderedOpaqueModels = renderedOpaqueModels,
                renderedTranslucentModels = renderedTranslucentModels,
                renderedDepthHackModels = renderedDepthHackModels,
                visibleSprites = context.entityManager.visibleSprites.size,
                renderedOpaqueSprites = renderedOpaqueSprites,
                renderedTranslucentSprites = renderedTranslucentSprites,
                visibleBeams = context.entityManager.visibleBeams.size,
                renderedBeams = renderedBeams,
                levelEntityRendered = levelEntityRendered,
            )
        )

        val currentFrame = context.entityManager.currentFrame
        context.hudOverlayRenderer.render(
            hud = context.hud,
            delta = context.delta,
            screenWidth = Gdx.graphics.width,
            screenHeight = Gdx.graphics.height,
            gameplayHudState = HudOverlayRenderer.GameplayHudState(
                serverFrame = currentFrame.serverframe,
                playerState = currentFrame.playerstate,
                statusBarLayout = context.gameConfig.getStatusBarLayout(),
                additionalLayout = context.gameConfig.layout,
            ),
        )
        context.audioSystem.endFrame()

        if (frameBuffer != null) {
            frameBuffer.end()
            context.onPresentSceneFrame()
        }
    }
}
