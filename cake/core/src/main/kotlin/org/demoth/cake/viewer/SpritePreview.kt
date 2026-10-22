package org.demoth.cake.viewer

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import org.demoth.cake.assets.Sp2Asset
import com.badlogic.gdx.utils.Scaling

/** Borrows frame textures. Fixed canvas dimensions keep the layout stable while frames change. */
internal class SpriteFrameImage(private val sprite: Sp2Asset, private val playback: SpritePlayback, scaling: Scaling) : Image() {
    private var shownFrame = -1
    private val canvasWidth = sprite.frames.maxOf { it.width }.toFloat()
    private val canvasHeight = sprite.frames.maxOf { it.height }.toFloat()

    init { setScaling(scaling); refresh() }
    override fun getPrefWidth() = canvasWidth
    override fun getPrefHeight() = canvasHeight
    override fun act(delta: Float) { super.act(delta); refresh() }

    private fun refresh() {
        if (shownFrame == playback.frameIndex) return
        val frame = sprite.frames[playback.frameIndex]
        drawable = TextureRegionDrawable(frame.texture).apply {
            minWidth = frame.width.toFloat()
            minHeight = frame.height.toFloat()
        }
        shownFrame = playback.frameIndex
    }
}

internal class SpritePreview(private val sprite: Sp2Asset, private val playback: SpritePlayback, skin: Skin) : Table(skin) {
    private val info = Label("", skin)
    init {
        defaults().pad(4f)
        add(SpriteFrameImage(sprite, playback, Scaling.none)).colspan(6).row()
        add(info).colspan(6).row()
        button("Previous") { playback.step(-1) }
        button("Play") { playback.play() }
        button("Pause") { playback.pause() }
        button("Loop") { playback.play(loop = true) }
        button("Stop") { playback.stop() }
        button("Next") { playback.step(1) }
        row()
        add(Label("Space: play/pause | Left/Right: step", skin)).colspan(6)
        updateInfo()
    }

    override fun act(delta: Float) { super.act(delta); updateInfo() }
    private fun updateInfo() {
        val frame = sprite.frames[playback.frameIndex]
        info.setText("Frame ${playback.frameIndex + 1}/${sprite.frames.size} | ${frame.width} x ${frame.height}")
    }

    private fun button(text: String, action: () -> Unit) {
        add(TextButton(text, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) { action() }
            })
        })
    }
}
