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
    private final entity_state_t base = new entity_state_t(null);;

    public SpawnBaselineMessage() {
        super(ServerMessageType.svc_spawnbaseline);
    }

    public SpawnBaselineMessage(entity_state_t entityState) {
        this();
        this.entityState = entityState;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteDeltaEntity(base, entityState, buffer, true, true);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        DeltaEntityHeader header = parseDeltaEntityHeader(buffer);
        entityState = parseEntityState(header.number, header.flags, buffer);
    }

    @Override
    public int getSize() {
        return 1 + MSG.getDeltaSize(base, entityState, true);
    }

    @Override
    public String toString() {
        return "SpawnBaselineMessage{" +
                "entityState=" + entityState +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SpawnBaselineMessage))
            return false;

        SpawnBaselineMessage that = (SpawnBaselineMessage) o;

        return entityState != null ? entityState.equals(that.entityState) : that.entityState == null;
    }

    @Override
    public int hashCode() {
        return entityState != null ? entityState.hashCode() : 0;
    }
}
