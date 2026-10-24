package org.demoth.cake.viewer

import org.demoth.cake.ui.ScenePreviewPane

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import org.demoth.cake.assets.bsp.BspMapAsset
import kotlin.math.cos
import kotlin.math.sin

/** Free flight without gameplay or collision; movement belongs only to the focused pane. */
internal class BspPreviewPane(map: BspMapAsset, renderMap: (Camera) -> Unit) : ScenePreviewPane(renderMap) {
    private var yaw = 0f
    private var pitch = 0f
    private val movement = Vector3()
    private val right = Vector3()
    private val movementKeys = setOf(Input.Keys.W, Input.Keys.A, Input.Keys.S, Input.Keys.D, Input.Keys.Q, Input.Keys.E)
    private val releaseFocus = object : InputListener() {
        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (event.target !== this@BspPreviewPane && stage?.keyboardFocus === this@BspPreviewPane) {
                stage?.keyboardFocus = null
            }
            return false
        }
    }

    init {
        val spawn = sequenceOf("info_player_start", "info_player_deathmatch", "info_player_coop")
            .mapNotNull { type ->
                val candidates = map.entities.filter { it["classname"] == type && it["origin"] != null }
                // Named starts are entrances from other maps; prefer the normal level entry.
                candidates.firstOrNull { it["targetname"].isNullOrBlank() } ?: candidates.firstOrNull()
            }
            .firstOrNull()
        val origin = spawn?.get("origin")?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toFloatOrNull() }
        if (origin?.size == 3) {
            camera.position.set(origin[0], origin[1], origin[2] + 22f)
            yaw = spawn["angle"]?.toFloatOrNull() ?: 0f
        } else if (map.bounds.isValid) {
            map.bounds.getCenter(camera.position)
        }
        camera.near = 1f
        camera.far = if (map.bounds.isValid) map.bounds.getDimensions(Vector3()).len().coerceAtLeast(4096f) else 4096f
        addListener(object : InputListener() {
            private var lastX = 0f
            private var lastY = 0f

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                if (button != Input.Buttons.LEFT) return false
                stage?.keyboardFocus = this@BspPreviewPane
                lastX = x; lastY = y
                event.stop()
                return true
            }

            override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
                yaw -= (x - lastX) * 0.3f
                pitch = (pitch + (y - lastY) * 0.3f).coerceIn(-85f, 85f)
                lastX = x; lastY = y
                event.stop()
            }

            override fun keyDown(event: InputEvent, keycode: Int): Boolean = keycode in movementKeys
            override fun keyUp(event: InputEvent, keycode: Int): Boolean = keycode in movementKeys
        })
    }

    override fun setStage(stage: Stage?) {
        this.stage?.removeCaptureListener(releaseFocus)
        super.setStage(stage)
        stage?.addCaptureListener(releaseFocus)
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (stage?.keyboardFocus !== this) return
        movement.setZero()
        right.set(camera.direction).crs(Vector3.Z).nor()
        if (Gdx.input.isKeyPressed(Input.Keys.W)) movement.add(camera.direction)
        if (Gdx.input.isKeyPressed(Input.Keys.S)) movement.sub(camera.direction)
        if (Gdx.input.isKeyPressed(Input.Keys.D)) movement.add(right)
        if (Gdx.input.isKeyPressed(Input.Keys.A)) movement.sub(right)
        if (Gdx.input.isKeyPressed(Input.Keys.E)) movement.z += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) movement.z -= 1f
        camera.position.mulAdd(movement.nor(), 500f * delta.coerceAtMost(0.1f))
    }

    override fun updateCamera(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        val yawRadians = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRadians = Math.toRadians(pitch.toDouble()).toFloat()
        camera.direction.set(cos(yawRadians) * cos(pitchRadians), sin(yawRadians) * cos(pitchRadians), sin(pitchRadians))
        right.set(camera.direction).crs(Vector3.Z).nor()
        camera.up.set(right).crs(camera.direction).nor()
        camera.update()
    }
}
