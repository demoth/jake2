package jake2.qcommon.network.messages;

public enum ClientConnectionlessCommand {
    connect,
    info,
    ping,
    ack,
    getchallenge,
    status,
    rcon,
    unknown;

    public static ClientConnectionlessCommand fromString(String cmd) {
        try {
            return valueOf(cmd);
        } catch (IllegalArgumentException e) {
            return unknown;
        }
    }
}
