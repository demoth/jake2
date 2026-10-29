package jake2.qcommon.network.protocol

import jake2.qcommon.network.transport.PacketBytes

enum class PacketDirection(val sequencedHeaderSize: Int) {
    FROM_CLIENT(10),
    FROM_SERVER(8),
}

/** Validated envelope retaining the original bytes for the existing message-body reader. */
sealed interface PacketEnvelope {
    val bytes: PacketBytes

    @ConsistentCopyVisibility
    data class Sequenced internal constructor(
        override val bytes: PacketBytes,
        /** Wire values: the high bits still contain the reliable payload/ack flags. */
        val sequence: Int,
        val sequenceAck: Int,
        /** Only client-to-server packets carry a qport. */
        val qport: Int?,
    ) : PacketEnvelope {
        val headerSize: Int get() = if (qport == null) 8 else 10
    }

    @ConsistentCopyVisibility
    data class Connectionless internal constructor(
        override val bytes: PacketBytes,
        val commandLine: String,
        /** Remaining text for server info/print replies; absent for other commands. */
        val parameters: String?,
    ) : PacketEnvelope
}

sealed interface PacketDecodeResult {
    data class Decoded(val envelope: PacketEnvelope) : PacketDecodeResult
    data class Rejected(val reason: PacketRejection) : PacketDecodeResult
}

enum class PacketRejection {
    OVERSIZED,
    TRUNCATED_HEADER,
    EMPTY_CONNECTIONLESS_COMMAND,
}
