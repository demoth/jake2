package jake2.server

import jake2.qcommon.*
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.messages.server.StuffTextMessage
import jake2.qcommon.vfs.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GameContinuationTest {
    @TempDir lateinit var root: Path
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private lateinit var host: ServerHost

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        Globals.server_state = ServerStates.SS_DEAD
        Cvar.getInstance().clear()
        MainCommon.registerCvars(false)
        Netchan.Netchan_Init()
        EngineVfs.configure(VfsConfig(Path.of("src/test/resources"), "baseq2", null,
            true, false, false, emptyList(), setOf("pak")))
        EngineWriteRoot.setRoot(root)
        host = ServerHost()
        Cvar.getInstance().find<Int>("maxclients").forceSet(3)
        host.resetClients()
    }

    @AfterEach
    fun cleanup() {
        host.clients.forEach { it.state = ClientStates.CS_FREE }
        host.close()
        Cbuf.ClearDeferred()
        EngineWriteRoot.setRoot(previousRoot)
        Globals.server_state = previousState
        Cvar.getInstance().clear()
    }

    private fun game(name: String, expression: String): GameImportsImpl {
        val change = ChangeMapInfo(expression, false, false)
        return host.createGameInstance(change, null).also {
            it.name = name
            it.state = change.state
            host.games[name] = it
        }
    }

    private fun bind(index: Int, game: GameImportsImpl): ClientNetworkInfo = host.clients[index].apply {
        gameName = game.name
        edict = game.gameExports.getEdict(index + 1)
        state = ClientStates.CS_CONNECTED
        netchan.setup(Defines.NS_SERVER, netadr_t(), index)
    }

    private fun finish(client: ClientNetworkInfo, game: GameImportsImpl) =
        host.executeClientCommand(client, "nextserver ${game.spawncount}")

    @Test
    fun `two Games advance independently and retain all routed clients`() {
        val first = game("default", "intro.cin+testbox")
        val second = game("other", "end.cin+testbox2")
        val a = bind(0, first)
        val b = bind(1, second)
        val c = bind(2, first)
        val oldSpawn = first.spawncount
        finish(a, first)
        host.executeClientCommand(c, "nextserver $oldSpawn") // duplicate from another client
        assertSame(first, host.games["default"], "Replacement must wait until packet handling finishes")
        host.update(100)
        assertEquals("testbox", host.games.getValue("default").mapName)
        assertSame(second, host.games["other"])
        assertEquals("default", a.gameName)
        assertEquals("default", c.gameName)
        assertTrue(a.netchan.reliablePending.contains(StuffTextMessage("reconnect")))
        assertTrue(c.netchan.reliablePending.contains(StuffTextMessage("reconnect")))
        assertFalse(b.netchan.reliablePending.contains(StuffTextMessage("reconnect")))

        finish(b, second)
        host.update(100)
        assertEquals("testbox2", host.games.getValue("other").mapName)
        assertEquals("testbox", host.games.getValue("default").mapName)
        assertEquals(2, host.games.size)
    }

    @Test
    fun `same-tick completions both run despite command-buffer deferral`() {
        val first = game("default", "intro.cin+testbox")
        val second = game("other", "intro.cin+testbox2")
        finish(bind(0, first), first)
        finish(bind(1, second), second)
        host.update(100)
        assertEquals("testbox", host.games.getValue("default").mapName)
        assertEquals("testbox2", host.games.getValue("other").mapName)
        assertEquals(2, host.games.size)
    }

    @Test
    fun `ending a sequence disconnects only its Game`() {
        val first = game("default", "end.cin")
        val second = game("other", "intro.cin+testbox2")
        val a = bind(0, first)
        val b = bind(1, second)
        finish(a, first)
        host.update(100)
        assertFalse(host.games.containsKey("default"))
        assertSame(second, host.games["other"])
        assertEquals(ClientStates.CS_FREE, a.state)
        assertNull(a.gameName)
        assertEquals(ClientStates.CS_CONNECTED, b.state)
        finish(b, second)
        host.update(100)
        assertEquals("testbox2", host.games.getValue("other").mapName)
    }

    @Test
    fun `singleplayer chain preserves unit syntax and ends in an idle Host`() {
        val intro = game("default", "intro.cin+end.cin+*testbox")
        val client = bind(0, intro)
        finish(client, intro)
        host.update(100)
        val end = host.games.getValue("default")
        assertEquals("end.cin", end.mapName)
        finish(client, end)
        host.update(100)
        assertEquals("*testbox", host.games.getValue("default").mapcmd)
        val terminal = game("default", "end.cin")
        finish(client, terminal)
        host.update(100)
        assertFalse(host.isRunning)
        assertTrue(host.clients.isEmpty())
    }

    @Test
    fun `stale requests and replaced queued Games cannot advance`() {
        val first = game("default", "intro.cin+testbox")
        val client = bind(0, first)
        host.executeClientCommand(client, "nextserver ${first.spawncount - 1}")
        host.update(100)
        assertSame(first, host.games["default"])
        finish(client, first)
        val replacement = game("default", "intro.cin+testbox2")
        host.update(100)
        assertSame(replacement, host.games["default"])
        finish(client, replacement)
        host.update(100)
        assertEquals("testbox2", host.games.getValue("default").mapName)
    }

    @Test
    fun `gameplay and non-coop pictures reject completion without consuming continuation`() {
        val normal = game("default", "testbox+testbox2")
        val client = bind(0, normal)
        finish(client, normal)
        host.update(100)
        assertSame(normal, host.games["default"])
        val picture = game("default", "end.pcx+testbox2")
        finish(client, picture)
        host.update(100)
        assertSame(picture, host.games["default"])
        Cvar.getInstance().find<Boolean>("coop").forceSet(true)
        finish(client, picture)
        host.update(100)
        assertEquals("testbox2", host.games.getValue("default").mapName)
    }
}
