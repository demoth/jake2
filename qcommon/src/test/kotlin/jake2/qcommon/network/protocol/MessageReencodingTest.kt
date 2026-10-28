package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.*
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageReencodingTest {
    private fun write(message: NetworkMessage): ByteArray {
        val buffer = sizebuf_t(message.size)
        message.writeTo(buffer)
        assertEquals(message.size, buffer.cursize, "Declared size must fit an exactly sized buffer")
        return buffer.data.copyOf(buffer.cursize)
    }
    private fun parse(bytes: ByteArray): ServerMessage {
        val buffer = sizebuf_t(bytes.size).apply { writeBytes(bytes) }
        val result = ServerMessage.parseFromBuffer(buffer)!!
        assertEquals(buffer.cursize, buffer.readcount)
        return result
    }
    private fun roundTrip(message: ServerMessage) {
        val bytes = write(message)
        assertArrayEquals(bytes, write(parse(bytes)))
    }

    @Test
    fun `forced unchanged updates fit their declared size across entity number widths`() {
        for (index in listOf(1, 255, 256, MAX_EDICTS - 1)) {
            for (force in listOf(false, true)) for (isNew in listOf(false, true)) {
                val message = PacketEntitiesMessage().apply {
                    updates.add(EntityUpdate(entity_state_t(index), entity_state_t(index), force, isNew))
                }
                if (index < 256 && !isNew) assertEquals(if (force) 5 else 3, message.size)
                roundTrip(message)
            }
        }
    }

    @Test
    fun `changed and removed entities retain payload values and flag widths`() {
        val changes: List<(entity_state_t) -> Unit> = listOf(
            { it.modelindex = 7; it.modelindex2 = 8; it.modelindex3 = 9; it.modelindex4 = 10 },
            { it.frame = 255 }, { it.frame = 256 }, { it.frame = 65535 },
            { it.skinnum = 255 }, { it.skinnum = 65535 }, { it.skinnum = 0x12345678 },
            { it.effects = 255 }, { it.effects = 32767 }, { it.effects = 0x12345678 },
            { it.renderfx = 255 }, { it.renderfx = 32767 }, { it.renderfx = 0x12345678 },
            { it.origin = floatArrayOf(-1.125f, 2.25f, 3.5f) },
            { it.angles = floatArrayOf(-90f, 45f, 178.59375f) },
            { it.old_origin = floatArrayOf(1.125f, 2.25f, 3.5f); it.renderfx = RF_BEAM },
            { it.sound = 255; it.event = 255; it.solid = 65535 },
        )
        for (index in listOf(1, 256, MAX_EDICTS - 1)) for (change in changes) {
            val current = entity_state_t(index).also(change)
            val message = PacketEntitiesMessage().apply {
                updates.add(EntityUpdate(entity_state_t(index), current, false, true))
                val flags = U_REMOVE or if (index >= 256) U_NUMBER16 or U_MOREBITS1 else 0
                updates.add(EntityUpdate(DeltaEntityHeader(flags, index)))
            }
            roundTrip(message)
            val parsed = parse(write(message)) as PacketEntitiesMessage
            assertNotNull(parsed.updates[0].newState)
            assertNull(parsed.updates[1].newState)
        }
    }

    @Test
    fun `explicit zero fields and zero-valued flag extensions survive re-encoding`() {
        val old = entity_state_t(1).apply { modelindex = 5; origin[0] = 10f }
        val message = PacketEntitiesMessage().apply {
            updates.add(EntityUpdate(old, entity_state_t(1), false, false))
        }
        val decoded = parse(write(message)) as PacketEntitiesMessage
        assertTrue(decoded.updates.single().header.flags and U_MODEL != 0)
        assertEquals(0, decoded.updates.single().newState.modelindex)
        roundTrip(message)
        for (bytes in listOf(
            byteArrayOf(18, 128.toByte(), 0, 1, 0, 0),
            byteArrayOf(18, 128.toByte(), 128.toByte(), 128.toByte(), 0, 1, 0, 0),
        )) assertArrayEquals(bytes, write(parse(bytes)))
    }

    @Test
    fun `sound constructor and parser use the same entity and channel representation`() {
        for (flags in 0..31) for (index in listOf(0, 3, MAX_EDICTS - 1)) for (channel in 0..7) {
            val message = SoundMessage(flags, 2, 1f, 2f, 0.2f, (index shl 3) or channel,
                floatArrayOf(1f, -2f, 3f))
            val decoded = parse(write(message)) as SoundMessage
            assertEquals(if (flags and SND_ENT != 0) index else 0, decoded.entityIndex)
            assertEquals(if (flags and SND_ENT != 0) channel else 0, decoded.sendchan)
            roundTrip(message)
        }
    }

    @Test
    fun `outbound sound delay retains truncation and edited decoded delays use the same rule`() {
        val created = SoundMessage(SND_OFFSET, 2, 1f, 1f, 0.251f, 0, null)
        val expected = (0.251f * 1000).toInt()
        assertEquals(expected / 1000f, created.timeOffset)
        assertEquals(expected.toByte(), write(created).last())
        val decoded = parse(byteArrayOf(9, SND_OFFSET.toByte(), 2, 251.toByte())) as SoundMessage
        assertEquals(0.251f, decoded.timeOffset)
        assertEquals(251.toByte(), write(decoded).last())
        decoded.timeOffset = 0.1239f
        assertEquals(123.toByte(), write(decoded).last())
    }

    @Test
    fun `every quantized sound scalar byte survives decode and re-encode`() {
        for (value in 0..255) {
            val buffer = sizebuf_t(8).apply {
                writeByte(9); writeByte((SND_VOLUME or SND_ATTENUATION or SND_OFFSET or SND_ENT).toByte())
                writeByte(2); writeByte(value.toByte()); writeByte(value.toByte()); writeByte(value.toByte())
                writeShort((123 shl 3) or 7)
            }
            assertArrayEquals(buffer.data, write(parse(buffer.data)), "Scalar wire value $value")
        }
    }
}
