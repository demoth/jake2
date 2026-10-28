package org.demoth.cake.network

import jake2.qcommon.network.NetAddrType
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.netchan_t
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.messages.NetworkPacket
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientHandshakeTest {
    private data class Sent(val ip: List<Byte>, val port: Int, val command: ConnectionlessCommand, val payload: String)
    private class Network : ClientNetwork {
        val sent = mutableListOf<Sent>()
        val binds = mutableListOf<Boolean>()
        var bindSucceeds = true
        override fun configure(multiplayer: Boolean): Boolean {
            binds += multiplayer
            return bindSucceeds
        }
        override fun newChannel(): netchan_t = error("Handshake must not create protocol channels")
        override fun poll(budget: Int, consume: (NetworkPacket) -> Unit): Int = error("Caller owns polling")
        override fun sendConnectionless(to: netadr_t, command: ConnectionlessCommand, payload: String) {
            sent += Sent(to.ip.toList(), to.port, command, payload)
            // Deliberately hostile legacy consumer: it cannot mutate the retained target.
            to.ip.fill(0)
            to.port = 0
        }
        override fun stop() = error("Handshake must not stop its owner's transport")
    }
    private fun remote(port: Int = 27910) = netadr_t().apply {
        type = NetAddrType.NA_IP
        ip = byteArrayOf(127, 0, 0, 1)
        this.port = port
    }

    @Test
    fun `remote retry uses frame timing and fresh connect payload then stops after acceptance`() {
        val network = Network()
        var userinfo = "first"
        val handshake = ClientHandshake(network) { "$it $userinfo" }
        val peer = remote()
        assertTrue(handshake.begin(peer, false))
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
        assertTrue(handshake.begin(remote(), false))
        val wrongPeers = listOf(remote(27911), remote().apply { ip[3] = 2 }, remote().apply { type = NetAddrType.NA_LOOPBACK })
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
        val address = netadr_t()
        assertTrue(handshake.begin(address, true))
        address.ip[0] = 99
        handshake.lastTarget!!.ip[1] = 99
        assertTrue(handshake.isLocal)
        assertEquals(listOf(false), network.binds)
        handshake.tick(0f)
        assertEquals(ConnectionlessCommand.connect, network.sent.single().command)
        assertEquals("0", network.sent.single().payload)
        assertEquals(netadr_t().ip.toList(), network.sent.single().ip)
        assertTrue(handshake.acceptConnection(netadr_t()))
        handshake.reset()
        assertFalse(handshake.isLocal)
        val sent = network.sent.size
        handshake.tick(10f)
        assertEquals(sent, network.sent.size)
        assertTrue(handshake.begin(handshake.lastTarget!!, true))
        handshake.tick(0f)
        assertEquals(ConnectionlessCommand.connect, network.sent.last().command)
    }

    @Test
    fun `server restart retries immediately and map changing pauses pending replies`() {
        val network = Network()
        val handshake = ClientHandshake(network) { it.toString() }
        handshake.begin(remote(), false)
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
        handshake.begin(remote(), false)
        network.bindSucceeds = false
        assertFalse(handshake.begin(remote(12345), false))
        assertEquals(27910, handshake.lastTarget!!.port)
        handshake.tick(0f)
        assertEquals(27910, network.sent.single().port)
        assertTrue(handshake.acceptConnection(remote()))
    }
}
