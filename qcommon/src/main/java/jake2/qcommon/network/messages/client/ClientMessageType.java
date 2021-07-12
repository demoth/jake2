package jake2.qcommon.network.messages.client;

public enum ClientMessageType {
    CLC_BAD((byte) 0),
    CLC_NOP((byte) 1),
    CLC_MOVE((byte) 2), // [[usercmd_t]
    // +
    CLC_USERINFO((byte) 3), // [[userinfo string]
    // +
    CLC_STRINGCMD((byte) 4); // [string] message

    public final byte value;

    ClientMessageType(byte value) {
        this.value = value;
    }

    public static ClientMessageType fromByte(byte value) {
        for (ClientMessageType cmd : values()) {
            if (cmd.value == value)
                return cmd;
        }
        return CLC_BAD;
    }
}
