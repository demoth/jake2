package org.demoth.cake.stages.ingame.effects

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SustainSchedulerTest {
    @Test fun `bursts start immediately follow cadence and stop at expiry`() {
        val scheduler = SustainScheduler<String>()
        val bursts = mutableListOf<String>()
        scheduler.start(1, "steam", 1000, 300, 100)
        scheduler.update(1000, bursts::add)
        repeat(20) { scheduler.update(1000, bursts::add) } // A paused clock cannot repeat emission.
        scheduler.update(1099, bursts::add)
        assertEquals(listOf("steam"), bursts)
        scheduler.update(1100, bursts::add)
        scheduler.update(1200, bursts::add)
        assertEquals(3, bursts.size)
        scheduler.update(1300, bursts::add)
        assertEquals(3, bursts.size)
        assertEquals(0, scheduler.activeCount)
    }

    @Test fun `late updates emit once skip missed ticks and retain cadence`() {
        val scheduler = SustainScheduler<Int>()
        val bursts = mutableListOf<Int>()
        scheduler.start(1, 7, 0, 20000, 100)
        scheduler.update(0, bursts::add)
        scheduler.update(10025, bursts::add)
        assertEquals(listOf(7, 7), bursts)
        scheduler.update(10099, bursts::add)
        assertEquals(2, bursts.size)
        scheduler.update(10100, bursts::add)
        assertEquals(3, bursts.size)
        scheduler.update(30000, bursts::add)
        assertEquals(3, bursts.size)
        assertEquals(0, scheduler.activeCount)
    }

    @Test fun `reusing an identifier replaces state and refreshes duration even at capacity`() {
        val scheduler = SustainScheduler<String>(capacity = 1)
        val bursts = mutableListOf<String>()
        assertTrue(scheduler.start(1, "old", 0, 200, 100))
        scheduler.update(0, bursts::add)
        assertTrue(scheduler.start(1, "new", 150, 300, 100))
        assertFalse(scheduler.start(2, "overflow", 150, 1000, 100))
        scheduler.update(150, bursts::add)
        scheduler.update(300, bursts::add)
        assertEquals(listOf("old", "new", "new"), bursts)
        assertEquals(1, scheduler.activeCount)
        scheduler.update(450, bursts::add)
        assertEquals(0, scheduler.activeCount)
    }

    @Test fun `expired and cancelled entries free slots and clear releases all scheduled emissions`() {
        val scheduler = SustainScheduler<Int>(capacity = 1)
        val bursts = mutableListOf<Int>()
        scheduler.start(1, 1, 0, 100, 100)
        assertTrue(scheduler.start(2, 2, 100, 500, 100)) // Admission prunes expired state without needing update.
        assertTrue(scheduler.start(2, 2, 100, 0, 100))
        assertEquals(0, scheduler.activeCount)
        scheduler.start(3, 3, 100, 500, 100)
        scheduler.start(3, 3, 100, -1, 100)
        assertEquals(0, scheduler.activeCount)
        scheduler.start(4, 4, 100, 500, 100)
        scheduler.clear()
        scheduler.update(200, bursts::add)
        assertTrue(bursts.isEmpty())
    }

    @Test fun `long protocol durations do not overflow into immediate expiry`() {
        val scheduler = SustainScheduler<Int>()
        var emitted = 0
        scheduler.start(1, 1, 1000, Int.MAX_VALUE, 100)
        scheduler.update(2000) { emitted++ }
        assertEquals(1, emitted)
        assertEquals(1, scheduler.activeCount)
    }
}
