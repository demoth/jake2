package jake2.game.func

import jake2.game.GameExportsImpl
import jake2.game.GameEntity
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.hasSpawnFlag
import jake2.game.setSpawnFlag
import jake2.game.unsetSpawnFlag
import jake2.qcommon.Defines

/**
 * QUAKED func_conveyor (0 .5 .8) ?
 * START_ON
 * TOGGLE
 * Conveyors are stationary brushes that move what's on them.
 * The brush should have a surface with at least one current content enabled.
 *
 * speed default 100
 */
private const val START_ON = 1
private const val TOGGLE = 2

fun funcConveyor(self: GameEntity, game: GameExportsImpl) {
    if (self.speed == 0f)
        self.speed = 100f

    if (!self.hasSpawnFlag(START_ON)) {
        self.count = self.speed.toInt()
        self.speed = 0f
    }

    self.use = conveyorUse

    game.gameImports.setmodel(self, self.model)
    self.solid = Defines.SOLID_BSP
    game.gameImports.linkentity(self)
}

private val conveyorUse = registerUse("func_conveyor_use") { self, _, _, _ ->
    if (self.hasSpawnFlag(START_ON)) {
        self.speed = 0f
        self.unsetSpawnFlag(START_ON)
    } else {
        self.speed = self.count.toFloat()
        self.setSpawnFlag(START_ON)
    }

    if (!self.hasSpawnFlag(TOGGLE))
        self.count = 0
}

