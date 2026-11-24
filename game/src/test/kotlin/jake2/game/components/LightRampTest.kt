package jake2.game.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LightRampTest {
    @Test
    fun styleUsesElapsedLevelTime() {
        val ramp = LightRamp(0, 10, duration = 2f, targetTime = 12f)
        assertEquals('a', ramp.styleAt(10f))
        assertEquals('f', ramp.styleAt(11f))
        assertEquals('k', ramp.styleAt(12.1f))
    }

    @Test
    fun toggleSwapsTheEndpoints() {
        val ramp = LightRamp(0, 25, duration = 5f, targetTime = 15f)
        ramp.toggle()
        assertEquals('z', ramp.styleAt(10f))
        assertEquals('u', ramp.styleAt(11f))
        assertEquals('a', ramp.styleAt(15f))
    }
}
