package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.Com
import jake2.qcommon.exec.Cbuf
import ktx.actors.onClick
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
    private val canDisconnectProvider: () -> Boolean,
    private val onDisconnectRequested: () -> Unit,
    private val onOpenProfileEditor: () -> Unit,
) : Stage(viewport) {
    private lateinit var currentProfileButton: TextButton
    private lateinit var disconnectButton: TextButton
    private var renderedProfileId: String = ""
    private var lastCanDisconnectState: Boolean = false

    init {
        actors {
            table {
                defaults().pad(16f).uniformX().fillX()
                setFillParent(true)

                currentProfileButton = textButton("") {
                    onClick {
                        onOpenProfileEditor()
                    }
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
        refreshConnectionDependentUi(force = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        val active = activeProfileIdProvider().trim()
        if (active != renderedProfileId) {
            refreshProfileHeader()
        }
        val canDisconnect = canDisconnectProvider()
        if (canDisconnect != lastCanDisconnectState) {
            refreshConnectionDependentUi(force = false)
        }
    }

    private fun refreshProfileHeader() {
        renderedProfileId = activeProfileIdProvider().trim()
        val shown = renderedProfileId.ifBlank { "<unset>" }
        currentProfileButton.setText("Current profile: $shown")
    }

    private fun refreshConnectionDependentUi(force: Boolean) {
        val canDisconnect = canDisconnectProvider()
        if (!force && canDisconnect == lastCanDisconnectState) return
        lastCanDisconnectState = canDisconnect
        disconnectButton.isDisabled = !canDisconnect
    }
}
