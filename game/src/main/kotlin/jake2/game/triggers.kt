package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.items.GameItems
import jake2.qcommon.Defines
import jake2.qcommon.Defines.DAMAGE_NO
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
fun triggerAlways(self: GameEntity, game: GameExportsImpl) {
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
fun triggerOnce(self: GameEntity, game: GameExportsImpl) {
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
fun triggerMultiple(self: GameEntity, game: GameExportsImpl) {
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
private fun multiTrigger(self: GameEntity, game: GameExportsImpl) {
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
fun triggerCounter(self: GameEntity, game: GameExportsImpl) {
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

/**
 * QUAKED trigger_relay (.5 .5 .5) (-8 -8 -8) (8 8 8) This fixed size
 * trigger cannot be touched, it can only be fired by other events.
 */
fun triggerRelay(self: GameEntity, game: GameExportsImpl) {
    self.use = triggerRelayUse
}

private val triggerRelayUse = registerUse("trigger_relay_use") { self: GameEntity, other: GameEntity?, activator: GameEntity?, game: GameExportsImpl ->
    GameUtil.G_UseTargets(self, activator, game)
}

/**
 * QUAKED trigger_key (.5 .5 .5) (-8 -8 -8) (8 8 8)
 * A relay trigger that only fires its targets if player has the proper key.
 * Use "item" to specify the required key, for example "key_data_cd"
 */
fun triggerKey(self: GameEntity, game: GameExportsImpl) {
    if (self.st.item == null) {
        game.gameImports.dprintf("no key item for trigger_key at ${Lib.vtos(self.s.origin)}\n")
        return
    }
    self.item = GameItems.FindItemByClassname(self.st.item, game)

    if (self.item == null) {
        game.gameImports.dprintf("item ${self.st.item} not found for trigger_key at ${Lib.vtos(self.s.origin)}\n")
        return
    }

    if (self.target == null) {
        game.gameImports.dprintf("${self.classname} at ${Lib.vtos(self.s.origin)} has no target\n")
        return
    }

    game.gameImports.soundindex("misc/keytry.wav")
    game.gameImports.soundindex("misc/keyuse.wav")
    self.use = triggerKeyUse
}

private val triggerKeyUse = registerUse("trigger_key_use") { self, other, activator, game ->

    if (self.item == null)
        return@registerUse

    val activatorClient = activator?.client ?: return@registerUse

    val index = self.item.index
    if (activatorClient.pers.inventory[index] == 0) {
        if (game.level.time < self.touch_debounce_time)
            return@registerUse
        self.touch_debounce_time = game.level.time + 5.0f
        game.gameImports.centerprintf(activator, "You need the " + self.item.pickup_name)
        game.gameImports.sound(
            activator, Defines.CHAN_AUTO,
            game.gameImports.soundindex("misc/keytry.wav"), 1f,
            Defines.ATTN_NORM.toFloat(), 0f
        )
        return@registerUse
    }

    game.gameImports.sound(
        activator, Defines.CHAN_AUTO,
        game.gameImports.soundindex("misc/keyuse.wav"), 1f,
        Defines.ATTN_NORM.toFloat(), 0f
    )

    // in multiplayer games we deduct keys from all players at once
    if (game.gameCvars.coop.value != 0f) {
        if ("key_power_cube" == self.item.classname) {
            // power cubes are handled differently in coop than other keys
            var cubeMaxBit = 0
            while (cubeMaxBit < 8) {
                // find the highest cube bit the player has
                if (activatorClient.pers.power_cubes and (1 shl cubeMaxBit) != 0)
                    break
                cubeMaxBit++
            }
            for (player in 1..game.game.maxclients) {
                val ent = game.g_edicts[player]
                if (!ent.inuse)
                    continue
                val client = ent.client ?: continue
                if (client.pers.power_cubes and (1 shl cubeMaxBit) != 0) {
                    client.pers.inventory[index]--
                    client.pers.power_cubes = client.pers.power_cubes and (1 shl cubeMaxBit).inv()
                }
            }
        } else {
            for (player in 1..game.game.maxclients) {
                val ent = game.g_edicts[player]
                if (!ent.inuse)
                    continue
                val client = ent.client ?: continue
                client.pers.inventory[index] = 0
            }
        }
    } else {
        activatorClient.pers.inventory[index]--
    }

    GameUtil.G_UseTargets(self, activator, game)
    self.use = null
}

/**
 * QUAKED trigger_push (.5 .5 .5) ?
 * PUSH_ONCE
 * Pushes the player
 * "speed" defaults to 1000
 */

private const val PUSH_ONCE = 1

fun triggerPush(self: GameEntity, game: GameExportsImpl) {
    initTrigger(self, game)
    game.windsound_index = game.gameImports.soundindex("misc/windfly.wav")
    self.touch = triggerPushTouch
    if (self.speed == 0f)
        self.speed = 1000f
    game.gameImports.linkentity(self)
}

private val triggerPushTouch = registerTouch("trigger_push_touch") { self, other, plane, surf, game ->
    if ("grenade" == other.classname) {
        Math3D.VectorScale(self.movedir, self.speed * 10, other.velocity)
    } else if (other.health > 0) {
        Math3D.VectorScale(self.movedir, self.speed * 10, other.velocity)
        val player = other.client
        if (player != null) {
            // don't take falling damage immediately from this
            Math3D.VectorCopy(other.velocity, player.oldvelocity)
            if (other.fly_sound_debounce_time < game.level.time) {
                other.fly_sound_debounce_time = game.level.time + 1.5f
                game.gameImports.sound(
                    other, Defines.CHAN_AUTO, game.windsound_index,
                    1f, Defines.ATTN_NORM.toFloat(), 0f
                )
            }
        }
    }
    // fixme: should it be inside the player branch above?
    if (self.hasSpawnFlag(PUSH_ONCE))
        game.freeEntity(self)
}

/**
 * QUAKED trigger_gravity (.5 .5 .5) ? Changes the touching entity's gravity
 * to the value of "gravity". 1.0 is standard gravity for the level.
 */
fun triggerGravity(self: GameEntity, game: GameExportsImpl) {
    if (self.st.gravity == null) {
        game.gameImports.dprintf("trigger_gravity without gravity set at ${Lib.vtos(self.s.origin)}\n")
        game.freeEntity(self)
        return
    }
    initTrigger(self, game)
    self.gravity = Lib.atoi(self.st.gravity).toFloat()
    self.touch = triggerGravityTouch
}

private val triggerGravityTouch = registerTouch("trigger_gravity_touch") { self, other, _, _, _ ->
    other.gravity = self.gravity
}

/**
 * QUAKED trigger_hurt (.5 .5 .5) ?
 * START_OFF
 * TOGGLE
 * SILENT
 * NO_PROTECTION
 * SLOW
 * Any entity that touches this will be hurt. It does dmg points of damage each server frame
 *
 * SILENT supresses playing the sound.
 * SLOW changes the damage rate to once per second.
 * NO_PROTECTION *nothing* stops the damage.
 *
 * "dmg" default 5 (whole numbers only)
 *
 */
private const val START_OFF = 1
private const val TOGGLE = 2
private const val SILENT = 4
private const val NO_PROTECTION = 8
private const val SLOW = 16
fun triggerHurt(self: GameEntity, game: GameExportsImpl) {
    initTrigger(self, game)
    self.noise_index = game.gameImports.soundindex("world/electro.wav")
    self.touch = triggerHurtTouch
    if (self.dmg == 0)
        self.dmg = 5

    if (self.hasSpawnFlag(START_OFF))
        self.solid = Defines.SOLID_NOT
    else
        self.solid = Defines.SOLID_TRIGGER

    if (self.hasSpawnFlag(TOGGLE))
        self.use = triggerHurtUse
    game.gameImports.linkentity(self)
}

private val triggerHurtTouch = registerTouch("hurt_touch") { self, other, plane, surf, game ->
    if (other.takedamage == DAMAGE_NO)
        return@registerTouch

    if (self.timestamp > game.level.time)
        return@registerTouch

    if (self.hasSpawnFlag(SLOW))
        self.timestamp = game.level.time + 1
    else
        self.timestamp = game.level.time + Defines.FRAMETIME

    if (!self.hasSpawnFlag(SILENT)) {
        if (game.level.framenum % 10 == 0)
            game.gameImports.sound(
                other, Defines.CHAN_AUTO,
                self.noise_index, 1f,
                Defines.ATTN_NORM.toFloat(), 0f
            )
    }

    val dflags: Int = if (self.hasSpawnFlag(NO_PROTECTION)) DamageFlags.DAMAGE_NO_PROTECTION else 0
    GameCombat.T_Damage(
        other, self, self, Globals.vec3_origin,
        other.s.origin, Globals.vec3_origin, self.dmg, self.dmg,
        dflags, GameDefines.MOD_TRIGGER_HURT, game
    )
}

private val triggerHurtUse = registerUse("hurt_use") { self, other, activator, game ->
    if (self.solid == Defines.SOLID_NOT)
        self.solid = Defines.SOLID_TRIGGER
    else
        self.solid = Defines.SOLID_NOT

    // todo: fix from yamagi quake for monsters spawning inside the trigger_hurt

    game.gameImports.linkentity(self)

    if (!self.hasSpawnFlag(TOGGLE))
        self.use = null

}


/**
 * QUAKED trigger_monsterjump (.5 .5 .5) ?
 * Walking monsters that touch this will jump in the direction of the trigger's angle
 *
 * "speed" default to 200, the speed thrown forward
 * "height" default to 200, the speed thrown upwards
 */
fun triggerMonsterJump(self: GameEntity, game: GameExportsImpl) {
    if (self.speed == 0f)
        self.speed = 200f

    if (self.st.height == 0)
        self.st.height = 200

    if (self.s.angles[Defines.YAW] == 0f)
        self.s.angles[Defines.YAW] = 360f
    initTrigger(self, game)
    self.touch = triggerMonsterJumpTouch
    self.movedir[2] = self.st.height.toFloat()
}

private val triggerMonsterJumpTouch = registerTouch("trigger_monsterjump_touch") { self, other, plane, surf, game ->
    if (other.flags and (GameDefines.FL_FLY or GameDefines.FL_SWIM) != 0)
        return@registerTouch
    if (other.svflags and Defines.SVF_DEADMONSTER != 0)
        return@registerTouch
    if (other.svflags and Defines.SVF_MONSTER == 0)
        return@registerTouch

    // set XY even if not on ground, so the jump will clear lips
    other.velocity[0] = self.movedir[0] * self.speed
    other.velocity[1] = self.movedir[1] * self.speed

    if (other.groundentity != null)
        return@registerTouch

    other.velocity[2] = self.movedir[2]

}

private fun initTrigger(self: GameEntity, game: GameExportsImpl) {
    if (!Math3D.VectorEquals(self.s.angles, Globals.vec3_origin))
        GameBase.G_SetMovedir(self.s.angles, self.movedir)
    self.solid = Defines.SOLID_TRIGGER
    self.movetype = GameDefines.MOVETYPE_NONE
    game.gameImports.setmodel(self, self.model)
    self.svflags = Defines.SVF_NOCLIENT
}

