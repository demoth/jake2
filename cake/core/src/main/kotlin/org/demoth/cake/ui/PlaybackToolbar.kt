package org.demoth.cake.ui

import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener

/** Shared transport anatomy; media controllers retain ownership of state and resources. */
internal class PlaybackToolbar(
    labelStyle: Label.LabelStyle,
    buttonStyle: TextButton.TextButtonStyle,
    private val playing: () -> Boolean,
    toggle: () -> Unit,
    stop: () -> Unit,
    private val status: () -> String,
    private val looping: (() -> Boolean)? = null,
    setLoop: ((Boolean) -> Unit)? = null,
    previous: (() -> Unit)? = null,
    next: (() -> Unit)? = null,
) : Table() {
    private val play = TextButton("Play", TextButton.TextButtonStyle(buttonStyle).apply {
        up = over ?: up
    })
    private val loop = looping?.let { TextButton("Loop", TextButton.TextButtonStyle(buttonStyle).apply {
        checked = over ?: up
        checkedOver = checked
        checkedFontColor = overFontColor ?: fontColor
    }) }
    private val info = Label("", labelStyle).apply { setEllipsis(true) }

    init {
        val controls = HorizontalGroup().wrap().left().space(4f).wrapSpace(4f)
        val playWidth = maxOf(GlyphLayout(buttonStyle.font, "Play").width,
            GlyphLayout(buttonStyle.font, "Pause").width) +
            (buttonStyle.up?.leftWidth ?: 0f) + (buttonStyle.up?.rightWidth ?: 0f)
        controls.addActor(Table().apply { add(play).minWidth(playWidth) })
        play.command(toggle)
        controls.addActor(TextButton("Stop", buttonStyle).apply { command(stop) })
        loop?.let { button ->
            button.setProgrammaticChangeEvents(false)
            button.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) { setLoop?.invoke(button.isChecked); refresh() }
            })
            controls.addActor(button)
        }
        // Keep frame stepping and its readout together when the toolbar wraps.
        controls.addActor(Table().apply {
            defaults().padLeft(4f)
            previous?.let { add(TextButton("<", buttonStyle).apply { command(it) }) }
            add(info).minWidth(0f).maxWidth(labelStyle.font.data.lineHeight * 16f)
            next?.let { add(TextButton(">", buttonStyle).apply { command(it) }) }
        })
        add(controls).growX().pad(4f)
        refresh()
    }

    private fun TextButton.command(action: () -> Unit) {
        setProgrammaticChangeEvents(false)
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) { isChecked = false; action(); refresh() }
        })
    }

    override fun act(delta: Float) { super.act(delta); refresh() }
    fun refresh() {
        play.setText(if (playing()) "Pause" else "Play")
        loop?.isChecked = looping?.invoke() == true
        info.setText(status())
    }
}
