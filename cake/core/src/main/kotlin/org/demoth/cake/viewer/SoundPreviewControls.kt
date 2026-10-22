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
internal class SoundPreviewControls(sound: Sound, skin: Skin) : Table(skin) {
    init {
        val duration = SoundDurationRegistry.durationMs(sound)
        defaults().pad(8f)
        add(Label(duration?.let { "Duration: ${it / 1000f} s" } ?: "Duration: unknown", skin)).colspan(3).row()
        button("Play") { sound.stop(); sound.play() }
        button("Loop") { sound.stop(); sound.loop() }
        button("Stop") { sound.stop() }
        row()
        add(Label("Space: replay | S: stop", skin)).colspan(3)
    }

    private fun button(text: String, action: () -> Unit) {
        add(TextButton(text, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) { action() }
            })
        })
    }
}
