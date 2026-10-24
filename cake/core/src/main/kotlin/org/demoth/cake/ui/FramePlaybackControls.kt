package org.demoth.cake.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener

/** Shared sprite/model transport; playback state and resources remain host-owned. */
internal class FramePlaybackControls(
    private val playback: FramePlayback, labelStyle: Label.LabelStyle,
    private val buttonStyle: TextButton.TextButtonStyle, private val description: () -> String,
) : Table() {
    constructor(playback: FramePlayback, skin: Skin, description: () -> String) : this(playback,
        skin.get(Label.LabelStyle::class.java), skin.get(TextButton.TextButtonStyle::class.java), description)

    private val info = Label("", labelStyle).apply { setWrap(true) }
    private val buttons = HorizontalGroup().apply { wrap(); space(4f); wrapSpace(4f) }

    init {
        defaults().pad(4f)
        add(info).growX().row()
        button("Previous") { playback.step(-1) }
        button("Play") { playback.play() }
        button("Pause") { playback.pause() }
        button("Loop") { playback.play(loop = true) }
        button("Stop") { playback.stop() }
        button("Next") { playback.step(1) }
        add(buttons).growX().row()
        add(Label("Space: play/pause | Left/Right: step", labelStyle).apply { setWrap(true) }).growX()
        updateInfo()
    }

    override fun act(delta: Float) { super.act(delta); updateInfo() }
    private fun updateInfo() {
        info.setText("Frame ${playback.frameIndex + 1}/${playback.frameCount} | ${description()}")
    }

    private fun button(text: String, action: () -> Unit) {
        buttons.addActor(TextButton(text, buttonStyle).apply {
            setProgrammaticChangeEvents(false)
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) { isChecked = false; action() }
            })
        })
    }
}
