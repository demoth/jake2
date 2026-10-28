package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

/**
 * Transitional mutable message parser while server messages move to Kotlin values/codecs.
 * @deprecated Migrate subclasses to immutable values decoded by ServerMessageCodec.
 */
@Deprecated(forRemoval = true)
public abstract class LegacyServerMessage extends ServerMessage {
    protected LegacyServerMessage(ServerMessageType type) {
        super(type);
    }

    public abstract void parse(sizebuf_t buffer);

    @Override
    public abstract int getSize();

    protected abstract void writeProperties(sizebuf_t buffer);

    final void writeLegacyProperties(sizebuf_t buffer) {
        writeProperties(buffer);
    }
}
