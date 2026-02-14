package org.demoth.cake.stages.ingame

import jake2.qcommon.Defines
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.messages.server.DeltaEntityHeader
import jake2.qcommon.network.messages.server.EntityUpdate
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import org.demoth.cake.ClientEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ClientEntityManagerTest {

    @Test
    fun teleportEventUsesNoLerpReappearedPath() {
        val manager = ClientEntityManager()
        try {
            val entityNumber = 7
            val initialOrigin = floatArrayOf(100f, 0f, 0f)
            val teleportedOrigin = floatArrayOf(160f, 32f, 8f)

            manager.processServerFrameHeader(frameHeader(frameNumber = 1, lastFrame = 0))
            val firstFrameEntities = PacketEntitiesMessage().apply {
                updates += update(
                    number = entityNumber,
                    flags = Defines.U_MODEL or Defines.U_ORIGIN1 or Defines.U_ORIGIN2 or Defines.U_ORIGIN3,
                ) {
                    modelindex = 1
                    origin = initialOrigin.copyOf()
                }
            }
            manager.processPacketEntitiesMessage(firstFrameEntities)

            manager.processServerFrameHeader(frameHeader(frameNumber = 2, lastFrame = 1))
            val secondFrameEntities = PacketEntitiesMessage().apply {
                updates += update(
                    number = entityNumber,
                    flags = Defines.U_EVENT or Defines.U_ORIGIN1 or Defines.U_ORIGIN2 or Defines.U_ORIGIN3,
                ) {
                    event = Defines.EV_OTHER_TELEPORT
                    origin = teleportedOrigin.copyOf()
                }
            }
            manager.processPacketEntitiesMessage(secondFrameEntities)

            val entity = getClientEntity(manager, entityNumber)
            assertArrayEquals(teleportedOrigin, entity.current.origin, 0f)
            assertArrayEquals(teleportedOrigin, entity.prev.origin, 0f)
        } finally {
            manager.dispose()
        }
    }

    private fun frameHeader(frameNumber: Int, lastFrame: Int): FrameHeaderMessage {
        return FrameHeaderMessage(frameNumber, lastFrame, 0, 0, byteArrayOf())
    }

    private fun update(number: Int, flags: Int, apply: entity_state_t.() -> Unit): EntityUpdate {
        val newState = entity_state_t(number).apply(apply)
        return EntityUpdate(DeltaEntityHeader(flags, number), newState)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getClientEntity(manager: ClientEntityManager, number: Int): ClientEntity {
        val field = ClientEntityManager::class.java.getDeclaredField("clientEntities")
        field.isAccessible = true
        val entities = field.get(manager) as Array<ClientEntity>
        return entities[number]
    }
}
