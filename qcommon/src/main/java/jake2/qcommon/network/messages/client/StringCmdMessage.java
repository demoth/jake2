package jake2.qcommon.network.messages.client;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public class StringCmdMessage extends ClientMessage {

    public String command;

    public StringCmdMessage(String command) {
        super(ClientMessageType.CLC_STRINGCMD);
        this.command = command;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteString(buffer, command);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
