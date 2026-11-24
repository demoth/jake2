package jake2.game.func

import jake2.qcommon.Com
import jake2.qcommon.Com.LogSubsystem.GAME
import jake2.game.*
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.util.Lib
import java.time.LocalTime
import java.util.Locale

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

fun funcClock(self: GameEntity, game: GameExportsImpl) {
    if (self.target == null) {
        Com.Warn(GAME, "${self.classname} with no target at ${Lib.vtos(self.s.origin)}")
        game.freeEntity(self)
        return
    }

    if (self.hasSpawnFlag(TIMER_DOWN) && self.count <= 0) {
        Com.Warn(GAME, "${self.classname} with no count at ${Lib.vtos(self.s.origin)}")
        game.freeEntity(self)
        return
    }

    if (self.hasSpawnFlag(TIMER_UP) && self.count <= 0)
        self.count = 60 * 60

    clockReset(self)

    self.think.action = clockThink
    if (self.hasSpawnFlag(START_OFF))
        self.use = clockUse
    else
        self.think.nextTime = game.level.time + 1
}

private val clockThink = registerThink("func_clock_think") { self, game ->
    // enemy here is just the target entity
    val display = self.enemy?.takeIf { it.inuse && it.classname == "target_string" }
        ?: GameBase.G_Find(null, GameBase.findByTargetName, self.target, game)?.o
            ?.takeIf { it.classname == "target_string" }
    self.enemy = display
    if (display != null) {
        val text = if (self.hasSpawnFlag(TIMER_UP) || self.hasSpawnFlag(TIMER_DOWN)) {
            formatCounter(self.style, self.health)
        } else {
            formatCounter(2, LocalTime.now().toSecondOfDay())
        }
        showTargetString(display, text)
    }

    if (self.hasSpawnFlag(TIMER_UP)) {
        self.health++
    } else if (self.hasSpawnFlag(TIMER_DOWN)) {
        self.health--
    }

    if (self.hasSpawnFlag(TIMER_UP) && self.health > self.count
                || self.hasSpawnFlag(TIMER_DOWN) && self.health < 0) {
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
            self.think.action = GameUtil.G_FreeEdictA
            self.think.nextTime = game.level.time + 1
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
    if (!self.hasSpawnFlag(MULTI_USE))
        self.use = null

    if (self.activator != null)
        return@registerUse
    self.activator = activator
    self.think.action.think(self, game)

}

private fun clockReset(self: GameEntity) {
    self.activator = null
    if (self.hasSpawnFlag(TIMER_UP)) {
        self.health = 0
    } else if (self.hasSpawnFlag(TIMER_DOWN)) {
        self.health = self.count
    }
}

private fun formatCounter(style: Int, seconds: Int): String = when (style) {
    0 -> "%2d".format(Locale.ROOT, seconds)
    1 -> "%2d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    2 -> "%2d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
    else -> ""
}
