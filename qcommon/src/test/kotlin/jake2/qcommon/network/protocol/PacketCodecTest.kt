package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.MAX_MSGLEN
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.transport.IpAddress
import jake2.qcommon.network.transport.LocalSide
import jake2.qcommon.network.transport.NetworkAddress
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import jake2.qcommon.usercmd_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

/** Deliberately has no engine/Cvar initialization: envelope decoding is standalone. */
class PacketCodecTest {
    @ParameterizedTest
    @EnumSource(PacketDirection::class)
    fun `reject every truncated sequenced header`(direction: PacketDirection) {
        for (length in 0 until direction.sequencedHeaderSize) {
            val bytes = ByteArray(length)
            if (length > 0) bytes[0] = 1
            assertEquals(PacketDecodeResult.Rejected(PacketRejection.TRUNCATED_HEADER),
                PacketCodec.decode(PacketBytes.copyOf(bytes), direction), "length=$length, direction=$direction")
        }
    }

    @Test
    fun `short ff prefix cannot masquerade as a connectionless packet`() {
        for (length in 0..3) {
            assertEquals(PacketDecodeResult.Rejected(PacketRejection.TRUNCATED_HEADER),
                PacketCodec.decode(PacketBytes.copyOf(ByteArray(length) { -1 }), PacketDirection.FROM_CLIENT))
        }
        for (suffix in listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(10), byteArrayOf(-1))) {
            assertEquals(PacketDecodeResult.Rejected(PacketRejection.EMPTY_CONNECTIONLESS_COMMAND),
                PacketCodec.decode(PacketBytes.copyOf(byteArrayOf(-1, -1, -1, -1) + suffix), PacketDirection.FROM_CLIENT))
        }
    }

    @ParameterizedTest
    @EnumSource(PacketDirection::class)
    fun `decode little endian sequence flags without consuming body bytes`(direction: PacketDirection) {
        val header = byteArrayOf(0x78, 0x56, 0x34, 0x92.toByte(), 0xef.toByte(), 0xcd.toByte(), 0xab.toByte(), 0x89.toByte())
        val qport = if (direction == PacketDirection.FROM_CLIENT) byteArrayOf(0xfe.toByte(), 0xff.toByte()) else byteArrayOf()
        val wire = header + qport + byteArrayOf(0x23, 0x45)
        val envelope = assertInstanceOf(PacketEnvelope.Sequenced::class.java, decode(wire, direction))

        assertEquals(0x92345678.toInt(), envelope.sequence)
        assertEquals(0x89abcdef.toInt(), envelope.sequenceAck)
        assertEquals(if (direction == PacketDirection.FROM_CLIENT) 65534 else null, envelope.qport)
        assertEquals(direction.sequencedHeaderSize, envelope.headerSize)
        assertArrayEquals(wire, envelope.bytes.toByteArray())
        assertArrayEquals(wire, PacketCodec.encodeSequenced(envelope.sequence, envelope.sequenceAck,
            envelope.qport, PacketBytes.copyOf(byteArrayOf(0x23, 0x45))).toByteArray())

        val source = NetworkAddress.Udp(IpAddress.fromBytes(byteArrayOf(127, 0, 0, 1)), 27910)
        val packet = NetworkPacket.fromEnvelope(source, envelope)
        assertEquals(envelope.sequence, packet.sequence)
        assertEquals(envelope.sequenceAck, packet.sequenceAck)
        assertEquals(wire.size, packet.buffer.cursize)
        assertEquals(direction.sequencedHeaderSize, packet.buffer.readcount)
        assertEquals(0x23, packet.buffer.readByte())
        assertEquals(0x45, packet.buffer.readByte())
        assertEquals(27910, packet.from.port)
        packet.buffer.data.fill(0)
        packet.sequence = 0
        assertArrayEquals(wire, envelope.bytes.toByteArray())
        assertEquals(0x92345678.toInt(), envelope.sequence)
    }

    @ParameterizedTest
    @EnumSource(PacketDirection::class)
    fun `header-only sequenced packets are valid`(direction: PacketDirection) {
        val bytes = ByteArray(direction.sequencedHeaderSize)
        bytes[0] = 1
        val envelope = assertInstanceOf(PacketEnvelope.Sequenced::class.java, decode(bytes, direction))
        assertEquals(1, envelope.sequence)
        assertEquals(if (direction == PacketDirection.FROM_CLIENT) 0 else null, envelope.qport)
    }

    @ParameterizedTest
    @ValueSource(strings = ["\n", "\u0000"])
    fun `server info and print replies preserve multiline text after command separator`(separator: String) {
        for (command in listOf("info", "print")) {
            val envelope = assertInstanceOf(PacketEnvelope.Connectionless::class.java,
                decode(connectionless("$command${separator}Café\nsecond line\n"), PacketDirection.FROM_SERVER))
            assertEquals(command, envelope.commandLine)
            assertEquals("Café\nsecond line\n", envelope.parameters)
        }
    }

    @Test
    fun `connectionless commands accept datagram boundary and legacy terminators`() {
        for (suffix in listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(-1), byteArrayOf(10))) {
            val envelope = assertInstanceOf(PacketEnvelope.Connectionless::class.java,
                decode(connectionless("challenge 123") + suffix, PacketDirection.FROM_SERVER))
            assertEquals("challenge 123", envelope.commandLine)
            assertNull(envelope.parameters)
        }
        val request = assertInstanceOf(PacketEnvelope.Connectionless::class.java,
            decode(connectionless("info 34\n"), PacketDirection.FROM_CLIENT))
        assertEquals("info 34", request.commandLine)
        assertNull(request.parameters)
        val unknown = assertInstanceOf(PacketEnvelope.Connectionless::class.java,
            decode(connectionless("future_command data"), PacketDirection.FROM_CLIENT))
        assertEquals("future_command data", unknown.commandLine)
    }

    @Test
    fun `text payload stops at terminator and empty print body stays empty`() {
        val envelope = assertInstanceOf(PacketEnvelope.Connectionless::class.java,
            decode(connectionless("print\nhello\u0000ignored"), PacketDirection.FROM_SERVER))
        assertEquals("hello", envelope.parameters)
        val empty = assertInstanceOf(PacketEnvelope.Connectionless::class.java,
            decode(connectionless("print"), PacketDirection.FROM_SERVER))
        assertEquals("", empty.parameters)
    }

    @Test
    fun `full sized datagram is valid but oversized input is rejected`() {
        val wire = connectionless("x".repeat(MAX_MSGLEN - 4))
        val envelope = assertInstanceOf(PacketEnvelope.Connectionless::class.java,
            decode(wire, PacketDirection.FROM_CLIENT))
        assertEquals(MAX_MSGLEN - 4, envelope.commandLine.length)
        assertArrayEquals(wire, envelope.bytes.toByteArray())
        assertEquals(PacketDecodeResult.Rejected(PacketRejection.OVERSIZED),
            PacketCodec.decode(PacketBytes.copyOf(wire + byteArrayOf(1)), PacketDirection.FROM_CLIENT))
    }

    @Test
    fun `legacy body reader retains movement checksum offsets`() {
        val sequence = 17
        val newest = usercmd_t().apply { forwardmove = 120; msec = 16 }
        val movement = MoveMessage.fromInput(false, 12, usercmd_t(), usercmd_t(), newest)
        val buffer = sizebuf_t(MAX_MSGLEN)
        buffer.writeInt(sequence)
        buffer.writeInt(5)
        buffer.writeShort(42)
        movement.writeTo(buffer, sequence)

        val envelope = decode(buffer.data.copyOf(buffer.cursize), PacketDirection.FROM_CLIENT)
        val packet = NetworkPacket.fromEnvelope(NetworkAddress.Local(UUID.randomUUID(), LocalSide.FIRST), envelope)
        val parsed = assertInstanceOf(MoveMessage::class.java, (packet.parseBodyFromClient(sequence) as MessageDecodeResult.Decoded).messages.single())
        assertTrue(parsed.checksumValid)
        assertEquals(12, parsed.lastReceivedFrame)
        assertEquals(120, parsed.newCmd.forwardmove)
        assertEquals(16, parsed.newCmd.msec)
    }

    private fun connectionless(text: String): ByteArray =
        byteArrayOf(-1, -1, -1, -1) + text.toByteArray(Charsets.ISO_8859_1)

    private fun decode(bytes: ByteArray, direction: PacketDirection): PacketEnvelope =
        assertInstanceOf(PacketDecodeResult.Decoded::class.java, PacketCodec.decode(PacketBytes.copyOf(bytes), direction)).envelope
}
