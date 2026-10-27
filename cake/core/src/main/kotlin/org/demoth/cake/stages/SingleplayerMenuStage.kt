package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.viewport.Viewport
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

/** Navigation stays accessible while playing; eligibility applies to individual actions. */
class SingleplayerMenuStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
) : BackNavigableMenuStage(viewport, menuEventBus, MenuIntent.OpenMainMenu) {
    private val newGame = createMenuButton("New Game", style.menuWidgets.button) {
        menuEventBus.postIntent(MenuIntent.OpenHostingSetup(true))
    }.apply { name = "new-game-menu" }
    private val load = createMenuButton("Load Game", style.menuWidgets.button) {
        menuEventBus.postIntent(MenuIntent.OpenLoadGame)
    }.apply { name = "load-game-menu" }
    private val save = createMenuButton("Save Game", style.menuWidgets.button) {
        menuEventBus.postIntent(MenuIntent.OpenSaveGame)
    }.apply { name = "save-game-menu" }

    init {
        addActor(createHubMenuTable().apply {
            add(Label("Singleplayer", style.menuWidgets.label)).row()
            add(newGame).row()
            add(load).row()
            add(save).row()
            add(createBackButton(style = style.menuWidgets.button)).row()
        })
    }

    override fun act(delta: Float) {
        super.act(delta)
        val state = menuEventBus.latestState()
        newGame.isDisabled = !state.canHostGame
        load.isDisabled = state.saveAccess.loadRejectionReason != null
        save.isDisabled = state.saveAccess.saveRejectionReason != null
    }
}
