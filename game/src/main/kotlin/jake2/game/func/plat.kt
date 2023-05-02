package jake2.game.func

import jake2.game.GameCombat
import jake2.game.GameDefines
import jake2.game.GameExportsImpl
import jake2.game.GameMisc
import jake2.game.SubgameEntity
import jake2.game.adapters.SuperAdapter.Companion.registerBlocked
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
import jake2.qcommon.util.Math3D

/*
 * PLATS
 *
 * movement options:
 *
 * linear smooth start, hard stop smooth start, smooth stop
 *
 * start end acceleration speed deceleration begin sound end sound target
 * fired when reaching end wait at end
 *
 * object characteristics that use move segments
 * --------------------------------------------- 
 * movetype_push, or
 * movetype_stop action when touched action when blocked action when used
 * disabled? auto trigger spawning
 *
 */

private const val PLAT_LOW_TRIGGER = 1

/**
 * QUAKED func_plat (0 .5 .8) ? PLAT_LOW_TRIGGER speed default 150
 * Plat or elevator.
 * Plats are always drawn in the extended position, so they will light
 * correctly.
 *
 * If the plat is the target of another trigger or button, it will start out
 * disabled in the extended position until it is trigger, when it will lower
 * and become a normal plat.
 *
 * "speed" overrides default 200. "accel" overrides default 500 "lip"
 * overrides default 8 pixel lip
 *
 * If the "height" key is set, that will determine the amount the plat
 * moves, instead of being implicitly determoveinfoned by the model's
 * height.
 *
 * Set "sounds" to one of the following: 1) base fast 2) chain slow
 */
fun funcPlat(self: SubgameEntity, game: GameExportsImpl) {
    Math3D.VectorClear(self.s.angles)
    self.solid = Defines.SOLID_BSP
    self.movetype = GameDefines.MOVETYPE_PUSH

    game.gameImports.setmodel(self, self.model)

    self.blocked = platBlocked

    if (self.speed == 0f) 
        self.speed = 20f 
    else
        self.speed *= 0.1f

    if (self.accel == 0f) 
        self.accel = 5f
    else
        self.accel *= 0.1f

    if (self.decel == 0f) 
        self.decel = 5f
    else 
        self.decel *= 0.1f

    if (self.dmg == 0)    
        self.dmg = 2
    if (self.st.lip == 0) 
        self.st.lip = 8


    // pos1 is the top position, pos2 is the bottom
    Math3D.VectorCopy(self.s.origin, self.pos1)
    Math3D.VectorCopy(self.s.origin, self.pos2)
    if (self.st.height != 0)
        self.pos2[2] -= self.st.height.toFloat()
    else
        self.pos2[2] -= self.maxs[2] - self.mins[2] - self.st.lip

    self.use = platUse

    // the "start moving" trigger
    spawnInsideTrigger(self, game)

    val targeted = self.targetname != null

    if (!targeted) {
        Math3D.VectorCopy(self.pos2, self.s.origin)
        game.gameImports.linkentity(self)
    }
    
    self.addComponent(MoveInfo(
        state = if (targeted) MovementState.UP else MovementState.BOTTOM,
        speed = self.speed,
        accel = self.accel,
        decel = self.decel,
        wait = self.wait,
        start_origin = Vector3f(self.pos1),
        start_angles = Vector3f(self.s.angles),
        end_origin = Vector3f(self.pos2),
        end_angles = Vector3f(self.s.angles),
        sound_start = game.gameImports.soundindex("plats/pt1_strt.wav"),
        sound_middle = game.gameImports.soundindex("plats/pt1_mid.wav"),
        sound_end = game.gameImports.soundindex("plats/pt1_end.wav")       
    ))
}

private val platBlocked = registerBlocked("plat_blocked") { self, obstacle, gameExports ->
    if (obstacle.svflags and Defines.SVF_MONSTER == 0 && obstacle.client == null) {
        // give it a chance to go away on it's own terms (like gibs)
        GameCombat.T_Damage(
            obstacle, self, self, Globals.vec3_origin,
            obstacle.s.origin, Globals.vec3_origin, 100000, 1, 0,
            GameDefines.MOD_CRUSH, gameExports
        )
        // if it's still there, nuke it
        if (obstacle.inuse)
            GameMisc.BecomeExplosion1(obstacle, gameExports)
        return@registerBlocked
    }

    GameCombat.T_Damage(
        obstacle, self, self, Globals.vec3_origin,
        obstacle.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
        GameDefines.MOD_CRUSH, gameExports
    )

    val moveInfo: MoveInfo = self.getComponent()!!

    if (moveInfo.state == MovementState.UP)
        platGoDown.think(self, gameExports)
    else if (moveInfo.state == MovementState.DOWN)
        platGoUp(self, gameExports)

}

private val platUse = registerUse("use_plat") { self, other, activator, game ->
    // already down fixme: use a better check (like moveinfo.state ?)
    if (self.think.action != null)
        return@registerUse

    platGoDown.think(self, game)

}

private fun spawnInsideTrigger(plat: SubgameEntity, gameExports: GameExportsImpl) {
    val trigger = gameExports.G_Spawn()
    trigger.touch = platTriggerTouch
    trigger.movetype = GameDefines.MOVETYPE_NONE
    trigger.solid = Defines.SOLID_TRIGGER
    trigger.enemy = plat
    val tmin = floatArrayOf(plat.mins[0] + 25, plat.mins[1] + 25, plat.mins[2])
    val tmax = floatArrayOf(plat.maxs[0] - 25, plat.maxs[1] - 25, plat.maxs[2] + 8)

    tmin[2] = tmax[2] - (plat.pos1[2] - plat.pos2[2] + plat.st.lip)

    if (plat.hasSpawnFlag(PLAT_LOW_TRIGGER)) {
        tmax[2] = tmin[2] + 8
    }
    if (tmax[0] - tmin[0] <= 0) {
        tmin[0] = (plat.mins[0] + plat.maxs[0]) * 0.5f
        tmax[0] = tmin[0] + 1
    }
    if (tmax[1] - tmin[1] <= 0) {
        tmin[1] = (plat.mins[1] + plat.maxs[1]) * 0.5f
        tmax[1] = tmin[1] + 1
    }
    Math3D.VectorCopy(tmin, trigger.mins)
    Math3D.VectorCopy(tmax, trigger.maxs)
    gameExports.gameImports.linkentity(trigger)
}

private val platTriggerTouch = registerTouch("touch_plat_center") { self, other, plane, surf, game ->
    if (other.client == null)
        return@registerTouch

    if (other.health <= 0)
        return@registerTouch

    // now point at the plat, not the trigger
    val plat = self.enemy
    val moveInfo: MoveInfo = self.enemy.getComponent()!!
    if (moveInfo.state == MovementState.BOTTOM)
        platGoUp(plat, game)
    else if (moveInfo.state == MovementState.TOP) {
        // the player is still on the plat, so delay going down
        plat.think.nextTime = game.level.time + 1 
    }
}
private fun platGoUp(self: SubgameEntity, game: GameExportsImpl) {
    val moveInfo: MoveInfo = self.getComponent()!!

    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (moveInfo.sound_start != 0)
            game.gameImports.sound(
            self, Defines.CHAN_NO_PHS_ADD
                    + Defines.CHAN_VOICE, moveInfo.sound_start, 1f,
            Defines.ATTN_STATIC.toFloat(), 0f
        )
        self.s.sound = moveInfo.sound_middle
    }
    moveInfo.state = MovementState.UP
    startMovement(self, moveInfo.start_origin, platHitTop, game)
}

private val platHitTop = registerThink("plat_hit_top") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!

    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (moveInfo.sound_end != 0)
            game.gameImports.sound(
            self, Defines.CHAN_NO_PHS_ADD
                    + Defines.CHAN_VOICE, moveInfo.sound_end, 1f,
            Defines.ATTN_STATIC.toFloat(), 0f
        )
        self.s.sound = 0
    }
    moveInfo.state = MovementState.TOP

    self.think.action = platGoDown
    self.think.nextTime = game.level.time + 3
    true
}

private val platGoDown = registerThink("plat_go_down") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (moveInfo.sound_start != 0)
            game.gameImports.sound(
            self, Defines.CHAN_NO_PHS_ADD
                    + Defines.CHAN_VOICE, moveInfo.sound_start, 1f,
            Defines.ATTN_STATIC.toFloat(), 0f
        )
        self.s.sound = moveInfo.sound_middle
    }
    moveInfo.state = MovementState.DOWN
    startMovement(self, moveInfo.end_origin, platHitBottom, game)
    true
}

private val platHitBottom = registerThink("plat_hit_bottom") { self, game ->
    val moveInfo: MoveInfo = self.getComponent()!!
    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (moveInfo.sound_end != 0)
            game.gameImports.sound(
            self, Defines.CHAN_NO_PHS_ADD
                    + Defines.CHAN_VOICE, moveInfo.sound_end, 1f,
            Defines.ATTN_STATIC.toFloat(), 0f
        )
        self.s.sound = 0
    }
    moveInfo.state = MovementState.BOTTOM
    true
}
