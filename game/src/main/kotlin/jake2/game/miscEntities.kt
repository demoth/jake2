package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerDie
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D

/*
 * QUAKED misc_explobox (0 .5 .8) (-16 -16 0) (16 16 40)
 * Large exploding barrel. You can override its mass (400), health (10), and dmg (150).
 */
fun miscExplobox(self: SubgameEntity, game: GameExportsImpl) {
    if (game.skipForDeathmatch(self)) return

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

private val miscExploboxExplode = registerThink("barrel_explode") { self, game ->
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

/*
 * QUAKED misc_blackhole (1 .5 0) (-8 -8 -8) (8 8 8)
 * 
 * Disappears when used.
 */
fun miscBlackhole(self: SubgameEntity, game: GameExportsImpl) {
    self.movetype = GameDefines.MOVETYPE_NONE
    self.solid = Defines.SOLID_NOT
    Math3D.VectorSet(self.mins, -64f, -64f, 0f)
    Math3D.VectorSet(self.maxs, 64f, 64f, 8f)
    self.s.modelindex = game.gameImports.modelindex("models/objects/black/tris.md2")
    self.s.renderfx = Defines.RF_TRANSLUCENT
    self.use = miscBlackholeDisappear
    self.think.action = miscBlackholeThink
    self.think.nextTime = game.level.time + 2 * Defines.FRAMETIME
    game.gameImports.linkentity(self)
}

private val miscBlackholeThink = registerThink("misc_blackhole_think") { self, game ->
    if (++self.s.frame >= 19) {
        self.s.frame = 0
    }
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    true
}

private val miscBlackholeDisappear = registerUse("misc_blackhole_use") { self, _, _, game ->
     game.freeEntity(self)
}

/*
 * QUAKED misc_banner (1 .5 0) (-4 -4 -4) (4 4 4)
 * The origin is the bottom of the banner.
 * The banner is 128 tall.
 */
fun miscBanner(self: SubgameEntity, game: GameExportsImpl) {
    self.movetype = GameDefines.MOVETYPE_NONE
    self.solid = Defines.SOLID_NOT
    self.s.modelindex = game.gameImports.modelindex("models/objects/banner/tris.md2")
    self.s.frame = Lib.rand() % 16
    game.gameImports.linkentity(self)
    self.think.action = bannerThink
    self.think.nextTime = game.level.time + Defines.FRAMETIME
}

private val bannerThink = registerThink("misc_banner_think") { self, game ->
    self.s.frame = (self.s.frame + 1) % 16
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    true
}

/*
 * QUAKED misc_gib_arm (1 0 0) (-8 -8 -8) (8 8 8) 
 * Intended for use with the target_spawner
 */
fun miscGibArm(self: SubgameEntity, game: GameExportsImpl) {
    initGib(self, game, "arm")
}

/*
 * QUAKED misc_gib_leg (1 0 0) (-8 -8 -8) (8 8 8) 
 * Intended for use with the target_spawner
 */
fun miscGibLeg(self: SubgameEntity, game: GameExportsImpl) {
    initGib(self, game, "leg")
}

/*
 * QUAKED misc_gib_head (1 0 0) (-8 -8 -8) (8 8 8) 
 * Intended for use with the target_spawner
 */
fun miscGibHead(self: SubgameEntity, game: GameExportsImpl) {
    initGib(self, game, "head")
}

private fun initGib(ent: SubgameEntity, game: GameExportsImpl, model: String) {
    game.gameImports.setmodel(ent, "models/objects/gibs/$model/tris.md2")
    ent.solid = Defines.SOLID_NOT
    ent.s.effects = ent.s.effects or Defines.EF_GIB
    ent.takedamage = Defines.DAMAGE_YES
    ent.die = dieFreeEntity
    ent.movetype = GameDefines.MOVETYPE_TOSS
    ent.svflags = ent.svflags or Defines.SVF_MONSTER
    ent.deadflag = GameDefines.DEAD_DEAD
    ent.avelocity[0] = Lib.random() * 200
    ent.avelocity[1] = Lib.random() * 200
    ent.avelocity[2] = Lib.random() * 200
    ent.think.action = GameUtil.G_FreeEdictA
    ent.think.nextTime = game.level.time + 30
    game.gameImports.linkentity(ent)
}

private val dieFreeEntity = registerDie("die-free-entity") { self, _, _, _, _, game ->
    game.freeEntity(self)
}

/*
 * QUAKED misc_satellite_dish (1 .5 0) (-64 -64 0) (64 64 128)
 */
fun miscSatelliteDish(self: SubgameEntity, game: GameExportsImpl) {
    self.movetype = GameDefines.MOVETYPE_NONE
    self.solid = Defines.SOLID_BBOX
    Math3D.VectorSet(self.mins, -64f, -64f, 0f)
    Math3D.VectorSet(self.maxs, 64f, 64f, 128f)
    self.s.modelindex = game.gameImports.modelindex("models/objects/satellite/tris.md2")
    self.use = miscSatelliteDishUse
    game.gameImports.linkentity(self)
}

private val miscSatelliteDishUse = registerUse("misc_satellite_dish_use") { self, other, activator, game ->
    self.s.frame = 0
    self.think.action = miscDishRotate
    self.think.nextTime = game.level.time + Defines.FRAMETIME
}

private val miscDishRotate = registerThink("misc_satellite_dish_think") { self, game ->
    self.s.frame++
    if (self.s.frame < 38)
        self.think.nextTime = game.level.time + Defines.FRAMETIME

    true
}

/*
 * QUAKED misc_deadsoldier (1 .5 0) (-16 -16 0) (16 16 16)
 * ON_BACK
 * ON_STOMACH
 * BACK_DECAP
 * FETAL_POS
 * SIT_DECAP
 * IMPALED
 * This is the dead player model.
 * Comes in 6 exciting different poses!
 */
// todo: extract spawnflags
fun miscDeadSoldier(self: SubgameEntity, game: GameExportsImpl) {
    if (game.gameCvars.deathmatch.value != 0f) { // auto-remove for deathmatch
        game.freeEntity(self)
        return
    }
    self.movetype = GameDefines.MOVETYPE_NONE
    self.solid = Defines.SOLID_BBOX
    self.s.modelindex = game.gameImports.modelindex("models/deadbods/dude/tris.md2")

    // Defaults to frame 0
    self.s.frame = if (self.spawnflags and 2 != 0) 1
    else if (self.spawnflags and 4 != 0) 2
    else if (self.spawnflags and 8 != 0) 3
    else if (self.spawnflags and 16 != 0) 4
    else if (self.spawnflags and 32 != 0) 5
    else 0

    Math3D.VectorSet(self.mins, -16f, -16f, 0f)
    Math3D.VectorSet(self.maxs, 16f, 16f, 16f)
    self.deadflag = GameDefines.DEAD_DEAD
    self.takedamage = Defines.DAMAGE_YES
    self.svflags = self.svflags or (Defines.SVF_MONSTER or Defines.SVF_DEADMONSTER)
    self.die = miscDeadSoldierDieAdapter
    self.monsterinfo.aiflags = self.monsterinfo.aiflags or GameDefines.AI_GOOD_GUY
    game.gameImports.linkentity(self)
}

private val miscDeadSoldierDieAdapter = registerDie("misc_deadsoldier_die") { self, inflictor, attacker, damage, point, game ->
    if (self.health > -80)
        return@registerDie

    game.gameImports.sound(self, Defines.CHAN_BODY, game.gameImports.soundindex("misc/udeath.wav"), 1f, Defines.ATTN_NORM.toFloat(), 0f)
    repeat(3) {
        GameMisc.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage, GameDefines.GIB_ORGANIC, game)
    }
    GameMisc.ThrowHead(self, "models/objects/gibs/head2/tris.md2", damage, GameDefines.GIB_ORGANIC, game)
}

/*
 * QUAKED misc_viper_bomb (1 0 0) (-8 -8 -8) (8 8 8) "dmg" how much boom
 * should the bomb make?
 */
fun miscViperBomb(self: SubgameEntity, game: GameExportsImpl) {
    self.movetype = GameDefines.MOVETYPE_NONE
    self.solid = Defines.SOLID_NOT
    Math3D.VectorSet(self.mins, -8f, -8f, -8f)
    Math3D.VectorSet(self.maxs, 8f, 8f, 8f)
    self.s.modelindex = game.gameImports.modelindex("models/objects/bomb/tris.md2")
    if (self.dmg == 0)
        self.dmg = 1000
    self.use = miscViperBombUse
    self.svflags = self.svflags or Defines.SVF_NOCLIENT
    game.gameImports.linkentity(self)
}

private val miscViperBombUse = registerUse("misc_viper_bomb_use") { self, other, activator, game ->
    self.solid = Defines.SOLID_BBOX
    self.svflags = self.svflags and Defines.SVF_NOCLIENT.inv()
    self.s.effects = self.s.effects or Defines.EF_ROCKET
    self.use = null
    self.movetype = GameDefines.MOVETYPE_TOSS
    self.think.prethink = miscViperBombPrethink
    self.touch = miscViperBombTouch
    self.activator = activator

    val es = GameBase.G_Find(null, GameBase.findByClassName, "misc_viper", game)
    var viper: SubgameEntity? = null
    if (es != null)
        viper = es.o

    self.velocity = (viper!!.moveinfo.dir * viper.moveinfo.speed).toArray()
    // Math3D.VectorScale(viper!!.moveinfo.dir, viper.moveinfo.speed, self.velocity)

    self.timestamp = game.level.time
    self.moveinfo.dir = viper.moveinfo.dir.copy()
    // Math3D.VectorCopy(viper.moveinfo.dir, self.moveinfo.dir)
}

/**
 * Rotates the bomb to imitate the drag of the tail
 * todo: why it's prethink?
 */
private val miscViperBombPrethink = registerThink("misc_viper_bomb_prethink") { self, game ->
    self.groundentity = null

    var diff: Float = self.timestamp - game.level.time
    if (diff < -1.0)
        diff = -1.0f

    val result = self.moveinfo.dir * (1.0f + diff)
    // Math3D.VectorScale(self.moveinfo.dir, 1.0f + diff, v)
    val v = Vector3f(result.x, result.y, diff)
    // v.z = diff

    diff = self.s.angles[2]
    self.s.angles =  v.toAngles()
    // Math3D.vectoangles(v, self.s.angles)
    self.s.angles[2] = diff + 10

    true
}

private val miscViperBombTouch = registerTouch("misc_viper_bomb_touch") { self, other, plane, surf, game ->
    GameUtil.G_UseTargets(self, self.activator, game)

    self.s.origin[2] = self.absmin[2] + 1
    GameCombat.T_RadiusDamage(self, self, self.dmg.toFloat(), null, (self.dmg + 40).toFloat(), GameDefines.MOD_BOMB, game)
    GameMisc.BecomeExplosion2(self, game)
}


/*
 * QUAKED misc_bigviper (1 .5 0) (-176 -120 -24) (176 120 72)
 * This is a large stationary viper as seen in Paul's intro
 */
fun miscBigViper(self: SubgameEntity, game: GameExportsImpl) {
    self.movetype = GameDefines.MOVETYPE_NONE
    self.solid = Defines.SOLID_BBOX
    Math3D.VectorSet(self.mins, -176f, -120f, -24f)
    Math3D.VectorSet(self.maxs, 176f, 120f, 72f)
    self.s.modelindex = game.gameImports.modelindex("models/ships/bigviper/tris.md2")
    game.gameImports.linkentity(self)
}

/**
 * QUAKED misc_teleporter (1 0 0) (-32 -32 -24) (32 32 -16)
 * Stepping onto this disc will teleport player to the targeted misc_teleporter_dest object.
 */
fun miscTeleporter(self: SubgameEntity, game: GameExportsImpl) {
    if (self.target == null) {
        game.gameImports.dprintf("teleporter without a target.\n")
        game.freeEntity(self)
        return
    }
    game.gameImports.setmodel(self, "models/objects/dmspot/tris.md2")
    self.s.skinnum = 1
    self.s.effects = Defines.EF_TELEPORTER
    self.s.sound = game.gameImports.soundindex("world/amb10.wav")
    self.solid = Defines.SOLID_BBOX
    Math3D.VectorSet(self.mins, -32f, -32f, -24f)
    Math3D.VectorSet(self.maxs, 32f, 32f, -16f)
    game.gameImports.linkentity(self)

    val trigger = game.G_Spawn()
    trigger.touch = teleporterTriggerTouch
    trigger.solid = Defines.SOLID_TRIGGER
    trigger.target = self.target
    trigger.owner = self
    Math3D.VectorCopy(self.s.origin, trigger.s.origin)
    Math3D.VectorSet(trigger.mins, -8f, -8f, 8f)
    Math3D.VectorSet(trigger.maxs, 8f, 8f, 24f)
    game.gameImports.linkentity(trigger)
}

private val teleporterTriggerTouch = registerTouch("teleporter_touch") { self, other, plane, surf, game ->
    val client = other.client ?: return@registerTouch

    val dest = GameBase.G_Find(null, GameBase.findByTargetName, self.target, game)?.o

    if (dest == null) {
        game.gameImports.dprintf("Couldn't find destination\n")
        return@registerTouch
    }

    // unlink to make sure it can't possibly interfere with KillBox
    game.gameImports.unlinkentity(other)

    Math3D.VectorCopy(dest.s.origin, other.s.origin)
    Math3D.VectorCopy(dest.s.origin, other.s.old_origin)
    other.s.origin[2] += 10f

    // clear the velocity and hold them in place briefly
    Math3D.VectorClear(other.velocity)
    client.playerState.pmove.pm_time = (160 shr 3).toByte() // hold time

    client.playerState.pmove.pm_flags = (client.playerState.pmove.pm_flags.toInt() or Defines.PMF_TIME_TELEPORT).toByte()

    // draw the teleport splash at source and on the player
    self.owner.s.event = Defines.EV_PLAYER_TELEPORT
    other.s.event = Defines.EV_PLAYER_TELEPORT

    // set angles
    for (i in 0..2) {
        client.playerState.pmove.delta_angles[i] = Math3D.ANGLE2SHORT(dest.s.angles[i]- client.resp.cmd_angles[i]).toShort()
    }

    Math3D.VectorClear(other.s.angles)
    Math3D.VectorClear(client.playerState.viewangles)
    Math3D.VectorClear(client.v_angle)

    // kill anything at the destination
    GameUtil.KillBox(other, game)

    game.gameImports.linkentity(other)
}

/*
 * QUAKED misc_teleporter_dest (1 0 0) (-32 -32 -24) (32 32 -16)
 * Point teleporters at these.
 */
fun miscTeleporterDest(self: SubgameEntity, game: GameExportsImpl) {
    game.gameImports.setmodel(self, "models/objects/dmspot/tris.md2")
    self.s.skinnum = 0
    self.solid = Defines.SOLID_BBOX
    Math3D.VectorSet(self.mins, -32f, -32f, -24f)
    Math3D.VectorSet(self.maxs, 32f, 32f, -16f)
    game.gameImports.linkentity(self)

}
