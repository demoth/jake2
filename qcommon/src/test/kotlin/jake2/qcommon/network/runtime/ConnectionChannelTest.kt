package jake2.qcommon.network.runtime

import jake2.qcommon.Defines
import jake2.qcommon.network.ConnectionChannel
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.transport.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class ConnectionChannelTest {
    @Test
    fun `keepalive uses elapsed long clock and only successful sends restart it`() {
        val link = LoopbackLink()
        var now = Int.MAX_VALUE.toLong() + 100_000
        var result: SendResult = SendResult.Sent
        val channel = ConnectionChannel(ChannelRole.CLIENT, link.second.localAddress, { _, _ -> result }) { now }
        channel.setup(channel.remoteAddress, 42)
        assertFalse(channel.hasBeenIdleFor(1000))
        now += 1000
        assertFalse(channel.hasBeenIdleFor(1000))
        now++
        assertTrue(channel.hasBeenIdleFor(1000))
        assertTrue(channel.transmit(null))
        assertEquals(now, channel.lastSentMillis)
        assertFalse(channel.hasBeenIdleFor(1000))
        now += 1001
        result = SendResult.WouldBlock
        assertFalse(channel.transmit(null))
        assertTrue(channel.hasBeenIdleFor(1000))
        result = SendResult.Sent
        assertTrue(channel.transmit(null))
        assertFalse(channel.hasBeenIdleFor(1000))
        now += 1001
        channel.clear()
        assertFalse(channel.hasBeenIdleFor(1000))
        assertEquals(0L, channel.lastSentMillis)
    }

    @Test
    fun `address input and views are detached and NAT updates affect only the owned port`() {
        val link = LoopbackLink()
        val destinations = mutableListOf<NetworkAddress>()
        val channel = ConnectionChannel(ChannelRole.SERVER, link.first.localAddress, { to, _ ->
            destinations += to; SendResult.Sent
        }) { 0L }
        val address = NetworkAddress.Udp(IpAddress.fromBytes(byteArrayOf(10, 1, 2, 3)), 27910)
        channel.setup(address, 1234)
        address.ip.toByteArray().fill(99)
        val view = channel.remoteAddress
        (view as NetworkAddress.Udp).ip.toByteArray().fill(88)
        channel.transmit(null)
        channel.updateRemotePort(27911)
        channel.transmit(null)
        assertEquals(listOf(27910, 27911), destinations.map { (it as NetworkAddress.Udp).port })
        assertTrue(destinations.all { (it as NetworkAddress.Udp).ip == IpAddress.fromBytes(byteArrayOf(10, 1, 2, 3)) })
        assertEquals(1234, channel.qport)
    }

    @Test
    fun `staging snapshots are immutable and cancellation cannot erase transmitted reliable bytes`() {
        val link = LoopbackLink()
        val wire = mutableListOf<PacketBytes>()
        val channel = ConnectionChannel(ChannelRole.CLIENT, link.second.localAddress, { _, bytes ->
            wire += bytes; SendResult.Sent
        }) { 0L }
        channel.setup(channel.remoteAddress, 42)
        val command = StringCmdMessage("first")
        channel.queueReliable(command)
        val snapshot = channel.queuedReliable
        assertThrows(UnsupportedOperationException::class.java) { (snapshot as MutableList<NetworkMessage>).clear() }
        assertTrue(channel.transmit(null))
        assertEquals(listOf(command), snapshot)
        assertFalse(channel.hasQueuedReliable)
        assertFalse(channel.canSendReliable)
        channel.queueReliable(StringCmdMessage("cancelled"))
        channel.clearQueuedReliable()
        assertFalse(channel.hasQueuedReliable)
        assertFalse(channel.canSendReliable)
        // A higher sequence acknowledgement with the wrong reliable bit requests retransmission.
        repeat(2) { assertTrue(channel.transmit(null)) }
        val ack = PacketCodec.encodeSequenced(1, 3, null, PacketBytes.copyOf(byteArrayOf()))
        val decoded = PacketCodec.decode(ack, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded
        assertTrue(channel.accept(jake2.qcommon.network.messages.NetworkPacket.fromEnvelope(link.second.localAddress, decoded.envelope)))
        assertTrue(channel.needsReliable)
        assertTrue(channel.transmit(null))
        val resent = (PacketCodec.decode(wire.last(), PacketDirection.FROM_CLIENT) as PacketDecodeResult.Decoded).envelope as PacketEnvelope.Sequenced
        val original = (PacketCodec.decode(wire.first(), PacketDirection.FROM_CLIENT) as PacketDecodeResult.Decoded).envelope as PacketEnvelope.Sequenced
        assertArrayEquals(original.bytes.toByteArray().copyOfRange(10, original.bytes.size), resent.bytes.toByteArray().copyOfRange(10, resent.bytes.size))
    }

    @Test
    fun `would block and IO failure retain exact bytes sequence and staging for retry`() {
        val link = LoopbackLink()
        for (failure in listOf(SendResult.WouldBlock, SendResult.Failed(IOException("unavailable")))) {
            val attempts = mutableListOf<PacketBytes>()
            val channel = ConnectionChannel(ChannelRole.CLIENT, link.second.localAddress, { _, bytes ->
                attempts += bytes
                if (attempts.size == 1) failure else SendResult.Sent
            }) { 100L }
            channel.setup(channel.remoteAddress, 42)
            channel.queueReliable(StringCmdMessage("retry"))
            assertFalse(channel.transmit(null))
            assertEquals(1, channel.outgoingSequence)
            assertTrue(channel.hasQueuedReliable)
            assertTrue(channel.transmit(null))
            assertEquals(attempts[0], attempts[1])
            assertFalse(channel.hasQueuedReliable)
            assertEquals(2, channel.outgoingSequence)
        }
    }

    @Test
    fun `overflow marks only the connection fatal and clear retains its server role and sender`() {
        val link = LoopbackLink()
        val wire = mutableListOf<PacketBytes>()
        val channel = ConnectionChannel(ChannelRole.SERVER, link.first.localAddress, { _, bytes ->
            wire += bytes; SendResult.Sent
        }) { 0L }
        channel.queueReliable(StringCmdMessage("x".repeat(Defines.MAX_MSGLEN)))
        assertFalse(channel.transmit(null))
        assertTrue(channel.fatalError)
        assertTrue(wire.isEmpty())
        channel.clear()
        assertFalse(channel.fatalError)
        assertFalse(channel.hasQueuedReliable)
        channel.setup(channel.remoteAddress, 42)
        assertTrue(channel.transmit(null))
        assertEquals(8, wire.single().size, "A server channel retains its header role across clear/setup")
    }
}
