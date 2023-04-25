package jake2.game.func

import jake2.game.GameDefines
import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.game.adapters.EntThinkAdapter
import jake2.game.adapters.SuperAdapter
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.util.Math3D
import kotlin.math.floor

enum class MovementState {
    TOP,
    UP,
    DOWN,
    BOTTOM
}

/**
 * Move_Calc
 */
fun startMovement(self: SubgameEntity, dest: Vector3f, endFunction: EntThinkAdapter?, game: GameExportsImpl) {
    Math3D.VectorClear(self.velocity)
    // Math3D.VectorSubtract(dest, self.s.origin, self.moveinfo.dir)
    self.moveinfo.dir = dest - Vector3f(self.s.origin)

    // self.moveinfo.remaining_distance = Math3D.VectorNormalize(self.moveinfo.dir)
    self.moveinfo.remaining_distance = self.moveinfo.dir.length()
    self.moveinfo.dir = self.moveinfo.dir.normalize()

    self.moveinfo.endfunc = endFunction

    if (self.moveinfo.speed == self.moveinfo.accel && self.moveinfo.speed == self.moveinfo.decel) {
        //  steady movement, reached final speed
        val teamMaster = self.flags and GameDefines.FL_TEAMSLAVE == 0
        if (game.level.current_entity === (if (teamMaster) self else self.teammaster)) {
            moveBegin.think(self, game)
        } else {
            self.think.nextTime = game.level.time + Defines.FRAMETIME
            self.think.action = moveBegin
        }
    } else {
        // accelerate
        self.moveinfo.current_speed = 0f
        self.think.action = accelMove
        self.think.nextTime = game.level.time + Defines.FRAMETIME
    }
}

private val moveBegin = registerThink("move_begin") { self, game ->
    if (self.moveinfo.speed * Defines.FRAMETIME >= self.moveinfo.remaining_distance) {
        moveFinal.think(self, game)
        return@registerThink true
    }
    //Math3D.VectorScale(self.moveinfo.dir, self.moveinfo.speed, self.velocity)
    self.velocity = (self.moveinfo.dir * self.moveinfo.speed).toArray()
    val frames =
        floor((self.moveinfo.remaining_distance / self.moveinfo.speed / Defines.FRAMETIME).toDouble()).toFloat()
    self.moveinfo.remaining_distance -= frames * self.moveinfo.speed * Defines.FRAMETIME
    self.think.nextTime = game.level.time + frames * Defines.FRAMETIME
    self.think.action = moveFinal
    true

}

private val moveFinal = registerThink("move_final") { self, game ->
    if (self.moveinfo.remaining_distance == 0f) {
        moveDone.think(self, game)
        return@registerThink true
    }

    // Math3D.VectorScale(
    //    self.moveinfo.dir,
    //    self.moveinfo.remaining_distance / Defines.FRAMETIME,
    //    self.velocity
    // )
    self.velocity = (self.moveinfo.dir * self.moveinfo.remaining_distance / Defines.FRAMETIME).toArray()

    self.think.action = moveDone
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    true

}

private val moveDone = registerThink("move_done") { self, game ->
    Math3D.VectorClear(self.velocity)
    self.moveinfo.endfunc?.think(self, game)
    true
}

/**
 * Think_AccelMove
 *
 * The team has completed a frame of movement, so change the speed for the next frame.
 */
private val accelMove: EntThinkAdapter = registerThink("thinc_accelmove") { self, game ->
    self.moveinfo.remaining_distance -= self.moveinfo.current_speed

    if (self.moveinfo.current_speed == 0f) // starting or blocked
        self.moveinfo.platCalculateAcceleratedMove()

    self.moveinfo.platAccelerate()

    // will the entire move complete on next frame?
    if (self.moveinfo.remaining_distance <= self.moveinfo.current_speed) {
        moveFinal.think(self, game)
        return@registerThink true
    }

    // Math3D.VectorScale(self.moveinfo.dir, self.moveinfo.current_speed * 10, self.velocity)
    self.velocity = (self.moveinfo.dir * self.moveinfo.current_speed * 10f).toArray()
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    self.think.action = SuperAdapter.think("thinc_accelmove") // hack to have a pointer while initializing the function
    true
}


