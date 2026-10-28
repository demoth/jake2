package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.network.NetAddrType
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.transport.IpAddress
import jake2.qcommon.util.Lib

/** Game-thread protocol admission and routing; gameplay admission remains with the Host. */
class ServerConnections(
    private val challengeCapacity: Int = Defines.MAX_CHALLENGES,
    private val nextToken: () -> Int = { Lib.rand().toInt() },
) {
    private data class PeerKey(val type: NetAddrType, val ip: IpAddress?) {
        companion object {
            fun of(address: netadr_t) = PeerKey(address.type,
                if (address.type == NetAddrType.NA_LOOPBACK) null else IpAddress.fromBytes(address.ip))
        }
    }

    enum class ChallengeValidation { ACCEPTED, BAD, MISSING }
    sealed interface Admission {
        data class Slot(val index: Int, val reconnect: Boolean) : Admission
        data object TooSoon : Admission
        data object Full : Admission
    }
    data class Routed(val client: ClientNetworkInfo, val portChanged: Boolean)

    init { require(challengeCapacity > 0) }

    // Insertion order is issuance order, independent of engine/Game clocks. Reuse never refreshes it.
    private val challenges = linkedMapOf<PeerKey, Int>()

    /**
     * Issue or reuse the token for the connectionless getchallenge -> challenge -> connect handshake.
     * A remote client echoes the token in its connect request; [validateChallenge] checks it against
     * the cached source IP (UDP port is ignored). Local connections bypass that check.
     *
     * This makes blind source-IP spoofing harder: the requester normally needs to receive the
     * reply before it can connect. The legacy 15-bit random token is not authentication or a
     * general denial-of-service defence; it can be guessed and does not prevent request floods.
     *
     * The bounded cache has no time-based expiry. Repeated requests reuse the token without
     * refreshing its position. New addresses take an unused entry or evict the earliest-issued
     * entry. Map insertion order tracks issuance even within one clock tick and across global
     * clock resets/wrap. Session stop calls [reset] to clear the cache; this order is never sent.
     */
    fun issueChallenge(from: netadr_t): Int {
        val key = PeerKey.of(from)
        challenges[key]?.let { return it }
        if (challenges.size == challengeCapacity) challenges.remove(challenges.keys.first())
        return (nextToken() and 0x7fff).also { challenges[key] = it }
    }

    fun validateChallenge(from: netadr_t, token: Int): ChallengeValidation {
        if (from.type == NetAddrType.NA_LOOPBACK) return ChallengeValidation.ACCEPTED
        val expected = challenges[PeerKey.of(from)] ?: return ChallengeValidation.MISSING
        return if (expected == token) ChallengeValidation.ACCEPTED else ChallengeValidation.BAD
    }

    fun selectSlot(
        clients: List<ClientNetworkInfo>, from: netadr_t, qport: Int,
        nowMillis: Long, reconnectDelayMillis: Float,
    ): Admission {
        val key = PeerKey.of(from)
        for ((index, client) in clients.withIndex()) {
            if (client.state == ClientStates.CS_FREE) continue
            if (PeerKey.of(client.netchan.remote_address) != key) continue
            if (client.netchan.qport != qport && client.netchan.remote_address.port != from.port) continue
            if (from.type != NetAddrType.NA_LOOPBACK && nowMillis - client.lastconnect < reconnectDelayMillis)
                return Admission.TooSoon
            return Admission.Slot(index, reconnect = true)
        }
        val free = clients.indexOfFirst { it.state == ClientStates.CS_FREE }
        return if (free < 0) Admission.Full else Admission.Slot(free, reconnect = false)
    }

    /** Match qport/base address, then accept sequence state before committing a NAT port change. */
    fun route(packet: NetworkPacket, clients: List<ClientNetworkInfo>): Routed? {
        if (packet.isConnectionless) return null
        val key = PeerKey.of(packet.from)
        val client = clients.firstOrNull {
            it.state != ClientStates.CS_FREE && it.netchan.qport == packet.qport &&
                PeerKey.of(it.netchan.remote_address) == key
        } ?: return null
        if (!client.netchan.accept(packet)) return null
        val changed = client.netchan.remote_address.port != packet.from.port
        if (changed) client.netchan.remote_address.port = packet.from.port
        return Routed(client, changed)
    }

    fun reset() = challenges.clear()
}
