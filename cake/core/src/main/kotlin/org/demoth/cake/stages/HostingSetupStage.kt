package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.*

/** Shared layout for singleplayer difficulty and multiplayer host settings. */
class HostingSetupStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
    private val singleplayer: Boolean,
) : BackNavigableMenuStage(viewport, menuEventBus,
    if (singleplayer) MenuIntent.OpenMainMenu else MenuIntent.OpenMultiplayerMenu) {
    private val initial = (if (singleplayer) menuEventBus.latestState().singleplayer else menuEventBus.latestState().hostGame).form
    private val map = TextField(initial.map, style.menuWidgets.textField)
    private val hostname = TextField(initial.hostname, style.menuWidgets.textField)
    private val clients = TextField(initial.maxClients, style.menuWidgets.textField)
    private val mode = SelectBox<String>(style.menuWidgets.selectBox).apply {
        setItems("Deathmatch", "Coop")
        selectedIndex = if (initial.coop) 1 else 0
    }
    private val difficulty = SelectBox<String>(style.menuWidgets.selectBox).apply {
        setItems("Easy", "Medium", "Hard", "Nightmare")
        selectedIndex = initial.skill
    }
    private val status = Label("", style.menuWidgets.label).apply { setWrap(true) }
    private val start = createMenuButton(if (singleplayer) "New Game" else "Start Server", style.menuWidgets.button) {
        menuEventBus.postIntent(MenuIntent.StartHosting(singleplayer, form()))
    }
    private var previousForm = initial
    private var previousCoop = initial.coop

    init {
        val root = Table().apply {
            defaults().left().pad(8f)
            add(Label(if (singleplayer) "Singleplayer" else "Host Game", style.menuWidgets.label)).colspan(2).row()
            if (!singleplayer) {
                add(Label("Map", style.menuWidgets.label)); add(map).width(360f).row()
                add(Label("Mode", style.menuWidgets.label)); add(mode).growX().row()
                add(Label("Players (including you)", style.menuWidgets.label)); add(clients).growX().row()
                add(Label("Server name", style.menuWidgets.label)); add(hostname).growX().row()
            }
            add(Label(if (singleplayer) "Difficulty" else "Coop difficulty", style.menuWidgets.label))
            add(difficulty).width(360f).row()
            add(start); add(createBackButton(style = style.menuWidgets.button)).row()
            add(status).colspan(2).width(560f).row()
        }
        addCenteredForm(root, style.menuWidgets.scrollPane)
        menuEventBus.postIntent(MenuIntent.RequestStateSync)
    }

    private fun form() = HostGameFormState(map.text, mode.selectedIndex == 1, clients.text, hostname.text, difficulty.selectedIndex)

    override fun act(delta: Float) {
        super.act(delta)
        val coop = mode.selectedIndex == 1
        if (coop != previousCoop) {
            clients.text = if (coop) "4" else "8"
            previousCoop = coop
        }
        difficulty.isDisabled = !singleplayer && !coop
        val form = form()
        if (form != previousForm) {
            menuEventBus.postIntent(MenuIntent.UpdateHostingDraft(singleplayer, form))
            previousForm = form
        }
        val snapshot = menuEventBus.latestState()
        start.isDisabled = if (singleplayer) !snapshot.canStartSingleplayer else !snapshot.canHostGame
        val setup = if (singleplayer) snapshot.singleplayer else snapshot.hostGame
        status.setText(setup.statusMessage)
    }
}
