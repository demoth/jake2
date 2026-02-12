package org.demoth.cake.ui

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable

/**
 * Runtime UI style resources bound to the currently active game.
 * This wraps all HUD text resources that are swapped when the game/mod changes.
 */
interface GameUiStyle : Disposable {
    val hudFont: BitmapFont
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
