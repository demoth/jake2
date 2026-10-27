package jake2.server

import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.transport.*
import jake2.qcommon.network.runtime.NetworkTransport
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServerNetworkTest {
    private class Peer : AutoCloseable {
        private val link = LoopbackLink()
        val client = NetworkTransport(link.first, link.second.localAddress)
        val server = NetworkTransport(link.second, link.first.localAddress)
        val network = TransportServerNetwork(server)
        val host = ServerHost(network)
        fun ping() {
            client.send(client.localPeer, PacketBytes.copyOf(byteArrayOf(-1, -1, -1, -1) + "ping".toByteArray()))
            host.readPackets()
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
