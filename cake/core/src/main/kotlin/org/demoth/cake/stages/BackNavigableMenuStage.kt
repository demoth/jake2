package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.MenuSignal
import org.demoth.cake.ui.menu.MenuUiSoundEffect

private const val HUB_MENU_ROW_PADDING = 8f
private const val HUB_MENU_BUTTON_MIN_WIDTH = 260f
private const val HUB_MENU_BUTTON_VERTICAL_PADDING = 16f

// Common class for menus which have a parent menu (for a "back" button and ESC behavior)
abstract class BackNavigableMenuStage(
    viewport: Viewport,
    protected val menuEventBus: MenuEventBus,
    private val parentIntent: MenuIntent?,
) : Stage(viewport) {
    private val menuInteractionResets = mutableListOf<() -> Unit>()

    protected fun createHubMenuTable(): Table {
        return Table().apply {
            defaults().pad(HUB_MENU_ROW_PADDING).uniformX().fillX().minWidth(HUB_MENU_BUTTON_MIN_WIDTH)
            setFillParent(true)
        }
    }

    protected fun createMenuButton(
        label: String,
        style: TextButton.TextButtonStyle = Scene2DSkin.defaultSkin.get(TextButton.TextButtonStyle::class.java),
        onClickAction: (() -> Unit)? = null,
    ): TextButton {
        val buttonStyle = TextButton.TextButtonStyle(style)
        val baseFontColor = buttonStyle.fontColor?.cpy() ?: Color.WHITE.cpy()
        val hoverFontColor = buttonStyle.overFontColor?.cpy() ?: baseFontColor.cpy()
        fun resetHoverState() {
            buttonStyle.fontColor = baseFontColor.cpy()
        }

        return TextButton(label, buttonStyle).apply {
            var hovered = false
            pad(
                HUB_MENU_BUTTON_VERTICAL_PADDING,
                0f,
                HUB_MENU_BUTTON_VERTICAL_PADDING,
                0f,
            )
            resetHoverState()
            menuInteractionResets += {
                hovered = false
                resetHoverState()
            }
            addListener(object : InputListener() {
                override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    if (!hovered && !isDisabled) {
                        hovered = true
                        buttonStyle.fontColor = hoverFontColor.cpy()
                        menuEventBus.postSignal(MenuSignal.PlayUiSound(MenuUiSoundEffect.HOVER_BUTTON))
                    }
                }

                override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    hovered = false
                    resetHoverState()
                }
            })
            onClickAction?.let { action ->
                onClick {
                    hovered = false
                    resetHoverState()
                    action()
                }
            }
        }
    }

    fun resetMenuInteractionState() {
        menuInteractionResets.forEach { it() }
    }

    protected fun navigateBack(): Boolean {
        val target = parentIntent ?: return false
        menuEventBus.postIntent(target)
        return true
    }

    protected fun createBackButton(label: String = "Back"): TextButton {
        return createBackButton(
            label = label,
            style = Scene2DSkin.defaultSkin.get(TextButton.TextButtonStyle::class.java),
        )
    }

    protected fun createBackButton(
        label: String = "Back",
        style: TextButton.TextButtonStyle,
    ): TextButton {
        return createMenuButton(label = label, style = style, onClickAction = ::navigateBack)
    }

    override fun keyUp(keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ESCAPE -> navigateBack()
            else -> super.keyUp(keycode)
        }
    }
}
