package org.demoth.cake.viewer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpritePlaybackTest {
    @Test
    fun playsOnceAtTenFpsHoldsLastFrameAndCanRestart() {
        val playback = SpritePlayback(4)
        assertFalse(playback.playing)
        playback.play()
        playback.update(0.11f)
        assertEquals(1, playback.frameIndex)
        playback.update(1f)
        assertEquals(3, playback.frameIndex)
        assertFalse(playback.playing)
        playback.update(1f)
        assertEquals(3, playback.frameIndex)
        playback.play()
        assertEquals(0, playback.frameIndex)
        assertTrue(playback.playing)
    }

    @Test
    fun pausePreservesFrameAndFractionalTimeWhileStopResetsBoth() {
        val playback = SpritePlayback(4)
        playback.play()
        playback.update(0.16f)
        playback.pause()
        playback.update(5f)
        assertEquals(1, playback.frameIndex)
        playback.toggle()
        playback.update(0.06f)
        assertEquals(2, playback.frameIndex)
        playback.stop()
        assertEquals(0, playback.frameIndex)
        assertFalse(playback.playing)
        playback.play()
        playback.update(0.05f)
        assertEquals(0, playback.frameIndex)
    }

    @Test
    fun loopingHandlesSeveralCyclesAndFrameSteppingPausesAndWraps() {
        val playback = SpritePlayback(3)
        playback.play(loop = true)
        playback.update(1.25f)
        assertEquals(0, playback.frameIndex)
        assertTrue(playback.playing)
        playback.step(-1)
        assertEquals(2, playback.frameIndex)
        assertFalse(playback.playing)
        playback.step(1)
        assertEquals(0, playback.frameIndex)
        playback.toggle()
        assertTrue(playback.looping)
        playback.stop()
        assertFalse(playback.looping)
    }
}
