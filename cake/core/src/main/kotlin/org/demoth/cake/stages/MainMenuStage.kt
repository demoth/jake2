package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.exec.Cbuf
import org.demoth.cake.BuildVersion
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

/**
 * Main menu stage for profile selection and game navigation.
 *
 * Purpose:
 * - Opens profile editing, multiplayer, and options screens and offers disconnect/exit actions.
 * - Shows Singleplayer when hosting support is available.
 *
 * Ownership/Lifecycle:
 * - Created by [MenuStages] and rebuilt when the shared content style changes.
 * - Drawn/acted while menu visibility is enabled by `Cake` input routing.
 * - Disposed by `MenuStages.dispose()`.
 *
 * Invariants/Constraints:
 * - Menu buttons are laid out in a single table column with uniform width
 *   (`uniformX().fillX()`).
 * - Navigation and disconnect actions post [MenuIntent] messages; Exit queues `quit` through [Cbuf].
 */
class MainMenuStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
    showHosting: Boolean,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = null,
) {
    private var currentProfileLabel: Label
    private var switchProfileButton: TextButton
    private var singleplayerButton: TextButton? = null
    private var loadButton: TextButton? = null
    private var saveButton: TextButton? = null
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

            if (showHosting) {
                singleplayerButton = createMenuButton("Singleplayer", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenHostingSetup(true))
                }
                add(singleplayerButton)
                row()
                loadButton = createMenuButton("Load Game", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenLoadGame)
                }.apply { labelCell.minWidth(0f) }
                saveButton = createMenuButton("Save Game", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenSaveGame)
                }.apply { labelCell.minWidth(0f) }
                add(Table().apply {
                    add(loadButton).growX().padRight(4f)
                    add(saveButton).growX().padLeft(4f)
                })
                row()
            }

            add(createMenuButton("Multiplayer", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenMultiplayerMenu)
            })
            row()

            add(createMenuButton("Options", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenOptions)
            })
            row()

            add(createMenuButton("Content browser", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.OpenContentBrowser)
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
        val snapshot = menuEventBus.latestState()
        singleplayerButton?.isDisabled = !snapshot.canHostGame
        loadButton?.isDisabled = snapshot.saveAccess.loadRejectionReason != null
        saveButton?.isDisabled = snapshot.saveAccess.saveRejectionReason != null
        val state = snapshot.mainMenu
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
