package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PathFamilyBehaviorTest {
    private lateinit var contact: GameEntity
    private val scenario = EntityScenario(object : GameImports by TestGameImports() {
        override fun BoxEdicts(mins: FloatArray?, maxs: FloatArray?, list: Array<ServerEntity?>,
                               maxcount: Int, areatype: Int): Int {
            if (areatype != Defines.AREA_TRIGGERS) return 0
            list[0] = contact
            return 1
        }
    })
    private val game = scenario.game

    @Test
    fun combatPointFacesItsNextGoalAndConsumesTheRoute() {
        val next = scenario.spawn("point_combat") { targetname = "next"; s.origin[1] = 32f }
        contact = scenario.spawn("point_combat") { target = "next" }
        val enemy = game.G_Spawn()
        val walker = game.G_Spawn().apply {
            s.origin[1] = 64f
            movetarget = contact
            this.enemy = enemy
        }
        GameBase.G_TouchTriggers(walker, game)
        assertSame(next, walker.movetarget)
        assertSame(next, walker.goalentity)
        assertEquals(-90f, walker.ideal_yaw)

        walker.movetarget = contact
        GameBase.G_TouchTriggers(walker, game)
        assertNull(walker.movetarget)
        assertSame(enemy, walker.goalentity)
    }

    @Test
    fun pathCornerFiresItsPathtargetAndSkipsTheTeleportDestination() {
        val goal = scenario.spawn("target_goal")
        contact = scenario.spawn("path_corner") { target = "warp"; pathtarget = goal.targetname }
        scenario.spawn("path_corner") {
            targetname = "warp"
            target = "end"
            spawnflags = 1
            s.origin = floatArrayOf(10f, 20f, 30f)
        }
        val end = scenario.spawn("path_corner") { targetname = "end"; s.origin = floatArrayOf(10f, 50f, 46f) }
        val walker = game.G_Spawn().apply { mins[2] = -24f }
        GameBase.G_TouchTriggers(walker, game)
        walker.movetarget = contact
        walker.enemy = game.G_Spawn()
        GameBase.G_TouchTriggers(walker, game)
        assertEquals(0, game.level.found_goals)
        walker.enemy = null
        GameBase.G_TouchTriggers(walker, game)
        assertEquals(1, game.level.found_goals)
        assertArrayEquals(floatArrayOf(10f, 20f, 46f), walker.s.origin)
        assertEquals(Defines.EV_OTHER_TELEPORT, walker.s.event)
        assertSame(end, walker.movetarget)
        assertSame(end, walker.goalentity)
        assertEquals(90f, walker.ideal_yaw)
    }
}
