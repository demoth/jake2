package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;
/*
 [string] stuffed into client's console buffer, should be \n terminated
 todo: append newline automatically
 */
public class StuffTextMessage extends NetworkMessage {
    public StuffTextMessage(String text) {
        super(NetworkCommandType.svc_stufftext);
        this.text = text;
    }

    final String text;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteString(buffer, text);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
