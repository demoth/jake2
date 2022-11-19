package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerBlocked
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D

/*
 * QUAKED func_train (0 .5 .8) ? START_ON TOGGLE BLOCK_STOPS Trains are
 * moving platforms that players can ride. The targets origin specifies the
 * min point of the train at each corner. The train spawns at the first
 * target it is pointing at. If the train is the target of a button or
 * trigger, it will not begin moving until activated. speed default 100 dmg
 * default 2 noise looping sound to play when the train is in motion
 *
 */
val train = registerThink("func_train") { self, game ->
    self.movetype = GameDefines.MOVETYPE_PUSH

    Math3D.VectorClear(self.s.angles)
    self.blocked = trainBlocked
    if (self.spawnflags and GameFunc.TRAIN_BLOCK_STOPS != 0) {
        self.dmg = 0
    } else if (self.dmg == 0) {
        self.dmg = 100
    }
    self.solid = Defines.SOLID_BSP
    game.gameImports.setmodel(self, self.model)

    if (self.st.noise != null)
        self.moveinfo.sound_middle = game.gameImports.soundindex(self.st.noise)

    if (self.speed == 0f)
        self.speed = 100f

    self.moveinfo.speed = self.speed

    self.moveinfo.accel = self.moveinfo.speed
    self.moveinfo.decel = self.moveinfo.speed

    self.use = trainUse

    game.gameImports.linkentity(self)

    if (self.target != null) {
        // start trains on the second frame, to make sure their targets have had a chance to spawn
        self.think.nextTime = game.level.time + Defines.FRAMETIME
        self.think.action = trainFindTarget
    } else {
        game.gameImports.dprintf("func_train without a target at ${Lib.vtos(self.absmin)}\n")
    }
    true
}

private val trainBlocked = registerBlocked("train_blocked") { self, obstacle, game ->
    if (obstacle.svflags and Defines.SVF_MONSTER == 0 && obstacle.client == null) {
        // give it a chance to go away on it's own terms (like gibs)
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

    if (self.dmg == 0)
        return@registerBlocked
    self.touch_debounce_time = game.level.time + 0.5f

    GameCombat.T_Damage(
        obstacle, self, self, Globals.vec3_origin,
        obstacle.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
        GameDefines.MOD_CRUSH, game
    )
}

val trainUse = registerUse("train_use") { self, other, activator, game ->
    self.activator = activator

    if (self.spawnflags and GameFunc.TRAIN_START_ON != 0) {
        if (self.spawnflags and GameFunc.TRAIN_TOGGLE == 0)
            return@registerUse
        self.spawnflags = self.spawnflags and GameFunc.TRAIN_START_ON.inv()
        Math3D.VectorClear(self.velocity)
        self.think.nextTime = 0f
    } else {
        if (self.target_ent != null)
            GameFunc.train_resume(self, game)
        else
            trainNextGoal.think(self, game)
    }
}

val trainFindTarget = registerThink("func_train_find") { self, game ->
    if (self.target == null) {
        game.gameImports.dprintf("train_find: no target\n")
        return@registerThink true
    }
    val ent = GameBase.G_PickTarget(self.target, game)
    if (ent == null) {
        game.gameImports.dprintf("train_find: target ${self.target} not found\n")
        return@registerThink true
    }
    self.target = ent.target

    Math3D.VectorSubtract(ent.s.origin, self.mins, self.s.origin)
    game.gameImports.linkentity(self)


    // if not targeted, start immediately
    if (self.targetname == null) {
        self.spawnflags = self.spawnflags or GameFunc.TRAIN_START_ON
    }

    if (self.spawnflags and GameFunc.TRAIN_START_ON != 0) {
        self.think.nextTime = game.level.time + Defines.FRAMETIME
        self.think.action = trainNextGoal
        self.activator = self
    }
    true
}

val trainNextGoal = registerThink("train_next") { self, game ->
    val dest = floatArrayOf(0f, 0f, 0f)

    var first = true

    var dogoto = true
    var ent: SubgameEntity? = null
    // fixme: change to for (for each) loop
    while (dogoto) {
        if (self.target == null) {
            //			gi.dprintf ("train_next: no next target\n");
            return@registerThink true
        }
        ent = GameBase.G_PickTarget(self.target, game)
        if (ent == null) {
            game.gameImports.dprintf("train_next: bad target " + self.target + "\n")
            return@registerThink true
        }
        self.target = ent.target
        dogoto = false
        // check for a teleport path_corner
        if ((ent.spawnflags and 1) != 0) {
            if (!first) {
                game.gameImports.dprintf("connected teleport path_corners, see " + ent.classname + " at " + Lib.vtos(ent.s.origin) + "\n")
                return@registerThink true
            }
            first = false
            Math3D.VectorSubtract(ent.s.origin, self.mins, self.s.origin)
            Math3D.VectorCopy(self.s.origin, self.s.old_origin)
            self.s.event = Defines.EV_OTHER_TELEPORT
            game.gameImports.linkentity(self)
            dogoto = true
        }
    }
    self.moveinfo.wait = ent!!.wait
    self.target_ent = ent

    if (self.flags and GameDefines.FL_TEAMSLAVE == 0) {
        if (self.moveinfo.sound_start != 0)
            game.gameImports.sound(
                self, (Defines.CHAN_NO_PHS_ADD
                        + Defines.CHAN_VOICE), self.moveinfo.sound_start, 1f,
                Defines.ATTN_STATIC.toFloat(), 0f
            )
        self.s.sound = self.moveinfo.sound_middle
    }

    Math3D.VectorSubtract(ent.s.origin, self.mins, dest)
    self.moveinfo.state = GameFunc.STATE_TOP
    Math3D.VectorCopy(self.s.origin, self.moveinfo.start_origin)
    Math3D.VectorCopy(dest, self.moveinfo.end_origin)
    GameFunc.Move_Calc(self, dest, GameFunc.train_wait, game)
    self.spawnflags = self.spawnflags or GameFunc.TRAIN_START_ON

    true

}
