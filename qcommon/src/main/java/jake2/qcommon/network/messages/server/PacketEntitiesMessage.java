package jake2.qcommon.network.messages.server;

import jake2.qcommon.*;

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
            // During serialization we don't have a header for entity state changes
            if (u.header == null) {
                // entity is changed
                MSG.WriteDeltaEntity(u.oldState, u.newState, buffer, u.force, u.isNewEntity);
            } else {
                // entity is removed
                MSG.WriteByte(buffer, u.header.flags & 255);

                if ((u.header.flags & 0x0000ff00) != 0)
                    MSG.WriteByte(buffer, (u.header.flags >> 8) & 255);

                if ((u.header.flags & Defines.U_NUMBER16) != 0)
                    MSG.WriteShort(buffer, u.header.number);
                else
                    MSG.WriteByte(buffer, u.header.number);

            }
        }
        // end of packetentities
        MSG.WriteShort(buffer, 0);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        while (true) {
            DeltaEntityHeader header = ServerMessage.parseDeltaEntityHeader(buffer);
            // end of packetentities
            if (0 == header.number)
                break;

            if (header.number >= Defines.MAX_EDICTS)
                Com.Error(Defines.ERR_DROP, "CL_ParsePacketEntities: bad number:" + header.number);

            if (buffer.readcount > buffer.cursize)
                Com.Error(Defines.ERR_DROP, "CL_ParsePacketEntities: end of message");


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
            // entity is changed
            return MSG.getDeltaSize(value.oldState, value.newState, value.isNewEntity);
        } else {
            // entity is removed
            int result = 1;
            if ((value.header.flags & 0x0000ff00) != 0)
                result += 1;

            if ((value.header.flags & Defines.U_NUMBER16) != 0)
                result += 2;
            else
                result += 1;

            return result;
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

