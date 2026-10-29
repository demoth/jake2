package org.demoth.cake.stages.ingame.effects

import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.TrailTEMessage
import kotlin.random.Random

internal data class LaserBeam(val origin: Vector3f, val destination: Vector3f, val paletteIndex: Int) {
    val diameter: Float get() = 4f
    val alpha: Float get() = .3f
}

/** CL_ParseLaser / CL_AddLasers: short-lived palette beams, submitted through the shared RF_BEAM renderer. */
internal class LaserEffects(private val random: Random = Random.Default, private val capacity: Int = 64) {
    init { require(capacity > 0) }
    private data class Entry(val beam: LaserBeam, val expiresAt: Long)
    private val entries = ArrayList<Entry>()
    val activeCount: Int get() = entries.size

    fun process(message: TrailTEMessage, nowMs: Int): Boolean {
        if (message.style != Defines.TE_BFG_LASER) return false
        expire(nowMs)
        if (entries.size >= capacity || message.position.distance(message.destination) < .001f) return true
        entries += Entry(LaserBeam(message.position, message.destination, 0xD0 + random.nextInt(4)), nowMs.toLong() + 100)
        return true
    }

    fun submit(nowMs: Int, draw: (LaserBeam) -> Unit) {
        expire(nowMs)
        entries.forEach { draw(it.beam) }
    }

    fun clear() = entries.clear()
    private fun expire(nowMs: Int) = entries.removeAll { nowMs.toLong() >= it.expiresAt }
}
