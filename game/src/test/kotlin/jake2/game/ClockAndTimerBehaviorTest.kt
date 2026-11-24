package jake2.game

import jake2.qcommon.GameImports
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClockAndTimerBehaviorTest {
    private val writes = mutableListOf<Boolean>()
    private val scene = EntityScenario(object : GameImports by TestGameImports() {
        override fun SetAreaPortalState(portalnum: Int, open: Boolean) { writes.add(open) }
    })
    private fun advance(frames: Int) = repeat(frames) { scene.game.G_RunFrame() }

    @Test
    fun timerKeepsItsScheduleUntilToggledByAnActivator() {
        val portal = scene.spawn("func_areaportal")
        val timer = scene.spawn("func_timer") { target = portal.targetname; spawnflags = 1; wait = 0.5f; st.pausetime = 1f }
        scene.fire(timer, null)
        advance(24)
        assertTrue(writes.isEmpty())
        advance(6)
        assertEquals(listOf(true, false), writes)
        scene.fire(timer)
        advance(10)
        assertEquals(2, writes.size)
        scene.fire(timer)
        assertEquals(listOf(true, false, true), writes)
    }

    @Test
    fun countdownDisplaysPaddedDigitsWithoutReplacingTheDisplaysOwnMessage() {
        val display = scene.spawn("target_string") { message = "12345678" }
        val digits = (1..8).map { scene.spawn("target_character") { count = it } }
        display.teammaster = digits.first()
        digits.zipWithNext().forEach { (a, b) -> a.teamchain = b }
        scene.spawn("func_clock") { target = display.targetname; count = 3662; style = 2; spawnflags = 2 }
        advance(10)
        assertEquals(listOf(12, 1, 11, 0, 1, 11, 0, 2), digits.map { it.s.frame })
        scene.fire(display)
        assertEquals((1..8).toList(), digits.map { it.s.frame })
    }

    @Test
    fun restartableClockFiresAtZeroEvenWithoutItsDisplay() {
        val portal = scene.spawn("func_areaportal")
        val clock = scene.spawn("func_clock") {
            target = "absent"; pathtarget = portal.targetname; count = 1; spawnflags = 2 or 4 or 8
        }
        repeat(2) { run ->
            scene.fire(clock)
            advance(9)
            assertEquals(run, writes.size)
            advance(1)
            assertEquals(run + 1, writes.size)
        }
    }

    @Test
    fun oneShotClockIgnoresANonDisplayTargetAndExpiresAfterItsFinalTick() {
        val portal = scene.spawn("func_areaportal")
        val clock = scene.spawn("func_clock") { target = portal.targetname; count = 1; spawnflags = 1 }
        advance(20)
        assertTrue(clock.inuse)
        assertTrue(writes.isEmpty())
        advance(10)
        assertFalse(clock.inuse)
    }
}
