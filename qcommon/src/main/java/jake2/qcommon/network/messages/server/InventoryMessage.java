package jake2.qcommon.network.messages.server;

import jake2.qcommon.Defines;
import jake2.qcommon.sizebuf_t;

import java.util.Arrays;

/**
 * Inventory sent to client.
 * Holds information about how many specific items player holds.
 * The names of the items are sent via {@link ConfigStringMessage} starting from {@link Defines#CS_ITEMS} (Max 256).
 */
public class InventoryMessage extends ServerMessage {
    public int[] inventory;

    public InventoryMessage() {
        super(ServerMessageType.svc_inventory);
    }

    public InventoryMessage(int[] inventory) {
        this();
        this.inventory = inventory;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        for (int i = 0; i < Defines.MAX_ITEMS; i++) {
            buffer.WriteShort(inventory[i]);
        }

    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.inventory = new int[Defines.MAX_ITEMS];
        for (int i = 0; i < Defines.MAX_ITEMS; i++) {
            this.inventory[i] = sizebuf_t.ReadShort(buffer);
        }
    }

    @Override
    public int getSize() {
        return 1 + 2 * Defines.MAX_ITEMS;
    }

    @Override
    public String toString() {
        return "InventoryMessage{" +
                "inventory=" + Arrays.toString(inventory) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InventoryMessage that = (InventoryMessage) o;

        return Arrays.equals(inventory, that.inventory);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(inventory);
    }
}
