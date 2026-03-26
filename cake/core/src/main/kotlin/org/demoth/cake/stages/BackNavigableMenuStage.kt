package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

private const val HUB_MENU_ROW_PADDING = 8f
private const val HUB_MENU_BUTTON_MIN_WIDTH = 260f
private const val HUB_MENU_BUTTON_VERTICAL_PADDING = 16f

// Common class for menus which have a parent menu (for a "back" button and ESC behavior)
abstract class BackNavigableMenuStage(
    viewport: Viewport,
    protected val menuEventBus: MenuEventBus,
    private val parentIntent: MenuIntent?,
) : Stage(viewport) {
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
        return TextButton(label, style).apply {
            pad(
                HUB_MENU_BUTTON_VERTICAL_PADDING,
                0f,
                HUB_MENU_BUTTON_VERTICAL_PADDING,
                0f,
            )
            onClickAction?.let { action ->
                onClick { action() }
            }
        }
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
