package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.*
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.player_state_t
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlayerStateCodecTest {
    private fun write(message: NetworkMessage): ByteArray = sizebuf_t(message.size).apply {
        message.writeTo(this)
        assertEquals(message.size, cursize)
    }.let { it.data.copyOf(it.cursize) }

    private fun read(bytes: ByteArray): PlayerInfoMessage {
        val buffer = MessageReader(PacketBytes.copyOf(bytes), 0)
        val message = ServerMessage.parseFromBuffer(buffer) as PlayerInfoMessage
        assertEquals(bytes.size, buffer.readcount)
        buffer.data.fill(0)
        return message
    }

    @Test
    fun `all field masks match legacy decoding and retain raw payloads on reencoding`() {
        for (mask in 0..65535) {
            // The old parser independently determines payload order, signedness, and consumed length.
            val raw = sizebuf_t(256).apply {
                writeByte(17); writeShort(mask)
                repeat(253) { writeByte((it * 73 + 129).toByte()) }
                readcount = 1
            }
            val legacy = LegacyPlayerInfoReference().apply { parse(raw) }
            val bytes = raw.data.copyOf(raw.readcount)
            val message = read(bytes)
            assertEquals(mask, message.deltaFlags)
            assertArrayEquals(bytes, write(message), "mask=$mask")
            assertEquals(legacy.currentState, player_state_t().also(message::applyTo), "mask=$mask")
        }
    }

    @Test
    fun `outbound selection and quantization match the old writer at boundaries`() {
        val changes: List<(player_state_t, Int) -> Unit> = listOf(
            { s, v -> s.pmove.pm_type = v },
            { s, v -> s.pmove.origin[0] = v.toShort(); s.pmove.origin[2] = (-v).toShort() },
            { s, v -> s.pmove.velocity[1] = v.toShort() },
            { s, v -> s.pmove.pm_time = v.toByte(); s.pmove.pm_flags = v.toByte(); s.pmove.gravity = v.toShort() },
            { s, v -> s.pmove.delta_angles[0] = v.toShort() },
            { s, v -> s.viewoffset[0] = v / 8f; s.kick_angles[1] = -v / 8f },
            { s, v -> s.viewangles = floatArrayOf(v.toFloat(), -v.toFloat(), v / 1000f) },
            { s, v -> s.gunindex = v; s.gunframe = v; s.gunoffset[1] = v / 8f; s.gunangles[2] = -v / 8f },
            { s, v -> s.blend = floatArrayOf(v / 255f, v / 1000f, -v / 255f, 1f); s.fov = v + 0.5f; s.rdflags = v },
            { s, v -> for (i in 0 until MAX_STATS) s.stats[i] = (v + i).toShort() },
        )
        for (value in listOf(Int.MIN_VALUE, -65536, -32768, -257, -128, -1, 0, 1, 127, 128, 255, 256, 32767, 65535, Int.MAX_VALUE)) {
            for (change in changes) {
                val current = player_state_t().also { change(it, value) }
                val message = PlayerInfoMessage.fromGame(player_state_t(), current)
                assertArrayEquals(write(LegacyPlayerInfoReference(player_state_t(), current)), write(message))
                assertEquals(message, read(write(message)))
            }
        }
        // A sub-unit change must retain presence even when its encoded value is zero.
        val tiny = PlayerInfoMessage.fromGame(player_state_t(), player_state_t().apply { viewoffset[0] = 0.01f })
        assertEquals(PlayerDeltaVector(0, 0, 0), tiny.viewOffset)
        // Weapon offset/angle changes alone never add the weapon-frame group.
        val offsetsOnly = PlayerInfoMessage.fromGame(player_state_t(), player_state_t().apply { gunoffset[0] = 2f; gunangles[0] = 3f })
        assertNull(offsetsOnly.weaponFrame)
        assertEquals(PS_WEAPONINDEX, offsetsOnly.deltaFlags)
    }

    @Test
    fun `blend retains every byte and stats retain every slot including explicit zeroes`() {
        for (value in 0..255) {
            val message = PlayerInfoMessage(blend = PlayerBlend(value, value, value, value))
            assertEquals(message, read(write(message)))
            val engine = player_state_t().also(message::applyTo)
            assertArrayEquals(FloatArray(4) { value / 255f }, engine.blend)
        }
        for (slot in 0 until MAX_STATS) {
            val message = PlayerInfoMessage(stats = PlayerStatDelta(mapOf(slot to 0)))
            assertEquals(1 shl slot, message.stats.mask)
            assertEquals(message, read(write(message)))
            val engine = player_state_t().apply { stats.fill(7) }.also(message::applyTo)
            assertArrayEquals(ShortArray(MAX_STATS) { if (it == slot) 0 else 7 }, engine.stats)
        }
    }

    @Test
    fun `queued messages own engine arrays and selected stats and never share reconstructed storage`() {
        val previous = player_state_t()
        val current = player_state_t().apply {
            pmove.origin[0] = 12; viewoffset[1] = -2.25f; viewangles[2] = -90f
            gunindex = 4; gunframe = 5; gunangles[1] = 1.25f
            blend[2] = 0.5f; stats[31] = -1
        }
        val message = PlayerInfoMessage.fromGame(previous, current)
        val bytes = write(message)
        val hash = message.hashCode()
        previous.set(current); current.clear()
        assertArrayEquals(bytes, write(message))
        assertEquals(hash, message.hashCode())
        val first = player_state_t().also(message::applyTo)
        val second = player_state_t().also(message::applyTo)
        first.clear()
        assertEquals(read(bytes), message)
        assertEquals(12, second.pmove.origin[0].toInt())
        assertEquals(-1, second.stats[31].toInt())
        assertArrayEquals(bytes, write(message))
        val selected = mutableMapOf(31 to -1)
        val stats = PlayerStatDelta(selected)
        selected.clear()
        assertEquals(-1, stats[31])
        assertNotEquals(PlayerInfoMessage(), PlayerInfoMessage(weaponIndex = 0))
    }

    @Test
    fun `every truncated full player update rejects the whole body`() {
        val raw = sizebuf_t(256).apply {
            writeByte(17); writeShort(65535)
            repeat(253) { writeByte(255.toByte()) }
            readcount = 1
        }
        LegacyPlayerInfoReference().parse(raw)
        val bytes = raw.data.copyOf(raw.readcount)
        for (length in 1 until bytes.size) {
            val wire = PacketCodec.encodeSequenced(1, 0, null, PacketBytes.copyOf(byteArrayOf(7) + bytes.copyOf(length)))
            val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope
            assertEquals(MessageRejection.TRUNCATED, (MessageBodyCodec.decodeServer(envelope) as MessageDecodeResult.Rejected).reason)
        }
    }
}
