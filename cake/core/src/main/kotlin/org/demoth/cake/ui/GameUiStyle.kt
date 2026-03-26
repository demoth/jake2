package org.demoth.cake.ui

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Disposable

data class MenuWidgetStyles(
    val label: Label.LabelStyle,
    val button: TextButton.TextButtonStyle,
)

data class MenuSoundStyles(
    val enterSubmenu: Sound? = null,
    val exitSubmenu: Sound? = null,
)

/**
 * Runtime content style resources bound to the currently active game/mod.
 * This wraps HUD rendering primitives plus menu widget/audio styles derived from the same content context.
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

    /**
     * Menu enter/exit sounds shared by content-styled menus.
     */
    val menuSounds: MenuSoundStyles
}

/**
 * Engine default style backed by the existing Scene2D skin.
 */
class EngineUiStyle(
    private val skin: Skin,
    override val menuSounds: MenuSoundStyles = MenuSoundStyles(),
    private val onDispose: () -> Unit = {},
) : GameUiStyle {
    override val hudFont: BitmapFont
        get() = skin.getFont("default")
    override val hudNumberFont: HudNumberFont = EngineHudNumberFont { hudFont }
    override val menuWidgets: MenuWidgetStyles = createMenuWidgetStyles(skin, hudFont)

    override fun dispose() {
        hudNumberFont.dispose()
        onDispose()
        // Skin owns the default font.
    }
}

private const val BOX_TOP_LEFT = 1
private const val BOX_TOP = 2
private const val BOX_TOP_RIGHT = 3
private const val BOX_LEFT = 4
private const val BOX_FILL = 5
private const val BOX_RIGHT = 6
private const val BOX_BOTTOM_LEFT = 7
private const val BOX_BOTTOM = 8
private const val BOX_BOTTOM_RIGHT = 9

internal fun createMenuWidgetStyles(
    skin: Skin,
    font: BitmapFont,
    concharsTexture: Texture? = null,
): MenuWidgetStyles {
    val baseLabelStyle = skin.get(Label.LabelStyle::class.java)
    val baseButtonStyle = skin.get(TextButton.TextButtonStyle::class.java)
    val contentButtonDrawable = concharsTexture?.let { createConcharsButtonDrawable(it) }
    return MenuWidgetStyles(
        label = Label.LabelStyle(baseLabelStyle).also { it.font = font },
        button = TextButton.TextButtonStyle(baseButtonStyle).also { style ->
            style.font = font
            if (contentButtonDrawable != null) {
                style.up = contentButtonDrawable
                style.over = contentButtonDrawable
                style.checked = contentButtonDrawable
                style.down = contentButtonDrawable.tint(Color(0.7f, 0.7f, 0.7f, 1f))
                style.disabled = contentButtonDrawable.tint(Color(0.45f, 0.45f, 0.45f, 0.9f))
            }
        },
    )
}

private fun createConcharsButtonDrawable(texture: Texture): NinePatchDrawable {
    val cells = ConcharsFontLoader.mapCells(texture.width, texture.height).associateBy { it.index }
    fun glyph(index: Int): TextureRegion {
        val cell = requireNotNull(cells[index]) { "Missing conchars glyph $index" }
        return TextureRegion(texture, cell.x, cell.y, cell.width, cell.height)
    }

    return NinePatchDrawable(
        NinePatch(
            glyph(BOX_TOP_LEFT),
            glyph(BOX_TOP),
            glyph(BOX_TOP_RIGHT),
            glyph(BOX_LEFT),
            glyph(BOX_FILL),
            glyph(BOX_RIGHT),
            glyph(BOX_BOTTOM_LEFT),
            glyph(BOX_BOTTOM),
            glyph(BOX_BOTTOM_RIGHT),
        ),
    )
}
