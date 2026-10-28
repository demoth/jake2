package jake2.qcommon.network.runtime

import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration

class NetworkTransportTest {
    private fun pair(): Pair<NetworkTransport, NetworkTransport> {
        val link = LoopbackLink()
        return NetworkTransport(link.first, link.second.localAddress) to
            NetworkTransport(link.second, link.first.localAddress)
    }

    @Test
    fun `standalone owners have no local peer and close independently`() {
        NetworkTransport.standalone(LocalSide.FIRST).use { client ->
            NetworkTransport.standalone(LocalSide.SECOND).use { server ->
                assertTrue(client.send(client.localPeer, bytes) is SendResult.Failed)
                assertTrue(server.send(server.localPeer, bytes) is SendResult.Failed)
                assertEquals(ReceiveResult.Empty, client.receive())
                assertEquals(ReceiveResult.Empty, server.receive())
                client.close()
                assertEquals(ReceiveResult.Empty, server.receive())
            }
        }
    }

    private val bytes = PacketBytes.copyOf(byteArrayOf(1, 2, 3))

    @Test
    fun `continuous local traffic does not starve UDP`() {
        val (a, b) = pair()
        a.use { b.use {
            val udp = UdpDatagramEndpoint.bind(InetSocketAddress("127.0.0.1", 0))
            b.replaceUdp(udp)
            UdpDatagramEndpoint.bind(InetSocketAddress("127.0.0.1", 0)).use { sender ->
                assertEquals(SendResult.Sent, sender.send(udp.localAddress, bytes))
                assertTimeoutPreemptively(Duration.ofSeconds(2)) {
                    var receivedUdp = false
                    while (!receivedUdp) {
                        a.send(a.localPeer, bytes)
                        val result = b.receive() as ReceiveResult.Received
                        receivedUdp = result.datagram.source is NetworkAddress.Udp
                    }
                }
                // UDP selection did not consume the queued local message.
                assertTrue((b.receive() as ReceiveResult.Received).datagram.source is NetworkAddress.Local)
            }
        } }
    }

    @Test
    fun `independent pairs retain final peer traffic after sender closes`() {
        val (a, b) = pair()
        val (c, d) = pair()
        a.use { b.use { c.use { d.use {
            assertEquals(SendResult.Sent, a.send(a.localPeer, bytes))
            assertEquals(SendResult.Sent, c.send(c.localPeer, bytes))
            a.close()
            assertEquals(bytes, (b.receive() as ReceiveResult.Received).datagram.payload)
            assertEquals(bytes, (d.receive() as ReceiveResult.Received).datagram.payload)
            assertEquals(SendResult.Sent, d.send(d.localPeer, bytes))
            assertTrue(c.receive() is ReceiveResult.Received)
            assertTrue(a.send(a.localPeer, bytes) is SendResult.Failed)
        } } } }
    }

    @Test
    fun `poll budget bounds work even when callback keeps producing traffic`() {
        val (a, b) = pair()
        a.use { b.use {
            a.send(a.localPeer, bytes)
            assertEquals(3, b.poll(3) {
                assertTrue(it is ReceiveResult.Received)
                a.send(a.localPeer, bytes)
            })
            assertTrue(b.receive() is ReceiveResult.Received)
            assertEquals(0, b.poll(0) { fail("zero budget") })
            assertThrows(IllegalArgumentException::class.java) { b.poll(-1) {} }
        } }
    }

    @Test
    fun `binding transfers across local session reset and closes with new owner`() {
        val (a, b) = pair()
        val (c, d) = pair()
        val udp = UdpDatagramEndpoint.bind(InetSocketAddress(0))
        a.use { b.use { c.use { d.use {
            a.replaceUdp(udp)
            c.replaceUdp(a.detachUdp())
            a.close()
            assertTrue(udp.isOpen)
            assertSame(udp, c.udpEndpoint)
            c.replaceUdp(null)
            assertFalse(udp.isOpen)
            assertEquals(SendResult.Sent, c.send(c.localPeer, bytes))
            assertTrue(d.receive() is ReceiveResult.Received)
        } } } }
    }
}
