package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class WeaponSoundMessage extends NetworkMessage {

    /**
     * @param type - weapon index and silenced flag packed together into 1 byte
     * @param entityIndex - index of the owner
     */
    public WeaponSoundMessage(int entityIndex, int type) {
        super(NetworkCommandType.svc_muzzleflash);
        this.entityIndex = entityIndex;
        this.type = type;
    }

    public final int entityIndex;
    public final int type;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteShort(buffer, entityIndex);
        MSG.WriteByte(buffer, type);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
