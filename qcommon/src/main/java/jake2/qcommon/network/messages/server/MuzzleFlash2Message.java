package jake2.qcommon.network.messages.server;

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
        buffer.writeShort(entityIndex);
        buffer.writeByte((byte) flashType);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.entityIndex = buffer.readShort();
        this.flashType = buffer.readByte();
    }

    @Override
    public int getSize() {
        return 4;
    }

    @Override
    public String toString() {
        return "MuzzleFlash2Message{" +
                "entityIndex=" + entityIndex +
                ", flashType=" + flashType +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MuzzleFlash2Message that = (MuzzleFlash2Message) o;

        if (entityIndex != that.entityIndex) return false;
        return flashType == that.flashType;
    }

    @Override
    public int hashCode() {
        int result = entityIndex;
        result = 31 * result + flashType;
        return result;
    }
}
