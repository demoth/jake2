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
    public void parse(sizebuf_t buffer) {
        // no other fields
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public String toString() {
        return "DisconnectMessage";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        else
            return this.getClass() == obj.getClass();
    }
}
