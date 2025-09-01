package jake2.game.func

import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.components.AreaPortal
import jake2.game.components.addComponent
import jake2.game.components.getComponent

/**
 * QUAKED func_areaportal (0 0 0) ?
 *
 * This is a non-visible object that divides the world into areas that are separated when this portal is not activated.
 * Usually enclosed in the middle of a door.
 */
fun funcAreaPortal(self: GameEntity, game: GameExportsImpl) {
    self.use = areaportalUse
    self.addComponent(AreaPortal(self.style, false)) // always start closed;
}

private val areaportalUse = registerUse("use_areaportal") { self, _, _, game ->
    val portal: AreaPortal = self.getComponent() ?: return@registerUse
    portal.open = !portal.open
    game.gameImports.SetAreaPortalState(portal.portalNumber, portal.open)
}
