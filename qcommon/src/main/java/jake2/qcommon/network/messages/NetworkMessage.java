package jake2.qcommon.network.messages;

import jake2.qcommon.sizebuf_t;

public interface NetworkMessage {

    void writeTo(sizebuf_t buffer);

    /** Encode using the packet's reserved sequence; sequence-independent messages use their ordinary codec. */
    default void writeTo(sizebuf_t buffer, int sequence) {
        writeTo(buffer);
    }

    /**
     * size of the message in bytes
     */
    int getSize();
}
