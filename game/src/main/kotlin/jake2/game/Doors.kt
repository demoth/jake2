package jake2.game

import jake2.game.GameBase.G_SetMovedir
import jake2.game.GameFunc.Think_CalcMoveSpeed
import jake2.game.GameFunc.Think_SpawnDoorTrigger
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.qcommon.Defines
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D
import jake2.qcommon.util.Math3D.VectorCopy
import jake2.qcommon.util.Math3D.VectorMA
import kotlin.math.abs

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
    self.solid = jake2.qcommon.Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)
    self.blocked = GameFunc.door_blocked
    self.use = GameFunc.door_use
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
    if (self.spawnflags and GameFunc.DOOR_START_OPEN != 0) {
        VectorCopy(self.pos2, self.s.origin)
        VectorCopy(self.pos1, self.pos2)
        VectorCopy(self.s.origin, self.pos1)
    }
    self.moveinfo.state = GameFunc.STATE_BOTTOM
    if (self.health != 0) {
        self.takedamage = jake2.qcommon.Defines.DAMAGE_YES
        self.die = GameFunc.door_killed
        self.max_health = self.health
    } else if (self.targetname != null && self.message != null) {
        game.gameImports.soundindex("misc/talk.wav")
        self.touch = GameFunc.door_touch
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
        self.s.effects = self.s.effects or jake2.qcommon.Defines.EF_ANIM_ALL
    if (self.spawnflags and 64 != 0)
        self.s.effects = self.s.effects or jake2.qcommon.Defines.EF_ANIM_ALLFAST

    // to simplify logic elsewhere, make non-teamed doors into a team of one
    if (null == self.team)
        self.teammaster = self
    game.gameImports.linkentity(self)
    self.think.nextTime = game.level.time + jake2.qcommon.Defines.FRAMETIME
    if (self.health != 0 || self.targetname != null)
        self.think.action = GameFunc.Think_CalcMoveSpeed
    else
        self.think.action = GameFunc.Think_SpawnDoorTrigger
    true
}

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
    if (self.spawnflags and GameFunc.DOOR_X_AXIS != 0)
        self.movedir[2] = 1.0f
    else if (self.spawnflags and GameFunc.DOOR_Y_AXIS != 0)
        self.movedir[0] = 1.0f
    else  // Z_AXIS
        self.movedir[1] = 1.0f

    // check for reverse rotation
    if (self.spawnflags and GameFunc.DOOR_REVERSE != 0)
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

    self.blocked = GameFunc.door_blocked
    self.use = GameFunc.door_use

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
    if (self.spawnflags and GameFunc.DOOR_START_OPEN != 0) {
        VectorCopy(self.pos2, self.s.angles)
        VectorCopy(self.pos1, self.pos2)
        VectorCopy(self.s.angles, self.pos1)
        Math3D.VectorNegate(self.movedir, self.movedir)
    }

    if (self.health != 0) {
        self.takedamage = Defines.DAMAGE_YES
        self.die = GameFunc.door_killed
        self.max_health = self.health
    }

    if (self.targetname != null && self.message != null) {
        game.gameImports.soundindex("misc/talk.wav")
        self.touch = GameFunc.door_touch
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
        self.think.action = Think_CalcMoveSpeed
    else
        self.think.action = Think_SpawnDoorTrigger
    true

}


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

    self.blocked = GameFunc.door_secret_blocked
    self.use = GameFunc.door_secret_use

    if (null == self.targetname || 0 != self.spawnflags and SECRET_ALWAYS_SHOOT) {
        self.health = 0
        self.takedamage = Defines.DAMAGE_YES
        self.die = GameFunc.door_secret_die
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
        self.die = GameFunc.door_killed
        self.max_health = self.health
    } else if (self.targetname != null && self.message != null) {
        game.gameImports.soundindex("misc/talk.wav")
        self.touch = GameFunc.door_touch
    }

    self.classname = "func_door"

    game.gameImports.linkentity(self)

    true

}
