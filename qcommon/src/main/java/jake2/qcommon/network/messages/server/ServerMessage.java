package jake2.qcommon.network.messages.server;

import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.sizebuf_t;
import jake2.qcommon.network.protocol.MessageReadException;
import jake2.qcommon.network.protocol.MessageRejection;

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

    /** Decode the remaining mutable message families until each migrates to a Kotlin value/codec. */
    static LegacyServerMessage readLegacyProperties(ServerMessageType type, sizebuf_t buffer) {
        final LegacyServerMessage msg;
        switch (type) {
            default:
                throw new MessageReadException(MessageRejection.UNKNOWN_OPCODE, buffer.readcount - 1, "Invalid server opcode " + type);
            case svc_playerinfo:
                msg = new PlayerInfoMessage();
                break;

        }

        if (msg != null) {
            msg.parse(buffer);
        }
        return msg;
    }
}
