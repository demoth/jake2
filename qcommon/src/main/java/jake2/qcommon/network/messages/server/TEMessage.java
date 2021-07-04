package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

import java.util.Collection;

/**
 * Temp entity
 */
public abstract class TEMessage extends ServerMessage {
    public TEMessage(int style) {
        super(ServerMessageType.svc_temp_entity);
        this.style = style;
    }

    public int style;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteByte(buffer, style);
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.style = MSG.ReadByte(buffer);
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

    protected void validateStyle(int style, Collection<Integer> supported) {
        if (!supported.contains(style)) {
            throw new IllegalArgumentException("Wrong style for temp entity: " + style + " for class: " + this.getClass().getSimpleName());
        }
    }
}
