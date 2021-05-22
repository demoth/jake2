package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class DisconnectMessage extends NetworkMessage {
    public DisconnectMessage() {
        super(NetworkCommandType.svc_disconnect);
    }

    @Override
    public void send(sizebuf_t buffer) {
        MSG.WriteByte(buffer, type);
    }

    @Override
    void parse(sizebuf_t buffer) {
        // no other fields
    }
}
