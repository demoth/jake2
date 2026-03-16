package org.demoth.cake.stages.console

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.Com
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import ktx.actors.setKeyboardFocus
import ktx.scene2d.Scene2DSkin
import ktx.scene2d.*

/**
 * Developer console overlay stage.
 *
 * Purpose:
 * - Presents scrollable output and a one-line command input.
 * - Executes entered commands via `Cbuf` and appends local feedback.
 *
 * Ownership/Lifecycle:
 * - Created by [org.demoth.cake.Cake] during app startup, lives for the whole duration of the app (not just game session)
 * - Drawn/acted only when console visibility is enabled by `Cake` input routing.
 * - Disposed by `Cake.dispose()`.
 *
 * Invariants/Constraints:
 * - [consoleOutput] and [consoleInput] are initialized in `init` before stage usage.
 * - Command execution is Enter-key driven and occurs on the render thread.
 * - Console chrome relies on skin drawable names from `assets/ui/uiskin.json`
 *   (`console-panel`).
 * - `Stack` child order is visual-order sensitive (last child is drawn on top).
 * TODO: add last command navigation with up/down. command completion with TAB
 */
class ConsoleStage(viewport: Viewport) : Stage(viewport) {
    private val consoleBuffer = ConsoleBuffer()
    private val consoleSink = Com.ConsoleSink { level, message -> appendOutput(level, message) }

    /** Requests keyboard focus for the input field. */
    fun focus() = consoleInput.setKeyboardFocus(true)

    /** Output widget used by command echo and console logging. */
    val consoleOutput: ConsoleOutputWidget

    /** Input field where commands are typed and submitted with Enter. */
    val consoleInput: TextField

    val entries: List<ConsoleEntry>
        get() = consoleBuffer.entries()

    init {
        Com.SetConsoleSink(consoleSink)

        actors {
            table {
                defaults().pad(8f).growX()
                setFillParent(true)
                container {
                    it.growY()
                    stack {
                        image("console-panel")
                        consoleOutput = ConsoleOutputWidget(consoleBuffer, Scene2DSkin.defaultSkin)
                        consoleOutput.addListener(object : InputListener() {
                            override fun scrolled(
                                event: InputEvent,
                                x: Float,
                                y: Float,
                                amountX: Float,
                                amountY: Float,
                            ): Boolean {
                                consoleOutput.scrollLines(amountY.toInt())
                                return true
                            }
                        })
                        add(consoleOutput)
                    }
                    fill()
                }
                row()
                container {
                    stack {
                        image("console-panel")
                        consoleInput = textField()
                        consoleInput.addListener(object : InputListener() {
                            override fun keyUp(event: InputEvent, keycode: Int): Boolean {
                                if (keycode == Input.Keys.ENTER) {
                                    appendOutput(Com.ConsoleLevel.INFO, "${consoleInput.text}\n")
                                    try {
                                        Cbuf.AddText(consoleInput.text)
                                        Cbuf.Execute()
                                    } catch (e: Exception) {
                                        appendOutput(Com.ConsoleLevel.ERROR, "" + e.message)
                                    }
                                    consoleInput.text = ""
                                    return true
                                }
                                if (keycode == Input.Keys.PAGE_UP) {
                                    consoleOutput.scrollPage(-1)
                                    return true
                                }
                                if (keycode == Input.Keys.PAGE_DOWN) {
                                    consoleOutput.scrollPage(1)
                                    return true
                                }
                                return false
                            }
                        })
                        add(consoleInput)
                    }
                    fill()
                }
            }
        }

        Cmd.AddCommand("clear") {
            consoleBuffer.clear()
        }

        Cmd.AddCommand("console_print") { args: List<String?> ->
            appendOutput(Com.ConsoleLevel.INFO, "${args.first()}")
        }
    }

    private fun appendOutput(level: Com.ConsoleLevel, text: String) {
        consoleBuffer.append(level, text)
        consoleOutput.invalidate()
    }

    override fun dispose() {
        Com.ClearConsoleSink(consoleSink)
        super.dispose()
    }
}
