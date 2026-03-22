package org.demoth.cake.input

import jake2.qcommon.Defines.PITCH
import jake2.qcommon.Defines.PM_NORMAL
import jake2.qcommon.Defines.YAW
import jake2.qcommon.network.messages.client.ClientMessage
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.sizebuf_t
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientFrame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputManagerDeltaCompressionTest {

    @Test
    fun invalidCurrentFrameRequestsFullFrameResync() {
        val input = InputManager(nowNanosProvider = { 1_000_000L })
        val msg = input.gatherInput(
            outgoingSequence = 5,
            deltaTime = 0f,
            currentFrame = frame(serverframe = 77, valid = false),
        )

        val parsed = roundTrip(msg, incomingSequence = 5)

        assertTrue(
            parsed.lastReceivedFrame <= 0,
            "invalid client frame should disable delta compression for the next move packet",
        )
    }

    private fun roundTrip(message: MoveMessage, incomingSequence: Int): MoveMessage {
        val buffer = sizebuf_t()
        buffer.init(ByteArray(256), 256)
        message.writeTo(buffer)
        return ClientMessage.parseFromBuffer(buffer, incomingSequence) as MoveMessage
    }

    private fun frame(serverframe: Int, valid: Boolean): ClientFrame {
        return ClientFrame().apply {
            this.serverframe = serverframe
            this.valid = valid
            playerstate.pmove.pm_type = PM_NORMAL
            playerstate.viewangles[YAW] = 15f
            playerstate.viewangles[PITCH] = 5f
            playerstate.pmove.delta_angles[YAW] = Math3D.ANGLE2SHORT(10f).toShort()
            playerstate.pmove.delta_angles[PITCH] = Math3D.ANGLE2SHORT(2f).toShort()
        }
    }
}
