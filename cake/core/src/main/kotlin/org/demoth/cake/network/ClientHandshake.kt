package org.demoth.cake.network

import jake2.qcommon.network.NetAddrType
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.transport.IpAddress

/** Game-thread handshake controller. Time and current connect payload are supplied by the caller. */
class ClientHandshake(
    private val network: ClientNetwork,
    private val connectPayload: (challenge: Int) -> String,
) {
    private data class Target(val type: NetAddrType, val ip: IpAddress, val port: Int) {
        fun toLegacy() = netadr_t().also { it.type = type; it.ip = ip.toByteArray(); it.port = port }
        fun matches(from: netadr_t) = type == from.type && port == from.port && ip == IpAddress.fromBytes(from.ip)
        companion object {
            fun copyOf(address: netadr_t) = Target(address.type, IpAddress.fromBytes(address.ip), address.port)
        }
    }

    private var target: Target? = null
    /** Owned snapshot, retained after reset for the manual reconnect command. */
    val lastTarget: netadr_t? get() = target?.toLegacy()
    var isLocal = false
        private set
    private var pending = false
    private var challenge = 0
    private var retryRemainingSeconds = 0f

    /** A failed bind leaves the current handshake untouched. */
    fun begin(address: netadr_t, local: Boolean): Boolean {
        if (!network.configure(!local)) return false
        target = Target.copyOf(address)
        isLocal = local
        challenge = 0
        restart()
        return true
    }

    /** Server reconnect keeps the target/challenge and fires on the next frame. */
    fun restart() {
        pending = target != null
        retryRemainingSeconds = 0f
    }

    /** Map-change commands can retire a pending handshake without resetting the connection. */
    fun pause() { pending = false }

    fun reset() {
        pending = false
        isLocal = false
        challenge = 0
        retryRemainingSeconds = 0f
    }

    /** Preserve frame-based retry timing: a timer reaching zero sends on the following tick. */
    fun tick(deltaSeconds: Float) {
        if (!pending) return
        val peer = target ?: return
        if (retryRemainingSeconds <= 0f) {
            if (isLocal) sendConnect(peer)
            else network.sendConnectionless(peer.toLegacy(), ConnectionlessCommand.getchallenge, "\n")
            retryRemainingSeconds = 1f
        } else {
            retryRemainingSeconds -= deltaSeconds
        }
    }

    fun receiveChallenge(from: netadr_t, value: String?): Boolean {
        val peer = expected(from) ?: return false
        val parsed = value?.toIntOrNull() ?: return false
        challenge = parsed
        sendConnect(peer)
        return true
    }

    fun acceptConnection(from: netadr_t): Boolean {
        if (expected(from) == null) return false
        pending = false
        return true
    }

    private fun expected(from: netadr_t): Target? = target?.takeIf { pending && it.matches(from) }

    private fun sendConnect(peer: Target) {
        network.sendConnectionless(peer.toLegacy(), ConnectionlessCommand.connect, connectPayload(challenge))
    }
}
