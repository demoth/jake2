package jake2.game

import jake2.qcommon.Defines
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HealthPickupBehaviorTest {
    private val scenario = EntityScenario(TestGameImports())
    private val game = scenario.game
    private val player = game.G_Spawn().apply {
        setClient(GamePlayerInfo(0))
        max_health = 100
        health = 100
    }

    private fun advance(frames: Int) = repeat(frames) { game.G_RunFrame() }

    private fun collect(item: GameEntity) =
        item.touch.touch(item, player, GameBase.dummyplane, null, game)

    @Test
    fun noHealthFlagOnlySuppressesPickupsInDeathmatch() {
        game.gameCvars.dmflags.forceSet(Defines.DF_NO_HEALTH)
        val names = listOf("item_health", "item_health_small", "item_health_large", "item_health_mega")
        for (deathmatch in listOf(false, true)) {
            game.gameCvars.deathmatch.forceSet(deathmatch)
            for (name in names) assertEquals(!deathmatch, scenario.spawn(name).inuse, name)
        }
    }

    @Test
    fun regularHealthCapsAtMaximumAndStimpacksCanExceedIt() {
        val medium = scenario.spawn("item_health")
        val large = scenario.spawn("item_health_large")
        val small = scenario.spawn("item_health_small")
        advance(2)
        collect(large)
        assertTrue(large.inuse)
        player.health = 68
        collect(large)
        assertEquals(93, player.health)
        collect(medium)
        assertEquals(100, player.health)
        collect(small)
        assertEquals(102, player.health)
        assertFalse(small.inuse)
    }

    @Test
    fun deathmatchMegaHealthDecaysBeforeItsRespawnTimerStarts() {
        game.gameCvars.deathmatch.forceSet(true)
        val mega = scenario.spawn("item_health_mega")
        val medium = scenario.spawn("item_health")
        advance(2)
        player.health = 80
        collect(medium)
        assertEquals(90, player.health)
        collect(mega)
        assertEquals(190, player.health)
        assertEquals(Defines.SOLID_NOT, mega.solid)
        advance(49)
        assertEquals(190, player.health)
        advance(1)
        assertEquals(189, player.health)
        advance(10)
        assertEquals(188, player.health)
        player.health = 100
        advance(209)
        assertEquals(Defines.SOLID_NOT, mega.solid)
        advance(1)
        assertEquals(Defines.SOLID_TRIGGER, mega.solid)
        advance(29)
        assertEquals(Defines.SOLID_NOT, medium.solid)
        advance(1)
        assertEquals(Defines.SOLID_TRIGGER, medium.solid)
    }
}
