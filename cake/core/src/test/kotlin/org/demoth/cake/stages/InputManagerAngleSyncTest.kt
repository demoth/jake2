package org.demoth.cake.stages

import com.badlogic.gdx.Input
import jake2.qcommon.Defines.PM_DEAD
import jake2.qcommon.Defines.PM_NORMAL
import jake2.qcommon.Defines.PITCH
import jake2.qcommon.Defines.YAW
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientFrame
import org.demoth.cake.input.InputManager
import org.demoth.cake.wrapSignedAngle
import org.junit.Assert.assertEquals
import org.junit.Test

class InputManagerAngleSyncTest {
    private val angleEpsilon = 0.05f
    private val cmdAngleEpsilon = 0.1f

    @Test
    fun initializesLocalAndInitialAnglesFromServerViewAndDelta() {
        val input = InputManager()
        val frame = frame(pmType = PM_NORMAL, viewYaw = 30f, deltaYaw = 10f, viewPitch = 5f, deltaPitch = 2f)

        val msg = input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame)

        assertEquals(30f, input.localYaw, angleEpsilon)
        assertEquals(10f, input.initialYaw!!, angleEpsilon)
        assertEquals(5f, input.localPitch, angleEpsilon)
        assertEquals(2f, input.initialPitch!!, angleEpsilon)
        assertEquals(20f, wrapSignedAngle(Math3D.SHORT2ANGLE(msg.newCmd.angles[YAW].toInt())), cmdAngleEpsilon)
        assertEquals(3f, wrapSignedAngle(Math3D.SHORT2ANGLE(msg.newCmd.angles[PITCH].toInt())), cmdAngleEpsilon)
    }

    @Test
    fun rebasesLocalAnglesWhenServerDeltaYawChanges() {
        val input = InputManager()
        val frame1 = frame(pmType = PM_NORMAL, viewYaw = 30f, deltaYaw = 10f)
        input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame1)

        // emulate local mouse turn before next server frame
        input.localYaw = 50f

        // server changed basis (+15 yaw), and authoritative view now reflects it
        val frame2 = frame(pmType = PM_NORMAL, viewYaw = 65f, deltaYaw = 25f)
        val msg = input.gatherInput(outgoingSequence = 1, deltaTime = 0f, currentFrame = frame2)

        assertEquals(65f, input.localYaw, angleEpsilon)
        assertEquals(25f, input.initialYaw!!, angleEpsilon)
        assertEquals(40f, wrapSignedAngle(Math3D.SHORT2ANGLE(msg.newCmd.angles[YAW].toInt())), cmdAngleEpsilon)
    }

    @Test
    fun enteringLocalControlResyncsAnglesAfterDeadState() {
        val input = InputManager()
        val deadFrame = frame(pmType = PM_DEAD, viewYaw = 140f, deltaYaw = 70f)
        input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = deadFrame)

        val respawnFrame = frame(pmType = PM_NORMAL, viewYaw = 90f, deltaYaw = 30f)
        val msg = input.gatherInput(outgoingSequence = 1, deltaTime = 0f, currentFrame = respawnFrame)

        assertEquals(90f, input.localYaw, angleEpsilon)
        assertEquals(30f, input.initialYaw!!, angleEpsilon)
        assertEquals(60f, wrapSignedAngle(Math3D.SHORT2ANGLE(msg.newCmd.angles[YAW].toInt())), cmdAngleEpsilon)
    }

    @Test
    fun oppositeMovementKeysCancelOut() {
        val input = InputManager()
        val frame = frame(pmType = PM_NORMAL, viewYaw = 0f, deltaYaw = 0f)

        input.keyDown(Input.Keys.W)
        input.keyDown(Input.Keys.S)
        input.keyDown(Input.Keys.A)
        input.keyDown(Input.Keys.D)
        input.keyDown(Input.Keys.SPACE)
        input.keyDown(Input.Keys.C)

        val msg = input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame)

        assertEquals(0, msg.newCmd.forwardmove.toInt())
        assertEquals(0, msg.newCmd.sidemove.toInt())
        assertEquals(0, msg.newCmd.upmove.toInt())
    }

    private fun frame(
        pmType: Int,
        viewYaw: Float,
        deltaYaw: Float,
        viewPitch: Float = 0f,
        deltaPitch: Float = 0f,
    ): ClientFrame {
        return ClientFrame().apply {
            playerstate.pmove.pm_type = pmType
            playerstate.viewangles[YAW] = viewYaw
            playerstate.viewangles[PITCH] = viewPitch
            playerstate.pmove.delta_angles[YAW] = Math3D.ANGLE2SHORT(deltaYaw).toShort()
            playerstate.pmove.delta_angles[PITCH] = Math3D.ANGLE2SHORT(deltaPitch).toShort()
        }
    }
}
