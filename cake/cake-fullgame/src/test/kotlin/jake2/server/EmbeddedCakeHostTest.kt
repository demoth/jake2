package jake2.server

import jake2.game.GameExportsImpl
import jake2.qcommon.*
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.NET
import jake2.qcommon.network.Netchan
import jake2.qcommon.network.messages.server.DownloadMessage
import jake2.qcommon.network.messages.server.ServerMessage
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.server.save.ServerSaveFiles
import org.demoth.cake.download.CakeDownloadCache
import org.demoth.cake.download.CakeDownloadManager
import org.demoth.cake.download.CakeDownloadSession
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.*
import org.demoth.cake.profile.CakeGameProfile
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.net.DatagramSocket

class EmbeddedCakeHostTest {
    @TempDir lateinit var temp: Path
    private lateinit var host: EmbeddedCakeHost
    private lateinit var previousRoot: Path
    private val cvars = Cvar.getInstance()
    private val profile get() = CakeGameProfile("session", Path.of("src/test/resources").toAbsolutePath().toString())
    private fun request(mode: HostMode = HostMode.SINGLEPLAYER, skill: Int = 1) =
        HostRequest(profile, "testbox", mode, skill)

    @BeforeEach
    fun setup() {
        cvars.clear()
        Globals.server_state = ServerStates.SS_DEAD
        MainCommon.registerCvars(false)
        Netchan.Netchan_Init()
        cvars.find<Int>("port").forceSet(0)
        cvars.find<Int>("clientport").forceSet(0)
        previousRoot = EngineWriteRoot.path()
        host = EmbeddedCakeHost(temp)
    }

    @AfterEach
    fun cleanup() {
        host.stop("test cleanup")
        NET.Config(false)
        EngineWriteRoot.setRoot(previousRoot)
        cvars.clear()
    }

    private fun game() = SV_MAIN.games.getValue("default").gameExports as GameExportsImpl

    @Test
    fun `missing download receives failure for fresh and resumed requests`() {
        assertEquals(HostStartResult.Started, host.start(request()))
        val path = "models/missing/tris.md2"
        val cache = temp.resolve("downloads")
        val partial = cache.resolve("baseq2/models/missing/tris.tmp")
        for (resume in listOf(false, true)) {
            if (resume) {
                Files.createDirectories(partial.parent)
                Files.write(partial, byteArrayOf(1, 2, 3))
            }
            val client = ClientNetworkInfo()
            val downloads = CakeDownloadSession(
                sendReliable = { command ->
                    SV_USER.userCommands.getValue("download").execute(
                        Cmd.TokenizeString(command, false), SV_MAIN.games.getValue("default"), client)
                },
                downloadManager = CakeDownloadManager(CakeDownloadCache(cache), cvars),
            )
            assertTrue(downloads.requestMissingResource(path, null, 1))
            assertEquals(1, client.netchan.reliablePending.size,
                "Missing files must receive a reply instead of leaving the client waiting")
            val buffer = sizebuf_t(64)
            client.netchan.reliablePending.single().writeTo(buffer)
            val reply = ServerMessage.parseFromBuffer(buffer) as DownloadMessage
            assertNull(reply.data)
            assertEquals(CakeDownloadSession.Result.MissingOnServer, downloads.handle(reply))
            assertFalse(Files.exists(cache.resolve("baseq2/$path")))
            if (resume) assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(partial))
            downloads.reset()
        }
    }

    @Test
    fun `newgame killserver gamemap sequence restores client slots`() {
        assertEquals(HostStartResult.Started, host.prepareNewGame(profile, 1))
        Cmd.ExecuteString("killserver")
        Cmd.ExecuteString("gamemap *testbox")
        host.update(100)
        assertEquals(HostState.RUNNING, host.state)
        assertEquals(1, SV_MAIN.games.getValue("default").serverMain.clients.size)
        assertTrue(NET.ip_sockets.all { it == null })
    }

    @Test
    fun `missing selected mod cannot silently fall back to base content`() {
        val result = host.start(request().copy(profile = profile.copy(gamemod = "missing-mod")))
        assertInstanceOf(HostStartResult.Rejected::class.java, result)
        assertEquals(HostState.STOPPED, host.state)
        assertEquals(previousRoot, EngineWriteRoot.path())
        assertNull(Cmd.FindCommand("map"))
        assertEquals(HostStartResult.Started, host.start(request()))
    }

    @Test
    fun `invalid slots leave existing saves untouched and copying a slot to itself is harmless`() {
        assertEquals(HostStartResult.Started, host.start(request()))
        val current = temp.resolve("session/save/current/server_mapcmd.ssv.json")
        val snapshot = Files.readString(current)
        val saveFiles = ServerSaveFiles(temp.resolve("session"))
        val sentinel = temp.resolve("sentinel/server_mapcmd.ssv.json")
        Files.createDirectories(sentinel.parent)
        Files.writeString(sentinel, "outside profile")
        for (slot in listOf("../../sentinel", ".", "..", "nested/slot", "nested\\slot", "C:slot", "")) {
            // Invalid names must be rejected before the not-yet-spawned player check or any writes.
            Cmd.ExecuteString("save \"$slot\"")
            Cmd.ExecuteString("load \"$slot\"")
            assertThrows(IllegalArgumentException::class.java) { saveFiles.copy("current", slot) }
            assertThrows(IllegalArgumentException::class.java) { saveFiles.copy(slot, "current") }
            assertThrows(IllegalArgumentException::class.java) { saveFiles.wipe(slot) }
            assertEquals(snapshot, Files.readString(current))
            assertEquals("outside profile", Files.readString(sentinel))
        }
        saveFiles.copy("current", "current")
        Cmd.ExecuteString("load current")
        Cmd.ExecuteString("save current")
        Cmd.ExecuteString("save before-spawn")
        assertFalse(Files.exists(temp.resolve("session/save/before-spawn")))
        assertEquals(snapshot, Files.readString(current))
    }

    @Test
    fun `start uses profile files and saves and stop unregisters the session`() {
        assertEquals(HostState.STOPPED, host.state)
        assertEquals(HostStartResult.Started, host.start(request(skill = 2)))
        assertEquals(HostState.RUNNING, host.state)
        assertEquals(2, game().gameCvars.skill.typedValue)
        assertEquals("", cvars.find<String>("gamedir").typedValue)
        assertEquals(temp.resolve("session"), EngineWriteRoot.path())
        assertTrue(Files.exists(temp.resolve("session/save/save0/game.ssv.json")))
        assertTrue(NET.ip_sockets.all { it == null })
        val frame = game().level.framenum
        host.update(100)
        assertEquals(frame + 1, game().level.framenum)
        assertNotNull(Cmd.FindCommand("save"))
        host.stop("done")
        host.stop("again")
        assertEquals(HostState.STOPPED, host.state)
        assertTrue(SV_MAIN.games.isEmpty())
        assertNull(Cmd.FindCommand("map"))
        assertNull(Cmd.FindCommand("save"))
        assertEquals(previousRoot, EngineWriteRoot.path())
        assertEquals(HostStartResult.Started, host.start(request(skill = 3)))
        assertEquals(3, game().gameCvars.skill.typedValue)
    }

    @Test
    fun `new profile selects its mod and save root without repeating client bootstrap`() {
        assertEquals(HostStartResult.Started, host.start(request()))
        host.stop("switch profile")
        val content = temp.resolve("content")
        Files.createDirectories(content.resolve("baseq2"))
        val maps = Files.createDirectories(content.resolve("custom/maps"))
        Files.copy(Path.of("src/test/resources/baseq2/maps/testbox.bsp"), maps.resolve("modmap.bsp"))
        val modProfile = CakeGameProfile("mod-session", content.toString(), "custom")
        assertEquals(HostStartResult.Started, host.start(request().copy(profile = modProfile, map = "modmap")))
        assertEquals("custom", cvars.find<String>("game").typedValue)
        assertEquals("custom", cvars.find<String>("gamedir").typedValue)
        assertEquals("modmap", cvars.find<String>("mapname").typedValue)
        assertTrue(Files.exists(temp.resolve("mod-session/save/save0/game.ssv.json")))
        host.stop("switch back")
        assertEquals(HostStartResult.Started, host.start(request()))
        assertEquals("", cvars.find<String>("gamedir").typedValue)
    }

    @Test
    fun `shutdown restores the command that preceded a session across map changes`() {
        var called = false
        Cmd.AddCommand("status") { called = true }
        try {
            assertEquals(HostStartResult.Started, host.start(request()))
            Cmd.ExecuteString("gamemap testbox2")
            host.stop("done")
            Cmd.ExecuteString("status")
            assertTrue(called)
        } finally {
            Cmd.RemoveCommand("status")
        }
    }

    @Test
    fun `singleplayer pauses but resumes without simulation catchup`() {
        assertEquals(HostStartResult.Started, host.start(request()))
        val frame = game().level.framenum
        host.setPaused(true)
        repeat(5) { host.update(100) }
        assertEquals(frame, game().level.framenum)
        host.setPaused(false)
        host.update(100)
        assertEquals(frame + 1, game().level.framenum)
    }

    @Test
    fun `multiplayer continues while menus request pause`() {
        for (mode in listOf(HostMode.DEATHMATCH, HostMode.COOP)) {
            assertEquals(HostStartResult.Started, host.start(request(mode)))
            assertNotNull(NET.ip_sockets[Defines.NS_SERVER])
            val frame = game().level.framenum
            host.setPaused(true)
            host.update(100)
            assertFalse(cvars.find<Boolean>("paused").typedValue)
            assertEquals(frame + 1, game().level.framenum)
            host.stop("next mode")
            assertTrue(NET.ip_sockets.all { it == null })
        }
    }

    @Test
    fun `occupied multiplayer port rejects and can be retried without stale channels`() {
        DatagramSocket(0).use { occupied ->
            cvars.find<Int>("port").forceSet(occupied.localPort)
            assertInstanceOf(HostStartResult.Rejected::class.java, host.start(request(HostMode.COOP)))
            assertEquals(HostState.STOPPED, host.state)
            assertTrue(NET.ip_sockets.all { it == null })
            assertTrue(NET.ip_channels.all { it == null })
            assertNull(Cmd.FindCommand("map"))
        }
        assertEquals(HostStartResult.Started, host.start(request(HostMode.COOP)))
        assertNotNull(NET.ip_sockets[Defines.NS_SERVER])
        assertNull(NET.ip_sockets[Defines.NS_CLIENT])
    }

    @Test
    fun `missing map and bad save directory reject cleanly and allow retry`() {
        assertInstanceOf(HostStartResult.Rejected::class.java, host.start(request().copy(map = "missing")))
        assertEquals(HostState.STOPPED, host.state)
        assertNull(Cmd.FindCommand("map"))
        Files.writeString(temp.resolve("blocked"), "not a directory")
        assertInstanceOf(HostStartResult.Rejected::class.java,
            host.start(request().copy(profile = profile.copy(id = "blocked"))))
        assertEquals(HostState.STOPPED, host.state)
        assertEquals(previousRoot, EngineWriteRoot.path())
        assertEquals(HostStartResult.Started, host.start(request()))
    }

    @Test
    fun `console transitions retain manager and shutdown clears deferred commands`() {
        assertEquals(HostStartResult.Started, host.start(request()))
        val mapCommand = Cmd.FindCommand("map")
        Cmd.ExecuteString("gamemap testbox2")
        assertEquals(HostState.RUNNING, host.state)
        assertSame(mapCommand, Cmd.FindCommand("map"))
        assertEquals("testbox2", cvars.find<String>("mapname").typedValue)
        var ran = false
        Cmd.AddCommand("host_test_deferred") { ran = true }
        try {
            Cbuf.AddText("host_test_deferred")
            Cbuf.CopyToDefer()
            Cmd.ExecuteString("killserver")
            host.update(0)
            Cbuf.InsertFromDefer()
            Cbuf.Execute()
            assertFalse(ran)
            assertEquals(HostState.STOPPED, host.state)
            assertNull(Cmd.FindCommand("killserver"))
            assertNull(Cmd.FindCommand("map"))
        } finally {
            Cmd.RemoveCommand("host_test_deferred")
        }
    }

    @Test
    fun `server drop and later command replacement are cleaned up without removing another owner`() {
        assertEquals(HostStartResult.Started, host.start(request()))
        assertThrows(longjmpException::class.java) { Com.Error(Defines.ERR_DROP, "test drop") }
        host.update(0)
        assertEquals(HostState.STOPPED, host.state)
        assertNull(Cmd.FindCommand("save"))
        assertEquals(HostStartResult.Started, host.start(request()))
        var called = false
        Cmd.AddCommand("status", true) { called = true }
        host.stop("done")
        try {
            Cmd.ExecuteString("status")
            assertTrue(called)
        } finally {
            Cmd.RemoveCommand("status")
        }
    }
}
