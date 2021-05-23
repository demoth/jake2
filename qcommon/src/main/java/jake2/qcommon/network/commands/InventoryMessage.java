package jake2.qcommon.network.commands;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

public class InventoryMessage extends NetworkMessage {
    public int[] inventory;

    public InventoryMessage() {
        super(NetworkCommandType.svc_inventory);
    }

    public InventoryMessage(int[] inventory) {
        this();
        this.inventory = inventory;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        for (int i = 0; i < Defines.MAX_ITEMS; i++) {
            MSG.WriteShort(buffer, inventory[i]);
        }

    }

    @Override
    void parse(sizebuf_t buffer) {
        this.inventory = new int[Defines.MAX_ITEMS];
        for (int i = 0; i < Defines.MAX_ITEMS; i++) {
            this.inventory[i] = MSG.ReadShort(buffer);
        }
    }
}
