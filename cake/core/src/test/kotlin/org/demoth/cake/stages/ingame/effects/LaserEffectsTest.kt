package org.demoth.cake.stages.ingame.effects

import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.messages.server.TrailTEMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class LaserEffectsTest {
    private fun message(end: Vector3f = Vector3f(100f, 0f, 0f)) = TrailTEMessage(Defines.TE_BFG_LASER, Vector3f.zero, end)
    private fun beams(effects: LaserEffects, now: Int): List<LaserBeam> = buildList { effects.submit(now) { add(it) } }

    @Test fun `BFG lasers keep their endpoints palette diameter and alpha until their hundred millisecond expiry`() {
        val effects = LaserEffects(Random(7))
        effects.process(message(), 0)
        val first = beams(effects, 0).single()
        assertEquals(Vector3f.zero, first.origin)
        assertEquals(Vector3f(100f, 0f, 0f), first.destination)
        assertTrue(first.paletteIndex in 0xD0..0xD3)
        assertEquals(4f, first.diameter)
        assertEquals(.3f, first.alpha)
        repeat(20) { assertEquals(listOf(first), beams(effects, 50)) }
        assertEquals(listOf(first), beams(effects, 99))
        assertTrue(beams(effects, 100).isEmpty())
    }

    @Test fun `overlapping pulses are independent bounded and expired capacity is reusable`() {
        val effects = LaserEffects(Random(7), capacity = 2)
        effects.process(message(), 0)
        effects.process(message(Vector3f.unitZ), 50)
        effects.process(message(Vector3f.unitY), 50)
        assertEquals(2, effects.activeCount)
        assertEquals(2, beams(effects, 50).size)
        effects.process(message(Vector3f.unitY), 100)
        assertEquals(2, effects.activeCount)
        assertEquals(setOf(Vector3f.unitZ, Vector3f.unitY), beams(effects, 100).map { it.destination }.toSet())
        effects.clear()
        assertTrue(beams(effects, 100).isEmpty())
    }

    @Test fun `zero length and unrelated trail messages create no laser state`() {
        val effects = LaserEffects()
        assertTrue(effects.process(message(Vector3f.zero), 0))
        assertFalse(effects.process(message().copy(style = Defines.TE_RAILTRAIL), 0))
        assertEquals(0, effects.activeCount)
    }
}
