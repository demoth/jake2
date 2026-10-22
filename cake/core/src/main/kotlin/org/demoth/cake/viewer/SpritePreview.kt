package org.demoth.cake.viewer

import com.badlogic.gdx.scenes.scene2d.Actor
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
        drawable = TextureRegionDrawable(frame.texture).apply {
            minWidth = frame.width.toFloat()
            minHeight = frame.height.toFloat()
        }
        shownFrame = playback.frameIndex
    }
}

internal class SpritePreview(sprite: Sp2Asset, playback: FramePlayback, skin: Skin) : Table(skin) {
    init {
        add(SpriteFrameImage(sprite, playback, Scaling.none)).row()
        add(FramePlaybackControls(playback, skin) {
            val frame = sprite.frames[playback.frameIndex]
            "${frame.width} x ${frame.height}"
        }).growX()
    }
}
