package jake2.qcommon.network.messages.client;

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
    public static final String PRECACHE = "precache";

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
        buffer.writeString(command);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.command = buffer.readString();
    }

    @Override
    public int getSize() {
        return 1 + command.length() + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringCmdMessage that = (StringCmdMessage) o;
        return command.equals(that.command);
    }

    @Override
    public int hashCode() {
        return command != null ? command.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "StringCmdMessage{" +
                "command='" + command + '\'' +
                '}';
    }
}
