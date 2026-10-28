package jake2.qcommon.network.messages.server;

import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.sizebuf_t;

/**
 * Common parent for all messages sent from server to client.
 */
public abstract class ServerMessage implements NetworkMessage {
    public final ServerMessageType type;

    public ServerMessage(ServerMessageType type) {
        this.type = type;
    }

    public final void writeTo(sizebuf_t buffer) {
        ServerMessageCodec.write(this, buffer);
    }

    /** Size of the message in bytes, including its opcode. */
    public int getSize() {
        return ServerMessageCodec.size(this);
    }

    public static ServerMessage parseFromBuffer(sizebuf_t buffer) {
        return ServerMessageCodec.read(buffer);
    }

}
