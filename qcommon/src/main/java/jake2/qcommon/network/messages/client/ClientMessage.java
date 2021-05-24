package jake2.qcommon.network.messages.client;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public abstract class ClientMessage {
    public final ClientMessageType type;

    protected ClientMessage(ClientMessageType type) {
        this.type = type;
    }

    public final void writeTo(sizebuf_t buffer) {
        MSG.WriteByte(buffer, type.value);
        writeProperties(buffer);
    }

    protected abstract void writeProperties(sizebuf_t buffer);

    abstract void parse(sizebuf_t buffer);

    public static ClientMessage parseFromBuffer(ClientMessageType type, sizebuf_t buffer) {
        final ClientMessage msg;
        switch (type) {
            case CLC_BAD:
            case CLC_NOP:
            case CLC_MOVE:
            default:
                msg = null;
                break;
            case CLC_USERINFO:
                msg = new UserInfoMessage();
                break;
            case CLC_STRINGCMD:
                msg = new StringCmdMessage();
                break;
        }
        if (msg != null) {
            msg.parse(buffer);
        }
        return msg;
    }

}
