package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D

private const val MONSTER = 1
private const val NOT_PLAYER = 2
private const val TRIGGERED = 4


/**
 * QUAKED trigger_always (.5 .5 .5) (-8 -8 -8) (8 8 8)
 * This trigger will always fire.
 * It is activated by the world.
 */
fun triggerAlways(self: SubgameEntity, game: GameExportsImpl) {
    if (self.delay < 0.2f)
        self.delay = 0.2f
    GameUtil.G_UseTargets(self, self, game)
}

/**
 * QUAKED trigger_once (.5 .5 .5) ? x x
 * TRIGGERED
 * Triggers once, then removes itself.
 * You must set the key "target" to the name of another
 * object in the level that has a matching "targetname".
 *
 * If TRIGGERED, this trigger must be triggered before it is live.
 *
 * sounds 1) secret 2) beep beep 3) large switch 4)
 *
 * "message" string to be displayed when triggered
 */
fun triggerOnce(self: SubgameEntity, game: GameExportsImpl) {
    // make old maps work because I messed up on flag assignments here
    // triggered was on bit 1 when it should have been on bit 4
    if (self.hasSpawnFlag(1)) {
        self.unsetSpawnFlag(1)
        self.setSpawnFlag(TRIGGERED)
        val v = floatArrayOf(0f, 0f, 0f)
        Math3D.VectorMA(self.mins, 0.5f, self.size, v)
        game.gameImports.dprintf("fixed TRIGGERED flag on ${self.classname} at ${Lib.vtos(v)}\n")
    }
    self.wait = -1f
    triggerMultiple(self, game)
}

/**
 * QUAKED trigger_multiple (.5 .5 .5) ?
 * MONSTER
 * NOT_PLAYER
 * TRIGGERED
 * Variable sized repeatable trigger. Must be targeted at one or more entities.
 *
 * If "delay" is set, the trigger waits some time after activating before firing.
 * "wait" : Seconds between triggering. (.2 default)
 * sounds  1) secret 2) beep beep 3) large switch 4)
 * set "message" to text string
 */
fun triggerMultiple(self: SubgameEntity, game: GameExportsImpl) {
    when (self.sounds) {
        1 -> self.noise_index = game.gameImports.soundindex("misc/secret.wav")
        2 -> self.noise_index = game.gameImports.soundindex("misc/talk.wav")
        3 -> self.noise_index = game.gameImports.soundindex("misc/trigger1.wav")
    }

    if (self.wait == 0f)
        self.wait = 0.2f
    self.touch = triggerTouchMultiple
    self.movetype = GameDefines.MOVETYPE_NONE
    self.svflags = self.svflags or Defines.SVF_NOCLIENT
    if (self.hasSpawnFlag(TRIGGERED)) {
        self.solid = Defines.SOLID_NOT
        self.use = triggerEnable
    } else {
        self.solid = Defines.SOLID_TRIGGER
        self.use = triggerUseMultiple
    }
    if (!Math3D.VectorEquals(self.s.angles, Globals.vec3_origin))
        GameBase.G_SetMovedir(self.s.angles, self.movedir)
    game.gameImports.setmodel(self, self.model)
    game.gameImports.linkentity(self)
}

private val triggerUseMultiple = registerUse("Use_Multi") { self, _, activator, game ->
    self.activator = activator
    multiTrigger(self, game)
}

private val triggerEnable = registerUse("trigger_enable") { self, _, _, game ->
    self.solid = Defines.SOLID_TRIGGER
    self.use = triggerUseMultiple
    game.gameImports.linkentity(self)
}

private val triggerTouchMultiple = registerTouch("Touch_Multi") { self, other, _, _, game ->
    // fixme: looks like these 2 conditions (and therefore spawnflags) are doing the same thing
    if (other.client != null) {
        if (self.hasSpawnFlag(NOT_PLAYER))
            return@registerTouch
    } else if (other.svflags and Defines.SVF_MONSTER != 0) {
        if (!self.hasSpawnFlag(MONSTER))
            return@registerTouch
    } else
        return@registerTouch

    if (!Math3D.VectorEquals(self.movedir, Globals.vec3_origin)) {
        val forward = floatArrayOf(0f, 0f, 0f)
        Math3D.AngleVectors(other.s.angles, forward, null, null)
        if (Math3D.DotProduct(forward, self.movedir) < 0)
            return@registerTouch
    }

    self.activator = other
    multiTrigger(self, game)
}

/*
 * The trigger was just activated.
 * self.activator should be set to the activator, so it can be held through a
 * delay so wait for the delay time before firing.
 */
private fun multiTrigger(self: SubgameEntity, game: GameExportsImpl) {
    if (self.think.nextTime != 0f)
        return  // already been triggered
    GameUtil.G_UseTargets(self, self.activator, game)
    if (self.wait > 0) {
        self.think.action = triggerMultiWait
        self.think.nextTime = game.level.time + self.wait
    } else {
        // we can't just remove (self) here, because this is a touch
        // function
        // called while looping through area links...
        self.touch = null
        self.think.nextTime = game.level.time + Defines.FRAMETIME
        self.think.action = GameUtil.G_FreeEdictA
    }
}

// the wait time has passed, so set back up for another activation
private val triggerMultiWait = registerThink("multi_wait") { self, _ ->
    self.think.nextTime = 0f
    true
}

/**
 * QUAKED trigger_counter (.5 .5 .5) ? nomessage Acts as an intermediary for
 * an action that takes multiple inputs.
 *
 * If NO_MESSAGE is not set, t will print "1 more... " etc. when triggered and
 * "sequence complete" when finished.
 *
 * After the counter has been triggered "count" times (default 2), it will
 * fire all of its targets and remove itself.
 */
private const val NO_MESSAGE = 1
fun triggerCounter(self: SubgameEntity, game: GameExportsImpl) {
    self.wait = -1f
    if (0 == self.count)
        self.count = 2
    self.use = triggerCounterUse
}

private val triggerCounterUse = registerUse("trigger_counter_use") { self, other, activator, game ->
    if (self.count == 0)
        return@registerUse

    self.count--

    if (self.count != 0) {
        if (!self.hasSpawnFlag(NO_MESSAGE)) {
            game.gameImports.centerprintf(activator, "${self.count} more to go...")
            game.gameImports.sound(
                activator, Defines.CHAN_AUTO, game.gameImports
                    .soundindex("misc/talk1.wav"), 1f,
                Defines.ATTN_NORM.toFloat(), 0f
            )
        }
        return@registerUse
    }

    if (!self.hasSpawnFlag(NO_MESSAGE)) {
        game.gameImports.centerprintf(activator, "Sequence completed!")
        game.gameImports.sound(
            activator, Defines.CHAN_AUTO, game.gameImports
                .soundindex("misc/talk1.wav"), 1f,
            Defines.ATTN_NORM.toFloat(), 0f
        )
    }
    self.activator = activator
    multiTrigger(self, game)
}
