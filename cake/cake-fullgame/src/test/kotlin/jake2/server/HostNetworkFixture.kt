package jake2.server

import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.CvarNetworkSettings
import jake2.qcommon.Defines
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.runtime.*
import jake2.qcommon.network.transport.*

/** Private client/server resources for a Host test, never shared through the legacy facade. */
internal class HostNetworkFixture : AutoCloseable {
    private val link = LoopbackLink()
    val serverTransport = NetworkTransport(link.second, link.first.localAddress)
    val clientTransport = NetworkTransport(link.first, link.second.localAddress)
    val server = TransportServerNetwork(serverTransport)
    val client = PacketNetwork(clientTransport, NetworkRole.CLIENT, CvarNetworkSettings(Cvar.getInstance()))

    /** Inject a programming failure at send time without corrupting connection address state. */
    fun failTransmissions(client: ClientNetworkInfo) {
        val previous = client.netchan
        client.netchan = jake2.qcommon.network.ConnectionChannel(
            ChannelRole.SERVER, serverTransport.localPeer,
            { _, _ -> throw IllegalStateException("injected send failure") },
            clockMillis = { 0L },
        ).also { replacement ->
            replacement.setup(previous.remoteAddress, previous.qport)
            previous.queuedReliable.forEach(replacement::queueReliable)
        }
    }

    fun clearLocalTraffic() {
        link.first.clearIncoming()
        link.second.clearIncoming()
    }

    fun receiveLocal(role: Int): NetworkPacket? {
        val endpoint = if (role == Defines.NS_CLIENT) link.first else link.second
        val received = endpoint.receive() as? ReceiveResult.Received ?: return null
        val datagram = received.datagram
        val direction = if (role == Defines.NS_CLIENT) PacketDirection.FROM_SERVER else PacketDirection.FROM_CLIENT
        val decoded = PacketCodec.decode(datagram.payload, direction) as? PacketDecodeResult.Decoded ?: return null
        return NetworkPacket.fromEnvelope(datagram.source, decoded.envelope)
    }

    fun sendLocal(role: Int, length: Int, bytes: ByteArray) {
        val transport = if (role == Defines.NS_CLIENT) clientTransport else serverTransport
        transport.send(transport.localPeer, PacketBytes.copyOf(bytes, length))
    }

    override fun close() {
        try { server.close() } finally { client.close() }
    }
}
