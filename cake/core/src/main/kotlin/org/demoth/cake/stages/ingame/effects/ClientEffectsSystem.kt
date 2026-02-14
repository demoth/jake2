package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.network.messages.server.MuzzleFlash2Message
import jake2.qcommon.network.messages.server.TEMessage
import org.demoth.cake.GameConfiguration
import org.demoth.cake.stages.ingame.ClientEntityManager

/**
 * Owns client-side transient effects produced by server effect messages.
 *
 * Scope:
 * - TEMessage hierarchy
 * - MuzzleFlash2Message
 *
 * Non-goals:
 * - replicated world/entity state (owned by [ClientEntityManager])
 */
class ClientEffectsSystem(
    private val assetManager: AssetManager,
    private val entityManager: ClientEntityManager,
    private val gameConfig: GameConfiguration
) : Disposable {
    @Suppress("UNUSED_PARAMETER")
    fun processMuzzleFlash2Message(_msg: MuzzleFlash2Message) {
        // implemented in follow-up commits
    }

    @Suppress("UNUSED_PARAMETER")
    fun processTempEntityMessage(_msg: TEMessage) {
        // implemented in follow-up commits
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(_deltaSeconds: Float, _serverFrame: Int) {
        // implemented in follow-up commits
    }

    @Suppress("UNUSED_PARAMETER")
    fun render(_modelBatch: ModelBatch) {
        // implemented in follow-up commits
    }

    override fun dispose() {
        // implemented in follow-up commits
    }
}
