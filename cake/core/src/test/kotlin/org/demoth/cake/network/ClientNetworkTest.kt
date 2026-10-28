package org.demoth.cake.network

import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.messages.ConnectionlessCommand
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.DatagramSocket

class ClientNetworkTest {
    @Test
    fun `client falls back from occupied UDP port and retains local transport on stop`() {
        val cvars = Cvar.getInstance()
        cvars.clear()
        Netchan.Netchan_Init()
        try {
            LoopbackLink().use { link ->
                val transport = NetworkTransport(link.first, link.second.localAddress)
                TransportClientNetwork(transport).use { client ->
                    DatagramSocket(0).use { occupied ->
                        cvars.find("clientport", Int::class.javaObjectType).forceSet(occupied.localPort)
                        assertTrue(client.configure(true))
                        val binding = transport.udpEndpoint!!
                        assertNotEquals(occupied.localPort, binding.localAddress.port)
                        assertTrue(client.configure(true))
                        assertSame(binding, transport.udpEndpoint)
                        client.stop()
                        assertFalse(binding.isOpen)
                        client.sendConnectionless(netadr_t(), ConnectionlessCommand.getchallenge, "\n")
                        val received = link.second.receive() as ReceiveResult.Received
                        assertEquals("getchallenge\n", received.datagram.payload.toByteArray()
                            .drop(4).toByteArray().toString(Charsets.ISO_8859_1))
                        val channel = client.newChannel()
                        channel.setup(jake2.qcommon.Defines.NS_CLIENT, netadr_t(), 42)
                        assertTrue(channel.transmit(null))
                        assertTrue(link.second.receive() is ReceiveResult.Received)
                    }
                    client.close()
                    client.close()
                    assertTrue(transport.send(transport.localPeer, PacketBytes.copyOf(byteArrayOf(1))) is SendResult.Failed)
                }
            }
        } finally {
            cvars.clear()
        }
    }
}
