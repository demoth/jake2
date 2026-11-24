package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InfoFamilyBehaviorTest {
    private val scenario = EntityScenario(object : GameImports by TestGameImports() {
        override fun setmodel(ent: ServerEntity, name: String) {
            (ent as GameEntity).model = name
        }
    })
    private val game = scenario.game

    private fun starts(classname: String) = game.g_edicts.filter { it.inuse && it.classname == classname }

    @Test
    fun unnamedStartsCopyOnlyTheMatchingEntranceAfterOneFrame() {
        val entrances = mapOf("mine1" to "mintro", "mine2" to "mine1", "mine3" to "mine2a",
            "mine4" to "mine3", "power2" to "power1", "waste1" to "power2",
            "waste2" to "waste1", "city2" to "city2NL")
        for ((map, entrance) in entrances) {
            game.level.mapname = map.uppercase()
            scenario.spawn("info_player_start") { targetname = "other" }
            val start = scenario.spawn("info_player_start") {
                targetname = entrance.uppercase()
                s.origin = floatArrayOf(16f, 32f, 64f)
                s.angles = floatArrayOf(20f, 90f, 30f)
            }
            val before = starts("info_player_start").size
            game.G_RunFrame()
            val after = starts("info_player_start")
            assertEquals(before + 1, after.size, map)
            val added = after.last()
            assertNull(added.targetname)
            assertArrayEquals(start.s.origin, added.s.origin)
            assertArrayEquals(floatArrayOf(0f, 90f, 0f), added.s.angles)
        }
    }

    @Test
    fun securityAddsThreeCoopSpotsOnlyInCoop() {
        game.level.mapname = "Security"
        scenario.spawn("info_player_start")
        game.G_RunFrame()
        game.gameCvars.coop.forceSet(true)
        scenario.spawn("info_player_start")
        assertTrue(starts("info_player_coop").isEmpty())
        game.G_RunFrame()
        val added = starts("info_player_coop")
        assertEquals(3, added.size)
        for ((entity, x) in added.zip(listOf(124f, 252f, 316f))) {
            assertEquals("jail3", entity.targetname)
            assertArrayEquals(floatArrayOf(x, -164f, 80f), entity.s.origin)
            assertEquals(90f, entity.s.angles[1])
        }
    }

    @Test
    fun repairUsesFirstNamedStartStrictlyWithin550Units() {
        game.level.mapname = "jail2"
        game.gameCvars.coop.forceSet(true)
        val coop = scenario.spawn("info_player_coop") { targetname = "wrong" }
        scenario.spawn("info_player_start") { targetname = null }
        scenario.spawn("info_player_start") { targetname = "boundary"; s.origin[0] = 550f }
        scenario.spawn("info_player_start") { targetname = "entrance"; s.origin[0] = 549f }
        scenario.spawn("info_player_start") { targetname = "closer" }
        assertEquals("wrong", coop.targetname)
        game.G_RunFrame()
        assertEquals("entrance", coop.targetname)
    }

    @Test
    fun repairRecognizesAffectedMapsAndPreservesEquivalentNames() {
        game.gameCvars.coop.forceSet(true)
        scenario.spawn("info_player_start") { targetname = "Exit" }
        val maps = listOf("JAIL2", "jail4", "MiNtRo", "mine1", "mine2", "mine3", "mine4",
            "lab", "boss1", "FACT1", "fact3", "waste1", "biggun", "space", "command",
            "power2", "strike", "city2", "base1")
        for (map in maps) {
            game.level.mapname = map
            val missing = scenario.spawn("info_player_coop") { targetname = null }
            val matching = scenario.spawn("info_player_coop") { targetname = "EXIT" }
            game.G_RunFrame()
            assertEquals(if (map == "base1") null else "Exit", missing.targetname, map)
            assertEquals("EXIT", matching.targetname)
        }
    }

    @Test
    fun unrelatedMapKeepsItsOriginalStart() {
        game.level.mapname = "base1"
        scenario.spawn("info_player_start") { targetname = "mintro" }
        game.G_RunFrame()
        assertEquals(1, starts("info_player_start").size)
    }

    @Test
    fun deathmatchPadAndPositionalTargetExposeTheirMapProperties() {
        assertFalse(scenario.spawn("info_player_coop").inuse)
        assertFalse(scenario.spawn("info_player_deathmatch").inuse)
        game.gameCvars.deathmatch.forceSet(true)
        val pad = scenario.spawn("info_player_deathmatch")
        assertTrue(pad.inuse)
        assertEquals("models/objects/dmspot/tris.md2", pad.model)
        assertEquals(Defines.SOLID_BBOX, pad.solid)
        assertArrayEquals(floatArrayOf(-32f, -32f, -24f), pad.mins)
        assertArrayEquals(floatArrayOf(32f, 32f, -16f), pad.maxs)
        val point = scenario.spawn("info_notnull") { s.origin = floatArrayOf(3f, -1f, 8f) }
        assertArrayEquals(point.s.origin, point.absmin)
        assertArrayEquals(point.s.origin, point.absmax)
    }

    @Test
    fun optionalEntranceCanBeSkippedWhenEverySlotIsInUse() {
        game.level.mapname = "mine1"
        scenario.spawn("info_player_start") { targetname = "mintro" }
        while (game.num_edicts < game.game.maxentities) game.G_Spawn()
        game.G_RunFrame()
        assertEquals(1, starts("info_player_start").size)
    }
}
