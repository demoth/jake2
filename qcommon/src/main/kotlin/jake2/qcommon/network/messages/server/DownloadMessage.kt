package jake2.qcommon.network.messages.server

import jake2.qcommon.network.transport.PacketBytes

/** Transmits chunks of media (maps, skins, sounds, etc). Chunk length is limited by a signed short (32767 bytes). */
sealed class DownloadMessage : ServerMessage(ServerMessageType.svc_download) {
    data class Chunk(val data: PacketBytes, val percentage: Int) : DownloadMessage() {
        init {
            require(data.size <= Short.MAX_VALUE) { "Download chunk exceeds signed short length" }
            require(percentage in 0..255) { "Download percentage exceeds unsigned byte range" }
        }
    }

    /** Signed length -1 is failure; retain the legacy -32640 failure marker for network compatibility. */
    data class Failure(val marker: Int = -1, val percentage: Int = 0) : DownloadMessage() {
        init {
            require(marker == -1 || marker == LEGACY_FAILURE_MARKER) { "Unknown download failure marker" }
            require(percentage in 0..255) { "Download percentage exceeds unsigned byte range" }
        }
    }

    companion object {
        const val LEGACY_FAILURE_MARKER = -32640
        @JvmStatic fun chunk(data: ByteArray, percentage: Int): Chunk = Chunk(PacketBytes.copyOf(data), percentage)
    }
}
