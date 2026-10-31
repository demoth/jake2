package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.*
import jake2.qcommon.Globals
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TemporaryEntityCodecTest {
    private data class Family(val styles: Set<Int>, val payload: ByteArray, val value: (Int) -> TEMessage)
    private val position = Vector3f(1f, 2f, 3f)
    private val end = Vector3f(4f, 5f, 6f)
    private val offset = Vector3f(7f, 8f, 9f)
    private val pointBytes = byteArrayOf(8, 0, 16, 0, 24, 0)
    private val endBytes = byteArrayOf(32, 0, 40, 0, 48, 0)
    private val offsetBytes = byteArrayOf(56, 0, 64, 0, 72, 0)

    private fun families() = listOf(
        Family(PointTEMessage.SUBTYPES, pointBytes) { PointTEMessage(it, position) },
        Family(BeamTEMessage.SUBTYPES, byteArrayOf(-1, -1) + pointBytes + endBytes) { BeamTEMessage(it, -1, position, end) },
        Family(BeamOffsetTEMessage.SUBTYPES, byteArrayOf(0, -128) + pointBytes + endBytes + offsetBytes) {
            BeamOffsetTEMessage(it, -32768, position, end, offset)
        },
        Family(PointDirectionTEMessage.SUBTYPES, pointBytes + byteArrayOf(161.toByte())) { PointDirectionTEMessage(it, position, 161) },
        Family(TrailTEMessage.SUBTYPES, pointBytes + endBytes) { TrailTEMessage(it, position, end) },
        Family(SplashTEMessage.SUBTYPES, byteArrayOf(-1) + pointBytes + byteArrayOf(161.toByte(), -2)) { SplashTEMessage(it, 255, position, 161, 254) },
        // Independent field layouts from Yamagi CL_ParseTEnt / CL_ParseLightning / CL_ParseSteam / CL_ParseWidow.
        Family(setOf(TE_LIGHTNING), byteArrayOf(0, -128, -1, 127) + pointBytes + endBytes) {
            LightningTEMessage(-32768, 32767, position, end)
        },
        Family(setOf(TE_FLASHLIGHT), pointBytes + byteArrayOf(-1, -1)) { FlashlightTEMessage(position, -1) },
        Family(setOf(TE_FORCEWALL), pointBytes + endBytes + byteArrayOf(-2)) { ForceWallTEMessage(position, end, 254) },
        Family(setOf(TE_STEAM), byteArrayOf(-1, -1, -1) + pointBytes + byteArrayOf(161.toByte(), -2, 0, -128)) {
            SteamTEMessage(-1, 255, position, 161, 254, -32768)
        },
        Family(setOf(TE_STEAM), byteArrayOf(7, 0, -1) + pointBytes + byteArrayOf(161.toByte(), -2, -1, 127, 4, 3, 2, 1)) {
            SteamTEMessage(7, 255, position, 161, 254, 32767, 0x01020304)
        },
        Family(setOf(TE_WIDOWBEAMOUT), byteArrayOf(0, -128) + pointBytes) { WidowBeamOutTEMessage(-32768, position) },
    )

    private fun write(message: TEMessage): ByteArray {
        val buffer = sizebuf_t(message.size)
        message.writeTo(buffer)
        assertEquals(buffer.maxsize, buffer.cursize)
        return buffer.data
    }

    private fun read(bytes: ByteArray): TEMessage {
        val reader = MessageReader(PacketBytes.copyOf(bytes), 0)
        val message = ServerMessageCodec.read(reader) as TEMessage
        assertEquals(bytes.size, reader.readcount)
        reader.data.fill(0)
        return message
    }

    private fun body(bytes: ByteArray): MessageDecodeResult<ServerMessage> {
        val wire = PacketCodec.encodeSequenced(1, 0, null, PacketBytes.copyOf(bytes))
        val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope
        return MessageBodyCodec.decodeServer(envelope)
    }

    @Test
    fun `all supported subtypes retain their exact field order widths and signed owners`() {
        // TE_RAILTRAIL2 (31) and TE_FLAME (32) have no wire parser in protocol 34's reference client.
        assertEquals((0..55).toSet() - setOf(TE_RAILTRAIL2, TE_FLAME), families().flatMap { it.styles }.toSet())
        for (family in families()) for (style in family.styles) {
            val message = family.value(style)
            val expected = byteArrayOf(3, style.toByte()) + family.payload
            assertArrayEquals(expected, write(message), "Style $style")
            assertEquals(message, read(expected))
            val decoded = body(expected + byteArrayOf(10, 2, 'o'.code.toByte(), 'k'.code.toByte(), 0)) as MessageDecodeResult.Decoded
            assertEquals("ok", (decoded.messages.last() as PrintMessage).text, "Style $style must consume exactly its payload")
            assertEquals(message.hashCode(), read(expected).hashCode())
            for (length in 1 until expected.size) {
                val result = body(expected.copyOf(length)) as MessageDecodeResult.Rejected
                assertEquals(MessageRejection.TRUNCATED, result.reason, "Style $style prefix $length")
            }
        }
    }

    @Test
    fun `normal indices survive decoding without requantization and invalid indices reject atomically`() {
        for (index in 0 until NUMVERTEXNORMALS) {
            val values = listOf(PointDirectionTEMessage(TE_GUNSHOT, position, index), SplashTEMessage(TE_SPLASH, 1, position, index, 7))
            for (message in values) assertArrayEquals(write(message), write(read(write(message))))
            assertEquals(Vector3f(Globals.bytedirs[index]), DirectionCodec.vector(index))
        }
        for (index in NUMVERTEXNORMALS..255) {
            for (bytes in listOf(
                byteArrayOf(3, TE_GUNSHOT.toByte()) + pointBytes + byteArrayOf(index.toByte()),
                byteArrayOf(3, TE_SPLASH.toByte(), 1) + pointBytes + byteArrayOf(index.toByte(), 7),
            )) assertEquals(MessageRejection.INVALID_VALUE, (body(byteArrayOf(7) + bytes) as MessageDecodeResult.Rejected).reason)
        }
        val known = families().flatMap { it.styles }.toSet()
        for (style in 0..255) if (style !in known) {
            assertEquals(MessageRejection.INVALID_VALUE,
                (body(byteArrayOf(7, 3, style.toByte())) as MessageDecodeResult.Rejected).reason)
        }
    }

    @Test
    fun `game direction quantization matches the legacy encoder including null zero and arbitrary vectors`() {
        val random = Random(173)
        val directions = listOf<FloatArray?>(null, floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 1f, 0f)) +
            List(1024) { FloatArray(3) { random.nextFloat() * 20f - 10f } }
        for (direction in directions) {
            val reference = sizebuf_t(1).apply { writeDir(direction) }
            val value = PointDirectionTEMessage(TE_GUNSHOT, floatArrayOf(0f, 0f, 0f), direction)
            assertEquals(reference.data[0].toInt() and 255, value.directionIndex)
        }
    }

    @Test
    fun `queued temporary effects own every source vector and preserve unsigned game byte wrapping`() {
        val source = floatArrayOf(1f, 2f, 3f)
        val destination = floatArrayOf(4f, 5f, 6f)
        val offset = floatArrayOf(7f, 8f, 9f)
        val direction = Globals.bytedirs[0].clone()
        val values = listOf(
            PointTEMessage(TE_EXPLOSION1, source), BeamTEMessage(TE_PARASITE_ATTACK, 2, source, destination),
            BeamOffsetTEMessage(TE_GRAPPLE_CABLE, 3, source, destination, offset),
            PointDirectionTEMessage(TE_GUNSHOT, source, direction), TrailTEMessage(TE_RAILTRAIL, source, destination),
            SplashTEMessage(TE_SPLASH, 511, source, direction, -1),
        )
        val before = values.map { write(it) to it.hashCode() }
        listOf(source, destination, offset, direction).forEach { it.fill(99f) }
        DirectionCodec.vector(0).toArray().fill(99f)
        for ((message, original) in values.zip(before)) {
            assertArrayEquals(original.first, write(message))
            assertEquals(original.second, message.hashCode())
            assertEquals(message, read(original.first))
        }
        assertEquals(255, (values.last() as SplashTEMessage).count)
        assertEquals(255, (values.last() as SplashTEMessage).param)
    }

    @Test
    fun `effect subtype participates in equality and constructors reject mismatched layouts`() {
        for (family in families().filter { it.styles.size > 1 }) {
            val styles = family.styles.toList()
            val first = family.value(styles[0])
            val second = family.value(styles[1])
            assertNotEquals(first, second)
            assertEquals(2, setOf(first, second).size)
            assertThrows(IllegalArgumentException::class.java) { family.value(255) }
        }
        assertThrows(IllegalArgumentException::class.java) { PointDirectionTEMessage(TE_GUNSHOT, position, 255) }
        assertThrows(IllegalArgumentException::class.java) { SteamTEMessage(-1, 1, position, 0, 1, 60, 100) }
        assertThrows(IllegalArgumentException::class.java) { SteamTEMessage(1, 1, position, 0, 1, 60) }
        assertThrows(IllegalArgumentException::class.java) { SteamTEMessage(-1, 1, position, 255, 1, 60) }
    }
}
