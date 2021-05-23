package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

/**
 * Print message to console or to the top of the screen
 */
public class PrintMessage extends NetworkMessage {

    public int level;
    public String text;

    public PrintMessage() {
        super(NetworkCommandType.svc_print);
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
    public String toString() {
        return "PrintMessage{" + level + "=" + text + '}';
    }
}
