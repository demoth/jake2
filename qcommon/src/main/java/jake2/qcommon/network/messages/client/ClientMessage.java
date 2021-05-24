package jake2.qcommon.network.messages.client;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public abstract class ClientMessage {
    public final ClientMessageType type;

    protected ClientMessage(ClientMessageType type) {
        this.type = type;
    }

    public final void writeTo(sizebuf_t buffer) {
        MSG.WriteByte(buffer, type.value);
        writeProperties(buffer);
    }

    protected abstract void writeProperties(sizebuf_t buffer);

    abstract void parse(sizebuf_t buffer);

}
