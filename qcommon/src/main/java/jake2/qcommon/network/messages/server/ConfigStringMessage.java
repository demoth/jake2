package jake2.qcommon.network.messages.server;

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
        buffer.WriteShort(index);
        sizebuf_t.WriteString(buffer, config);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.index = sizebuf_t.ReadShort(buffer);
        this.config = sizebuf_t.ReadString(buffer);
    }

    @Override
    public int getSize() {
        return 1 + 2 + config.length() + 1;
    }

    @Override
    public String toString() {
        return "ConfigStringMessage{" + index + "=" + config + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConfigStringMessage that = (ConfigStringMessage) o;

        if (index != that.index) return false;
        return config != null ? config.equals(that.config) : that.config == null;
    }

    @Override
    public int hashCode() {
        int result = index;
        result = 31 * result + (config != null ? config.hashCode() : 0);
        return result;
    }
}
