package jake2.game.func

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse

/**
 * QUAKED func_areaportal (0 0 0) ?
 *
 * This is a non-visible object that divides the world into areas that are
 * separated when this portal is not activated. Usually enclosed in the
 * middle of a door.
 */
val areaportal = registerThink("func_areaportal") { self, game ->
    self.use = areaportalUse
    self.count = 0 // always start closed;
    true
}

private val areaportalUse = registerUse("use_areaportal") { self, other, activator, game ->
    self.count = self.count xor 1 // toggle state
    game.gameImports.SetAreaPortalState(self.style, self.count != 0)
}
