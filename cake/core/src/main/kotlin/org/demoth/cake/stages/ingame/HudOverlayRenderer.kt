package org.demoth.cake.stages.ingame

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import jake2.qcommon.Defines
import jake2.qcommon.player_state_t
import ktx.graphics.use
import org.demoth.cake.config.GameConfiguration
import org.demoth.cake.stages.ingame.hud.Hud
import org.demoth.cake.ui.UiViewport

/**
 * Draws in-game HUD overlays for both world and cinematic presentation modes.
 *
 * Legacy references:
 * - `SCR_DrawStats` (statusbar layout)
 * - `SCR_DrawLayout` (additional layout/help/score)
 * - `CL_inv.DrawInventory` (inventory overlay)
 */
internal class HudOverlayRenderer(
    private val spriteBatch: SpriteBatch,
    private val gameConfig: GameConfiguration,
    private val uiViewport: UiViewport,
) {

    data class GameplayHudState(
        val serverFrame: Int,
        val playerState: player_state_t,
        val statusBarLayout: String?,
        val additionalLayout: String?,
    )

    fun render(
        hud: Hud?,
        delta: Float,
        gameplayHudState: GameplayHudState?,
    ) {
        // The viewport covers the full target, including when drawing into the physical-pixel scene FBO.
        // Only change the batch projection here; framebuffer binding owns the GL viewport.
        spriteBatch.projectionMatrix = uiViewport.camera.combined
        val screenWidth = uiViewport.worldWidth.toInt()
        val screenHeight = uiViewport.worldHeight.toInt()
        spriteBatch.use {
            hud?.update(delta, screenWidth, screenHeight)

            val state = gameplayHudState ?: return@use

            hud?.drawCrosshair(
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )

            hud?.executeLayout(
                layout = state.statusBarLayout,
                serverFrame = state.serverFrame,
                stats = state.playerState.stats,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )

            // draw additional layout, like help or score
            // Legacy counterpart: `SCR_DrawLayout`
            if ((state.playerState.stats[Defines.STAT_LAYOUTS].toInt() and 1) != 0) {
                hud?.executeLayout(
                    layout = state.additionalLayout,
                    serverFrame = state.serverFrame,
                    stats = state.playerState.stats,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                )
            }
            // draw additional layout, like help or score
            // Legacy counterpart: `CL_inv.DrawInventory`
            if ((state.playerState.stats[Defines.STAT_LAYOUTS].toInt() and 2) != 0) {
                hud?.drawInventory(
                    playerstate = state.playerState,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    gameConfig = gameConfig,
                )
            }
        }
    }
}
