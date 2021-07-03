package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

/**
 * Print message to console or to the top of the screen
 */
public class PrintMessage extends ServerMessage {

    public int level;
    public String text;

    public PrintMessage() {
        super(ServerMessageType.svc_print);
    }

    public PrintMessage(int level, String text) {
        this();
        this.level = level;
        this.text = text;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteByte(buffer, level);
        MSG.WriteString(buffer, text);

    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.level = MSG.ReadByte(buffer);
        this.text = MSG.ReadString(buffer);
    }

    @Override
    int getSize() {
        return 2 + text.length() + 1;
    }

    @Override
    public String toString() {
        return "PrintMessage{" + level + "=" + text + '}';
    }
}
