package jake2.qcommon.network.protocol

import jake2.qcommon.Defines
import jake2.qcommon.network.transport.PacketBytes

enum class ChannelRole(val headerSize: Int) { CLIENT(10), SERVER(8) }

data class ChannelLimits @JvmOverloads constructor(
    val maxPacketSize: Int = Defines.MAX_MSGLEN,
    val maxReliableBytes: Int = Defines.MAX_MSGLEN - 16,
    val rttHistorySize: Int = 64,
) {
    init {
        require(maxPacketSize in 10..Defines.MAX_MSGLEN)
        require(maxReliableBytes in 1..maxPacketSize - 10)
        require(rttHistorySize > 0)
    }
}

enum class ReliableQueueResult { QUEUED, FULL }
enum class ChannelRejection { OUT_OF_ORDER, WRONG_DIRECTION }

sealed interface ChannelAcceptResult {
    data object Accepted : ChannelAcceptResult
    data class Rejected(val reason: ChannelRejection) : ChannelAcceptResult
}

/** Called with the reserved sequence (needed for movement checksums) and remaining packet space.
 * Return null to omit an unreliable batch that cannot fit; an empty payload means no batch.
 */
fun interface UnreliableEncoder {
    fun encode(sequence: Int, capacity: Int): PacketBytes?
}

sealed interface PrepareSendResult {
    data object SequenceExhausted : PrepareSendResult

    class Prepared internal constructor(
        val sequence: Int,
        val bytes: PacketBytes,
        val containsReliable: Boolean,
        val unreliableDropped: Boolean,
        internal val promotedReliable: PacketBytes?,
        internal val reliableFlag: Int,
    ) : PrepareSendResult
}

/** Quake II reliable channel state, confined to one owner thread.
 * Times are elapsed monotonic milliseconds supplied by the caller. Every prepared
 * transmission must be completed before further channel operations, even on I/O failure.
 */
class ReliableChannel @JvmOverloads constructor(
    private val role: ChannelRole,
    private val qport: Int,
    private val limits: ChannelLimits = ChannelLimits(),
) {
    init { require(qport in 0..65535) }

    var outgoingSequence = 1
        private set
    var incomingSequence = 0
        private set
    var incomingAcknowledged = 0
        private set
    var incomingReliableAcknowledged = 0
        private set
    var reliableReceivedFlag = 0
        private set
    var reliableSentFlag = 0
        private set
    var lastReliableSequence = 0
        private set
    var dropped = 0
        private set
    var lastReceivedMillis = 0L
        private set
    var lastSentMillis = 0L
        private set
    var lastReceivedSize = 0
        private set
    var lastSentSize = 0
        private set
    var smoothedPingMillis = 0
        private set

    private val pending = ArrayDeque<PacketBytes>()
    var pendingReliableBytes = 0
        private set
    private var unacknowledged: PacketBytes? = null
    private var prepared: PrepareSendResult.Prepared? = null
    private var encoding = false
    private val sendTimes = LinkedHashMap<Int, Long>()

    val unacknowledgedReliableBytes: Int get() = unacknowledged?.size ?: 0
    val trackedSendCount: Int get() = sendTimes.size
    val canSendReliable: Boolean get() = unacknowledged == null
    val needsReliable: Boolean get() =
        (unacknowledged == null && pendingReliableBytes > 0) ||
            (unacknowledged != null && incomingAcknowledged > lastReliableSequence &&
                incomingReliableAcknowledged != reliableSentFlag)

    fun enqueueReliable(payload: PacketBytes): ReliableQueueResult {
        checkIdle()
        if (payload.size > limits.maxReliableBytes - pendingReliableBytes) return ReliableQueueResult.FULL
        if (payload.size > 0) {
            pending.addLast(payload)
            pendingReliableBytes += payload.size
        }
        return ReliableQueueResult.QUEUED
    }

    /** Cancel only unsent work; already-transmitted reliable bytes still require acknowledgement. */
    fun discardPending() {
        checkIdle()
        pending.clear()
        pendingReliableBytes = 0
    }

    fun accept(packet: PacketEnvelope.Sequenced, nowMillis: Long): ChannelAcceptResult {
        checkIdle()
        if ((packet.qport == null) != (role == ChannelRole.CLIENT)) {
            return ChannelAcceptResult.Rejected(ChannelRejection.WRONG_DIRECTION)
        }
        val sequence = packet.sequence and Int.MAX_VALUE
        val acknowledgement = packet.sequenceAck and Int.MAX_VALUE
        if (sequence <= incomingSequence) return ChannelAcceptResult.Rejected(ChannelRejection.OUT_OF_ORDER)

        val reliableAck = packet.sequenceAck ushr 31
        dropped = sequence - incomingSequence - 1
        // Preserve the protocol's one-bit reliable acknowledgement rule.
        if (reliableAck == reliableSentFlag) unacknowledged = null
        incomingSequence = sequence
        incomingAcknowledged = acknowledgement
        incomingReliableAcknowledged = reliableAck
        if (packet.sequence ushr 31 != 0) reliableReceivedFlag = reliableReceivedFlag xor 1
        lastReceivedMillis = nowMillis
        lastReceivedSize = packet.bytes.size

        sendTimes.remove(acknowledgement)?.let { sentAt ->
            val measured = (nowMillis - sentAt).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            smoothedPingMillis = if (smoothedPingMillis == 0) measured
                else ((smoothedPingMillis.toLong() * 3 + measured) / 4).toInt()
            sendTimes.keys.removeIf { it < acknowledgement - 32 }
        }
        return ChannelAcceptResult.Accepted
    }

    fun prepareSend(unreliable: PacketBytes): PrepareSendResult =
        prepareSend { _, capacity -> unreliable.takeIf { it.size <= capacity } }

    fun prepareSend(encoder: UnreliableEncoder): PrepareSendResult {
        checkIdle()
        encoding = true
        try {
            return preparePacket(encoder)
        } finally {
            encoding = false
        }
    }

    private fun preparePacket(encoder: UnreliableEncoder): PrepareSendResult {
        // Do not wrap into an old sequence or emit the connectionless marker with a reliable flag.
        if (outgoingSequence == Int.MAX_VALUE) return PrepareSendResult.SequenceExhausted
        val promoted = if (canSendReliable && pendingReliableBytes > 0) join(pending, pendingReliableBytes) else null
        val containsReliable = needsReliable
        val reliable = if (containsReliable) promoted ?: unacknowledged else null
        val reliableFlag = if (promoted != null) reliableSentFlag xor 1 else reliableSentFlag
        val capacity = limits.maxPacketSize - role.headerSize - (reliable?.size ?: 0)
        val unreliable = encoder.encode(outgoingSequence, capacity)
        require(unreliable == null || unreliable.size <= capacity) { "Unreliable encoder exceeded its capacity" }
        val body = join(listOfNotNull(reliable, unreliable), (reliable?.size ?: 0) + (unreliable?.size ?: 0))
        val bytes = PacketCodec.encodeSequenced(
            outgoingSequence or (if (containsReliable) Int.MIN_VALUE else 0),
            incomingSequence or (reliableReceivedFlag shl 31),
            if (role == ChannelRole.CLIENT) qport else null,
            body,
        )
        return PrepareSendResult.Prepared(outgoingSequence, bytes, containsReliable, unreliable == null,
            promoted, reliableFlag).also { prepared = it }
    }

    /** sent=false covers WouldBlock, transport failure, or cancellation before sending. */
    fun completeSend(transmission: PrepareSendResult.Prepared, sent: Boolean, nowMillis: Long) {
        check(prepared === transmission) { "Transmission is not this channel's active send" }
        prepared = null
        if (!sent) return

        transmission.promotedReliable?.let {
            unacknowledged = it
            reliableSentFlag = transmission.reliableFlag
            discardPending()
        }
        outgoingSequence++
        // Legacy retransmit threshold is the sequence *after* the reliable transmission.
        if (transmission.containsReliable) lastReliableSequence = outgoingSequence
        lastSentMillis = nowMillis
        lastSentSize = transmission.bytes.size
        sendTimes[transmission.sequence] = nowMillis
        while (sendTimes.size > limits.rttHistorySize) sendTimes.remove(sendTimes.keys.first())
    }

    private fun checkIdle() = check(prepared == null && !encoding) {
        "Finish encoding and complete the prepared send before changing channel state"
    }

    private fun join(parts: Collection<PacketBytes>, size: Int): PacketBytes {
        val bytes = ByteArray(size)
        var offset = 0
        for (part in parts) {
            val data = part.toByteArray()
            data.copyInto(bytes, offset)
            offset += data.size
        }
        return PacketBytes.copyOf(bytes)
    }
}
