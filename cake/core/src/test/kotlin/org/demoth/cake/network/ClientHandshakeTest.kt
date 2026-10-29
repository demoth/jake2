package org.demoth.cake.network

import jake2.qcommon.network.transport.IpAddress
import jake2.qcommon.network.transport.NetworkAddress
import jake2.qcommon.network.ConnectionChannel
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkPacket
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientHandshakeTest {
    private data class Sent(val address: NetworkAddress, val command: ConnectionlessCommand, val payload: String)
    private class Network : ClientNetwork {
        override val localPeer = jake2.qcommon.network.transport.LoopbackLink().second.localAddress
        val sent = mutableListOf<Sent>()
        val binds = mutableListOf<Boolean>()
        var bindSucceeds = true
        override fun configure(multiplayer: Boolean): Boolean {
            binds += multiplayer
            return bindSucceeds
        }
        override fun newChannel(): ConnectionChannel = error("Handshake must not create protocol channels")
        override fun poll(budget: Int, consume: (NetworkPacket) -> Unit): Int = error("Caller owns polling")
        override fun sendConnectionless(to: NetworkAddress, command: ConnectionlessCommand, payload: String) {
            sent += Sent(to, command, payload)
            // An exported byte array cannot mutate the immutable target.
            (to as? NetworkAddress.Udp)?.ip?.toByteArray()?.fill(0)
        }
        override fun stop() = error("Handshake must not stop its owner's transport")
    }
    private fun remote(port: Int = 27910) = NetworkAddress.Udp(IpAddress.fromBytes(byteArrayOf(127, 0, 0, 1)), port)

    @Test
    fun `remote retry uses frame timing and fresh connect payload then stops after acceptance`() {
        val network = Network()
        var userinfo = "first"
        val handshake = ClientHandshake(network) { "$it $userinfo" }
        val peer = remote()
        assertTrue(handshake.begin(peer))
        assertEquals(listOf(true), network.binds)
        handshake.tick(0f)
        assertEquals(ConnectionlessCommand.getchallenge, network.sent.single().command)
        assertEquals("\n", network.sent.single().payload)
        handshake.tick(1f)
        assertEquals(1, network.sent.size)
        handshake.tick(0f)
        assertEquals(2, network.sent.size)
        assertTrue(handshake.receiveChallenge(peer, "123"))
        assertEquals("123 first", network.sent.last().payload)
        userinfo = "updated"
        assertTrue(handshake.receiveChallenge(peer, "456"))
        assertEquals("456 updated", network.sent.last().payload)
        assertTrue(handshake.acceptConnection(peer))
        val acceptedCount = network.sent.size
        handshake.tick(100f)
        assertFalse(handshake.receiveChallenge(peer, "789"))
        assertFalse(handshake.acceptConnection(peer))
        assertEquals(acceptedCount, network.sent.size)
    }

    @Test
    fun `challenge and acceptance require a pending handshake and exact endpoint`() {
        val network = Network()
        val handshake = ClientHandshake(network) { it.toString() }
        assertFalse(handshake.acceptConnection(remote()))
        assertTrue(handshake.begin(remote()))
        val wrongPeers = listOf(remote(27911), NetworkAddress.Udp(IpAddress.fromBytes(byteArrayOf(127, 0, 0, 2)), 27910), network.localPeer)
        for (peer in wrongPeers) {
            assertFalse(handshake.receiveChallenge(peer, "42"))
            assertFalse(handshake.acceptConnection(peer))
        }
        for (value in listOf(null, "", "nonsense", "2147483648"))
            assertFalse(handshake.receiveChallenge(remote(), value))
        assertTrue(network.sent.isEmpty())
        assertTrue(handshake.receiveChallenge(remote(), "-123"))
        handshake.reset()
        assertFalse(handshake.receiveChallenge(remote(), "42"))
        assertFalse(handshake.acceptConnection(remote()))
    }

    @Test
    fun `local attachment bypasses challenge and reset retains an isolated target for reconnect`() {
        val network = Network()
        val handshake = ClientHandshake(network) { it.toString() }
        val address = network.localPeer
        assertTrue(handshake.begin(address))
        assertTrue(handshake.isLocal)
        assertEquals(listOf(false), network.binds)
        handshake.tick(0f)
        assertEquals(ConnectionlessCommand.connect, network.sent.single().command)
        assertEquals("0", network.sent.single().payload)
        assertEquals(address, network.sent.single().address)
        assertTrue(handshake.acceptConnection(address))
        handshake.reset()
        assertFalse(handshake.isLocal)
        val sent = network.sent.size
        handshake.tick(10f)
        assertEquals(sent, network.sent.size)
        assertTrue(handshake.begin(handshake.lastTarget!!))
        handshake.tick(0f)
        assertEquals(ConnectionlessCommand.connect, network.sent.last().command)
    }

    @Test
    fun `local handshake rejects another link or the opposite endpoint`() {
        val network = Network()
        val handshake = ClientHandshake(network) { it.toString() }
        assertTrue(handshake.begin(network.localPeer))
        assertFalse(handshake.acceptConnection(jake2.qcommon.network.transport.LoopbackLink().second.localAddress))
        val wrongEndpoint = network.localPeer.copy(side = jake2.qcommon.network.transport.LocalSide.FIRST)
        assertFalse(handshake.acceptConnection(wrongEndpoint))
        assertFalse(handshake.receiveChallenge(wrongEndpoint, "42"))
        assertTrue(handshake.acceptConnection(network.localPeer))
    }

    @Test
    fun `server restart retries immediately and map changing pauses pending replies`() {
        val network = Network()
        val handshake = ClientHandshake(network) { it.toString() }
        handshake.begin(remote())
        assertTrue(handshake.acceptConnection(remote()))
        handshake.restart()
        handshake.tick(0f)
        assertEquals(ConnectionlessCommand.getchallenge, network.sent.single().command)
        handshake.pause()
        assertFalse(handshake.receiveChallenge(remote(), "42"))
        assertFalse(handshake.acceptConnection(remote()))
        handshake.tick(100f)
        assertEquals(1, network.sent.size)
    }

    @Test
    fun `failed bind preserves active target and retry state`() {
        val network = Network()
        val handshake = ClientHandshake(network) { it.toString() }
        handshake.begin(remote())
        network.bindSucceeds = false
        assertFalse(handshake.begin(remote(12345)))
        assertEquals(27910, (handshake.lastTarget as NetworkAddress.Udp).port)
        handshake.tick(0f)
        assertEquals(27910, (network.sent.single().address as NetworkAddress.Udp).port)
        assertTrue(handshake.acceptConnection(remote()))
    }
}
