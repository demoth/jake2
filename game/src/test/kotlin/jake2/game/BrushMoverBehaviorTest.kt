package jake2.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrushMoverBehaviorTest {
    private val scenario = EntityScenario(TestGameImports())
    private val game = scenario.game

    @Test
    fun buttonRequiresAnActivatorAndFiresItsTargetAtFullTravel() {
        val goal = scenario.spawn("target_goal")
        val button = scenario.spawn("func_button") {
            size[0] = 20f
            target = goal.targetname
        }
        scenario.fire(button, null)
        repeat(5) { game.G_RunFrame() }
        assertEquals(0f, button.s.origin[0])
        scenario.fire(button)
        repeat(3) { game.G_RunFrame() }
        assertEquals(12f, button.s.origin[0])
        assertEquals(0, game.level.found_goals)
        game.G_RunFrame()
        assertEquals(16f, button.s.origin[0])
        assertEquals(1, game.level.found_goals)
        repeat(34) { game.G_RunFrame() }
        assertEquals(0f, button.s.origin[0])
    }

    @Test
    fun reversedRotatorStopsAndResumesOnItsConfiguredAxis() {
        val rotor = scenario.spawn("func_rotating") { spawnflags = 1 or 2 or 4 }
        game.G_RunFrame()
        assertEquals(-10f, rotor.s.angles[2])
        scenario.fire(rotor)
        game.G_RunFrame()
        assertEquals(-10f, rotor.s.angles[2])
        scenario.fire(rotor)
        game.G_RunFrame()
        assertEquals(-20f, rotor.s.angles[2])
    }

    @Test
    fun toggleableConveyorRestoresItsConfiguredSpeed() {
        val belt = scenario.spawn("func_conveyor") { speed = 80f; spawnflags = 2 }
        assertEquals(0f, belt.speed)
        for (expected in listOf(80f, 0f, 80f)) {
            scenario.fire(belt)
            assertEquals(expected, belt.speed)
        }
    }
}
