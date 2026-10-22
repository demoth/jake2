package org.demoth.cake.viewer

import com.badlogic.gdx.audio.Sound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class SoundPlaybackTest {
    private class Audio {
        var time = 0L
        var nextId = 10L
        val calls = mutableListOf<String>()
        val sound = Proxy.newProxyInstance(Sound::class.java.classLoader, arrayOf(Sound::class.java)) { _, method, args ->
            calls += "${method.name}(${args?.joinToString().orEmpty()})"
            if (method.name == "play" || method.name == "loop") nextId else null
        } as Sound
        fun playback(duration: Int? = 1000) = SoundPlayback(sound, duration) { time }
        fun advance(ms: Long) { time += ms * 1_000_000L }
    }

    @Test
    fun pauseAndPlayResumeTheSameInstanceAndExcludePausedTime() {
        val audio = Audio()
        val playback = audio.playback()
        playback.play()
        audio.advance(400)
        playback.pause()
        audio.advance(5000)
        playback.play()
        assertEquals(listOf("play()", "pause(10)", "resume(10)"), audio.calls)
        audio.advance(599)
        playback.update()
        assertTrue(playback.playing)
        audio.advance(1)
        playback.update()
        assertFalse(playback.playing)
        assertEquals("stop(10)", audio.calls.last())
        playback.play()
        assertEquals("play()", audio.calls.last())
    }

    @Test
    fun loopingAndToggleRetainPositionWhilePlaySelectsSinglePlayback() {
        val audio = Audio()
        val playback = audio.playback()
        playback.play(loop = true)
        audio.advance(2250)
        playback.toggle()
        assertFalse(playback.playing)
        assertTrue(playback.looping)
        audio.advance(3000)
        playback.toggle()
        assertTrue(playback.playing)
        assertEquals(listOf("loop()", "pause(10)", "resume(10)"), audio.calls)
        playback.play()
        assertFalse(playback.looping)
        assertEquals("setLooping(10, false)", audio.calls.last())
        audio.advance(750)
        playback.update()
        assertFalse(playback.playing)
    }

    @Test
    fun enablingLoopOnPausedPlaybackResumesWithoutRestarting() {
        val audio = Audio()
        val playback = audio.playback()
        playback.play()
        playback.pause()
        playback.play(loop = true)
        assertEquals(listOf("play()", "pause(10)", "setLooping(10, true)", "resume(10)"), audio.calls)
        audio.advance(9000)
        playback.update()
        assertTrue(playback.playing)
        assertTrue(playback.looping)
    }

    @Test
    fun replayAndStopOnlyReleaseTheOwnedInstanceAndResetState() {
        val audio = Audio()
        val playback = audio.playback()
        playback.play(loop = true)
        playback.pause()
        audio.nextId = 11
        playback.replay()
        assertEquals(listOf("loop()", "pause(10)", "stop(10)", "play()"), audio.calls)
        assertTrue(playback.playing)
        assertFalse(playback.looping)
        playback.stop()
        playback.stop()
        assertEquals("stop(11)", audio.calls.last())
        assertEquals(1, audio.calls.count { it == "stop(11)" })
        assertFalse(playback.playing)
        assertFalse(playback.looping)
    }

    @Test
    fun failedStartCanBeRetriedWithoutPausingOrStoppingAnInvalidInstance() {
        val audio = Audio()
        val playback = audio.playback()
        audio.nextId = -1
        playback.play(loop = true)
        playback.pause()
        playback.stop()
        assertFalse(playback.playing)
        assertFalse(playback.looping)
        assertEquals(listOf("loop()"), audio.calls)
        audio.nextId = 12
        playback.toggle()
        assertTrue(playback.playing)
        assertEquals("play()", audio.calls.last())
    }

    @Test
    fun unknownDurationStaysControllableUntilExplicitlyStopped() {
        val audio = Audio()
        val playback = audio.playback(null)
        playback.play()
        audio.advance(9000)
        playback.update()
        assertTrue(playback.playing)
        playback.pause()
        playback.play()
        playback.stop()
        assertEquals(listOf("play()", "pause(10)", "resume(10)", "stop(10)"), audio.calls)
    }
}
