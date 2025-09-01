package jake2.game.func

import jake2.game.GameDefines
import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.game.GameUtil
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.hasSpawnFlag
import jake2.game.setSpawnFlag
import jake2.qcommon.Defines

/**
 * QUAKED func_wall (0 .5 .8) ?
 * TRIGGER_SPAWN
 * TOGGLE
 * START_ON
 * ANIMATED
 * ANIMATED_FAST
 * This is just a solid wall if not inhibited
 *
 * TRIGGER_SPAWN the wall will not be present until triggered it will then
 * blink in to existence; it will kill anything that was in its way
 *
 * TOGGLE only valid for TRIGGER_SPAWN walls this allows the wall to be
 * turned on and off
 *
 * START_ON only valid for TRIGGER_SPAWN walls the wall will initially be
 * present
 */
private const val TRIGGER_SPAWN = 1
private const val TOGGLE = 2
private const val START_ON = 4
private const val ANIMATED = 8
private const val ANIMATED_FAST = 16

fun funcWall(self: GameEntity, game: GameExportsImpl) {
    self.movetype = GameDefines.MOVETYPE_PUSH
    game.gameImports.setmodel(self, self.model)

    if (self.hasSpawnFlag(ANIMATED))
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALL
    if (self.hasSpawnFlag(ANIMATED_FAST))
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALLFAST

    // not triggered, not toggle, not started on == just a wall
    if (!self.hasSpawnFlag(TRIGGER_SPAWN) && !self.hasSpawnFlag(TOGGLE) && !self.hasSpawnFlag(START_ON)) {
        self.solid = Defines.SOLID_BSP
        game.gameImports.linkentity(self)
        return
    }

    // it must be TRIGGER_SPAWN
    if (!self.hasSpawnFlag(TRIGGER_SPAWN)) {
        self.setSpawnFlag(TRIGGER_SPAWN)
        game.gameImports.dprintf("func_wall missing TRIGGER_SPAWN\n")
    }

    // yell if the spawnflags are odd
    if (self.hasSpawnFlag(START_ON)) {
        if (!self.hasSpawnFlag(TOGGLE)) {
            self.setSpawnFlag(TOGGLE)
            game.gameImports.dprintf("func_wall START_ON without TOGGLE\n")
        }
    }

    self.use = wallUse
    if (self.hasSpawnFlag(START_ON)) {
        self.solid = Defines.SOLID_BSP
    } else {
        self.solid = Defines.SOLID_NOT
        self.svflags = self.svflags or Defines.SVF_NOCLIENT
    }
    game.gameImports.linkentity(self)
}

private val wallUse = registerUse("func_wall_use") { self, other, activator, game ->
    if (self.solid == Defines.SOLID_NOT) {
        // appear and kill everything inside
        self.solid = Defines.SOLID_BSP
        self.svflags = self.svflags and Defines.SVF_NOCLIENT.inv()
        GameUtil.KillBox(self, game)
    } else {
        // disappear
        self.solid = Defines.SOLID_NOT
        self.svflags = self.svflags or Defines.SVF_NOCLIENT
    }
    game.gameImports.linkentity(self)

    if (!self.hasSpawnFlag(TOGGLE))
        self.use = null
}

