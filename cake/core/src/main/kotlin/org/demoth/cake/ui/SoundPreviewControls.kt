package org.demoth.cake.ui

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import org.demoth.cake.audio.SoundDurationRegistry
import org.demoth.cake.audio.SoundPlayback

/** Borrows its sound from the active preview; removing the widget does not unload it. */
internal class SoundPreviewControls(
    sound: Sound, playback: SoundPlayback,
    labelStyle: Label.LabelStyle, private val buttonStyle: TextButton.TextButtonStyle,
) : Table() {
    constructor(sound: Sound, playback: SoundPlayback, skin: Skin) : this(sound, playback,
        skin.get(Label.LabelStyle::class.java), skin.get(TextButton.TextButtonStyle::class.java))

    init {
        val duration = SoundDurationRegistry.durationMs(sound)
        defaults().pad(8f)
        add(Label(duration?.let { "Duration: ${it / 1000f} s" } ?: "Duration: unknown", labelStyle)).colspan(4).row()
        button("Play") { playback.play() }
        button("Pause") { playback.pause() }
        button("Loop") { playback.play(loop = true) }
        button("Stop") { playback.stop() }
        row()
        add(Label("Space: play/pause | S: stop", labelStyle)).colspan(4)
    }

    private fun button(text: String, action: () -> Unit) {
        add(TextButton(text, buttonStyle).apply {
            setProgrammaticChangeEvents(false)
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    isChecked = false
                    action()
                }
            })
        })
    }
}
