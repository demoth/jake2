package jake2.game

import jake2.game.adapters.SuperAdapter
import jake2.game.components.*
import jake2.game.func.MovementState
import jake2.qcommon.math.Vector3f
import jake2.qcommon.save.SaveJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EntityComponentsSnapshotTest {
    @Test
    fun `component state survives JSON round trip into an empty entity`() {
        val entity = GameEntity(1)
        val callback = SuperAdapter.registerThink("test.component.end") { _, _ -> true }
        val movement = entity.addComponent(MoveInfo(
            start_origin = Vector3f(1f, 2f, 3f), end_origin = Vector3f(4f, 5f, 6f),
            speed = 20f, accel = 2f, decel = 3f, remaining_distance = 90f,
            state = MovementState.UP, endfunc = callback
        ))
        movement.platCalculateAcceleratedMove()
        repeat(3) {
            movement.platAccelerate()
            movement.remaining_distance -= movement.current_speed
        }
        entity.addComponent(Medkit(100, 12, ignoreMax = true, timed = true))
        entity.addComponent(Light(35, false))
        entity.addComponent(LightRamp(97, 122, 3f, targetTime = 10f,
            toggleable = true, fraction = 0.4f, targetLightStyle = 35))
        entity.addComponent(AreaPortal(4, true))
        entity.addComponent(Earthquake(13, duration = 9f, magnitude = 250f, soundTime = 4f, stopTime = 12f))

        val bytes = ByteArrayOutputStream().also { SaveJson.write(it, EntityComponentsSnapshot.capture(entity)) }
        val snapshot = SaveJson.read(ByteArrayInputStream(bytes.toByteArray()), EntityComponentsSnapshot::class.java)
        val restored = GameEntity(1)
        snapshot.restore(restored)
        val restoredMovement = restored.getComponent<MoveInfo>()!!
        assertSame(callback, restoredMovement.endfunc)
        assertEquals(movement.state, restoredMovement.state)
        assertArrayEquals(movement.start_origin.toArray(), restoredMovement.start_origin.toArray())
        // Continuing acceleration/deceleration also checks the private movement bookkeeping.
        repeat(20) {
            movement.platAccelerate()
            restoredMovement.platAccelerate()
            assertEquals(movement.current_speed, restoredMovement.current_speed)
            movement.remaining_distance -= movement.current_speed
            restoredMovement.remaining_distance -= restoredMovement.current_speed
        }
        for ((key, value) in entity.components) {
            if (value is MoveInfo) continue
            assertEquals(value, restored.components[key])
            assertNotSame(value, restored.components[key])
        }
    }
}
