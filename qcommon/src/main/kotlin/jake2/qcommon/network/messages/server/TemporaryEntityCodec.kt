package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.protocol.DirectionCodec
import jake2.qcommon.network.protocol.MessageReadException
import jake2.qcommon.network.protocol.MessageReader
import jake2.qcommon.network.protocol.MessageRejection
import jake2.qcommon.sizebuf_t

/** Subtype-specific temporary-entity wire layouts. The server codec owns the enclosing opcode. */
object TemporaryEntityCodec {
    fun writeProperties(message: TEMessage, buffer: sizebuf_t) {
        buffer.writeByte(message.style.toByte())
        when (message) {
            is LightningTEMessage -> {
                buffer.writeShort(message.sourceIndex); buffer.writeShort(message.destinationIndex)
                writePosition(message.origin, buffer); writePosition(message.destination, buffer)
            }
            is FlashlightTEMessage -> { writePosition(message.position, buffer); buffer.writeShort(message.entityIndex) }
            is ForceWallTEMessage -> {
                writePosition(message.origin, buffer); writePosition(message.destination, buffer); buffer.writeByte(message.color.toByte())
            }
            is SteamTEMessage -> {
                buffer.writeShort(message.id); buffer.writeByte(message.count.toByte()); writePosition(message.position, buffer)
                buffer.writeByte(message.directionIndex.toByte()); buffer.writeByte(message.color.toByte()); buffer.writeShort(message.magnitude)
                message.durationMillis?.let(buffer::writeInt)
            }
            is WidowBeamOutTEMessage -> { buffer.writeShort(message.id); writePosition(message.position, buffer) }
            is PointTEMessage -> writePosition(message.position, buffer)
            is BeamTEMessage -> {
                buffer.writeShort(message.ownerIndex)
                writePosition(message.origin, buffer); writePosition(message.destination, buffer)
            }
            is BeamOffsetTEMessage -> {
                buffer.writeShort(message.ownerIndex)
                writePosition(message.origin, buffer); writePosition(message.destination, buffer); writePosition(message.offset, buffer)
            }
            is PointDirectionTEMessage -> { writePosition(message.position, buffer); buffer.writeByte(message.directionIndex.toByte()) }
            is TrailTEMessage -> { writePosition(message.position, buffer); writePosition(message.destination, buffer) }
            is SplashTEMessage -> {
                buffer.writeByte(message.count.toByte()); writePosition(message.position, buffer)
                buffer.writeByte(message.directionIndex.toByte()); buffer.writeByte(message.param.toByte())
            }
        }
    }

    /** Includes the server opcode and subtype byte. */
    fun size(message: TEMessage): Int = when (message) {
        is LightningTEMessage -> 18
        is FlashlightTEMessage, is WidowBeamOutTEMessage -> 10
        is ForceWallTEMessage -> 15
        is SteamTEMessage -> if (message.durationMillis == null) 15 else 19
        is PointTEMessage -> 8
        is BeamTEMessage -> 16
        is BeamOffsetTEMessage -> 22
        is PointDirectionTEMessage -> 9
        is TrailTEMessage -> 14
        is SplashTEMessage -> 11
    }

    fun read(buffer: MessageReader): TEMessage {
        val style = buffer.readUnsignedByte()
        return when (style) {
            TE_LIGHTNING -> LightningTEMessage(buffer.readSignedShort(), buffer.readSignedShort(), buffer.readPosition(), buffer.readPosition())
            TE_FLASHLIGHT -> FlashlightTEMessage(buffer.readPosition(), buffer.readSignedShort())
            TE_FORCEWALL -> ForceWallTEMessage(buffer.readPosition(), buffer.readPosition(), buffer.readUnsignedByte())
            TE_STEAM -> {
                val id = buffer.readSignedShort()
                val count = buffer.readUnsignedByte()
                val position = buffer.readPosition()
                val direction = DirectionCodec.readIndex(buffer)
                val color = buffer.readUnsignedByte()
                val magnitude = buffer.readSignedShort()
                SteamTEMessage(id, count, position, direction, color, magnitude, if (id == -1) null else buffer.readInt())
            }
            TE_WIDOWBEAMOUT -> WidowBeamOutTEMessage(buffer.readSignedShort(), buffer.readPosition())
            in PointTEMessage.SUBTYPES -> PointTEMessage(style, buffer.readPosition())
            in BeamTEMessage.SUBTYPES -> BeamTEMessage(style, buffer.readSignedShort(), buffer.readPosition(), buffer.readPosition())
            in BeamOffsetTEMessage.SUBTYPES -> BeamOffsetTEMessage(style, buffer.readSignedShort(), buffer.readPosition(), buffer.readPosition(), buffer.readPosition())
            in PointDirectionTEMessage.SUBTYPES -> PointDirectionTEMessage(style, buffer.readPosition(), DirectionCodec.readIndex(buffer))
            in TrailTEMessage.SUBTYPES -> TrailTEMessage(style, buffer.readPosition(), buffer.readPosition())
            in SplashTEMessage.SUBTYPES -> SplashTEMessage(style, buffer.readUnsignedByte(), buffer.readPosition(), DirectionCodec.readIndex(buffer), buffer.readUnsignedByte())
            else -> throw MessageReadException(MessageRejection.INVALID_VALUE, buffer.readcount - 1, "Unexpected temp entity type: $style")
        }
    }

    private fun writePosition(position: Vector3f, buffer: sizebuf_t) {
        buffer.writeCoord(position.x); buffer.writeCoord(position.y); buffer.writeCoord(position.z)
    }
}
