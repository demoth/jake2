package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.vfs.EngineWriteRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DoorBehaviorTest {
    private val scenario = EntityScenario(TestGameImports())
    private val game = scenario.game
    private fun advance(frames: Int) = repeat(frames) { game.G_RunFrame() }

    @Test
    fun slidingDoorOpensForAnAttackerAndRearmsAfterClosing() {
        val door = scenario.spawn("func_door") { size[0] = 48f; health = 10; wait = 1f }
        advance(1)
        door.die.die(door, door, null, 10, door.s.origin, game)
        advance(5)
        assertEquals(0f, door.s.origin[0])
        val attacker = game.G_Spawn()
        GameCombat.T_Damage(door, attacker, attacker, FloatArray(3), door.s.origin,
            FloatArray(3), 10, 0, 0, GameDefines.MOD_BLASTER, game)
        advance(6)
        assertEquals(40f, door.s.origin[0])
        advance(20)
        assertEquals(0f, door.s.origin[0])
        assertEquals(Defines.DAMAGE_YES, door.takedamage)
    }

    @Test
    fun secretDoorOpensInTwoStagesAndReturns() {
        val door = scenario.spawn("func_door_secret") {
            spawnflags = 1 or 4
            size = floatArrayOf(20f, 40f, 10f)
            wait = 1f
        }
        door.die.die(door, door, null, 1, door.s.origin, game)
        assertEquals(Defines.DAMAGE_YES, door.takedamage)
        scenario.fire(door)
        advance(6)
        assertEquals(-10f, door.s.origin[2])
        assertEquals(0f, door.s.origin[0])
        advance(14)
        assertEquals(20f, door.s.origin[0])
        advance(40)
        assertEquals(0f, door.s.origin[0])
        assertEquals(0f, door.s.origin[2])
    }

    @Test
    fun rotatingDoorFinishesItsPartialTurnAfterLoad(@TempDir directory: Path) {
        val door = scenario.spawn("func_door_rotating") { st.distance = 95; spawnflags = 32 }
        val index = door.index
        advance(1)
        scenario.fire(door)
        advance(3)
        val originalRoot = EngineWriteRoot.path()
        try {
            EngineWriteRoot.setRoot(directory)
            game.WriteLevel("door.sav.json")
            advance(12)
            assertEquals(95f, door.s.angles[1])
            game.ReadLevel("door.sav.json")
            advance(12)
            val restored = game.g_edicts[index]
            assertEquals(95f, restored.s.angles[1])
            scenario.fire(restored)
            advance(12)
            assertEquals(0f, restored.s.angles[1])
        } finally {
            EngineWriteRoot.setRoot(originalRoot)
        }
    }
}
