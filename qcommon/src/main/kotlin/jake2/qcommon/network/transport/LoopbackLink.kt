package jake2.qcommon.network.transport

import jake2.qcommon.Defines
import java.nio.channels.ClosedChannelException
import java.util.UUID

/** A private pair of bounded queues, used on one owner thread just like UDP endpoints. */
class LoopbackLink @JvmOverloads constructor(
    private val capacity: Int = 4,
    private val maxPacketSize: Int = Defines.MAX_MSGLEN,
) : AutoCloseable {
    init {
        require(capacity > 0) { "Loopback queue capacity must be positive" }
        require(maxPacketSize > 0) { "Maximum datagram size must be positive" }
    }

    private val id = UUID.randomUUID()
    val first = Endpoint(LocalSide.FIRST)
    val second = Endpoint(LocalSide.SECOND)

    override fun close() {
        first.close()
        second.close()
    }

    inner class Endpoint internal constructor(side: LocalSide) : DatagramEndpoint {
        override val localAddress = NetworkAddress.Local(id, side)
        override var isOpen: Boolean = true
            private set
        private val incoming = ArrayDeque<Datagram>()
        private val peer: Endpoint get() = if (this === first) second else first

        override fun receive(): ReceiveResult = when {
            !isOpen -> ReceiveResult.Failed(ClosedChannelException())
            incoming.isEmpty() -> ReceiveResult.Empty
            else -> ReceiveResult.Received(incoming.removeFirst())
        }

        override fun send(to: NetworkAddress, payload: PacketBytes): SendResult {
            require(to == peer.localAddress) { "Destination belongs to a different loopback endpoint" }
            require(payload.size <= maxPacketSize) { "Datagram exceeds $maxPacketSize bytes" }
            if (!isOpen || !peer.isOpen) return SendResult.Failed(ClosedChannelException())
            // Preserve the legacy bounded-queue policy: retain the most recent datagrams.
            if (peer.incoming.size == capacity) peer.incoming.removeFirst()
            peer.incoming.addLast(Datagram(localAddress, payload))
            return SendResult.Sent
        }

        /** Retire only this receiver's queued traffic, preserving the peer's final messages. */
        fun clearIncoming() = incoming.clear()

        override fun close() {
            isOpen = false
            clearIncoming()
        }
    }
}
