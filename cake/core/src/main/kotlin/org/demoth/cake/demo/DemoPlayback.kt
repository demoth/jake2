package org.demoth.cake.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.demo.DemoReader
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.network.messages.server.ServerMessage
import jake2.qcommon.network.protocol.MessageBodyCodec
import jake2.qcommon.network.protocol.MessageDecodeResult
import java.io.Closeable
import java.io.IOException

/** Client-owned playback clock. Neither sockets nor a running game server participate. */
class DemoPlayback(private val reader: DemoReader) : Closeable {
    private data class Pending(val messages: List<ServerMessage>?, val due: Double, val frameTime: Int?)
    private var pending: Pending? = null
    private var closed = false
    private var serverDataSeen = false
    private var originFrame: Int? = null
    private var previousFrame: Int? = null
    private var originDue = 0.0
    private var lastDue = 0.0
    private var elapsed = 0.0
    private var framePresented = false
    private var presentedDue = 0.0
    private var presentedTime = 0

    /** Absolute recorded render time, one snapshot behind the current frame for interpolation. */
    val presentationTimeMillis: Int
        get() = presentedTime - 100 + ((elapsed - presentedDue) * 1000).toInt().coerceIn(0, 100)
    val interpolationSeconds: Float
        get() = (elapsed - presentedDue).toFloat().coerceIn(0f, .1f)

    /**
     * Dispatch whole validated records in order. Returns false on normal completion or explicit close.
     * The record budget bounds bootstrap/empty-record work and catch-up after long application stalls.
     * Returning false from [dispatch] stops immediately, for example after a recorded disconnect.
     */
    fun update(deltaSeconds: Float, dispatch: (List<ServerMessage>) -> Boolean): Boolean {
        if (closed) return false
        require(deltaSeconds.isFinite() && deltaSeconds >= 0f)
        if (framePresented) elapsed += deltaSeconds
        repeat(MAX_RECORDS_PER_UPDATE) {
            val next = pending ?: readPending().also { pending = it }
            if (next.due > elapsed + 0.000001) return true
            pending = null
            val messages = next.messages
            if (messages == null) {
                close()
                return false
            }
            if (!dispatch(messages) || closed) return false
            if (next.frameTime != null) {
                framePresented = true
                presentedTime = next.frameTime
                presentedDue = next.due
            }
        }
        return true
    }

    private fun readPending(): Pending {
        val record = reader.read()
        if (record == DemoReader.Record.End) {
            if (!serverDataSeen || !framePresented) throw IOException("Demo contains no playable world frames")
            return Pending(null, lastDue + .1, null)
        }
        val body = (record as DemoReader.Record.Body).bytes
        val messages = when (val result = MessageBodyCodec.decodeServerBody(body)) {
            is MessageDecodeResult.Decoded -> result.messages
            is MessageDecodeResult.Rejected -> throw IOException("Invalid demo message at byte ${reader.offset - body.size + result.offset}: ${result.detail}")
        }
        if (messages.any { it is ServerDataMessage }) {
            for (data in messages.filterIsInstance<ServerDataMessage>()) {
                if (data.protocol != Defines.PROTOCOL_VERSION)
                    throw IOException("Unsupported demo protocol ${data.protocol}; expected ${Defines.PROTOCOL_VERSION}")
                if (data.playerNumber < 0) throw IOException("Cinematic demos are not supported")
            }
            serverDataSeen = true
            originFrame = null
            previousFrame = null
            originDue = if (framePresented) lastDue + .1 else 0.0
            lastDue = originDue
        }
        if (!serverDataSeen && messages.isNotEmpty()) throw IOException("Demo is missing initial server data")
        val frames = messages.filterIsInstance<FrameHeaderMessage>()
        if (frames.size > 1) throw IOException("Multiple snapshots in one demo record")
        val frame = frames.singleOrNull()
        if (frame != null) {
            if (frame.frameNumber < 0 || frame.frameNumber > Int.MAX_VALUE / 100)
                throw IOException("Invalid demo frame number ${frame.frameNumber}")
            if (previousFrame != null && frame.frameNumber <= previousFrame!!)
                throw IOException("Demo frame numbers do not advance")
            if (originFrame == null) originFrame = frame.frameNumber
            lastDue = originDue + (frame.frameNumber.toLong() - originFrame!!) / 10.0
            previousFrame = frame.frameNumber
        }
        return Pending(messages, lastDue, frame?.frameNumber?.times(100))
    }

    override fun close() {
        if (!closed) {
            closed = true
            pending = null
            reader.close()
        }
    }

    companion object {
        const val MAX_RECORDS_PER_UPDATE = 64
    }
}
