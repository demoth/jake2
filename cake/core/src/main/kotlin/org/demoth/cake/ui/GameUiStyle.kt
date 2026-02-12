package org.demoth.cake.ui

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable

/**
 * Runtime UI style resources bound to the currently active game.
 * The initial scope is HUD bitmap font only.
 */
interface GameUiStyle : Disposable {
    val hudFont: BitmapFont
}

/**
 * Engine default style backed by the existing Scene2D skin.
 */
class EngineUiStyle(private val skin: Skin) : GameUiStyle {
    override val hudFont: BitmapFont = skin.getFont("default")

    override fun dispose() {
        // Skin owns the default font.
    }
}
