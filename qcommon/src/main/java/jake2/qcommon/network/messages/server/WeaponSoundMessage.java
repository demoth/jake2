package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public class WeaponSoundMessage extends ServerMessage {

    /**
     * @param type - weapon index and silenced flag packed together into 1 byte
     * @param entityIndex - index of the owner
     */
    public WeaponSoundMessage(int entityIndex, int type) {
        this();
        this.entityIndex = entityIndex;
        this.type = type;
    }

    public WeaponSoundMessage() {
        super(ServerMessageType.svc_muzzleflash);
    }

    public int entityIndex;
    public int type;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteShort(buffer, entityIndex);
        MSG.WriteByte(buffer, type);
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.entityIndex = MSG.ReadShort(buffer);
        this.type = MSG.ReadByte(buffer);
    }

    @Override
    int getSize() {
        return 4;
    }
}
