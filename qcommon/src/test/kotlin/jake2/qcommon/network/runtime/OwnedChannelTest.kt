package jake2.qcommon.network.runtime

import jake2.qcommon.Defines
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.netchan_t
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OwnedChannelTest {
    private class Session : AutoCloseable {
        private val link = LoopbackLink()
        val clientTransport = NetworkTransport(link.first, link.second.localAddress)
        val serverTransport = NetworkTransport(link.second, link.first.localAddress)
        val client = netchan_t(clientTransport) { 100L }
        val server = netchan_t(serverTransport) { 100L }

        init {
            client.setup(Defines.NS_CLIENT, netadr_t(), 42)
            server.setup(Defines.NS_SERVER, netadr_t(), 42)
        }

        fun receive(transport: NetworkTransport, direction: PacketDirection): NetworkPacket {
            val datagram = (transport.receive() as ReceiveResult.Received).datagram
            val decoded = PacketCodec.decode(datagram.payload, direction) as PacketDecodeResult.Decoded
            return NetworkPacket.fromEnvelope(datagram.source, decoded.envelope)
        }

        override fun close() {
            clientTransport.close()
            serverTransport.close()
        }
    }

    @Test
    fun `two channel pairs deliver and acknowledge independently without global transport`() {
        Session().use { first -> Session().use { second ->
            first.client.reliablePending.add(StringCmdMessage("first"))
            second.client.reliablePending.add(StringCmdMessage("second"))
            assertTrue(first.client.transmit(null))
            assertTrue(second.client.transmit(null))
            val firstPacket = first.receive(first.serverTransport, PacketDirection.FROM_CLIENT)
            val secondPacket = second.receive(second.serverTransport, PacketDirection.FROM_CLIENT)
            assertTrue(first.server.accept(firstPacket))
            assertTrue(second.server.accept(secondPacket))
            assertEquals("first", ((firstPacket.parseBodyFromClient(1) as MessageDecodeResult.Decoded).messages.single() as StringCmdMessage).command)
            assertEquals("second", ((secondPacket.parseBodyFromClient(1) as MessageDecodeResult.Decoded).messages.single() as StringCmdMessage).command)
            assertTrue(first.server.transmit(null))
            assertTrue(first.client.accept(first.receive(first.clientTransport, PacketDirection.FROM_SERVER)))
            assertTrue(first.client.canReliable())
            assertFalse(second.client.canReliable())
            first.close()
            assertTrue(second.server.transmit(null))
            assertTrue(second.client.accept(second.receive(second.clientTransport, PacketDirection.FROM_SERVER)))
            assertTrue(second.client.canReliable())
        } }
    }

    @Test
    fun `clearing a channel retains its borrowed transport and failed send retains staging`() {
        Session().use { session ->
            session.client.clear()
            session.client.setup(Defines.NS_CLIENT, netadr_t(), 99)
            assertTrue(session.client.transmit(null))
            assertEquals(99, session.receive(session.serverTransport, PacketDirection.FROM_CLIENT).qport)
            session.clientTransport.close()
            session.client.reliablePending.add(StringCmdMessage("retry"))
            assertFalse(session.client.transmit(null))
            assertEquals(2, session.client.outgoing_sequence)
            assertEquals(1, session.client.reliablePending.size)
        }
    }
}
