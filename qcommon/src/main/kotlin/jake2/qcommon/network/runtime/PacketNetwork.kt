package jake2.qcommon.network.runtime

import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.netchan_t
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
class PacketNetwork(
    private val transport: NetworkTransport,
    private val role: NetworkRole,
) : AutoCloseable {
    companion object {
        /** Maximum datagrams consumed per receive pass, including malformed and dropped traffic. */
        const val DEFAULT_POLL_BUDGET = 256
    }

    private var closed = false
    private val label = if (role == NetworkRole.CLIENT) "Client" else "Server"
    private val direction = if (role == NetworkRole.CLIENT) PacketDirection.FROM_SERVER else PacketDirection.FROM_CLIENT

    fun newChannel(): netchan_t {
        check(!closed) { "$label network is closed" }
        return netchan_t(transport) { System.nanoTime() / 1_000_000L }
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
        val cvars = Cvar.getInstance()
        val ip = cvars.find("ip", String::class.java).typedValue
        val portName = if (role == NetworkRole.CLIENT) "clientport" else "port"
        val port = cvars.find(portName, Int::class.javaObjectType).typedValue
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
            Com.Println("$label UDP bind error: $error")
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
        if (received is ReceiveResult.Failed) Com.Println("$label receive error: ${received.cause}")
        if (received !is ReceiveResult.Received) return null
        val datagram = received.datagram
        val decoded = PacketCodec.decode(datagram.payload, direction)
        if (decoded is PacketDecodeResult.Rejected) {
            if (Netchan.showdrop?.typedValue == true)
                Com.Println("$label dropped packet from ${datagram.source}: ${decoded.reason}")
            return null
        }
        val packet = NetworkPacket.fromEnvelope(datagram.source, (decoded as PacketDecodeResult.Decoded).envelope)
        if (packet.isConnectionless && Netchan.printNetwork?.typedValue == true) {
            val peer = if (role == NetworkRole.CLIENT) "server" else "client"
            Com.Printf("Network: Received from $peer connectionless: ${packet.connectionlessMessage}\n")
        }
        return packet
    }

    /** Out-of-band datagram: a -1 sequence marker followed by Latin-1 command/payload bytes. */
    fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String) {
        val text = (command.name + payload).toByteArray(Charsets.ISO_8859_1)
        require(text.size <= Defines.MAX_MSGLEN - 4)
        val bytes = ByteBuffer.allocate(text.size + 4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).put(text).array()
        val destination = to.toNetworkAddress(transport.localPeer)
        val result = transport.send(destination, PacketBytes.copyOf(bytes))
        if (result is SendResult.Failed) Com.Println("$label send error: ${result.cause}")
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
