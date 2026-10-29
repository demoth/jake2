package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*
import jake2.qcommon.player_state_t
import jake2.qcommon.sizebuf_t
import jake2.qcommon.util.Math3D
import jake2.qcommon.network.protocol.MessageReader

/** Player delta selection is separate from encoding an already selected immutable wire value. */
internal object PlayerStateCodec {
    fun plan(previous: player_state_t, current: player_state_t): PlayerInfoMessage = PlayerInfoMessage(
        movementType = if (current.pmove.pm_type != previous.pmove.pm_type) current.pmove.pm_type and 255 else null,
        origin = if (different(current.pmove.origin, previous.pmove.origin)) shorts(current.pmove.origin) else null,
        velocity = if (different(current.pmove.velocity, previous.pmove.velocity)) shorts(current.pmove.velocity) else null,
        movementTime = if (current.pmove.pm_time != previous.pmove.pm_time) current.pmove.pm_time.toInt() and 255 else null,
        movementFlags = if (current.pmove.pm_flags != previous.pmove.pm_flags) current.pmove.pm_flags.toInt() and 255 else null,
        gravity = if (current.pmove.gravity != previous.pmove.gravity) current.pmove.gravity.toInt() else null,
        deltaAngles = if (different(current.pmove.delta_angles, previous.pmove.delta_angles)) shorts(current.pmove.delta_angles) else null,
        viewOffset = if (different(current.viewoffset, previous.viewoffset)) quarterBytes(current.viewoffset) else null,
        viewAngles = if (different(current.viewangles, previous.viewangles)) angleShorts(current.viewangles) else null,
        kickAngles = if (different(current.kick_angles, previous.kick_angles)) quarterBytes(current.kick_angles) else null,
        // Compare against the acknowledged reference, including resets to zero.
        // Different from vanilla server encoding behavior, but should be compatible with most clients
        weaponIndex = if (current.gunindex != previous.gunindex) current.gunindex and 255 else null,
        // Gun offsets/angles are sent only when gunframe changes, preserving protocol-34 behavior.
        weaponFrame = if (current.gunframe != previous.gunframe) PlayerWeaponFrame(current.gunframe and 255, quarterBytes(current.gunoffset), quarterBytes(current.gunangles)) else null,
        blend = if (different(current.blend, previous.blend, 4)) PlayerBlend((current.blend[0] * 255).toInt() and 255, (current.blend[1] * 255).toInt() and 255, (current.blend[2] * 255).toInt() and 255, (current.blend[3] * 255).toInt() and 255) else null,
        fov = if (current.fov != previous.fov) current.fov.toInt() and 255 else null,
        renderFlags = if (current.rdflags != previous.rdflags) current.rdflags and 255 else null,
        stats = PlayerStatDelta((0 until MAX_STATS).filter { current.stats[it] != previous.stats[it] }
            .associateWith { current.stats[it].toInt() }),
    )

    // Changes smaller than one wire unit still set the field mask.
    private fun different(a: FloatArray, b: FloatArray, count: Int = 3): Boolean = (0 until count).any { a[it] != b[it] }
    private fun different(a: ShortArray, b: ShortArray): Boolean = (0 until 3).any { a[it] != b[it] }
    private fun shorts(v: ShortArray) = PlayerDeltaVector(v[0].toInt(), v[1].toInt(), v[2].toInt())
    private fun angleShorts(v: FloatArray) = PlayerDeltaVector(
        Math3D.ANGLE2SHORT(v[0]).toShort().toInt(), Math3D.ANGLE2SHORT(v[1]).toShort().toInt(), Math3D.ANGLE2SHORT(v[2]).toShort().toInt())
    private fun quarterBytes(v: FloatArray) = PlayerDeltaVector(
        (v[0] * 4).toInt().toByte().toInt(), (v[1] * 4).toInt().toByte().toInt(), (v[2] * 4).toInt().toByte().toInt())

    fun write(message: PlayerInfoMessage, buffer: sizebuf_t) {
        buffer.writeShort(message.deltaFlags)
        // Write the pmove_state_t, then the rest of the player_state_t.
        message.movementType?.let { buffer.writeByte(it.toByte()) }
        message.origin?.let { writeVector(it, buffer, true) }
        message.velocity?.let { writeVector(it, buffer, true) }
        message.movementTime?.let { buffer.writeByte(it.toByte()) }
        message.movementFlags?.let { buffer.writeByte(it.toByte()) }
        message.gravity?.let { buffer.writeShort(it) }
        message.deltaAngles?.let { writeVector(it, buffer, true) }
        message.viewOffset?.let { writeVector(it, buffer, false) }
        message.viewAngles?.let { writeVector(it, buffer, true) }
        message.kickAngles?.let { writeVector(it, buffer, false) }
        message.weaponIndex?.let { buffer.writeByte(it.toByte()) }
        message.weaponFrame?.let { buffer.writeByte(it.frame.toByte()); writeVector(it.offset, buffer, false); writeVector(it.angles, buffer, false) }
        message.blend?.let { buffer.writeByte(it.red.toByte()); buffer.writeByte(it.green.toByte()); buffer.writeByte(it.blue.toByte()); buffer.writeByte(it.alpha.toByte()) }
        message.fov?.let { buffer.writeByte(it.toByte()) }
        message.renderFlags?.let { buffer.writeByte(it.toByte()) }
        buffer.writeInt(message.stats.mask)
        for (i in 0 until MAX_STATS) message.stats[i]?.let { buffer.writeShort(it) }
    }

    private fun writeVector(v: PlayerDeltaVector, buffer: sizebuf_t, short: Boolean) {
        if (short) { buffer.writeShort(v.x); buffer.writeShort(v.y); buffer.writeShort(v.z) }
        else { buffer.writeByte(v.x.toByte()); buffer.writeByte(v.y.toByte()); buffer.writeByte(v.z.toByte()) }
    }

    fun read(buffer: MessageReader): PlayerInfoMessage {
        val flags = buffer.readUnsignedShort()
        val movementType = if (flags and PS_M_TYPE != 0) buffer.readUnsignedByte() else null
        val origin = if (flags and PS_M_ORIGIN != 0) readVector(buffer, true) else null
        val velocity = if (flags and PS_M_VELOCITY != 0) readVector(buffer, true) else null
        val movementTime = if (flags and PS_M_TIME != 0) buffer.readUnsignedByte() else null
        val movementFlags = if (flags and PS_M_FLAGS != 0) buffer.readUnsignedByte() else null
        val gravity = if (flags and PS_M_GRAVITY != 0) buffer.readSignedShort() else null
        val deltaAngles = if (flags and PS_M_DELTA_ANGLES != 0) readVector(buffer, true) else null
        val viewOffset = if (flags and PS_VIEWOFFSET != 0) readVector(buffer, false) else null
        val viewAngles = if (flags and PS_VIEWANGLES != 0) readVector(buffer, true) else null
        val kickAngles = if (flags and PS_KICKANGLES != 0) readVector(buffer, false) else null
        val weaponIndex = if (flags and PS_WEAPONINDEX != 0) buffer.readUnsignedByte() else null
        val weaponFrame = if (flags and PS_WEAPONFRAME != 0) PlayerWeaponFrame(buffer.readUnsignedByte(), readVector(buffer, false), readVector(buffer, false)) else null
        val blend = if (flags and PS_BLEND != 0) PlayerBlend(buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte()) else null
        val fov = if (flags and PS_FOV != 0) buffer.readUnsignedByte() else null
        val renderFlags = if (flags and PS_RDFLAGS != 0) buffer.readUnsignedByte() else null
        // Parse only selected stats; signed mask bit 31 is a valid stat slot.
        val statMask = buffer.readInt()
        val stats = PlayerStatDelta((0 until MAX_STATS).filter { statMask and (1 shl it) != 0 }
            .associateWith { buffer.readSignedShort() })
        return PlayerInfoMessage(movementType, origin, velocity, movementTime, movementFlags, gravity, deltaAngles, viewOffset, viewAngles, kickAngles, weaponIndex, weaponFrame, blend, fov, renderFlags, stats, flags and 0x8000)
    }

    private fun readVector(buffer: MessageReader, short: Boolean): PlayerDeltaVector =
        if (short) PlayerDeltaVector(buffer.readSignedShort(), buffer.readSignedShort(), buffer.readSignedShort())
        else PlayerDeltaVector(buffer.readSignedByte().toInt(), buffer.readSignedByte().toInt(), buffer.readSignedByte().toInt())

    fun size(message: PlayerInfoMessage): Int = 6 + 2 * Integer.bitCount(message.stats.mask) +
        (if (message.movementType != null) 1 else 0) +
        (if (message.origin != null) 6 else 0) +
        (if (message.velocity != null) 6 else 0) +
        (if (message.movementTime != null) 1 else 0) +
        (if (message.movementFlags != null) 1 else 0) +
        (if (message.gravity != null) 2 else 0) +
        (if (message.deltaAngles != null) 6 else 0) +
        (if (message.viewOffset != null) 3 else 0) +
        (if (message.viewAngles != null) 6 else 0) +
        (if (message.kickAngles != null) 3 else 0) +
        (if (message.weaponIndex != null) 1 else 0) +
        (if (message.weaponFrame != null) 7 else 0) +
        (if (message.blend != null) 4 else 0) +
        (if (message.fov != null) 1 else 0) +
        (if (message.renderFlags != null) 1 else 0)
}
