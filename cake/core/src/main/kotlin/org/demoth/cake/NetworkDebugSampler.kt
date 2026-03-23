package org.demoth.cake

import java.util.ArrayDeque

data class NetworkDebugSnapshot(
    val inBytesPerSec: Int = 0,
    val outBytesPerSec: Int = 0,
    val pingMs: Int = 0,
    val downloadKilobytesPerSec: Int = 0,
)

class NetworkDebugSampler {
    private data class TimedBytes(val timeMs: Int, val bytes: Int)

    private val inbound = ArrayDeque<TimedBytes>()
    private val outbound = ArrayDeque<TimedBytes>()
    private val download = ArrayDeque<TimedBytes>()

    fun recordInbound(bytes: Int, currentTimeMs: Int) {
        record(inbound, bytes, currentTimeMs)
    }

    fun recordOutbound(bytes: Int, currentTimeMs: Int) {
        record(outbound, bytes, currentTimeMs)
    }

    fun recordDownload(bytes: Int, currentTimeMs: Int) {
        record(download, bytes, currentTimeMs)
    }

    fun snapshot(currentTimeMs: Int, pingMs: Int): NetworkDebugSnapshot {
        val inBytes = rollingBytes(inbound, currentTimeMs)
        val outBytes = rollingBytes(outbound, currentTimeMs)
        val downloadBytes = rollingBytes(download, currentTimeMs)
        return NetworkDebugSnapshot(
            inBytesPerSec = inBytes,
            outBytesPerSec = outBytes,
            pingMs = pingMs.coerceAtLeast(0),
            downloadKilobytesPerSec = downloadBytes / 1024,
        )
    }

    fun clear() {
        inbound.clear()
        outbound.clear()
        download.clear()
    }

    private fun record(buffer: ArrayDeque<TimedBytes>, bytes: Int, currentTimeMs: Int) {
        if (bytes <= 0) return
        buffer.addLast(TimedBytes(currentTimeMs, bytes))
        prune(buffer, currentTimeMs)
    }

    private fun rollingBytes(buffer: ArrayDeque<TimedBytes>, currentTimeMs: Int): Int {
        prune(buffer, currentTimeMs)
        return buffer.sumOf { it.bytes }
    }

    private fun prune(buffer: ArrayDeque<TimedBytes>, currentTimeMs: Int) {
        while (buffer.isNotEmpty() && currentTimeMs - buffer.first().timeMs > WINDOW_MS) {
            buffer.removeFirst()
        }
    }

    companion object {
        private const val WINDOW_MS = 1000
    }
}
