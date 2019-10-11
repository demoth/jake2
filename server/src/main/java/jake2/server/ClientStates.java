package jake2.server;

public enum ClientStates {
    // can be reused for a new connection
    CS_FREE, // 0
    // client has been disconnected, but don't reuse
    // connection for a couple seconds
    CS_ZOMBIE, // 1
    // has been assigned to a client_t, but not in game yet
    CS_CONNECTED, //2
    CS_SPAWNED //3
}
