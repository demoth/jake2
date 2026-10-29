package jake2.qcommon.network.demo

import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.transport.PacketBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DemoMessageDecoderTest {
    private fun int(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    // Independent classic wire fixtures from Yamagi CL_ParseServerData/CL_ParseFrame and Q2PRO CL_ParseFrame.
    private fun startup(protocol: Int) = byteArrayOf(12) + int(protocol) + int(1) + byteArrayOf(1) +
        "baseq2\u0000".toByteArray() + byteArrayOf(0, 0) + "test\u0000".toByteArray()
    private fun frame(protocol: Int, number: Int = 10) = byteArrayOf(20) + int(number) + int(-1) +
        (if (protocol == 26) byteArrayOf() else byteArrayOf(9)) + byteArrayOf(1, 3)
    private fun decode(decoder: DemoMessageDecoder, bytes: ByteArray) = decoder.decode(PacketBytes.copyOf(bytes))
    private fun messages(decoder: DemoMessageDecoder, bytes: ByteArray) =
        (decode(decoder, bytes) as MessageDecodeResult.Decoded).messages

    @Test fun `protocol 26 server data selects the shorter frame header inside the same record and persists`() {
        val decoder = DemoMessageDecoder()
        val first = messages(decoder, startup(26) + frame(26))
        assertEquals(26, decoder.protocol)
        val header = first.last() as FrameHeaderMessage
        assertEquals(10, header.frameNumber)
        assertEquals(-1, header.lastFrame)
        assertEquals(0, header.suppressCount)
        assertArrayEquals(byteArrayOf(3), header.areaBits.toByteArray())
        assertEquals(11, (messages(decoder, frame(26, 11)).single() as FrameHeaderMessage).frameNumber)
    }

    @Test fun `map server data can switch between all supported layouts without leaking across decoder instances`() {
        val decoder = DemoMessageDecoder()
        for (protocol in listOf(34, 26, 32, 26, 34)) {
            val header = messages(decoder, startup(protocol) + frame(protocol)).last() as FrameHeaderMessage
            assertEquals(protocol, decoder.protocol)
            assertEquals(if (protocol == 26) 0 else 9, header.suppressCount)
            assertArrayEquals(byteArrayOf(3), header.areaBits.toByteArray())
        }
        val independent = DemoMessageDecoder()
        messages(decoder, startup(26))
        assertEquals(34, independent.protocol)
        assertEquals(9, (messages(independent, frame(34)).single() as FrameHeaderMessage).suppressCount)
    }

    @Test fun `every truncated header prefix rejects without committing a protocol change`() {
        val header = frame(26)
        for (length in 1 until header.size) {
            val decoder = DemoMessageDecoder()
            messages(decoder, startup(32))
            val result = decode(decoder, startup(26) + header.copyOf(length)) as MessageDecodeResult.Rejected
            assertEquals(MessageRejection.TRUNCATED, result.reason, "Prefix $length")
            assertEquals(32, decoder.protocol)
            assertEquals(9, (messages(decoder, frame(32)).single() as FrameHeaderMessage).suppressCount)
        }
    }

    @Test fun `unsupported versions malformed suffixes and oversized areas reject whole records atomically`() {
        val decoder = DemoMessageDecoder()
        for (bytes in listOf(startup(25), startup(33), startup(35),
            startup(26) + byteArrayOf(254.toByte()),
            startup(26) + byteArrayOf(20) + int(10) + int(-1) + byteArrayOf(33))) {
            assertInstanceOf(MessageDecodeResult.Rejected::class.java, decode(decoder, bytes))
            assertEquals(34, decoder.protocol)
        }
    }

    @Test fun `protocol 26 demo context never changes ordinary body or live packet decoding`() {
        val decoder = DemoMessageDecoder()
        messages(decoder, startup(26) + frame(26))
        val classic34 = PacketBytes.copyOf(frame(34))
        val decoded = MessageBodyCodec.decodeServerBody(classic34) as MessageDecodeResult.Decoded
        assertEquals(9, (decoded.messages.single() as FrameHeaderMessage).suppressCount)
        val short26 = PacketBytes.copyOf(frame(26))
        assertInstanceOf(MessageDecodeResult.Rejected::class.java, MessageBodyCodec.decodeServerBody(short26))
        val wire = PacketCodec.encodeSequenced(1, 0, null, short26)
        val packet = (PacketCodec.decode(wire, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope
        assertInstanceOf(MessageDecodeResult.Rejected::class.java, MessageBodyCodec.decodeServer(packet))
    }
}
