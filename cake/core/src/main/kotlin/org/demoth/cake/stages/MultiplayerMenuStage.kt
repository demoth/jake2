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
    showHosting: Boolean,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = MenuIntent.OpenMainMenu,
) {
    private var hostButton: com.badlogic.gdx.scenes.scene2d.ui.TextButton? = null

    override fun act(delta: Float) {
        super.act(delta)
        hostButton?.isDisabled = !menuEventBus.latestState().canHostGame
    }

    init {
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val container = createHubMenuTable().apply {
            defaults().pad(8f).uniformX().fillX().minWidth(260f)

            add(Label("Multiplayer", labelStyle)).padBottom(12f)
            row()
            if (showHosting) {
                hostButton = createMenuButton("Host Game", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenHostingSetup(false))
                }
                add(hostButton)
                row()
            }
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
