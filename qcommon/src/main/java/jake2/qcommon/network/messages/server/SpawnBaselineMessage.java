package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.entity_state_t;
import jake2.qcommon.sizebuf_t;

/**
 * Contains baseline for a single entity,
 * sent to a new client when the client is just connected to the server.
 *
 * Baseline is a snapshot of an entity state at the moment of level instantiation.
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
        DeltaEntityHeader header = parseDeltaEntityHeader(buffer);
        entityState = parseEntityState(header.number, header.flags, buffer);
    }

    @Override
    int getSize() {
        return -1;
    }
}
