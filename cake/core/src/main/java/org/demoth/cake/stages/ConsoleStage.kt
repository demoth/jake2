package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextArea
import com.badlogic.gdx.utils.viewport.Viewport
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
                    textField("console input")
                    fill()
                }
            }
        }
    }
}