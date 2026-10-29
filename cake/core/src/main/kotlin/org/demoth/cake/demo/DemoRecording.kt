package org.demoth.cake.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.demo.DemoWriter
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.protocol.MessageBodyCodec
import jake2.qcommon.network.protocol.MessageDecodeResult
import jake2.qcommon.network.protocol.PacketEnvelope
import jake2.qcommon.network.transport.PacketBytes
import java.io.Closeable
import java.io.IOException

/**
 * Owns recording readiness and output, independently of sockets, a local host, or presentation.
 * [bootstrap] snapshots the latest client startup state when a full frame is ready, including
 * config/baseline changes received while waiting. The live client supplies reconstruction validity.
 */
class DemoRecording(private val writer: DemoWriter, private val bootstrap: () -> DemoBootstrap) : Closeable {
    private var waiting = true
    private var closed = false
    private val recordedFrames = IntArray(Defines.UPDATE_BACKUP) { -1 }

    /** The command sender must request a full snapshot without invalidating the displayed frame. */
    val needsFullSnapshot: Boolean get() = !closed && waiting

    /** Live server packets retain their exact body; channel headers are never part of .dm2 records. */
    fun capture(packet: PacketEnvelope.Sequenced, snapshotAccepted: Boolean) {
        require(packet.qport == null) { "Recording expects a server packet" }
        capture(PacketBytes.copyOfRange(packet.bytes.toByteArray(), packet.headerSize,
            packet.bytes.size - packet.headerSize), snapshotAccepted)
    }

    /**
     * Called after the client processes an accepted packet. [snapshotAccepted] must indicate whether
     * its snapshot was reconstructed successfully; structural decoding alone cannot establish that.
     * Whole recorded bodies are preserved, including messages before/after their snapshot.
     */
    fun capture(body: PacketBytes, snapshotAccepted: Boolean) {
        check(!closed) { "Demo recording is closed" }
        try {
            val messages = when (val decoded = MessageBodyCodec.decodeServerBody(body)) {
                is MessageDecodeResult.Decoded -> decoded.messages
                is MessageDecodeResult.Rejected -> throw IOException("Invalid recording payload: ${decoded.detail}")
            }
            if (messages.any { it is ServerDataMessage }) {
                // Map/session reset: capture fresh startup state with that map's first full snapshot.
                waitForFullSnapshot()
                return
            }
            val frames = messages.filterIsInstance<FrameHeaderMessage>()
            val frame = frames.singleOrNull()
            val player = messages.indexOfFirst { it is PlayerInfoMessage }
            val entities = messages.indexOfFirst { it is PacketEntitiesMessage }
            if (frames.isNotEmpty() || player >= 0 || entities >= 0) {
                val complete = frame != null && messages.indexOf(frame) < player && player < entities &&
                    messages.count { it is PlayerInfoMessage } == 1 && messages.count { it is PacketEntitiesMessage } == 1
                if (!complete || !snapshotAccepted) {
                    waitForFullSnapshot()
                    return
                }
                // A live-valid delta may still depend on a frame received before recording began.
                if (frame.lastFrame > 0 &&
                    recordedFrames[frame.lastFrame and (recordedFrames.size - 1)] != frame.lastFrame) {
                    waitForFullSnapshot()
                    return
                }
            }
            if (waiting) {
                if (frame == null || frame.lastFrame > 0) return
                bootstrap().writeTo(writer)
                waiting = false
            }
            writer.write(body)
            if (frame != null) recordedFrames[frame.frameNumber and (recordedFrames.size - 1)] = frame.frameNumber
        } catch (failure: Exception) {
            try { close() } catch (closeFailure: Exception) {
                if (closeFailure !== failure) failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    private fun waitForFullSnapshot() {
        waiting = true
        recordedFrames.fill(-1)
    }

    override fun close() {
        if (!closed) {
            closed = true
            writer.close()
        }
    }
}
