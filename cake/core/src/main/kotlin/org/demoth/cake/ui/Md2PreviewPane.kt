package org.demoth.cake.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import kotlin.math.*

/** The host owns model/shader resources; this widget owns its camera and pane-sized render target. */
internal class Md2PreviewPane(bounds: BoundingBox, renderModel: (Camera) -> Unit) : ScenePreviewPane(renderModel) {
    private val center = bounds.getCenter(Vector3())
    private val radius = (bounds.getDimensions(Vector3()).len() / 2f).coerceAtLeast(0.1f)
    private var yaw = -45f
    private var pitch = 20f
    private var zoom = 1f

    init {
        addListener(object : InputListener() {
            private var lastX = 0f
            private var lastY = 0f

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                if (button != Input.Buttons.LEFT) return false
                lastX = x; lastY = y
                stage?.scrollFocus = this@Md2PreviewPane
                stage?.keyboardFocus = this@Md2PreviewPane
                event.stop()
                return true
            }

            override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
                yaw -= (x - lastX) * 0.5f
                pitch = (pitch - (y - lastY) * 0.5f).coerceIn(-85f, 85f)
                lastX = x; lastY = y
                event.stop()
            }

            override fun mouseMoved(event: InputEvent, x: Float, y: Float): Boolean {
                stage?.scrollFocus = this@Md2PreviewPane
                event.stop()
                return true
            }

            override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                if (x < 0f || y < 0f || x >= width || y >= height) return false
                zoom = (zoom * 1.1f.pow(amountY)).coerceIn(0.1f, 30f)
                event.stop()
                return true
            }
        })
    }

    override fun updateCamera(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        val halfFov = Math.toRadians(camera.fieldOfView.toDouble() / 2).toFloat()
        val limitingFov = min(halfFov, atan(tan(halfFov) * width / height))
        val distance = radius / sin(limitingFov) * 1.1f * zoom
        val yawRadians = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRadians = Math.toRadians(pitch.toDouble()).toFloat()
        camera.position.set(cos(yawRadians) * cos(pitchRadians), sin(yawRadians) * cos(pitchRadians), sin(pitchRadians))
            .scl(distance).add(center)
        camera.up.set(0f, 0f, 1f)
        camera.lookAt(center)
        camera.near = radius * 0.01f
        camera.far = distance + radius * 2f
        camera.update()
    }

}
