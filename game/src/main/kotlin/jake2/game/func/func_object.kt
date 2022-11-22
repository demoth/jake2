package jake2.game.func

import jake2.game.*
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.Globals

/**
 * QUAKED func_object (0 .5 .8) ? 
 * TRIGGER_SPAWN 
 * ANIMATED 
 * ANIMATED_FAST 
 * This is solid bmodel that will fall if it's support it removed.
 */
private const val TRIGGER_SPAWN = 1
private const val ANIMATED = 2
private const val ANIMATED_FAST = 4

fun funcObject(self: SubgameEntity, game: GameExportsImpl) {
    game.gameImports.setmodel(self, self.model)

    self.mins[0] += 1f
    self.mins[1] += 1f
    self.mins[2] += 1f
    self.maxs[0] -= 1f
    self.maxs[1] -= 1f
    self.maxs[2] -= 1f

    if (self.dmg == 0)
        self.dmg = 100

    if (self.spawnflags == 0) { // todo: invert and check explicitly TRIGGER_SPAWN flag
        self.solid = Defines.SOLID_BSP
        self.movetype = GameDefines.MOVETYPE_PUSH
        self.think.action = funcObjectFall
        self.think.nextTime = game.level.time + 2 * Defines.FRAMETIME
    } else {
        self.solid = Defines.SOLID_NOT
        self.movetype = GameDefines.MOVETYPE_PUSH
        self.use = funcObjectAppear
        self.svflags = self.svflags or Defines.SVF_NOCLIENT
    }

    if (self.spawnflags and ANIMATED != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALL
    if (self.spawnflags and ANIMATED_FAST != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALLFAST

    self.clipmask = Defines.MASK_MONSTERSOLID

    game.gameImports.linkentity(self)
}

private val funcObjectAppear = registerUse("func_object_use") { self, _, _, game ->
    self.solid = Defines.SOLID_BSP
    self.svflags = self.svflags and Defines.SVF_NOCLIENT.inv()
    self.use = null
    GameUtil.KillBox(self, game)
    funcObjectFall.think(self, game)
}

private val funcObjectFall = registerThink("func_object_release") { self, game ->
    self.movetype = GameDefines.MOVETYPE_TOSS
    self.touch = funcObjectTouch
    true
}

private val funcObjectTouch = registerTouch("func_object_touch") { self, other, plane, surf, game ->
    // only squash thing we fall on top of
    if (plane == null || plane.normal[2] < 1.0)
        return@registerTouch
    
    if (other.takedamage == Defines.DAMAGE_NO)
        return@registerTouch
    
    GameCombat.T_Damage(
        other, self, self, Globals.vec3_origin,
        self.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
        GameDefines.MOD_CRUSH, game
    )
}
