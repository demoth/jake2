package org.demoth.cake.ui

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton

/** Shared sprite/model transport; controllers are host-owned. */
internal class FramePlaybackControls(
    playback: FramePlayback, labelStyle: Label.LabelStyle,
    buttonStyle: TextButton.TextButtonStyle, description: () -> String,
) : Table() {
    init {
        add(PlaybackToolbar(labelStyle, buttonStyle, { playback.playing }, playback::toggle, playback::stop,
            { "Frame ${playback.frameIndex + 1}/${playback.frameCount} | ${description()}" },
            { playback.looping }, playback::setLoop, { playback.step(-1) }, { playback.step(1) })).growX()
    }
}
