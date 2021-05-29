package jake2.qcommon.network.messages.client;

import jake2.qcommon.sizebuf_t;

public class NoopMessage extends ClientMessage {
    protected NoopMessage() {
        super(ClientMessageType.CLC_NOP);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
