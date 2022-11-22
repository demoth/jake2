package jake2.game.func

import jake2.game.*
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.util.Lib
import java.time.LocalDateTime

/**
 * QUAKED func_clock (0 0 1) (-8 -8 -8) (8 8 8)
 * TIMER_UP
 * TIMER_DOWN
 * START_OFF
 * MULTI_USE
 *
 * target a target_string with this
 *
 * The default is to be a time of day clock
 *
 * TIMER_UP and TIMER_DOWN run for "count" seconds and the fire "pathtarget"
 * If START_OFF, this entity must be used before it starts
 *
 * "style" 0 "SS" 1 "MM:SS" 2 "HH:MM:SS"
 */
private const val TIMER_UP = 1
private const val TIMER_DOWN = 2
private const val START_OFF = 4
private const val MULTI_USE = 8

fun funcClock(self: SubgameEntity, game: GameExportsImpl) {
    if (self.target == null) {
        game.gameImports.dprintf("${self.classname} with no target at ${Lib.vtos(self.s.origin)}\n")
        game.freeEntity(self)
        return
    }

    if (self.hasSpawnFlag(TIMER_DOWN) && self.count == 0) {
        game.gameImports.dprintf("${self.classname} with no count at ${Lib.vtos(self.s.origin)}")
        game.freeEntity(self)
        return
    }

    if (self.hasSpawnFlag(TIMER_UP) && self.count == 0)
        self.count = 60 * 60

    clockReset(self)

    self.message = ""
    self.think.action = clockThink
    if (self.hasSpawnFlag(START_OFF))
        self.use = clockUse
    else
        self.think.nextTime = game.level.time + 1
}

private val clockThink = registerThink("func_clock_think") { self, game ->
    // enemy here is just the target entity
    if (self.enemy == null) {
        val es = GameBase.G_Find(null, GameBase.findByTargetName, self.target, game)
        if (es != null)
            self.enemy = es.o
        if (self.enemy == null)
            return@registerThink true
    }

    if (self.hasSpawnFlag(TIMER_UP)) {
        clockFormatCountDown(self)
        self.health++
    } else if (self.hasSpawnFlag(TIMER_DOWN)) {
        clockFormatCountDown(self)
        self.health--
    } else {
        val now = LocalDateTime.now()
        self.message = "${now.hour}:${now.minute}:${now.second}"
    }

    self.enemy.message = self.message
    self.enemy.use.use(self.enemy, self, self, game)

    if (self.hasSpawnFlag(TIMER_UP) && self.health > self.wait
                || self.hasSpawnFlag(TIMER_DOWN) && self.health < self.wait) {
        if (self.pathtarget != null) {
            val prevTarget = self.target
            val prevMessage = self.message
            self.target = self.pathtarget
            self.message = null
            GameUtil.G_UseTargets(self, self.activator, game)
            self.target = prevTarget
            self.message = prevMessage
        }
        if (!self.hasSpawnFlag(MULTI_USE)) {
            // todo: free entity
            return@registerThink true
        }
        clockReset(self)
        if (self.hasSpawnFlag(START_OFF)) {
            return@registerThink true
        }
    }

    self.think.nextTime = game.level.time + 1
    true
}

private val clockUse = registerUse("func_clock_use") { self, other, activator, game ->
    if (self.hasSpawnFlag(MULTI_USE))
        self.use = null

    if (self.activator != null)
        return@registerUse
    self.activator = activator
    self.think.action.think(self, game)

}

// don't let field width of any clock messages change, or it
// could cause an overwrite after a game load
private fun clockReset(self: SubgameEntity) {
    self.activator = null
    if (self.hasSpawnFlag(TIMER_UP)) {
        self.health = 0
        self.wait = self.count.toFloat()
    } else if (self.hasSpawnFlag(TIMER_DOWN)) {
        self.health = self.count
        self.wait = 0f
    }
}

private fun clockFormatCountDown(self: SubgameEntity) {
    if (self.style == 0) {
        self.message = "" + self.health
        return
    }
    if (self.style == 1) {
        self.message = "${self.health / 60}:${self.health % 60}"
        return
    }
    if (self.style == 2) {
        self.message = "${self.health / 3600}:${(self.health - self.health / 3600 * 3600) / 60}:${self.health % 60}"
    }
}
