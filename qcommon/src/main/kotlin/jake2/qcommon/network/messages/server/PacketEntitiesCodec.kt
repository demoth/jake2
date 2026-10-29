package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.U_REMOVE
import jake2.qcommon.network.protocol.MessageReader
import jake2.qcommon.sizebuf_t

/** Packet-entity framing: changes carry payloads, removals carry headers, entity zero ends the list. */
internal object PacketEntitiesCodec {
    fun write(message: PacketEntitiesMessage, buffer: sizebuf_t) {
        for (update in message.updates) when (update) {
            is EntityUpdate.Change -> EntityDeltaCodec.write(update.delta, buffer)
            is EntityUpdate.Removal -> EntityDeltaCodec.writeHeader(update.flags, update.number, buffer)
        }
        // End of packetentities.
        buffer.writeShort(0)
    }

    fun read(buffer: MessageReader): PacketEntitiesMessage {
        val updates = ArrayList<EntityUpdate>()
        while (true) {
            val header = EntityDeltaCodec.readHeader(buffer)
            // End of packetentities; no payload follows, regardless of field bits.
            if (header.number == 0) return PacketEntitiesMessage(updates)
            updates += if (header.flags and U_REMOVE != 0) EntityUpdate.Removal(header.flags, header.number)
                else EntityUpdate.Change(EntityDeltaCodec.readPayload(header, buffer))
        }
    }

    fun size(message: PacketEntitiesMessage): Int = 2 + message.updates.sumOf { update ->
        when (update) {
            is EntityUpdate.Change -> EntityDeltaCodec.size(update.delta)
            is EntityUpdate.Removal -> EntityDeltaCodec.headerSize(update.flags)
        }
    }
}
