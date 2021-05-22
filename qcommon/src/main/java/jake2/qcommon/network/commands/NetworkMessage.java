package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public abstract class NetworkMessage {
    // todo: change to enum
    public int type;

    public NetworkMessage(int type) {
        this.type = type;
    }

    public final void send(sizebuf_t buffer) {
        MSG.WriteByte(buffer, type);
        sendProps(buffer);
    }

    protected abstract void sendProps(sizebuf_t buffer);

    abstract void parse(sizebuf_t buffer);
}
