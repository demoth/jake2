package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.network.MulticastTypes
import jake2.qcommon.network.messages.server.PointTEMessage
import jake2.qcommon.network.messages.server.ServerMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class BrushMoverBehaviorTest {
    private val scenario = EntityScenario(TestGameImports())
    private val game = scenario.game

    @Test
    fun platformWaitsOnceForCoopRidersBeforeStartingItsMotor() {
        fun checkDeparture(coop: Boolean, delay: Float?, waitFrames: Int) {
            val world = EntityScenario(object : GameImports by TestGameImports() {
                override fun soundindex(name: String?) = if (name == "plats/pt1_mid.wav") 1 else 0
            })
            val game = world.game
            game.gameCvars.coop.forceSet(coop)
            if (delay != null) game.gameCvars.coop_elevator_delay.forceSet(delay)
            val plat = world.spawn("func_plat") { targetname = null; st.height = 64 }
            val trigger = game.g_edicts.single { it.inuse && it.solid == Defines.SOLID_TRIGGER }
            val rider = game.G_Spawn().apply { setClient(GamePlayerInfo(0)); health = 100 }
            fun board() = trigger.touch.touch(trigger, rider, GameBase.dummyplane, null, game)
            board()
            repeat(waitFrames) {
                assertEquals(0, plat.s.sound)
                board()
                game.G_RunFrame()
            }
            assertEquals(1, plat.s.sound)
            repeat(15) { game.G_RunFrame() }
            assertEquals(0f, plat.s.origin[2])
        }
        checkDeparture(coop = true, delay = null, waitFrames = 10)
        checkDeparture(coop = true, delay = 0.4f, waitFrames = 4)
        checkDeparture(coop = true, delay = 0f, waitFrames = 0)
        checkDeparture(coop = false, delay = 1f, waitFrames = 0)
    }

    @Test
    fun moversExplodeDebrisAtItsWorldBoundsCenter() {
        val messages = mutableListOf<ServerMessage>()
        val world = EntityScenario(object : GameImports by TestGameImports() {
            override fun multicastMessage(origin: FloatArray?, msg: ServerMessage, to: MulticastTypes?) {
                messages.add(msg)
            }
        })
        for (classname in listOf("func_plat", "func_train", "func_door", "func_door_rotating", "func_door_secret")) {
            val mover = world.spawn(classname)
            val debris = world.game.G_Spawn().apply {
                absmin = floatArrayOf(100f, 200f, 300f)
                size = floatArrayOf(16f, 24f, 32f)
            }
            mover.blocked.blocked(mover, debris, world.game)
            assertFalse(debris.inuse)
            assertEquals(PointTEMessage(Defines.TE_EXPLOSION1, floatArrayOf(108f, 212f, 316f)), messages.single(), classname)
            messages.clear()
        }
    }

    @Test
    fun namedPlatformLowersOnUseAndWaitsAtTheBottom() {
        val plat = scenario.spawn("func_plat") { st.height = 64; s.origin[2] = 96f }
        repeat(10) { game.G_RunFrame() }
        assertEquals(96f, plat.s.origin[2])
        scenario.fire(plat)
        repeat(30) { game.G_RunFrame() }
        assertEquals(32f, plat.s.origin[2])
        assertEquals(0f, plat.velocity[2])
    }

    @Test
    fun trainResumesFromNegativeWaitCornerAndFiresItsArrivalTarget() {
        val goal = scenario.spawn("target_goal")
        scenario.spawn("path_corner") { targetname = "start"; target = "stop" }
        scenario.spawn("path_corner") {
            targetname = "stop"; target = "end"; s.origin[0] = 50f; wait = -1f
        }
        scenario.spawn("path_corner") { targetname = "end"; s.origin[0] = 90f; pathtarget = goal.targetname }
        val train = scenario.spawn("func_train") { target = "start"; spawnflags = 2; mins[0] = -8f }
        game.G_RunFrame()
        assertEquals(8f, train.s.origin[0])
        scenario.fire(train)
        repeat(20) { game.G_RunFrame() }
        assertEquals(58f, train.s.origin[0])
        assertEquals(0, game.level.found_goals)
        scenario.fire(train)
        repeat(10) { game.G_RunFrame() }
        assertEquals(98f, train.s.origin[0])
        assertEquals(1, game.level.found_goals)
    }

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
