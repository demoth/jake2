package org.demoth.cake.stages.ingame

import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import jake2.qcommon.network.messages.server.PlayerInfoMessage
import jake2.qcommon.network.messages.server.ServerMessage
import jake2.qcommon.player_state_t
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientEntityManagerFrameSelectionTest {

    @Test
    fun previousFrameTracksImmediatePriorServerFrameForInterpolation() {
        val manager = ClientEntityManager()
        try {
            processFrame(manager, frameNumber = 1, lastFrame = 0, previousGunFrame = 0, currentGunFrame = 10)
            processFrame(manager, frameNumber = 2, lastFrame = 1, previousGunFrame = 10, currentGunFrame = 20)
            processFrame(manager, frameNumber = 3, lastFrame = 1, previousGunFrame = 10, currentGunFrame = 30)

            assertEquals(2, manager.previousFrame?.serverframe)
            assertEquals(20, manager.previousFrame?.playerstate?.gunframe)
        } finally {
            manager.dispose()
        }
    }

    private fun processFrame(
        manager: ClientEntityManager,
        frameNumber: Int,
        lastFrame: Int,
        previousGunFrame: Int,
        currentGunFrame: Int,
    ) {
        manager.processServerFrameHeader(FrameHeaderMessage(frameNumber, lastFrame, 0, 0, byteArrayOf()))
        manager.processPlayerInfoMessage(
            roundTrip(
                PlayerInfoMessage(
                    playerState(previousGunFrame),
                    playerState(currentGunFrame),
                ),
            ),
        )
        assertTrue(manager.processPacketEntitiesMessage(PacketEntitiesMessage()))
    }

    private fun playerState(gunFrame: Int): player_state_t {
        return player_state_t().apply {
            gunindex = 1
            this.gunframe = gunFrame
        }
    }

    // The runtime receives parsed network messages, not freshly constructed DTOs. Round-tripping
    // here forces PlayerInfoMessage to compute/write its delta flags first so the test exercises
    // the same decoded shape that processPlayerInfoMessage sees in production.
    private fun roundTrip(message: PlayerInfoMessage): PlayerInfoMessage {
        val buffer = sizebuf_t()
        buffer.init(ByteArray(256), 256)
        message.writeTo(buffer)
        return ServerMessage.parseFromBuffer(buffer) as PlayerInfoMessage
    }
}
