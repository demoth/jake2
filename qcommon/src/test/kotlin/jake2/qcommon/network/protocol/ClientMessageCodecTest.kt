package jake2.qcommon.network.protocol

import jake2.qcommon.network.messages.client.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import jake2.qcommon.usercmd_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientMessageCodecTest {
    private fun body(bytes: ByteArray, sequence: Int): MessageDecodeResult<ClientMessage> {
        val wire = PacketCodec.encodeSequenced(sequence, 0, 7, PacketBytes.copyOf(bytes))
        val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_CLIENT) as PacketDecodeResult.Decoded).envelope
        return MessageBodyCodec.decodeClient(envelope, sequence)
    }

    @Test
    fun `mixed client values encode and decode without retaining parse state`() {
        val values = listOf(StringCmdMessage("new"), UserInfoMessage("\\name\\caf\u00e9"), NoopMessage)
        val buffer = sizebuf_t(values.sumOf(ClientMessageCodec::size) + 1)
        values.forEach { ClientMessageCodec.write(it, buffer) }
        ClientMessageCodec.write(EndOfClientPacketMessage, buffer)
        assertEquals(buffer.maxsize, buffer.cursize)
        for (sequence in listOf(1, 17)) {
            val decoded = body(buffer.data, sequence) as MessageDecodeResult.Decoded
            assertEquals(values, decoded.messages)
            assertSame(NoopMessage, decoded.messages.last())
        }
        val reader = MessageReader(PacketBytes.copyOf(byteArrayOf(0)), 0)
        assertSame(EndOfClientPacketMessage, ClientMessageCodec.read(reader, 1))
        assertSame(EndOfClientPacketMessage, ClientMessageCodec.read(reader, 17))
    }

    @Test
    fun `movement delegation preserves CRC boundaries after a string and before another message`() {
        val prefix = StringCmdMessage("new")
        val movement = MoveMessage(false, 7, usercmd_t(), usercmd_t(), usercmd_t(), 73)
        val suffix = UserInfoMessage("\\name\\player")
        val values = listOf(prefix, movement, suffix)
        val buffer = sizebuf_t(values.sumOf(ClientMessageCodec::size))
        values.forEach { ClientMessageCodec.write(it, buffer) }
        assertEquals(buffer.maxsize, buffer.cursize)
        val decoded = body(buffer.data, 73) as MessageDecodeResult.Decoded
        assertEquals(values, decoded.messages)
        assertTrue((decoded.messages[1] as MoveMessage).valid)

        // CRC policy belongs to the controller: a corrupted movement CRC remains structurally decodable.
        val badCRC = buffer.data.clone()
        val checksumOffset = prefix.size + 1
        badCRC[checksumOffset] = (badCRC[checksumOffset].toInt() xor 1).toByte()
        val corrupted = body(badCRC, 73) as MessageDecodeResult.Decoded
        assertFalse((corrupted.messages[1] as MoveMessage).valid)
        assertEquals(suffix, corrupted.messages.last())
    }

    @Test
    fun `direct codec failures copy the legacy cursor and reject truncated or unknown messages`() {
        for ((bytes, reason) in listOf(
            byteArrayOf(4, 'n'.code.toByte()) to MessageRejection.TRUNCATED,
            byteArrayOf(99) to MessageRejection.UNKNOWN_OPCODE,
        )) {
            val buffer = sizebuf_t(bytes.size).apply { writeBytes(bytes) }
            val error = assertThrows(MessageReadException::class.java) { ClientMessageCodec.read(buffer, 1) }
            assertEquals(reason, error.reason)
            assertEquals(bytes.size, buffer.readcount)
        }
    }
}
