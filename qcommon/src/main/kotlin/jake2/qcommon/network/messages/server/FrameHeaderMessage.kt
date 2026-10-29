package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.MAX_MAP_AREAS
import jake2.qcommon.network.transport.PacketBytes

/**
 * Generic information about a frame, sent with [PlayerInfoMessage] and [PacketEntitiesMessage] every server frame.
 * @property lastFrame what we are delta'ing from.
 * @property suppressCount rate dropped packets.
 * @property areaBits owned visibility bytes; their size is the transmitted prefix length, without padding.
 */
data class FrameHeaderMessage(
    val frameNumber: Int,
    val lastFrame: Int,
    val suppressCount: Int,
    val areaBits: PacketBytes,
) : ServerMessage(ServerMessageType.svc_frame) {
    init { require(areaBits.size <= MAX_MAP_AREAS / 8) { "Area bits exceed protocol limit" } }

    constructor(frameNumber: Int, lastFrame: Int, suppressCount: Int, areaBitsLength: Int, areaBits: ByteArray) :
        this(frameNumber, lastFrame, suppressCount, PacketBytes.copyOf(areaBits, areaBitsLength))
}
