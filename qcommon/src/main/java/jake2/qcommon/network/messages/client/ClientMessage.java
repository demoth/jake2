package jake2.qcommon.network.messages.client;

import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.sizebuf_t;
import jake2.qcommon.network.protocol.MessageReadException;
import jake2.qcommon.network.protocol.MessageReader;
import jake2.qcommon.network.protocol.MessageRejection;

public abstract class ClientMessage implements NetworkMessage {
    public final ClientMessageType type;

    protected ClientMessage(ClientMessageType type) {
        this.type = type;
    }

    public final void writeTo(sizebuf_t buffer) {
        buffer.writeByte(type.value);
        writeProperties(buffer);
    }

    protected abstract void writeProperties(sizebuf_t buffer);

    public static ClientMessage parseFromBuffer(sizebuf_t buffer, int incomingSequence) {
        if (!(buffer instanceof MessageReader)) {
            MessageReader reader = MessageReader.fromBuffer(buffer);
            try { return parseFromBuffer(reader, incomingSequence); } finally { buffer.readcount = reader.readcount; }
        }

        if (buffer.readcount == buffer.cursize) return new EndOfClientPacketMessage();
        int opcode = buffer.readByte();
        ClientMessageType type = ClientMessageType.fromByte((byte) opcode);
        if (type == null) throw new MessageReadException(MessageRejection.UNKNOWN_OPCODE, buffer.readcount - 1, "Unknown client opcode " + opcode);
        final ClientMessage msg;
        switch (type) {
            case CLC_BAD:
                msg = new EndOfClientPacketMessage();
                break;
            case CLC_NOP:
                // fixme: never sent by client directly
                msg = new NoopMessage();
                break;
            case CLC_USERINFO:
                msg = new UserInfoMessage();
                break;
            case CLC_STRINGCMD:
                msg = new StringCmdMessage();
                break;
            case CLC_MOVE:
                msg = new MoveMessage(incomingSequence);
                break;
            default:
                throw new MessageReadException(MessageRejection.UNKNOWN_OPCODE, buffer.readcount - 1, "Unsupported client opcode " + opcode);
        }
        if (msg != null) {
            msg.parse(buffer);
        }
        return msg;
    }

}
