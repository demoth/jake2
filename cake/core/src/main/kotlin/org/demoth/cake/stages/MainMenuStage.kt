package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.Com
import jake2.qcommon.exec.Cbuf
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import ktx.scene2d.actors
import ktx.scene2d.label
import ktx.scene2d.table
import ktx.scene2d.textButton

/**
 * TEMPORARY Main menu stage for pre-game navigation commands.
 *
 * Purpose:
 * - Exposes basic startup actions (`connect`, `quit`) and placeholders.
 * - In future will allow selecting concrete game configurations to run (q1/q2 etc)
 *
 * Ownership/Lifecycle:
 * - Created by [org.demoth.cake.Cake] once at startup.
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
    private val activeProfileIdProvider: () -> String,
    private val availableProfileIdsProvider: () -> List<String>,
    private val isDisconnectedProvider: () -> Boolean,
    private val canDisconnectProvider: () -> Boolean,
    private val onDisconnectRequested: () -> Unit,
    private val onProfileSelected: (String) -> Unit,
) : Stage(viewport) {
    private lateinit var currentProfileLabel: Label
    private lateinit var profileSwitchHintLabel: Label
    private lateinit var profileListTable: Table
    private lateinit var switchProfileButton: TextButton
    private lateinit var disconnectButton: TextButton
    private var renderedProfileId: String = ""
    private var lastDisconnectedState: Boolean = true

    init {
        actors {
            table {
                defaults().pad(16f).uniformX().fillX()
                setFillParent(true)

                currentProfileLabel = label("")
                row()
                switchProfileButton = textButton("Switch Profile") {
                    onClick {
                        toggleProfileList()
                    }
                }
                row()
                profileSwitchHintLabel = label("")
                row()
                profileListTable = table {
                    defaults().pad(6f).fillX()
                    isVisible = false
                }
                row()
                disconnectButton = textButton("Disconnect") {
                    onClick {
                        onDisconnectRequested()
                    }
                }
                row()
                textButton("Singleplayer (future)").apply {
                    isDisabled = true
                }
                row()
                textButton("Multiplayer") {
                    onClick {
                        Cbuf.AddText("connect 127.0.0.1")
                    }
                }
                row()
                textButton("Host Game (future)").apply {
                    isDisabled = true
                }
                row()
                textButton("Options") {
                    onClick {
                        Com.Println("console_print Options menu is not implemented yet.")
                    }
                }
                row()
                textButton("Exit") {
                    onClick {
                        Cbuf.AddText("quit")
                    }
                }
            }
            label("version: 1.2.0")
        }

        refreshProfileHeader()
        refreshConnectionDependentUi()
        rebuildProfileList()
    }

    override fun act(delta: Float) {
        super.act(delta)
        val active = activeProfileIdProvider().trim()
        val disconnected = isDisconnectedProvider()
        if (active != renderedProfileId) {
            refreshProfileHeader()
            if (profileListTable.isVisible) {
                rebuildProfileList()
            }
        }
        if (disconnected != lastDisconnectedState) {
            refreshConnectionDependentUi()
        }
    }

    private fun refreshProfileHeader() {
        renderedProfileId = activeProfileIdProvider().trim()
        val shown = renderedProfileId.ifBlank { "<unset>" }
        currentProfileLabel.setText("Current profile: $shown")
    }

    private fun refreshConnectionDependentUi() {
        lastDisconnectedState = isDisconnectedProvider()
        switchProfileButton.isDisabled = !lastDisconnectedState
        disconnectButton.isDisabled = !canDisconnectProvider()
        profileSwitchHintLabel.setText(
            if (lastDisconnectedState) "" else "Disconnect first to switch profile",
        )
        if (!lastDisconnectedState) {
            profileListTable.isVisible = false
        }
    }

    private fun toggleProfileList() {
        if (!isDisconnectedProvider()) {
            return
        }
        if (!profileListTable.isVisible) {
            rebuildProfileList()
        }
        profileListTable.isVisible = !profileListTable.isVisible
    }

    private fun rebuildProfileList() {
        profileListTable.clearChildren()
        val profileIds = availableProfileIdsProvider()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        if (profileIds.isEmpty()) {
            profileListTable.add(Label("No profiles available", Scene2DSkin.defaultSkin))
            return
        }

        val activeId = activeProfileIdProvider().trim()
        for (id in profileIds) {
            val text = if (id == activeId) "$id (active)" else id
            val profileButton = TextButton(text, Scene2DSkin.defaultSkin)
            profileButton.onClick {
                if (!isDisconnectedProvider()) {
                    return@onClick
                }
                onProfileSelected(id)
                refreshProfileHeader()
                profileListTable.isVisible = false
            }
            profileListTable.add(profileButton).fillX()
            profileListTable.row()
        }
    }
}
