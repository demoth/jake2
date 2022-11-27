package jake2.game.func

import jake2.game.GameDefines
import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.game.adapters.EntThinkAdapter
import jake2.game.adapters.SuperAdapter
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.moveinfo_t
import jake2.qcommon.Defines
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
fun startMovement(self: SubgameEntity, dest: FloatArray, endFunction: EntThinkAdapter?, game: GameExportsImpl) {
    Math3D.VectorClear(self.velocity)
    Math3D.VectorSubtract(dest, self.s.origin, self.moveinfo.dir)

    self.moveinfo.remaining_distance = Math3D.VectorNormalize(self.moveinfo.dir)
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
    Math3D.VectorScale(self.moveinfo.dir, self.moveinfo.speed, self.velocity)
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

    Math3D.VectorScale(
        self.moveinfo.dir,
        self.moveinfo.remaining_distance / Defines.FRAMETIME,
        self.velocity
    )

    self.think.action = moveDone
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    true

}

private val moveDone = registerThink("move_done") { self, game ->
    Math3D.VectorClear(self.velocity)
    self.moveinfo.endfunc.think(self, game)
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
        platCalculateAcceleratedMove(self.moveinfo)

    platAccelerate(self.moveinfo)

    // will the entire move complete on next frame?
    if (self.moveinfo.remaining_distance <= self.moveinfo.current_speed) {
        moveFinal.think(self, game)
        return@registerThink true
    }

    Math3D.VectorScale(self.moveinfo.dir, self.moveinfo.current_speed * 10, self.velocity)
    self.think.nextTime = game.level.time + Defines.FRAMETIME
    self.think.action = SuperAdapter.think("thinc_accelmove") // hack to have a pointer while initializing the function
    true
}

// todo: move to moveinfo_t
/**
 * plat_CalcAcceleratedMove 
 */
private fun platCalculateAcceleratedMove(moveinfo: moveinfo_t) {
    moveinfo.move_speed = moveinfo.speed
    if (moveinfo.remaining_distance < moveinfo.accel) {
        moveinfo.current_speed = moveinfo.remaining_distance
        return
    }
    val accel_dist = accelerationDistance(moveinfo.speed, moveinfo.accel)
    var decel_dist = accelerationDistance(moveinfo.speed, moveinfo.decel)
    if (moveinfo.remaining_distance - accel_dist - decel_dist < 0) {
        val f = (moveinfo.accel + moveinfo.decel) / (moveinfo.accel * moveinfo.decel)
        moveinfo.move_speed =
            ((-2 + Math.sqrt((4 - 4 * f * (-2 * moveinfo.remaining_distance)).toDouble())) / (2 * f)).toFloat()
        decel_dist = accelerationDistance(moveinfo.move_speed, moveinfo.decel)
    }
    moveinfo.decel_distance = decel_dist
}

// todo: move to moveinfo_t
/**
 * plat_Accelerate
 */
private fun platAccelerate(moveinfo: moveinfo_t) {
    // are we decelerating?
    if (moveinfo.remaining_distance <= moveinfo.decel_distance) {
        if (moveinfo.remaining_distance < moveinfo.decel_distance) {
            if (moveinfo.next_speed != 0f) {
                moveinfo.current_speed = moveinfo.next_speed
                moveinfo.next_speed = 0f
                return
            }
            if (moveinfo.current_speed > moveinfo.decel)
                moveinfo.current_speed -= moveinfo.decel
        }
        return
    }

    // are we at full speed and need to start decelerating during this move?
    if (moveinfo.current_speed == moveinfo.move_speed) {
        if (moveinfo.remaining_distance - moveinfo.current_speed < moveinfo.decel_distance) {
            val p1_distance: Float = moveinfo.remaining_distance - moveinfo.decel_distance
            val p2_distance = moveinfo.move_speed * (1.0f - p1_distance / moveinfo.move_speed)
            val distance = p1_distance + p2_distance
            moveinfo.current_speed = moveinfo.move_speed
            moveinfo.next_speed = moveinfo.move_speed - moveinfo.decel * (p2_distance / distance)
            return
        }
    }

    // are we accelerating?
    if (moveinfo.current_speed < moveinfo.speed) {
        val old_speed = moveinfo.current_speed

        // figure simple acceleration up to move_speed
        moveinfo.current_speed += moveinfo.accel
        if (moveinfo.current_speed > moveinfo.speed) moveinfo.current_speed = moveinfo.speed

        // are we accelerating throughout this entire move?
        if (moveinfo.remaining_distance - moveinfo.current_speed >= moveinfo.decel_distance)
            return

        // during this move we will accelerate from current_speed to move_speed
        // and cross over the decel_distance; figure the average speed for
        // the entire move
        val p1_distance = moveinfo.remaining_distance - moveinfo.decel_distance
        val p1_speed = (old_speed + moveinfo.move_speed) / 2.0f
        val p2_distance = moveinfo.move_speed * (1.0f - p1_distance / p1_speed)
        val distance = p1_distance + p2_distance
        moveinfo.current_speed = p1_speed * (p1_distance / distance) + moveinfo.move_speed * (p2_distance / distance)
        moveinfo.next_speed = moveinfo.move_speed - moveinfo.decel * (p2_distance / distance)
        return
    }
    // we are at constant velocity (move_speed)
}

private fun accelerationDistance(target: Float, rate: Float): Float {
    return target * (target / rate + 1) / 2
}
