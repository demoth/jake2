package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.network.protocol.MessageReadException
import jake2.qcommon.network.protocol.MessageReader
import jake2.qcommon.network.protocol.MessageRejection
import jake2.qcommon.sizebuf_t

/** Server wire operations. Mutable array/delta families retain their existing codecs during migration. */
object ServerMessageCodec {
    @JvmStatic
    fun write(message: ServerMessage, buffer: sizebuf_t) {
        buffer.writeByte(message.type.type.toByte())
        when (message) {
            is PrintMessage -> { buffer.writeByte(message.level.toByte()); buffer.writeString(message.text) }
            is PrintCenterMessage -> buffer.writeString(message.text)
            is LayoutMessage -> buffer.writeString(message.layout)
            is StuffTextMessage -> buffer.writeString(message.text)
            is ConfigStringMessage -> { buffer.writeShort(message.index); buffer.writeString(message.config) }
            DisconnectMessage, ReconnectMessage, NopMessage, EndOfServerPacketMessage -> Unit
            is LegacyServerMessage -> message.writeLegacyProperties(buffer)
            else -> error("Unsupported server message ${message.javaClass.name}")
        }
    }

    /** Size in bytes including the opcode, fixed-width fields, and string terminators. */
    @JvmStatic
    fun size(message: ServerMessage): Int = when (message) {
        is PrintMessage -> 3 + message.text.length
        is PrintCenterMessage -> 2 + message.text.length
        is LayoutMessage -> 2 + message.layout.length
        is StuffTextMessage -> 2 + message.text.length
        is ConfigStringMessage -> 4 + message.config.length
        DisconnectMessage, ReconnectMessage, NopMessage, EndOfServerPacketMessage -> 1
        is LegacyServerMessage -> message.size
        else -> error("Unsupported server message ${message.javaClass.name}")
    }

    /** Construct complete values from bounded reads; no-op and end markers keep their existing outcomes. */
    @JvmStatic
    fun read(buffer: sizebuf_t): ServerMessage? {
        if (buffer !is MessageReader) {
            val reader = MessageReader.fromBuffer(buffer)
            return try { read(reader) } finally { buffer.readcount = reader.readcount }
        }
        if (buffer.readcount == buffer.cursize) return EndOfServerPacketMessage
        val opcode = buffer.readUnsignedByte()
        return when (val type = ServerMessageType.fromInt(opcode)) {
            ServerMessageType.svc_end -> EndOfServerPacketMessage
            ServerMessageType.svc_nop -> null
            ServerMessageType.svc_disconnect -> DisconnectMessage
            ServerMessageType.svc_reconnect -> ReconnectMessage
            ServerMessageType.svc_print -> PrintMessage(buffer.readUnsignedByte(), buffer.readString())
            ServerMessageType.svc_centerprint -> PrintCenterMessage(buffer.readString())
            ServerMessageType.svc_layout -> LayoutMessage(buffer.readString())
            ServerMessageType.svc_stufftext -> StuffTextMessage(buffer.readString())
            ServerMessageType.svc_configstring -> {
                val index = buffer.readUnsignedShort()
                if (index >= MAX_CONFIGSTRINGS)
                    throw MessageReadException(MessageRejection.INVALID_VALUE, buffer.readcount, "Invalid configstring index $index")
                ConfigStringMessage(index, buffer.readString())
            }
            null, ServerMessageType.svc_bad -> throw MessageReadException(MessageRejection.UNKNOWN_OPCODE,
                buffer.readcount - 1, "Invalid server opcode $opcode")
            else -> ServerMessage.readLegacyProperties(type, buffer)
        }
    }
}
