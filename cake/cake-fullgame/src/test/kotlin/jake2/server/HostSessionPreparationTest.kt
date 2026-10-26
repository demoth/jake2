package jake2.server

import jake2.qcommon.*
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.NET
import jake2.qcommon.vfs.*
import jake2.server.save.ServerSaveFiles
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class HostSessionPreparationTest {
    @TempDir lateinit var root: Path
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private lateinit var host: ServerHost
    private val cvars = Cvar.getInstance()

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        Globals.server_state = ServerStates.SS_DEAD
        cvars.clear()
        MainCommon.registerCvars(false)
        jake2.qcommon.network.Netchan.Netchan_Init()
        cvars.find<Int>("port").forceSet(0)
        EngineVfs.configure(VfsConfig(Path.of("src/test/resources"), "baseq2", null,
            true, false, false, emptyList(), setOf("pak")))
        EngineWriteRoot.setRoot(root)
        host = ServerHost()
    }

    @AfterEach
    fun cleanup() {
        host.close()
        NET.configureServer(false)
        cvars.clear()
        EngineWriteRoot.setRoot(previousRoot)
        Globals.server_state = previousState
    }

    private fun save(coop: Boolean) {
        cvars.find<Boolean>("coop").forceSet(coop)
        host.map(listOf("map", "testbox"))
        val game = host.games.getValue("default")
        game.archiveLevelForTransition()
        game.SV_WriteServerFile(true)
        ServerSaveFiles(root).copy("current", "slot")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `load after stop prepares saved capacity before Game allocation`(coop: Boolean) {
        save(coop)
        host.stop("test", false)
        NET.configureServer(false)
        cvars.find<Boolean>("coop").forceSet(!coop)
        cvars.find<Int>("maxclients").forceSet(if (coop) 1 else 4)

        host.loadGame(listOf("load", "slot"))

        val loaded = host.games.getValue("default")
        val capacity = if (coop) 4 else 1
        assertEquals(capacity, host.clients.size)
        assertNotNull(loaded.gameExports.getEdict(capacity).getClient())
        assertEquals(coop, loaded.isCoop)
        assertEquals(!coop, cvars.find<Boolean>("coop").typedValue, "Loading must preserve Host mode defaults")
        assertTrue(host.clients.all { it.gameName == "default" })
        assertEquals(coop, NET.ip_sockets[Defines.NS_SERVER] != null)
        // Exercise the snapshot ring allocated from the prepared client capacity.
        host.clients[0].edict = loaded.gameExports.getEdict(1)
        loaded.buildClientFrameMessages(host.clients[0])
    }

    @Test
    fun `fresh Host loads save and can restart again`() {
        save(false)
        host.close()
        host = ServerHost()
        host.loadGame(listOf("load", "slot"))
        assertEquals(1, host.clients.size)
        assertEquals("testbox", host.games.getValue("default").mapName)
        host.stop("test", false)
        host.changeMap(listOf("gamemap", "testbox2"))
        assertEquals(1, host.clients.size)
        assertEquals("testbox2", host.games.getValue("default").mapName)
    }

    @Test
    fun `gamemap retains client slots while map prepares new slots`() {
        host.map(listOf("map", "testbox"))
        val original = host.clients[0]
        host.changeMap(listOf("gamemap", "testbox2"))
        assertSame(original, host.clients[0])
        host.map(listOf("map", "testbox"))
        assertNotSame(original, host.clients[0])
    }

    @Test
    fun `load cannot resize clients belonging to another Game`() {
        save(false)
        host.stop("test", false)
        cvars.find<Boolean>("coop").forceSet(true)
        host.map(listOf("map", "testbox2"))
        val other = host.games.remove("default")!!
        other.name = "other"
        host.games[other.name] = other
        host.clients.forEach { it.gameName = other.name }
        val client = host.clients[0]

        host.loadGame(listOf("load", "slot"))

        assertSame(other, host.games["other"])
        assertFalse(host.games.containsKey("default"))
        assertSame(client, host.clients[0])
        assertEquals(4, host.clients.size)
        assertEquals("testbox2", jake2.server.save.ServerSaveJsonStore.forWriteDir(root.toString())
            .readMapCommand("current").mapCommand())
    }

    @Test
    fun `unreadable allocation metadata preserves running session`() {
        save(false)
        val game = host.games.getValue("default")
        val client = host.clients[0]
        Files.writeString(root.resolve("save/slot/server_latched_cvars.ssv.json"), "invalid")
        host.loadGame(listOf("load", "slot"))
        assertSame(game, host.games["default"])
        assertSame(client, host.clients[0])
    }
}
