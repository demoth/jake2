package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import jake2.qcommon.network.MulticastTypes
import jake2.qcommon.network.messages.server.PointTEMessage
import jake2.qcommon.network.messages.server.ServerMessage
import jake2.qcommon.trace_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TargetFamilyBehaviorTest {
    private val messages = mutableListOf<ServerMessage>()
    private var positionedSounds = 0
    private var traceHit: GameEntity? = null
    private val scenario = EntityScenario(object : GameImports by TestGameImports() {
        override fun positioned_sound(origin: FloatArray?, ent: ServerEntity?, channel: Int, soundinedex: Int,
                                      volume: Float, attenuation: Float, timeofs: Float) {
            positionedSounds++
        }
        override fun soundindex(name: String?) = if (name == "world/test.wav") 3 else 0
        override fun multicastMessage(origin: FloatArray?, msg: ServerMessage, to: MulticastTypes?) {
            messages.add(msg)
        }
        override fun trace(start: FloatArray, mins: FloatArray?, maxs: FloatArray?, end: FloatArray,
                           passent: ServerEntity?, contentmask: Int) = trace_t().apply {
            ent = traceHit
            fraction = if (ent == null) 1f else 0f
            endpos = end.copyOf()
        }
    })
    private val game = scenario.game

    @Test
    fun firingSharedTargetsSkipsEntitiesRemovedByAnEarlierRelay() {
        val first = scenario.spawn("target_temp_entity") { targetname = "group"; style = Defines.TE_EXPLOSION2 }
        scenario.spawn("trigger_relay") { targetname = "group"; target = "cleanup" }
        val last = scenario.spawn("target_temp_entity") { targetname = "GROUP"; style = Defines.TE_EXPLOSION1 }
        scenario.spawn("trigger_relay") { targetname = "cleanup"; killtarget = "group" }
        scenario.fire(first)
        assertFalse(last.inuse)
        assertEquals(listOf(PointTEMessage(Defines.TE_EXPLOSION2, first.s.origin)), messages)
    }

    @Test
    fun tempEntitySendsItsConfiguredPointEffect() {
        val effect = scenario.spawn("target_temp_entity") { style = Defines.TE_EXPLOSION2 }
        scenario.fire(effect)
        assertEquals(PointTEMessage(Defines.TE_EXPLOSION2, effect.s.origin), messages.single())
    }

    @Test
    fun loopedSpeakerTogglesItsNetworkSound() {
        val speaker = scenario.spawn("target_speaker") { st.noise = "world/test"; spawnflags = 1 }
        assertEquals(3, speaker.s.sound)
        scenario.fire(speaker)
        assertEquals(0, speaker.s.sound)
        scenario.fire(speaker)
        assertEquals(3, speaker.s.sound)
    }

    @Test
    fun crosslevelTargetRequiresEveryRequestedFlag() {
        val goal = scenario.spawn("target_goal")
        scenario.spawn("target_crosslevel_target") { spawnflags = 3; target = goal.targetname }
        scenario.spawn("target_crosslevel_target") { spawnflags = 7; target = goal.targetname }
        game.game.serverflags = 3
        repeat(19) { game.G_RunFrame() }
        assertEquals(0, game.level.found_goals)
        game.G_RunFrame()
        assertEquals(1, game.level.found_goals)
        assertEquals(7, game.g_edicts.single { it.inuse && it.classname == "target_crosslevel_target" }.spawnflags)
    }

    @Test
    fun crosslevelTriggerFiresItsTargetsOnce() {
        val goal = scenario.spawn("target_goal")
        val trigger = scenario.spawn("target_crosslevel_trigger") { spawnflags = 4; target = goal.targetname }
        scenario.fire(trigger)
        scenario.fire(trigger)
        assertEquals(4, game.game.serverflags)
        assertEquals(1, game.level.found_goals)
    }

    @Test
    fun helpWaitsForStartupAndOnlySignalsChanges() {
        val help = scenario.spawn("target_help") { message = "Find the key"; spawnflags = 1 }
        val initial = game.game.helpchanged
        scenario.fire(help)
        repeat(2) { game.G_RunFrame() }
        assertEquals("", game.game.helpmessage1)
        game.G_RunFrame()
        assertEquals(help.message, game.game.helpmessage1)
        game.G_RunFrame()
        scenario.fire(help)
        assertEquals(initial + 1, game.game.helpchanged)
    }

    @Test
    fun laserEndsAtTheTraceEndpointInEmptySpace() {
        val laser = scenario.spawn("target_laser") { spawnflags = 1 }
        repeat(10) { game.G_RunFrame() }
        assertEquals(2048f, laser.s.old_origin[0])
    }

    @Test
    fun spawnerCorrectsTheStockFactorySpawnPoint() {
        game.level.mapname = "fact2"
        val spawner = scenario.spawn("target_spawner") {
            target = "target_temp_entity"
            s.origin = floatArrayOf(-1504f, 512f, 72f)
        }
        scenario.fire(spawner)
        val spawned = game.g_edicts.single { it.inuse && it.classname == "target_temp_entity" }
        assertArrayEquals(floatArrayOf(-1504f, 512f, 64f), spawned.s.origin)
    }

    @Test
    fun mapCorrectionsAcceptMixedCase() {
        game.level.mapname = "Mine3"
        assertEquals("You have found a secret area.", scenario.spawn("target_secret") {
            s.origin = floatArrayOf(280f, -2048f, -624f)
        }.message)
        game.level.mapname = "Fact1"
        assertEquals("fact3\$secret1", scenario.spawn("target_changelevel") { map = "Fact3" }.map)
    }

    @Test
    fun reloadedExplosionStillExplodesAndSecretStillCounts() {
        val explosion = scenario.spawn("target_explosion")
        val secret = scenario.spawn("target_secret")
        for (entity in listOf(explosion, secret)) {
            val snapshot = LevelSaveSnapshots.snapshot(entity.index, entity)
            LevelSaveSnapshots.apply(entity, snapshot, game.items, game.g_edicts, game.game.clients)
        }
        scenario.fire(explosion)
        assertEquals(1, messages.size)
        scenario.fire(secret)
        assertEquals(1, game.level.found_secrets)
    }

    @Test
    fun blasterImpactHasMapHazardDamageCredit() {
        val victim = game.G_Spawn().apply { health = 100; takedamage = 1 }
        traceHit = victim
        scenario.fire(scenario.spawn("target_blaster") { spawnflags = 1 })
        assertEquals(85, victim.health)
        assertEquals(GameDefines.MOD_TARGET_BLASTER, victim.meansOfDeath)
    }

    @Test
    fun stringSkipsNonpositiveCharacterPositions() {
        val unused = scenario.spawn("target_character") { count = -1 }
        val digit = scenario.spawn("target_character") { count = 1 }
        unused.teamchain = digit
        scenario.fire(scenario.spawn("target_string") { teammaster = unused; message = "9" })
        assertEquals(12, unused.s.frame)
        assertEquals(9, digit.s.frame)
    }

    @Test
    fun targetsRequiringAnActivatorIgnoreAnEmptyActivation() {
        for (name in listOf("target_explosion", "target_splash", "target_crosslevel_trigger", "target_earthquake")) {
            val entity = scenario.spawn(name) { spawnflags = 1 }
            scenario.fire(entity, null)
            assertTrue(entity.inuse, name)
            assertTrue(messages.isEmpty(), name)
        }
        val laser = scenario.spawn("target_laser")
        repeat(10) { game.G_RunFrame() }
        assertEquals(0, positionedSounds)
        scenario.fire(laser, null)
        assertTrue(laser.svflags and Defines.SVF_NOCLIENT != 0)
    }
}
