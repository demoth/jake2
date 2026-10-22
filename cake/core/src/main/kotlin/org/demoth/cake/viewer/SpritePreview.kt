package org.demoth.cake.viewer

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import org.demoth.cake.assets.Sp2Asset
import com.badlogic.gdx.utils.Scaling

/** Borrows frame textures. Fixed canvas dimensions keep the layout stable while frames change. */
internal class SpriteFrameImage(private val sprite: Sp2Asset, private val playback: FramePlayback, scaling: Scaling) : Image() {
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
        drawable = object : TextureRegionDrawable(frame.texture) {
            init {
                minWidth = canvasWidth
                minHeight = canvasHeight
            }

            override fun draw(batch: Batch, x: Float, y: Float, width: Float, height: Float) {
                val frameWidth = width * frame.width / canvasWidth
                val frameHeight = height * frame.height / canvasHeight
                super.draw(batch, x + (width - frameWidth) / 2f, y + (height - frameHeight) / 2f,
                    frameWidth, frameHeight)
            }
        }
        shownFrame = playback.frameIndex
    }
}

internal class SpritePreview(sprite: Sp2Asset, playback: FramePlayback, skin: Skin) : Table(skin) {
    init {
        add(ImagePreviewPane(SpriteFrameImage(sprite, playback, Scaling.fit))).grow().row()
        add(FramePlaybackControls(playback, skin) {
            val frame = sprite.frames[playback.frameIndex]
            "${frame.width} x ${frame.height}"
        }).growX().row()
        add(Label("Drag: pan | Wheel: zoom", skin)).pad(4f)
    }
}
