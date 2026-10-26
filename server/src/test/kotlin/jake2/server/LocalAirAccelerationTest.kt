package jake2.server

import jake2.qcommon.*
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.server.save.ServerSaveJsonStore
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LocalAirAccelerationTest {
    @TempDir lateinit var root: Path
    private lateinit var host: ServerHost
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private val cvars = Cvar.getInstance()

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        EngineWriteRoot.setRoot(root)
        Globals.server_state = ServerStates.SS_DEAD
        cvars.clear()
        MainCommon.registerCvars(false)
        host = ServerHost()
        cvars.find<Boolean>("deathmatch").forceSet(true)
        cvars.find<Float>("sv_airaccelerate").forceSet(2f)
    }

    @AfterEach
    fun cleanup() {
        host.close()
        cvars.clear()
        EngineWriteRoot.setRoot(previousRoot)
        Globals.server_state = previousState
    }

    private fun game(previous: GameImportsImpl? = null, load: Boolean = false) =
        GameImportsImpl(host, ChangeMapInfo("test.pcx", false, load), previous)

    private fun assertEffective(game: GameImportsImpl, expected: Float) {
        val movement = pmove_t().apply { s.pm_type = Defines.PM_FREEZE }
        game.Pmove(movement)
        assertEquals(expected, movement.airAccelerate)
        assertEquals(expected, game.airAcceleration)
        assertEquals(expected.toString(), game.configstrings[Defines.CS_AIRACCEL])
    }

    @Test
    fun `local pending value follows successor but not fresh map defaults`() {
        val original = game()
        val local = original.findCvar("sv_airaccelerate", Float::class.java)
        assertNotSame(cvars.find<Float>("sv_airaccelerate"), local)
        Globals.server_state = ServerStates.SS_GAME
        local.set(4f)
        cvars.find<Float>("sv_airaccelerate").forceSet(9f)
        val successor = game(original)
        assertEffective(original, 2f)
        assertEffective(successor, 4f)
        assertEquals(4f, local.pendingValue)
        assertNull(successor.findCvar("sv_airaccelerate", Float::class.java).pendingValue)
        original.state = ServerStates.SS_DEAD
        assertEffective(game(original), 9f)
    }

    @Test
    fun `save records current local configuration not pending value or Host default`() {
        val original = game()
        Globals.server_state = ServerStates.SS_GAME
        original.findCvar("sv_airaccelerate", Float::class.java).set(4f)
        cvars.find<Float>("sv_airaccelerate").forceSet(9f)
        val store = ServerSaveJsonStore.forWriteDir(root.toString())
        original.writeLatchedCvars(store)
        val saved = store.readLatchedCvars("current").cvars().filter { it.name() == "sv_airaccelerate" }
        assertEquals(1, saved.size)
        assertEquals("2.0", saved.single().value())
        val loaded = game(load = true)
        assertEffective(loaded, 2f)
        assertEffective(original, 2f)
        assertEquals(9f, cvars.find<Float>("sv_airaccelerate").typedValue)
    }
}
