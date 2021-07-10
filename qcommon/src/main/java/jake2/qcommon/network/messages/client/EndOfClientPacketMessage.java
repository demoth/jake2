package jake2.qcommon.network.messages.client;

import jake2.qcommon.sizebuf_t;

public class EndOfClientPacketMessage extends ClientMessage {
    public EndOfClientPacketMessage() {
        super(ClientMessageType.CLC_BAD);
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
        return "EndOfClientPacketMessage{}";
    }
}
