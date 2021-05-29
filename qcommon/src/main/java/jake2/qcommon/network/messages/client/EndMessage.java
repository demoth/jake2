package jake2.qcommon.network.messages.client;

import jake2.qcommon.sizebuf_t;

public class EndMessage extends ClientMessage {
    protected EndMessage() {
        super(ClientMessageType.CLC_BAD);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
