package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

/**
 * Print a message in the center of the screen, like when a door is closed.
 * Sent to a particular client only
 */
public class PrintCenterMessage extends NetworkMessage {
    public PrintCenterMessage(String text) {
        super(NetworkCommandType.svc_centerprint);
        this.text = text;
    }

    public final String text;

    @Override
    protected void sendProps(sizebuf_t buffer) {
        MSG.WriteString(buffer, text);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
