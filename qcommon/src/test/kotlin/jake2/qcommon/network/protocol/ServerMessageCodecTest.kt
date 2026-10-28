package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServerMessageCodecTest {
    private fun write(message: ServerMessage): ByteArray {
        val buffer = sizebuf_t(ServerMessageCodec.size(message))
        ServerMessageCodec.write(message, buffer)
        assertEquals(buffer.maxsize, buffer.cursize)
        return buffer.data
    }

    @Test
    fun `mixed server values retain unsigned fields and control outcomes`() {
        val values = listOf(
            PrintMessage(255, "caf\u00e9"), PrintCenterMessage("center"), LayoutMessage("layout"),
            ConfigStringMessage(MAX_CONFIGSTRINGS - 1, "config"), StuffTextMessage.command("reconnect"),
            DisconnectMessage, ReconnectMessage,
        )
        val buffer = sizebuf_t(values.sumOf(ServerMessageCodec::size) + 2)
        ServerMessageCodec.write(NopMessage, buffer)
        values.forEach { ServerMessageCodec.write(it, buffer) }
        ServerMessageCodec.write(EndOfServerPacketMessage, buffer)
        assertEquals(buffer.maxsize, buffer.cursize)
        val wire = PacketCodec.encodeSequenced(1, 0, null, PacketBytes.copyOf(buffer.data))
        val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope
        val decoded = MessageBodyCodec.decodeServer(envelope) as MessageDecodeResult.Decoded
        assertEquals(values, decoded.messages)
        assertSame(DisconnectMessage, decoded.messages[5])
        assertSame(ReconnectMessage, decoded.messages[6])
        val reader = MessageReader(PacketBytes.copyOf(byteArrayOf(6, -1)), 0)
        assertNull(ServerMessageCodec.read(reader))
        assertSame(EndOfServerPacketMessage, ServerMessageCodec.read(reader))
        assertSame(EndOfServerPacketMessage, ServerMessageCodec.read(reader))
    }

    @Test
    fun `stuffed text retains exact wire newlines when decoded copied and re-encoded`() {
        for (text in listOf("", "reconnect", "reconnect\n", "reconnect\n\n", "caf\u00e9\n")) {
            val bytes = byteArrayOf(11) + text.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0)
            val decoded = ServerMessageCodec.read(MessageReader(PacketBytes.copyOf(bytes), 0)) as StuffTextMessage
            assertEquals(text, decoded.text)
            assertArrayEquals(bytes, write(decoded))
            assertArrayEquals(bytes, write(decoded.copy()))
            val command = StuffTextMessage.command(text)
            assertEquals("$text\n", command.text)
            assertArrayEquals(byteArrayOf(11) + text.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(10, 0), write(command))
        }
    }

    @Test
    fun `unsigned reader boundaries widen high bits and retain bounded failure behavior`() {
        for (value in listOf(0, 1, 127, 128, 255)) {
            val reader = MessageReader(PacketBytes.copyOf(byteArrayOf(value.toByte())), 0)
            assertEquals(value, reader.readUnsignedByte())
            assertEquals(MessageRejection.TRUNCATED,
                assertThrows(MessageReadException::class.java) { reader.readUnsignedByte() }.reason)
        }
        for (value in listOf(0, 127, 128, 255, 256, 32767, 32768, 65535)) {
            val reader = MessageReader(PacketBytes.copyOf(byteArrayOf(value.toByte(), (value shr 8).toByte())), 0)
            assertEquals(value, reader.readUnsignedShort())
        }
        val truncated = MessageReader(PacketBytes.copyOf(byteArrayOf(-1)), 0)
        assertEquals(MessageRejection.TRUNCATED,
            assertThrows(MessageReadException::class.java) { truncated.readUnsignedShort() }.reason)
        assertEquals(0, truncated.readcount, "A truncated fixed-width field consumes no bytes")
    }

    @Test
    fun `direct server codec failures update cursor and reject invalid unsigned config indices`() {
        for ((bytes, reason) in listOf(
            byteArrayOf(10, 255.toByte(), 'x'.code.toByte()) to MessageRejection.TRUNCATED,
            byteArrayOf(13, -1, -1, 0) to MessageRejection.INVALID_VALUE,
            byteArrayOf(254.toByte()) to MessageRejection.UNKNOWN_OPCODE,
        )) {
            val buffer = sizebuf_t(bytes.size).apply { writeBytes(bytes) }
            val error = assertThrows(MessageReadException::class.java) { ServerMessageCodec.read(buffer) }
            assertEquals(reason, error.reason)
            assertEquals(if (reason == MessageRejection.INVALID_VALUE) 3 else bytes.size, buffer.readcount)
        }
    }
}
