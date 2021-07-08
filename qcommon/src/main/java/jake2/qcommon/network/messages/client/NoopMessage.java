package jake2.qcommon.network.messages.client;

import jake2.qcommon.sizebuf_t;

public class NoopMessage extends ClientMessage {
    public NoopMessage() {
        super(ClientMessageType.CLC_NOP);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    public void parse(sizebuf_t buffer) {

    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public String toString() {
        return "NoopMessage{}";
    }
}
