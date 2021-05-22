package jake2.qcommon.network.commands;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class InventoryMessage extends NetworkMessage {
    public InventoryMessage(int[] inventory) {
        super(NetworkCommandType.svc_inventory);
        this.inventory = inventory;
    }

    final int[] inventory;

    @Override
    protected void sendProps(sizebuf_t buffer) {
        for (int i = 0; i < Defines.MAX_ITEMS; i++) {
            MSG.WriteShort(buffer, inventory[i]);
        }

    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
