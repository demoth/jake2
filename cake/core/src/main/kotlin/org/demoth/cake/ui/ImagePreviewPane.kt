package org.demoth.cake.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Scaling
import kotlin.math.min
import kotlin.math.pow

/** Borrows an image; zoom and pan are relative to its aspect-preserving fit in this pane. */
internal class ImagePreviewPane(private val image: Image) : WidgetGroup() {
    private val canvasWidth = image.prefWidth
    private val canvasHeight = image.prefHeight
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    init {
        touchable = Touchable.enabled
        image.setScaling(Scaling.fit)
        addActor(image)
        addListener(object : InputListener() {
            private var lastX = 0f
            private var lastY = 0f

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                if (button != Input.Buttons.LEFT) return false
                lastX = x; lastY = y
                stage?.scrollFocus = this@ImagePreviewPane
                event.stop()
                return true
            }

            override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
                if (width <= 0f || height <= 0f) return
                panX += (x - lastX) / width
                panY += (y - lastY) / height
                lastX = x; lastY = y
                invalidate()
                event.stop()
            }

            override fun mouseMoved(event: InputEvent, x: Float, y: Float): Boolean {
                stage?.scrollFocus = this@ImagePreviewPane
                event.stop()
                return true
            }

            override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                if (x < 0f || y < 0f || x >= width || y >= height) return false
                val nextZoom = (zoom * 1.1f.pow(-amountY)).coerceIn(0.05f, 32f)
                val ratio = nextZoom / zoom
                // Keep the image point under the pointer fixed while changing scale.
                panX = (x - width / 2f - (x - width / 2f - panX * width) * ratio) / width
                panY = (y - height / 2f - (y - height / 2f - panY * height) * ratio) / height
                zoom = nextZoom
                invalidate()
                event.stop()
                return true
            }
        })
    }

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        if (x < 0f || y < 0f || x >= width || y >= height) return null
        return super.hit(x, y, touchable)
    }

    override fun layout() {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return
        val scale = min(width / canvasWidth, height / canvasHeight) * zoom
        val imageWidth = canvasWidth * scale
        val imageHeight = canvasHeight * scale
        image.setBounds(width / 2f + panX * width - imageWidth / 2f,
            height / 2f + panY * height - imageHeight / 2f, imageWidth, imageHeight)
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()
        batch.flush()
        if (clipBegin(x, y, width, height)) {
            try {
                super.draw(batch, parentAlpha)
                batch.flush()
            } finally {
                clipEnd()
            }
        }
    }
}
