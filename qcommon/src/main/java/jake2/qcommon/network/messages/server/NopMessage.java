package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

// fixme: why do we need it?
public class NopMessage extends ServerMessage {
    public NopMessage() {
        super(ServerMessageType.svc_nop);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
