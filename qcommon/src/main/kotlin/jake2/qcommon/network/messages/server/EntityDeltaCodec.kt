package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.protocol.MessageReadException
import jake2.qcommon.network.protocol.MessageReader
import jake2.qcommon.network.protocol.MessageRejection
import jake2.qcommon.sizebuf_t

/** Entity delta planning is separate from reading/writing an already-selected wire mask. */
object EntityDeltaCodec {
    private val zero = EntityStateSnapshot()

    fun baseline(state: EntityStateSnapshot): EntityDelta = requireNotNull(plan(zero, state, force = true, newEntity = true))

    /** Writes part of packetentities: delta from either a baseline or a previous packet entity. */
    fun plan(from: EntityStateSnapshot, to: EntityStateSnapshot, force: Boolean, newEntity: Boolean): EntityDelta? {
        require(to.index in 1 until MAX_EDICTS) { "Unset or invalid entity number ${to.index}" }
        var flags = if (to.index >= 256) U_NUMBER16 else 0
        if (to.origin.x != from.origin.x) flags = flags or U_ORIGIN1
        if (to.origin.y != from.origin.y) flags = flags or U_ORIGIN2
        if (to.origin.z != from.origin.z) flags = flags or U_ORIGIN3
        if (to.angles.x != from.angles.x) flags = flags or U_ANGLE1
        if (to.angles.y != from.angles.y) flags = flags or U_ANGLE2
        if (to.angles.z != from.angles.z) flags = flags or U_ANGLE3
        if (to.skinNumber != from.skinNumber) flags = flags or when {
            Integer.compareUnsigned(to.skinNumber, 256) < 0 -> U_SKIN8
            Integer.compareUnsigned(to.skinNumber, 65536) < 0 -> U_SKIN16
            else -> U_SKIN8 or U_SKIN16
        }
        if (to.frame != from.frame) flags = flags or if (to.frame < 256) U_FRAME8 else U_FRAME16
        // Preserve the legacy signed comparisons for effects/renderfx field-width selection.
        if (to.effects != from.effects) flags = flags or widthFlags(to.effects, U_EFFECTS8, U_EFFECTS16)
        if (to.renderEffects != from.renderEffects) flags = flags or widthFlags(to.renderEffects, U_RENDERFX8, U_RENDERFX16)
        if (to.solid != from.solid) flags = flags or U_SOLID
        // Event is not delta compressed, just zero compressed: nonzero impulses are sent for one frame.
        if (to.event != 0) flags = flags or U_EVENT
        if (to.modelIndex != from.modelIndex) flags = flags or U_MODEL
        if (to.modelIndex2 != from.modelIndex2) flags = flags or U_MODEL2
        if (to.modelIndex3 != from.modelIndex3) flags = flags or U_MODEL3
        if (to.modelIndex4 != from.modelIndex4) flags = flags or U_MODEL4
        if (to.sound != from.sound) flags = flags or U_SOUND
        if (newEntity || to.renderEffects and RF_BEAM != 0) flags = flags or U_OLDORIGIN
        flags = when {
            flags and -0x1000000 != 0 -> flags or U_MOREBITS3 or U_MOREBITS2 or U_MOREBITS1
            flags and 0x00ff0000 != 0 -> flags or U_MOREBITS2 or U_MOREBITS1
            flags and 0x0000ff00 != 0 -> flags or U_MOREBITS1
            else -> flags
        }
        return if (flags == 0 && !force) null else EntityDelta(flags, wireState(to, flags))
    }

    /** Compare full engine snapshots first, then retain only selected, quantized payload values. */
    private fun wireState(state: EntityStateSnapshot, flags: Int): EntityStateSnapshot {
        fun selected(bit: Int) = flags and bit != 0
        fun coord(value: Float): Float = (value * 8).toInt().toShort().toInt() * 0.125f
        fun angle(value: Float): Float = ((value * 256 / 360).toInt() and 255).toByte().toInt() * (180f / 128)
        fun byte(value: Int, bit: Int): Int = if (selected(bit)) value and 255 else 0
        return EntityStateSnapshot(
            index = state.index,
            origin = Vector3f(if (selected(U_ORIGIN1)) coord(state.origin.x) else 0f,
                if (selected(U_ORIGIN2)) coord(state.origin.y) else 0f, if (selected(U_ORIGIN3)) coord(state.origin.z) else 0f),
            angles = Vector3f(if (selected(U_ANGLE1)) angle(state.angles.x) else 0f,
                if (selected(U_ANGLE2)) angle(state.angles.y) else 0f, if (selected(U_ANGLE3)) angle(state.angles.z) else 0f),
            oldOrigin = if (selected(U_OLDORIGIN)) Vector3f(coord(state.oldOrigin.x), coord(state.oldOrigin.y), coord(state.oldOrigin.z)) else Vector3f.zero,
            modelIndex = byte(state.modelIndex, U_MODEL), modelIndex2 = byte(state.modelIndex2, U_MODEL2),
            modelIndex3 = byte(state.modelIndex3, U_MODEL3), modelIndex4 = byte(state.modelIndex4, U_MODEL4),
            frame = if (selected(U_FRAME16)) state.frame.toShort().toInt() else byte(state.frame, U_FRAME8),
            skinNumber = narrow(state.skinNumber, flags, U_SKIN8, U_SKIN16),
            effects = narrow(state.effects, flags, U_EFFECTS8, U_EFFECTS16),
            renderEffects = narrow(state.renderEffects, flags, U_RENDERFX8, U_RENDERFX16),
            solid = if (selected(U_SOLID)) state.solid.toShort().toInt() else 0,
            sound = byte(state.sound, U_SOUND), event = byte(state.event, U_EVENT),
        )
    }

    private fun narrow(value: Int, flags: Int, byte: Int, short: Int): Int = when (flags and (byte or short)) {
        byte or short -> value
        byte -> value and 255
        short -> value.toShort().toInt()
        else -> 0
    }

    private fun widthFlags(value: Int, byte: Int, short: Int): Int = when {
        value < 256 -> byte
        value < 0x8000 -> short
        else -> byte or short
    }

    fun write(delta: EntityDelta, buffer: sizebuf_t) {
        val flags = delta.flags
        val state = delta.state
        writeHeader(flags, state.index, buffer)
        writePayload(flags, state, buffer)
    }

    internal fun writeHeader(flags: Int, number: Int, buffer: sizebuf_t) {
        buffer.writeByte(flags.toByte())
        if (flags and U_MOREBITS1 != 0) buffer.writeByte((flags ushr 8).toByte())
        if (flags and U_MOREBITS2 != 0) buffer.writeByte((flags ushr 16).toByte())
        if (flags and U_MOREBITS3 != 0) buffer.writeByte((flags ushr 24).toByte())
        if (flags and U_NUMBER16 != 0) buffer.writeShort(number) else buffer.writeByte(number.toByte())
    }

    private fun writePayload(flags: Int, state: EntityStateSnapshot, buffer: sizebuf_t) {
        if (flags and U_MODEL != 0) buffer.writeByte(state.modelIndex.toByte())
        if (flags and U_MODEL2 != 0) buffer.writeByte(state.modelIndex2.toByte())
        if (flags and U_MODEL3 != 0) buffer.writeByte(state.modelIndex3.toByte())
        if (flags and U_MODEL4 != 0) buffer.writeByte(state.modelIndex4.toByte())
        if (flags and U_FRAME8 != 0) buffer.writeByte(state.frame.toByte())
        if (flags and U_FRAME16 != 0) buffer.writeShort(state.frame)
        writeSized(state.skinNumber, flags, U_SKIN8, U_SKIN16, buffer)
        writeSized(state.effects, flags, U_EFFECTS8, U_EFFECTS16, buffer)
        writeSized(state.renderEffects, flags, U_RENDERFX8, U_RENDERFX16, buffer)
        if (flags and U_ORIGIN1 != 0) buffer.writeCoord(state.origin.x)
        if (flags and U_ORIGIN2 != 0) buffer.writeCoord(state.origin.y)
        if (flags and U_ORIGIN3 != 0) buffer.writeCoord(state.origin.z)
        if (flags and U_ANGLE1 != 0) buffer.writeAngleByte(state.angles.x)
        if (flags and U_ANGLE2 != 0) buffer.writeAngleByte(state.angles.y)
        if (flags and U_ANGLE3 != 0) buffer.writeAngleByte(state.angles.z)
        if (flags and U_OLDORIGIN != 0) {
            buffer.writeCoord(state.oldOrigin.x); buffer.writeCoord(state.oldOrigin.y); buffer.writeCoord(state.oldOrigin.z)
        }
        if (flags and U_SOUND != 0) buffer.writeByte(state.sound.toByte())
        if (flags and U_EVENT != 0) buffer.writeByte(state.event.toByte())
        if (flags and U_SOLID != 0) buffer.writeShort(state.solid)
    }

    private fun writeSized(value: Int, flags: Int, byte: Int, short: Int, buffer: sizebuf_t) {
        when (flags and (byte or short)) {
            byte or short -> buffer.writeInt(value) // Combined skin flags also carry laser colors.
            byte -> buffer.writeByte(value.toByte())
            short -> buffer.writeShort(value)
        }
    }

    /** CL_ParseEntityBits followed by the former CL_ParseDelta, retaining presence and selected widths. */
    fun read(buffer: MessageReader): EntityDelta = readPayload(readHeader(buffer), buffer)

    /** Validate indices before a decoded message can address an engine entity table. */
    internal fun readHeader(buffer: MessageReader): DeltaEntityHeader {
        var flags = buffer.readUnsignedByte()
        if (flags and U_MOREBITS1 != 0) flags = flags or (buffer.readUnsignedByte() shl 8)
        if (flags and U_MOREBITS2 != 0) flags = flags or (buffer.readUnsignedByte() shl 16)
        if (flags and U_MOREBITS3 != 0) flags = flags or (buffer.readUnsignedByte() shl 24)
        val number = if (flags and U_NUMBER16 != 0) buffer.readUnsignedShort() else buffer.readUnsignedByte()
        if (number >= MAX_EDICTS)
            throw MessageReadException(MessageRejection.INVALID_VALUE, buffer.readcount, "Invalid entity number $number")
        return DeltaEntityHeader(flags, number)
    }

    /** Former CL_ParseDelta: read selected fields after the entity header. */
    internal fun readPayload(header: DeltaEntityHeader, buffer: MessageReader): EntityDelta {
        val (flags, number) = header
        val model = if (flags and U_MODEL != 0) buffer.readUnsignedByte() else 0
        val model2 = if (flags and U_MODEL2 != 0) buffer.readUnsignedByte() else 0
        val model3 = if (flags and U_MODEL3 != 0) buffer.readUnsignedByte() else 0
        val model4 = if (flags and U_MODEL4 != 0) buffer.readUnsignedByte() else 0
        var frame = if (flags and U_FRAME8 != 0) buffer.readUnsignedByte() else 0
        if (flags and U_FRAME16 != 0) frame = buffer.readSignedShort()
        val skin = readSized(flags, U_SKIN8, U_SKIN16, buffer)
        val effects = readSized(flags, U_EFFECTS8, U_EFFECTS16, buffer)
        val render = readSized(flags, U_RENDERFX8, U_RENDERFX16, buffer)
        val x = if (flags and U_ORIGIN1 != 0) buffer.readCoord() else 0f
        val y = if (flags and U_ORIGIN2 != 0) buffer.readCoord() else 0f
        val z = if (flags and U_ORIGIN3 != 0) buffer.readCoord() else 0f
        val pitch = if (flags and U_ANGLE1 != 0) buffer.readAngleByte() else 0f
        val yaw = if (flags and U_ANGLE2 != 0) buffer.readAngleByte() else 0f
        val roll = if (flags and U_ANGLE3 != 0) buffer.readAngleByte() else 0f
        val oldOrigin = if (flags and U_OLDORIGIN != 0) buffer.readPosition() else Vector3f.zero
        val sound = if (flags and U_SOUND != 0) buffer.readUnsignedByte() else 0
        val event = if (flags and U_EVENT != 0) buffer.readUnsignedByte() else 0
        val solid = if (flags and U_SOLID != 0) buffer.readSignedShort() else 0
        return EntityDelta(flags, EntityStateSnapshot(number, Vector3f(x, y, z), Vector3f(pitch, yaw, roll),
            oldOrigin, model, model2, model3, model4, frame, skin, effects, render, solid, sound, event))
    }

    private fun readSized(flags: Int, byte: Int, short: Int, buffer: MessageReader): Int = when (flags and (byte or short)) {
        byte or short -> buffer.readInt()
        byte -> buffer.readUnsignedByte()
        short -> buffer.readSignedShort()
        else -> 0
    }

    fun size(delta: EntityDelta): Int {
        val flags = delta.flags
        var size = headerSize(flags)
        size += Integer.bitCount(flags and (U_MODEL or U_MODEL2 or U_MODEL3 or U_MODEL4 or U_FRAME8 or U_SOUND or U_EVENT or U_ANGLE1 or U_ANGLE2 or U_ANGLE3))
        size += 2 * Integer.bitCount(flags and (U_ORIGIN1 or U_ORIGIN2 or U_ORIGIN3 or U_FRAME16 or U_SOLID))
        size += sizedLength(flags, U_SKIN8, U_SKIN16) + sizedLength(flags, U_EFFECTS8, U_EFFECTS16) + sizedLength(flags, U_RENDERFX8, U_RENDERFX16)
        if (flags and U_OLDORIGIN != 0) size += 6
        return size
    }

    internal fun headerSize(flags: Int): Int = 1 + (if (flags and U_NUMBER16 != 0) 2 else 1) +
        Integer.bitCount(flags and (U_MOREBITS1 or U_MOREBITS2 or U_MOREBITS3))

    private fun sizedLength(flags: Int, byte: Int, short: Int): Int = when (flags and (byte or short)) {
        byte or short -> 4
        byte -> 1
        short -> 2
        else -> 0
    }
}
