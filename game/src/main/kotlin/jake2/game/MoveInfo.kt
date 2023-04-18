package jake2.game

import jake2.game.adapters.EntThinkAdapter
import jake2.game.func.MovementState
import jake2.qcommon.math.Vector3f

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
    var move_speed: Float = 0f,
    var next_speed: Float = 0f,
    var remaining_distance: Float = 0f,
    var decel_distance: Float = 0f,
    var endfunc: EntThinkAdapter? = null
)
