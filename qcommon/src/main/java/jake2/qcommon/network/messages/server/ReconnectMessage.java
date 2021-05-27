package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

public class ReconnectMessage extends ServerMessage {
    public ReconnectMessage() {
        super(ServerMessageType.svc_reconnect);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
