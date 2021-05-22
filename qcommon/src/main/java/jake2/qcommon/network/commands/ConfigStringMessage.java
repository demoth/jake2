package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class ConfigStringMessage extends NetworkMessage {
    public ConfigStringMessage(int index, String config) {
        super(NetworkCommandType.svc_configstring);
        this.index = index;
        this.config = config;
    }

    public final int index;
    public final String config;

    @Override
    protected void sendProps(sizebuf_t buffer) {
        MSG.WriteShort(buffer, index);
        MSG.WriteString(buffer, config);

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
