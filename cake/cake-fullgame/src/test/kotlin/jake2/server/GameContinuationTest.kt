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
import java.nio.file.Files
import jake2.qcommon.network.NET
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `continuation autosave eligibility follows its Game mode`(deathmatch: Boolean) {
        assertEquals(ServerHost.OperationResult.Completed, host.map("testbox"))
        val autosave = root.resolve("save/save0")
        fun contents() = Files.walk(autosave).use { paths -> paths.filter(Files::isRegularFile).toList()
            .associate { autosave.relativize(it).toString() to Files.readAllBytes(it).toList() } }
        val before = contents()
        assertTrue(before.isNotEmpty())
        host.stop("cinematic", false)
        val cvars = Cvar.getInstance()
        cvars.find<Boolean>("deathmatch").forceSet(deathmatch)
        cvars.find<Int>("maxclients").forceSet(if (deathmatch) 3 else 1)
        host.resetClients()
        val cinematic = game("default", "intro.cin+testbox2")
        // Changed Host defaults must not decide persistence for an existing Game.
        cvars.find<Boolean>("deathmatch").forceSet(!deathmatch)
        finish(bind(0, cinematic), cinematic)
        host.update(100)
        val replacement = host.games.getValue("default")
        assertEquals("testbox2", replacement.mapName)
        assertEquals(deathmatch, replacement.isDeathmatch)
        if (deathmatch) assertEquals(before, contents()) else {
            assertNotEquals(before, contents())
            assertEquals("testbox2", jake2.server.save.ServerSaveJsonStore.forWriteDir(root.toString())
                .readMapCommand("save0").mapCommand())
        }
    }

    @Test
    fun `live timelimit ends only its owning Game`() {
        Cvar.getInstance().find<Boolean>("deathmatch").forceSet(true)
        val first = host.spawnServerInstance(ChangeMapInfo("testbox", false, false), 0)
        first.name = "default"
        host.games[first.name] = first
        val second = host.spawnServerInstance(ChangeMapInfo("testbox2", false, false), 0)
        second.name = "other"
        host.games[second.name] = second
        first.findCvar("dmflags", Int::class.java).set(Defines.DF_SAME_LEVEL)
        first.findCvar("timelimit", Float::class.java).set(0.001f)
        second.findCvar("fraglimit", Int::class.java).set(10)
        val firstGameplay = first.gameExports as jake2.game.GameExportsImpl
        val secondGameplay = second.gameExports as jake2.game.GameExportsImpl
        firstGameplay.G_RunFrame()
        secondGameplay.G_RunFrame()
        assertTrue(firstGameplay.level.intermissiontime > 0)
        assertEquals(0f, secondGameplay.level.intermissiontime)
        assertEquals(10, secondGameplay.gameCvars.fraglimit.typedValue)
        assertEquals(0, firstGameplay.gameCvars.fraglimit.typedValue)
    }

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
    fun `targeted stop survives notification failure and cancels only its pending work`() {
        val first = game("default", "intro.cin+testbox")
        val second = game("other", "intro.cin+testbox2")
        val a = bind(0, first)
        val b = bind(1, second)
        val c = bind(2, first)
        finish(a, first)
        finish(b, second)
        a.state = ClientStates.CS_SPAWNED
        a.download = byteArrayOf(1)
        a.netchan.reliablePending.add(StuffTextMessage("stale"))
        a.unreliable.add(StuffTextMessage("stale"))
        a.netchan.remote_address = null // Fail the first final notification.
        first.gameExports = null // Discarding a Game must not call back into its gameplay.
        Cvar.getInstance().find<Int>("port").forceSet(0)
        assertTrue(NET.configureServer(true))
        val socket = NET.ip_sockets[Defines.NS_SERVER]
        val peerChannel = b.netchan

        host.stopGame(first, "Game failed\n")

        for (client in listOf(a, c)) {
            assertEquals(ClientStates.CS_FREE, client.state)
            assertNull(client.gameName)
            assertNull(client.edict)
            assertNull(client.download)
            assertTrue(client.netchan.reliablePending.isEmpty())
            assertTrue(client.unreliable.isEmpty())
        }
        assertSame(second, host.games["other"])
        assertSame(socket, NET.ip_sockets[Defines.NS_SERVER])
        assertSame(peerChannel, b.netchan)
        assertEquals(ClientStates.CS_CONNECTED, b.state)
        host.update(100)
        assertFalse(host.games.containsKey("default"))
        assertEquals("testbox2", host.games.getValue("other").mapName)
        host.stopGame(first, "Already stopped\n")
        assertTrue(host.isRunning)
    }

    @Test
    fun `stale Game stop cannot remove its replacement or disconnect its clients`() {
        val old = game("default", "end.cin")
        val replacement = game("default", "intro.cin+testbox")
        val client = bind(0, replacement)
        host.stopGame(old, "Stale failure\n")
        assertSame(replacement, host.games["default"])
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        finish(client, replacement)
        host.update(100)
        assertEquals("testbox", host.games.getValue("default").mapName)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `Game frame failure stops only its owner and other Game runs in the same tick`(collision: Boolean) {
        val first = game("default", "testbox")
        val second = game("other", "testbox2")
        val a = bind(0, first)
        val b = bind(1, second)
        var secondFrames = 0
        first.gameExports = object : GameExports by first.gameExports {
            override fun G_RunFrame() {
                if (collision) first.cm.InlineModel("invalid")
                else first.configstring(-1, "invalid")
            }
        }
        second.gameExports = object : GameExports by second.gameExports {
            override fun G_RunFrame() { secondFrames++ }
        }
        host.update(1000)
        assertFalse(host.games.containsKey("default"))
        assertSame(second, host.games["other"])
        assertEquals(ClientStates.CS_FREE, a.state)
        assertEquals(ClientStates.CS_CONNECTED, b.state)
        assertEquals(1, secondFrames)
        host.update(1000)
        assertEquals(2, secondFrames)
    }

    @Test
    fun `Game failure in client command aborts packet and stops Game rather than just client`() {
        val first = game("default", "testbox")
        val second = game("other", "end.cin")
        val a = bind(0, first)
        val peer = bind(1, first)
        val other = bind(2, second)
        var commands = 0
        first.gameExports = object : GameExports by first.gameExports {
            override fun ClientCommand(ent: ServerEntity, args: MutableList<String>) {
                commands++
                first.error(Defines.ERR_FATAL, "broken Game state")
            }
        }
        host.handleClientMessages(a, listOf(
            jake2.qcommon.network.messages.client.StringCmdMessage("fail"),
            jake2.qcommon.network.messages.client.StringCmdMessage("must_not_run")))
        assertEquals(1, commands)
        assertEquals(ClientStates.CS_FREE, a.state)
        assertEquals(ClientStates.CS_FREE, peer.state)
        assertEquals(ClientStates.CS_CONNECTED, other.state)
        assertSame(second, host.games["other"])
    }

    @Test
    fun `last Game failure leaves reusable idle Host`() {
        val first = game("default", "testbox")
        first.gameExports = object : GameExports by first.gameExports {
            override fun G_RunFrame() { first.error("failed frame") }
        }
        host.update(1000)
        assertFalse(host.isRunning)
        assertTrue(host.clients.isEmpty())
        assertEquals(ServerHost.OperationResult.Completed, host.map("testbox"))
    }

    @Test
    fun `failed cleanup escapes nested Game boundaries with original failure`() {
        val first = game("default", "testbox")
        bind(0, first).netchan.remote_address = null
        val failure = GameFailure(first, "failed frame")
        first.gameExports = object : GameExports by first.gameExports {
            override fun G_RunFrame() { throw failure }
        }
        val escaped = assertThrows(IllegalStateException::class.java) { host.update(1000) }
        assertSame(failure, escaped.cause)
        assertEquals(1, failure.suppressed.size)
        assertFalse(host.isRunning)
        assertTrue(host.clients.isEmpty())
    }

    @Test
    fun `unclassified programming failures are not swallowed by Game boundary`() {
        val first = game("default", "testbox")
        val bug = IllegalStateException("unexpected bug")
        first.gameExports = object : GameExports by first.gameExports {
            override fun G_RunFrame() { throw bug }
        }
        assertSame(bug, assertThrows(IllegalStateException::class.java) { host.update(1000) })
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

    @ParameterizedTest
    @ValueSource(strings = ["missing", "*", "unsupported.dm2"])
    fun `invalid continuation preserves its Game while another queued Game advances`(target: String) {
        val first = game("default", "intro.cin+$target")
        val second = game("other", "intro.cin+testbox2")
        val client = bind(0, first)
        finish(client, first)
        finish(bind(1, second), second)
        host.update(100)
        assertSame(first, host.games["default"])
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        assertEquals("default", client.gameName)
        assertEquals("testbox2", host.games.getValue("other").mapName)
        assertNull(first.takeNextMap(), "Rejected continuation remains consumed")
        assertFalse(client.netchan.reliablePending.contains(StuffTextMessage("reconnect")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["wipe", "restore", "autosave"])
    fun `late continuation failure stops the batch and permits Host restart`(stage: String) {
        val first = game("default", "intro.cin+${if (stage == "wipe") "*" else ""}testbox")
        val second = game("other", "intro.cin+testbox2")
        finish(bind(0, first), first)
        finish(bind(1, second), second)
        Cvar.getInstance().find<Boolean>("paused").forceSet(true)
        Cvar.getInstance().find<Int>("port").forceSet(0)
        assertTrue(NET.configureServer(true))
        val broken = when (stage) {
            "wipe" -> root.resolve("save/current/game.ssv.json")
            "autosave" -> root.resolve("save/save0/game.ssv.json")
            else -> root.resolve("save/current/testbox.sav.json")
        }
        if (stage == "restore") {
            Files.createDirectories(broken.parent)
            Files.writeString(broken, "invalid level")
        } else {
            Files.createDirectories(broken)
            Files.writeString(broken.resolve("blocker"), "prevent replacement")
        }
        host.update(100)
        assertTrue(host.games.isEmpty())
        assertTrue(host.clients.isEmpty())
        assertFalse(host.isPaused)
        assertNull(NET.ip_sockets[Defines.NS_SERVER])
        assertNotNull(jake2.qcommon.exec.Cmd.FindCommand("gamemap"))
        host.update(100)
        assertTrue(host.games.isEmpty(), "Remaining queued continuation must not restart a Game")

        if (stage != "restore") {
            Files.delete(broken.resolve("blocker"))
            Files.delete(broken)
        }
        assertEquals(ServerHost.OperationResult.Completed, host.map("testbox"))
        assertEquals(1, host.games.size)
        assertEquals("testbox", host.games.getValue("default").mapName)
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
        picture.findCvar("coop", Boolean::class.java).forceSet(true)
        finish(client, picture)
        host.update(100)
        assertEquals("testbox2", host.games.getValue("default").mapName)
    }
}
