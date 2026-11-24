package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TriggerFamilyBehaviorTest {
    private val occupants = mutableListOf<GameEntity>()
    private val messages = mutableListOf<String?>()
    private val scenario = EntityScenario(object : GameImports by TestGameImports() {
        override fun centerprintf(ent: ServerEntity?, s: String?) { messages.add(s) }

        override fun BoxEdicts(mins: FloatArray?, maxs: FloatArray?, list: Array<ServerEntity?>,
                               maxcount: Int, areatype: Int): Int {
            if (areatype != Defines.AREA_SOLID) return 0
            occupants.forEachIndexed { index, entity -> list[index] = entity }
            return occupants.size
        }
    })
    private val game = scenario.game

    private fun touch(trigger: GameEntity, entity: GameEntity) =
        trigger.touch.touch(trigger, entity, GameBase.dummyplane, null, game)

    @Test
    fun useWithoutActivatorPreservesTheNextValidUse() {
        for (classname in listOf("trigger_multiple", "trigger_once", "trigger_relay", "trigger_counter")) {
            val goal = scenario.spawn("target_goal") { targetname = classname + "_goal" }
            val trigger = scenario.spawn(classname) { target = goal.targetname; count = 1 }
            val before = game.level.found_goals
            scenario.fire(trigger, null)
            assertEquals(before, game.level.found_goals, classname)
            scenario.fire(trigger)
            assertEquals(before + 1, game.level.found_goals, classname)
        }
    }

    @Test
    fun triggeredOnceAcceptsTheOldFlagAndWaitsForActivation() {
        val goal = scenario.spawn("target_goal")
        val trigger = scenario.spawn("trigger_once") { spawnflags = 1; target = goal.targetname }
        assertEquals(Defines.SOLID_NOT, trigger.solid)
        scenario.fire(trigger)
        assertEquals(Defines.SOLID_TRIGGER, trigger.solid)
        assertEquals(0, game.level.found_goals)
        scenario.fire(trigger)
        assertEquals(1, game.level.found_goals)
        game.G_RunFrame()
        assertFalse(trigger.inuse)
    }

    @Test
    fun alwaysWaitsForLaterSpawnsAndMultipleHonorsCooldown() {
        scenario.spawn("trigger_always") { target = "later" }
        scenario.spawn("target_goal") { targetname = "later" }
        game.G_RunFrame()
        assertEquals(0, game.level.found_goals)
        game.G_RunFrame()
        assertEquals(1, game.level.found_goals)
        val counter = scenario.spawn("trigger_counter") { count = 3; spawnflags = 1 }
        val multiple = scenario.spawn("trigger_multiple") { target = counter.targetname }
        scenario.fire(multiple)
        game.G_RunFrame()
        scenario.fire(multiple)
        assertEquals(2, counter.count)
        game.G_RunFrame()
        scenario.fire(multiple)
        assertEquals(1, counter.count)
    }

    @Test
    fun enablingHurtDamagesAnOccupantImmediatelyAndSharesTheTouchCooldown() {
        val stationary = game.G_Spawn().apply { health = 100; takedamage = Defines.DAMAGE_YES }
        occupants.add(stationary)
        val hurt = scenario.spawn("trigger_hurt") { spawnflags = 1 or 2 or 16 }
        scenario.fire(hurt)
        assertEquals(95, stationary.health)
        touch(hurt, stationary)
        assertEquals(95, stationary.health)
        repeat(10) { game.G_RunFrame() }
        touch(hurt, stationary)
        assertEquals(90, stationary.health)
        scenario.fire(hurt)
        assertEquals(Defines.SOLID_NOT, hurt.solid)
        assertEquals(90, stationary.health)
    }

    @Test
    fun monsterJumpOnlyLiftsGroundedWalkers() {
        val jump = scenario.spawn("trigger_monsterjump") { speed = 320f; st.height = 180 }
        val walker = game.G_Spawn().apply { svflags = Defines.SVF_MONSTER }
        walker.velocity[2] = -10f
        touch(jump, walker)
        assertEquals(320f, walker.velocity[0], 0.001f)
        assertEquals(-10f, walker.velocity[2])
        walker.groundentity = game.g_edicts[0]
        touch(jump, walker)
        assertNull(walker.groundentity)
        assertEquals(180f, walker.velocity[2])
    }

    @Test
    fun gravityAppliesTheConfiguredMultiplier() {
        val entity = game.G_Spawn()
        for ((value, expected) in listOf("2" to 2f, "0.5" to 0.5f, "0" to 0f, "-1" to -1f)) {
            touch(scenario.spawn("trigger_gravity") { st.gravity = value }, entity)
            assertEquals(expected, entity.gravity, value)
        }
        assertFalse(scenario.spawn("trigger_gravity").inuse)
        for (invalid in listOf("half", "NaN", "Infinity")) {
            assertFalse(scenario.spawn("trigger_gravity") { st.gravity = invalid }.inuse)
        }
    }

    @Test
    fun pushOnceLaunchesAGrenade() {
        val grenade = game.G_Spawn().apply { classname = "grenade" }
        val push = scenario.spawn("trigger_push") { spawnflags = 1; speed = 70f; s.angles[1] = 90f }
        touch(push, grenade)
        assertEquals(700f, grenade.velocity[1], 0.001f)
        assertFalse(push.inuse)
    }

    @Test
    fun keyReportsMissingInventoryAndUnlocksOnlyOnce() {
        val goal = scenario.spawn("target_goal")
        val key = scenario.spawn("trigger_key") { st.item = "key_data_cd"; target = goal.targetname }
        val player = game.g_edicts[1]
        val client = game.game.clients[0]
        player.setClient(client)
        repeat(2) { scenario.fire(key, player) }
        assertEquals(listOf("You need the " + key.item.pickup_name), messages)
        client.pers.inventory[key.item.index] = 2
        repeat(2) { scenario.fire(key, player) }
        assertEquals(1, client.pers.inventory[key.item.index])
        assertEquals(1, game.level.found_goals)
    }

    @Test
    fun coopKeyRemovesOnlyTheFirstSharedPowerCube() {
        val imports = TestGameImports()
        imports.cvars.setResult("maxclients", "2")
        val coop = EntityScenario(imports)
        coop.game.gameCvars.coop.forceSet(true)
        val goal = coop.spawn("target_goal")
        val lock = coop.spawn("trigger_key") {
            st.item = "key_power_cube"
            target = goal.targetname
        }
        val players = (1..2).map { slot ->
            coop.game.g_edicts[slot].apply {
                inuse = true
                setClient(coop.game.game.clients[slot - 1])
                client.pers.power_cubes = if (slot == 1) 0b0110 else 0b1010
                client.pers.inventory[lock.item.index] = 2
            }
        }
        coop.fire(lock, players.first())
        assertEquals(listOf(0b0100, 0b1000), players.map { it.client.pers.power_cubes })
        assertEquals(listOf(1, 1), players.map { it.client.pers.inventory[lock.item.index] })
        assertEquals(1, coop.game.level.found_goals)
    }

    @Test
    fun elevatorDispatchesAnIdleTrainToTheCallersFloor() {
        scenario.spawn("path_corner") { targetname = "start" }
        scenario.spawn("path_corner") { targetname = "floor"; s.origin[2] = 200f; wait = -1f }
        val train = scenario.spawn("func_train") { target = "start"; speed = 100f }
        val elevator = scenario.spawn("trigger_elevator") { target = train.targetname }
        val button = scenario.spawn("trigger_relay") { target = elevator.targetname; pathtarget = "floor" }
        game.G_RunFrame()
        scenario.fire(button)
        repeat(25) { game.G_RunFrame() }
        assertEquals(200f, train.s.origin[2])
    }

    @Test
    fun elevatorIgnoresADeletedTrain() {
        scenario.spawn("path_corner") { targetname = "start" }
        val stop = scenario.spawn("path_corner") { targetname = "floor"; s.origin[2] = 128f }
        val train = scenario.spawn("func_train") { target = "start" }
        val elevator = scenario.spawn("trigger_elevator") { target = train.targetname }
        val button = scenario.spawn("trigger_relay") { target = elevator.targetname; pathtarget = stop.targetname }
        game.G_RunFrame()
        game.freeEntity(train)
        scenario.fire(button)
        assertNull(train.target_ent)
    }
}
