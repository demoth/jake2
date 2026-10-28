package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.PROTOCOL_VERSION
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.client.*
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageStringEncodingTest {
    private fun write(message: NetworkMessage): ByteArray {
        val buffer = sizebuf_t(message.size)
        message.writeTo(buffer)
        assertEquals(message.size, buffer.cursize)
        return buffer.data.copyOf(buffer.cursize)
    }

    @Test
    fun `every payload string message retains all non-terminator Latin-1 bytes`() {
        // NUL and 0xff are legacy terminators; all other bytes, including newlines, are payload.
        val text = (1..254).map(Int::toChar).joinToString("")
        val messages = listOf(
            PrintMessage(2, text), PrintCenterMessage(text), LayoutMessage(text),
            ConfigStringMessage(4, text), StuffTextMessage(text),
            ServerDataMessage(PROTOCOL_VERSION, 3, false, text, 1, text),
            StringCmdMessage(text), UserInfoMessage(text),
        )
        for (message in messages) {
            val bytes = write(message)
            val buffer = sizebuf_t(bytes.size).apply { writeBytes(bytes) }
            val decoded = if (message is ClientMessage) ClientMessage.parseFromBuffer(buffer, 1)
                else ServerMessage.parseFromBuffer(buffer)
            assertEquals(message, decoded, message.javaClass.simpleName)
            assertEquals(buffer.cursize, buffer.readcount)
            assertArrayEquals(bytes, write(decoded!!), message.javaClass.simpleName)
        }
    }

    @Test
    fun `raw wire bytes decode as Latin-1 independently of the platform charset`() {
        val bytes = byteArrayOf(0x63, 0x61, 0x66, 0xe9.toByte(), 10, 0x80.toByte(), 0xfe.toByte(), 0)
        val expected = "caf\u00e9\n\u0080\u00fe"
        val legacy = sizebuf_t(bytes.size).apply { writeBytes(bytes) }
        val bounded = MessageReader(PacketBytes.copyOf(bytes), 0)
        assertEquals(expected, legacy.readString())
        assertEquals(expected, bounded.readString())
        assertEquals(bytes.size, legacy.readcount)
        assertEquals(bytes.size, bounded.readcount)
    }

    @Test
    fun `NUL and legacy ff terminate payload strings without consuming the following message`() {
        for (terminator in listOf(0, 255)) {
            val wire = byteArrayOf(10, 2, 0xe9.toByte(), terminator.toByte(), 7)
            val reader = MessageReader(PacketBytes.copyOf(wire), 0)
            assertEquals(PrintMessage(2, "\u00e9"), ServerMessage.parseFromBuffer(reader))
            assertEquals(4, reader.readcount)
            assertInstanceOf(DisconnectMessage::class.java, ServerMessage.parseFromBuffer(reader))
            assertEquals(wire.size, reader.readcount)
        }
    }

    @Test
    fun `unterminated high-bit strings reject atomically while legacy buffers retain their sentinel`() {
        val bytes = byteArrayOf(4, 0xe9.toByte(), 0x80.toByte())
        val wire = PacketCodec.encodeSequenced(1, 0, 7, PacketBytes.copyOf(bytes))
        val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_CLIENT) as PacketDecodeResult.Decoded).envelope
        val result = MessageBodyCodec.decodeClient(envelope, 1) as MessageDecodeResult.Rejected
        assertEquals(MessageRejection.TRUNCATED, result.reason)
        val legacy = sizebuf_t(2).apply { writeBytes(bytes.copyOfRange(1, bytes.size)) }
        assertEquals("\u00e9\u0080", legacy.readString())
    }

    @Test
    fun `connectionless lines stop at newline while payload strings keep it`() {
        val payload = byteArrayOf(0xe9.toByte(), 10, 0xfe.toByte(), 0)
        assertEquals("\u00e9\n\u00fe", MessageReader(PacketBytes.copyOf(payload), 0).readString())
        val wire = PacketBytes.copyOf(byteArrayOf(-1, -1, -1, -1) + payload)
        val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_CLIENT) as PacketDecodeResult.Decoded).envelope
        assertEquals("\u00e9", (envelope as PacketEnvelope.Connectionless).commandLine)
    }
}
