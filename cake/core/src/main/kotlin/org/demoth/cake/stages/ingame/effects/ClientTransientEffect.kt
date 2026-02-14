package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.utils.Disposable

/**
 * Runtime effect instance owned by [ClientEffectsSystem].
 */
interface ClientTransientEffect : Disposable {
    /**
     * Advances effect state.
     *
     * @return true if effect should remain active, false if it has expired.
     */
    fun update(nowMs: Int, deltaSeconds: Float): Boolean

    /**
     * Emits renderables for the current frame.
     */
    fun render(modelBatch: ModelBatch)

    override fun dispose() {
        // no-op by default
    }
}
