package org.demoth.cake.stages

import com.badlogic.gdx.Input
import jake2.qcommon.Defines.BUTTON_ANY
import jake2.qcommon.Defines.BUTTON_USE
import jake2.qcommon.Defines.PM_NORMAL
import jake2.qcommon.Defines.YAW
import jake2.qcommon.usercmd_t
import jake2.qcommon.util.Math3D
import org.demoth.cake.CakeCvars
import org.demoth.cake.ClientFrame
import org.demoth.cake.input.ClientBindings
import org.demoth.cake.input.InputManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Checks bindings through generated commands; registers input definitions without bootstrapping Cake. */
class InputManagerBindingsIntegrationTest {
    @BeforeEach
    fun registerInputCvars() {
        CakeCvars.registerInput()
    }

    @Test
    fun rebindedImmediateControlAffectsGeneratedUserCommand() {
        val bindings = ClientBindings()
        bindings.clearBindings()
        bindings.setBindingByName("x", "+forward")

        val input = InputManager(bindings = bindings)
        input.keyDown(Input.Keys.X)

        val msg = input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame())
        assertEquals(200, msg.newCmd.forwardmove)
    }

    @Test
    fun commandStyleBindingsExecuteOnKeyDown() {
        val queued = mutableListOf<String>()
        val bindings = ClientBindings(queueCommand = queued::add)
        bindings.clearBindings()
        bindings.setBindingByName("x", "use shotgun")

        val input = InputManager(bindings = bindings)
        input.keyDown(Input.Keys.X)
        input.keyUp(Input.Keys.X)

        assertEquals(listOf("use shotgun"), queued)
    }

    @Test
    fun wheelBindingTriggersOncePerScrollEvent() {
        val queued = mutableListOf<String>()
        val bindings = ClientBindings(queueCommand = queued::add)
        bindings.clearBindings()
        bindings.setBindingByName("mwheelup", "weapnext")

        val input = InputManager(bindings = bindings)
        input.scrolled(0f, -1f)

        assertEquals(listOf("weapnext"), queued)
    }

    @Test
    fun multiBindImmediateCommandDoesNotReleaseEarly() {
        val bindings = ClientBindings()
        bindings.clearBindings()
        bindings.setBindingByName("q", "+forward")
        bindings.setBindingByName("e", "+forward")

        val input = InputManager(bindings = bindings)
        val frame = frame()

        input.keyDown(Input.Keys.Q)
        input.keyDown(Input.Keys.E)
        input.keyUp(Input.Keys.Q)

        val stillForward = input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame)
        assertEquals(200, stillForward.newCmd.forwardmove)

        input.keyUp(Input.Keys.E)
        val released = input.gatherInput(outgoingSequence = 1, deltaTime = 0f, currentFrame = frame)
        assertEquals(0, released.newCmd.forwardmove)
    }

    @Test
    fun useButtonBindingSetsButtonUseBit() {
        val bindings = ClientBindings()
        bindings.clearBindings()
        bindings.setBindingByName("u", "+use")

        val input = InputManager(bindings = bindings)
        input.keyDown(Input.Keys.U)

        val msg = input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame())
        assertTrue((msg.newCmd.buttons and BUTTON_USE) != 0)
        assertTrue((msg.newCmd.buttons and BUTTON_ANY) != 0)
    }

    @Test
    fun movementBindingSetsButtonAnyBit() {
        val bindings = ClientBindings()
        bindings.clearBindings()
        bindings.setBindingByName("x", "+forward")

        val input = InputManager(bindings = bindings)
        input.keyDown(Input.Keys.X)

        val msg = input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame())
        assertTrue((msg.newCmd.buttons and BUTTON_ANY) != 0)
    }

    @Test
    fun clearInputStateResetsImmediateControls() {
        val bindings = ClientBindings()
        bindings.clearBindings()
        bindings.setBindingByName("q", "+forward")

        val input = InputManager(bindings = bindings)
        val frame = frame()

        input.keyDown(Input.Keys.Q)
        input.clearInputState()

        val msg = input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame)
        assertEquals(0, msg.newCmd.forwardmove)
    }

    private fun frame(): ClientFrame {
        return ClientFrame().apply {
            playerstate.pmove.pm_type = PM_NORMAL
            playerstate.viewangles[YAW] = 0f
            playerstate.pmove.delta_angles[YAW] = Math3D.ANGLE2SHORT(0f).toShort()
        }
    }
}
