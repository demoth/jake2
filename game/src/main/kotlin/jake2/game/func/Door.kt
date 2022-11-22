package jake2.game

import jake2.game.GameBase.G_SetMovedir
import jake2.game.adapters.SuperAdapter.Companion.registerBlocked
import jake2.game.adapters.SuperAdapter.Companion.registerDie
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.func.startMovement
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D
import jake2.qcommon.util.Math3D.VectorCopy
import jake2.qcommon.util.Math3D.VectorMA
import kotlin.math.abs


const val DOOR_START_OPEN = 1
private const val DOOR_REVERSE = 2
private const val DOOR_CRUSHER = 4
private const val DOOR_NOMONSTER = 8

/**
 * QUAKED func_door (0 .5 .8) ?
 *
 * ## Spawnflags
 *  * START_OPEN - will make the door operate in reverse. So the door will be open and then close when triggered.
 *  * CRUSHER - By default when a door crushes you against a wall it will take off damage and then go back a bit and then take off damage again. If crusher is on then there won't be that little pause between trying to close again. It will just keep trying to close and crush you. No matter what, if crush is turned on or turned off a door will take off damage and eventually kill you. Crusher will just do it quicker.
 *  * NOMONSTER - monsters will not trigger this door, by default monsters can open a door
 *  * ANIMATED
 *  * TOGGLE - wait in both the start and end states for a trigger event.
 *  * ANIMATED
 *  * ANIMATED_FAST

 *  todo: add animation flags
 *
 * ## Properties:
 *  * message - will print a message when the door is triggered. Message will NOT work when the door is not triggered open.
 *  * targetname - if set, no touch field will be spawned and a remote button or trigger field activates the door
 *  * health - if set, door must be shot to be opened
 *  * angle - determines the opening direction
 *  * dmg - damage to inflict when blocked (2 default)
 *  * speed/accel/decel - movement related (100 default), twice as fast in deathmatch
 *  * wait - wait before returning (3 default, -1 = never return)
 *  * lip - lip remaining at end of move (8 default)
 *  * sounds 1) silent 2) light 3) medium 4) heavy
 */
val funcDoor = registerThink("func_door") { self, game ->
    // todo: split into entity property initialization & moveinfo
    val abs_movedir = floatArrayOf(0f, 0f, 0f)
    if (self.sounds != 1) {
        self.moveinfo.sound_start = game.gameImports.soundindex("doors/dr1_strt.wav")
        self.moveinfo.sound_middle = game.gameImports.soundindex("doors/dr1_mid.wav")
        self.moveinfo.sound_end = game.gameImports.soundindex("doors/dr1_end.wav")
    }
    G_SetMovedir(self.s.angles, self.movedir)
    self.movetype = GameDefines.MOVETYPE_PUSH
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)
    self.blocked = doorBlocked
    self.use = doorOpenUse
    if (self.speed == 0f)
        self.speed = 100f
    if (game.gameCvars.deathmatch.value != 0f)
        self.speed *= 2f
    if (self.accel == 0f)
        self.accel = self.speed
    if (self.decel == 0f)
        self.decel = self.speed
    if (self.wait == 0f)
        self.wait = 3f
    if (self.st.lip == 0)
        self.st.lip = 8
    if (self.dmg == 0)
        self.dmg = 2

    // calculate second position
    VectorCopy(self.s.origin, self.pos1)
    abs_movedir[0] = abs(self.movedir[0])
    abs_movedir[1] = abs(self.movedir[1])
    abs_movedir[2] = abs(self.movedir[2])
    self.moveinfo.distance =
        abs_movedir[0] * self.size[0] + abs_movedir[1] * self.size[1] + (abs_movedir[2] * self.size[2]) - self.st.lip
    VectorMA(self.pos1, self.moveinfo.distance, self.movedir, self.pos2)

    // if it starts open, switch the positions
    if (self.spawnflags and DOOR_START_OPEN != 0) {
        VectorCopy(self.pos2, self.s.origin)
        VectorCopy(self.pos1, self.pos2)
        VectorCopy(self.s.origin, self.pos1)
    }
    self.moveinfo.state = GameFunc.STATE_BOTTOM
    if (self.health != 0) {
        self.takedamage = Defines.DAMAGE_YES
        self.die = doorKilled
        self.max_health = self.health
    } else if (self.targetname != null && self.message != null) {
        game.gameImports.soundindex("misc/talk.wav")
        self.touch = doorTouch
    }
    self.moveinfo.speed = self.speed
    self.moveinfo.accel = self.accel
    self.moveinfo.decel = self.decel
    self.moveinfo.wait = self.wait
    VectorCopy(self.pos1, self.moveinfo.start_origin)
    VectorCopy(self.s.angles, self.moveinfo.start_angles)
    VectorCopy(self.pos2, self.moveinfo.end_origin)
    VectorCopy(self.s.angles, self.moveinfo.end_angles)
    if (self.spawnflags and 16 != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALL
    if (self.spawnflags and 64 != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALLFAST

    // to simplify logic elsewhere, make non-teamed doors into a team of one
    if (null == self.team)
        self.teammaster = self
    game.gameImports.linkentity(self)
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    if (self.health != 0 || self.targetname != null)
        self.think.action = doorCalculateMoveSpeed
    else
        self.think.action = spawnTouchTrigger
    true
}

private const val DOOR_X_AXIS = 64

private const val DOOR_Y_AXIS = 128

/**
 * QUAKED func_door_rotating (0 .5 .8) ?
 * Flags:
 *   * START_OPEN
 *   * REVERSE
 *   * CRUSHER
 *   * NOMONSTER
 *   * ANIMATED
 *   * TOGGLE
 *   * X_AXIS
 *   * Y_AXIS
 *
 * TOGGLE causes the door to wait in
 * both the start and end states for a trigger event.
 *
 * START_OPEN the door to moves to its destination when spawned, and operate
 * in reverse. It is used to temporarily or permanently close off an area
 * when triggered (not useful for touch or takedamage doors). NOMONSTER
 * monsters will not trigger this door
 *
 * You need to have an origin brush as part of this entity. The center of
 * that brush will be the point around which it is rotated. It will rotate
 * around the Z axis by default. You can check either the X_AXIS or Y_AXIS
 * box to change that.
 *
 * "distance" is how many degrees the door will be rotated. "speed"
 * determines how fast the door moves; default value is 100.
 *
 * REVERSE will cause the door to rotate in the opposite direction.
 *
 * "message" is printed when the door is touched if it is a trigger door and
 * it hasn't been fired yet
 *
 * "angle" determines the opening direction
 * "targetname" if set, no touch field will be spawned and a remote button
 * or trigger field activates the door.
 *
 * "health" if set, door must be shot open
 *
 * "speed" movement speed (100 default)
 *
 * "wait" wait before returning (3 default, -1 = never return)
 *
 * "dmg" damage to inflict when blocked (2 default)
 *
 * "sounds" 1) silent 2) light 3) medium 4) heavy
 */
val funcDoorRotating = registerThink("func_door_rotating") { self, game ->
    Math3D.VectorClear(self.s.angles)

    // set the axis of rotation
    Math3D.VectorClear(self.movedir)
    if (self.spawnflags and DOOR_X_AXIS != 0)
        self.movedir[2] = 1.0f
    else if (self.spawnflags and DOOR_Y_AXIS != 0)
        self.movedir[0] = 1.0f
    else  // Z_AXIS
        self.movedir[1] = 1.0f

    // check for reverse rotation
    if (self.spawnflags and DOOR_REVERSE != 0)
        Math3D.VectorNegate(self.movedir, self.movedir)

    if (0 == self.st.distance) {
        game.gameImports.dprintf("${self.classname} at ${Lib.vtos(self.s.origin)} with no distance set")
        self.st.distance = 90
    }

    VectorCopy(self.s.angles, self.pos1)
    VectorMA(self.s.angles, self.st.distance.toFloat(), self.movedir, self.pos2)
    self.moveinfo.distance = self.st.distance.toFloat()

    self.movetype = GameDefines.MOVETYPE_PUSH
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    self.blocked = doorBlocked
    self.use = doorOpenUse

    if (0f == self.speed)
        self.speed = 100f
    if (0f == self.accel)
        self.accel = self.speed
    if (0f == self.decel)
        self.decel = self.speed

    if (0f == self.wait)
        self.wait = 3f
    if (0 == self.dmg)
        self.dmg = 2

    if (self.sounds != 1) {
        self.moveinfo.sound_start = game.gameImports.soundindex("doors/dr1_strt.wav")
        self.moveinfo.sound_middle = game.gameImports.soundindex("doors/dr1_mid.wav")
        self.moveinfo.sound_end = game.gameImports.soundindex("doors/dr1_end.wav")
    }

    // if it starts open, switch the positions
    if (self.spawnflags and DOOR_START_OPEN != 0) {
        VectorCopy(self.pos2, self.s.angles)
        VectorCopy(self.pos1, self.pos2)
        VectorCopy(self.s.angles, self.pos1)
        Math3D.VectorNegate(self.movedir, self.movedir)
    }

    if (self.health != 0) {
        self.takedamage = Defines.DAMAGE_YES
        self.die = doorKilled
        self.max_health = self.health
    }

    if (self.targetname != null && self.message != null) {
        game.gameImports.soundindex("misc/talk.wav")
        self.touch = doorTouch
    }

    self.moveinfo.state = GameFunc.STATE_BOTTOM
    self.moveinfo.speed = self.speed
    self.moveinfo.accel = self.accel
    self.moveinfo.decel = self.decel
    self.moveinfo.wait = self.wait
    VectorCopy(self.s.origin, self.moveinfo.start_origin)
    VectorCopy(self.pos1, self.moveinfo.start_angles)
    VectorCopy(self.s.origin, self.moveinfo.end_origin)
    VectorCopy(self.pos2, self.moveinfo.end_angles)

    if (self.spawnflags and 16 != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALL

    // to simplify logic elsewhere, make non-teamed doors into a team of one
    if (self.team == null)
        self.teammaster = self

    game.gameImports.linkentity(self)

    self.think.nextTime = game.level.time + Defines.FRAMETIME
    if (self.health != 0 || self.targetname != null)
        self.think.action = doorCalculateMoveSpeed
    else
        self.think.action = spawnTouchTrigger
    true

}

private val doorBlocked = registerBlocked("door_blocked") { self, obstacle, game ->
    // if not a monster or player
    if ((obstacle.svflags and Defines.SVF_MONSTER == 0) && obstacle.client == null) {
        // give it a chance to go away on its own terms (like gibs)
        GameCombat.T_Damage(
            obstacle, self, self, Globals.vec3_origin,
            obstacle.s.origin, Globals.vec3_origin, 100000, 1, 0,
            GameDefines.MOD_CRUSH, game
        )
        // if it's still there, nuke it
        // fixme check: before it was `obstacle != null`
        if (obstacle.inuse)
            GameMisc.BecomeExplosion1(obstacle, game)
        return@registerBlocked
    }

    GameCombat.T_Damage(
        obstacle, self, self, Globals.vec3_origin,
        obstacle.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
        GameDefines.MOD_CRUSH, game
    )

    if (self.spawnflags and DOOR_CRUSHER != 0)
        return@registerBlocked

    // if a door has a negative wait, it would never come back if
    // blocked, so let it just squash the object to death real fast
    if (self.moveinfo.wait >= 0) {
        var team: SubgameEntity?
        if (self.moveinfo.state == GameFunc.STATE_DOWN) {
            team = self.teammaster
            while (team != null) {
                doorOpening(team, team.activator, game)
                team = team.teamchain
            }
        } else {
            team = self.teammaster
            while (team != null) {
                doorClosing.think(team, game)
                team = team.teamchain
            }
        }
    }

}

val doorOpenUse = registerUse("door_use") { self, other, activator, game ->

    if (self.flags and GameDefines.FL_TEAMSLAVE != 0)
        return@registerUse

    if (self.spawnflags and GameFunc.DOOR_TOGGLE != 0) {
        if (self.moveinfo.state == GameFunc.STATE_UP || self.moveinfo.state == GameFunc.STATE_TOP) {
            // trigger all paired doors
            var team: SubgameEntity? = self
            while (team != null) {
                team.message = null
                team.touch = null
                doorClosing.think(team, game)
                team = team.teamchain
            }
            return@registerUse
        }
    }

    // trigger all paired doors
    var team: SubgameEntity? = self
    while (team != null) {
        team.message = null
        team.touch = null
        doorOpening(team, activator, game)
        team = team.teamchain
    }
}

// print a message
private val doorTouch = registerTouch("door_touch") { self, other, plane, surf, game ->
    // only players receive messages
    if (null == other.client)
        return@registerTouch

    if (game.level.time < self.touch_debounce_time)
        return@registerTouch

    self.touch_debounce_time = game.level.time + 5.0f

    if (!self.message.isNullOrBlank()) {
        game.gameImports.centerprintf(other, self.message)
        game.gameImports.sound(other, Defines.CHAN_AUTO, game.gameImports.soundindex("misc/talk1.wav"), 1f, Defines.ATTN_NORM.toFloat(), 0f)
    }

}

/**
 * spawn a trigger surrounding the entire team unless it is already targeted by another entity.
 */
private val spawnTouchTrigger = registerThink("think_spawn_door_trigger") { ent, game->
    val mins = floatArrayOf(0f, 0f, 0f)
    val maxs = floatArrayOf(0f, 0f, 0f)

    // only the team leader spawns a trigger
    if (ent.flags and GameDefines.FL_TEAMSLAVE != 0)
        return@registerThink true

    VectorCopy(ent.absmin, mins)
    VectorCopy(ent.absmax, maxs)

    var team: SubgameEntity? = ent.teamchain
    while (team != null) {
        addPointToBounds(team.absmin, mins, maxs)
        addPointToBounds(team.absmax, mins, maxs)
        team = team.teamchain
    }

    // expand
    mins[0] -= 60f
    mins[1] -= 60f
    maxs[0] += 60f
    maxs[1] += 60f

    val trigger = game.G_Spawn()
    VectorCopy(mins, trigger.mins)
    VectorCopy(maxs, trigger.maxs)
    trigger.owner = ent
    trigger.solid = Defines.SOLID_TRIGGER
    trigger.movetype = GameDefines.MOVETYPE_NONE
    trigger.touch = doorTriggerTouch
    game.gameImports.linkentity(trigger)

    if (ent.spawnflags and DOOR_START_OPEN != 0)
        doorUseAreaPortals(ent, true, game)

    doorCalculateMoveSpeed.think(ent, game)
    true
}

private fun addPointToBounds(v: FloatArray, mins: FloatArray, maxs: FloatArray) {
    for (i in 0..2) {
        if (v[i] < mins[i])
            mins[i] = v[i]
        if (v[i] > maxs[i])
            maxs[i] = v[i]
    }
}

private val doorTriggerTouch = registerTouch("touch_door_trigger") { self, other, plane, surf, game ->
    // dead cannot open doors
    if (other.health <= 0)
        return@registerTouch

    // only monsters & players
    if (other.svflags and Defines.SVF_MONSTER == 0 && other.client == null)
        return@registerTouch

    if (other.svflags and Defines.SVF_MONSTER != 0 && self.owner.spawnflags and DOOR_NOMONSTER != 0)
        return@registerTouch

    if (game.level.time < self.touch_debounce_time)
        return@registerTouch

    self.touch_debounce_time = game.level.time + 1.0f

    doorOpenUse.use(self.owner, other, other, game)
}

// Door opening/closing
private fun doorOpening(self: SubgameEntity, activator: SubgameEntity?, game: GameExportsImpl) {
    if (self.moveinfo.state == GameFunc.STATE_UP)
        return  // already going up
    if (self.moveinfo.state == GameFunc.STATE_TOP) {
        // reset top wait time
        if (self.moveinfo.wait >= 0)
            self.think.nextTime = game.level.time + self.moveinfo.wait
        return
    }
    if (0 == self.flags and GameDefines.FL_TEAMSLAVE) {
        if (self.moveinfo.sound_start != 0)
            game.gameImports.sound(
                self, Defines.CHAN_NO_PHS_ADD
                        + Defines.CHAN_VOICE, self.moveinfo.sound_start, 1f,
                Defines.ATTN_STATIC.toFloat(), 0f
            )
        self.s.sound = self.moveinfo.sound_middle
    }
    self.moveinfo.state = GameFunc.STATE_UP
    if ("func_door" == self.classname) {
        startMovement(self, self.moveinfo.end_origin!!, doorOpened, game)
    }
    else if ("func_door_rotating" == self.classname)
        GameFunc.AngleMove_Calc(self, doorOpened, game)
    GameUtil.G_UseTargets(self, activator, game)
    doorUseAreaPortals(self, true, game)
}

private val doorOpened = registerThink("door_hit_top") { self: SubgameEntity, game: GameExportsImpl ->
    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (self.moveinfo.sound_end != 0)
            game.gameImports.sound(self, Defines.CHAN_NO_PHS_ADD
                    + Defines.CHAN_VOICE, self.moveinfo.sound_end, 1f,
            Defines.ATTN_STATIC.toFloat(), 0f
        )
        self.s.sound = 0
    }
    self.moveinfo.state = GameFunc.STATE_TOP

    if (self.spawnflags and GameFunc.DOOR_TOGGLE != 0)
        return@registerThink true

    if (self.moveinfo.wait >= 0) {
        self.think.action = doorClosing
        self.think.nextTime = game.level.time + self.moveinfo.wait
    }
    true
}

private val doorClosing = registerThink("door_go_down") { self, game ->
    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (self.moveinfo.sound_start != 0)
            game.gameImports.sound(self, Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
                self.moveinfo.sound_start, 1f, Defines.ATTN_STATIC.toFloat(), 0f)

        self.s.sound = self.moveinfo.sound_middle
    }

    if (self.max_health != 0) {
        self.takedamage = Defines.DAMAGE_YES
        self.health = self.max_health
    }

    self.moveinfo.state = GameFunc.STATE_DOWN
    if ("func_door" == self.classname) {
        startMovement(self, self.moveinfo.start_origin!!, doorClosed, game)
    }
    else if ("func_door_rotating" == self.classname)
        GameFunc.AngleMove_Calc(self, doorClosed, game)
    true
}

private val doorClosed = registerThink("door_hit_bottom") { self, game ->
    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (self.moveinfo.sound_end != 0)
            game.gameImports.sound(self, Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
                self.moveinfo.sound_end, 1f, Defines.ATTN_STATIC.toFloat(), 0f)
        self.s.sound = 0
    }
    self.moveinfo.state = GameFunc.STATE_BOTTOM
    doorUseAreaPortals(self, false, game)
    true
}

private val doorKilled = registerDie("door_killed") { self, inflictor, attacker, damage, point, game ->
    var ent: SubgameEntity? = self.teammaster
    while (ent != null) {
            ent.health = ent.max_health
            ent.takedamage = Defines.DAMAGE_NO
            ent = ent.teamchain
    }
    doorOpenUse.use(self.teammaster, attacker, attacker, game)

}

// secret door related functions

const val SECRET_ALWAYS_SHOOT = 1

private const val SECRET_1ST_LEFT = 2

private const val SECRET_1ST_DOWN = 4

/**
 * QUAKED func_door_secret (0 .5 .8) ? always_shoot 1st_left 1st_down A
 * secret door. Slide back and then to the side.
 *
 * open_once doors never closes 1st_left 1st move is left of arrow 1st_down
 * 1st move is down from arrow always_shoot door is shootebale even if
 * targeted
 *
 * "angle" determines the direction "dmg" damage to inflic when blocked
 * (default 2) "wait" how long to hold in the open position (default 5, -1
 * means hold)
 *
 * TODO: add proper description
 */
val funcDoorSecret = registerThink("func_door_secret") { self, game ->
    self.moveinfo.sound_start = game.gameImports.soundindex("doors/dr1_strt.wav")
    self.moveinfo.sound_middle = game.gameImports.soundindex("doors/dr1_mid.wav")
    self.moveinfo.sound_end = game.gameImports.soundindex("doors/dr1_end.wav")

    self.movetype = GameDefines.MOVETYPE_PUSH
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    self.blocked = doorSecretBlocked
    self.use = doorSecretOpeningBack

    if (null == self.targetname || 0 != self.spawnflags and SECRET_ALWAYS_SHOOT) {
        self.health = 0
        self.takedamage = Defines.DAMAGE_YES
        self.die = doorSecretKilled
    }

    if (0 == self.dmg)
        self.dmg = 2

    if (0f == self.wait)
        self.wait = 5f

    self.moveinfo.accel = 50f
    self.moveinfo.speed = 50f
    self.moveinfo.decel = 50f

    // calculate positions
    val forward = floatArrayOf(0f, 0f, 0f)
    val right = floatArrayOf(0f, 0f, 0f)
    val up = floatArrayOf(0f, 0f, 0f)
    Math3D.AngleVectors(self.s.angles, forward, right, up)
    Math3D.VectorClear(self.s.angles)
    val side = 1.0f - (self.spawnflags and SECRET_1ST_LEFT)
    val width: Float =
        if (self.spawnflags and SECRET_1ST_DOWN != 0)
            abs(Math3D.DotProduct(up, self.size))
        else
            abs(Math3D.DotProduct(right, self.size))

    val length = abs(Math3D.DotProduct(forward, self.size))

    if (self.spawnflags and SECRET_1ST_DOWN != 0)
        VectorMA(self.s.origin, -1 * width, up, self.pos1)
    else
        VectorMA(self.s.origin, side * width, right, self.pos1)
    VectorMA(self.pos1, length, forward, self.pos2)

    if (self.health != 0) {
        self.takedamage = Defines.DAMAGE_YES
        self.die = doorKilled
        self.max_health = self.health
    } else if (self.targetname != null && self.message != null) {
        game.gameImports.soundindex("misc/talk.wav")
        self.touch = doorTouch
    }

    self.classname = "func_door"

    game.gameImports.linkentity(self)

    true

}

private val doorSecretKilled = registerDie("door_secret_die") { self, inflictor, attacker, damage, point, game ->
    self.takedamage = Defines.DAMAGE_NO
    doorSecretOpeningBack.use(self, attacker, attacker, game)
}

private val doorSecretOpeningBack = registerUse("door_secret_use") { self, other, activator, game ->
    // make sure we're not already moving
    // fixme: == zero
    if (!Math3D.VectorEquals(self.s.origin, Globals.vec3_origin))
        return@registerUse

    startMovement(self, self.pos1!!, doorSecretOpeningWait, game)
    doorUseAreaPortals(self, true, game)
}

// Wait between 2 moves
private val doorSecretOpeningWait = registerThink("door_secret_move1") { self, game ->
    self.think.nextTime = game.level.time + 1.0f
    self.think.action = doorSecretOpeningSideways
    true
}

private val doorSecretOpeningSideways = registerThink("door_secret_move2") { self, game ->
    startMovement(self, self.pos2!!, doorSecretOpened, game)
    true
}

private val doorSecretOpened = registerThink("door_secret_move3") { self, game ->
    if (self.wait == -1f) 
        return@registerThink true
    self.think.nextTime = game.level.time + self.wait
    self.think.action = doorSecretClosingSideways
    true
}

private val doorSecretClosingSideways = registerThink("door_secret_move4") { self, game ->
    startMovement(self, self.pos1!!, doorSecretClosingWait, game)
    true
}

private val doorSecretClosingWait = registerThink("door_secret_move5") { self, game ->
    self.think.nextTime = game.level.time + 1.0f
    self.think.action = doorSecretClosingBack
    true
}

private val doorSecretClosingBack = registerThink("door_secret_move6") { self, game ->
    startMovement(self, Globals.vec3_origin!!, doorSecretClosed, game)
    true
}

private val doorSecretClosed = registerThink("door_secret_move7") { self, game ->
    if (self.targetname == null || self.spawnflags and SECRET_ALWAYS_SHOOT != 0) {
        self.health = 0
        self.takedamage = Defines.DAMAGE_YES
    }
    doorUseAreaPortals(self, false, game)
    true
}

private val doorSecretBlocked = registerBlocked("door_secret_blocked") { self, obstacle, game ->
    // if not a monster or player
    if (obstacle.svflags and Defines.SVF_MONSTER == 0 && obstacle.client == null) {
        // give it a chance to go away on its own terms (like gibs)
        GameCombat.T_Damage(
            obstacle, self, self, Globals.vec3_origin,
            obstacle.s.origin, Globals.vec3_origin, 100000, 1, 0,
            GameDefines.MOD_CRUSH, game)
        // if it's still there, nuke it
        if (obstacle.inuse)
            GameMisc.BecomeExplosion1(obstacle, game)
        return@registerBlocked
    }

    if (game.level.time < self.touch_debounce_time)
        return@registerBlocked

    self.touch_debounce_time = game.level.time + 0.5f

    GameCombat.T_Damage(
        obstacle, self, self, Globals.vec3_origin,
        obstacle.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
        GameDefines.MOD_CRUSH, game
    )
}

private fun doorUseAreaPortals(self: SubgameEntity, open: Boolean, game: GameExportsImpl) {
    if (self.target == null)
        return

    var iterator: EdictIterator? = null
    
    while (true) {
        iterator = GameBase.G_Find(iterator, GameBase.findByTargetName, self.target, game)
        if (iterator == null)
            break
        val entity = iterator.o

        if ("func_areaportal".equals(entity?.classname, true)) {
            game.gameImports.SetAreaPortalState(entity!!.style, open)
        }
    }
}

private val doorCalculateMoveSpeed = registerThink("think_calc_movespeed") { self, game ->
    if (self.flags and GameDefines.FL_TEAMSLAVE != 0)
        return@registerThink true // only the team master does this

    // find the smallest distance any member of the team will be moving
    var minDistance: Float = abs(self.moveinfo.distance)
    var entity = self.teamchain
    while (entity != null) {
        val dist = abs(entity.moveinfo.distance)
        if (dist < minDistance)
            minDistance = dist
        entity = entity.teamchain
    }

    val time = minDistance / self.moveinfo.speed

    // adjust speeds so they will all complete at the same time
    var team: SubgameEntity? = self
    while (team != null) {
        val newspeed = abs(team.moveinfo.distance) / time
        val ratio = newspeed / team.moveinfo.speed
        if (team.moveinfo.accel == team.moveinfo.speed)
            team.moveinfo.accel = newspeed
        else
            team.moveinfo.accel *= ratio
        if (team.moveinfo.decel == team.moveinfo.speed)
            team.moveinfo.decel = newspeed
        else
            team.moveinfo.decel *= ratio
        team.moveinfo.speed = newspeed
        team = team.teamchain
    }

    true

}
