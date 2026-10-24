package org.demoth.cake.ui

/** Fixed-rate frame playback for sprite and MD2 previews, independent of rendering. */
internal class FramePlayback(val frameCount: Int) {
    init { require(frameCount > 0) }
    var frameIndex = 0
        private set
    var playing = false
        private set
    var looping = false
        private set
    val nextFrameIndex get() = if (looping) (frameIndex + 1) % frameCount else (frameIndex + 1).coerceAtMost(frameCount - 1)
    val interpolation get() = elapsed / 0.1f
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
