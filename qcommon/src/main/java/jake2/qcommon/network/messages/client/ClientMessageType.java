package jake2.qcommon.network.messages.client;

public enum ClientMessageType {
    CLC_BAD(0),
    CLC_NOP(1),
    CLC_MOVE(2), // [[usercmd_t]
    // +
    CLC_USERINFO(3), // [[userinfo string]
    // +
    CLC_STRINGCMD(4); // [string] message

    public final int value;

    ClientMessageType(int value) {
        this.value = value;
    }

    public static ClientMessageType fromInt(int value) {
        for (ClientMessageType cmd : values()) {
            if (cmd.value == value)
                return cmd;
        }
        return CLC_BAD;
    }
}
