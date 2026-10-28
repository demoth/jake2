package jake2.qcommon.network.protocol

import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import jake2.qcommon.usercmd_t
import jake2.qcommon.network.messages.NetworkPacket
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.network.transport.NetworkAddress
import jake2.qcommon.network.transport.LocalSide
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

/** Deterministic protocol simulations: no sockets, wall-clock waits, Cvars, or Globals. */
class ReliableChannelTest {
    private val empty = bytes()

    @Test
    fun `first client packet matches legacy wire layout and channel qport`() {
        val client = ReliableChannel(ChannelRole.CLIENT, 0x1234)
        assertEquals(ReliableQueueResult.QUEUED, client.enqueueReliable(bytes(0x55)))
        val packet = prepare(client, bytes(0x66))
        assertArrayEquals(byteArrayOf(1, 0, 0, -128, 0, 0, 0, 0, 0x34, 0x12, 0x55, 0x66), packet.bytes.toByteArray())
        assertEquals(1, client.outgoingSequence)
        client.completeSend(packet, true, 100)
        assertEquals(2, client.outgoingSequence)
        assertEquals(1, client.reliableSentFlag)
        assertEquals(2, client.lastReliableSequence)
        assertEquals(100, client.lastSentMillis)
        assertEquals(12, client.lastSentSize)
    }

    @Test
    fun `lost reliable payload retransmits only after the legacy acknowledgement threshold`() {
        val client = ReliableChannel(ChannelRole.CLIENT, 7)
        val server = ReliableChannel(ChannelRole.SERVER, 7)
        val original = byteArrayOf(11, 12)
        client.enqueueReliable(PacketBytes.copyOf(original))
        val lost = send(client, now = 10)
        original.fill(99)
        assertTrue(lost.containsReliable)

        accept(server, send(client, now = 20), PacketDirection.FROM_CLIENT, 25)
        accept(client, send(server, now = 30), PacketDirection.FROM_SERVER, 35)
        assertFalse(client.needsReliable, "Ack 2 must not retransmit the reliable sent with sequence 1")

        accept(server, send(client, now = 40), PacketDirection.FROM_CLIENT, 45)
        accept(client, send(server, now = 50), PacketDirection.FROM_SERVER, 55)
        assertTrue(client.needsReliable)
        val retry = send(client, now = 60)
        assertTrue(retry.containsReliable)
        assertArrayEquals(byteArrayOf(11, 12), body(retry, PacketDirection.FROM_CLIENT))
        accept(server, retry, PacketDirection.FROM_CLIENT, 65)
        assertEquals(1, server.reliableReceivedFlag)
        assertEquals(ChannelAcceptResult.Rejected(ChannelRejection.OUT_OF_ORDER),
            server.accept(envelope(lost, PacketDirection.FROM_CLIENT), 66))
        assertEquals(1, server.reliableReceivedFlag)
        accept(client, send(server, now = 70), PacketDirection.FROM_SERVER, 75)
        assertTrue(client.canSendReliable)
        assertEquals(0, client.unacknowledgedReliableBytes)
    }

    @Test
    fun `lost acknowledgement is recovered without retransmitting delivered reliable bytes`() {
        val client = ReliableChannel(ChannelRole.CLIENT, 7)
        val server = ReliableChannel(ChannelRole.SERVER, 7)
        client.enqueueReliable(bytes(11))
        accept(server, send(client), PacketDirection.FROM_CLIENT)
        send(server) // Drop the acknowledgement.
        val next = send(client)
        assertFalse(next.containsReliable)
        accept(server, next, PacketDirection.FROM_CLIENT)
        accept(client, send(server), PacketDirection.FROM_SERVER)
        assertTrue(client.canSendReliable)
        assertEquals(1, server.reliableReceivedFlag)
    }

    @Test
    fun `queued reliable batches wait for acknowledgement and alternate flags`() {
        val client = ReliableChannel(ChannelRole.CLIENT, 7)
        val server = ReliableChannel(ChannelRole.SERVER, 7)
        client.enqueueReliable(bytes(1))
        accept(server, send(client), PacketDirection.FROM_CLIENT)
        client.enqueueReliable(bytes(2))
        assertFalse(send(client).containsReliable)
        assertEquals(1, client.pendingReliableBytes)
        accept(client, send(server), PacketDirection.FROM_SERVER)
        val second = send(client)
        assertArrayEquals(byteArrayOf(2), body(second, PacketDirection.FROM_CLIENT))
        assertEquals(0, client.reliableSentFlag)
        accept(server, second, PacketDirection.FROM_CLIENT)
        assertEquals(0, server.reliableReceivedFlag)
        accept(client, send(server), PacketDirection.FROM_SERVER)
        assertTrue(client.canSendReliable)
    }

    @Test
    fun `failed send preserves sequence queues flags and statistics for retry`() {
        val channel = ReliableChannel(ChannelRole.SERVER, 0)
        channel.enqueueReliable(bytes(1, 2))
        val blocked = prepare(channel, bytes(3))
        channel.completeSend(blocked, false, 100)
        assertEquals(1, channel.outgoingSequence)
        assertEquals(2, channel.pendingReliableBytes)
        assertEquals(0, channel.unacknowledgedReliableBytes)
        assertEquals(0, channel.reliableSentFlag)
        assertEquals(0, channel.lastSentMillis)
        assertEquals(0, channel.lastSentSize)
        assertEquals(0, channel.trackedSendCount)
        val retry = prepare(channel, bytes(3))
        assertEquals(blocked.bytes, retry.bytes)
        channel.completeSend(retry, true, 150)
        assertEquals(2, channel.outgoingSequence)
        assertEquals(0, channel.pendingReliableBytes)
        assertEquals(2, channel.unacknowledgedReliableBytes)
        assertEquals(150, channel.lastSentMillis)
    }

    @Test
    fun `failed retransmission retains its retry eligibility and original bytes`() {
        val client = ReliableChannel(ChannelRole.CLIENT, 7)
        val server = ReliableChannel(ChannelRole.SERVER, 7)
        client.enqueueReliable(bytes(10))
        send(client) // Lost.
        send(client) // Lost.
        accept(server, send(client), PacketDirection.FROM_CLIENT)
        accept(client, send(server), PacketDirection.FROM_SERVER)
        val retry = prepare(client)
        assertTrue(retry.containsReliable)
        client.completeSend(retry, false, 100)
        assertTrue(client.needsReliable)
        val secondTry = prepare(client)
        assertEquals(retry.bytes, secondTry.bytes)
        client.completeSend(secondTry, true, 110)
    }

    @Test
    fun `duplicate or reordered packets cannot change acknowledgements or receive statistics`() {
        val client = ReliableChannel(ChannelRole.CLIENT, 7)
        val server = ReliableChannel(ChannelRole.SERVER, 7)
        val older = send(client)
        client.enqueueReliable(bytes(1))
        val newer = send(client)
        accept(server, newer, PacketDirection.FROM_CLIENT, 100)
        assertEquals(1, server.dropped)
        for (packet in listOf(older, newer)) {
            assertEquals(ChannelAcceptResult.Rejected(ChannelRejection.OUT_OF_ORDER),
                server.accept(envelope(packet, PacketDirection.FROM_CLIENT), 200))
        }
        assertEquals(2, server.incomingSequence)
        assertEquals(1, server.reliableReceivedFlag)
        assertEquals(100, server.lastReceivedMillis)
        assertEquals(newer.bytes.size, server.lastReceivedSize)
        assertEquals(ChannelAcceptResult.Rejected(ChannelRejection.WRONG_DIRECTION),
            client.accept(envelope(newer, PacketDirection.FROM_CLIENT), 200))
    }

    @Test
    fun `bounded queue rejects overflow without losing already queued bytes`() {
        val channel = ReliableChannel(ChannelRole.CLIENT, 7, ChannelLimits(20, 4, 4))
        assertEquals(ReliableQueueResult.QUEUED, channel.enqueueReliable(bytes(1, 2)))
        assertEquals(ReliableQueueResult.QUEUED, channel.enqueueReliable(bytes(3, 4)))
        assertEquals(ReliableQueueResult.FULL, channel.enqueueReliable(bytes(5)))
        assertEquals(4, channel.pendingReliableBytes)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), body(send(channel), PacketDirection.FROM_CLIENT))
        channel.enqueueReliable(bytes(6))
        channel.discardPending()
        assertEquals(0, channel.pendingReliableBytes)
        assertEquals(4, channel.unacknowledgedReliableBytes)
    }

    @Test
    fun `reliable bytes take priority and exact-fit unreliable bytes are included`() {
        val channel = ReliableChannel(ChannelRole.CLIENT, 7, ChannelLimits(20, 4, 4))
        channel.enqueueReliable(bytes(1, 2, 3, 4))
        val exact = prepare(channel, bytes(5, 6, 7, 8, 9, 10))
        assertEquals(20, exact.bytes.size)
        assertFalse(exact.unreliableDropped)
        channel.completeSend(exact, false, 0)
        val tooLarge = prepare(channel, bytes(5, 6, 7, 8, 9, 10, 11))
        assertEquals(14, tooLarge.bytes.size)
        assertTrue(tooLarge.unreliableDropped)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), body(tooLarge, PacketDirection.FROM_CLIENT))
        channel.completeSend(tooLarge, true, 10)
    }

    @Test
    fun `RTT uses successful completion time and history stays bounded without acknowledgements`() {
        val client = ReliableChannel(ChannelRole.CLIENT, 7, ChannelLimits(rttHistorySize = 4))
        val server = ReliableChannel(ChannelRole.SERVER, 7)
        accept(server, send(client, now = 100), PacketDirection.FROM_CLIENT, 120)
        accept(client, send(server, now = 130), PacketDirection.FROM_SERVER, 160)
        assertEquals(60, client.smoothedPingMillis)
        accept(server, send(client, now = 200), PacketDirection.FROM_CLIENT, 220)
        accept(client, send(server, now = 230), PacketDirection.FROM_SERVER, 300)
        assertEquals(70, client.smoothedPingMillis)
        repeat(20) { send(client, now = 400L + it) }
        assertEquals(4, client.trackedSendCount)
    }

    @Test
    fun `prepared send must complete once and belongs to its channel`() {
        val a = ReliableChannel(ChannelRole.CLIENT, 1)
        val b = ReliableChannel(ChannelRole.CLIENT, 2)
        val packet = prepare(a)
        assertThrows(IllegalStateException::class.java) { prepare(a) }
        assertThrows(IllegalStateException::class.java) { a.enqueueReliable(bytes(1)) }
        assertThrows(IllegalStateException::class.java) { b.completeSend(packet, true, 0) }
        a.completeSend(packet, false, 0)
        assertThrows(IllegalStateException::class.java) { a.completeSend(packet, true, 0) }
        assertEquals(1, a.outgoingSequence)
        assertEquals(1, b.outgoingSequence)
    }

    @Test
    fun `unreliable encoder receives reserved sequence and failure leaves channel reusable`() {
        val client = ReliableChannel(ChannelRole.CLIENT, 7)
        assertThrows(IllegalStateException::class.java) {
            client.prepareSend { _, _ -> client.enqueueReliable(bytes(1)); empty }
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.prepareSend { _, _ -> throw IllegalArgumentException("encoder failed") }
        }
        val packet = assertInstanceOf(PrepareSendResult.Prepared::class.java, client.prepareSend { sequence, capacity ->
            assertEquals(1, sequence)
            val message = MoveMessage(false, 3, usercmd_t(), usercmd_t(), usercmd_t(), sequence)
            val buffer = sizebuf_t(capacity)
            message.writeTo(buffer)
            PacketBytes.copyOf(buffer.data, buffer.cursize)
        })
        client.completeSend(packet, true, 10)
        val legacy = NetworkPacket.fromEnvelope(NetworkAddress.Local(UUID.randomUUID(), LocalSide.FIRST),
            envelope(packet, PacketDirection.FROM_CLIENT))
        assertTrue(((legacy.parseBodyFromClient(1) as MessageDecodeResult.Decoded).messages.single() as MoveMessage).valid)
    }

    private fun bytes(vararg values: Int) = PacketBytes.copyOf(ByteArray(values.size) { values[it].toByte() })
    private fun prepare(channel: ReliableChannel, payload: PacketBytes = empty) =
        assertInstanceOf(PrepareSendResult.Prepared::class.java, channel.prepareSend(payload))
    private fun send(channel: ReliableChannel, payload: PacketBytes = empty, now: Long = 0): PrepareSendResult.Prepared =
        prepare(channel, payload).also { channel.completeSend(it, true, now) }
    private fun envelope(packet: PrepareSendResult.Prepared, direction: PacketDirection) =
        assertInstanceOf(PacketEnvelope.Sequenced::class.java,
            assertInstanceOf(PacketDecodeResult.Decoded::class.java, PacketCodec.decode(packet.bytes, direction)).envelope)
    private fun accept(channel: ReliableChannel, packet: PrepareSendResult.Prepared, direction: PacketDirection, now: Long = 0) {
        assertEquals(ChannelAcceptResult.Accepted, channel.accept(envelope(packet, direction), now))
    }
    private fun body(packet: PrepareSendResult.Prepared, direction: PacketDirection): ByteArray =
        packet.bytes.toByteArray().copyOfRange(direction.sequencedHeaderSize, packet.bytes.size)
}
