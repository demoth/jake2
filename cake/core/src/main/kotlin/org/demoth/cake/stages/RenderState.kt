package org.demoth.cake.stages

import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.exec.Cmd

// todo: maybe remove this class: dissolve into ClientEntityManager?
data class RenderState(
    var drawEntities: Boolean = true,
    var drawLevel: Boolean = true,
    var drawSkybox: Boolean = true,
    var lerpAcc: Float = 0f,
    var playerNumber: Int = 1,
): Disposable {
    init {
        // force replace because the command lambdas capture the render state.
        // fixme: make proper disposable approach
        Cmd.AddCommand("toggle_skybox", true) {
            drawSkybox = !drawSkybox
        }
        Cmd.AddCommand("toggle_level", true) {
            drawLevel = !drawLevel
        }
        Cmd.AddCommand("toggle_entities", true) {
            drawEntities = !drawEntities
        }
    }

    override fun dispose() {
        Cmd.RemoveCommand("toggle_skybox")
        Cmd.RemoveCommand("toggle_level")
        Cmd.RemoveCommand("toggle_entities")
    }
}
