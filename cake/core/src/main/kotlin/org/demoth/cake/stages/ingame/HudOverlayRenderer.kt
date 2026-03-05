package org.demoth.cake.stages.ingame

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import jake2.qcommon.Defines
import jake2.qcommon.player_state_t
import ktx.graphics.use
import org.demoth.cake.GameConfiguration
import org.demoth.cake.stages.ingame.hud.Hud

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
        screenWidth: Int,
        screenHeight: Int,
        gameplayHudState: GameplayHudState?,
    ) {
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
