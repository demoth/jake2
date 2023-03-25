package jake2.game.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LightRampTest {
    @Test
    fun testUpdateInterpolation() {
        val lightRamp = LightRamp(0, 10, 1f)
        val interpolatedValue1 = lightRamp.update(0.1f)
        assertEquals(1, interpolatedValue1)
        val interpolatedValue2 = lightRamp.update(0.4f)
        assertEquals(5, interpolatedValue2)
    }

    @Test
    fun testUpdateFractionClamping() {
        val lightRamp = LightRamp(0, 10, 1f)
        val maxValue = lightRamp.update(1.5f)
        assertEquals(1f, lightRamp.fraction)
        assertEquals(10, maxValue)
    }

    @Test
    fun testToggle() {
        val lightRamp = LightRamp(0, 25, 5f)
        lightRamp.toggle(10f)
        assertEquals(25, lightRamp.start)
        assertEquals(0, lightRamp.end)
        assertEquals(0f, lightRamp.fraction)
        assertEquals(15f, lightRamp.targetTime)
        val newValue = lightRamp.update(1f)
        assertEquals(20, newValue)
    }
}
