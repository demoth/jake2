package org.demoth.cake.viewer

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import org.demoth.cake.audio.SoundDurationRegistry

/** Borrows its sound from the active preview; removing the widget does not unload it. */
internal class SoundPreviewControls(sound: Sound, playback: SoundPlayback, skin: Skin) : Table(skin) {
    init {
        val duration = SoundDurationRegistry.durationMs(sound)
        defaults().pad(8f)
        add(Label(duration?.let { "Duration: ${it / 1000f} s" } ?: "Duration: unknown", skin)).colspan(4).row()
        button("Play") { playback.play() }
        button("Pause") { playback.pause() }
        button("Loop") { playback.play(loop = true) }
        button("Stop") { playback.stop() }
        row()
        add(Label("Space: play/pause | S: stop", skin)).colspan(4)
    }

    private fun button(text: String, action: () -> Unit) {
        add(TextButton(text, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) { action() }
            })
        })
    }
}
