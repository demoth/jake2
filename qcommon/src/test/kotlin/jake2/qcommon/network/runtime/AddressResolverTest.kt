package jake2.qcommon.network.runtime

import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Inet6Address
import java.net.UnknownHostException

class AddressResolverTest {
    @Test
    fun `local aliases resolve only to the supplied owned peer without DNS`() {
        val first = LoopbackLink()
        val second = LoopbackLink()
        for (alias in listOf("localhost", "LOCALHOST", "loopback", "LOOPBACK")) {
            val target = requireNotNull(AddressResolver.parse(alias, 27910))
            assertSame(AddressTarget.Local, target)
            assertEquals(first.second.localAddress, AddressResolver.resolve(target, first.second.localAddress) { error("Local aliases must not resolve DNS") })
            assertNotEquals(AddressResolver.resolve(target, first.second.localAddress), AddressResolver.resolve(target, second.second.localAddress))
        }
        assertNotEquals(first.first.localAddress, first.second.localAddress)
        assertTrue(AddressResolver.isValid("localhost", 27910))
    }

    @Test
    fun `UDP parsing retains default explicit and zero fallback ports and bracketed IPv6`() {
        for ((text, expected) in mapOf(
            "example.test" to AddressTarget.Host("example.test", 27910),
            "10.132.12.54:4312" to AddressTarget.Host("10.132.12.54", 4312),
            "localhost:27911" to AddressTarget.Host("localhost", 27911),
            "127.0.0.1:0" to AddressTarget.Host("127.0.0.1", 27910),
            "[2001:db8::1]:65535" to AddressTarget.Host("2001:db8::1", 65535),
            "[::1]" to AddressTarget.Host("::1", 27910),
            "::1" to AddressTarget.Host("::1", 27910),
        )) assertEquals(expected, AddressResolver.parse(text, 27910))
        val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val peer = LoopbackLink().second.localAddress
        val udp = AddressResolver.resolve(AddressTarget.Host("localhost", 27910), peer) { loopback }
        assertInstanceOf(NetworkAddress.Udp::class.java, udp)
        assertNotEquals(peer, udp)
        assertEquals("127.0.0.1:27910", udp.toString())
    }

    @Test
    fun `invalid syntax fails before lookup and unknown hosts fail resolution without creating resources`() {
        for (text in listOf("", " ", ":27910", "host:", "host:nope", "host:-1", "host:65536", "host:2147483648", "host:12x", "bad host", "host\n", "[::1", "[]", "[::1]junk", "[::1]:", "[::1]:-1", "[127.0.0.1]"))
            assertNull(AddressResolver.parse(text, 27910), text)
        val peer = LoopbackLink().second.localAddress
        val target = requireNotNull(AddressResolver.parse("missing.test", 27910))
        assertNull(AddressResolver.resolve(target, peer) { throw UnknownHostException(it) })
    }

    @Test
    fun `scoped IPv6 values retain scope and exported bytes cannot change resolved addresses`() {
        val raw = InetAddress.getByName("fe80::1").address
        val scoped = Inet6Address.getByAddress(null, raw, 7)
        val peer = LoopbackLink().second.localAddress
        val address = AddressResolver.resolve(AddressTarget.Host("scoped.test", 27910), peer) { scoped } as NetworkAddress.Udp
        raw.fill(0)
        address.ip.toByteArray().fill(0)
        assertEquals(7, (address.ip.toInetAddress() as Inet6Address).scopeId)
        assertEquals(address, address.copy())
        assertTrue(address.toString().startsWith("["))
        assertTrue(address.toString().endsWith("]:27910"))
    }

    @Test
    fun `decoded local packet retains link and endpoint identity`() {
        val first = LoopbackLink()
        val second = LoopbackLink()
        val wire = PacketCodec.encodeSequenced(1, 0, 42, PacketBytes.copyOf(byteArrayOf()))
        val envelope = (PacketCodec.decode(wire, PacketDirection.FROM_CLIENT) as PacketDecodeResult.Decoded).envelope
        val packet = NetworkPacket.fromEnvelope(first.first.localAddress, envelope)
        assertSame(first.first.localAddress, packet.from)
        assertNotEquals(first.second.localAddress, packet.from)
        assertNotEquals(second.first.localAddress, packet.from)
    }
}
