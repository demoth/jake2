package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.OptionsHubState
import org.demoth.cake.ui.menu.OptionsSectionSummary

class OptionsMenuStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    private val style: GameUiStyle,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = MenuIntent.OpenMainMenu,
) {
    private var renderedSections: List<OptionsSectionSummary> = emptyList()

    init {
        menuEventBus.postIntent(MenuIntent.RequestStateSync)
        rebuild(menuEventBus.latestState().optionsHub, force = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        rebuild(menuEventBus.latestState().optionsHub, force = false)
    }

    private fun rebuild(state: OptionsHubState, force: Boolean) {
        if (!force && state.sections == renderedSections) {
            return
        }
        clear()
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val container = Table().apply {
            defaults().pad(16f).uniformX().fillX()
            setFillParent(true)

            add(Label("Options", labelStyle))
            row()

            state.sections.forEach { section ->
                val button = TextButton("${section.title} (${section.optionCount})", buttonStyle).apply {
                    onClick {
                        menuEventBus.postIntent(MenuIntent.OpenOptionsSection(section.prefix))
                    }
                }
                button.isDisabled = section.optionCount == 0
                add(button)
                row()
            }

            add(createBackButton(style = buttonStyle))
        }
        addActor(container)
        renderedSections = state.sections
    }
}
