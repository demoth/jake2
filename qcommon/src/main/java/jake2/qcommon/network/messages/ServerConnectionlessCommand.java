package jake2.qcommon.network.messages;

public enum ServerConnectionlessCommand {
    client_connect,
    info,
    ping,
    challenge,
    @Deprecated cmd,
    print,
    @Deprecated echo,
    unknown;

    public static ServerConnectionlessCommand fromString(String cmd) {
        try {
            return valueOf(cmd);
        } catch (IllegalArgumentException e) {
            return unknown;
        }
    }
}
