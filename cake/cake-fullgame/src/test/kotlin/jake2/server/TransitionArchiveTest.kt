package jake2.server

import jake2.game.LevelSaveJsonStore
import jake2.qcommon.*
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.EngineVfs
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.qcommon.vfs.VfsConfig
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class TransitionArchiveTest {
    @TempDir lateinit var root: Path
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private lateinit var host: ServerHost
    private lateinit var first: GameImportsImpl
    private lateinit var second: GameImportsImpl

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        Globals.server_state = ServerStates.SS_DEAD
        Cvar.getInstance().clear()
        MainCommon.registerCvars(false)
        EngineVfs.configure(VfsConfig(Path.of("src/test/resources"), "baseq2", null,
            true, false, false, emptyList(), setOf("pak")))
        EngineWriteRoot.setRoot(root)
        host = ServerHost()
        Cvar.getInstance().find<Int>("maxclients").forceSet(4)
        host.resetClients()
        first = host.createGameInstance(ChangeMapInfo("testbox", false, false), null)
        second = host.createGameInstance(ChangeMapInfo("testbox2", false, false), null)
        first.name = "default"
        host.games[first.name] = first
        host.games[second.name] = second
        first.state = ServerStates.SS_GAME
        second.state = ServerStates.SS_GAME
        host.clients[0].apply {
            gameName = first.name
            edict = first.gameExports.getEdict(1)
            edict.inuse = true
        }
        host.clients[1].apply {
            gameName = second.name
            edict = second.gameExports.getEdict(2)
            edict.inuse = true
        }
        host.clients[2].apply {
            gameName = first.name
            edict = first.gameExports.getEdict(3)
            edict.inuse = false
        }
        // A routed but unused client slot has no entity yet.
        host.clients[3].gameName = first.name
        first.gameExports.getEdict(0).inuse = true
    }

    @AfterEach
    fun cleanup() {
        host.close()
        EngineWriteRoot.setRoot(previousRoot)
        Globals.server_state = previousState
        Cvar.getInstance().clear()
    }

    @Test
    fun `archive excludes player bodies and preserves live state in both games`() {
        val gameplay = first.gameExports
        var wroteGameplay = false
        first.gameExports = object : GameExports by gameplay {
            override fun WriteLevel(filename: String) {
                assertFalse(host.clients[0].edict.inuse)
                assertFalse(host.clients[2].edict.inuse)
                assertTrue(host.clients[1].edict.inuse, "Unrelated Game must stay intact during serialization")
                assertTrue(gameplay.getEdict(0).inuse)
                wroteGameplay = true
                gameplay.WriteLevel(filename)
            }
        }

        first.archiveLevelForTransition()

        assertTrue(wroteGameplay)
        assertLiveState()
        val saved = LevelSaveJsonStore.forWriteDir(root.toString())
            .read(root.resolve("save/current/testbox.sav.json").toString())
        assertTrue(saved.entities.any { it.entityNumber == 0 })
        assertFalse(saved.entities.any { it.entityNumber == 1 || it.entityNumber == 3 })
        assertTrue(Files.exists(root.resolve("save/current/testbox.sv2.json")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["sv2", "sav"])
    fun `write failure restores entities and aborts map transition`(extension: String) {
        // A directory at the output filename forces a real filesystem write failure.
        Files.createDirectories(root.resolve("save/current/testbox.$extension.json"))
        val originalGameplay = first.gameExports
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.changeMap("testbox2"))
        assertLiveState()
        assertSame(first, host.games["default"])
        assertSame(second, host.games[second.name])
        assertEquals(2, host.games.size)
        assertSame(originalGameplay, first.gameExports)
        assertEquals(ServerStates.SS_GAME, first.state)
        assertEquals("default", host.clients[0].gameName)
        assertFalse(Files.exists(root.resolve("save/autosave")), "Failed archival must not continue to autosave")
        if (extension == "sv2") {
            assertFalse(Files.exists(root.resolve("save/current/testbox.sav.json")),
                "Server-state failure must stop before gameplay serialization")
        }
    }

    private fun assertLiveState() {
        assertTrue(host.clients[0].edict.inuse)
        assertFalse(host.clients[2].edict.inuse)
        assertTrue(host.clients[1].edict.inuse)
        assertTrue(first.gameExports.getEdict(0).inuse)
        assertNull(host.clients[3].edict)
    }
}
