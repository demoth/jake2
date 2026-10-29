package jake2.qcommon.network.messages.client

import jake2.qcommon.network.protocol.MessageReadException
import jake2.qcommon.network.protocol.MessageReader
import jake2.qcommon.network.protocol.MessageRejection
import jake2.qcommon.sizebuf_t

/** Client wire operations; movement checksums use an explicit packet sequence rather than message-owned state. */
object ClientMessageCodec {
    @JvmStatic
    fun write(message: ClientMessage, buffer: sizebuf_t) {
        require(message !is MoveMessage) { "Movement encoding requires a packet sequence" }
        write(message, buffer, 0)
    }

    @JvmStatic
    fun write(message: ClientMessage, buffer: sizebuf_t, sequence: Int) {
        if (message is MoveMessage) require(sequence >= 0) { "Movement sequence must be non-negative" }
        buffer.writeByte(message.type.value)
        when (message) {
            is StringCmdMessage -> buffer.writeString(message.command)
            is UserInfoMessage -> buffer.writeString(message.userInfo)
            NoopMessage, EndOfClientPacketMessage -> Unit
            is MoveMessage -> MovementCodec.writeProperties(message, buffer, sequence)
            else -> error("Unsupported client message ${message.javaClass.name}")
        }
    }

    /** Size in bytes including the opcode and string terminators. */
    @JvmStatic
    fun size(message: ClientMessage): Int = when (message) {
        is StringCmdMessage -> 2 + message.command.length
        is UserInfoMessage -> 2 + message.userInfo.length
        NoopMessage, EndOfClientPacketMessage -> 1
        is MoveMessage -> MovementCodec.size(message)
        else -> error("Unsupported client message ${message.javaClass.name}")
    }

    /** Construct complete values from bounded reads and update the supplied buffer's cursor. */
    @JvmStatic
    fun read(buffer: sizebuf_t, incomingSequence: Int): ClientMessage {
        if (buffer !is MessageReader) {
            val reader = MessageReader.fromBuffer(buffer)
            return try { read(reader, incomingSequence) } finally { buffer.readcount = reader.readcount }
        }
        if (buffer.readcount == buffer.cursize) return EndOfClientPacketMessage
        val opcode = buffer.readUnsignedByte()
        return when (ClientMessageType.fromByte(opcode.toByte())) {
            ClientMessageType.CLC_BAD -> EndOfClientPacketMessage
            // Clients do not normally send no-op messages directly; retain the accepted wire opcode.
            ClientMessageType.CLC_NOP -> NoopMessage
            ClientMessageType.CLC_USERINFO -> UserInfoMessage(buffer.readString())
            ClientMessageType.CLC_STRINGCMD -> StringCmdMessage(buffer.readString())
            ClientMessageType.CLC_MOVE -> MovementCodec.read(buffer, incomingSequence)
            null -> throw MessageReadException(MessageRejection.UNKNOWN_OPCODE, buffer.readcount - 1,
                "Unknown client opcode $opcode")
        }
    }
}
