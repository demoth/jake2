package jake2.qcommon.network.runtime

import jake2.qcommon.network.CvarNetworkSettings
import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.NetworkSettings
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.DatagramSocket

class PacketNetworkTest {
    @ParameterizedTest
    @EnumSource(NetworkRole::class)
    fun `malformed traffic consumes the default budget and remaining packets wait for next pass`(role: NetworkRole) {
        val budget = PacketNetwork.DEFAULT_POLL_BUDGET
        LoopbackLink(capacity = budget + 1).use { link ->
            val transport = NetworkTransport(link.first, link.second.localAddress)
            PacketNetwork(transport, role, CvarNetworkSettings(Cvar.getInstance())).use { network ->
                repeat(budget) { link.second.send(link.first.localAddress, PacketBytes.copyOf(byteArrayOf(1))) }
                val ping = PacketBytes.copyOf(byteArrayOf(-1, -1, -1, -1) + "ping".toByteArray())
                link.second.send(link.first.localAddress, ping)
                assertEquals(budget, network.poll { fail("Malformed datagrams must not reach consumers") })
                var delivered = 0
                assertEquals(1, network.poll {
                    delivered++
                    assertEquals("ping", it.connectionlessMessage)
                })
                assertEquals(1, delivered)
                assertEquals(0, network.poll { fail("Queue should be empty") })
            }
        }
    }

    @Test
    fun `poll continues past malformed packets within budget and bounds self-replenishing traffic`() {
        LoopbackLink().use { link ->
            val transport = NetworkTransport(link.first, link.second.localAddress)
            PacketNetwork(transport, NetworkRole.CLIENT, CvarNetworkSettings(Cvar.getInstance())).use { network ->
                val ping = PacketBytes.copyOf(byteArrayOf(-1, -1, -1, -1) + "ping".toByteArray())
                link.second.send(link.first.localAddress, PacketBytes.copyOf(byteArrayOf(1)))
                link.second.send(link.first.localAddress, ping)
                var delivered = 0
                assertEquals(5, network.poll(5) {
                    delivered++
                    link.second.send(link.first.localAddress, ping)
                })
                assertEquals(4, delivered)
                assertEquals(0, network.poll(0) { fail("Zero budget must not consume traffic") })
                assertThrows(IllegalArgumentException::class.java) { network.poll(-1) {} }
                assertEquals("ping", network.receive()!!.connectionlessMessage)
            }
        }
    }

    @Test
    fun `shared packet adapter preserves direction and Latin-1 packet size boundaries`() {
        LoopbackLink().use { link ->
            val clientTransport = NetworkTransport(link.first, link.second.localAddress)
            val serverTransport = NetworkTransport(link.second, link.first.localAddress)
            PacketNetwork(clientTransport, NetworkRole.CLIENT, CvarNetworkSettings(Cvar.getInstance())).use { client ->
                PacketNetwork(serverTransport, NetworkRole.SERVER, CvarNetworkSettings(Cvar.getInstance())).use { server ->
                    val clientChannel = client.newChannel()
                    clientChannel.setup(clientChannel.remoteAddress, 1234)
                    assertTrue(clientChannel.transmit(null))
                    val request = server.receive()!!
                    assertEquals(1234, request.qport)
                    assertEquals(10, request.buffer.readcount)

                    val serverChannel = server.newChannel()
                    serverChannel.setup(serverChannel.remoteAddress, 1234)
                    assertTrue(serverChannel.accept(request))
                    assertTrue(serverChannel.transmit(null))
                    val reply = client.receive()!!
                    assertEquals(8, reply.buffer.readcount)
                    assertTrue(clientChannel.accept(reply))

                    val body = "é".repeat(Defines.MAX_MSGLEN - 4 - "print\n".length)
                    server.sendConnectionless(server.localPeer, ConnectionlessCommand.print, "\n$body")
                    val text = client.receive()!!
                    assertEquals(Defines.MAX_MSGLEN, text.buffer.cursize)
                    assertEquals("print", text.connectionlessMessage)
                    assertEquals(body, text.connectionlessParameters)
                    assertThrows(IllegalArgumentException::class.java) {
                        server.sendConnectionless(server.localPeer, ConnectionlessCommand.print, "\n${body}é")
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
        NetworkSettings.registerCvars()
        val transport = NetworkTransport.standalone(LocalSide.SECOND)
        try {
            PacketNetwork(transport, NetworkRole.SERVER, CvarNetworkSettings(Cvar.getInstance())).use { server ->
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
