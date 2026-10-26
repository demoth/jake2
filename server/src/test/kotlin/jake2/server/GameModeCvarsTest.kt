package jake2.server

import jake2.qcommon.Globals
import jake2.qcommon.MainCommon
import jake2.qcommon.ServerStates
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.server.save.ServerSaveJsonStore
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files

class GameModeCvarsTest {
    @TempDir lateinit var root: Path
    private lateinit var host: ServerHost
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private val cvars = Cvar.getInstance()

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        Globals.server_state = ServerStates.SS_DEAD
        EngineWriteRoot.setRoot(root)
        cvars.clear()
        MainCommon.registerCvars(false)
        host = ServerHost()
    }

    @AfterEach
    fun cleanup() {
        host.close()
        cvars.clear()
        EngineWriteRoot.setRoot(previousRoot)
        Globals.server_state = previousState
    }

    private fun game(previous: GameImportsImpl? = null, load: Boolean = false) =
        GameImportsImpl(host, ChangeMapInfo("end.pcx+testbox", false, load), previous).apply {
            state = ServerStates.SS_PIC
        }

    @Test
    fun `independent modes control picture continuation air acceleration and server info`() {
        cvars.find<Boolean>("deathmatch").forceSet(true)
        cvars.find<Float>("sv_airaccelerate").forceSet(5f)
        val dm = game()
        cvars.find<Boolean>("deathmatch").forceSet(false)
        cvars.find<Boolean>("coop").forceSet(true)
        val coop = game()
        cvars.find<Boolean>("coop").forceSet(false)
        assertTrue(dm.isDeathmatch)
        assertFalse(dm.isCoop)
        assertTrue(coop.isCoop)
        assertFalse(coop.isDeathmatch)
        assertEquals(5f, dm.airAcceleration)
        assertEquals(0f, coop.airAcceleration)
        assertNull(dm.takeNextMap())
        assertEquals("testbox", coop.takeNextMap())
        assertTrue(dm.serverInfo().contains("\\deathmatch\\1"))
        assertTrue(coop.serverInfo().contains("\\coop\\1"))
        assertFalse(cvars.find<Boolean>("coop").typedValue)
        val archivedLevel = root.resolve("save/current/end.pcx.sav.json")
        Files.createDirectories(archivedLevel.parent)
        Files.writeString(archivedLevel, "existing archive")
        cvars.find<Boolean>("deathmatch").forceSet(true)
        assertFalse(dm.isPreviousLevel)
        assertTrue(coop.isPreviousLevel)
    }

    @Test
    fun `pending mode changes apply only to successor and normalize conflicting modes`() {
        cvars.find<Boolean>("coop").forceSet(true)
        val original = game()
        Globals.server_state = ServerStates.SS_GAME
        original.findCvar("deathmatch", Boolean::class.java).set(true)
        assertFalse(original.isDeathmatch)
        val successor = game(original)
        assertTrue(successor.isDeathmatch)
        assertFalse(successor.isCoop)
        assertTrue(original.isCoop)
        assertFalse(original.isDeathmatch)
        assertFalse(cvars.find<Boolean>("deathmatch").typedValue)
        assertTrue(cvars.find<Boolean>("coop").typedValue)
    }

    @Test
    fun `fresh map uses Host mode defaults instead of the stopped Game mode`() {
        cvars.find<Boolean>("coop").forceSet(true)
        val previous = game()
        previous.state = ServerStates.SS_DEAD
        cvars.find<Boolean>("coop").forceSet(false)
        cvars.find<Boolean>("deathmatch").forceSet(true)
        val next = game(previous)
        assertTrue(next.isDeathmatch)
        assertFalse(next.isCoop)
        assertTrue(previous.isCoop)
    }

    @Test
    fun `saved mode overrides defaults only in loaded Game`() {
        cvars.find<Boolean>("coop").forceSet(true)
        val original = game()
        val store = ServerSaveJsonStore.forWriteDir(root.toString())
        cvars.find<Boolean>("coop").forceSet(false)
        cvars.find<Boolean>("deathmatch").forceSet(true)
        val other = game()
        original.writeLatchedCvars(store)
        val values = store.readLatchedCvars("current").cvars().associate { it.name() to it.value() }
        assertEquals("1", values["coop"])
        assertEquals("0", values["deathmatch"])
        val loaded = game(load = true)
        assertTrue(loaded.isCoop)
        assertFalse(loaded.isDeathmatch)
        assertTrue(other.isDeathmatch)
        assertFalse(other.isCoop)
        assertTrue(cvars.find<Boolean>("deathmatch").typedValue)
        assertFalse(cvars.find<Boolean>("coop").typedValue)
    }
}
