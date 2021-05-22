package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class LayoutMessage extends NetworkMessage {
    public LayoutMessage(String layout) {
        super(NetworkCommandType.svc_layout);
        this.layout = layout;
    }

    public final String layout;

    @Override
    protected void sendProps(sizebuf_t buffer) {
        MSG.WriteString(buffer, layout);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
