package jake2.qcommon.network.runtime

import jake2.qcommon.Defines
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.ConnectionChannel
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.messages.client.UserCommand
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OwnedChannelTest {
    @Test
    fun `the same immutable movement uses each packet's reserved sequence after reliable data`() {
        Session().use { session ->
            val movement = MoveMessage(3, UserCommand(), UserCommand(), UserCommand(msec = 16, forwardmove = 120))
            session.client.queueReliable(StringCmdMessage("new"))
            repeat(2) { index ->
                assertTrue(session.client.transmit(listOf(movement)))
                val packet = session.receive(session.serverTransport, PacketDirection.FROM_CLIENT)
                assertTrue(session.server.accept(packet))
                assertEquals(index + 1, session.server.incomingSequence)
                val body = packet.parseBodyFromClient(session.server.incomingSequence) as MessageDecodeResult.Decoded
                val decoded = body.messages.last() as MoveMessage
                assertTrue(decoded.checksumValid)
                assertEquals(movement, decoded)
            }
        }
    }

    private class Session : AutoCloseable {
        private val link = LoopbackLink()
        val clientTransport = NetworkTransport(link.first, link.second.localAddress)
        val serverTransport = NetworkTransport(link.second, link.first.localAddress)
        val client = ConnectionChannel(ChannelRole.CLIENT, clientTransport.localPeer, clientTransport::send) { 100L }
        val server = ConnectionChannel(ChannelRole.SERVER, serverTransport.localPeer, serverTransport::send) { 100L }

        init {
            client.setup(netadr_t(), 42)
            server.setup(netadr_t(), 42)
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
            first.client.queueReliable(StringCmdMessage("first"))
            second.client.queueReliable(StringCmdMessage("second"))
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
            assertTrue(first.client.canSendReliable)
            assertFalse(second.client.canSendReliable)
            first.close()
            assertTrue(second.server.transmit(null))
            assertTrue(second.client.accept(second.receive(second.clientTransport, PacketDirection.FROM_SERVER)))
            assertTrue(second.client.canSendReliable)
        } }
    }

    @Test
    fun `clearing a channel retains its borrowed transport and failed send retains staging`() {
        Session().use { session ->
            session.client.clear()
            session.client.setup(netadr_t(), 99)
            assertTrue(session.client.transmit(null))
            assertEquals(99, session.receive(session.serverTransport, PacketDirection.FROM_CLIENT).qport)
            session.clientTransport.close()
            session.client.queueReliable(StringCmdMessage("retry"))
            assertFalse(session.client.transmit(null))
            assertEquals(2, session.client.outgoingSequence)
            assertEquals(1, session.client.queuedReliable.size)
        }
    }
}
