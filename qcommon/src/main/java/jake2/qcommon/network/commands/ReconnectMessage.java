package jake2.qcommon.network.commands;

import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class ReconnectMessage extends NetworkMessage {
    public ReconnectMessage() {
        super(NetworkCommandType.svc_reconnect);
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
