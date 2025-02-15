package org.demoth.cake.clientcommon

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Vector3

class FlyingCameraController(camera: Camera): CameraInputController(camera) {
    private val right = Vector3()
    var enabled = false
    val movementDirection = Vector3()

    init {
        translateUnits = 500f
    }

    override fun process(deltaX: Float, deltaY: Float, button: Int): Boolean {
        if (!enabled) return false
        if (button == rotateButton) {
            // PITCH: rotate around local "right" axis
            right.set(camera.direction).crs(camera.up).nor()
            camera.rotateAround(target, right, deltaY * rotateAngle)

            // YAW: rotate around global Z
            camera.rotateAround(target, Vector3.Z, -deltaX * rotateAngle)

            if (autoUpdate) camera.update()
        } else {
            super.process(deltaX, deltaY, button)
        }
        return true
    }

    override fun update() {
        if (!enabled) return
        // WASD movement
        if (rotateRightPressed || rotateLeftPressed || forwardPressed || backwardPressed) {
            movementDirection.set(0f, 0f, 0f)
            if (rotateRightPressed) {
                movementDirection.set(camera.direction)
                movementDirection.crs(camera.up)
                movementDirection.scl(-1f)
            } else if (rotateLeftPressed) {
                movementDirection.set(camera.direction)
                movementDirection.crs(camera.up)
            }
            if (forwardPressed) {
                movementDirection.add(camera.direction)
            } else if (backwardPressed) {
                movementDirection.add(-camera.direction.x, -camera.direction.y, -camera.direction.z)
            }
            movementDirection.nor()//malize
            camera.translate(movementDirection.scl(Gdx.graphics.deltaTime * translateUnits))
            target.add(movementDirection)

            if (autoUpdate) camera.update()

        } else {
            super.update()
        }
    }
}