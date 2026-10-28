package jake2.qcommon.network.messages.client

import jake2.qcommon.network.protocol.MessageReadException
import jake2.qcommon.network.protocol.MessageReader
import jake2.qcommon.network.protocol.MessageRejection
import jake2.qcommon.sizebuf_t

/** Client wire operations; values have no mutable parsing state. Movement retains its existing CRC/delta codec. */
object ClientMessageCodec {
    @JvmStatic
    fun write(message: ClientMessage, buffer: sizebuf_t) {
        buffer.writeByte(message.type.value)
        when (message) {
            is StringCmdMessage -> buffer.writeString(message.command)
            is UserInfoMessage -> buffer.writeString(message.userInfo)
            NoopMessage, EndOfClientPacketMessage -> Unit
            is MoveMessage -> message.writeMovementProperties(buffer)
            else -> error("Unsupported client message ${message.javaClass.name}")
        }
    }

    /** Size in bytes including the opcode and string terminators. */
    @JvmStatic
    fun size(message: ClientMessage): Int = when (message) {
        is StringCmdMessage -> 2 + message.command.length
        is UserInfoMessage -> 2 + message.userInfo.length
        NoopMessage, EndOfClientPacketMessage -> 1
        is MoveMessage -> message.movementSize
        else -> error("Unsupported client message ${message.javaClass.name}")
    }

    /** Construct complete values from bounded reads; preserve cursor updates for direct legacy factory callers. */
    @JvmStatic
    fun read(buffer: sizebuf_t, incomingSequence: Int): ClientMessage {
        if (buffer !is MessageReader) {
            val reader = MessageReader.fromBuffer(buffer)
            return try { read(reader, incomingSequence) } finally { buffer.readcount = reader.readcount }
        }
        if (buffer.readcount == buffer.cursize) return EndOfClientPacketMessage
        val opcode = buffer.readByte()
        return when (ClientMessageType.fromByte(opcode.toByte())) {
            ClientMessageType.CLC_BAD -> EndOfClientPacketMessage
            // Clients do not normally send no-op messages directly; retain the accepted wire opcode.
            ClientMessageType.CLC_NOP -> NoopMessage
            ClientMessageType.CLC_USERINFO -> UserInfoMessage(buffer.readString())
            ClientMessageType.CLC_STRINGCMD -> StringCmdMessage(buffer.readString())
            ClientMessageType.CLC_MOVE -> MoveMessage(incomingSequence).apply { parse(buffer) }
            null -> throw MessageReadException(MessageRejection.UNKNOWN_OPCODE, buffer.readcount - 1,
                "Unknown client opcode $opcode")
        }
    }
}
