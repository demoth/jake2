package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LightFamilyBehaviorTest {
    private val writes = mutableListOf<Pair<Int, String?>>()
    private val scenario = EntityScenario(object : GameImports by TestGameImports() {
        override fun configstring(num: Int, string: String?) {
            writes.add(num to string)
        }
    })

    @Test
    fun switchableLightsPublishTheirInitialStyleAndToggleIndependently() {
        val on = scenario.spawn("light") { style = 32 }
        val off = scenario.spawn("light") { style = 33; targetname = "second"; spawnflags = 1 }
        scenario.fire(on)
        scenario.fire(off)
        scenario.fire(on)
        val first = Defines.CS_LIGHTS + 32
        val second = Defines.CS_LIGHTS + 33
        assertEquals(listOf(first to "m", second to "a", first to "a", second to "m", first to "m"), writes)
    }

    @Test
    fun reservedStylesAndDiscardedLightsProduceNoWrites() {
        val reserved = scenario.spawn("light") { style = 31 }
        assertTrue(reserved.inuse)
        scenario.fire(reserved)
        assertFalse(scenario.spawn("light") { targetname = null; style = 32 }.inuse)
        scenario.game.gameCvars.deathmatch.forceSet(true)
        assertFalse(scenario.spawn("light") { style = 32 }.inuse)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun mineLightsLinkTheirStationaryModels() {
        val models = mutableListOf<String?>()
        val linked = mutableListOf<ServerEntity?>()
        val mine = EntityScenario(object : GameImports by TestGameImports() {
            override fun modelindex(name: String?): Int {
                models.add(name)
                return models.size
            }

            override fun linkentity(ent: ServerEntity?) {
                linked.add(ent)
            }
        })
        for (number in 1..2) {
            val fixture = mine.spawn("light_mine$number")
            assertEquals("models/objects/minelite/light$number/tris.md2", models[fixture.s.modelindex - 1])
            assertTrue(fixture in linked)
            assertEquals(Defines.SOLID_BBOX, fixture.solid)
            assertEquals(GameDefines.MOVETYPE_NONE, fixture.movetype)
        }
    }
}
