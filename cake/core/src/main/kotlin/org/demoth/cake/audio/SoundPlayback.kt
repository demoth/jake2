package org.demoth.cake.audio

import com.badlogic.gdx.audio.Sound

/** Owns one playback instance, borrowing the sound from the active preview's asset scope. */
internal class SoundPlayback(
    private val sound: Sound,
    durationMs: Int? = SoundDurationRegistry.durationMs(sound),
    private val now: () -> Long = System::nanoTime,
) {
    var playing = false
        private set
    var looping = false
        private set
    private var id = -1L
    private var elapsed = 0L
    private var lastUpdate = now()
    private val duration = durationMs?.takeIf { it > 0 }?.toLong()?.times(1_000_000L)

    fun play(loop: Boolean = false) {
        update()
        if (id == -1L) {
            id = if (loop) sound.loop() else sound.play()
            if (id == -1L) return
        } else {
            if (looping != loop) sound.setLooping(id, loop)
            if (!playing) sound.resume(id)
        }
        playing = true
        looping = loop
        lastUpdate = now()
    }

    fun replay() { stop(); play() }

    fun pause() {
        update()
        if (!playing) return
        sound.pause(id)
        playing = false
    }

    fun toggle() {
        update()
        if (playing) pause() else play(looping)
    }

    fun stop() {
        if (id != -1L) sound.stop(id)
        id = -1L
        playing = false
        looping = false
        elapsed = 0L
    }

    fun update() {
        val time = now()
        if (playing) {
            elapsed += (time - lastUpdate).coerceAtLeast(0L)
            // Sound has no completion callback/query. Unknown durations stay active until stopped.
            if (duration != null && elapsed >= duration) {
                if (looping) elapsed %= duration else stop()
            }
        }
        lastUpdate = time
    }
}
