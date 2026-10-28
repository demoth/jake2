package jake2.qcommon.network.runtime

import jake2.qcommon.network.transport.*
import java.io.IOException
import java.nio.channels.ClosedChannelException

/**
 * Transport resources belonging to one client or server, confined to its game-loop thread.
 * The composition root supplies one end of a private local link. Bind policy belongs to callers.
 */
class NetworkTransport(
    private val local: LoopbackLink.Endpoint,
    val localPeer: NetworkAddress.Local,
) : AutoCloseable {
    companion object {
        /** Remote-only application: no in-process peer may send to or receive from this owner. */
        @JvmStatic fun standalone(side: LocalSide): NetworkTransport {
            val link = LoopbackLink()
            val local = if (side == LocalSide.FIRST) link.first else link.second
            val peer = if (side == LocalSide.FIRST) link.second else link.first
            peer.close()
            return NetworkTransport(local, peer.localAddress)
        }
    }

    var udpEndpoint: UdpDatagramEndpoint? = null
        private set
    private var localFirst = true
    private var closed = false

    init {
        require(local.localAddress.linkId == localPeer.linkId && local.localAddress != localPeer)
    }

    /** Transfers ownership; replacing UDP leaves local traffic and its lifetime intact. */
    @Throws(IOException::class)
    fun replaceUdp(endpoint: UdpDatagramEndpoint?) {
        check(!closed) { "Transport is closed" }
        if (udpEndpoint === endpoint) return
        val previous = udpEndpoint
        udpEndpoint = endpoint
        previous?.close()
    }

    /** Transfer the binding to a new session owner without closing it. */
    fun detachUdp(): UdpDatagramEndpoint? = udpEndpoint.also { udpEndpoint = null }

    fun send(to: NetworkAddress, payload: PacketBytes): SendResult {
        if (closed) return SendResult.Failed(ClosedChannelException())
        return when (to) {
            is NetworkAddress.Local -> local.send(to, payload)
            is NetworkAddress.Udp -> udpEndpoint?.send(to, payload)
                ?: SendResult.Failed(ClosedChannelException())
        }
    }

    fun receiveLocal(): ReceiveResult = local.receive()

    /** Alternate first choice so a busy local peer cannot starve remote peers. */
    fun receive(): ReceiveResult {
        if (closed) return ReceiveResult.Failed(ClosedChannelException())
        val first = if (localFirst) local else udpEndpoint
        val second = if (localFirst) udpEndpoint else local
        localFirst = !localFirst
        val result = first?.receive() ?: ReceiveResult.Empty
        return if (result == ReceiveResult.Empty) second?.receive() ?: ReceiveResult.Empty else result
    }

    /** Every consumed datagram, including a dropped one, counts against the frame budget. */
    fun poll(budget: Int, consume: (ReceiveResult) -> Unit): Int {
        require(budget >= 0)
        var count = 0
        while (count < budget) {
            val result = receive()
            if (result == ReceiveResult.Empty) break
            count++
            consume(result)
            if (result is ReceiveResult.Failed) break
        }
        return count
    }

    fun clearLocalIncoming() = local.clearIncoming()

    @Throws(IOException::class)
    override fun close() {
        if (closed) return
        closed = true
        val endpoint = udpEndpoint
        udpEndpoint = null
        try {
            endpoint?.close()
        } finally {
            // Closing our receiver must not discard final messages queued for our peer.
            local.close()
        }
    }
}
