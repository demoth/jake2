package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

// fixme: why do we need it?
public class NopMessage extends ServerMessage {
    public NopMessage() {
        super(ServerMessageType.svc_nop);
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
        return "Nop";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        else
            return this.getClass() == obj.getClass();
    }

}
