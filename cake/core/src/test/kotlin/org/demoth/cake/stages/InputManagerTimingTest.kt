package org.demoth.cake.stages

import com.badlogic.gdx.Input
import jake2.qcommon.Defines.CMD_BACKUP
import jake2.qcommon.Defines.PM_NORMAL
import jake2.qcommon.Defines.YAW
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InputManagerTimingTest {

    @Test
    fun commandMsecUsesElapsedTimeAndClamp() {
        val clock = FakeClock(1_000_000_000L)
        val input = InputManager { clock.nowNanos }
        val frame = frame()

        val first = input.gatherInput(outgoingSequence = 0, deltaTime = 0f, currentFrame = frame)
        assertEquals(16, first.newCmd.msec.toInt() and 0xFF)

        clock.advanceMs(7)
        val second = input.gatherInput(outgoingSequence = 1, deltaTime = 0f, currentFrame = frame)
        assertEquals(7, second.newCmd.msec.toInt() and 0xFF)

        val third = input.gatherInput(outgoingSequence = 2, deltaTime = 0f, currentFrame = frame)
        assertEquals(1, third.newCmd.msec.toInt() and 0xFF)

        clock.advanceMs(500)
        val fourth = input.gatherInput(outgoingSequence = 3, deltaTime = 0f, currentFrame = frame)
        assertEquals(250, fourth.newCmd.msec.toInt() and 0xFF)
    }

    @Test
    fun commandHistoryIsStoredBySequenceAndEvictedOnRingWrap() {
        val clock = FakeClock(1_000_000_000L)
        val input = InputManager { clock.nowNanos }
        val frame = frame()

        input.keyDown(Input.Keys.W)
        input.gatherInput(outgoingSequence = 10, deltaTime = 0f, currentFrame = frame)

        assertNotNull(input.getCommandForSequence(10))
        assertEquals(100, input.getCommandForSequence(10)?.forwardmove?.toInt())
        assertEquals(clock.nowNanos, input.getCommandTimestampNanos(10))
        assertNull(input.getCommandForSequence(9))

        for (sequence in 11..(10 + CMD_BACKUP)) {
            clock.advanceMs(1)
            input.gatherInput(outgoingSequence = sequence, deltaTime = 0f, currentFrame = frame)
        }

        assertNull(input.getCommandForSequence(10))
        assertNotNull(input.getCommandForSequence(10 + CMD_BACKUP))
    }

    private fun frame(): ClientFrame {
        return ClientFrame().apply {
            playerstate.pmove.pm_type = PM_NORMAL
            playerstate.viewangles[YAW] = 0f
            playerstate.pmove.delta_angles[YAW] = Math3D.ANGLE2SHORT(0f).toShort()
        }
    }

    private data class FakeClock(var nowNanos: Long) {
        fun advanceMs(ms: Long) {
            nowNanos += ms * 1_000_000L
        }
    }
}
