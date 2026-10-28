package org.demoth.cake.stages.ingame

import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import jake2.qcommon.network.messages.server.PlayerInfoMessage
import jake2.qcommon.network.messages.server.ServerMessage
import jake2.qcommon.network.messages.server.SpawnBaselineMessage
import jake2.qcommon.network.messages.server.EntityUpdate
import jake2.qcommon.network.messages.server.EntityDelta
import jake2.qcommon.network.messages.server.EntityStateSnapshot
import jake2.qcommon.Defines.*
import jake2.qcommon.entity_state_t
import jake2.qcommon.player_state_t
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.demoth.cake.CakeCvars
import org.junit.jupiter.api.Test

/** Checks frame selection from parsed messages; registers only entity cvars before consumer construction. */
class ClientEntityManagerFrameSelectionTest {

    @Test
    fun immutableBaselineReconstructsTheFirstEntityFrameWithoutSharingProducerState() {
        CakeCvars.registerEntities()
        val manager = ClientEntityManager()
        try {
            val source = entity_state_t(1).apply {
                origin = floatArrayOf(1.125f, 2.25f, 3.5f)
                angles = floatArrayOf(45f, -90f, 0f)
                modelindex = 7; event = 2
            }
            val queued = SpawnBaselineMessage(source)
            source.clear()
            val buffer = sizebuf_t(queued.size).apply { queued.writeTo(this) }
            manager.processBaselineMessage(ServerMessage.parseFromBuffer(buffer) as SpawnBaselineMessage)
            manager.processServerFrameHeader(FrameHeaderMessage(1, -1, 0, 0, byteArrayOf()))
            val updates = PacketEntitiesMessage(listOf(EntityUpdate.Change(EntityDelta(0, EntityStateSnapshot(1)))))
            assertTrue(manager.processPacketEntitiesMessage(updates))
            assertArrayEquals(floatArrayOf(1.125f, 2.25f, 3.5f), manager.getEntityOrigin(1)!!.let { floatArrayOf(it.x, it.y, it.z) })
            assertArrayEquals(floatArrayOf(45f, -90f, 0f), manager.getEntityAngles(1))
            manager.forEachCurrentEntityState {
                assertEquals(7, it.modelindex)
                assertEquals(0, it.event, "Baseline impulse events must not repeat in subsequent entity updates")
            }
        } finally {
            manager.dispose()
        }
    }

    @Test
    fun packetChangesMergePreviousStatesAndBaselinesWhileClearingEventsAndRemovingEntities() {
        CakeCvars.registerEntities()
        val manager = ClientEntityManager()
        fun state(index: Int) = entity_state_t(index).apply {
            modelindex = index
            origin[0] = index.toFloat()
            event = 2
        }
        fun receive(updates: List<EntityUpdate>) {
            val message = PacketEntitiesMessage(updates)
            val buffer = sizebuf_t(message.size).apply { message.writeTo(this) }
            assertTrue(manager.processPacketEntitiesMessage(ServerMessage.parseFromBuffer(buffer) as PacketEntitiesMessage))
        }
        try {
            val first = (1..3).map(::state)
            manager.processBaselineMessage(SpawnBaselineMessage(state(4)))
            manager.processServerFrameHeader(FrameHeaderMessage(1, -1, 0, 0, byteArrayOf()))
            receive(first.map { requireNotNull(EntityUpdate.plan(entity_state_t(it.index), it, true, true)) })
            manager.processServerFrameHeader(FrameHeaderMessage(2, 1, 0, 0, byteArrayOf()))
            receive(listOf(
                // Explicit zeroes reset the old model/origin; entity 2 has no update and survives unchanged.
                requireNotNull(EntityUpdate.plan(first[0], entity_state_t(1), false, false)),
                EntityUpdate.Removal(U_REMOVE, 3),
                // A newly visible entity inherits fields from its baseline.
                EntityUpdate.Change(EntityDelta(0, EntityStateSnapshot(4))),
            ))
            val reconstructed = mutableListOf<entity_state_t>()
            manager.forEachCurrentEntityState { reconstructed += it.getClone() }
            assertEquals(listOf(1, 2, 4), reconstructed.map { it.index })
            assertEquals(listOf(0, 2, 4), reconstructed.map { it.modelindex })
            assertEquals(listOf(0f, 2f, 4f), reconstructed.map { it.origin[0] })
            assertEquals(listOf(1f, 2f, 4f), reconstructed.map { it.old_origin[0] })
            assertEquals(listOf(0, 0, 0), reconstructed.map { it.event })
        } finally {
            manager.dispose()
        }
    }

    @Test
    fun shorterAreaBitsClearThePreviousVisibilityTail() {
        CakeCvars.registerEntities()
        val manager = ClientEntityManager()
        try {
            val size = manager.currentFrame.areabits.size
            manager.processServerFrameHeader(FrameHeaderMessage(1, -1, 0, size, ByteArray(size) { -1 }))
            manager.processServerFrameHeader(FrameHeaderMessage(2, -1, 0, 1, byteArrayOf(3)))
            assertArrayEquals(ByteArray(size).apply { this[0] = 3 }, manager.currentFrame.areabits)
            manager.processServerFrameHeader(FrameHeaderMessage(3, -1, 0, 0, byteArrayOf()))
            assertArrayEquals(ByteArray(size), manager.currentFrame.areabits)
        } finally {
            manager.dispose()
        }
    }

    @Test
    fun previousFrameTracksImmediatePriorServerFrameForInterpolation() {
        CakeCvars.registerEntities()
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
        assertTrue(manager.processPacketEntitiesMessage(PacketEntitiesMessage(emptyList())))
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
