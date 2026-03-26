package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
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
        val container = Table().apply {
            defaults().pad(16f).uniformX().fillX()
            setFillParent(true)

            add(Label("Multiplayer", labelStyle))
            row()
            add(TextButton("Host Game (future)", buttonStyle).apply {
                isDisabled = true
            })
            row()
            add(TextButton("Join Game", buttonStyle).apply {
                onClick {
                    menuEventBus.postIntent(MenuIntent.OpenJoinGame)
                }
            })
            row()
            add(TextButton("Player Setup", buttonStyle).apply {
                onClick {
                    menuEventBus.postIntent(MenuIntent.OpenPlayerSetup)
                }
            })
            row()
            add(createBackButton(style = buttonStyle))
        }
        addActor(container)
    }
}
