package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextArea
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import ktx.scene2d.*

class ConsoleStage(viewport: Viewport) : Stage(viewport) {

    val textArea: TextArea

    init {
        actors {
            table {
                defaults().growX()
                setFillParent(true)
                container {
                    it.growY()
                    textArea = textArea("console text")
                    fill()
                }
                row()
                container {
                    val consoleInput = textField()
                    consoleInput.addListener(object : InputListener() {
                        override fun keyUp(event: InputEvent, keycode: Int): Boolean {
                            if (keycode == Input.Keys.ENTER) {
                                println("Executing console input: ${consoleInput.text}")
                                textArea.appendText("${consoleInput.text}\n")
                                Cbuf.AddText(consoleInput.text)
                                Cbuf.Execute()
                                consoleInput.text = ""
                                return true
                            }
                            return false
                        }
                    })
                    fill()
                }
            }
        }

        Cmd.AddCommand("console_print") { args: List<String?> ->
            textArea.appendText(args.first())
        }
    }
}