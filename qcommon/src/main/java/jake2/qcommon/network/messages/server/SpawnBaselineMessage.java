package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.entity_state_t;
import jake2.qcommon.sizebuf_t;

public class SpawnBaselineMessage extends ServerMessage {

    public entity_state_t entityState;

    public SpawnBaselineMessage(entity_state_t entityState) {
        super(ServerMessageType.svc_spawnbaseline);
        this.entityState = entityState;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteDeltaEntity(new entity_state_t(null), entityState, buffer, true, true);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
