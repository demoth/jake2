package jake2.qcommon.network.runtime

import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.network.NetworkSettings
import jake2.qcommon.network.ConnectionChannel
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.transport.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class NetworkRole { CLIENT, SERVER }

/** Shared engine adapter: bind policy, packet envelopes and transport lifetime for one owner. */
class PacketNetwork @JvmOverloads constructor(
    private val transport: NetworkTransport,
    private val role: NetworkRole,
    private val settings: NetworkSettings,
    private val log: (String) -> Unit = { Com.Printf(it) },
) : AutoCloseable {
    companion object {
        /** Maximum datagrams consumed per receive pass, including malformed and dropped traffic. */
        const val DEFAULT_POLL_BUDGET = 256
    }

    private var closed = false
    private val label = if (role == NetworkRole.CLIENT) "Client" else "Server"
    private val direction = if (role == NetworkRole.CLIENT) PacketDirection.FROM_SERVER else PacketDirection.FROM_CLIENT

    val localPeer: NetworkAddress.Local get() = transport.localPeer

    fun resolveAddress(text: String, defaultPort: Int): NetworkAddress? = AddressResolver.resolve(text, defaultPort, localPeer)

    fun newChannel(): ConnectionChannel {
        check(!closed) { "$label network is closed" }
        return ConnectionChannel(
            if (role == NetworkRole.CLIENT) ChannelRole.CLIENT else ChannelRole.SERVER,
            transport.localPeer, transport::send,
            logPackets = { settings.logPackets },
            log = log,
            clockMillis = { System.nanoTime() / 1_000_000L },
        )
    }

    /**
     * Configure only this owner's UDP endpoint; loopback never needs a socket.
     * Reuse the binding until explicitly disabled. Only clients fall back to an ephemeral port.
     */
    fun configure(enabled: Boolean): Boolean {
        check(!closed) { "$label network is closed" }
        if (!enabled) {
            transport.replaceUdp(null)
            return true
        }
        if (transport.udpEndpoint?.isOpen == true) return true
        val ip = settings.bindAddress
        val port = (if (role == NetworkRole.CLIENT) settings.clientPort else settings.serverPort)
            .let { if (it == Defines.PORT_ANY) 0 else it }
        return try {
            val bindIp = if (ip.isEmpty() || ip == "localhost") null else InetAddress.getByName(ip)
            fun bind(port: Int) = UdpDatagramEndpoint.bind(
                if (bindIp == null) InetSocketAddress(port) else InetSocketAddress(bindIp, port))
            val endpoint = try {
                bind(port)
            } catch (error: IOException) {
                if (role != NetworkRole.CLIENT) throw error
                bind(0)
            }
            transport.replaceUdp(endpoint)
            true
        } catch (error: IOException) {
            log("$label UDP bind error: $error\n")
            false
        }
    }

    /** Compatibility nullable view; explicit transport/codec outcomes remain below this adapter. */
    fun receive(): NetworkPacket? {
        return decode(transport.receive())
    }

    /** Bound transport work, rather than only successfully decoded packets. */
    fun poll(budget: Int = DEFAULT_POLL_BUDGET, consume: (NetworkPacket) -> Unit): Int =
        transport.poll(budget) { received -> decode(received)?.let(consume) }

    private fun decode(received: ReceiveResult): NetworkPacket? {
        if (received is ReceiveResult.Failed) log("$label receive error: ${received.cause}\n")
        if (received !is ReceiveResult.Received) return null
        val datagram = received.datagram
        val decoded = PacketCodec.decode(datagram.payload, direction)
        if (decoded is PacketDecodeResult.Rejected) {
            if (settings.logDrops)
                log("$label dropped packet from ${datagram.source}: ${decoded.reason}\n")
            return null
        }
        val packet = NetworkPacket.fromEnvelope(datagram.source, (decoded as PacketDecodeResult.Decoded).envelope)
        if (packet.isConnectionless && settings.logMessages) {
            val peer = if (role == NetworkRole.CLIENT) "server" else "client"
            log("Network: Received from $peer connectionless: ${packet.connectionlessMessage}\n")
        }
        return packet
    }

    /** Out-of-band datagram: a -1 sequence marker followed by Latin-1 command/payload bytes. */
    fun sendConnectionless(to: NetworkAddress, command: ConnectionlessCommand, payload: String) {
        val text = (command.name + payload).toByteArray(Charsets.ISO_8859_1)
        require(text.size <= Defines.MAX_MSGLEN - 4)
        val bytes = ByteBuffer.allocate(text.size + 4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).put(text).array()
        val result = transport.send(to, PacketBytes.copyOf(bytes))
        if (result is SendResult.Failed) log("$label send error: ${result.cause}\n")
    }

    /** Session boundary: keep the local endpoint and final messages queued for its peer. */
    fun stop() {
        if (closed) return
        try {
            transport.replaceUdp(null)
        } finally {
            transport.clearLocalIncoming()
        }
    }

    /** Shutdown closes this owner's UDP and local endpoints, independently of the peer's resources. */
    override fun close() {
        if (closed) return
        closed = true
        transport.close()
    }
}
