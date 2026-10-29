package jake2.qcommon.network.messages.client

import jake2.qcommon.Defines.*
import jake2.qcommon.network.protocol.MessageReader
import jake2.qcommon.sizebuf_t

/** Three command deltas and their sequence-dependent CRC. No mutable checksum scratch state is retained. */
object MovementCodec {
    private val zero = UserCommand()

    fun writeProperties(message: MoveMessage, buffer: sizebuf_t, sequence: Int) {
        require(sequence >= 0) { "Movement sequence must be non-negative" }
        val checksumIndex = buffer.cursize
        // Reserve a byte and fill it after the complete movement payload is written.
        buffer.writeByte(0)
        buffer.writeInt(message.lastReceivedFrame)
        writeDelta(zero, message.oldestCmd, buffer)
        writeDelta(message.oldestCmd, message.oldCmd, buffer)
        writeDelta(message.oldCmd, message.newCmd, buffer)
        buffer.data[checksumIndex] = CRC.BlockSequenceCRCByte(buffer.data, checksumIndex + 1,
            buffer.cursize - checksumIndex - 1, sequence)
    }

    /** Includes the client opcode, CRC byte, frame marker, and all three command deltas. */
    fun size(message: MoveMessage): Int = 6 + deltaSize(flags(zero, message.oldestCmd)) +
        deltaSize(flags(message.oldestCmd, message.oldCmd)) + deltaSize(flags(message.oldCmd, message.newCmd))

    fun read(buffer: MessageReader, sequence: Int): MoveMessage {
        require(sequence >= 0) { "Movement sequence must be non-negative" }
        val checksum = buffer.readUnsignedByte()
        val payloadStart = buffer.readcount
        val frame = buffer.readInt()
        val oldest = readDelta(zero, buffer)
        val old = readDelta(oldest, buffer)
        val newest = readDelta(old, buffer)
        val calculated = CRC.BlockSequenceCRCByte(buffer.data, payloadStart, buffer.readcount - payloadStart, sequence).toInt() and 255
        return MoveMessage(frame, oldest, old, newest, calculated == checksum)
    }

    private fun flags(from: UserCommand, to: UserCommand): Int =
        (if (from.angles.pitch != to.angles.pitch) CM_ANGLE1 else 0) or
        (if (from.angles.yaw != to.angles.yaw) CM_ANGLE2 else 0) or
        (if (from.angles.roll != to.angles.roll) CM_ANGLE3 else 0) or
        (if (from.forwardmove != to.forwardmove) CM_FORWARD else 0) or
        (if (from.sidemove != to.sidemove) CM_SIDE else 0) or
        (if (from.upmove != to.upmove) CM_UP else 0) or
        (if (from.buttons != to.buttons) CM_BUTTONS else 0) or
        (if (from.impulse != to.impulse) CM_IMPULSE else 0)

    private fun deltaSize(flags: Int): Int {
        val shorts = flags and (CM_ANGLE1 or CM_ANGLE2 or CM_ANGLE3 or CM_FORWARD or CM_SIDE or CM_UP)
        val bytes = flags and (CM_BUTTONS or CM_IMPULSE)
        return 3 + 2 * Integer.bitCount(shorts) + Integer.bitCount(bytes)
    }

    private fun writeDelta(from: UserCommand, command: UserCommand, buffer: sizebuf_t) {
        val flags = flags(from, command)
        buffer.writeByte(flags.toByte())
        if (flags and CM_ANGLE1 != 0) buffer.writeShort(command.angles.pitch)
        if (flags and CM_ANGLE2 != 0) buffer.writeShort(command.angles.yaw)
        if (flags and CM_ANGLE3 != 0) buffer.writeShort(command.angles.roll)
        if (flags and CM_FORWARD != 0) buffer.writeShort(command.forwardmove)
        if (flags and CM_SIDE != 0) buffer.writeShort(command.sidemove)
        if (flags and CM_UP != 0) buffer.writeShort(command.upmove)
        if (flags and CM_BUTTONS != 0) buffer.writeByte(command.buttons.toByte())
        if (flags and CM_IMPULSE != 0) buffer.writeByte(command.impulse.toByte())
        buffer.writeByte(command.msec.toByte()); buffer.writeByte(command.lightlevel.toByte())
    }

    private fun readDelta(from: UserCommand, buffer: MessageReader): UserCommand {
        val flags = buffer.readUnsignedByte()
        // Read current angles, movement, buttons, then the always-present time and light level.
        val pitch = if (flags and CM_ANGLE1 != 0) buffer.readSignedShort() else from.angles.pitch
        val yaw = if (flags and CM_ANGLE2 != 0) buffer.readSignedShort() else from.angles.yaw
        val roll = if (flags and CM_ANGLE3 != 0) buffer.readSignedShort() else from.angles.roll
        val forward = if (flags and CM_FORWARD != 0) buffer.readSignedShort() else from.forwardmove
        val side = if (flags and CM_SIDE != 0) buffer.readSignedShort() else from.sidemove
        val up = if (flags and CM_UP != 0) buffer.readSignedShort() else from.upmove
        val buttons = if (flags and CM_BUTTONS != 0) buffer.readUnsignedByte() else from.buttons
        val impulse = if (flags and CM_IMPULSE != 0) buffer.readUnsignedByte() else from.impulse
        return UserCommand(buffer.readUnsignedByte(), buttons, CommandAngles(pitch, yaw, roll), forward, side, up, impulse, buffer.readUnsignedByte())
    }
}
