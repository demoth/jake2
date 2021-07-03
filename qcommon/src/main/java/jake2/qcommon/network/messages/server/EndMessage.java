package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

public class EndMessage extends ServerMessage {
    public EndMessage() {
        super(ServerMessageType.svc_end);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    void parse(sizebuf_t buffer) {

    }

    @Override
    int getSize() {
        return 1;
    }
}
