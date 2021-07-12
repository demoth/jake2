package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

/**
 * Sent to client stuffed into client's console buffer, \n terminated
 */
public class StuffTextMessage extends ServerMessage {
    public String text;

    public StuffTextMessage() {
        super(ServerMessageType.svc_stufftext);
    }

    public StuffTextMessage(String text) {
        this();
        this.text = text + "\n";
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        buffer.writeString(text);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.text = buffer.readString();
    }

    @Override
    public int getSize() {
        return 1 + text.length() + 1;
    }

    @Override
    public String toString() {
        return "StuffTextMessage{" + text + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StuffTextMessage that = (StuffTextMessage) o;

        return text != null ? text.equals(that.text) : that.text == null;
    }

    @Override
    public int hashCode() {
        return text != null ? text.hashCode() : 0;
    }
}

