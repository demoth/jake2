package org.demoth.cake.hosting

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HostFrameClockTest {
    @Test
    fun `high frame rates retain fractional milliseconds`() {
        val clock = HostFrameClock()
        val elapsed = (1..1440).sumOf { clock.advance(1f / 1440) }
        assertTrue(elapsed in 999..1000, "elapsed=$elapsed")
    }

    @Test
    fun `long frames are bounded without later catchup debt`() {
        val clock = HostFrameClock()
        assertEquals(100, clock.advance(10f))
        assertEquals(10, clock.advance(0.0101f))
        assertEquals(0, clock.advance(Float.NaN))
        assertEquals(0, clock.advance(-1f))
    }

    @Test
    fun `fixedtime takes precedence and timescale preserves fractional time`() {
        val clock = HostFrameClock()
        assertEquals(20, clock.advance(0.01f, fixedTime = 20, timeScale = 3f))
        assertEquals(100, clock.advance(0.01f, fixedTime = 1000))
        assertEquals(5, clock.advance(0.0101f, timeScale = 0.5f))
        clock.reset()
        assertEquals(0, clock.advance(0.0006f))
        clock.reset()
        assertEquals(0, clock.advance(0.0006f))
    }
}
