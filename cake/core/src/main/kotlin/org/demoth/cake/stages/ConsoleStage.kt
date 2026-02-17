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

class ConsoleStage(viewport: Viewport) : Stage(viewport) {

    fun focus() = consoleInput.setKeyboardFocus(true)

    val consoleOutput: TextArea
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
