package jake2.server

import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.transport.*
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.runtime.PacketNetwork
import jake2.qcommon.network.protocol.PacketCodec
import jake2.qcommon.network.messages.client.UserInfoMessage
import jake2.qcommon.network.messages.server.DisconnectMessage
import jake2.qcommon.Defines
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServerNetworkTest {
    private class Peer(capacity: Int = 4) : AutoCloseable {
        private val link = LoopbackLink(capacity)
        val client = NetworkTransport(link.first, link.second.localAddress)
        val server = NetworkTransport(link.second, link.first.localAddress)
        val network = TransportServerNetwork(server)
        val host = ServerHost(network)
        fun queuePing() {
            client.send(client.localPeer, PacketBytes.copyOf(byteArrayOf(-1, -1, -1, -1) + "ping".toByteArray()))
        }
        fun ping() {
            queuePing()
            host.readPackets()
        }
        fun countReplies(): Int {
            var count = 0
            while (client.receive() is ReceiveResult.Received) count++
            return count
        }
        fun reply(): String {
            val received = client.receive() as ReceiveResult.Received
            return received.datagram.payload.toByteArray().drop(4).toByteArray().toString(Charsets.ISO_8859_1)
        }
        override fun close() {
            host.close()
            client.close()
        }
    }

    @Test
    fun `malformed client body drops only that client without dispatching its valid prefix`() {
        Cvar.getInstance().clear()
        MainCommon.registerCvars(false)
        Netchan.Netchan_Init()
        try {
            Peer().use { peer ->
                fun connection(qport: Int) = ClientNetworkInfo(peer.network.newChannel()).apply {
                    state = ClientStates.CS_CONNECTED
                    userinfo = "before"
                    netchan.setup(netchan.remoteAddress, qport)
                }
                val bad = connection(7)
                val healthy = connection(8)
                peer.host.clients.addAll(listOf(bad, healthy))
                val payload = sizebuf_t(64).apply {
                    UserInfoMessage("\\name\\must_not_apply").writeTo(this)
                    writeByte(99)
                }
                val bytes = PacketCodec.encodeSequenced(1, 0, 7, PacketBytes.copyOf(payload.data, payload.cursize))
                peer.client.send(peer.client.localPeer, bytes)
                peer.host.readPackets()
                assertEquals(ClientStates.CS_ZOMBIE, bad.state)
                assertEquals("before", bad.userinfo)
                assertTrue(bad.netchan.queuedReliable.any { it is DisconnectMessage })
                assertEquals(ClientStates.CS_CONNECTED, healthy.state)
                assertTrue(healthy.netchan.queuedReliable.isEmpty())
            }
        } finally {
            Cvar.getInstance().clear()
        }
    }

    @Test
    fun `Host packet pass bounds malformed and valid traffic and resumes next pass`() {
        Cvar.getInstance().clear()
        MainCommon.registerCvars(false)
        Netchan.Netchan_Init()
        try {
            val budget = PacketNetwork.DEFAULT_POLL_BUDGET
            Peer(capacity = budget + 1).use { peer ->
                peer.client.send(peer.client.localPeer, PacketBytes.copyOf(byteArrayOf(1)))
                repeat(budget) { peer.queuePing() }
                peer.host.readPackets()
                assertEquals(budget - 1, peer.countReplies())
                peer.host.readPackets()
                assertEquals(1, peer.countReplies())
            }
        } finally {
            Cvar.getInstance().clear()
        }
    }

    @Test
    fun `hosts reply on their injected links and closing one leaves the other running`() {
        Cvar.getInstance().clear()
        MainCommon.registerCvars(false)
        Netchan.Netchan_Init()
        try {
            Peer().use { first -> Peer().use { second ->
                first.ping()
                second.ping()
                assertEquals("ack", first.reply())
                assertEquals("ack", second.reply())
                Cvar.getInstance().find("port", Int::class.javaObjectType).forceSet(0)
                assertTrue(first.network.configure(true))
                val binding = first.server.udpEndpoint!!
                assertTrue(binding.isOpen)
                first.host.stop("stopped", false)
                assertFalse(binding.isOpen)
                first.ping()
                assertEquals("ack", first.reply())
                first.close()
                second.ping()
                assertEquals("ack", second.reply())
                assertTrue(second.server.send(second.server.localPeer, PacketBytes.copyOf(byteArrayOf(1))) is SendResult.Sent)
                assertTrue(first.server.send(first.server.localPeer, PacketBytes.copyOf(byteArrayOf(1))) is SendResult.Failed)
            } }
        } finally {
            Cvar.getInstance().clear()
        }
    }
}
