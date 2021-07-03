package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public class ConfigStringMessage extends ServerMessage {
    public int index;
    public String config;

    public ConfigStringMessage() {
        super(ServerMessageType.svc_configstring);
    }

    public ConfigStringMessage(int index, String config) {
        this();
        this.index = index;
        this.config = config;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteShort(buffer, index);
        MSG.WriteString(buffer, config);
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.index = MSG.ReadShort(buffer);
        this.config = MSG.ReadString(buffer);
    }

    @Override
    int getSize() {
        return 1 + 2 + config.length() + 1;
    }

    @Override
    public String toString() {
        return "ConfigStringMessage{" + index + "=" + config + '}';
    }
}
