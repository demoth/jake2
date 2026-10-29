package jake2.game.components

import jake2.game.adapters.EntThinkAdapter
import jake2.game.adapters.SuperAdapter
import jake2.qcommon.math.toVector3f
import jake2.game.func.MovementState
import jake2.qcommon.math.Vector3f
import kotlin.math.sqrt

/**
 * Move info represents a fixed precalculated movement, like for buttons, door and elevators
 */
data class MoveInfo(
    // fixed data
    var start_origin: Vector3f = Vector3f.zero,
    var start_angles: Vector3f = Vector3f.zero,
    var end_origin: Vector3f = Vector3f.zero,
    var end_angles: Vector3f = Vector3f.zero,
    var sound_start: Int = 0,
    var sound_middle: Int = 0,
    var sound_end: Int = 0,
    var accel: Float = 0f,
    var speed: Float = 0f,
    var decel: Float = 0f,
    var distance: Float = 0f,
    var wait: Float = 0f,

    // state data
    var state: MovementState? = null,
    var dir: Vector3f = Vector3f.zero,
    var current_speed: Float = 0f,
    private var move_speed: Float = 0f,
    private var next_speed: Float = 0f,
    var remaining_distance: Float = 0f,
    private var decel_distance: Float = 0f,
    var endfunc: EntThinkAdapter? = null
) {
    /** Capture all movement state, including private acceleration bookkeeping. */
    fun snapshot() = MoveInfoSnapshot(
        startOrigin = start_origin.toArray(),
        startAngles = start_angles.toArray(),
        endOrigin = end_origin.toArray(),
        endAngles = end_angles.toArray(),
        soundStart = sound_start,
        soundMiddle = sound_middle,
        soundEnd = sound_end,
        accel = accel,
        speed = speed,
        decel = decel,
        distance = distance,
        wait = wait,
        state = state,
        dir = dir.toArray(),
        currentSpeed = current_speed,
        moveSpeed = move_speed,
        nextSpeed = next_speed,
        remainingDistance = remaining_distance,
        decelDistance = decel_distance,
        endFuncId = endfunc?.iD
    )

    /**
     * plat_CalcAcceleratedMove
     */
    fun platCalculateAcceleratedMove() {
        move_speed = speed
        if (remaining_distance < accel) {
            current_speed = remaining_distance
            return
        }
        val accel_dist = accelerationDistance(speed, accel)
        var decel_dist = accelerationDistance(speed, decel)
        if (remaining_distance - accel_dist - decel_dist < 0) {
            val f = (accel + decel) / (accel * decel)
            move_speed = ((-2 + sqrt((4 - 4 * f * (-2 * remaining_distance)))) / (2 * f))
            decel_dist = accelerationDistance(move_speed, decel)
        }
        decel_distance = decel_dist
    }

    private fun accelerationDistance(target: Float, rate: Float): Float {
        return target * (target / rate + 1) / 2
    }

    /**
     * plat_Accelerate
     */
    fun platAccelerate() {
        // are we decelerating?
        if (remaining_distance <= decel_distance) {
            if (remaining_distance < decel_distance) {
                if (next_speed != 0f) {
                    current_speed = next_speed
                    next_speed = 0f
                    return
                }
                if (current_speed > decel)
                    current_speed -= decel
            }
            return
        }

        // are we at full speed and need to start decelerating during this move?
        if (current_speed == move_speed) {
            if (remaining_distance - current_speed < decel_distance) {
                val p1_distance: Float = remaining_distance - decel_distance
                val p2_distance = move_speed * (1.0f - p1_distance / move_speed)
                val distance = p1_distance + p2_distance
                current_speed = move_speed
                next_speed = move_speed - decel * (p2_distance / distance)
                return
            }
        }

        // are we accelerating?
        if (current_speed < speed) {
            val old_speed = current_speed

            // figure simple acceleration up to move_speed
            current_speed += accel
            if (current_speed > speed) current_speed = speed

            // are we accelerating throughout this entire move?
            if (remaining_distance - current_speed >= decel_distance)
                return

            // during this move we will accelerate from current_speed to move_speed
            // and cross over the decel_distance; figure the average speed for
            // the entire move
            val p1_distance = remaining_distance - decel_distance
            val p1_speed = (old_speed + move_speed) / 2.0f
            val p2_distance = move_speed * (1.0f - p1_distance / p1_speed)
            val distance = p1_distance + p2_distance
            current_speed = p1_speed * (p1_distance / distance) + move_speed * (p2_distance / distance)
            next_speed = move_speed - decel * (p2_distance / distance)
            return
        }
        // we are at constant velocity (move_speed)
    }

}
/** Persisted mover data; callbacks are stored by adapter ID, never as runtime objects. */
data class MoveInfoSnapshot(
    val startOrigin: FloatArray,
    val startAngles: FloatArray,
    val endOrigin: FloatArray,
    val endAngles: FloatArray,
    val soundStart: Int,
    val soundMiddle: Int,
    val soundEnd: Int,
    val accel: Float,
    val speed: Float,
    val decel: Float,
    val distance: Float,
    val wait: Float,
    val state: MovementState?,
    val dir: FloatArray,
    val currentSpeed: Float,
    val moveSpeed: Float,
    val nextSpeed: Float,
    val remainingDistance: Float,
    val decelDistance: Float,
    val endFuncId: String?
) {
    fun restore() = MoveInfo(
        start_origin = startOrigin.toVector3f(),
        start_angles = startAngles.toVector3f(),
        end_origin = endOrigin.toVector3f(),
        end_angles = endAngles.toVector3f(),
        sound_start = soundStart,
        sound_middle = soundMiddle,
        sound_end = soundEnd,
        accel = accel,
        speed = speed,
        decel = decel,
        distance = distance,
        wait = wait,
        state = state,
        dir = dir.toVector3f(),
        current_speed = currentSpeed,
        move_speed = moveSpeed,
        next_speed = nextSpeed,
        remaining_distance = remainingDistance,
        decel_distance = decelDistance,
        endfunc = endFuncId?.let { requireNotNull(SuperAdapter.think(it)) { "Unknown mover callback: $it" } }
    )
}
