package jake2.qcommon.network.messages.client;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

/**
 * Represents a user command sent from client to server, either manual or automatic.
 * Some commands are built-in (the constants below).
 * Other commands are redirected to the game to handle (like, 'god' or 'say')
 */
public class StringCmdMessage extends ClientMessage {

    public static final String NEW = "new";
    public static final String CONFIG_STRINGS = "configstrings";
    public static final String BASELINES = "baselines";
    public static final String BEGIN = "begin";
    public static final String NEXT_SERVER = "nextserver";
    public static final String DISCONNECT = "disconnect";
    public static final String INFO = "info";
    public static final String DOWNLOAD = "download";
    public static final String NEXT_DOWNLOAD = "nextdl";

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
