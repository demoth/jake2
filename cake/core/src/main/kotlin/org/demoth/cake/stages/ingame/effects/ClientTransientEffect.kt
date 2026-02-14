package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.utils.Disposable

/**
 * Runtime effect instance owned by [ClientEffectsSystem].
 *
 * Timing assumption:
 * [update] and [render] are called from the render thread in order once per frame.
 *
 * Contract:
 * - Return `false` from [update] to request removal and disposal in the same frame loop.
 * - Implementations should not mutate global game state.
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
