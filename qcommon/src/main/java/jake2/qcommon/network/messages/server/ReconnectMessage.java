package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

// fixme: same as "reconnect" StuffTextMessage
public class ReconnectMessage extends ServerMessage {
    public ReconnectMessage() {
        super(ServerMessageType.svc_reconnect);
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
        return "ReconnectMessage";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        else
            return this.getClass() == obj.getClass();
    }

}
