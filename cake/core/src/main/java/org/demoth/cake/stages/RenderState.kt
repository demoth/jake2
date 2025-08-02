package org.demoth.cake.stages

import com.badlogic.gdx.graphics.g3d.Model
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
)