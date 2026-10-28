package jake2.qcommon.network.messages.server;

import jake2.qcommon.*;
import jake2.qcommon.network.protocol.MessageReadException;
import jake2.qcommon.network.protocol.MessageRejection;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains state changes for multiple entities.
 */
public class PacketEntitiesMessage extends ServerMessage {

    public final List<EntityUpdate> updates = new ArrayList<>();

    public PacketEntitiesMessage() {
        super(ServerMessageType.svc_packetentities);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        for (EntityUpdate u : updates) {
            // Outbound changes calculate their header from baseline/current state.
            if (u.header == null) {
                DeltaUtils.WriteDeltaEntity(u.oldState, u.newState, buffer, u.force, u.isNewEntity);
            } else if ((u.header.flags & Defines.U_REMOVE) != 0) {
                // Entity removal carries only a header (former entity bits).
                DeltaUtils.writeEntityHeader(u.header.flags, u.header.number, buffer);
            } else {
                // Received changes already have a wire header; preserve its field presence and values.
                DeltaUtils.writeEntityDelta(u.header.flags, u.header.number, u.newState, buffer);
            }
        }
        // end of packetentities
        buffer.writeShort(0);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        while (true) {
            DeltaEntityHeader header = ServerMessage.parseDeltaEntityHeader(buffer);
            // end of packetentities
            if (0 == header.number)
                break;

            if (header.number >= Defines.MAX_EDICTS)
                throw new MessageReadException(MessageRejection.INVALID_VALUE, buffer.readcount, "CL_ParsePacketEntities: bad number:" + header.number);

            if (buffer.readcount > buffer.cursize)
                throw new MessageReadException(MessageRejection.TRUNCATED, buffer.readcount, "CL_ParsePacketEntities: end of message");


            if ((header.flags & Defines.U_REMOVE) != 0) {
                updates.add(new EntityUpdate(header));
            } else {
                entity_state_t newState = parseEntityState(header.number, header.flags, buffer);
                updates.add(new EntityUpdate(header, newState));
            }
        }
    }

    @Override
    public int getSize() {
        return 1 + updates.stream().mapToInt(this::getUpdateSize).sum() + 2;
    }

    private int getUpdateSize(EntityUpdate value) {
        if (value.header == null) {
            // Outbound change, including the forced unchanged-header case.
            return DeltaUtils.getDeltaSize(value.oldState, value.newState, value.force, value.isNewEntity);
        } else if ((value.header.flags & Defines.U_REMOVE) != 0) {
            // Entity removal has no payload.
            return DeltaUtils.getEntityHeaderSize(value.header.flags);
        } else {
            return DeltaUtils.getEntityDeltaSize(value.header.flags);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PacketEntitiesMessage)) return false;

        PacketEntitiesMessage that = (PacketEntitiesMessage) o;

        return updates != null ? updates.equals(that.updates) : that.updates == null;
    }

    @Override
    public int hashCode() {
        return updates != null ? updates.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "PacketEntitiesMessage{" +
                "updates=" + updates +
                '}';
    }
}

