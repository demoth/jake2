package jake2.server

import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.*
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.transport.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Server transport boundary. A stopped game retires UDP and incoming local traffic, not its peer. */
interface ServerNetwork : AutoCloseable {
    fun configure(multiplayer: Boolean): Boolean
    fun newChannel(): netchan_t
    fun receive(): NetworkPacket?
    fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String)
    fun stop()
    fun sleep(millis: Int)
    override fun close() = stop()
}

/** Compatibility composition until application roots supply their own local link. */
@Deprecated("Compatibility adapter scheduled for removal; inject TransportServerNetwork with an owned NetworkTransport")
internal object LegacyServerNetwork : ServerNetwork {
    override fun configure(multiplayer: Boolean) = NET.configureServer(multiplayer)
    override fun newChannel() = netchan_t()
    override fun receive() = NET.receiveNetworkPacket(Defines.NS_SERVER)
    override fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String) =
        Netchan.sendConnectionlessPacket(Defines.NS_SERVER, to, command, payload)
    override fun stop() {
        NET.configureServer(false)
        NET.clearLoopback(Defines.NS_SERVER)
    }
    override fun sleep(millis: Int) = NET.Sleep(millis)
}

/** Owns the supplied transport; connections borrow it and share the server UDP binding. */
class TransportServerNetwork(private val transport: NetworkTransport) : ServerNetwork {
    private var closed = false
    override fun newChannel() = netchan_t(transport) { System.nanoTime() / 1_000_000L }

    override fun configure(multiplayer: Boolean): Boolean {
        check(!closed) { "Server network is closed" }
        if (!multiplayer) {
            transport.replaceUdp(null)
            return true
        }
        if (transport.udpEndpoint?.isOpen == true) return true
        val cvars = Cvar.getInstance()
        val ip = cvars.find("ip", String::class.java).typedValue
        val port = cvars.find("port", Int::class.javaObjectType).typedValue.let { if (it == Defines.PORT_ANY) 0 else it }
        return try {
            transport.replaceUdp(UdpDatagramEndpoint.bind(
                if (ip.isEmpty() || ip == "localhost") InetSocketAddress(port) else InetSocketAddress(java.net.InetAddress.getByName(ip), port)))
            true
        } catch (error: java.io.IOException) {
            Com.Println("Server UDP bind error: $error")
            false
        }
    }

    override fun receive(): NetworkPacket? {
        val received = transport.receive()
        if (received is ReceiveResult.Failed) Com.Println("Server receive error: ${received.cause}")
        if (received !is ReceiveResult.Received) return null
        val datagram = received.datagram
        val decoded = PacketCodec.decode(datagram.payload, PacketDirection.FROM_CLIENT)
        return if (decoded is PacketDecodeResult.Decoded) NetworkPacket.fromEnvelope(datagram.source, decoded.envelope)
        else null
    }

    override fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String) {
        val text = (command.name + payload).toByteArray(Charsets.ISO_8859_1)
        require(text.size <= Defines.MAX_MSGLEN - 4)
        val bytes = ByteBuffer.allocate(text.size + 4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).put(text).array()
        val destination = if (to.type == NetAddrType.NA_LOOPBACK) transport.localPeer else
            NetworkAddress.Udp(IpAddress.fromInetAddress(to.inetAddress), to.port)
        val result = transport.send(destination, PacketBytes.copyOf(bytes))
        if (result is SendResult.Failed) Com.Println("Server send error: ${result.cause}")
    }

    override fun stop() {
        if (closed) return
        try {
            transport.replaceUdp(null)
        } finally {
            transport.clearLocalIncoming()
        }
    }

    override fun sleep(millis: Int) {
        if (transport.udpEndpoint == null) return
        try {
            Thread.sleep(millis.toLong())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        transport.close()
    }
}
