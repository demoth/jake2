package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class LayoutMessage extends NetworkMessage {
    public String layout;

    public LayoutMessage() {
        super(NetworkCommandType.svc_layout);
    }

    public LayoutMessage(String layout) {
        this();
        this.layout = layout;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteString(buffer, layout);
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.layout = MSG.ReadString(buffer);
    }
}
