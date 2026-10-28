package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*
import jake2.qcommon.player_state_t
import jake2.qcommon.util.Math3D
import java.util.Collections

/**
 * Selected player-state fields in their quantized wire form. Null means inherit from the delta source;
 * an explicit zero remains a transmitted reset. Prediction shorts are bit-precise; view/gun offsets
 * and kicks use signed quarter units, view angles use signed angle shorts, and blend uses byte channels.
 */
data class PlayerInfoMessage(
    val movementType: Int? = null,
    val origin: PlayerDeltaVector? = null,
    val velocity: PlayerDeltaVector? = null,
    val movementTime: Int? = null,
    val movementFlags: Int? = null,
    val gravity: Int? = null,
    val deltaAngles: PlayerDeltaVector? = null,
    val viewOffset: PlayerDeltaVector? = null,
    val viewAngles: PlayerDeltaVector? = null,
    val kickAngles: PlayerDeltaVector? = null,
    val weaponIndex: Int? = null,
    val weaponFrame: PlayerWeaponFrame? = null,
    val blend: PlayerBlend? = null,
    val fov: Int? = null,
    val renderFlags: Int? = null,
    val stats: PlayerStatDelta = PlayerStatDelta(),
    /** Protocol-34's unused high mask bit is retained when decoding/re-encoding. */
    val extraFlags: Int = 0,
) : ServerMessage(ServerMessageType.svc_playerinfo) {
    val deltaFlags: Int get() {
        var flags = extraFlags
        if (movementType != null) flags = flags or PS_M_TYPE
        if (origin != null) flags = flags or PS_M_ORIGIN
        if (velocity != null) flags = flags or PS_M_VELOCITY
        if (movementTime != null) flags = flags or PS_M_TIME
        if (movementFlags != null) flags = flags or PS_M_FLAGS
        if (gravity != null) flags = flags or PS_M_GRAVITY
        if (deltaAngles != null) flags = flags or PS_M_DELTA_ANGLES
        if (viewOffset != null) flags = flags or PS_VIEWOFFSET
        if (viewAngles != null) flags = flags or PS_VIEWANGLES
        if (kickAngles != null) flags = flags or PS_KICKANGLES
        if (weaponIndex != null) flags = flags or PS_WEAPONINDEX
        if (weaponFrame != null) flags = flags or PS_WEAPONFRAME
        if (blend != null) flags = flags or PS_BLEND
        if (fov != null) flags = flags or PS_FOV
        if (renderFlags != null) flags = flags or PS_RDFLAGS
        return flags
    }

    init {
        require(extraFlags and 0x8000.inv() == 0)
        movementType?.let { require(it in 0..255) }
        origin?.requireRange(-32768..32767)
        velocity?.requireRange(-32768..32767)
        movementTime?.let { require(it in 0..255) }
        movementFlags?.let { require(it in 0..255) }
        gravity?.let { require(it in -32768..32767) }
        deltaAngles?.requireRange(-32768..32767)
        viewOffset?.requireRange(-128..127)
        viewAngles?.requireRange(-32768..32767)
        kickAngles?.requireRange(-128..127)
        weaponIndex?.let { require(it in 0..255) }
        fov?.let { require(it in 0..255) }
        renderFlags?.let { require(it in 0..255) }
    }

    /** Merge selected movement, view/weapon fields, and changed stats into an already seeded state. */
    fun applyTo(target: player_state_t) {
        // Parse the pmove_state_t, preserving bit-accurate prediction values.
        movementType?.let { target.pmove.pm_type = it }
        origin?.copyShortsTo(target.pmove.origin)
        velocity?.copyShortsTo(target.pmove.velocity)
        movementTime?.let { target.pmove.pm_time = it.toByte() }
        movementFlags?.let { target.pmove.pm_flags = it.toByte() }
        gravity?.let { target.pmove.gravity = it.toShort() }
        deltaAngles?.copyShortsTo(target.pmove.delta_angles)
        // Parse the rest of the player_state_t; unselected values remain inherited.
        viewOffset?.copyQuarterUnitsTo(target.viewoffset)
        viewAngles?.copyAngleShortsTo(target.viewangles)
        kickAngles?.copyQuarterUnitsTo(target.kick_angles)
        weaponIndex?.let { target.gunindex = it }
        weaponFrame?.let {
            target.gunframe = it.frame
            it.offset.copyQuarterUnitsTo(target.gunoffset)
            it.angles.copyQuarterUnitsTo(target.gunangles)
        }
        blend?.let {
            target.blend[0] = it.red / 255f; target.blend[1] = it.green / 255f
            target.blend[2] = it.blue / 255f; target.blend[3] = it.alpha / 255f
        }
        fov?.let { target.fov = it.toFloat() }
        renderFlags?.let { target.rdflags = it }
        // Copy only changed stats, including bit 31 of the stat mask.
        for (i in 0 until MAX_STATS) stats[i]?.let { target.stats[i] = it.toShort() }
    }

    companion object {
        /** Determine what needs sending and snapshot it now, without retaining engine state. */
        @JvmStatic fun fromGame(previous: player_state_t, current: player_state_t): PlayerInfoMessage =
            PlayerStateCodec.plan(previous, current)
    }
}

/** Three raw wire components; field context determines short, angle-short, or signed quarter-byte units. */
data class PlayerDeltaVector(val x: Int, val y: Int, val z: Int) {
    internal fun requireRange(range: IntRange) { require(x in range && y in range && z in range) }
    internal fun copyShortsTo(target: ShortArray) {
        target[0] = x.toShort(); target[1] = y.toShort(); target[2] = z.toShort()
    }
    internal fun copyAngleShortsTo(target: FloatArray) {
        target[0] = Math3D.SHORT2ANGLE(x); target[1] = Math3D.SHORT2ANGLE(y); target[2] = Math3D.SHORT2ANGLE(z)
    }
    internal fun copyQuarterUnitsTo(target: FloatArray) {
        target[0] = x * 0.25f; target[1] = y * 0.25f; target[2] = z * 0.25f
    }
}

/** Offsets and angles accompany the weapon frame as a single wire group. */
data class PlayerWeaponFrame(val frame: Int, val offset: PlayerDeltaVector, val angles: PlayerDeltaVector) {
    init { require(frame in 0..255); offset.requireRange(-128..127); angles.requireRange(-128..127) }
}

/** RGBA full-screen effect channels, retained as bytes to avoid float re-quantization on re-encoding. */
data class PlayerBlend(val red: Int, val green: Int, val blue: Int, val alpha: Int) {
    init { require(red in 0..255 && green in 0..255 && blue in 0..255 && alpha in 0..255) }
}

/** Owned selected signed-short stats. Missing keys inherit; present zeroes reset. */
class PlayerStatDelta(values: Map<Int, Int> = emptyMap()) {
    private val values: Map<Int, Int> = Collections.unmodifiableMap(HashMap(values))
    val mask: Int = this.values.keys.fold(0) { mask, index -> mask or (1 shl index) }
    init { require(this.values.all { (index, value) -> index in 0 until MAX_STATS && value in -32768..32767 }) }
    operator fun get(index: Int): Int? = values[index]
    override fun equals(other: Any?) = other is PlayerStatDelta && values == other.values
    override fun hashCode() = values.hashCode()
    override fun toString() = values.toString()
}
