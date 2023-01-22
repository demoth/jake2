package jake2.game.func

import jake2.game.GameCombat
import jake2.game.GameDefines
import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.game.adapters.SuperAdapter.Companion.registerBlocked
import jake2.game.adapters.SuperAdapter.Companion.registerTouch
import jake2.game.adapters.SuperAdapter.Companion.registerUse
import jake2.game.hasSpawnFlag
import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.util.Math3D

/**
 * QUAKED func_rotating (0 .5 .8) ? 
 * START_ON - 1
 * REVERSE - 2
 * X_AXIS - 4
 * Y_AXIS - 8
 * TOUCH_PAIN - 16
 * STOP - 32
 * ANIMATED - 64
 * ANIMATED_FAST - 128
 * You need to have an origin brush
 * as part of this entity. The center of that brush will be the point around
 * which it is rotated. It will rotate around the Z axis by default. You can
 * check either the X_AXIS or Y_AXIS box to change that.
 *
 * "speed" determines how fast it moves; default value is 100. 
 * "dmg" damage to inflict when blocked or touched (2 default)
 *
 * REVERSE will cause it to rotate in the opposite direction. 
 * STOP mean it will stop moving instead of pushing entities
 */
private const val START_ON = 1
private const val REVERSE = 2
private const val X_AXIS = 4
private const val Y_AXIS = 8
private const val TOUCH_PAIN = 16
private const val STOP = 32
private const val ANIMATED = 64
private const val ANIMATED_FAST = 128
fun funcRotating(self: SubgameEntity, game: GameExportsImpl) {
    self.solid = Defines.SOLID_BSP
    if (self.hasSpawnFlag(STOP))
        self.movetype = GameDefines.MOVETYPE_STOP
    else
        self.movetype = GameDefines.MOVETYPE_PUSH

    // set the axis of rotation
    Math3D.VectorClear(self.movedir)
    if (self.hasSpawnFlag(X_AXIS))
        self.movedir[2] = 1.0f
    else if (self.hasSpawnFlag(Y_AXIS))
        self.movedir[0] = 1.0f
    else  // Z_AXIS
        self.movedir[1] = 1.0f


    // check for reverse rotation
    if (self.hasSpawnFlag(REVERSE))
        Math3D.VectorNegate(self.movedir, self.movedir)

    if (self.speed == 0f)
        self.speed = 100f
    if (self.dmg == 0)
        self.dmg = 2

    //		ent.moveinfo.sound_middle = "doors/hydro1.wav";
    self.use = rotatingUse
    if (self.dmg != 0) 
        self.blocked = rotatingBlocked
    if (self.hasSpawnFlag(START_ON))
        self.use.use(self, null, null, game)
    if (self.hasSpawnFlag(ANIMATED))
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALL
    if (self.hasSpawnFlag(ANIMATED_FAST))
        self.s.effects = self.s.effects or Defines.EF_ANIM_ALLFAST

    game.gameImports.setmodel(self, self.model)
    game.gameImports.linkentity(self)
}

private val rotatingUse = registerUse("rotating_use") { self, _, _, _ ->
    if (!Math3D.VectorEquals(self.avelocity, Globals.vec3_origin)) {
        self.s.sound = 0
        Math3D.VectorClear(self.avelocity)
        self.touch = null
    } else {
        self.s.sound = self.moveinfo.sound_middle
        Math3D.VectorScale(self.movedir, self.speed, self.avelocity)
        if (self.hasSpawnFlag(TOUCH_PAIN))
            self.touch = rotatingTouch
    }
}

// does damage to touching entity if rotating
private val rotatingTouch = registerTouch("rotating_touch") { self, other, plane, surf, game ->
    if (self.avelocity[0] != 0f || self.avelocity[1] != 0f || self.avelocity[2] != 0f)
        GameCombat.T_Damage(
            other, self, self, Globals.vec3_origin,
            other.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
            GameDefines.MOD_CRUSH, game
        )
}

private val rotatingBlocked = registerBlocked("rotating_blocked") { self, obstacle, game ->
    GameCombat.T_Damage(
        obstacle, self, self, Globals.vec3_origin,
        obstacle.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
        GameDefines.MOD_CRUSH, game
    )
}
