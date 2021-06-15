package jake2.server;

public enum ClientStates {
    /*
     * No client is connected. This slot can be reused for a new connection.
     */
    CS_FREE,

    /*
     * Client in this slot has been disconnected.
     * Don't reuse the slot for a couple seconds, maybe he will reconnect.
     */
    CS_ZOMBIE,

    /*
     * Client is connected (new) and been assigned to this slot (client_t).
     * Client is receiving "baselines" (command), but not in game yet.
     */
    CS_CONNECTED,

    /*
     * Client has received all the entities' baselines and ready to receive game updates. "begin" command
     */
    CS_SPAWNED
}
