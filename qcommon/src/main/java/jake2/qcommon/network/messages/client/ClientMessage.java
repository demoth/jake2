package jake2.qcommon.network.messages.client;

import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.sizebuf_t;

/** Client message identity with wire operations delegated to the separate codec. */
public abstract class ClientMessage implements NetworkMessage {
    public final ClientMessageType type;

    protected ClientMessage(ClientMessageType type) {
        this.type = type;
    }

    public final void writeTo(sizebuf_t buffer) {
        ClientMessageCodec.write(this, buffer);
    }

    /** Size of the message in bytes. */
    public final int getSize() {
        return ClientMessageCodec.size(this);
    }

    public static ClientMessage parseFromBuffer(sizebuf_t buffer, int incomingSequence) {
        return ClientMessageCodec.read(buffer, incomingSequence);
    }

}
