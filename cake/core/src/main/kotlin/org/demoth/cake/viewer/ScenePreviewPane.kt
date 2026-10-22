package org.demoth.cake.viewer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.HdpiUtils
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.utils.Disposable
import kotlin.math.*

/** Owns the pane-sized render target; scene assets belong to the host's preview scope. */
internal abstract class ScenePreviewPane(private val renderScene: (Camera) -> Unit) : Widget(), Disposable {
    protected val camera = PerspectiveCamera(60f, 1f, 1f)
    private var target: FrameBuffer? = null
    private var region: TextureRegion? = null

    override fun draw(batch: Batch, parentAlpha: Float) {
        if (width <= 0f || height <= 0f) return
        batch.end()
        val scissors = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)
        try {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
            val pixelWidth = HdpiUtils.toBackBufferX(ceil(width).toInt()).coerceAtLeast(1)
            val pixelHeight = HdpiUtils.toBackBufferY(ceil(height).toInt()).coerceAtLeast(1)
            if (target?.width != pixelWidth || target?.height != pixelHeight) {
                val replacement = FrameBuffer(Pixmap.Format.RGBA8888, pixelWidth, pixelHeight, true)
                replacement.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                target?.dispose()
                target = replacement
                region = TextureRegion(replacement.colorBufferTexture).apply { flip(false, true) }
            }
            updateCamera(pixelWidth, pixelHeight)
            target!!.begin()
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            renderScene(camera)
        } finally {
            target?.end()
            stage?.viewport?.apply()
            if (scissors) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
            batch.begin()
        }
        val previousColor = batch.packedColor
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
        batch.draw(region, x, y, width, height)
        batch.packedColor = previousColor
    }

    protected abstract fun updateCamera(width: Int, height: Int)

    override fun dispose() {
        target?.dispose()
        target = null
        region = null
    }
}
