package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import jake2.qcommon.trace_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UtilityBrushBehaviorTest {
    private var occupant: GameEntity? = null
    private val scene = EntityScenario(object : GameImports by TestGameImports() {
        override fun trace(start: FloatArray, mins: FloatArray?, maxs: FloatArray?, end: FloatArray,
                           passent: ServerEntity?, contentmask: Int) = trace_t().apply {
            fraction = 1f
            endpos = end.copyOf()
            if (contentmask == Defines.MASK_PLAYERSOLID && occupant?.inuse == true) ent = occupant
        }
    })
    private val game = scene.game

    @Test
    fun killboxAndAppearingWallClearTheirOccupiedVolume() {
        val killbox = scene.spawn("func_killbox")
        val wall = scene.spawn("func_wall") { spawnflags = 1 or 2 }
        assertEquals(Defines.SOLID_NOT, wall.solid)
        for (brush in listOf(killbox, killbox, wall)) {
            val blocker = scene.spawn("func_explosive") { targetname = null; flags = GameDefines.FL_GODMODE }
            occupant = blocker
            scene.fire(brush)
            assertFalse(blocker.inuse)
        }
        assertEquals(Defines.SOLID_BSP, wall.solid)
        scene.fire(wall)
        assertEquals(Defines.SOLID_NOT, wall.solid)
        assertTrue(wall.svflags and Defines.SVF_NOCLIENT != 0)
    }

    @Test
    fun fallingObjectReleasesAfterTwoFramesOrOnUse() {
        val objectBrush = scene.spawn("func_object")
        val triggered = scene.spawn("func_object") { spawnflags = 1 }
        game.G_RunFrame()
        assertEquals(GameDefines.MOVETYPE_PUSH, objectBrush.movetype)
        game.G_RunFrame()
        assertEquals(GameDefines.MOVETYPE_TOSS, objectBrush.movetype)
        assertEquals(Defines.SOLID_NOT, triggered.solid)
        scene.fire(triggered)
        assertEquals(Defines.SOLID_BSP, triggered.solid)
        assertEquals(GameDefines.MOVETYPE_TOSS, triggered.movetype)
        game.G_RunFrame()
        assertTrue(triggered.s.origin[2] < 0f)
    }

    @Test
    fun negativeDebrisMassProducesNoChunks() {
        val brush = scene.spawn("func_explosive") { mass = -25 }
        scene.fire(brush)
        assertFalse(brush.inuse)
        assertFalse(game.g_edicts.any { it.inuse && it.classname == "debris" })
    }

    @Test
    fun explosiveNeedsDamageParticipantsBeforeBreakingAndFiringTargets() {
        val goal = scene.spawn("target_goal")
        val brush = scene.spawn("func_explosive") { targetname = null; target = goal.targetname; mass = 100 }
        val attacker = game.G_Spawn()
        brush.die.die(brush, attacker, null, 100, brush.s.origin, game)
        assertTrue(brush.inuse)
        brush.die.die(brush, null, attacker, 100, brush.s.origin, game)
        assertTrue(brush.inuse)
        assertEquals(0, game.level.found_goals)
        GameCombat.T_Damage(brush, attacker, attacker, FloatArray(3), brush.s.origin, FloatArray(3),
            100, 0, 0, GameDefines.MOD_BLASTER, game)
        assertFalse(brush.inuse)
        assertEquals(1, game.level.found_goals)
        assertEquals(5, game.g_edicts.count { it.inuse && it.classname == "debris" })
    }
}
