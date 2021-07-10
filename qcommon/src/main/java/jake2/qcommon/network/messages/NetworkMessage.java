package jake2.qcommon.network.messages;

import jake2.qcommon.sizebuf_t;

public interface NetworkMessage {

    void writeTo(sizebuf_t buffer);

    void parse(sizebuf_t buffer);

    /**
     * size of the message in bytes
     */
    int getSize();
}
