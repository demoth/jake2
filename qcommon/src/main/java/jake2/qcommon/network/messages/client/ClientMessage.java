package jake2.qcommon.network.messages.client;

import jake2.qcommon.MSG;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.sizebuf_t;

public abstract class ClientMessage implements NetworkMessage {
    public final ClientMessageType type;

    protected ClientMessage(ClientMessageType type) {
        this.type = type;
    }

    public final void writeTo(sizebuf_t buffer) {
        MSG.WriteByte(buffer, type.value);
        writeProperties(buffer);
    }

    protected abstract void writeProperties(sizebuf_t buffer);

    public static ClientMessage parseFromBuffer(sizebuf_t buffer, int incomingSequence) {
        ClientMessageType type = ClientMessageType.fromByte((byte) MSG.ReadByte(buffer));
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
                // todo: notify somehow in case of unexpected type
                msg = null;
        }
        if (msg != null) {
            msg.parse(buffer);
        }
        return msg;
    }

}
