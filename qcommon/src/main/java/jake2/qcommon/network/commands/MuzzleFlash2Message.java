package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class MuzzleFlash2Message extends NetworkMessage {
    public MuzzleFlash2Message(int entityIndex, int flashType) {
        super(NetworkCommandType.svc_muzzleflash2);
        this.entityIndex = entityIndex;
        this.flashType = flashType;
    }

    public final int entityIndex;
    public final int flashType;


    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteShort(buffer, entityIndex);
        MSG.WriteByte(buffer, flashType);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
