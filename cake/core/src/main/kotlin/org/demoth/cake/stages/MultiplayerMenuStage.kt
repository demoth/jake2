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
    private val hostButton: com.badlogic.gdx.scenes.scene2d.ui.TextButton
    private val hostingStatus: Label

    override fun act(delta: Float) {
        super.act(delta)
        val reason = menuEventBus.latestState().hostingUnavailableReason
        hostButton.isDisabled = reason != null
        hostingStatus.setText(reason.orEmpty())
    }

    init {
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val container = createHubMenuTable().apply {
            defaults().pad(8f).uniformX().fillX().minWidth(260f)

            add(Label("Multiplayer", labelStyle)).padBottom(12f)
            row()
            hostButton = createMenuButton("Host Game", buttonStyle) {
                menuEventBus.postIntent(MenuIntent.OpenHostingSetup(false))
            }
            add(hostButton)
            row()
            hostingStatus = Label("", labelStyle).apply { setWrap(true) }
            add(hostingStatus).width(300f)
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
