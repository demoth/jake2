package org.demoth.cake.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Scaling
import ktx.actors.onClick
import org.demoth.cake.assets.CinematicCinDecoder
import org.demoth.cake.assets.DecodedCinFrame

/** Owns the decoded frame, texture and audio device; stage act drives 14 FPS playback. */
internal class CinematicPreview(
    private val file: com.badlogic.gdx.files.FileHandle,
    private val palette: IntArray,
    styles: ContentBrowserStyles,
) : Table(), Disposable {
    private var decoder = CinematicCinDecoder(file.read(), palette)
    private var frame = try {
        requireNotNull(decoder.readNextFrame()) { "Cinematic has no frames" }
    } catch (error: Exception) {
        decoder.close()
        throw error
    }
    private var pixmap: Pixmap? = null
    private var texture: Texture? = null
    private var audio: AudioDevice? = null
    private val picture = Image().apply { setScaling(Scaling.fit) }
    private val status = Label("", styles.label)
    private var playing = false
    private var ended = false
    private var elapsed = 0f
    private var audioSubmitted = false

    init {
        try {
            upload(frame)
            add(picture).minSize(0f).grow().row()
            add(Table().apply {
                for (name in listOf("Play", "Pause", "Stop")) {
                    add(TextButton(name, styles.button).apply {
                        onClick {
                            when (name) {
                                "Play" -> play()
                                "Pause" -> pause()
                                else -> stop()
                            }
                        }
                    }).pad(4f)
                }
            }).row()
            add(status).pad(4f)
            updateStatus()
            addListener(object : InputListener() {
                override fun keyDown(event: InputEvent, keycode: Int): Boolean = this@CinematicPreview.keyDown(keycode)
                override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                    stage?.keyboardFocus = this@CinematicPreview
                    return false
                }
            })
        } catch (error: Exception) {
            dispose()
            throw error
        }
    }

    fun keyDown(keycode: Int): Boolean = when (keycode) {
        Input.Keys.SPACE -> { if (playing) pause() else play(); true }
        Input.Keys.S -> { stop(); true }
        else -> false
    }

    private fun play() {
        if (ended && !stop()) return
        playing = true
        updateStatus()
    }

    private fun pause() {
        playing = false
        audio?.dispose()
        audio = null
        updateStatus()
    }

    private fun stop(): Boolean {
        pause()
        decoder.close()
        return try {
            decoder = CinematicCinDecoder(file.read(), palette)
            frame = requireNotNull(decoder.readNextFrame()) { "Cinematic has no frames" }
            elapsed = 0f
            ended = false
            audioSubmitted = false
            upload(frame)
            updateStatus()
            true
        } catch (error: Exception) {
            decoder.close()
            ended = true
            status.setText("Could not reopen cinematic.")
            Gdx.app.error("Cinematic preview", "Restart failed", error)
            false
        }
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (!playing) return
        try {
            if (!audioSubmitted) submitAudio(frame)
            elapsed += delta.coerceAtLeast(0f)
            while (elapsed >= 1f / 14f) {
                elapsed -= 1f / 14f
                val next = decoder.readNextFrame()
                if (next == null) {
                    ended = true
                    pause()
                    break
                }
                frame = next
                upload(frame)
                submitAudio(frame)
            }
            updateStatus()
        } catch (error: Exception) {
            decoder.close()
            pause()
            ended = true
            status.setText("Could not play cinematic. Stop to retry.")
            Gdx.app.error("Cinematic preview", "Playback failed", error)
        }
    }

    private fun upload(frame: DecodedCinFrame) {
        require(frame.indexedFrame.size == decoder.width * decoder.height) { "Invalid cinematic frame size" }
        val pixels = pixmap ?: Pixmap(decoder.width, decoder.height, Pixmap.Format.RGBA8888).also { pixmap = it }
        val colors = decoder.currentPalette()
        for (y in 0 until decoder.height) for (x in 0 until decoder.width) {
            pixels.drawPixel(x, y, colors[frame.indexedFrame[y * decoder.width + x].toInt() and 255])
        }
        val current = texture
        if (current == null) {
            texture = Texture(pixels)
            picture.drawable = com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(texture!!)
        } else current.draw(pixels, 0, 0)
    }

    private fun submitAudio(frame: DecodedCinFrame) {
        audioSubmitted = true
        val pcm = frame.audioPcmBytes
        if (pcm.isEmpty() || decoder.sampleRate == 0) return
        require(decoder.sampleWidth in 1..2 && decoder.sampleChannels in 1..2) { "Unsupported cinematic audio format" }
        val device = audio ?: Gdx.audio.newAudioDevice(decoder.sampleRate, decoder.sampleChannels == 1).also { audio = it }
        val samples = ShortArray(pcm.size / decoder.sampleWidth) { i ->
            if (decoder.sampleWidth == 1) (((pcm[i].toInt() and 255) - 128) shl 8).toShort()
            else ((pcm[i * 2].toInt() and 255) or (pcm[i * 2 + 1].toInt() shl 8)).toShort()
        }
        device.writeSamples(samples, 0, samples.size)
    }

    private fun updateStatus() {
        status.setText("${decoder.width} x ${decoder.height} | Frame ${decoder.decodedFrameCount} | " +
            if (ended) "Finished" else if (playing) "Playing" else "Paused")
    }

    override fun dispose() {
        decoder.close()
        playing = false
        audio?.dispose()
        audio = null
        texture?.dispose()
        texture = null
        pixmap?.dispose()
        pixmap = null
        picture.drawable = null
    }
}
