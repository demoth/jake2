package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.MAX_EDICTS
import jake2.qcommon.Defines.PROTOCOL_VERSION
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServerScalarMessageCodecTest {
    private fun write(message: ServerMessage): ByteArray {
        val buffer = sizebuf_t(ServerMessageCodec.size(message))
        ServerMessageCodec.write(message, buffer)
        assertEquals(buffer.maxsize, buffer.cursize)
        return buffer.data
    }

    private fun read(bytes: ByteArray): ServerMessage {
        val reader = MessageReader(PacketBytes.copyOf(bytes), 0)
        val message = ServerMessageCodec.read(reader)!!
        assertEquals(bytes.size, reader.readcount)
        return message
    }

    @Test
    fun `received server metadata retains its version high-bit text and cinematic slot`() {
        // An unsupported version is metadata for the controller to interpret, not a codec rewrite rule.
        val bytes = byteArrayOf(12, 33, 0, 0, 0, 0x78, 0x56, 0x34, 0x12, 1,
            'b'.code.toByte(), 0xe9.toByte(), 0, -1, -1, 'l'.code.toByte(), 0xfe.toByte(), 0)
        val message = read(bytes) as ServerDataMessage
        assertEquals(ServerDataMessage(33, 0x12345678, true, "b\u00e9", -1, "l\u00fe"), message)
        assertArrayEquals(bytes, write(message.copy()))
        for (slot in listOf(-1, 0, 255)) for (demo in listOf(false, true)) {
            val outgoing = ServerDataMessage.forClient(3, demo, "", slot, "level")
            assertEquals(PROTOCOL_VERSION, outgoing.protocol)
            assertEquals(outgoing, read(write(outgoing)))
        }
    }

    @Test
    fun `all packed effect bytes retain their opcode and entity number width`() {
        for (index in listOf(0, 1, 255, 256, MAX_EDICTS - 1)) for (flash in 0..255) {
            for ((opcode, message) in listOf(1 to WeaponSoundMessage(index, flash), 2 to MuzzleFlash2Message(index, flash))) {
                val bytes = byteArrayOf(opcode.toByte(), index.toByte(), (index shr 8).toByte(), flash.toByte())
                assertArrayEquals(bytes, write(message))
                assertEquals(message, read(bytes))
            }
        }
    }

    @Test
    fun `signed short reads preserve negative markers and bounded failures`() {
        for (value in listOf(-32768, -1, 0, 32767)) {
            val reader = MessageReader(PacketBytes.copyOf(byteArrayOf(value.toByte(), (value shr 8).toByte())), 0)
            assertEquals(value, reader.readSignedShort())
        }
        val reader = MessageReader(PacketBytes.copyOf(byteArrayOf(-1)), 0)
        val error = assertThrows(MessageReadException::class.java) { reader.readSignedShort() }
        assertEquals(MessageRejection.TRUNCATED, error.reason)
        assertEquals(0, reader.readcount)
    }
}
