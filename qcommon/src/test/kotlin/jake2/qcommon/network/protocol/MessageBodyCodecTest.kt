package jake2.qcommon.network.protocol

import jake2.qcommon.Defines
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.messages.client.ClientMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.*
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Message decoding deliberately requires no engine, Cvar, or logger initialization. */
class MessageBodyCodecTest {
    private fun decode(bytes: ByteArray, client: Boolean = false): MessageDecodeResult<*> {
        val wire = PacketCodec.encodeSequenced(1, 0, if (client) 7 else null, PacketBytes.copyOf(bytes))
        val envelope = (PacketCodec.decode(wire, if (client) PacketDirection.FROM_CLIENT else PacketDirection.FROM_SERVER)
            as PacketDecodeResult.Decoded).envelope
        return if (client) MessageBodyCodec.decodeClient(envelope, 1) else MessageBodyCodec.decodeServer(envelope)
    }

    @ParameterizedTest(name = "{0} complete fixture and truncated prefixes")
    @MethodSource("jake2.qcommon.network.messages.NetworkMessageSizeTest#createTestData")
    fun `valid fixtures decode and every incomplete message prefix rejects`(name: String, message: NetworkMessage) {
        val bytes = javaClass.getResourceAsStream("/jake2/qcommon/network/messages/$name.bin")!!.use { it.readBytes() }
        val client = message is ClientMessage
        assertInstanceOf(MessageDecodeResult.Decoded::class.java, decode(bytes, client), name)
        assertInstanceOf(MessageDecodeResult.Decoded::class.java, decode(byteArrayOf(), client), "Header-only channel packets remain valid")
        for (length in 1 until bytes.size) {
            assertInstanceOf(MessageDecodeResult.Rejected::class.java, decode(bytes.copyOf(length), client), "$name prefix $length")
        }
    }

    @Test
    fun `reject malformed lengths and values without unchecked parser errors`() {
        val badDownloads = listOf(-2, -32768, 32767)
        for (length in badDownloads) {
            val result = decode(byteArrayOf(16, length.toByte(), (length shr 8).toByte(), 0))
            assertInstanceOf(MessageDecodeResult.Rejected::class.java, result)
        }
        // Missing inventory fields, oversized frame area bits, missing/invalid direction, entity bounds.
        val frame = sizebuf_t(16).apply {
            writeByte(20); writeInt(1); writeInt(0); writeByte(0); writeByte((Defines.MAX_MAP_AREAS / 8 + 1).toByte())
        }
        val invalidSound = sizebuf_t(8).apply {
            writeByte(9); writeByte(Defines.SND_ENT.toByte()); writeByte(1); writeShort(Defines.MAX_EDICTS shl 3)
        }
        val direction = sizebuf_t(16).apply {
            writeByte(3); writeByte(Defines.TE_GUNSHOT.toByte()); writePos(floatArrayOf(0f, 0f, 0f)); writeByte(255.toByte())
        }
        val entity = sizebuf_t(8).apply {
            writeByte(18); writeByte(Defines.U_MOREBITS1.toByte()); writeByte((Defines.U_NUMBER16 shr 8).toByte()); writeShort(Defines.MAX_EDICTS)
        }
        assertEquals(MessageRejection.INVALID_VALUE,
            (decode(entity.data.copyOf(entity.cursize)) as MessageDecodeResult.Rejected).reason)
        for (body in listOf(byteArrayOf(5), byteArrayOf(3, 254.toByte()), frame.data.copyOf(frame.cursize),
            invalidSound.data.copyOf(invalidSound.cursize), direction.data.copyOf(direction.cursize), entity.data.copyOf(entity.cursize))) {
            assertInstanceOf(MessageDecodeResult.Rejected::class.java, decode(body))
        }
    }

    @Test
    fun `entity and config indices cannot escape destination tables`() {
        for (opcode in listOf(1, 2)) {
            for (index in listOf(-1, Defines.MAX_EDICTS)) {
                val bytes = byteArrayOf(opcode.toByte(), index.toByte(), (index shr 8).toByte(), 1)
                assertEquals(MessageRejection.INVALID_VALUE, (decode(bytes) as MessageDecodeResult.Rejected).reason)
            }
        }
        for (index in listOf(-1, Defines.MAX_CONFIGSTRINGS)) {
            val bytes = byteArrayOf(13, index.toByte(), (index shr 8).toByte(), 0)
            assertEquals(MessageRejection.INVALID_VALUE, (decode(bytes) as MessageDecodeResult.Rejected).reason)
        }
    }

    @Test
    fun `known download failure markers and explicit packet endings remain supported`() {
        for (length in listOf(-1, -32640)) {
            val result = decode(byteArrayOf(16, length.toByte(), (length shr 8).toByte(), 0)) as MessageDecodeResult.Decoded
            assertNull((result.messages.single() as DownloadMessage).data)
        }
        for (body in listOf(byteArrayOf(6), byteArrayOf(255.toByte())))
            assertTrue((decode(body) as MessageDecodeResult.Decoded).messages.isEmpty())
        assertTrue((decode(byteArrayOf(0), true) as MessageDecodeResult.Decoded).messages.isEmpty())
        assertInstanceOf(MessageDecodeResult.Rejected::class.java, decode(byteArrayOf(255.toByte(), 7)))
        assertInstanceOf(MessageDecodeResult.Rejected::class.java, decode(byteArrayOf(0, 1), true))
    }

    @Test
    fun `unknown opcode rejects the whole packet rather than publishing a valid prefix`() {
        // disconnect prefix must not be delivered when followed by an unknown server opcode.
        val server = decode(byteArrayOf(7, 254.toByte())) as MessageDecodeResult.Rejected
        assertEquals(MessageRejection.UNKNOWN_OPCODE, server.reason)
        // string command prefix must not execute when followed by an unknown client opcode.
        val client = decode(byteArrayOf(4, 'x'.code.toByte(), 0, 99), true) as MessageDecodeResult.Rejected
        assertEquals(MessageRejection.UNKNOWN_OPCODE, client.reason)
        assertInstanceOf(MessageDecodeResult.Rejected::class.java, decode(byteArrayOf(254.toByte(), 7)))
        assertInstanceOf(MessageDecodeResult.Rejected::class.java, decode(byteArrayOf(99, 4, 'x'.code.toByte(), 0), true))
    }

    @Test
    fun `legacy factory entry points also enforce bounds and never synthesize inventory data`() {
        val raw = sizebuf_t(1).apply { writeByte(5) }
        val error = assertThrows(MessageReadException::class.java) { ServerMessage.parseFromBuffer(raw) }
        assertEquals(MessageRejection.TRUNCATED, error.reason)
        assertTrue(raw.readcount <= raw.cursize)
    }

    @Test
    fun `repeat decoding uses owned packet bytes and direction mistakes reject explicitly`() {
        val bytes = PacketCodec.encodeSequenced(1, 0, null, PacketBytes.copyOf(byteArrayOf(7)))
        val envelope = (PacketCodec.decode(bytes, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope
        val packet = NetworkPacket.fromEnvelope(NetworkAddress.Udp(IpAddress.fromBytes(byteArrayOf(127,0,0,1)), 27910), envelope)
        packet.buffer.data.fill(0)
        assertEquals(packet.parseBodyFromServer(), packet.parseBodyFromServer())
        assertInstanceOf(DisconnectMessage::class.java, (packet.parseBodyFromServer() as MessageDecodeResult.Decoded).messages.single())
        assertEquals(MessageRejection.WRONG_DIRECTION, (packet.parseBodyFromClient(1) as MessageDecodeResult.Rejected).reason)
        val connectionless = PacketBytes.copyOf(byteArrayOf(-1,-1,-1,-1) + "ping".toByteArray())
        val decoded = (PacketCodec.decode(connectionless, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope
        assertEquals(MessageRejection.NOT_SEQUENCED, (MessageBodyCodec.decodeServer(decoded) as MessageDecodeResult.Rejected).reason)
    }
}
