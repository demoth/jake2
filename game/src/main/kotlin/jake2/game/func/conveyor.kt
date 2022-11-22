package jake2.game.func

import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.game.adapters.SuperAdapter.Companion.registerUse
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

fun funcConveyor(self: SubgameEntity, game: GameExportsImpl) {
    if (self.speed == 0f)
        self.speed = 100f

    if (self.spawnflags and START_ON == 0) {
        self.count = self.speed.toInt()
        self.speed = 0f
    }

    self.use = conveyorUse

    game.gameImports.setmodel(self, self.model)
    self.solid = Defines.SOLID_BSP
    game.gameImports.linkentity(self)
}

private val conveyorUse = registerUse("func_conveyor_use") { self, _, _, _ ->
    if (self.spawnflags and START_ON != 0) {
        self.speed = 0f
        self.spawnflags = self.spawnflags and START_ON.inv()
    } else {
        self.speed = self.count.toFloat()
        self.spawnflags = self.spawnflags or START_ON
    }

    if (self.spawnflags and TOGGLE == 0)
        self.count = 0
}

