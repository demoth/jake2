package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import ktx.scene2d.actors
import ktx.scene2d.table
import org.demoth.cake.ui.menu.JoinGameFormState
import org.demoth.cake.ui.menu.JoinGameState
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

class JoinGameStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
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
        actors {
            table {
                setFillParent(true)
                align(com.badlogic.gdx.utils.Align.topLeft)
                pad(12f)
                defaults().top().left().pad(12f)

                add(Label("Join Game", Scene2DSkin.defaultSkin)).left().row()

                add(Label("Host name", Scene2DSkin.defaultSkin)).left().row()
                hostField = TextField("", Scene2DSkin.defaultSkin)
                add(hostField).minWidth(320f).prefWidth(640f).growX().fillX().row()

                add(Label("Port", Scene2DSkin.defaultSkin)).left().row()
                portField = TextField("", Scene2DSkin.defaultSkin)
                add(portField).width(180f).left().row()

                val buttonsRow = com.badlogic.gdx.scenes.scene2d.ui.Table(Scene2DSkin.defaultSkin).apply {
                    defaults().padRight(12f).left()
                    val joinButton = TextButton("Join", Scene2DSkin.defaultSkin).apply {
                        onClick {
                            menuEventBus.postIntent(
                                MenuIntent.JoinGameRequested(
                                    JoinGameFormState(
                                        host = hostField.text,
                                        port = portField.text,
                                    ),
                                ),
                            )
                        }
                    }
                    add(joinButton)

                    add(createBackButton())
                }
                add(buttonsRow).left().row()

                statusLabel = Label("", Scene2DSkin.defaultSkin).apply {
                    setWrap(true)
                }
                add(statusLabel).growX().fillX().row()
            }
        }

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
