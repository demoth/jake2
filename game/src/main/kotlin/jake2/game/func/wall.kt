package jake2.game.func

import jake2.game.GameDefines
import jake2.game.GameUtil
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
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

val wall = registerThink("func_wall") { self, game ->
    self.movetype = GameDefines.MOVETYPE_PUSH
    game.gameImports.setmodel(self, self.model)

    if (self.spawnflags and ANIMATED != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALL
    if (self.spawnflags and ANIMATED_FAST != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALLFAST

    // not triggered, not toggle, not started on == just a wall
    if (self.spawnflags and 7 == 0) {
        self.solid = Defines.SOLID_BSP
        game.gameImports.linkentity(self)
        return@registerThink true
    }

    // it must be TRIGGER_SPAWN
    if (self.spawnflags and TRIGGER_SPAWN == 0) {
        game.gameImports.dprintf("func_wall missing TRIGGER_SPAWN\n")
        self.spawnflags = self.spawnflags or TRIGGER_SPAWN
    }

    // yell if the spawnflags are odd
    if (self.spawnflags and START_ON != 0) {
        if (self.spawnflags and TOGGLE == 0) {
            game.gameImports.dprintf("func_wall START_ON without TOGGLE\n")
            self.spawnflags = self.spawnflags or TOGGLE
        }
    }

    self.use = wallUse
    if (self.spawnflags and START_ON != 0) {
        self.solid = Defines.SOLID_BSP
    } else {
        self.solid = Defines.SOLID_NOT
        self.svflags = self.svflags or Defines.SVF_NOCLIENT
    }
    game.gameImports.linkentity(self)

    true
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

    if (self.spawnflags and TOGGLE == 0)
        self.use = null
}

