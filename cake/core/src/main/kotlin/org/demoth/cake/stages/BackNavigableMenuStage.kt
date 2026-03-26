package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

// Common class for menus which have a parent menu (for a "back" button and ESC behavior)
abstract class BackNavigableMenuStage(
    viewport: Viewport,
    protected val menuEventBus: MenuEventBus,
    private val parentIntent: MenuIntent?,
) : Stage(viewport) {
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
        return TextButton(label, style).apply {
            onClick { navigateBack() }
        }
    }

    override fun keyUp(keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ESCAPE -> navigateBack()
            else -> super.keyUp(keycode)
        }
    }
}
