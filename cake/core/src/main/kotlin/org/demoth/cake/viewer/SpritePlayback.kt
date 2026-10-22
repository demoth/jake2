package org.demoth.cake.viewer

/** Frame playback shared by loose, tile and full-pane sprite views. SP2 has no stored rate. */
internal class SpritePlayback(val frameCount: Int) {
    init { require(frameCount > 0) }
    var frameIndex = 0
        private set
    var playing = false
        private set
    var looping = false
        private set
    private var elapsed = 0f
    private var finished = false

    fun play(loop: Boolean = false) {
        if (finished) { frameIndex = 0; elapsed = 0f }
        finished = false
        looping = loop
        playing = true
    }

    fun pause() { playing = false }
    fun toggle() { if (playing) pause() else play(looping) }

    fun stop() {
        playing = false
        looping = false
        finished = false
        frameIndex = 0
        elapsed = 0f
    }

    fun step(delta: Int) {
        pause()
        frameIndex = Math.floorMod(frameIndex + delta, frameCount)
        elapsed = 0f
        finished = false
    }

    fun update(delta: Float) {
        if (!playing) return
        elapsed += delta
        val steps = (elapsed / 0.1f).toInt()
        if (steps == 0) return
        elapsed %= 0.1f
        if (looping) frameIndex = (frameIndex + steps) % frameCount
        else if (frameIndex + steps >= frameCount) {
            frameIndex = frameCount - 1
            playing = false
            finished = true
            elapsed = 0f
        } else frameIndex += steps
    }
}
