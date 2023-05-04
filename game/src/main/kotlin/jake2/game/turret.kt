package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerBlocked
import jake2.game.adapters.SuperAdapter.Companion.registerDie
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.items.GameItems
import jake2.game.monsters.M_Infantry
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 TURRET
 There are 3 parts of the turret:
 1) base - only yaw rotation
 2) breach - the gun part - yaw + pitch rotation
 3) driver - the enforcer like entity, responsible for enemy tracking and firing
 */

// indicates firing: todo: use proper communication between driver and turret
private const val FIRE_FLAG = 65536

/**
 * QUAKED turret_base (0 0 0) ?
 * This portion of the turret changes yaw only.
 * MUST be teamed with a turret_breach.
 */
fun turretBase(self: SubgameEntity, game: GameExportsImpl) {
    self.solid = Defines.SOLID_BSP
    self.movetype = GameDefines.MOVETYPE_PUSH
    self.blocked = turretBlocked
    game.gameImports.setmodel(self, self.model)
    game.gameImports.linkentity(self)
}

private val turretBlocked = registerBlocked("turret_blocked") { self, obstacle, game ->
    if (obstacle.takedamage != 0) {
        val attacker = if (self.teammaster.owner != null)
            self.teammaster.owner
        else
            self.teammaster
        GameCombat.T_Damage(
            obstacle, self, attacker, Globals.vec3_origin,
            obstacle.s.origin, Globals.vec3_origin,
            self.teammaster.dmg, 10, 0, GameDefines.MOD_CRUSH, game
        )
    }

}

/**
 * QUAKED turret_breach (0 0 0) ?
 * The gun part of the turret.
 * This portion of the turret can change both pitch and yaw.
 * The model should be made with a flat pitch.
 * It (and the associated base) need to be oriented towards 0.
 * Use "angle" to set the starting angle.
 *
 * "speed" default 50
 * "dmg" default 10
 * "angle" point this forward
 * "target" point this at an info_notnull at the muzzle tip
 * "minpitch" min acceptable pitch angle : default -30
 * "maxpitch" max acceptable pitch angle : default 30
 * "minyaw" min acceptable yaw angle : default 0
 * "maxyaw" max acceptable yaw angle : default 360
 */
fun turretBreach(self: SubgameEntity, game: GameExportsImpl) {
    self.solid = Defines.SOLID_BSP
    self.movetype = GameDefines.MOVETYPE_PUSH
    game.gameImports.setmodel(self, self.model)
    if (self.speed == 0f) {
        self.speed = 50f
    }
    if (self.dmg == 0) {
        self.dmg = 10
    }
    if (self.st.minpitch == 0f)
        self.st.minpitch = -30f
    if (self.st.maxpitch == 0f)
        self.st.maxpitch = 30f
    if (self.st.maxyaw == 0f)
        self.st.maxyaw = 360f

    self.pos1[Defines.PITCH] = -1 * self.st.minpitch
    self.pos1[Defines.YAW] = self.st.minyaw
    self.pos2[Defines.PITCH] = -1 * self.st.maxpitch
    self.pos2[Defines.YAW] = self.st.maxyaw
    self.ideal_yaw = self.s.angles[Defines.YAW]
    self.move_angles[Defines.YAW] = self.ideal_yaw
    self.blocked = turretBlocked
    self.think.action = turretBreachFinishInit
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    game.gameImports.linkentity(self)
}

private val turretBreachFinishInit = registerThink("turret_breach_finish_init") { self, game ->
    // get and save info for muzzle location
    if (self.target == null) {
        game.gameImports.dprintf(self.classname + " at " + Lib.vtos(self.s.origin) + " needs a target\n")
    } else {
        self.target_ent = GameBase.G_PickTarget(self.target, game)
        Math3D.VectorSubtract(self.target_ent.s.origin, self.s.origin, self.move_origin)
        game.freeEntity(self.target_ent)
    }

    self.teammaster.dmg = self.dmg
    self.think.action = turretBreachThink
    self.think.action.think(self, game)
    true
}

private val turretBreachThink = registerThink("turret_breach_think") { self, game ->
    val currentAngles = floatArrayOf(0f, 0f, 0f)

    Math3D.VectorCopy(self.s.angles, currentAngles)
    anglesNormalize(currentAngles)

    anglesNormalize(self.move_angles)
    if (self.move_angles[Defines.PITCH] > 180)
        self.move_angles[Defines.PITCH] -= 360f

    // clamp angles to mins & maxs
    if (self.move_angles[Defines.PITCH] > self.pos1[Defines.PITCH])
        self.move_angles[Defines.PITCH] = self.pos1[Defines.PITCH]
    else if (self.move_angles[Defines.PITCH] < self.pos2[Defines.PITCH])
        self.move_angles[Defines.PITCH] = self.pos2[Defines.PITCH]

    if (self.move_angles[Defines.YAW] < self.pos1[Defines.YAW] || self.move_angles[Defines.YAW] > self.pos2[Defines.YAW]) {
        var dmin = abs(self.pos1[Defines.YAW] - self.move_angles[Defines.YAW])
        if (dmin < -180)
            dmin += 360f
        else if (dmin > 180)
            dmin -= 360f
        var dmax = abs(self.pos2[Defines.YAW] - self.move_angles[Defines.YAW])
        if (dmax < -180)
            dmax += 360f
        else if (dmax > 180)
            dmax -= 360f
        if (abs(dmin) < abs(dmax))
            self.move_angles[Defines.YAW] = self.pos1[Defines.YAW]
        else
            self.move_angles[Defines.YAW] = self.pos2[Defines.YAW]
    }
    val delta = floatArrayOf(0f, 0f, 0f)
    Math3D.VectorSubtract(self.move_angles, currentAngles, delta)
    if (delta[0] < -180)
        delta[0] += 360f
    else if (delta[0] > 180)
        delta[0] -= 360f
    if (delta[1] < -180)
        delta[1] += 360f
    else if (delta[1] > 180)
        delta[1] -= 360f
    delta[2] = 0f

    if (delta[0] > self.speed * Defines.FRAMETIME)
        delta[0] = self.speed * Defines.FRAMETIME
    if (delta[0] < -1 * self.speed * Defines.FRAMETIME)
        delta[0] = -1 * self.speed * Defines.FRAMETIME
    if (delta[1] > self.speed * Defines.FRAMETIME)
        delta[1] = self.speed * Defines.FRAMETIME
    if (delta[1] < -1 * self.speed * Defines.FRAMETIME)
        delta[1] = -1 * self.speed * Defines.FRAMETIME

    Math3D.VectorScale(delta, 1.0f / Defines.FRAMETIME, self.avelocity)

    self.think.nextTime = game.level.time + Defines.FRAMETIME

    // for base(s) only yaw rotation
    var team = self.teammaster
    while (team != null) {
        team.avelocity[1] = self.avelocity[1]
        team = team.teamchain
    }


    // if we have a driver, adjust his velocities
    if (self.owner != null) {

        // angular is easy, just copy ours
        self.owner.avelocity[0] = self.avelocity[0]
        self.owner.avelocity[1] = self.avelocity[1]

        // x & y
        var angle = self.s.angles[1] + self.owner.move_origin[1]
        angle *= (Math.PI * 2 / 360).toFloat()
        val target = floatArrayOf(0f, 0f, 0f)
        target[0] = (self.s.origin[0] + cos(angle) * self.owner.move_origin[0]).snapToEights()
        target[1] = (self.s.origin[1] + sin(angle) * self.owner.move_origin[0]).snapToEights()
        target[2] = self.owner.s.origin[2]
        val dir = floatArrayOf(0f, 0f, 0f)
        Math3D.VectorSubtract(target, self.owner.s.origin, dir)
        self.owner.velocity[0] = dir[0] * 1.0f / Defines.FRAMETIME
        self.owner.velocity[1] = dir[1] * 1.0f / Defines.FRAMETIME

        // z
        angle = self.s.angles[Defines.PITCH] * (Math.PI * 2f / 360f).toFloat()
        val targetZ = (self.s.origin[2] + self.owner.move_origin[0] * tan(angle) + self.owner.move_origin[2]).snapToEights()
        val diff = targetZ - self.owner.s.origin[2]
        self.owner.velocity[2] = diff * 1.0f / Defines.FRAMETIME

        if (self.hasSpawnFlag(FIRE_FLAG)) {
            turretBreachFireRocket(self, game)
            self.unsetSpawnFlag(FIRE_FLAG)
        }
    }
    true
}

fun turretBreachFireRocket(self: SubgameEntity, game: GameExportsImpl) {
    val forward = floatArrayOf(0f, 0f, 0f)
    val right = floatArrayOf(0f, 0f, 0f)
    val up = floatArrayOf(0f, 0f, 0f)
    val start = floatArrayOf(0f, 0f, 0f)
    Math3D.AngleVectors(self.s.angles, forward, right, up)
    Math3D.VectorMA(self.s.origin, self.move_origin[0], forward, start)
    Math3D.VectorMA(start, self.move_origin[1], right, start)
    Math3D.VectorMA(start, self.move_origin[2], up, start)
    val damage = (100 + Lib.random() * 50).toInt()
    val speed = (550 + 50 * game.gameCvars.skill.value).toInt()
    GameWeapon.fire_rocket(
        self.teammaster.owner, start, forward, damage, speed, 150f,
        damage, game
    )
    game.gameImports.positioned_sound(
        start, self, Defines.CHAN_WEAPON,
        game.gameImports.soundindex("weapons/rocklf1a.wav"), 1f,
        Defines.ATTN_NORM.toFloat(), 0f
    )
}

// todo: extract to some FloatUtils or Math?
internal fun Float.snapToEights(): Float {
    var result = this * 8.0f

    // Without this, the rounding would be biased towards rounding down for positive numbers and rounding up for negative numbers.
    if (result > 0.0)
        result += 0.5f
    else
        result -= 0.5f

    return 0.125f * result.toInt()
}

/**
 * QUAKED turret_driver (1 .5 0) (-16 -16 -24) (16 16 32)
 * Must NOT be on the team with the rest of the turret parts.
 * Instead, it must target the turret_breach.
 */
fun turretDriver(self: SubgameEntity, game: GameExportsImpl) {
    if (game.skipForDeathmatch(self)) return

    // todo: decide weather to move to enforcer code or create a separate entity (non enforcer based driver?)
    // would be nice if the driver could decide to detach and then act as a regular monster,
    // or another monster could be a driver (or take over the driver seat)

    self.movetype = GameDefines.MOVETYPE_PUSH
    self.solid = Defines.SOLID_BBOX
    self.s.modelindex = game.gameImports.modelindex("models/monsters/infantry/tris.md2")
    Math3D.VectorSet(self.mins, -16f, -16f, -24f)
    Math3D.VectorSet(self.maxs, 16f, 16f, 32f)

    self.health = 100
    self.gib_health = 0
    self.mass = 200
    self.viewheight = 24

    self.die = turretDriverDie
    self.monsterinfo.stand = M_Infantry.infantry_stand

    self.flags = self.flags or GameDefines.FL_NO_KNOCKBACK

    game.level.total_monsters++

    self.svflags = self.svflags or Defines.SVF_MONSTER
    self.s.renderfx = self.s.renderfx or Defines.RF_FRAMELERP
    self.takedamage = Defines.DAMAGE_AIM
    self.use = GameUtil.monster_use
    self.clipmask = Defines.MASK_MONSTERSOLID
    Math3D.VectorCopy(self.s.origin, self.s.old_origin)
    self.monsterinfo.aiflags = self.monsterinfo.aiflags or (GameDefines.AI_STAND_GROUND or GameDefines.AI_DUCKED)

    if (self.st.item != null) {
        self.item = GameItems.FindItemByClassname(self.st.item, game)
        if (self.item == null)
            game.gameImports.dprintf("${self.classname} at ${Lib.vtos(self.s.origin)} has bad item: ${self.st.item}\n")
    }
    self.think.action = turretDriverLink
    self.think.nextTime = game.level.time + Defines.FRAMETIME

    game.gameImports.linkentity(self)
}

private val turretDriverLink = registerThink("turret_driver_link") { self, game ->
    self.target_ent = GameBase.G_PickTarget(self.target, game)
    self.target_ent.owner = self
    self.target_ent.teammaster.owner = self
    Math3D.VectorCopy(self.target_ent.s.angles, self.s.angles)

    val vec = floatArrayOf(
        self.target_ent.s.origin[0] - self.s.origin[0],
        self.target_ent.s.origin[1] - self.s.origin[1],
        0f)
    self.move_origin[0] = Math3D.VectorLength(vec)

    Math3D.VectorSubtract(self.s.origin, self.target_ent.s.origin, vec)
    Math3D.vectoangles(vec, vec)
    anglesNormalize(vec)

    self.move_origin[1] = vec[1]
    self.move_origin[2] = self.s.origin[2] - self.target_ent.s.origin[2]

    // add the driver to the end of them team chain
    var ent = self.target_ent.teammaster
    while (ent.teamchain != null) {
        ent = ent.teamchain
    }
    ent.teamchain = self
    self.teammaster = self.target_ent.teammaster
    self.flags = self.flags or GameDefines.FL_TEAMSLAVE

    self.think.action = turretDriverThink
    self.think.nextTime = game.level.time + Defines.FRAMETIME

    true
}

private val turretDriverThink = registerThink("turret_driver_think") { self, game ->
    self.think.nextTime = game.level.time + Defines.FRAMETIME

    // self.enemy is not a thread anymore
    if (self.enemy != null && (!self.enemy.inuse || self.enemy.health <= 0))
        self.enemy = null

    if (self.enemy == null) {
        if (!GameUtil.FindTarget(self, game))
            return@registerThink true
        self.monsterinfo.trail_time = game.level.time
        self.monsterinfo.aiflags = self.monsterinfo.aiflags and GameDefines.AI_LOST_SIGHT.inv()
    } else {
        if (GameUtil.visible(self, self.enemy, game)) {
            if (self.monsterinfo.aiflags and GameDefines.AI_LOST_SIGHT != 0) {
                self.monsterinfo.trail_time = game.level.time
                self.monsterinfo.aiflags = self.monsterinfo.aiflags and GameDefines.AI_LOST_SIGHT.inv()
            }
        } else {
            self.monsterinfo.aiflags = self.monsterinfo.aiflags or GameDefines.AI_LOST_SIGHT
            return@registerThink true
        }
    }

    // let the turret know where we want it to aim
    val target = floatArrayOf(0f, 0f, 0f)
    Math3D.VectorCopy(self.enemy.s.origin, target)
    target[2] += self.enemy.viewheight.toFloat()
    val dir = floatArrayOf(0f, 0f, 0f)
    Math3D.VectorSubtract(target, self.target_ent.s.origin, dir)
    Math3D.vectoangles(dir, self.target_ent.move_angles)

    // decide if we should shoot
    if (game.level.time < self.monsterinfo.attack_finished)
        return@registerThink true

    val reactionTime = 3 - game.gameCvars.skill.value
    if (game.level.time - self.monsterinfo.trail_time < reactionTime)
        return@registerThink true

    self.monsterinfo.attack_finished = game.level.time + reactionTime + 1.0f
    self.target_ent.setSpawnFlag(FIRE_FLAG) // FIRE!

    true
}

private val turretDriverDie = registerDie("turret_driver_die") { self, inflictor, attacker, damage, _, game ->
    // level the gun
    self.target_ent.move_angles[0] = 0f

    // remove the driver from the end of them team chain
    var ent = self.target_ent.teammaster
    while (ent.teamchain !== self) {
        ent = ent.teamchain
    }
    ent.teamchain = null

    self.teammaster = null
    self.flags = self.flags and GameDefines.FL_TEAMSLAVE.inv()

    self.target_ent.owner = null
    self.target_ent.teammaster.owner = null

    M_Infantry.infantry_die.die(self, inflictor, attacker, damage, null, game)

}


// todo: move to vector3f
private fun anglesNormalize(vec: FloatArray) {
    while (vec[0] > 360)
        vec[0] -= 360f
    while (vec[0] < 0)
        vec[0] += 360f
    while (vec[1] > 360)
        vec[1] -= 360f
    while (vec[1] < 0)
        vec[1] += 360f
}

