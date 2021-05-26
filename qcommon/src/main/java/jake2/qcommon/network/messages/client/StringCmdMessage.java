package jake2.qcommon.network.messages.client;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public class StringCmdMessage extends ClientMessage {

    public String command;

    public StringCmdMessage() {
        super(ClientMessageType.CLC_STRINGCMD);
    }

    public StringCmdMessage(String command) {
        this();
        this.command = command;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteString(buffer, command);
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.command = MSG.ReadString(buffer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringCmdMessage that = (StringCmdMessage) o;
        return command.equals(that.command);
    }
}
