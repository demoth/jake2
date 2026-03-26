package org.demoth.cake.ui

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.List as Scene2dList
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable

data class MenuWidgetStyles(
    val label: Label.LabelStyle,
    val button: TextButton.TextButtonStyle,
    val textField: TextField.TextFieldStyle,
    val selectBox: SelectBox.SelectBoxStyle,
    val list: Scene2dList.ListStyle,
    val scrollPane: ScrollPane.ScrollPaneStyle,
)

private val MENU_HOVER_TEXT_COLOR = Color(0.35f, 0.95f, 0.35f, 1f)
private val MENU_SELECTION_COLOR = Color(0.22f, 0.35f, 0.22f, 0.95f)

data class MenuSoundStyles(
    val enterSubmenu: Sound? = null,
    val hoverButton: Sound? = null,
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
private const val SCROLL_TRACK_MIDDLE = 129
private const val SCROLL_KNOB = 131

internal fun createMenuWidgetStyles(
    skin: Skin,
    font: BitmapFont,
    concharsTexture: Texture? = null,
): MenuWidgetStyles {
    val baseLabelStyle = skin.get(Label.LabelStyle::class.java)
    val baseButtonStyle = skin.get(TextButton.TextButtonStyle::class.java)
    val baseTextFieldStyle = skin.get(TextField.TextFieldStyle::class.java)
    val baseSelectBoxStyle = skin.get(SelectBox.SelectBoxStyle::class.java)
    val baseListStyle = skin.get(Scene2dList.ListStyle::class.java)
    val baseScrollPaneStyle = skin.get(ScrollPane.ScrollPaneStyle::class.java)
    val contentButtonDrawable = concharsTexture?.let { createConcharsButtonDrawable(it) }
    val contentSelectionDrawable = concharsTexture?.let { createConcharsFillDrawable(it, BOX_FILL, MENU_SELECTION_COLOR) }
    val contentScrollTrackDrawable = concharsTexture?.let { createConcharsGlyphDrawable(it, SCROLL_TRACK_MIDDLE) }
    val contentScrollKnobDrawable = concharsTexture?.let { createConcharsGlyphDrawable(it, SCROLL_KNOB) }
    val contentScrollPaneStyle = ScrollPane.ScrollPaneStyle(baseScrollPaneStyle).also { style ->
        if (contentButtonDrawable != null) {
            style.background = contentButtonDrawable
        }
        if (contentScrollTrackDrawable != null) {
            style.vScroll = contentScrollTrackDrawable
        }
        if (contentScrollKnobDrawable != null) {
            style.vScrollKnob = contentScrollKnobDrawable
        }
    }
    val contentListStyle = Scene2dList.ListStyle(baseListStyle).also { style ->
        style.font = font
        style.fontColorSelected = MENU_HOVER_TEXT_COLOR.cpy()
        style.fontColorUnselected = baseSelectBoxStyle.fontColor?.cpy() ?: Color.WHITE.cpy()
        if (contentSelectionDrawable != null) {
            style.selection = contentSelectionDrawable
        }
        style.background = null
    }
    return MenuWidgetStyles(
        label = Label.LabelStyle(baseLabelStyle).also { it.font = font },
        button = TextButton.TextButtonStyle(baseButtonStyle).also { style ->
            style.font = font
            style.overFontColor = MENU_HOVER_TEXT_COLOR.cpy()
            if (contentButtonDrawable != null) {
                style.up = contentButtonDrawable
                style.over = contentButtonDrawable
                style.checked = contentButtonDrawable
                style.down = contentButtonDrawable.tint(Color(0.7f, 0.7f, 0.7f, 1f))
                style.disabled = contentButtonDrawable.tint(Color(0.45f, 0.45f, 0.45f, 0.9f))
            }
        },
        textField = TextField.TextFieldStyle(baseTextFieldStyle).also { style ->
            style.font = font
            style.messageFont = font
            if (contentButtonDrawable != null) {
                style.background = contentButtonDrawable
                style.focusedBackground = contentButtonDrawable
                style.disabledBackground = contentButtonDrawable.tint(Color(0.45f, 0.45f, 0.45f, 0.9f))
            }
        },
        selectBox = SelectBox.SelectBoxStyle(baseSelectBoxStyle).also { style ->
            style.font = font
            style.overFontColor = MENU_HOVER_TEXT_COLOR.cpy()
            style.scrollStyle = ScrollPane.ScrollPaneStyle(contentScrollPaneStyle)
            style.listStyle = Scene2dList.ListStyle(contentListStyle)
            if (contentButtonDrawable != null) {
                style.background = contentButtonDrawable
                style.backgroundOver = contentButtonDrawable
                style.backgroundOpen = contentButtonDrawable
                style.backgroundDisabled = contentButtonDrawable.tint(Color(0.45f, 0.45f, 0.45f, 0.9f))
            }
        },
        list = contentListStyle,
        scrollPane = contentScrollPaneStyle,
    )
}

private fun createConcharsButtonDrawable(texture: Texture): NinePatchDrawable {
    return NinePatchDrawable(
        NinePatch(
            concharsGlyph(texture, BOX_TOP_LEFT),
            concharsGlyph(texture, BOX_TOP),
            concharsGlyph(texture, BOX_TOP_RIGHT),
            concharsGlyph(texture, BOX_LEFT),
            concharsGlyph(texture, BOX_FILL),
            concharsGlyph(texture, BOX_RIGHT),
            concharsGlyph(texture, BOX_BOTTOM_LEFT),
            concharsGlyph(texture, BOX_BOTTOM),
            concharsGlyph(texture, BOX_BOTTOM_RIGHT),
        ),
    )
}

private fun createConcharsFillDrawable(
    texture: Texture,
    glyphIndex: Int,
    tint: Color,
): Drawable = createConcharsGlyphDrawable(texture, glyphIndex).tint(tint)

private fun createConcharsGlyphDrawable(
    texture: Texture,
    glyphIndex: Int,
): TextureRegionDrawable = TextureRegionDrawable(concharsGlyph(texture, glyphIndex))

private fun concharsGlyph(
    texture: Texture,
    glyphIndex: Int,
): TextureRegion {
    val cells = ConcharsFontLoader.mapCells(texture.width, texture.height).associateBy { it.index }
    val cell = requireNotNull(cells[glyphIndex]) { "Missing conchars glyph $glyphIndex" }
    return TextureRegion(texture, cell.x, cell.y, cell.width, cell.height)
}
