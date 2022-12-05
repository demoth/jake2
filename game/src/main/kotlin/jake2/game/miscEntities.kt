package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerDie
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.qcommon.Defines
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D

/*
 * QUAKED misc_explobox (0 .5 .8) (-16 -16 0) (16 16 40)
 * Large exploding barrel. You can override its mass (400), health (10), and dmg (150).
 */
fun miscExplobox(self: SubgameEntity, game: GameExportsImpl) {
    if (game.gameCvars.deathmatch.value != 0f) { // auto-remove for deathmatch
        game.freeEntity(self)
        return
    }
    game.gameImports.modelindex("models/objects/debris1/tris.md2")
    game.gameImports.modelindex("models/objects/debris2/tris.md2")
    game.gameImports.modelindex("models/objects/debris3/tris.md2")
    self.solid = Defines.SOLID_BBOX
    self.movetype = GameDefines.MOVETYPE_STEP
    self.model = "models/objects/barrels/tris.md2"
    self.s.modelindex = game.gameImports.modelindex(self.model)
    Math3D.VectorSet(self.mins, -16f, -16f, 0f)
    Math3D.VectorSet(self.maxs, 16f, 16f, 40f)

    if (self.mass == 0)
        self.mass = 400
    if (self.health == 0)
        self.health = 10
    if (self.dmg == 0)
        self.dmg = 150

    self.die = miscExploboxDelayedExplode
    self.takedamage = Defines.DAMAGE_YES
    self.monsterinfo.aiflags = GameDefines.AI_NOSTEP
    self.touch = miscExploboxPush
    self.think.action = M.M_droptofloor
    self.think.nextTime = game.level.time + 2 * Defines.FRAMETIME
    game.gameImports.linkentity(self)
}

private val miscExploboxDelayedExplode = registerDie("barrel_delay") { self, _, attacker, _, _, game ->
    self.takedamage = Defines.DAMAGE_NO
    self.think.nextTime = game.level.time + 2 * Defines.FRAMETIME
    self.think.action = miscExploboxExplode
    self.activator = attacker
}

// pushes the barrel forward
private val miscExploboxPush = registerTouch("barrel_touch") { self, other, _, _, game ->
    if (other.groundentity == null || other.groundentity === self) 
        return@registerTouch

    val ratio = other.mass.toFloat() / self.mass.toFloat()
    val v = floatArrayOf(0f, 0f, 0f)
    Math3D.VectorSubtract(self.s.origin, other.s.origin, v)
    M.M_walkmove(self, Math3D.vectoyaw(v), 20 * ratio * Defines.FRAMETIME, game)
}

private val miscExploboxExplode = registerThink("") { self, game ->
    val oldOrigin = floatArrayOf(0f, 0f, 0f)

    GameCombat.T_RadiusDamage(self, self.activator, self.dmg.toFloat(), null, (self.dmg + 40).toFloat(), GameDefines.MOD_BARREL, game)

    Math3D.VectorCopy(self.s.origin, oldOrigin)
    Math3D.VectorMA(self.absmin, 0.5f, self.size, self.s.origin)

    // a few big chunks
    var speed = 1.5f * self.dmg.toFloat() / 200.0f
    val origin = floatArrayOf(0f, 0f, 0f)
    repeat(2) {
        origin[0] = self.s.origin[0] + Lib.crandom() * self.size[0]
        origin[1] = self.s.origin[1] + Lib.crandom() * self.size[1]
        origin[2] = self.s.origin[2] + Lib.crandom() * self.size[2]
        GameMisc.ThrowDebris(self, "models/objects/debris1/tris.md2", speed, origin, game)
    }

    // bottom corners
    speed = 1.75f * self.dmg.toFloat() / 200.0f
    Math3D.VectorCopy(self.absmin, origin)
    GameMisc.ThrowDebris(self, "models/objects/debris3/tris.md2", speed, origin, game)
    Math3D.VectorCopy(self.absmin, origin)
    origin[0] += self.size[0]
    GameMisc.ThrowDebris(self, "models/objects/debris3/tris.md2", speed, origin, game)
    Math3D.VectorCopy(self.absmin, origin)
    origin[1] += self.size[1]
    GameMisc.ThrowDebris(self, "models/objects/debris3/tris.md2", speed, origin, game)
    Math3D.VectorCopy(self.absmin, origin)
    origin[0] += self.size[0]
    origin[1] += self.size[1]
    GameMisc.ThrowDebris(self, "models/objects/debris3/tris.md2", speed, origin, game)

    // a bunch of little chunks
    speed = 2 * self.dmg.toFloat() / 200.0f
    repeat(8) {
        origin[0] = self.s.origin[0] + Lib.crandom() * self.size[0]
        origin[1] = self.s.origin[1] + Lib.crandom() * self.size[1]
        origin[2] = self.s.origin[2] + Lib.crandom() * self.size[2]
        GameMisc.ThrowDebris(self, "models/objects/debris2/tris.md2", speed, origin, game)
    }

    Math3D.VectorCopy(oldOrigin, self.s.origin)
    if (self.groundentity != null)
        GameMisc.BecomeExplosion2(self, game)
    else
        GameMisc.BecomeExplosion1(self, game)
    true
}