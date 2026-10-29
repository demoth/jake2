package jake2.qcommon.network.transport

import jake2.qcommon.Defines
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/** Owns one nonblocking channel. No socket or channel handle escapes this class. */
class UdpDatagramEndpoint private constructor(
    private val channel: DatagramChannel,
    private val maxPacketSize: Int,
) : DatagramEndpoint {
    override val localAddress = NetworkAddress.Udp.fromSocketAddress(channel.localAddress as InetSocketAddress)
    override val isOpen: Boolean get() = channel.isOpen

    override fun receive(): ReceiveResult = try {
        // The extra byte distinguishes a full-sized packet from a truncated oversized one.
        val buffer = ByteBuffer.allocate(maxPacketSize + 1)
        val source = channel.receive(buffer) as InetSocketAddress?
        when {
            source == null -> ReceiveResult.Empty
            buffer.position() > maxPacketSize -> ReceiveResult.Dropped(DatagramDropReason.OVERSIZED)
            else -> ReceiveResult.Received(Datagram(
                NetworkAddress.Udp.fromSocketAddress(source),
                PacketBytes.copyOf(buffer.array(), buffer.position()),
            ))
        }
    } catch (failure: IOException) {
        ReceiveResult.Failed(failure)
    }

    override fun send(to: NetworkAddress, payload: PacketBytes): SendResult {
        require(to is NetworkAddress.Udp) { "UDP endpoint requires a UDP destination" }
        require(payload.size <= maxPacketSize) { "Datagram exceeds $maxPacketSize bytes" }
        return try {
            val sent = channel.send(payload.readOnlyBuffer(), to.toSocketAddress())
            if (sent == payload.size) SendResult.Sent else SendResult.WouldBlock
        } catch (failure: IOException) {
            SendResult.Failed(failure)
        }
    }

    @Throws(IOException::class)
    override fun close() = channel.close()

    companion object {
        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class)
        fun bind(address: InetSocketAddress, maxPacketSize: Int = Defines.MAX_MSGLEN): UdpDatagramEndpoint {
            require(!address.isUnresolved) { "Resolve the hostname before opening an endpoint" }
            require(maxPacketSize in 1..65507) { "Invalid maximum datagram size" }
            val channel = DatagramChannel.open()
            try {
                channel.bind(address)
                channel.configureBlocking(false)
                channel.setOption(StandardSocketOptions.SO_BROADCAST, true)
                return UdpDatagramEndpoint(channel, maxPacketSize)
            } catch (failure: Exception) {
                try {
                    channel.close()
                } catch (closeFailure: IOException) {
                    failure.addSuppressed(closeFailure)
                }
                throw failure
            }
        }
    }
}
