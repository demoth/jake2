package jake2.qcommon.network.transport

import java.io.IOException

/** Owned transport resource. Operations are confined to the owner's game-loop thread. */
interface DatagramEndpoint : AutoCloseable {
    val localAddress: NetworkAddress
    val isOpen: Boolean

    /** Nonblocking. An empty poll is distinct from a discarded datagram or I/O failure. */
    fun receive(): ReceiveResult

    /** Sent means accepted by the transport, not acknowledged by the peer. */
    fun send(to: NetworkAddress, payload: PacketBytes): SendResult

    @Throws(IOException::class)
    override fun close()
}

data class Datagram(val source: NetworkAddress, val payload: PacketBytes)

sealed interface ReceiveResult {
    data class Received(val datagram: Datagram) : ReceiveResult
    data object Empty : ReceiveResult
    data class Dropped(val reason: DatagramDropReason) : ReceiveResult
    data class Failed(val cause: IOException) : ReceiveResult
}

enum class DatagramDropReason { OVERSIZED }

sealed interface SendResult {
    data object Sent : SendResult
    data object WouldBlock : SendResult
    data class Failed(val cause: IOException) : SendResult
}
