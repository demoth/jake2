package jake2.game.func

import jake2.game.GameUtil
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.util.Lib

/**
 * QUAKED func_timer (0.3 0.1 0.6) (-8 -8 -8) (8 8 8)
 * START_ON
 * "wait" base time between triggering all targets, default is 1
 * "random" wait variance, default is 0 so,
 * the basic time between firing is a random time between (wait - random) and (wait + random)
 *
 * "delay" delay before first firing when turned on, default is 0
 *
 * "pausetime" additional delay used only the very first time and only if spawned with START_ON
 *
 * These can used but not touched.
 */

private const val TIMER_START_ON = 1

val timer = registerThink("func_timer") { self, game ->
    if (self.wait == 0f)
        self.wait = 1.0f

    self.use = timerUse
    self.think.action = timerThink

    if (self.random >= self.wait) {
        self.random = self.wait - Defines.FRAMETIME
        game.gameImports.dprintf("func_timer at ${Lib.vtos(self.s.origin)} has random >= wait\n")
    }

    if (self.spawnflags and TIMER_START_ON != 0) {
        self.think.nextTime = game.level.time + 1.0f + self.st.pausetime + self.delay + self.wait + Lib.crandom() * self.random
        self.activator = self
    }

    self.svflags = Defines.SVF_NOCLIENT
    true
}

private val timerUse = registerUse("func_timer_use") { self, _, activator, game ->
    self.activator = activator

    // if on, turn it off
    if (self.think.nextTime != 0f) {
        self.think.nextTime = 0f
        return@registerUse
    }

    // turn it on
    if (self.delay != 0f)
        self.think.nextTime = game.level.time + self.delay
    else
        timerThink.think(self, game)

}

private val timerThink = registerThink("func_timer_think") { self, game ->
    GameUtil.G_UseTargets(self, self.activator, game)
    self.think.nextTime = game.level.time + self.wait + (Lib.crandom() * self.random)
    true
}
