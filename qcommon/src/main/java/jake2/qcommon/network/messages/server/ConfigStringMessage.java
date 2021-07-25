package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

import static jake2.qcommon.Defines.*;

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
        buffer.writeShort(index);
        buffer.writeString(config);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.index = buffer.readShort();
        this.config = buffer.readString();
    }

    @Override
    public int getSize() {
        return 1 + 2 + config.length() + 1;
    }

    @Override
    public String toString() {
        return "ConfigStringMessage{" + getType(index) + "(" + index + ")" + "=" + config + '}';
    }

    public String getType(int index) {
        if (index == CS_NAME) {
            return "CS_NAME";
        } else if (index == CS_CDTRACK) {
            return "CS_CDTRACK";
        } else if (index == CS_SKY) {
            return "CS_SKY";
        } else if (index == CS_SKYAXIS) {
            return "CS_SKYAXIS";
        } else if (index == CS_SKYROTATE) {
            return "CS_SKYROTATE";
        } else if (index == CS_STATUSBAR) {
            return "CS_STATUSBAR";
            // fixme: what is between CS_STATUSBAR(5) and CS_AIRACCEL(29)?
        } else if (index == CS_AIRACCEL) {
            return "CS_AIRACCEL";
        } else if (index == CS_MAXCLIENTS) {
            return "CS_MAXCLIENTS";
        } else if (index == CS_MAPCHECKSUM) {
            return "CS_MAPCHECKSUM";
        } else if (index < CS_SOUNDS) {
            return "CS_MODELS";
        } else if (index < CS_IMAGES) {
            return "CS_SOUNDS";
        } else if (index < CS_LIGHTS) {
            return "CS_IMAGES";
        } else if (index < CS_ITEMS) {
            return "CS_LIGHTS";
        } else if (index < CS_PLAYERSKINS) {
            return "CS_ITEMS";
        } else if (index < CS_GENERAL) {
            return "CS_PLAYERSKINS";
        } else if (index < MAX_CONFIGSTRINGS) {
            return "CS_GENERAL";
        } else {
            // todo: validate
            return "UNKNOWN";
        }
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
