package org.demoth.cake.viewer

import org.demoth.cake.ui.FramePlayback

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FramePlaybackTest {
    @Test
    fun playsOnceAtTenFpsHoldsLastFrameAndCanRestart() {
        val playback = FramePlayback(4)
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
        val playback = FramePlayback(4)
        playback.play()
        playback.update(0.16f)
        assertEquals(2, playback.nextFrameIndex)
        assertEquals(0.6f, playback.interpolation, 0.001f)
        playback.pause()
        playback.update(5f)
        assertEquals(1, playback.frameIndex)
        assertEquals(0.6f, playback.interpolation, 0.001f)
        playback.toggle()
        playback.update(0.06f)
        assertEquals(2, playback.frameIndex)
        playback.stop()
        assertEquals(0, playback.frameIndex)
        assertEquals(0f, playback.interpolation)
        assertFalse(playback.playing)
        playback.play()
        playback.update(0.05f)
        assertEquals(0, playback.frameIndex)
    }

    @Test
    fun loopingHandlesSeveralCyclesAndFrameSteppingPausesAndWraps() {
        val playback = FramePlayback(3)
        playback.play(loop = true)
        playback.update(1.25f)
        assertEquals(0, playback.frameIndex)
        assertTrue(playback.playing)
        playback.step(-1)
        assertEquals(2, playback.frameIndex)
        assertEquals(0, playback.nextFrameIndex)
        assertEquals(0f, playback.interpolation)
        assertFalse(playback.playing)
        playback.step(1)
        assertEquals(0, playback.frameIndex)
        playback.toggle()
        assertTrue(playback.looping)
        playback.stop()
        assertFalse(playback.looping)
    }

    @Test
    fun singlePlaybackClampsInterpolationAtLastFrameAndLoopingBlendsBackToFirst() {
        val playback = FramePlayback(3)
        playback.play()
        playback.update(0.25f)
        assertEquals(2, playback.frameIndex)
        assertEquals(2, playback.nextFrameIndex)
        playback.play(loop = true)
        assertEquals(0, playback.nextFrameIndex)
        assertEquals(0.5f, playback.interpolation, 0.001f)
        playback.play()
        playback.update(0.1f)
        assertFalse(playback.playing)
        assertEquals(2, playback.frameIndex)
        assertEquals(0f, playback.interpolation)
    }
}
