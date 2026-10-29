package jake2.qcommon.network.protocol

import jake2.qcommon.Defines.*
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.sizebuf_t
import jake2.qcommon.network.transport.PacketBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PacketEntitiesCodecTest {
    private fun write(message: PacketEntitiesMessage): ByteArray = sizebuf_t(message.size).apply {
        message.writeTo(this)
        assertEquals(message.size, cursize)
    }.let { it.data.copyOf(it.cursize) }

    private fun read(bytes: ByteArray): PacketEntitiesMessage = MessageReader(PacketBytes.copyOf(bytes), 0).let {
        val message = ServerMessage.parseFromBuffer(it) as PacketEntitiesMessage
        assertEquals(bytes.size, it.readcount)
        message
    }

    @Test
    fun `queued changes own engine states and the update list and compare payloads`() {
        val previous = entity_state_t(1).apply { modelindex = 3; origin[0] = 1f }
        val current = entity_state_t(1).apply { modelindex = 7; origin[0] = 2.125f }
        val entries = arrayListOf<EntityUpdate>(requireNotNull(EntityUpdate.plan(previous, current, false, false)))
        val message = PacketEntitiesMessage(entries)
        val bytes = write(message)
        val hash = message.hashCode()
        previous.clear(); current.clear(); entries.clear()
        assertArrayEquals(bytes, write(message))
        assertEquals(hash, message.hashCode())
        assertEquals(message, read(bytes))
        assertThrows(UnsupportedOperationException::class.java) { (message.updates as MutableList).clear() }
        val change = message.updates.single() as EntityUpdate.Change
        val differentPayload = PacketEntitiesMessage(listOf(change.copy(delta = change.delta.copy(state = change.delta.state.copy(modelIndex = 8)))))
        assertNotEquals(message, differentPayload, "Equal headers must not hide different payloads")
    }

    @Test
    fun `removals preserve extended headers without consuming selected payload bits`() {
        for (index in listOf(1, 255, 256, MAX_EDICTS - 1)) {
            val flags = U_REMOVE or U_MODEL or U_ORIGIN1 or U_MOREBITS1 or U_MOREBITS2 or U_MOREBITS3 or
                (if (index >= 256) U_NUMBER16 else 0)
            val bytes = sizebuf_t(32).apply {
                writeByte(18)
                jake2.qcommon.DeltaUtils.writeEntityHeader(flags, index, this)
                writeShort(0)
            }.let { it.data.copyOf(it.cursize) }
            assertEquals(EntityUpdate.Removal(flags, index), read(bytes).updates.single())
            assertArrayEquals(bytes, write(read(bytes)))
        }
        // Entity zero ends the list before fields are read, even with a model bit set.
        assertTrue(read(byteArrayOf(18, 128.toByte(), 8, 0)).updates.isEmpty())
    }

    @Test
    fun `invalid indices and truncated headers or payloads reject the entire body`() {
        val valid = write(PacketEntitiesMessage(listOf(requireNotNull(EntityUpdate.plan(entity_state_t(256),
            entity_state_t(256).apply { modelindex = 4; origin[2] = 7f }, true, true)))))
        for (length in 1 until valid.size) {
            val result = decodeBody(valid.copyOf(length))
            assertEquals(MessageRejection.TRUNCATED, (result as MessageDecodeResult.Rejected).reason)
        }
        for (index in listOf(MAX_EDICTS, 65535)) {
            val bytes = sizebuf_t(16).apply {
                writeByte(18)
                jake2.qcommon.DeltaUtils.writeEntityHeader(U_MOREBITS1 or U_NUMBER16 or U_REMOVE, index, this)
                writeShort(0)
            }.let { it.data.copyOf(it.cursize) }
            assertEquals(MessageRejection.INVALID_VALUE, (decodeBody(bytes) as MessageDecodeResult.Rejected).reason)
        }
    }

    private fun decodeBody(bytes: ByteArray): MessageDecodeResult<ServerMessage> {
        // Prefix a valid message to check that malformed packetentities cannot deliver a partial body.
        val body = PacketBytes.copyOf(byteArrayOf(7) + bytes)
        val wire = PacketCodec.encodeSequenced(1, 0, null, body)
        val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope
        return MessageBodyCodec.decodeServer(envelope)
    }
}
