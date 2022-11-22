package jake2.game.func

import jake2.game.GameCombat
import jake2.game.GameDefines
import jake2.game.GameMisc
import jake2.game.GameUtil
import jake2.game.adapters.SuperAdapter.Companion.registerDie
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D

/**
 * QUAKED func_explosive (0 .5 .8) ?
 * TRIGGER_SPAWN
 * ANIMATED
 * ANIMATED_FAST
 * Any brush that you want to explode or break apart. If you want an
 * explosion, set dmg and it will do a radius explosion of that amount at
 * the center of the brush.
 *
 * If targeted it will not be shootable.
 *
 * health defaults to 100.
 *
 * mass defaults to 75. This determines how much debris is emitted when it
 * explodes. You get one large chunk per 100 of mass (up to 8) and one small
 * chunk per 25 of mass (up to 16). So 800 gives the most.
 */
private const val TRIGGER_SPAWN = 1
private const val ANIMATED = 2
private const val ANIMATED_FAST = 4

val explosive = registerThink("func_explosive") { self, game ->
    if (game.gameCvars.deathmatch.value != 0f) { // auto-remove for deathmatch
        game.freeEntity(self)
        return@registerThink true
    }

    self.movetype = GameDefines.MOVETYPE_PUSH

    game.gameImports.modelindex("models/objects/debris1/tris.md2")
    game.gameImports.modelindex("models/objects/debris2/tris.md2")

    game.gameImports.setmodel(self, self.model)

    if (self.spawnflags and TRIGGER_SPAWN != 0) {
        self.svflags = self.svflags or Defines.SVF_NOCLIENT
        self.solid = Defines.SOLID_NOT
        self.use = explosiveUseSpawn
    } else {
        self.solid = Defines.SOLID_BSP
        if (self.targetname != null)
            self.use = explosiveUse
    }

    if (self.spawnflags and ANIMATED != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALL
    if (self.spawnflags and ANIMATED_FAST != 0)
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALLFAST

    if (self.use !== explosiveUse) { // fixme: any better way to indicate it's not usable?
        if (self.health == 0)
            self.health = 100
        self.die = explosiveExplode
        self.takedamage = Defines.DAMAGE_YES
    }

    game.gameImports.linkentity(self)
    true
}

private val explosiveUseSpawn = registerUse("func_explosive_spawn") { self, _, _, game ->
    self.solid = Defines.SOLID_BSP
    self.svflags = self.svflags and Defines.SVF_NOCLIENT.inv()
    self.use = null
    GameUtil.KillBox(self, game)
    game.gameImports.linkentity(self)
}

private val explosiveUse = registerUse("func_explosive_use") { self, other, activator, game ->
    explosiveExplode.die(self, self, other, self.health, Globals.vec3_origin, game)
}

private val explosiveExplode = registerDie("func_explosive_explode") { self, inflictor, attacker, damage, point, game ->
    val origin = floatArrayOf(0f, 0f, 0f)
    val chunkorigin = floatArrayOf(0f, 0f, 0f)
    val size = floatArrayOf(0f, 0f, 0f)

    // bmodel origins are (0 0 0), we need to adjust that here
    Math3D.VectorScale(self.size, 0.5f, size)
    Math3D.VectorAdd(self.absmin, size, origin)
    Math3D.VectorCopy(origin, self.s.origin)

    self.takedamage = Defines.DAMAGE_NO

    if (self.dmg != 0)
        GameCombat.T_RadiusDamage(
            self,
            attacker,
            self.dmg.toFloat(),
            null,
            (self.dmg + 40).toFloat(),
            GameDefines.MOD_EXPLOSIVE,
            game
        )

    Math3D.VectorSubtract(self.s.origin, inflictor!!.s.origin, self.velocity)
    Math3D.VectorNormalize(self.velocity)
    Math3D.VectorScale(self.velocity, 150f, self.velocity)

    // start chunks towards the center
    Math3D.VectorScale(size, 0.5f, size)

    val mass = if (self.mass != 0) self.mass else 75

    // big chunks
    if (mass >= 100) {
        var bigChunks = mass / 100
        if (bigChunks > 8) bigChunks = 8
        while (bigChunks-- != 0) {
            chunkorigin[0] = origin[0] + Lib.crandom() * size[0]
            chunkorigin[1] = origin[1] + Lib.crandom() * size[1]
            chunkorigin[2] = origin[2] + Lib.crandom() * size[2]
            GameMisc.ThrowDebris(self, "models/objects/debris1/tris.md2", 1f, chunkorigin, game)
        }
    }

    // small chunks
    var smallChunks = mass / 25
    if (smallChunks > 16)
        smallChunks = 16
    while (smallChunks-- != 0) {
        chunkorigin[0] = origin[0] + Lib.crandom() * size[0]
        chunkorigin[1] = origin[1] + Lib.crandom() * size[1]
        chunkorigin[2] = origin[2] + Lib.crandom() * size[2]
        GameMisc.ThrowDebris(self, "models/objects/debris2/tris.md2", 2f, chunkorigin, game)
    }

    GameUtil.G_UseTargets(self, attacker, game)

    if (self.dmg != 0)
        GameMisc.BecomeExplosion1(self, game)
    else
        game.freeEntity(self)

}
