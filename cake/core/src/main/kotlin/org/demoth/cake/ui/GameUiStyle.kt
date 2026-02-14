package org.demoth.cake.ui

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable

/**
 * Runtime UI style resources bound to the currently active game.
 * This wraps all HUD text resources that are swapped when the game/mod changes.
 *
 * Ownership:
 * created by `GameUiStyleFactory` and owned/disposed by `Hud` (constructed by `Game3dScreen`).
 */
interface GameUiStyle : Disposable {
    /**
     * Primary HUD console font (`pics/conchars.pcx` for IdTech2 style).
     */
    val hudFont: BitmapFont

    /**
     * Numeric HUD field renderer (`num_*` / `anum_*` for IdTech2 style).
     */
    val hudNumberFont: HudNumberFont
}

/**
 * Engine default style backed by the existing Scene2D skin.
 */
class EngineUiStyle(private val skin: Skin) : GameUiStyle {
    override val hudFont: BitmapFont
        get() = skin.getFont("default")
    override val hudNumberFont: HudNumberFont = EngineHudNumberFont { hudFont }

    override fun dispose() {
        hudNumberFont.dispose()
        // Skin owns the default font.
    }
}
