package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;
/*
 [string] stuffed into client's console buffer, should be \n terminated
 todo: append newline automatically
 */
public class StuffTextMessage extends ServerMessage {
    public String text;

    public StuffTextMessage() {
        super(ServerMessageType.svc_stufftext);
    }

    public StuffTextMessage(String text) {
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
        return "StuffTextMessage{" + text + '}';
    }
}

