package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.viewport.Viewport
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

class MultiplayerMenuStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = MenuIntent.OpenMainMenu,
) {
    init {
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val container = createHubMenuTable().apply {
            defaults().pad(8f).uniformX().fillX().minWidth(260f)

            add(Label("Multiplayer", labelStyle)).padBottom(12f)
            row()
            add(createMenuButton("Host Game (future)", buttonStyle).apply {
                isDisabled = true
            })
            row()
            add(createMenuButton("Join Game", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenJoinGame)
            })
            row()
            add(createMenuButton("Player Setup", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenPlayerSetup)
            })
            row()
            add(createBackButton(style = buttonStyle))
        }
        addActor(container)
    }
}
