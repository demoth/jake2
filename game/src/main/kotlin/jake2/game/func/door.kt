package jake2.game.func

import jake2.game.EdictIterator
import jake2.game.GameBase
import jake2.game.GameBase.G_SetMovedir
import jake2.game.GameCombat
import jake2.game.GameDefines
import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.game.GameMisc
import jake2.game.GameUtil
import jake2.game.adapters.EntThinkAdapter
import jake2.game.adapters.SuperAdapter.Companion.registerBlocked
import jake2.game.adapters.SuperAdapter.Companion.registerDie
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.components.MoveInfo
import jake2.game.components.addComponent
import jake2.game.components.getComponent
import jake2.game.hasSpawnFlag
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.math.Vector3f
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D
import jake2.qcommon.util.Math3D.VectorCopy
import jake2.qcommon.util.Math3D.VectorMA
import kotlin.math.abs
import kotlin.math.floor


const val DOOR_START_OPEN = 1
private const val DOOR_REVERSE = 2
private const val DOOR_CRUSHER = 4
private const val DOOR_NOMONSTER = 8
private const val ANIMATED = 16
const val DOOR_TOGGLE = 32
private const val ANIMATED_FAST = 64
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
fun funcDoor(self: GameEntity, game: GameExportsImpl) {
    // todo: split into entity property initialization & moveinfo
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
    val abs_movedir = floatArrayOf(abs(self.movedir[0]), abs(self.movedir[1]), abs(self.movedir[2]))
    
    // self.movedir.abs() dot self.size - lip
    val distance = abs_movedir[0] * self.size[0] + abs_movedir[1] * self.size[1] + abs_movedir[2] * self.size[2] - self.st.lip
    VectorMA(self.pos1, distance, self.movedir, self.pos2)

    // if it starts open, switch the positions
    if (self.hasSpawnFlag(DOOR_START_OPEN)) {
        VectorCopy(self.pos2, self.s.origin)
        VectorCopy(self.pos1, self.pos2)
        VectorCopy(self.s.origin, self.pos1)
    }
    if (self.health != 0) {
        self.takedamage = Defines.DAMAGE_YES
        self.die = doorKilled
        self.max_health = self.health
    } else if (self.targetname != null && self.message != null) {
        game.gameImports.soundindex("misc/talk.wav")
        self.touch = doorTouch
    }
    
    self.addComponent(MoveInfo(
        state = MovementState.BOTTOM,
        distance = distance,
        sound_start = if (self.sounds != 1) game.gameImports.soundindex("doors/dr1_strt.wav") else 0,
        sound_middle = if (self.sounds != 1) game.gameImports.soundindex("doors/dr1_mid.wav") else 0,
        sound_end = if (self.sounds != 1) game.gameImports.soundindex("doors/dr1_end.wav") else 0,
        speed = self.speed,
        accel = self.accel,
        decel = self.decel,
        wait = self.wait,
        start_origin = Vector3f(self.pos1),
        start_angles = Vector3f(self.s.angles),
        end_origin = Vector3f(self.pos2),
        end_angles = Vector3f(self.s.angles)
    ))

    if (self.hasSpawnFlag(ANIMATED))
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALL
    if (self.hasSpawnFlag(ANIMATED_FAST))
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
fun funcDoorRotating(self: GameEntity, game: GameExportsImpl) {
    Math3D.VectorClear(self.s.angles)

    // set the axis of rotation
    Math3D.VectorClear(self.movedir)
    if (self.hasSpawnFlag(DOOR_X_AXIS))
        self.movedir[2] = 1.0f
    else if (self.hasSpawnFlag(DOOR_Y_AXIS))
        self.movedir[0] = 1.0f
    else  // Z_AXIS
        self.movedir[1] = 1.0f

    // check for reverse rotation
    if (self.hasSpawnFlag(DOOR_REVERSE))
        Math3D.VectorNegate(self.movedir, self.movedir)

    if (0 == self.st.distance) {
        game.gameImports.dprintf("${self.classname} at ${Lib.vtos(self.s.origin)} with no distance set")
        self.st.distance = 90
    }

    VectorCopy(self.s.angles, self.pos1)
    VectorMA(self.s.angles, self.st.distance.toFloat(), self.movedir, self.pos2)

    self.movetype = GameDefines.MOVETYPE_PUSH
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    self.blocked = doorBlocked
    self.use = doorOpenUse

    if (self.speed == 0f)
        self.speed = 100f
    if (self.accel == 0f)
        self.accel = self.speed
    if (self.decel == 0f)
        self.decel = self.speed

    if (self.wait == 0f)
        self.wait = 3f
    if (self.dmg == 0)
        self.dmg = 2


    // if it starts open, switch the positions
    if (self.hasSpawnFlag(DOOR_START_OPEN)) {
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
    self.addComponent(MoveInfo(
        state = MovementState.BOTTOM,
        distance = self.st.distance.toFloat(),
        sound_start = if (self.sounds != 1) game.gameImports.soundindex("doors/dr1_strt.wav") else 0,
        sound_middle = if (self.sounds != 1) game.gameImports.soundindex("doors/dr1_mid.wav") else 0,
        sound_end = if (self.sounds != 1) game.gameImports.soundindex("doors/dr1_end.wav") else 0,
        speed = self.speed,
        accel = self.accel,
        decel = self.decel,
        wait = self.wait,
        start_angles = Vector3f(self.pos1),
        end_angles = Vector3f(self.pos2),
        // The rotating door does not move, only rotates
        start_origin = Vector3f(self.s.origin),
        end_origin = Vector3f(self.s.origin),
        ))


    if (self.hasSpawnFlag(ANIMATED))
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

    if (self.hasSpawnFlag(DOOR_CRUSHER))
        return@registerBlocked

    // if a door has a negative wait, it would never come back if
    // blocked, so let it just squash the object to death real fast
    val moveInfo: MoveInfo = self.getComponent()!!

    if (moveInfo.wait >= 0) {
        var team: GameEntity?
        if (moveInfo.state == MovementState.DOWN) {
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

    if (self.hasSpawnFlag(DOOR_TOGGLE)) {
        val moveInfo: MoveInfo = self.getComponent()!!
        if (moveInfo.state == MovementState.UP || moveInfo.state == MovementState.TOP) {
            // trigger all paired doors
            var team: GameEntity? = self
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
    var team: GameEntity? = self
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

    var team: GameEntity? = ent.teamchain
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

    if (ent.hasSpawnFlag(DOOR_START_OPEN))
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

    if (other.svflags and Defines.SVF_MONSTER != 0 && self.owner.hasSpawnFlag(DOOR_NOMONSTER))
        return@registerTouch

    if (game.level.time < self.touch_debounce_time)
        return@registerTouch

    self.touch_debounce_time = game.level.time + 1.0f

    doorOpenUse.use(self.owner, other, other, game)
}

// Door opening/closing
private fun doorOpening(self: GameEntity, activator: GameEntity?, game: GameExportsImpl) {
    val moveInfo: MoveInfo = self.getComponent()!!

    if (moveInfo.state == MovementState.UP)
        return  // already going up
    if (moveInfo.state == MovementState.TOP) {
        // reset top wait time
        if (moveInfo.wait >= 0)
            self.think.nextTime = game.level.time + moveInfo.wait
        return
    }
    if (0 == self.flags and GameDefines.FL_TEAMSLAVE) {
        if (moveInfo.sound_start != 0)
            game.gameImports.sound(
                self, Defines.CHAN_NO_PHS_ADD
                        + Defines.CHAN_VOICE, moveInfo.sound_start, 1f,
                Defines.ATTN_STATIC.toFloat(), 0f
            )
        self.s.sound = moveInfo.sound_middle
    }
    moveInfo.state = MovementState.UP
    if ("func_door" == self.classname) {
        startMovement(self, moveInfo.end_origin!!, doorOpened, game)
    } else if ("func_door_rotating" == self.classname) {
        calculateRotation(self, doorOpened, game)
    }
    GameUtil.G_UseTargets(self, activator, game)
    doorUseAreaPortals(self, true, game)
}

private val doorOpened = registerThink("door_hit_top") { self: GameEntity, game: GameExportsImpl ->
    val moveInfo: MoveInfo = self.getComponent()!!
    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (moveInfo.sound_end != 0)
            game.gameImports.sound(self, Defines.CHAN_NO_PHS_ADD
                    + Defines.CHAN_VOICE, moveInfo.sound_end, 1f,
            Defines.ATTN_STATIC.toFloat(), 0f
        )
        self.s.sound = 0
    }
    moveInfo.state = MovementState.TOP

    if (self.hasSpawnFlag(DOOR_TOGGLE))
        return@registerThink true

    if (moveInfo.wait >= 0) {
        self.think.action = doorClosing
        self.think.nextTime = game.level.time + moveInfo.wait
    }
    true
}

private val doorClosing = registerThink("door_go_down") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (moveInfo.sound_start != 0)
            game.gameImports.sound(self, Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
                moveInfo.sound_start, 1f, Defines.ATTN_STATIC.toFloat(), 0f)

        self.s.sound = moveInfo.sound_middle
    }

    if (self.max_health != 0) {
        self.takedamage = Defines.DAMAGE_YES
        self.health = self.max_health
    }

    moveInfo.state = MovementState.DOWN
    if ("func_door" == self.classname) {
        startMovement(self, moveInfo.start_origin, doorClosed, game)
    } else if ("func_door_rotating" == self.classname) {
        calculateRotation(self, doorClosed, game)
    }
    true
}

private val doorClosed = registerThink("door_hit_bottom") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (moveInfo.sound_end != 0)
            game.gameImports.sound(self, Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
                moveInfo.sound_end, 1f, Defines.ATTN_STATIC.toFloat(), 0f)
        self.s.sound = 0
    }
    moveInfo.state = MovementState.BOTTOM
    doorUseAreaPortals(self, false, game)
    true
}

private val doorKilled = registerDie("door_killed") { self, inflictor, attacker, damage, point, game ->
    var ent: GameEntity? = self.teammaster
    while (ent != null) {
            ent.health = ent.max_health
            ent.takedamage = Defines.DAMAGE_NO
            ent = ent.teamchain
    }
    doorOpenUse.use(self.teammaster, attacker, attacker, game)

}

// secret door related functions

private const val SECRET_ALWAYS_SHOOT = 1

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
fun funcDoorSecret(self: GameEntity, game: GameExportsImpl) {

    self.movetype = GameDefines.MOVETYPE_PUSH
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    self.blocked = doorSecretBlocked
    self.use = doorSecretOpeningBack

    if (self.targetname == null || self.hasSpawnFlag(SECRET_ALWAYS_SHOOT)) {
        self.health = 0
        self.takedamage = Defines.DAMAGE_YES
        self.die = doorSecretKilled
    }

    if (self.dmg == 0)
        self.dmg = 2

    if (self.wait == 0f)
        self.wait = 5f

    self.addComponent(MoveInfo(
        accel = 50f,
        speed = 50f,
        decel = 50f,
        sound_start = game.gameImports.soundindex("doors/dr1_strt.wav"),
        sound_middle = game.gameImports.soundindex("doors/dr1_mid.wav"),
        sound_end = game.gameImports.soundindex("doors/dr1_end.wav"),
    ))

    // calculate positions
    val forward = floatArrayOf(0f, 0f, 0f)
    val right = floatArrayOf(0f, 0f, 0f)
    val up = floatArrayOf(0f, 0f, 0f)
    Math3D.AngleVectors(self.s.angles, forward, right, up)
    Math3D.VectorClear(self.s.angles)
    val side = if (self.hasSpawnFlag(SECRET_1ST_LEFT)) -1.0f else 1.0f
    val width: Float =
        if (self.hasSpawnFlag(SECRET_1ST_DOWN))
            abs(Math3D.DotProduct(up, self.size))
        else
            abs(Math3D.DotProduct(right, self.size))

    val length = abs(Math3D.DotProduct(forward, self.size))

    if (self.hasSpawnFlag(SECRET_1ST_DOWN))
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

    startMovement(self, Vector3f(self.pos1), doorSecretOpeningWait, game)
    doorUseAreaPortals(self, true, game)
}

// Wait between 2 moves
private val doorSecretOpeningWait = registerThink("door_secret_move1") { self, game ->
    self.think.nextTime = game.level.time + 1.0f
    self.think.action = doorSecretOpeningSideways
    true
}

private val doorSecretOpeningSideways = registerThink("door_secret_move2") { self, game ->
    startMovement(self, Vector3f(self.pos2), doorSecretOpened, game)
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
    startMovement(self, Vector3f(self.pos1), doorSecretClosingWait, game)
    true
}

private val doorSecretClosingWait = registerThink("door_secret_move5") { self, game ->
    self.think.nextTime = game.level.time + 1.0f
    self.think.action = doorSecretClosingBack
    true
}

private val doorSecretClosingBack = registerThink("door_secret_move6") { self, game ->
    startMovement(self, Vector3f.zero, doorSecretClosed, game)
    true
}

private val doorSecretClosed = registerThink("door_secret_move7") { self, game ->
    if (self.targetname == null || self.hasSpawnFlag(SECRET_ALWAYS_SHOOT)) {
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
            GameDefines.MOD_CRUSH, game
        )
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

private fun doorUseAreaPortals(self: GameEntity, open: Boolean, game: GameExportsImpl) {
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

    val moveInfo: MoveInfo = self.getComponent()!!

    // find the smallest distance any member of the team will be moving
    var minDistance: Float = abs(moveInfo.distance)
    var entity = self.teamchain
    while (entity != null) {
        val dist = abs(entity.getComponent<MoveInfo>()!!.distance)
        if (dist < minDistance)
            minDistance = dist
        entity = entity.teamchain
    }

    val time = minDistance / moveInfo.speed

    // adjust speeds so they will all complete at the same time
    var team: GameEntity? = self
    while (team != null) {
        val teamMoveInfo: MoveInfo = team.getComponent()!!
        val newspeed = abs(teamMoveInfo.distance) / time
        val ratio = newspeed / teamMoveInfo.speed
        if (teamMoveInfo.accel == teamMoveInfo.speed)
            teamMoveInfo.accel = newspeed
        else
            teamMoveInfo.accel *= ratio
        if (teamMoveInfo.decel == teamMoveInfo.speed)
            teamMoveInfo.decel = newspeed
        else
            teamMoveInfo.decel *= ratio
        teamMoveInfo.speed = newspeed
        team = team.teamchain
    }

    true

}

// Rotating door routines

// AngleMove_Calc
private fun calculateRotation(self: GameEntity, endFunction: EntThinkAdapter?, game: GameExportsImpl) {
    Math3D.VectorClear(self.avelocity)
    self.getComponent<MoveInfo>()!!.endfunc = endFunction
    val teamMaster = self.flags and GameDefines.FL_TEAMSLAVE == 0
    if (game.level.current_entity === (if (teamMaster) self else self.teammaster)) {
        doorRotatingBegin.think(self, game)
    } else {
        self.think.nextTime = game.level.time + Defines.FRAMETIME
        self.think.action = doorRotatingBegin
    }
}

private val doorRotatingBegin = registerThink("angle_move_begin") { self, game ->
    // set destdelta to the vector needed to move
    val moveInfo: MoveInfo = self.getComponent()!!
    val destdelta = if (moveInfo.state == MovementState.UP)
        moveInfo.end_angles - Vector3f(self.s.angles)
    else
        moveInfo.start_angles - Vector3f(self.s.angles)

    // divide by speed to get time to reach dest
    val traveltime = destdelta.length() / moveInfo.speed

    if (traveltime < Defines.FRAMETIME) {
        doorRotatingFinal.think(self, game)
        return@registerThink true
    }


    // scale the destdelta vector by the time spent traveling to get velocity
    Math3D.VectorScale(destdelta.toArray(), 1.0f / traveltime, self.avelocity)

    // set nextthink to trigger a think when dest is reached
    val frames = floor((traveltime / Defines.FRAMETIME))
    self.think.nextTime = game.level.time + frames * Defines.FRAMETIME
    self.think.action = doorRotatingFinal
    true
}

private val doorRotatingFinal = registerThink("angle_move_final") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    val move = if (moveInfo.state == MovementState.UP)
        // Math3D.VectorSubtract(moveInfo.end_angles, self.s.angles, move)
            moveInfo.end_angles - Vector3f(self.s.angles)
        else
        // Math3D.VectorSubtract(moveInfo.start_angles, self.s.angles, move)
            moveInfo.start_angles - Vector3f(self.s.angles)

    // if (Math3D.VectorEquals(move, Globals.vec3_origin)) {
    if (move == Vector3f.zero) {
        doorRotatingDone.think(self, game)
        return@registerThink true
    }

    Math3D.VectorScale(move.toArray(), 1.0f / Defines.FRAMETIME, self.avelocity)

    self.think.action = doorRotatingDone
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    true
}

private val doorRotatingDone = registerThink("angle_move_final") { self, game ->
    Math3D.VectorClear(self.avelocity)
    self.getComponent<MoveInfo>()!!.endfunc!!.think(self, game)
    true
}
