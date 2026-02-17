package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextArea
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import ktx.actors.setKeyboardFocus
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

    /** Requests keyboard focus for the input field. */
    fun focus() = consoleInput.setKeyboardFocus(true)

    /** Output text area used by command echo and `console_print`. */
    val consoleOutput: TextArea

    /** Input field where commands are typed and submitted with Enter. */
    val consoleInput: TextField

    init {
        actors {
            table {
                defaults().pad(8f).growX()
                setFillParent(true)
                container {
                    it.growY()
                    stack {
                        consoleOutput = textArea()
                        add(consoleOutput)
                        image("console-panel")
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
                                    consoleOutput.appendText("${consoleInput.text}\n")
                                    try {
                                        Cbuf.AddText(consoleInput.text)
                                        Cbuf.Execute()
                                    } catch (e: Exception) {
                                        consoleOutput.appendText("" + e.message)
                                    }
                                    consoleInput.text = ""
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
            consoleOutput.text = ""
        }

        Cmd.AddCommand("console_print") { args: List<String?> ->
            consoleOutput.appendText("${args.first()}")
        }
    }
}
