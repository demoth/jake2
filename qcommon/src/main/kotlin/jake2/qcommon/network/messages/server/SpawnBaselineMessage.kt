package jake2.qcommon.network.messages.server

import jake2.qcommon.entity_state_t

/**
 * Contains the baseline for a single entity, sent to a new client when it has just connected to the server.
 * A baseline is a snapshot of the entity state at the moment of level instantiation.
 * Received values retain their original delta mask instead of planning a new delta during encoding.
 */
data class SpawnBaselineMessage(val delta: EntityDelta) : ServerMessage(ServerMessageType.svc_spawnbaseline) {
    val entityState: EntityStateSnapshot get() = delta.state
    constructor(state: EntityStateSnapshot) : this(EntityDeltaCodec.baseline(state))
    constructor(state: entity_state_t) : this(EntityStateSnapshot.fromEngine(state))
}
