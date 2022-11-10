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
val funcDoor = registerThink("func_door") { ent, gameExports ->
    // todo: split into entity property initialization & moveinfo
    val abs_movedir = floatArrayOf(0f, 0f, 0f)
    if (ent.sounds != 1) {
        ent.moveinfo.sound_start = gameExports.gameImports.soundindex("doors/dr1_strt.wav")
        ent.moveinfo.sound_middle = gameExports.gameImports.soundindex("doors/dr1_mid.wav")
        ent.moveinfo.sound_end = gameExports.gameImports.soundindex("doors/dr1_end.wav")
    }
    G_SetMovedir(ent.s.angles, ent.movedir)
    ent.movetype = GameDefines.MOVETYPE_PUSH
    ent.solid = jake2.qcommon.Defines.SOLID_BSP
    gameExports.gameImports.setmodel(ent, ent.model)
    ent.blocked = GameFunc.door_blocked
    ent.use = GameFunc.door_use
    if (ent.speed == 0f)
        ent.speed = 100f
    if (gameExports.gameCvars.deathmatch.value != 0f)
        ent.speed *= 2f
    if (ent.accel == 0f)
        ent.accel = ent.speed
    if (ent.decel == 0f)
        ent.decel = ent.speed
    if (ent.wait == 0f)
        ent.wait = 3f
    if (ent.st.lip == 0)
        ent.st.lip = 8
    if (ent.dmg == 0)
        ent.dmg = 2

    // calculate second position
    VectorCopy(ent.s.origin, ent.pos1)
    abs_movedir[0] = abs(ent.movedir[0])
    abs_movedir[1] = abs(ent.movedir[1])
    abs_movedir[2] = abs(ent.movedir[2])
    ent.moveinfo.distance =
        abs_movedir[0] * ent.size[0] + abs_movedir[1] * ent.size[1] + (abs_movedir[2] * ent.size[2]) - ent.st.lip
    VectorMA(ent.pos1, ent.moveinfo.distance, ent.movedir, ent.pos2)

    // if it starts open, switch the positions
    if (ent.spawnflags and GameFunc.DOOR_START_OPEN != 0) {
        VectorCopy(ent.pos2, ent.s.origin)
        VectorCopy(ent.pos1, ent.pos2)
        VectorCopy(ent.s.origin, ent.pos1)
    }
    ent.moveinfo.state = GameFunc.STATE_BOTTOM
    if (ent.health != 0) {
        ent.takedamage = jake2.qcommon.Defines.DAMAGE_YES
        ent.die = GameFunc.door_killed
        ent.max_health = ent.health
    } else if (ent.targetname != null && ent.message != null) {
        gameExports.gameImports.soundindex("misc/talk.wav")
        ent.touch = GameFunc.door_touch
    }
    ent.moveinfo.speed = ent.speed
    ent.moveinfo.accel = ent.accel
    ent.moveinfo.decel = ent.decel
    ent.moveinfo.wait = ent.wait
    VectorCopy(ent.pos1, ent.moveinfo.start_origin)
    VectorCopy(ent.s.angles, ent.moveinfo.start_angles)
    VectorCopy(ent.pos2, ent.moveinfo.end_origin)
    VectorCopy(ent.s.angles, ent.moveinfo.end_angles)
    if (ent.spawnflags and 16 != 0)
        ent.s.effects = ent.s.effects or jake2.qcommon.Defines.EF_ANIM_ALL
    if (ent.spawnflags and 64 != 0)
        ent.s.effects = ent.s.effects or jake2.qcommon.Defines.EF_ANIM_ALLFAST

    // to simplify logic elsewhere, make non-teamed doors into a team of one
    if (null == ent.team)
        ent.teammaster = ent
    gameExports.gameImports.linkentity(ent)
    ent.think.nextTime = gameExports.level.time + jake2.qcommon.Defines.FRAMETIME
    if (ent.health != 0 || ent.targetname != null)
        ent.think.action = GameFunc.Think_CalcMoveSpeed
    else
        ent.think.action = GameFunc.Think_SpawnDoorTrigger
    true
}

val funcDoorRotating = registerThink("func_door_rotating") { ent, gameExports ->
    Math3D.VectorClear(ent.s.angles)

    // set the axis of rotation
    Math3D.VectorClear(ent.movedir)
    if (ent.spawnflags and GameFunc.DOOR_X_AXIS != 0) ent.movedir[2] =
        1.0f else if (ent.spawnflags and GameFunc.DOOR_Y_AXIS != 0) ent.movedir[0] = 1.0f else  // Z_AXIS
        ent.movedir[1] = 1.0f

    // check for reverse rotation
    if (ent.spawnflags and GameFunc.DOOR_REVERSE != 0) Math3D.VectorNegate(ent.movedir, ent.movedir)

    if (0 == ent.st.distance) {
        gameExports.gameImports.dprintf("${ent.classname} at ${Lib.vtos(ent.s.origin)} with no distance set")
        ent.st.distance = 90
    }

    VectorCopy(ent.s.angles, ent.pos1)
    VectorMA(
        ent.s.angles, ent.st.distance.toFloat(), ent.movedir,
        ent.pos2
    )
    ent.moveinfo.distance = ent.st.distance.toFloat()

    ent.movetype = GameDefines.MOVETYPE_PUSH
    ent.solid = Defines.SOLID_BSP
    gameExports.gameImports.setmodel(ent, ent.model)

    ent.blocked = GameFunc.door_blocked
    ent.use = GameFunc.door_use

    if (0f == ent.speed) ent.speed = 100f
    if (0f == ent.accel) ent.accel = ent.speed
    if (0f == ent.decel) ent.decel = ent.speed

    if (0f == ent.wait) ent.wait = 3f
    if (0 == ent.dmg) ent.dmg = 2

    if (ent.sounds != 1) {
        ent.moveinfo.sound_start = gameExports.gameImports
            .soundindex("doors/dr1_strt.wav")
        ent.moveinfo.sound_middle = gameExports.gameImports
            .soundindex("doors/dr1_mid.wav")
        ent.moveinfo.sound_end = gameExports.gameImports
            .soundindex("doors/dr1_end.wav")
    }

    // if it starts open, switch the positions
    if (ent.spawnflags and GameFunc.DOOR_START_OPEN != 0) {
        VectorCopy(ent.pos2, ent.s.angles)
        VectorCopy(ent.pos1, ent.pos2)
        VectorCopy(ent.s.angles, ent.pos1)
        Math3D.VectorNegate(ent.movedir, ent.movedir)
    }

    if (ent.health != 0) {
        ent.takedamage = Defines.DAMAGE_YES
        ent.die = GameFunc.door_killed
        ent.max_health = ent.health
    }

    if (ent.targetname != null && ent.message != null) {
        gameExports.gameImports.soundindex("misc/talk.wav")
        ent.touch = GameFunc.door_touch
    }

    ent.moveinfo.state = GameFunc.STATE_BOTTOM
    ent.moveinfo.speed = ent.speed
    ent.moveinfo.accel = ent.accel
    ent.moveinfo.decel = ent.decel
    ent.moveinfo.wait = ent.wait
    VectorCopy(ent.s.origin, ent.moveinfo.start_origin)
    VectorCopy(ent.pos1, ent.moveinfo.start_angles)
    VectorCopy(ent.s.origin, ent.moveinfo.end_origin)
    VectorCopy(ent.pos2, ent.moveinfo.end_angles)

    if (ent.spawnflags and 16 != 0) ent.s.effects = ent.s.effects or Defines.EF_ANIM_ALL

    // to simplify logic elsewhere, make non-teamed doors into a team of one
    if (ent.team == null) ent.teammaster = ent

    gameExports.gameImports.linkentity(ent)

    ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME
    if (ent.health != 0 || ent.targetname != null)
        ent.think.action = Think_CalcMoveSpeed
    else ent.think.action = Think_SpawnDoorTrigger
    true

}
