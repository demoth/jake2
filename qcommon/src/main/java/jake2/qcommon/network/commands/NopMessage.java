package jake2.qcommon.network.commands;

import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

// fixme: why do we need it?
public class NopMessage extends NetworkMessage {
    public NopMessage() {
        super(NetworkCommandType.svc_nop);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
