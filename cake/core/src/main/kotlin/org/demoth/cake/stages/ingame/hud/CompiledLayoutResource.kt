package org.demoth.cake.stages.ingame.hud

import com.badlogic.gdx.utils.Disposable

/**
 * Wrapper that allows storing a compiled layout program in config entries.
 */
class CompiledLayoutResource(
    val program: LayoutProgram,
) : Disposable {
    override fun dispose() {
        // No native resources.
    }
}
