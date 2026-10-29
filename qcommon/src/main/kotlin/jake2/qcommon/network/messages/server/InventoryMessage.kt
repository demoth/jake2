package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.MAX_ITEMS
import jake2.qcommon.Defines.CS_ITEMS

/**
 * Inventory sent to client. Holds information about how many specific items a player holds.
 * The names of the items are sent via [ConfigStringMessage] starting from [CS_ITEMS] (max 256).
 * Owns the first [MAX_ITEMS] counts, represented as signed 16-bit wire values.
 */
class InventoryMessage(inventory: IntArray) : ServerMessage(ServerMessageType.svc_inventory) {
    init { require(inventory.size >= MAX_ITEMS) { "Inventory must contain $MAX_ITEMS counts" } }
    private val counts = IntArray(MAX_ITEMS) { inventory[it].toShort().toInt() }

    operator fun get(index: Int): Int = counts[index]
    fun copyTo(destination: IntArray) { counts.copyInto(destination) }

    override fun equals(other: Any?): Boolean = other is InventoryMessage && counts.contentEquals(other.counts)
    override fun hashCode(): Int = counts.contentHashCode()
    override fun toString(): String = "InventoryMessage{inventory=${counts.contentToString()}}"
}
