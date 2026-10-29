package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*
import jake2.qcommon.entity_state_t
import java.util.Collections

/** Contains state changes for multiple entities, in server frame order. Owns its update list. */
class PacketEntitiesMessage(updates: List<EntityUpdate>) : ServerMessage(ServerMessageType.svc_packetentities) {
    val updates: List<EntityUpdate> = Collections.unmodifiableList(ArrayList(updates))
    override fun equals(other: Any?) = other is PacketEntitiesMessage && updates == other.updates
    override fun hashCode() = updates.hashCode()
    override fun toString() = "PacketEntitiesMessage(updates=$updates)"
}

/**
 * An update of one entity, calculated and transmitted from server to client on each frame.
 * Value equality includes the header and payload; matching headers alone do not imply equal changes.
 */
sealed interface EntityUpdate {
    val number: Int
    /** A selected delta, either planned from baseline/previous state or received with its wire mask. */
    data class Change(val delta: EntityDelta) : EntityUpdate {
        override val number: Int get() = delta.state.index
        init { require(number != 0 && delta.flags and U_REMOVE == 0) }
    }
    /** Removal carries only the entity header, even if payload field bits are set. */
    data class Removal(val flags: Int, override val number: Int) : EntityUpdate {
        init {
            require(number in 1 until MAX_EDICTS && flags and U_REMOVE != 0)
            require(number < 256 || flags and U_NUMBER16 != 0)
        }
    }
    companion object {
        /** Snapshot and plan now: queued updates never retain mutable baseline/current engine state. */
        @JvmStatic fun plan(from: entity_state_t, to: entity_state_t, force: Boolean, newEntity: Boolean): Change? =
            EntityDeltaCodec.plan(EntityStateSnapshot.fromEngine(from), EntityStateSnapshot.fromEngine(to), force, newEntity)?.let(::Change)
    }
}

/** CL_ParseEntityBits: entity number (index) and header flags, including received extension bits. */
internal data class DeltaEntityHeader(val flags: Int, val number: Int)
