package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

/**
 * Temp entity
 */
public abstract class TEMessage extends ServerMessage {
    public TEMessage(int style) {
        super(ServerMessageType.svc_temp_entity);
        this.style = style;
    }

    public final int style;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteByte(buffer, style);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }

    @Override
    int getSize() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String toString() {
        return "TEMessage{" +
                "style=" + style +
                '}';
    }
}
