package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public class MuzzleFlash2Message extends ServerMessage {
    public int entityIndex;
    public int flashType;

    public MuzzleFlash2Message() {
        super(ServerMessageType.svc_muzzleflash2);
    }

    public MuzzleFlash2Message(int entityIndex, int flashType) {
        this();
        this.entityIndex = entityIndex;
        this.flashType = flashType;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteShort(buffer, entityIndex);
        MSG.WriteByte(buffer, flashType);
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.entityIndex = MSG.ReadShort(buffer);
        this.flashType = MSG.ReadByte(buffer);
    }

    @Override
    int getSize() {
        return 4;
    }

    @Override
    public String toString() {
        return "MuzzleFlash2Message{" +
                "entityIndex=" + entityIndex +
                ", flashType=" + flashType +
                '}';
    }
}
