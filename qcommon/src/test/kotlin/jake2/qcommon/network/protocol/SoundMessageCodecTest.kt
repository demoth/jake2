package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.*
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SoundMessageCodecTest {
    private fun write(message: SoundMessage): ByteArray {
        val buffer = sizebuf_t(message.size)
        message.writeTo(buffer)
        assertEquals(buffer.maxsize, buffer.cursize)
        return buffer.data
    }

    private fun read(bytes: ByteArray): SoundMessage {
        val reader = MessageReader(PacketBytes.copyOf(bytes), 0)
        val result = ServerMessageCodec.read(reader) as SoundMessage
        assertEquals(bytes.size, reader.readcount)
        reader.data.fill(0)
        return result
    }

    @Test
    fun `sound positions own their source and copies preserve independent field presence`() {
        val position = floatArrayOf(1f, -2f, 3f)
        val message = SoundMessage.fromGame(SND_POS or SND_ENT, 7, 0.25f, 2f, 0.2f, (3 shl 3) or 7, position)
        val bytes = write(message)
        val hash = message.hashCode()
        position.fill(99f)
        message.origin!!.toArray().fill(99f)
        assertArrayEquals(bytes, write(message))
        assertEquals(hash, message.hashCode())
        assertEquals(Vector3f(1f, -2f, 3f), message.origin)
        assertEquals(message, read(bytes))
        assertEquals(DEFAULT_SOUND_PACKET_VOLUME, message.volume)
        assertEquals(DEFAULT_SOUND_PACKET_ATTENUATION, message.attenuation)
        assertEquals(0f, message.timeOffset)
        val explicitDefault = message.copy(volumeByte = 255)
        assertEquals(message.volume, explicitDefault.volume)
        assertNotEquals(message, explicitDefault)
        assertEquals(message.flags or SND_VOLUME, explicitDefault.flags)
        assertEquals(explicitDefault, read(write(explicitDefault)))
        val changed = message.copy(volumeByte = 0, source = null, origin = null, timeOffsetMillis = 0)
        assertEquals(SND_VOLUME or SND_OFFSET, changed.flags)
        assertEquals(changed, read(write(changed)))
        assertArrayEquals(bytes, write(message))
    }

    @Test
    fun `outbound sound scalar and position quantization retains legacy truncation and wrapping`() {
        val message = SoundMessage.fromGame(SND_VOLUME or SND_ATTENUATION or SND_OFFSET or SND_POS,
            255, 0.5f, 4f, 0.251f, 0, floatArrayOf(-0.124f, 0.249f, 4096f))
        // Half volume truncates to 127; attenuation 4 wraps its scaled byte to 0.
        // Coordinate conversion truncates toward zero and wraps a signed short at 4096 units.
        val bytes = byteArrayOf(9, (SND_VOLUME or SND_ATTENUATION or SND_OFFSET or SND_POS).toByte(), -1,
            127, 0, (0.251f * 1000).toInt().toByte(), 0, 0, 1, 0, 0, 128.toByte())
        assertArrayEquals(bytes, write(message))
        val decoded = read(bytes)
        assertEquals(Vector3f(0f, 0.125f, -4096f), decoded.origin)
        assertArrayEquals(bytes, write(decoded))
    }

    @Test
    fun `every signed coordinate value survives sound decode and re-encode`() {
        for (coordinate in Short.MIN_VALUE..Short.MAX_VALUE) {
            val bytes = byteArrayOf(9, SND_POS.toByte(), 1, coordinate.toByte(), (coordinate shr 8).toByte(), 0, 0, 0, 0)
            assertArrayEquals(bytes, write(read(bytes)), "Coordinate $coordinate")
        }
    }
}
