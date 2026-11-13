package org.demoth.cake.ui

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import org.demoth.cake.audio.SoundDurationRegistry
import org.demoth.cake.audio.SoundPlayback

/** Sound follows the same bottom transport placement as visual animated resources. */
internal class SoundPreviewControls(
    sound: Sound, playback: SoundPlayback, labelStyle: Label.LabelStyle,
    buttonStyle: TextButton.TextButtonStyle,
) : Table() {
    constructor(sound: Sound, playback: SoundPlayback, skin: Skin) : this(sound, playback,
        skin.get(Label.LabelStyle::class.java), ContentBrowserStyles.fromSkin(skin).button)

    init {
        val duration = SoundDurationRegistry.durationMs(sound)
        add().grow().row()
        add(PlaybackToolbar(labelStyle, buttonStyle, { playback.playing }, playback::toggle, playback::stop,
            { (if (playback.playing) "Playing" else "Paused") +
                (duration?.let { " | Duration: ${it / 1000f} s" } ?: " | Duration: unknown") },
            { playback.looping }, playback::setLoop)).growX()
    }
}
