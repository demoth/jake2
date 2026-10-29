package jake2.qcommon.network.transport

import jake2.qcommon.Defines.MAX_MSGLEN
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.locks.LockSupport

class DatagramEndpointTest {
    @Test
    fun `address and payload values own their bytes and have content equality`() {
        val bytes = byteArrayOf(127, 0, 0, 1)
        val ip = IpAddress.fromBytes(bytes)
        val payload = PacketBytes.copyOf(bytes)
        bytes[0] = 42
        ip.toByteArray()[0] = 11
        payload.toByteArray()[0] = 12

        val sameIp = IpAddress.fromBytes(byteArrayOf(127, 0, 0, 1))
        assertEquals(ip, sameIp)
        assertEquals(ip.hashCode(), sameIp.hashCode())
        assertEquals(payload, PacketBytes.copyOf(byteArrayOf(127, 0, 0, 1)))
        assertEquals("value", mapOf(NetworkAddress.Udp(ip, 27910) to "value")[NetworkAddress.Udp(sameIp, 27910)])
        assertNotEquals(NetworkAddress.Udp(ip, 27910), NetworkAddress.Udp(ip, 27911))
    }

    @Test
    fun `IPv6 scope participates in address identity`() {
        val bytes = InetAddress.getByName("fe80::1").address
        val first = IpAddress.fromInetAddress(Inet6Address.getByAddress(null, bytes, 1))
        val second = IpAddress.fromInetAddress(Inet6Address.getByAddress(null, bytes, 2))
        assertNotEquals(first, second)
        assertEquals(1, (first.toInetAddress() as Inet6Address).scopeId)
    }

    @Test
    fun `two loopback pairs exchange bytes independently`() {
        LoopbackLink().use { a ->
            LoopbackLink().use { b ->
                val first = PacketBytes.copyOf(byteArrayOf(1))
                val second = PacketBytes.copyOf(byteArrayOf(2))
                assertEquals(SendResult.Sent, a.first.send(a.second.localAddress, first))
                assertEquals(SendResult.Sent, b.first.send(b.second.localAddress, second))
                assertEquals(Datagram(a.first.localAddress, first), received(a.second).datagram)
                assertEquals(Datagram(b.first.localAddress, second), received(b.second).datagram)
                assertEquals(ReceiveResult.Empty, a.first.receive())
                assertEquals(ReceiveResult.Empty, b.first.receive())
                assertThrows(IllegalArgumentException::class.java) { a.first.send(b.second.localAddress, first) }
                a.close()
                assertTrue(b.first.isOpen)
                assertEquals(SendResult.Sent, b.second.send(b.first.localAddress, second))
                assertEquals(second, received(b.first).datagram.payload)
            }
        }
    }

    @Test
    fun `loopback overflow keeps newest packets and receive buffers stay owned`() {
        LoopbackLink(capacity = 2).use { link ->
            val buffer = byteArrayOf(1)
            link.first.send(link.second.localAddress, PacketBytes.copyOf(buffer))
            val retained = received(link.second).datagram
            for (value in 2..4) {
                buffer[0] = value.toByte()
                link.first.send(link.second.localAddress, PacketBytes.copyOf(buffer))
            }
            assertArrayEquals(byteArrayOf(1), retained.payload.toByteArray())
            assertArrayEquals(byteArrayOf(3), received(link.second).datagram.payload.toByteArray())
            assertArrayEquals(byteArrayOf(4), received(link.second).datagram.payload.toByteArray())
            assertEquals(ReceiveResult.Empty, link.second.receive())
        }
    }

    @Test
    fun `closing one local endpoint preserves final messages at its peer`() {
        LoopbackLink().use { link ->
            val finalMessage = PacketBytes.copyOf(byteArrayOf(9))
            link.first.send(link.second.localAddress, finalMessage)
            link.second.send(link.first.localAddress, finalMessage)
            link.first.close()
            link.first.close()
            assertFalse(link.first.isOpen)
            assertTrue(link.second.isOpen)
            assertEquals(finalMessage, received(link.second).datagram.payload)
            assertEquals(ReceiveResult.Empty, link.second.receive())
            assertInstanceOf(ReceiveResult.Failed::class.java, link.first.receive())
            assertInstanceOf(SendResult.Failed::class.java, link.second.send(link.first.localAddress, finalMessage))
        }
    }

    @Test
    fun `clearing a local inbox preserves traffic in the other direction`() {
        LoopbackLink().use { link ->
            val payload = PacketBytes.copyOf(byteArrayOf(1))
            link.first.send(link.second.localAddress, payload)
            link.second.send(link.first.localAddress, payload)
            link.first.clearIncoming()
            assertEquals(ReceiveResult.Empty, link.first.receive())
            assertEquals(payload, received(link.second).datagram.payload)
        }
    }

    @Test
    fun `UDP endpoints are nonblocking and independent with full size datagrams`() {
        udp().use { a ->
            udp().use { b ->
                udp().use { c ->
                    udp().use { d ->
                        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
                            assertEquals(ReceiveResult.Empty, a.receive())
                        }
                        val full = PacketBytes.copyOf(ByteArray(MAX_MSGLEN) { it.toByte() })
                        val small = PacketBytes.copyOf(byteArrayOf(7))
                        assertEquals(SendResult.Sent, a.send(b.localAddress, full))
                        assertEquals(SendResult.Sent, c.send(d.localAddress, small))
                        assertEquals(Datagram(a.localAddress, full), received(b).datagram)
                        assertEquals(Datagram(c.localAddress, small), received(d).datagram)
                        a.close()
                        assertTrue(b.isOpen)
                        assertTrue(c.isOpen)
                        assertEquals(SendResult.Sent, d.send(c.localAddress, small))
                        assertEquals(small, received(c).datagram.payload)
                        assertInstanceOf(ReceiveResult.Failed::class.java, a.receive())
                        assertInstanceOf(SendResult.Failed::class.java, a.send(b.localAddress, small))
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1401, 4096])
    fun `oversized UDP datagrams are reported and do not block later packets`(size: Int) {
        udp().use { receiver ->
            DatagramSocket().use { sender ->
                val bytes = ByteArray(size)
                sender.send(DatagramPacket(bytes, bytes.size, receiver.localAddress.toSocketAddress()))
                assertEquals(ReceiveResult.Dropped(DatagramDropReason.OVERSIZED), awaitPacket(receiver))
                val valid = byteArrayOf(1, 2)
                sender.send(DatagramPacket(valid, valid.size, receiver.localAddress.toSocketAddress()))
                assertArrayEquals(valid, received(receiver).datagram.payload.toByteArray())
            }
        }
    }

    @Test
    fun `UDP bind failure leaves the occupied endpoint intact and allows retry`() {
        val address = udp().use { occupied ->
            val address = occupied.localAddress.toSocketAddress()
            assertThrows(IOException::class.java) { UdpDatagramEndpoint.bind(address) }
            assertTrue(occupied.isOpen)
            address
        }
        UdpDatagramEndpoint.bind(address).use { retry ->
            assertTrue(retry.isOpen)
            assertEquals(address.port, retry.localAddress.port)
        }
    }

    @Test
    fun `transports reject oversized outgoing payloads before changing state`() {
        val oversized = PacketBytes.copyOf(ByteArray(MAX_MSGLEN + 1))
        LoopbackLink().use { link ->
            assertThrows(IllegalArgumentException::class.java) { link.first.send(link.second.localAddress, oversized) }
            assertEquals(ReceiveResult.Empty, link.second.receive())
        }
        udp().use { endpoint ->
            assertThrows(IllegalArgumentException::class.java) { endpoint.send(endpoint.localAddress, oversized) }
            assertEquals(ReceiveResult.Empty, endpoint.receive())
            assertTrue(endpoint.isOpen)
        }
    }

    private fun udp() = UdpDatagramEndpoint.bind(InetSocketAddress("127.0.0.1", 0))

    private fun received(endpoint: DatagramEndpoint): ReceiveResult.Received =
        assertInstanceOf(ReceiveResult.Received::class.java, awaitPacket(endpoint))

    private fun awaitPacket(endpoint: DatagramEndpoint): ReceiveResult {
        val deadline = System.nanoTime() + 2_000_000_000L
        do {
            val result = endpoint.receive()
            if (result != ReceiveResult.Empty) return result
            LockSupport.parkNanos(1_000_000)
        } while (System.nanoTime() < deadline)
        fail<Unit>("Datagram did not arrive")
        return ReceiveResult.Empty
    }
}
