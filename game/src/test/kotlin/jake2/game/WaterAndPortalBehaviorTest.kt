package jake2.game

import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WaterAndPortalBehaviorTest {
    private val portalWrites = mutableListOf<Pair<Int, Boolean>>()
    private val samples = listOf("world/mov_watr.wav", "world/stp_watr.wav")
    private val played = mutableListOf<String>()
    private val scenario = EntityScenario(object : GameImports by TestGameImports() {
        override fun SetAreaPortalState(portalnum: Int, open: Boolean) {
            portalWrites.add(portalnum to open)
        }

        override fun soundindex(name: String?) = samples.indexOf(name) + 1

        override fun sound(ent: ServerEntity?, channel: Int, soundindex: Int, volume: Float, attenuation: Float, timeofs: Float) {
            played.add(samples[soundindex - 1])
        }
    })

    private fun advance(frames: Int) = repeat(frames) { scenario.game.G_RunFrame() }

    @Test
    fun portalTogglesOnDirectUse() {
        val portal = scenario.spawn("func_areaportal") { style = 9 }
        assertEquals(emptyList<Pair<Int, Boolean>>(), portalWrites)
        repeat(3) { scenario.fire(portal) }
        assertEquals(listOf(9 to true, 9 to false, 9 to true), portalWrites)
    }

    @Test
    fun waterKeepsItsPortalOpenUntilTheReturnTripFinishes() {
        val portal = scenario.spawn("func_areaportal") { style = 5 }
        val water = scenario.spawn("func_water") {
            s.angles[1] = -1f
            size[2] = 24f
            st.lip = 4
            target = portal.targetname
        }
        scenario.fire(water)
        assertEquals(listOf(5 to true), portalWrites)
        advance(40)
        assertEquals(20f, water.s.origin[2])
        scenario.fire(water)
        advance(2)
        assertEquals(listOf(5 to true), portalWrites)
        advance(10)
        assertEquals(0f, water.s.origin[2])
        assertEquals(listOf(5 to true, 5 to false), portalWrites)
    }

    @Test
    fun startOpenWaterReturnsAfterItsWaitAndUsesTheSelectedSounds() {
        for (preset in listOf(0, 1, 2, 3)) {
            val water = scenario.spawn("func_water") {
                targetname = "water$preset"
                s.angles[1] = -1f
                size[2] = 10f
                spawnflags = 1
                sounds = preset
                wait = 1f
            }
            assertEquals(10f, water.s.origin[2])
            scenario.fire(water)
            advance(7)
            assertEquals(0f, water.s.origin[2])
            advance(20)
            assertEquals(10f, water.s.origin[2])
            val expected = if (preset == 1 || preset == 2) samples + samples else emptyList()
            assertEquals(expected, played, "sound preset $preset")
            played.clear()
        }
    }
}
