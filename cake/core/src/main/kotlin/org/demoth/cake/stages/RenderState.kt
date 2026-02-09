package org.demoth.cake.stages

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.exec.Cmd
import org.demoth.cake.ClientEntity

data class RenderState(
    var drawEntities: Boolean = true,
    var drawLevel: Boolean = true,
    var drawSkybox: Boolean = true,
    var lerpAcc: Float = 0f,
    var playerNumber: Int = 1,
    // client side models
    var gun: ClientEntity? = null,
    var levelModel: ClientEntity? = null,
    var playerModel: Model? = null
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
