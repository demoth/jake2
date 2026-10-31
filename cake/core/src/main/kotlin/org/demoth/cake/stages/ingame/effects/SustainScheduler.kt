package org.demoth.cake.stages.ingame.effects

/**
 * Owned scheduling for repeated effect bursts (CL_ProcessSustain), independent of graphics and wall time.
 * Identifiers replace existing effects. Each update emits at most once per active effect; missed ticks
 * are skipped rather than replayed after a stall. The expiry boundary is exclusive.
 */
internal class SustainScheduler<T>(private val capacity: Int = 32) {
    init { require(capacity > 0) }

    private data class Entry<T>(val value: T, val expiresAt: Long, val intervalMillis: Int, var nextAt: Long)
    private val entries = LinkedHashMap<Int, Entry<T>>()
    val activeCount: Int get() = entries.size

    /** A nonpositive duration cancels the identifier. Expired entries never consume admission capacity. */
    fun start(id: Int, value: T, nowMs: Int, durationMillis: Int, intervalMillis: Int): Boolean {
        require(intervalMillis > 0)
        entries.values.removeAll { nowMs.toLong() >= it.expiresAt }
        if (durationMillis <= 0) {
            entries.remove(id)
            return true
        }
        if (id !in entries && entries.size >= capacity) return false
        entries[id] = Entry(value, nowMs.toLong() + durationMillis, intervalMillis, nowMs.toLong())
        return true
    }

    fun update(nowMs: Int, emit: (T) -> Unit) {
        val now = nowMs.toLong()
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now >= entry.expiresAt) {
                iterator.remove()
            } else if (now >= entry.nextAt) {
                // Keep the emission cadence while bounding work to one burst after a missed interval.
                entry.nextAt += ((now - entry.nextAt) / entry.intervalMillis + 1) * entry.intervalMillis
                emit(entry.value)
            }
        }
    }

    fun clear() = entries.clear()
}
