package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MiscFamilyBehaviorTest {
    private val models = mutableListOf<String?>()
    private val scenario = EntityScenario(object : GameImports by TestGameImports() {
        override fun setmodel(ent: ServerEntity?, name: String?) {
            models.add(name)
        }
    })
    private val game = scenario.game

    @Test
    fun teleporterMovesThePlayerAboveItsDestinationAndEmitsBothEvents() {
        val destination = scenario.spawn("misc_teleporter_dest") {
            targetname = "exit"
            s.origin = floatArrayOf(64f, 32f, 16f)
            s.angles[1] = 90f
        }
        val pad = scenario.spawn("misc_teleporter") { target = destination.targetname }
        val trigger = game.g_edicts.single { it.inuse && it.solid == Defines.SOLID_TRIGGER }
        val player = game.g_edicts[1]
        val client = game.game.clients[0]
        player.setClient(client)
        player.velocity[0] = 100f
        trigger.touch.touch(trigger, player, GameBase.dummyplane, null, game)
        assertArrayEquals(floatArrayOf(64f, 32f, 26f), player.s.origin)
        assertArrayEquals(destination.s.origin, player.s.old_origin)
        assertArrayEquals(FloatArray(3), player.velocity)
        assertEquals(20, client.playerState.pmove.pm_time.toInt())
        assertEquals(16384, client.playerState.pmove.delta_angles[1].toInt())
        assertEquals(Defines.EV_PLAYER_TELEPORT, pad.s.event)
        assertEquals(Defines.EV_PLAYER_TELEPORT, player.s.event)
    }

    @Test
    fun bannerLoopsAndDishStopsUntilTriggeredAgain() {
        val banner = scenario.spawn("misc_banner")
        val dish = scenario.spawn("misc_satellite_dish")
        val initial = banner.s.frame
        scenario.fire(dish)
        repeat(16) { game.G_RunFrame() }
        assertEquals(initial, banner.s.frame)
        assertEquals(16, dish.s.frame)
        repeat(24) { game.G_RunFrame() }
        assertEquals(38, dish.s.frame)
        scenario.fire(dish)
        game.G_RunFrame()
        assertEquals(1, dish.s.frame)
    }

    @Test
    fun mapGibsAreSolidUntilTheyExpire() {
        val gibs = listOf("arm", "leg", "head").map { scenario.spawn("misc_gib_$it") }
        assertTrue(gibs.all { it.solid == Defines.SOLID_BBOX })
        repeat(299) { game.G_RunFrame() }
        assertTrue(gibs.all { it.inuse })
        game.G_RunFrame()
        assertTrue(gibs.none { it.inuse })
    }

    @Test
    fun deadSoldierBreaksIntoFourMeatGibsAndAHead() {
        val soldier = scenario.spawn("misc_deadsoldier")
        GameCombat.T_Damage(soldier, soldier, soldier, FloatArray(3), soldier.s.origin,
            FloatArray(3), 80, 0, 0, GameDefines.MOD_EXPLOSIVE, game)
        assertEquals(4, models.count { it == "models/objects/gibs/sm_meat/tris.md2" })
        assertEquals(1, models.count { it == "models/objects/gibs/head2/tris.md2" })
    }

    @Test
    fun blackholeSetsItsShadowFlagAfterSpawnAndDisappearsOnUse() {
        val hole = scenario.spawn("misc_blackhole")
        assertEquals(Defines.RF_TRANSLUCENT, hole.s.renderfx)
        game.G_RunFrame()
        assertEquals(Defines.RF_TRANSLUCENT or Defines.RF_NOSHADOW, hole.s.renderfx)
        assertEquals(0, hole.s.frame)
        game.G_RunFrame()
        assertEquals(1, hole.s.frame)
        scenario.fire(hole)
        assertFalse(hole.inuse)
    }

    @Test
    fun shipsWaitForAnActivatorBeforeStartingTheirFlight() {
        scenario.spawn("path_corner") { targetname = "start"; target = "end" }
        scenario.spawn("path_corner") { targetname = "end"; s.origin[0] = 600f }
        val ships = listOf("misc_viper", "misc_strogg_ship").map {
            scenario.spawn(it) { target = "start" }
        }
        game.G_RunFrame()
        for (ship in ships) {
            scenario.fire(ship, null)
            assertTrue(ship.svflags and Defines.SVF_NOCLIENT != 0, ship.classname)
            scenario.fire(ship)
            assertEquals(0, ship.svflags and Defines.SVF_NOCLIENT)
        }
        repeat(2) { game.G_RunFrame() }
        for (ship in ships) assertTrue(ship.s.origin[0] > 16f, ship.classname)
    }

    @Test
    fun bombNeedsAnActivatorAndInheritsTheMovingVipersVelocity() {
        scenario.spawn("path_corner") { targetname = "first"; target = "second" }
        scenario.spawn("path_corner") { targetname = "second"; s.origin[0] = 500f }
        val ship = scenario.spawn("misc_viper") { target = "first"; speed = 200f }
        val bomb = scenario.spawn("misc_viper_bomb")
        game.G_RunFrame()
        scenario.fire(ship)
        game.G_RunFrame()
        scenario.fire(bomb, null)
        assertEquals(Defines.SOLID_NOT, bomb.solid)
        scenario.fire(bomb)
        assertEquals(Defines.SOLID_BBOX, bomb.solid)
        assertArrayEquals(floatArrayOf(200f, 0f, 0f), bomb.velocity)
    }

    @Test
    fun barrelNeedsAnAttackerToExplode() {
        val barrel = scenario.spawn("misc_explobox")
        barrel.die.die(barrel, barrel, null, 10, barrel.s.origin, game)
        assertEquals(Defines.DAMAGE_YES, barrel.takedamage)
        GameCombat.T_Damage(barrel, barrel, barrel, FloatArray(3), barrel.s.origin,
            FloatArray(3), 10, 0, 0, GameDefines.MOD_EXPLOSIVE, game)
        game.G_RunFrame()
        assertTrue(barrel.inuse)
        game.G_RunFrame()
        assertFalse(barrel.inuse)
    }
}
