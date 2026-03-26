package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.JoinGameFormState
import org.demoth.cake.ui.menu.JoinGameState
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

class JoinGameStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = MenuIntent.OpenMultiplayerMenu,
) {
    private var hostField: TextField
    private var portField: TextField
    private var statusLabel: Label
    private var renderedForm: JoinGameFormState = JoinGameFormState()
    private var renderedStatusMessage: String = ""

    init {
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val root = Table().apply {
            setFillParent(true)
            align(com.badlogic.gdx.utils.Align.topLeft)
            pad(12f)
            defaults().top().left().pad(12f)

            add(Label("Join Game", labelStyle)).left().row()

            add(Label("Host name", labelStyle)).left().row()
            hostField = TextField("", Scene2DSkin.defaultSkin)
            add(hostField).minWidth(320f).prefWidth(640f).growX().fillX().row()

            add(Label("Port", labelStyle)).left().row()
            portField = TextField("", Scene2DSkin.defaultSkin)
            add(portField).width(180f).left().row()

            val buttonsRow = Table(Scene2DSkin.defaultSkin).apply {
                defaults().padRight(12f).left()
                add(
                    createMenuButton("Join", buttonStyle) {
                        menuEventBus.postIntent(
                            MenuIntent.JoinGameRequested(
                                JoinGameFormState(
                                    host = hostField.text,
                                    port = portField.text,
                                ),
                            ),
                        )
                    },
                )
                add(createBackButton(style = buttonStyle))
            }
            add(buttonsRow).left().row()

            statusLabel = Label("", labelStyle).apply {
                setWrap(true)
            }
            add(statusLabel).growX().fillX().row()
        }
        addActor(root)

        menuEventBus.postIntent(MenuIntent.RequestStateSync)
        applyState(menuEventBus.latestState().joinGame, force = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        applyState(menuEventBus.latestState().joinGame, force = false)
    }

    private fun applyState(
        state: JoinGameState,
        force: Boolean,
    ) {
        if (force || state.form != renderedForm) {
            hostField.text = state.form.host
            portField.text = state.form.port
        }
        if (force || state.statusMessage != renderedStatusMessage) {
            statusLabel.setText(state.statusMessage)
        }
        renderedForm = state.form
        renderedStatusMessage = state.statusMessage
    }
}
