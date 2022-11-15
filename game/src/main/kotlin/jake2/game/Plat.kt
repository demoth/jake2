package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerBlocked
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.util.Math3D

/**
 * QUAKED func_plat (0 .5 .8) ? PLAT_LOW_TRIGGER speed default 150
 *
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
val plat = registerThink("func_plat") { self, game ->
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


    if (self.targetname != null) {
        self.moveinfo.state = GameFunc.STATE_UP
    } else {
        Math3D.VectorCopy(self.pos2, self.s.origin)
        game.gameImports.linkentity(self)
        self.moveinfo.state = GameFunc.STATE_BOTTOM
    }

    self.moveinfo.speed = self.speed
    self.moveinfo.accel = self.accel
    self.moveinfo.decel = self.decel
    self.moveinfo.wait = self.wait
    Math3D.VectorCopy(self.pos1, self.moveinfo.start_origin)
    Math3D.VectorCopy(self.s.angles, self.moveinfo.start_angles)
    Math3D.VectorCopy(self.pos2, self.moveinfo.end_origin)
    Math3D.VectorCopy(self.s.angles, self.moveinfo.end_angles)

    self.moveinfo.sound_start = game.gameImports.soundindex("plats/pt1_strt.wav")
    self.moveinfo.sound_middle = game.gameImports.soundindex("plats/pt1_mid.wav")
    self.moveinfo.sound_end = game.gameImports.soundindex("plats/pt1_end.wav")

    true
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

    if (self.moveinfo.state == GameFunc.STATE_UP)
        GameFunc.plat_go_down.think(self, gameExports) 
    else if (self.moveinfo.state == GameFunc.STATE_DOWN)
        GameFunc.plat_go_up(self, gameExports)

}

private val platUse = registerUse("use_plat") { self, other, activator, game ->
    // already down fixme: use a better check (like moveinfo.state ?)
    if (self.think.action != null)
        return@registerUse

    GameFunc.plat_go_down.think(self, game)

}

private fun spawnInsideTrigger(plat: SubgameEntity, gameExports: GameExportsImpl) {
    val trigger = gameExports.G_Spawn()
    trigger.touch = GameFunc.Touch_Plat_Center
    trigger.movetype = GameDefines.MOVETYPE_NONE
    trigger.solid = Defines.SOLID_TRIGGER
    trigger.enemy = plat
    val tmin = floatArrayOf(plat.mins[0] + 25, plat.mins[1] + 25, plat.mins[2])
    val tmax = floatArrayOf(plat.maxs[0] - 25, plat.maxs[1] - 25, plat.maxs[2] + 8)

    tmin[2] = tmax[2] - (plat.pos1[2] - plat.pos2[2] + plat.st.lip)

    if (plat.spawnflags and GameFunc.PLAT_LOW_TRIGGER != 0) {
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