package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.*
import jake2.qcommon.DeltaUtils
import jake2.qcommon.entity_state_t
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EntityBaselineCodecTest {
    private fun write(message: SpawnBaselineMessage): ByteArray {
        val buffer = sizebuf_t(message.size)
        message.writeTo(buffer)
        assertEquals(buffer.maxsize, buffer.cursize)
        return buffer.data
    }

    private fun read(bytes: ByteArray): SpawnBaselineMessage {
        val buffer = MessageReader(PacketBytes.copyOf(bytes), 0)
        val message = ServerMessageCodec.read(buffer) as SpawnBaselineMessage
        assertEquals(bytes.size, buffer.readcount)
        buffer.data.fill(0)
        return message
    }

    @Test
    fun `baseline field widths match the legacy writer including signed and unsigned boundaries`() {
        val fields: List<(entity_state_t, Int) -> Unit> = listOf(
            { state, value -> state.frame = value }, { state, value -> state.skinnum = value },
            { state, value -> state.effects = value }, { state, value -> state.renderfx = value },
            { state, value -> state.solid = value }, { state, value -> state.modelindex = value },
            { state, value -> state.modelindex2 = value; state.modelindex3 = value; state.modelindex4 = value },
            { state, value -> state.sound = value; state.event = value },
        )
        for (index in listOf(1, 255, 256, MAX_EDICTS - 1)) for ((field, change) in fields.withIndex()) {
            for (value in listOf(Int.MIN_VALUE, -1, 0, 1, 255, 256, 32767, 32768, 65535, 65536, 0x12345678, Int.MAX_VALUE)) {
                val state = entity_state_t(index).also { change(it, value) }
                state.origin = floatArrayOf(-0.124f, 0.249f, 4096f)
                state.angles = floatArrayOf(-90f, 45f, 178.59375f)
                state.old_origin = floatArrayOf(1.125f, -2.25f, 3.5f)
                val legacy = sizebuf_t(128).apply {
                    writeByte(14)
                    DeltaUtils.WriteDeltaEntity(entity_state_t(), state, this, true, true)
                }.let { it.data.copyOf(it.cursize) }
                val message = SpawnBaselineMessage(state)
                val encoded = write(message)
                assertArrayEquals(legacy, encoded, "Entity $index field $field value $value")
                assertEquals(message, read(encoded))
                assertArrayEquals(encoded, write(read(encoded)))
            }
        }
    }

    @Test
    fun `shared planner retains force event and beam rules across entity header widths`() {
        for (index in listOf(1, 256)) for (force in listOf(false, true)) for (isNew in listOf(false, true)) {
            for (variant in 0..2) {
                val from = entity_state_t(index).apply { modelindex = 7; event = 1 }
                val to = from.getClone()
                if (variant == 1) { to.modelindex = 0; to.event = 0; to.origin[0] = 10f }
                if (variant == 2) to.renderfx = RF_BEAM
                val legacy = sizebuf_t(128).apply { DeltaUtils.WriteDeltaEntity(from, to, this, force, isNew) }
                val planned = EntityDeltaCodec.plan(EntityStateSnapshot.fromEngine(from), EntityStateSnapshot.fromEngine(to), force, isNew)
                val actual = if (planned == null) byteArrayOf() else sizebuf_t(EntityDeltaCodec.size(planned)).apply {
                    EntityDeltaCodec.write(planned, this)
                    assertEquals(maxsize, cursize)
                }.data
                assertArrayEquals(legacy.data.copyOf(legacy.cursize), actual)
                if (planned != null) assertEquals(planned, EntityDeltaCodec.read(MessageReader(PacketBytes.copyOf(actual), 0)))
            }
        }
        val unchanged = EntityStateSnapshot(index = 1)
        assertNull(EntityDeltaCodec.plan(unchanged, unchanged, false, false))
        assertEquals(0, EntityDeltaCodec.plan(unchanged, unchanged, true, false)!!.flags)
        val wideIdentity = EntityStateSnapshot(index = 256)
        // Preserve the legacy number-width header even for an otherwise unchanged unforced entity.
        assertEquals(U_NUMBER16 or U_MOREBITS1, EntityDeltaCodec.plan(wideIdentity, wideIdentity, false, false)!!.flags)
    }

    @Test
    fun `received baselines retain explicit zero fields extension bytes and selected signed widths`() {
        val flags = U_MOREBITS1 or U_MOREBITS2 or U_MOREBITS3 or U_NUMBER16 or U_MODEL or U_FRAME16 or U_SKIN16 or U_EFFECTS16 or U_RENDERFX16 or U_SOLID
        val state = entity_state_t(1).apply { frame = -1; skinnum = -1; effects = -1; renderfx = -1; solid = -1 }
        val wide = sizebuf_t(128).apply { writeByte(14); DeltaUtils.writeEntityDelta(flags, 1, state, this) }.let { it.data.copyOf(it.cursize) }
        for (bytes in listOf(wide, byteArrayOf(14, 128.toByte(), 0, 1),
            byteArrayOf(14, 128.toByte(), 128.toByte(), 128.toByte(), 0, 1),
            byteArrayOf(14, 128.toByte(), 128.toByte(), 128.toByte(), 128.toByte(), 1))) {
            assertArrayEquals(bytes, write(read(bytes)))
        }
        val decoded = read(wide)
        assertEquals(flags, decoded.delta.flags)
        assertEquals(0, decoded.entityState.modelIndex)
        assertEquals(-1, decoded.entityState.frame)
        assertEquals(-1, decoded.entityState.skinNumber)
    }

    @Test
    fun `baseline snapshots own engine arrays and export all fields into independent engine storage`() {
        val source = entity_state_t(7).apply {
            origin = floatArrayOf(1f, 2f, 3f); angles = floatArrayOf(0f, 45f, 90f); old_origin = floatArrayOf(4f, 5f, 6f)
            modelindex = 1; modelindex2 = 2; modelindex3 = 3; modelindex4 = 4
            frame = 17; skinnum = 23; effects = 32; renderfx = RF_BEAM; solid = 7; sound = 5; event = 2
        }
        val message = SpawnBaselineMessage(source)
        val original = source.getClone()
        val bytes = write(message)
        val hash = message.hashCode()
        source.clear()
        message.entityState.origin.toArray().fill(99f)
        val destination = entity_state_t()
        message.entityState.copyTo(destination)
        assertEquals(original, destination)
        destination.clear()
        assertEquals(hash, message.hashCode())
        assertEquals(Vector3f(1f, 2f, 3f), message.entityState.origin)
        assertArrayEquals(bytes, write(message.copy()))
        assertEquals(message, read(bytes))
    }

    @Test
    fun `invalid baseline entity indices reject the complete body before dispatch`() {
        for (index in listOf(MAX_EDICTS, 65535)) {
            val body = sizebuf_t(16).apply {
                writeByte(7); writeByte(14)
                DeltaUtils.writeEntityHeader(U_MOREBITS1 or U_NUMBER16, index, this)
            }.let { PacketBytes.copyOf(it.data, it.cursize) }
            val wire = PacketCodec.encodeSequenced(1, 0, null, body)
            val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope
            assertEquals(MessageRejection.INVALID_VALUE, (MessageBodyCodec.decodeServer(envelope) as MessageDecodeResult.Rejected).reason)
        }
    }
}
