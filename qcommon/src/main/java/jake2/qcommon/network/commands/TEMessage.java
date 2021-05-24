package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

/**
 * Temp entity
 */
public abstract class TEMessage extends NetworkMessage {
    public TEMessage(int style) {
        super(NetworkCommandType.svc_temp_entity);
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
}
