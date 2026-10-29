package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.network.ConnectionChannel
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServerConnectionsTest {
    private fun address(host: Int = 1, port: Int = 10000) = NetworkAddress.Udp(IpAddress.fromBytes(byteArrayOf(192.toByte(), 0, 2, host.toByte())), port)

    @Test
    fun `bounded challenges own addresses and reuse without refreshing issuance order`() {
        var token = 32767
        val connections = ServerConnections(2) { token++ }
        val first = address()
        assertEquals(32767, connections.issueChallenge(first))
        first.ip.toByteArray()[3] = 99
        assertEquals(0, connections.issueChallenge(address(2)))
        assertEquals(32767, connections.issueChallenge(address(port = 54321)))
        assertEquals(ServerConnections.ChallengeValidation.ACCEPTED, connections.validateChallenge(address(), 32767))
        assertEquals(ServerConnections.ChallengeValidation.BAD, connections.validateChallenge(address(), 123))
        assertEquals(1, connections.issueChallenge(address(3)))
        assertEquals(ServerConnections.ChallengeValidation.MISSING, connections.validateChallenge(address(), 32767))
        assertEquals(ServerConnections.ChallengeValidation.ACCEPTED, connections.validateChallenge(address(2), 0))
        connections.reset()
        assertEquals(ServerConnections.ChallengeValidation.MISSING, connections.validateChallenge(address(2), 0))
        assertEquals(ServerConnections.ChallengeValidation.ACCEPTED, connections.validateChallenge(LoopbackLink().first.localAddress, 123))
        assertEquals(ServerConnections.ChallengeValidation.MISSING,
            connections.validateChallenge(NetworkAddress.Udp(IpAddress.fromBytes(byteArrayOf(127, 0, 0, 1)), 0), 0))
    }

    @Test
    fun `reconnect uses actual slot index after a free slot and respects Host time boundary`() {
        NetworkTransport.standalone(LocalSide.SECOND).use { transport ->
            fun client(state: ClientStates) = ClientNetworkInfo(ConnectionChannel(ChannelRole.SERVER, transport.localPeer, transport::send) { 0L }).apply {
                this.state = state
                netchan.setup(address(), 7)
                lastconnect = 100
            }
            val free = client(ClientStates.CS_FREE)
            val occupied = client(ClientStates.CS_CONNECTED)
            val clients = listOf(free, occupied)
            val connections = ServerConnections()
            assertEquals(ServerConnections.Admission.TooSoon,
                connections.selectSlot(clients, address(port = 20000), 7, 599, 500f))
            assertEquals(ServerConnections.Admission.Slot(1, true),
                connections.selectSlot(clients, address(port = 20000), 7, 600, 500f))
            // A matching source port can identify an existing connection even if qport changed.
            assertEquals(ServerConnections.Admission.Slot(1, true),
                connections.selectSlot(clients, address(), 8, 600, 500f))
            assertEquals(ServerConnections.Admission.Slot(0, false),
                connections.selectSlot(clients, address(2), 7, 600, 500f))
            assertEquals(ServerConnections.Admission.Full,
                connections.selectSlot(listOf(occupied), address(2), 7, 600, 500f))
            occupied.netchan.setup(transport.localPeer, 7)
            assertEquals(ServerConnections.Admission.Slot(1, true),
                connections.selectSlot(clients, transport.localPeer, 7, 100, 500f))
        }
    }

    private fun packet(sequence: Int, port: Int, qport: Int = 7, host: Int = 1): NetworkPacket {
        val source = address(host, port)
        val bytes = PacketCodec.encodeSequenced(sequence, 0, qport, PacketBytes.copyOf(byteArrayOf()))
        val envelope = (PacketCodec.decode(bytes, PacketDirection.FROM_CLIENT) as PacketDecodeResult.Decoded).envelope
        return NetworkPacket.fromEnvelope(NetworkAddress.Udp(source.ip, port), envelope)
    }

    @Test
    fun `local routing and reconnect admission retain link and endpoint identity`() {
        val first = LoopbackLink()
        val second = LoopbackLink()
        fun client(link: LoopbackLink) = ClientNetworkInfo(ConnectionChannel(
            ChannelRole.SERVER, link.first.localAddress, { _, _ -> SendResult.Sent }) { 0L }).apply {
            state = ClientStates.CS_CONNECTED
            netchan.setup(link.first.localAddress, 7)
        }
        val a = client(first)
        val b = client(second)
        val connections = ServerConnections()
        val clients = listOf(a, b)
        fun packet(source: NetworkAddress.Local): NetworkPacket {
            val bytes = PacketCodec.encodeSequenced(1, 0, 7, PacketBytes.copyOf(byteArrayOf()))
            val envelope = (PacketCodec.decode(bytes, PacketDirection.FROM_CLIENT) as PacketDecodeResult.Decoded).envelope
            return NetworkPacket.fromEnvelope(source, envelope)
        }
        assertEquals(ServerConnections.Routed(b, false), connections.route(packet(second.first.localAddress), clients))
        assertEquals(0, a.netchan.incomingSequence)
        assertEquals(1, b.netchan.incomingSequence)
        assertNull(connections.route(packet(first.second.localAddress), clients))
        assertNull(connections.route(packet(LoopbackLink().first.localAddress), clients))
        assertEquals(ServerConnections.Admission.Slot(1, true),
            connections.selectSlot(clients, second.first.localAddress, 7, 0, 500f))
        assertEquals(ServerConnections.Admission.Full,
            connections.selectSlot(clients, first.second.localAddress, 7, 0, 500f))
    }

    @Test
    fun `routing rejects wrong peers and stale packets before committing a translated port`() {
        NetworkTransport.standalone(LocalSide.SECOND).use { transport ->
            val client = ClientNetworkInfo(ConnectionChannel(ChannelRole.SERVER, transport.localPeer, transport::send) { 0L }).apply {
                state = ClientStates.CS_CONNECTED
                netchan.setup(address(), 7)
            }
            val connections = ServerConnections()
            val clients = listOf(client)
            assertNull(connections.route(packet(1, 20000, qport = 8), clients))
            assertNull(connections.route(packet(1, 20000, host = 2), clients))
            assertEquals(10000, (client.netchan.remoteAddress as NetworkAddress.Udp).port)
            assertEquals(ServerConnections.Routed(client, true), connections.route(packet(2, 20000), clients))
            assertEquals(20000, (client.netchan.remoteAddress as NetworkAddress.Udp).port)
            assertNull(connections.route(packet(1, 30000), clients))
            assertNull(connections.route(packet(2, 30000), clients))
            assertEquals(20000, (client.netchan.remoteAddress as NetworkAddress.Udp).port)
            assertEquals(2, client.netchan.incomingSequence)
            assertEquals(ServerConnections.Routed(client, false), connections.route(packet(3, 20000), clients))
            client.state = ClientStates.CS_ZOMBIE
            assertNotNull(connections.route(packet(4, 20000), clients))
            client.state = ClientStates.CS_FREE
            assertNull(connections.route(packet(5, 40000), clients))
            assertEquals(20000, (client.netchan.remoteAddress as NetworkAddress.Udp).port)
        }
    }
}
