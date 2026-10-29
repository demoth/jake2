package org.demoth.cake.network

import jake2.qcommon.network.transport.NetworkAddress
import jake2.qcommon.network.messages.ConnectionlessCommand

/** Game-thread handshake controller. Time and current connect payload are supplied by the caller. */
class ClientHandshake(
    private val network: ClientNetwork,
    private val connectPayload: (challenge: Int) -> String,
) {
    private var target: NetworkAddress? = null
    /** Immutable target, retained after reset for the manual reconnect command. */
    val lastTarget: NetworkAddress? get() = target
    var isLocal = false
        private set
    private var pending = false
    private var challenge = 0
    private var retryRemainingSeconds = 0f

    /** A failed bind leaves the current handshake untouched. */
    fun begin(address: NetworkAddress): Boolean {
        val local = address is NetworkAddress.Local
        if (!network.configure(!local)) return false
        target = address
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
            else network.sendConnectionless(peer, ConnectionlessCommand.getchallenge, "\n")
            retryRemainingSeconds = 1f
        } else {
            retryRemainingSeconds -= deltaSeconds
        }
    }

    fun receiveChallenge(from: NetworkAddress, value: String?): Boolean {
        val peer = expected(from) ?: return false
        val parsed = value?.toIntOrNull() ?: return false
        challenge = parsed
        sendConnect(peer)
        return true
    }

    fun acceptConnection(from: NetworkAddress): Boolean {
        if (expected(from) == null) return false
        pending = false
        return true
    }

    private fun expected(from: NetworkAddress): NetworkAddress? = target?.takeIf { pending && it == from }

    private fun sendConnect(peer: NetworkAddress) {
        network.sendConnectionless(peer, ConnectionlessCommand.connect, connectPayload(challenge))
    }
}
