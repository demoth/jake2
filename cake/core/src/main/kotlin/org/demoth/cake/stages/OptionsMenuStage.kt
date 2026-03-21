package org.demoth.cake.stages

import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.actors
import ktx.scene2d.label
import ktx.scene2d.table
import ktx.scene2d.textButton
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.OptionsHubState
import org.demoth.cake.ui.menu.OptionsSectionSummary

class OptionsMenuStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
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
        actors {
            table {
                defaults().pad(16f).uniformX().fillX()
                setFillParent(true)

                label("Options")
                row()

                state.sections.forEach { section ->
                    val button = textButton("${section.title} (${section.optionCount})") {
                        onClick {
                            menuEventBus.postIntent(MenuIntent.OpenOptionsSection(section.prefix))
                        }
                    }
                    button.isDisabled = section.optionCount == 0
                    row()
                }

                add(createBackButton())
            }
        }
        renderedSections = state.sections
    }
}
