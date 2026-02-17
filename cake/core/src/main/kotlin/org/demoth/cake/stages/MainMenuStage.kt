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
class MainMenuStage(viewport: Viewport) : Stage(viewport) {
    init {
        actors {
            table {
                defaults().pad(16f).uniformX().fillX()
                setFillParent(true)
                textButton("Single player")
                row()
                textButton("Multiplayer"){
                    onClick {
                        Cbuf.AddText("connect 127.0.0.1")
                    }
                }
                row()
                textButton("Settings")
                row()
                textButton("Exit") {
                    onClick {
                        Cbuf.AddText("quit")
                    }
                }
            }
            label("version: 1.2.0")
        }
    }
}
