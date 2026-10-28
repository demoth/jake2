package org.demoth.cake.network

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

/** Client transport boundary. A stopped game retires UDP and incoming local traffic, not its peer. */
interface ClientNetwork : AutoCloseable {
    fun configure(multiplayer: Boolean): Boolean
    fun newChannel(): netchan_t
    fun receive(): NetworkPacket?
    fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String)
    fun stop()
    override fun close() = stop()
}

/** Compatibility composition until application roots supply their own local link. */
@Deprecated("Compatibility adapter scheduled for removal; inject TransportClientNetwork with an owned NetworkTransport")
object LegacyClientNetwork : ClientNetwork {
    override fun configure(multiplayer: Boolean) = NET.configureClient(multiplayer)
    override fun newChannel() = netchan_t()
    override fun receive() = NET.receiveNetworkPacket(Defines.NS_CLIENT)
    override fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String) =
        Netchan.sendConnectionlessPacket(Defines.NS_CLIENT, to, command, payload)
    override fun stop() {
        NET.configureClient(false)
        NET.clearLoopbacks()
    }
}

/** Owns the supplied transport; connections borrow it and share the client UDP binding. */
class TransportClientNetwork(private val transport: NetworkTransport) : ClientNetwork {
    private var closed = false
    override fun newChannel() = netchan_t(transport) { System.nanoTime() / 1_000_000L }

    override fun configure(multiplayer: Boolean): Boolean {
        check(!closed) { "Client network is closed" }
        if (!multiplayer) {
            transport.replaceUdp(null)
            return true
        }
        if (transport.udpEndpoint?.isOpen == true) return true
        val cvars = Cvar.getInstance()
        val ip = cvars.find("ip", String::class.java).typedValue
        val port = cvars.find("clientport", Int::class.javaObjectType).typedValue.let { if (it == Defines.PORT_ANY) 0 else it }
        return try {
            val bindIp = if (ip.isEmpty() || ip == "localhost") null else java.net.InetAddress.getByName(ip)
            fun bind(port: Int) = UdpDatagramEndpoint.bind(if (bindIp == null) InetSocketAddress(port) else InetSocketAddress(bindIp, port))
            transport.replaceUdp(try { bind(port) } catch (_: java.io.IOException) { bind(0) })
            true
        } catch (error: java.io.IOException) {
            Com.Println("Client UDP bind error: $error")
            false
        }
    }

    override fun receive(): NetworkPacket? {
        val received = transport.receive()
        if (received is ReceiveResult.Failed) Com.Println("Client receive error: ${received.cause}")
        if (received !is ReceiveResult.Received) return null
        val datagram = received.datagram
        val decoded = PacketCodec.decode(datagram.payload, PacketDirection.FROM_SERVER)
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
        if (result is SendResult.Failed) Com.Println("Client send error: ${result.cause}")
    }

    override fun stop() {
        if (closed) return
        try {
            transport.replaceUdp(null)
        } finally {
            transport.clearLocalIncoming()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        transport.close()
    }
}
