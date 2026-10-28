package jake2.qcommon.network.runtime

import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.DatagramSocket

class PacketNetworkTest {
    @Test
    fun `shared packet adapter preserves direction and Latin-1 packet size boundaries`() {
        LoopbackLink().use { link ->
            val clientTransport = NetworkTransport(link.first, link.second.localAddress)
            val serverTransport = NetworkTransport(link.second, link.first.localAddress)
            PacketNetwork(clientTransport, NetworkRole.CLIENT).use { client ->
                PacketNetwork(serverTransport, NetworkRole.SERVER).use { server ->
                    val clientChannel = client.newChannel()
                    clientChannel.setup(Defines.NS_CLIENT, netadr_t(), 1234)
                    assertTrue(clientChannel.transmit(null))
                    val request = server.receive()!!
                    assertEquals(1234, request.qport)
                    assertEquals(10, request.buffer.readcount)

                    val serverChannel = server.newChannel()
                    serverChannel.setup(Defines.NS_SERVER, netadr_t(), 1234)
                    assertTrue(serverChannel.accept(request))
                    assertTrue(serverChannel.transmit(null))
                    val reply = client.receive()!!
                    assertEquals(8, reply.buffer.readcount)
                    assertTrue(clientChannel.accept(reply))

                    val body = "é".repeat(Defines.MAX_MSGLEN - 4 - "print\n".length)
                    server.sendConnectionless(netadr_t(), ConnectionlessCommand.print, "\n$body")
                    val text = client.receive()!!
                    assertEquals(Defines.MAX_MSGLEN, text.buffer.cursize)
                    assertEquals("print", text.connectionlessMessage)
                    assertEquals(body, text.connectionlessParameters)
                    assertThrows(IllegalArgumentException::class.java) {
                        server.sendConnectionless(netadr_t(), ConnectionlessCommand.print, "\n${body}é")
                    }
                    assertNull(client.receive())
                }
            }
        }
    }

    @Test
    fun `server bind failure never falls back and legacy wildcard port works with explicit IP`() {
        val cvars = Cvar.getInstance()
        cvars.clear()
        Netchan.Netchan_Init()
        val transport = NetworkTransport.standalone(LocalSide.SECOND)
        try {
            PacketNetwork(transport, NetworkRole.SERVER).use { server ->
                DatagramSocket(0).use { occupied ->
                    cvars.find("port", Int::class.javaObjectType).forceSet(occupied.localPort)
                    assertFalse(server.configure(true))
                    assertNull(transport.udpEndpoint)
                }
                cvars.find("ip", String::class.java).forceSet("127.0.0.1")
                cvars.find("port", Int::class.javaObjectType).forceSet(Defines.PORT_ANY)
                assertTrue(server.configure(true))
                val binding = transport.udpEndpoint!!
                assertTrue(binding.localAddress.port > 0)
                assertTrue(server.configure(true))
                assertSame(binding, transport.udpEndpoint)
                server.configure(false)
                assertFalse(binding.isOpen)
                assertNull(transport.udpEndpoint)
                server.close()
                server.stop()
                assertThrows(IllegalStateException::class.java) { server.configure(true) }
                assertThrows(IllegalStateException::class.java) { server.newChannel() }
            }
        } finally {
            cvars.clear()
        }
    }
}
