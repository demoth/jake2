package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class PrintMessage extends NetworkMessage {
    public PrintMessage(int level, String text) {
        super(NetworkCommandType.svc_print);
        this.level = level;
        this.text = text;
    }

    public final int level;
    public final String text;

    @Override
    protected void sendProps(sizebuf_t buffer) {
        MSG.WriteByte(buffer, level);
        MSG.WriteString(buffer, text);

    }

    @Override
    public void parse(sizebuf_t buffer) {
        //todo
    }
}
