package jake2.game.func

import jake2.game.GameUtil
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines

/**
 * QUAKED func_killbox (1 0 0) ? Kills everything inside when fired,
 * irrespective of protection.
 */
val killbox = registerThink("func_killbox") { self, game ->
    game.gameImports.setmodel(self, self.model)
    self.use = killboxUse
    self.svflags = Defines.SVF_NOCLIENT
    true
}

private val killboxUse = registerUse("use_killbox") { self, _, _, game ->
    GameUtil.KillBox(self, game)
}
