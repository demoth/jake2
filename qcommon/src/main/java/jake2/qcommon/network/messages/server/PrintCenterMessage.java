package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

/**
 * Print a message in the center of the screen, like when a door is closed.
 * Sent to a particular client only
 */
public class PrintCenterMessage extends ServerMessage {
    public String text;

    public PrintCenterMessage() {
        super(ServerMessageType.svc_centerprint);
    }

    public PrintCenterMessage(String text) {
        this();
        this.text = text;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteString(buffer, text);
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.text = MSG.ReadString(buffer);
    }

    @Override
    int getSize() {
        return 1 + text.length() + 1;
    }

    @Override
    public String toString() {
        return "PrintCenterMessage{" + text + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PrintCenterMessage that = (PrintCenterMessage) o;

        return text != null ? text.equals(that.text) : that.text == null;
    }

    @Override
    public int hashCode() {
        return text != null ? text.hashCode() : 0;
    }
}
