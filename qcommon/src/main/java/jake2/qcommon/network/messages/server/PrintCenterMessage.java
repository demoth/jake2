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
    public String toString() {
        return "PrintCenterMessage{" + text + '}';
    }

}
