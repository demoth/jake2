package org.demoth.cake.ui

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.List as Scene2dList
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Slider
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
    val checkBox: TextButton.TextButtonStyle,
    val textField: TextField.TextFieldStyle,
    val selectBox: SelectBox.SelectBoxStyle,
    val list: Scene2dList.ListStyle,
    val scrollPane: ScrollPane.ScrollPaneStyle,
    val containerPanel: Drawable,
    val controlPanel: Drawable,
    val slider: Slider.SliderStyle,
    val progress: ProgressBar.ProgressBarStyle,
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

private const val BOX_FILL = 5

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
    val containerPanel = concharsTexture?.let { createConcharsPanelDrawable(it, 1) }
        ?: skin.getDrawable("console-panel")
    val contentButtonDrawable = concharsTexture?.let { texture ->
        createConcharsPanelDrawable(texture, 18).apply {
            // Thin frames need half a cell of content inset, while the artwork keeps its corners.
            val inset = texture.width / ConcharsFontLoader.GRID_SIZE / 2f
            patch = patch.apply { setPadding(inset, inset, inset, inset) }
            minWidth = patch.leftWidth + patch.rightWidth
            minHeight = patch.topHeight + patch.bottomHeight
        }
    }
    val slider = Slider.SliderStyle(skin.get("default-horizontal", Slider.SliderStyle::class.java))
    if (concharsTexture != null) {
        val track = NinePatchDrawable(NinePatch(
            null, null, null,
            concharsGlyph(concharsTexture, 128), concharsGlyph(concharsTexture, 129), concharsGlyph(concharsTexture, 130),
            null, null, null,
        ))
        val knob = createConcharsGlyphDrawable(concharsTexture, 131)
        slider.background = track
        slider.knob = knob
        slider.knobOver = knob
        slider.knobDown = knob.tint(Color(0.7f, 0.7f, 0.7f, 1f))
        slider.disabledBackground = track.tint(Color(0.45f, 0.45f, 0.45f, 1f))
        slider.disabledKnob = knob.tint(Color(0.45f, 0.45f, 0.45f, 1f))
    }
    // A read-only position indicator uses the same track and knob as the editable slider.
    val progress = ProgressBar.ProgressBarStyle(slider)
    val contentSelectionDrawable = concharsTexture?.let { createConcharsFillDrawable(it, BOX_FILL, MENU_SELECTION_COLOR) }
    // ScrollPane stretches its track and proportional thumb vertically. A nine-patch keeps
    // their borders intact; the horizontal slider glyphs in conchars cannot be stretched this way.
    val contentScrollKnobDrawable = contentButtonDrawable?.let {
        NinePatchDrawable(NinePatch(it.patch).apply { scale(0.5f, 0.5f) })
    }
    val contentScrollTrackDrawable = contentScrollKnobDrawable?.tint(Color(0.45f, 0.45f, 0.45f, 1f))
    val contentScrollPaneStyle = ScrollPane.ScrollPaneStyle(baseScrollPaneStyle).also { style ->
        if (contentButtonDrawable != null) {
            style.background = containerPanel
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
        // A toggle button draws the font's x inside the same border as other controls.
        checkBox = TextButton.TextButtonStyle().also { style ->
            style.up = contentButtonDrawable ?: baseButtonStyle.up
            style.font = font
            style.fontColor = Color.CLEAR.cpy()
            style.checkedFontColor = baseLabelStyle.fontColor.cpy()
            style.overFontColor = MENU_HOVER_TEXT_COLOR.cpy()
            style.checkedOverFontColor = MENU_HOVER_TEXT_COLOR.cpy()
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
        containerPanel = containerPanel,
        controlPanel = contentButtonDrawable ?: baseTextFieldStyle.background,
        slider = slider,
        progress = progress,
    )
}

private fun createConcharsPanelDrawable(texture: Texture, firstGlyph: Int): NinePatchDrawable =
    NinePatchDrawable(NinePatch(*Array(9) { concharsGlyph(texture, firstGlyph + it) }))

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
    return ConcharsFontLoader.glyphRegion(texture, glyphIndex)
}
