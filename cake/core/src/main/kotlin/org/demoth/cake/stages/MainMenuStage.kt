package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.exec.Cbuf
import ktx.actors.onClick
import ktx.scene2d.actors
import ktx.scene2d.label
import ktx.scene2d.table
import ktx.scene2d.textButton

/**
 * Prototype for other stages
 */
class MainMenuStage(viewport: Viewport) : Stage(viewport) {
    init {
        actors {
            table {
                defaults().pad(8f)
                setFillParent(true)
                textButton("Single player").pad(32f)
                row()
                textButton("Multiplayer"){
                    onClick {
                        Cbuf.AddText("connect 127.0.0.1")
                    }
                }.pad(32f)
                row()
                textButton("Settings").pad(32f)
                row()
                textButton("Exit") {
                    onClick {
                        Cbuf.AddText("quit")
                    }
                }.pad(32f)
            }
            label("version: 1.2.0")
        }
    }
}
