package jake2.game

import jake2.game.adapters.SuperAdapter.Companion.registerBlocked
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * QUAKED turret_base (0 0 0) ? This portion of the turret changes yaw only.
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
 * QUAKED turret_breach (0 0 0) ? The gun part of the turret.
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
fun turretBreach(self: SubgameEntity, gameExports: GameExportsImpl) {
    self.solid = Defines.SOLID_BSP
    self.movetype = GameDefines.MOVETYPE_PUSH
    gameExports.gameImports.setmodel(self, self.model)
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
    self.think.nextTime = gameExports.level.time + Defines.FRAMETIME
    gameExports.gameImports.linkentity(self)
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
    val delta = floatArrayOf(0f, 0f, 0f)

    Math3D.VectorCopy(self.s.angles, currentAngles)
    GameTurret.AnglesNormalize(currentAngles)

    GameTurret.AnglesNormalize(self.move_angles)
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
        target[0] = (self.s.origin[0] + cos(angle.toDouble()) * self.owner.move_origin[0]).toFloat().snapToEights()
        target[1] = (self.s.origin[1] + sin(angle.toDouble()) * self.owner.move_origin[0]).toFloat().snapToEights()
        target[2] = self.owner.s.origin[2]
        val dir = floatArrayOf(0f, 0f, 0f)
        Math3D.VectorSubtract(target, self.owner.s.origin, dir)
        self.owner.velocity[0] = dir[0] * 1.0f / Defines.FRAMETIME
        self.owner.velocity[1] = dir[1] * 1.0f / Defines.FRAMETIME

        // z
        angle = self.s.angles[Defines.PITCH] * (Math.PI * 2f / 360f).toFloat()
        val target_z = (self.s.origin[2] + self.owner.move_origin[0] * tan(angle.toDouble()) + self.owner.move_origin[2]).toFloat().snapToEights()
        val diff = target_z - self.owner.s.origin[2]
        self.owner.velocity[2] = diff * 1.0f / Defines.FRAMETIME

        // terribly wrong: the driver passes the firing "flag" in this bit
        if (self.spawnflags and 65536 != 0) {
            turretBreachFireRocket(self, game)
            self.spawnflags = self.spawnflags and 65536.inv()
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

