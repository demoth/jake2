package jake2.qcommon.network.messages.server;

import jake2.qcommon.*;

/**
 * Contains baseline for a single entity,
 * sent to a new client
 * when the client is just connected to the server.
 */
public class SpawnBaselineMessage extends ServerMessage {

    public entity_state_t entityState;

    public SpawnBaselineMessage() {
        super(ServerMessageType.svc_spawnbaseline);
    }

    public SpawnBaselineMessage(entity_state_t entityState) {
        this();
        this.entityState = entityState;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteDeltaEntity(new entity_state_t(null), entityState, buffer, true, true);
    }

    @Override
    void parse(sizebuf_t buffer) {
        // todo: extract to function returning bits & number
        // up until ParseDelta
        int bits = MSG.ReadByte(buffer);
        if ((bits & Defines.U_MOREBITS1) != 0) {
            int b = MSG.ReadByte(buffer);
            bits |= b << 8;
        }
        if ((bits & Defines.U_MOREBITS2) != 0) {
            int b = MSG.ReadByte(buffer);
            bits |= b << 16;
        }
        if ((bits & Defines.U_MOREBITS3) != 0) {
            int b = MSG.ReadByte(buffer);
            bits |= b << 24;
        }

        int number;
        if ((bits & Defines.U_NUMBER16) != 0)
            number = MSG.ReadShort(buffer);
        else
            number = MSG.ReadByte(buffer);

        entityState = new entity_state_t(new edict_t(number));

        ParseDelta(new entity_state_t(null), entityState, number, bits, buffer);
    }
}
