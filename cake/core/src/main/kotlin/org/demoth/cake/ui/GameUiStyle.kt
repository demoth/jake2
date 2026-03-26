package org.demoth.cake.ui

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Disposable

data class MenuWidgetStyles(
    val label: Label.LabelStyle,
    val button: TextButton.TextButtonStyle,
)

/**
 * Runtime content style resources bound to the currently active game/mod.
 * This wraps HUD rendering primitives plus menu widget styles derived from the same content context.
 *
 * Ownership:
 * created by `GameUiStyleFactory` and owned/disposed by `Cake`.
 * `Hud` borrows the style for rendering only.
 *
 * Timing:
 * accessed only from render-thread HUD/menu drawing code.
 *
 * Related component:
 * `org.demoth.cake.stages.ingame.hud.Hud`.
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

    /**
     * Menu label/button styles shared by content-styled menus.
     */
    val menuWidgets: MenuWidgetStyles
}

/**
 * Engine default style backed by the existing Scene2D skin.
 */
class EngineUiStyle(private val skin: Skin) : GameUiStyle {
    override val hudFont: BitmapFont
        get() = skin.getFont("default")
    override val hudNumberFont: HudNumberFont = EngineHudNumberFont { hudFont }
    override val menuWidgets: MenuWidgetStyles = createMenuWidgetStyles(skin, hudFont)

    override fun dispose() {
        hudNumberFont.dispose()
        // Skin owns the default font.
    }
}

internal fun createMenuWidgetStyles(skin: Skin, font: BitmapFont): MenuWidgetStyles {
    val baseLabelStyle = skin.get(Label.LabelStyle::class.java)
    val baseButtonStyle = skin.get(TextButton.TextButtonStyle::class.java)
    return MenuWidgetStyles(
        label = Label.LabelStyle(baseLabelStyle).also { it.font = font },
        button = TextButton.TextButtonStyle(baseButtonStyle).also { it.font = font },
    )
}
