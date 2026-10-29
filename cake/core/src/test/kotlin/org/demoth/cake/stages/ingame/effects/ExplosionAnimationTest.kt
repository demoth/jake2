package org.demoth.cake.stages.ingame.effects

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExplosionAnimationTest {
    @Test fun `model frame pairs interpolate relative to the selected base and expire at the last transition`() {
        val animation = ExplosionAnimation(ExplosionType.POLY, 1000, 100, 30, 19)
        val first = animation.sample(1000)!!
        assertEquals(30, first.previousFrame); assertEquals(31, first.currentFrame)
        assertEquals(.5f, animation.sample(1050)!!.interpolation)
        assertEquals(31, animation.sample(1100)!!.previousFrame)
        assertNotNull(animation.sample(2799))
        assertNull(animation.sample(2800))
    }

    @Test fun `polygon explosion alpha skin and translucency follow the animation together`() {
        val animation = ExplosionAnimation(ExplosionType.POLY, 0, 100, 0, 15)
        assertEquals(0, animation.sample(0)!!.skin)
        assertEquals(.5f, animation.sample(800)!!.alpha)
        assertEquals(4, animation.sample(800)!!.skin)
        assertFalse(animation.sample(800)!!.translucent)
        assertEquals(5, animation.sample(1000)!!.skin)
        assertTrue(animation.sample(1000)!!.translucent)
        assertEquals(6, animation.sample(1300)!!.skin)
        assertNull(animation.sample(1400))
    }

    @Test fun `misc effects fade smoothly and preserve their supplied skin and translucency`() {
        val animation = ExplosionAnimation(ExplosionType.MISC, 0, 100, 0, 4, true, 2)
        assertEquals(.5f, animation.sample(150)!!.alpha)
        assertEquals(2, animation.sample(150)!!.skin)
        assertTrue(animation.sample(150)!!.translucent)
        assertNull(animation.sample(300))
    }

    @Test fun `flash muzzle flash and second polygon style retain their distinct lifetime and alpha rules`() {
        val flash = ExplosionAnimation(ExplosionType.FLASH, 0, 100, 0, 2)
        assertEquals(1f, flash.sample(99)!!.alpha)
        assertNull(flash.sample(100))
        val muzzle = ExplosionAnimation(ExplosionType.MFLASH, 0, 100, 0, 4)
        assertEquals(1f, muzzle.sample(200)!!.alpha)
        assertNull(muzzle.sample(300))
        val second = ExplosionAnimation(ExplosionType.POLY2, 0, 100, 0, 5)
        assertEquals(.6f, second.sample(200)!!.alpha)
        assertEquals(0, second.sample(200)!!.skin)
        assertTrue(second.sample(200)!!.translucent)
    }

    @Test fun `paused clocks and equivalent update paths yield the same frame without advancing state`() {
        val animation = ExplosionAnimation(ExplosionType.MISC, 0, 100, 0, 4)
        val frame = animation.sample(150)
        repeat(100) { assertEquals(frame, animation.sample(150)) }
        assertEquals(animation.sample(0), animation.sample(-100))
    }
}
