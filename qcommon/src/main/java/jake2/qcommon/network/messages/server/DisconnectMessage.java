package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

public class DisconnectMessage extends ServerMessage {
    public DisconnectMessage() {
        super(ServerMessageType.svc_disconnect);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    void parse(sizebuf_t buffer) {
        // no other fields
    }

    @Override
    int getSize() {
        return 1;
    }
}
