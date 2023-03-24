package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.qcommon.Defines
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D

/**
 * QUAKED path_corner (.5 .3 0) (-8 -8 -8) (8 8 8)
 * TELEPORT
 * Target: next path corner
 * Pathtarget: gets used when an entity that has this path_corner targeted touches it
 *
 * Named corner because it's aligned by the minimal corner of the bounding box
 */

private const val TELEPORT = 1
fun pathCorner(self: SubgameEntity, game: GameExportsImpl) {
    if (self.targetname == null) {
        game.gameImports.dprintf("path_corner with no targetname at ${Lib.vtos(self.s.origin)}")
        game.freeEntity(self)
        return
    }
    self.solid = Defines.SOLID_TRIGGER
    self.touch = pathCornerTouch
    Math3D.VectorSet(self.mins, -8f, -8f, -8f)
    Math3D.VectorSet(self.maxs, 8f, 8f, 8f)
    self.svflags = self.svflags or Defines.SVF_NOCLIENT
    game.gameImports.linkentity(self)
}

private val pathCornerTouch = registerTouch("path_corner_touch") { self, other, _, _, game ->
    if (other.movetarget !== self)
        return@registerTouch

    if (other.enemy != null)
        return@registerTouch

    if (self.pathtarget != null) {
        val savetarget = self.target
        self.target = self.pathtarget
        GameUtil.G_UseTargets(self, other, game)
        self.target = savetarget
    }

    var nextGoal = if (self.target != null) GameBase.G_PickTarget(self.target, game) else null

    val v = floatArrayOf(0f, 0f, 0f)
    if (nextGoal != null && nextGoal.hasSpawnFlag(TELEPORT)) {
        Math3D.VectorCopy(nextGoal.s.origin, v)
        v[2] += nextGoal.mins[2]
        v[2] -= other.mins[2]
        Math3D.VectorCopy(v, other.s.origin)
        nextGoal = GameBase.G_PickTarget(nextGoal.target, game)
        other.s.event = Defines.EV_OTHER_TELEPORT
    }

    other.movetarget = nextGoal
    other.goalentity = nextGoal

    if (self.wait != 0f) {
        other.monsterinfo.pausetime = game.level.time + self.wait
        other.monsterinfo.stand.think(other, game)
        return@registerTouch
    }

    if (other.movetarget == null) {
        other.monsterinfo.pausetime = game.level.time + 100000000
        other.monsterinfo.stand.think(other, game)
    } else {
        Math3D.VectorSubtract(other.goalentity.s.origin, other.s.origin, v)
        other.ideal_yaw = Math3D.vectoyaw(v)
    }
}

/**
 * QUAKED point_combat (0.5 0.3 0) (-8 -8 -8) (8 8 8)
 * HOLD
 * When targeted by the monster, make it head here first when activated before going after the activator.
 * If hold is selected, it will stay here.
 */
private const val HOLD = 1
fun pointCombat(self: SubgameEntity, game: GameExportsImpl) {
    if (game.skipForDeathmatch(self)) return

    self.solid = Defines.SOLID_TRIGGER
    self.touch = pointCombatTouch
    Math3D.VectorSet(self.mins, -8f, -8f, -16f)
    Math3D.VectorSet(self.maxs, 8f, 8f, 16f)
    self.svflags = Defines.SVF_NOCLIENT
    game.gameImports.linkentity(self)
}

private val pointCombatTouch = registerTouch("point_combat_touch") { self, other, _, _, game ->
    if (other.movetarget !== self)
        return@registerTouch

    if (self.target != null) {
        other.target = self.target
        other.movetarget = GameBase.G_PickTarget(other.target, game)
        other.goalentity = other.movetarget
        if (other.goalentity == null) {
            game.gameImports.dprintf("${self.classname} at ${Lib.vtos(self.s.origin)} target ${self.target} does not exist\n")
            other.movetarget = self
        }
        self.target = null
    } else if (self.hasSpawnFlag(HOLD) && (other.flags and (GameDefines.FL_SWIM or GameDefines.FL_FLY)) == 0) {
        other.monsterinfo.pausetime = game.level.time + 100000000
        other.monsterinfo.aiflags = other.monsterinfo.aiflags or GameDefines.AI_STAND_GROUND
        other.monsterinfo.stand.think(other, game)
    }

    if (other.movetarget === self) {
        other.target = null
        other.movetarget = null
        other.goalentity = other.enemy
        other.monsterinfo.aiflags = other.monsterinfo.aiflags and GameDefines.AI_COMBAT_POINT.inv()
    }

    if (self.pathtarget != null) {
        val savetarget = self.target
        self.target = self.pathtarget
        val activator = if (other.enemy != null && other.enemy.client != null)
            other.enemy
        else if ((other.oldenemy != null && other.oldenemy.client != null))
            other.oldenemy
        else if ((other.activator != null && other.activator.client != null))
            other.activator
        else other

        GameUtil.G_UseTargets(self, activator, game)
        self.target = savetarget
    }

}
