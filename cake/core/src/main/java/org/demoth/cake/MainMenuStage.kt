package org.demoth.cake

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.scene2d.actors
import ktx.scene2d.label

/**
 * Prototype for other stages
 */
class MainMenuStage(viewport: Viewport) : Stage(viewport) {
    init {
        actors {
            label("version: 0.0.1")
        }
    }
}
