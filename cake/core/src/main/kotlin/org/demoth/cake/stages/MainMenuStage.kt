package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.exec.Cbuf
import ktx.actors.onClick
import org.demoth.cake.BuildVersion
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

/**
 * TEMPORARY Main menu stage for pre-game navigation commands.
 *
 * Purpose:
 * - Exposes basic startup actions (`connect`, `quit`) and placeholders.
 * - In future will allow selecting concrete game configurations to run (q1/q2 etc)
 *
 * Ownership/Lifecycle:
 * - Created by [org.demoth.cake.Cake] and rebuilt when the shared content style changes.
 * - Drawn/acted while menu visibility is enabled by `Cake` input routing.
 * - Disposed by `Cake.dispose()`.
 *
 * Invariants/Constraints:
 * - Menu buttons are laid out in a single table column with uniform width
 *   (`uniformX().fillX()`).
 * - Button actions enqueue console commands through `Cbuf`.
 */
class MainMenuStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = null,
) {
    private var currentProfileLabel: Label
    private var switchProfileButton: TextButton
    private var disconnectButton: TextButton
    private var renderedProfileId: String = ""
    private var lastCanDisconnectState: Boolean = false

    init {
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val container = createHubMenuTable().apply {
            defaults().pad(8f).uniformX().fillX().minWidth(260f)

            currentProfileLabel = Label("", labelStyle)
            add(currentProfileLabel).padBottom(12f)
            row()

            switchProfileButton = createMenuButton("Switch profile", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenProfileEditor)
            }
            add(switchProfileButton)
            row()

            disconnectButton = createMenuButton("Disconnect", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.DisconnectRequested)
            }
            add(disconnectButton)
            row()

            add(createMenuButton("Singleplayer (future)", buttonStyle).apply {
                isDisabled = true
            })
            row()

            add(createMenuButton("Multiplayer", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenMultiplayerMenu)
            })
            row()

            add(createMenuButton("Options", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenOptions)
            })
            row()

            add(createMenuButton("Exit", buttonStyle) {
                    Cbuf.AddText("quit")
            })
        }
        addActor(container)
        addActor(Label("version: ${BuildVersion.displayVersion}", labelStyle))

        menuEventBus.postIntent(MenuIntent.RequestStateSync)
        refreshProfileHeader("<unset>")
        refreshConnectionDependentUi(force = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        val state = menuEventBus.latestState().mainMenu
        val active = state.activeProfileId.trim()
        if (active != renderedProfileId) {
            refreshProfileHeader(active)
        }
        val canDisconnect = state.canDisconnect
        if (canDisconnect != lastCanDisconnectState) {
            refreshConnectionDependentUi(force = false, canDisconnect = canDisconnect)
        }
    }

    private fun refreshProfileHeader(activeProfileId: String) {
        renderedProfileId = activeProfileId
        val shown = renderedProfileId.ifBlank { "<unset>" }
        currentProfileLabel.setText("Profile: $shown")
    }

    private fun refreshConnectionDependentUi(
        force: Boolean,
        canDisconnect: Boolean = menuEventBus.latestState().mainMenu.canDisconnect,
    ) {
        if (!force && canDisconnect == lastCanDisconnectState) return
        lastCanDisconnectState = canDisconnect
        disconnectButton.isDisabled = !canDisconnect
        disconnectButton.touchable = if (canDisconnect) Touchable.enabled else Touchable.disabled
        // Profile switching is allowed only while disconnected.
        switchProfileButton.isDisabled = canDisconnect
        switchProfileButton.touchable = if (canDisconnect) Touchable.disabled else Touchable.enabled
    }
}
