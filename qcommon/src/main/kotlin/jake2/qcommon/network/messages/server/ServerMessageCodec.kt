package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.Defines.MAX_EDICTS
import jake2.qcommon.Defines.MAX_ITEMS
import jake2.qcommon.Defines.MAX_MAP_AREAS
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
            is ServerDataMessage -> {
                buffer.writeInt(message.protocol)
                buffer.writeInt(message.spawnCount)
                buffer.writeByte(if (message.demo) 1 else 0)
                buffer.writeString(message.gameName)
                buffer.writeShort(message.playerNumber)
                buffer.writeString(message.levelString)
            }
            is WeaponSoundMessage -> { buffer.writeShort(message.entityIndex); buffer.writeByte(message.flashType.toByte()) }
            is MuzzleFlash2Message -> { buffer.writeShort(message.entityIndex); buffer.writeByte(message.flashType.toByte()) }
            is InventoryMessage -> repeat(MAX_ITEMS) { buffer.writeShort(message[it]) }
            is DownloadMessage.Chunk -> {
                buffer.writeShort(message.data.size)
                buffer.writeByte(message.percentage.toByte())
                buffer.writeBytes(message.data.toByteArray())
            }
            is DownloadMessage.Failure -> { buffer.writeShort(message.marker); buffer.writeByte(message.percentage.toByte()) }
            is FrameHeaderMessage -> {
                buffer.writeInt(message.frameNumber)
                // What we are delta'ing from; -1 denotes a full frame.
                buffer.writeInt(message.lastFrame)
                // Rate dropped packets.
                buffer.writeByte(message.suppressCount.toByte())
                buffer.writeByte(message.areaBits.size.toByte())
                buffer.writeBytes(message.areaBits.toByteArray())
            }
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
        is ServerDataMessage -> 14 + message.gameName.length + message.levelString.length
        is WeaponSoundMessage, is MuzzleFlash2Message -> 4
        is InventoryMessage -> 1 + 2 * MAX_ITEMS
        is DownloadMessage.Chunk -> 4 + message.data.size
        is DownloadMessage.Failure -> 4
        is FrameHeaderMessage -> 11 + message.areaBits.size
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
            ServerMessageType.svc_serverdata -> ServerDataMessage(
                buffer.readInt(), buffer.readInt(), buffer.readUnsignedByte() != 0,
                buffer.readString(), buffer.readSignedShort(), buffer.readString(),
            )
            ServerMessageType.svc_muzzleflash -> WeaponSoundMessage(readEntityIndex(buffer), buffer.readUnsignedByte())
            ServerMessageType.svc_muzzleflash2 -> MuzzleFlash2Message(readEntityIndex(buffer), buffer.readUnsignedByte())
            ServerMessageType.svc_inventory -> InventoryMessage(IntArray(MAX_ITEMS) { buffer.readSignedShort() })
            ServerMessageType.svc_download -> readDownload(buffer)
            ServerMessageType.svc_frame -> readFrameHeader(buffer)
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

    private fun readEntityIndex(buffer: MessageReader): Int {
        val index = buffer.readUnsignedShort()
        if (index >= MAX_EDICTS)
            throw MessageReadException(MessageRejection.INVALID_VALUE, buffer.readcount, "Invalid entity number $index")
        return index
    }

    private fun readDownload(buffer: MessageReader): DownloadMessage {
        val length = buffer.readSignedShort()
        val percentage = buffer.readUnsignedByte()
        return if (length == -1 || length == DownloadMessage.LEGACY_FAILURE_MARKER) {
            DownloadMessage.Failure(length, percentage)
        } else {
            DownloadMessage.Chunk(buffer.readBytes(length), percentage)
        }
    }

    private fun readFrameHeader(buffer: MessageReader): FrameHeaderMessage {
        val frameNumber = buffer.readInt()
        val lastFrame = buffer.readInt()
        // The legacy parser mentioned omitting suppressCount for protocol-26 demos.
        // Keep the existing protocol-34 layout: this byte is always present.
        val suppressCount = buffer.readUnsignedByte()
        val length = buffer.readUnsignedByte()
        if (length > MAX_MAP_AREAS / 8)
            throw MessageReadException(MessageRejection.INVALID_LENGTH, buffer.readcount - 1, "Invalid area bits length $length")
        return FrameHeaderMessage(frameNumber, lastFrame, suppressCount, buffer.readBytes(length))
    }
}
