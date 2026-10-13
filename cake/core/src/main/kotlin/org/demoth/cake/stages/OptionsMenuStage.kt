package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.viewport.Viewport
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.OptionsHubState
import org.demoth.cake.ui.menu.OptionsSection

class OptionsMenuStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    private val style: GameUiStyle,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = MenuIntent.OpenMainMenu,
) {
    private var renderedSections: List<OptionsSection> = emptyList()

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
        val container = createHubMenuTable().apply {
            defaults().pad(8f).uniformX().fillX().minWidth(260f)

            add(Label("Options", labelStyle)).padBottom(12f)
            row()

            state.sections.forEach { section ->
                val button = createMenuButton("${section.title} (${section.entries.size})", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenOptionsSection(section.prefix))
                }
                button.isDisabled = section.entries.isEmpty()
                add(button)
                row()
            }

            add(createBackButton(style = buttonStyle))
        }
        addActor(container)
        renderedSections = state.sections
    }
}
