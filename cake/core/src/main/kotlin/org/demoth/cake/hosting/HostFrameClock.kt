package org.demoth.cake.hosting

/**
 * Server elapsed time, independent of rendering. Keep sub-millisecond remainders at high FPS.
 * Discard time beyond 100 ms per frame rather than accumulating simulation debt after a stall.
 * fixedtime overrides timescale, as in MainCommon; both apply only to the local simulation.
 */
class HostFrameClock {
    private var remainder = 0.0

    fun advance(deltaSeconds: Float, fixedTime: Int = 0, timeScale: Float = 0f): Long {
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f) return 0
        val elapsed = when {
            fixedTime > 0 -> fixedTime.toDouble()
            timeScale > 0f -> deltaSeconds.toDouble() * 1000.0 * timeScale
            else -> deltaSeconds.toDouble() * 1000.0
        }.coerceAtMost(100.0)
        val total = remainder + elapsed
        val millis = total.toLong()
        remainder = total - millis
        return millis
    }

    fun reset() { remainder = 0.0 }
}
