package jake2.server

import jake2.qcommon.Defines.*
import jake2.qcommon.Globals
import jake2.qcommon.Info.Info_ValueForKey
import jake2.qcommon.MainCommon
import jake2.qcommon.ServerStates
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarSetResult.Status.QUEUED
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.exec.find
import jake2.qcommon.pmove_t
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.server.save.ServerSaveJsonStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class GameCvarLifecycleTest {
    @TempDir lateinit var root: Path
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private val cvars = Cvar.getInstance()
    private val hostInitialization = lazy {
        MainCommon.registerCvars(false)
        ServerHost(TransportServerNetwork.standalone())
    }
    private val host by hostInitialization
    private val movement = pmove_t().apply { s.pm_type = PM_FREEZE }

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        Globals.server_state = ServerStates.SS_DEAD
        EngineWriteRoot.setRoot(root)
        cvars.clear()
    }

    @AfterEach
    fun cleanup() {
        try {
            if (hostInitialization.isInitialized()) host.close()
        } finally {
            cvars.clear()
            EngineWriteRoot.setRoot(previousRoot)
            Globals.server_state = previousState
        }
    }

    // A picture transition constructs Game imports without loading a BSP.
    private fun game(previous: GameImportsImpl? = null, load: Boolean = false) =
        GameImportsImpl(host, ChangeMapInfo("end.pcx+testbox", false, load), previous).apply {
            state = ServerStates.SS_PIC
        }

    private fun skill(game: GameImportsImpl) =
        game.registerCvar("skill", CvarTypes.ChoiceType(listOf(0, 1, 2, 3)), 0, CVAR_LATCH, null)

    private fun assertAirAcceleration(game: GameImportsImpl, expected: Float) {
        game.Pmove(movement)
        assertEquals(expected, movement.airAccelerate)
        assertEquals(expected.toString(), game.configstrings[CS_AIRACCEL])
    }

    @Test
    fun `Host validates startup inputs when declaring shared settings`() {
        cvars.setResult("maxclients", "3.5")
        cvars.setResult("timeout", "0.5")
        val game = game()
        val maxClients = cvars.find<Int>("maxclients")

        assertEquals(1, maxClients.typedValue)
        assertEquals(0.5f, host.timeout.typedValue)
        assertSame(maxClients, game.findCvar("maxclients", Int::class.java))
        assertFalse(maxClients.forceSet(0).accepted)
        assertEquals(1, maxClients.typedValue)
    }

    @Test
    fun `early inputs are validated and consumed by the first Game declaration`() {
        cvars.setResult("skill", "4")
        cvars.setResult("sv_gravity", "600.5")
        val first = game()
        val second = game()
        val firstGravity = first.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, null)
        val secondGravity = second.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, null)

        assertEquals(0, skill(first).typedValue)
        assertEquals(600.5f, firstGravity.typedValue)
        assertSame(firstGravity, first.findCvar("sv_gravity", Float::class.java))
        firstGravity.set(-200f)
        assertEquals(-200f, firstGravity.typedValue)
        assertEquals(800f, secondGravity.typedValue)
        assertFalse(cvars.isRegistered("sv_gravity"))
        assertFalse(cvars.isRegistered("skill"))
    }

    @Test
    fun `Games share Host settings until a Game explicitly declares its own`() {
        val global = cvars.register("test_setting", CvarTypes.STRING, "host-value", CVAR_USERINFO or CVAR_ARCHIVE)
        val first = game()
        val second = game()
        assertSame(global, first.findCvar("test_setting", String::class.java))
        second.findCvar("test_setting", String::class.java).set("shared-value")
        assertEquals("shared-value", first.findCvar("test_setting", String::class.java).typedValue)

        val local = first.registerCvar("test_setting", CvarTypes.STRING, "", CVAR_USERINFO, null)
        assertEquals("", local.typedValue)
        local.set("game-value")
        assertEquals("game-value", first.findCvar("test_setting", String::class.java).typedValue)
        assertEquals("shared-value", second.findCvar("test_setting", String::class.java).typedValue)
        assertEquals(CVAR_USERINFO or CVAR_ARCHIVE, global.flags)
        assertEquals(CVAR_USERINFO, local.flags)
        assertThrows(IllegalArgumentException::class.java) { first.findCvar("test_setting", Boolean::class.java) }
    }

    @Test
    fun `live rules validate and publish per Game`() {
        val first = game()
        val second = game()
        val fragLimit = first.findCvar("fraglimit", Int::class.java)
        val timeLimit = second.findCvar("timelimit", Float::class.java)
        fragLimit.setText("12")
        timeLimit.setText("2.5")
        second.findCvar("dmflags", Int::class.java).setText("128")

        assertFalse(fragLimit.setText("-1").accepted)
        assertFalse(timeLimit.setText("invalid").accepted)
        assertEquals("12", Info_ValueForKey(first.serverInfo(), "fraglimit"))
        assertEquals("0", Info_ValueForKey(second.serverInfo(), "fraglimit"))
        assertEquals("0.0", Info_ValueForKey(first.serverInfo(), "timelimit"))
        assertEquals("2.5", Info_ValueForKey(second.serverInfo(), "timelimit"))
        assertEquals("128", Info_ValueForKey(second.serverInfo(), "dmflags"))
        assertEquals(0, cvars.find<Int>("fraglimit").typedValue)
        assertEquals(0f, cvars.find<Float>("timelimit").typedValue)
    }

    @Test
    fun `fresh Games take applied Host defaults and keep their own mode and movement`() {
        val singlePlayer = game()
        val deathmatch = cvars.find<Boolean>("deathmatch")
        val acceleration = cvars.find<Float>("sv_airaccelerate")
        deathmatch.forceSet(true)
        acceleration.forceSet(2.5f)
        val original = game()
        original.findCvar("fraglimit", Int::class.java).set(12)

        Globals.server_state = ServerStates.SS_GAME
        assertEquals(QUEUED, acceleration.set(7f).status)
        assertAirAcceleration(game(), 2.5f)
        cvars.updateLatchedVars()
        val anotherDeathmatch = game()
        assertAirAcceleration(anotherDeathmatch, 7f)
        assertAirAcceleration(singlePlayer, 0f)
        assertAirAcceleration(original, 2.5f)

        original.state = ServerStates.SS_DEAD
        deathmatch.forceSet(false)
        cvars.find<Boolean>("coop").forceSet(true)
        cvars.find<Int>("fraglimit").forceSet(30)
        val fresh = game(original)
        assertEquals("1", Info_ValueForKey(original.serverInfo(), "deathmatch"))
        assertEquals("1", Info_ValueForKey(fresh.serverInfo(), "coop"))
        assertFalse(fresh.isDeathmatch)
        assertEquals(30, fresh.findCvar("fraglimit", Int::class.java).typedValue)
        assertEquals(7f, fresh.findCvar("sv_airaccelerate", Float::class.java).typedValue)
        assertAirAcceleration(fresh, 0f)

        val archive = root.resolve("save/current/end.pcx.sav.json")
        Files.createDirectories(archive.parent)
        Files.writeString(archive, "existing archive")
        assertFalse(anotherDeathmatch.isPreviousLevel)
        assertTrue(fresh.isPreviousLevel)
    }

    @Test
    fun `successors inherit live rules and apply local latches independently`() {
        cvars.setResult("coop", "1")
        cvars.setResult("sv_airaccelerate", "2")
        cvars.setResult("skill", "2")
        val original = game()
        original.findCvar("fraglimit", Int::class.java).set(12)
        original.findCvar("timelimit", Float::class.java).set(2.5f)
        original.findCvar("dmflags", Int::class.java).set(128)
        val originalSkill = skill(original)
        val originalGravity = original.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, null)
        originalGravity.set(600f)
        val localMode = original.findCvar("deathmatch", Boolean::class.java)
        val localAcceleration = original.findCvar("sv_airaccelerate", Float::class.java)

        Globals.server_state = ServerStates.SS_GAME
        localMode.set(true)
        localAcceleration.set(4f)
        originalSkill.set(3)
        cvars.find<Int>("fraglimit").forceSet(30)
        cvars.updateLatchedVars()
        assertTrue(original.isCoop)
        assertFalse(original.isDeathmatch)

        val successor = game(original)
        val successorSkill = skill(successor)
        val successorGravity = successor.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, null)
        assertTrue(successor.isDeathmatch)
        assertFalse(successor.isCoop)
        assertEquals(4f, successor.findCvar("sv_airaccelerate", Float::class.java).typedValue)
        assertNull(successor.findCvar("sv_airaccelerate", Float::class.java).pendingValue)
        assertAirAcceleration(successor, 4f)
        assertAirAcceleration(original, 0f)
        assertEquals("12", Info_ValueForKey(successor.serverInfo(), "fraglimit"))
        assertEquals("2.5", Info_ValueForKey(successor.serverInfo(), "timelimit"))
        assertEquals("128", Info_ValueForKey(successor.serverInfo(), "dmflags"))
        assertEquals(3, successorSkill.typedValue)
        assertNull(successorSkill.pendingValue)
        assertEquals(600f, successorGravity.typedValue)

        successorSkill.forceSet(1)
        successorGravity.set(400f)
        assertEquals(2, originalSkill.typedValue)
        assertEquals(3, originalSkill.pendingValue)
        assertEquals(600f, originalGravity.typedValue)
        assertEquals(true, localMode.pendingValue)
        assertEquals(4f, localAcceleration.pendingValue)
        assertFalse(cvars.find<Boolean>("deathmatch").typedValue)
        assertEquals(2f, cvars.find<Float>("sv_airaccelerate").typedValue)
    }

    @ParameterizedTest(name = "deathmatch={0}")
    @ValueSource(booleans = [true, false])
    fun `saves restore applied settings without affecting a running Game or Host`(deathmatch: Boolean) {
        cvars.setResult("deathmatch", if (deathmatch) "1" else "0")
        cvars.setResult("coop", if (deathmatch) "0" else "1")
        cvars.setResult("sv_airaccelerate", "3.5")
        val original = game()
        val originalSkill = skill(original)
        originalSkill.set(2)
        Globals.server_state = ServerStates.SS_GAME
        originalSkill.set(3)
        original.findCvar("sv_airaccelerate", Float::class.java).set(4f)
        cvars.find<Boolean>("deathmatch").forceSet(!deathmatch)
        cvars.find<Boolean>("coop").forceSet(deathmatch)
        cvars.find<Float>("sv_airaccelerate").forceSet(9f)
        val other = game()
        val otherSkill = skill(other)
        otherSkill.forceSet(1)

        val store = ServerSaveJsonStore.forWriteDir(root.toString())
        original.writeLatchedCvars(store)
        val savedAirAcceleration = store.readLatchedCvars("current").cvars().filter { it.name() == "sv_airaccelerate" }
        assertEquals("3.5", savedAirAcceleration.single().value())

        val loaded = game(previous = other, load = true)
        assertEquals(deathmatch, loaded.isDeathmatch)
        assertEquals(!deathmatch, loaded.isCoop)
        assertEquals(3.5f, loaded.findCvar("sv_airaccelerate", Float::class.java).typedValue)
        val loadedSkill = skill(loaded)
        assertEquals(2, loadedSkill.typedValue)
        assertNull(loadedSkill.pendingValue)
        assertAirAcceleration(loaded, if (deathmatch) 3.5f else 0f)

        assertEquals(!deathmatch, other.isDeathmatch)
        assertEquals(1, otherSkill.typedValue)
        assertAirAcceleration(other, if (deathmatch) 0f else 9f)
        assertEquals(!deathmatch, cvars.find<Boolean>("deathmatch").typedValue)
        assertEquals(deathmatch, cvars.find<Boolean>("coop").typedValue)
        assertEquals(9f, cvars.find<Float>("sv_airaccelerate").typedValue)
        assertFalse(cvars.isRegistered("skill"))
    }

    @Test
    fun `server info for an oversized map name omits the map entry`() {
        val oversized = GameImportsImpl(host, ChangeMapInfo("x".repeat(64) + ".pcx", false, false))
        val globalMap = cvars.find<String>("mapname")
        globalMap.forceSet("other-map")

        assertEquals("", Info_ValueForKey(oversized.serverInfo(), "mapname"))
        assertEquals("other-map", globalMap.typedValue)
    }
}
