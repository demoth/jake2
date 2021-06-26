package jake2.qcommon.network.messages;

/**
 * Also known as Out of band.
 * Both server & client.
 */
public enum ConnectionlessCommand {
    connect,
    info,
    ping,
    ack,
    getchallenge,
    status,
    rcon,
    client_connect,
    challenge,
    print,
    unknown;

    public static ConnectionlessCommand fromString(String cmd) {
        try {
            return valueOf(cmd);
        } catch (IllegalArgumentException e) {
            return unknown;
        }
    }
}
