package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.MainCommon
import jake2.qcommon.ServerStates
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.NET
import jake2.qcommon.network.Netchan
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.server.save.ServerSaveFiles
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.*
import org.demoth.cake.profile.CakeGameProfile
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class EmbeddedCakeSavesTest {
    @TempDir lateinit var root: Path
    private lateinit var host: EmbeddedCakeHost
    private lateinit var previousRoot: Path
    private val cvars = Cvar.getInstance()
    private var remote = false
    private val profile get() = CakeGameProfile("first", Path.of("src/test/resources").toAbsolutePath().toString())

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        cvars.clear()
        Globals.server_state = ServerStates.SS_DEAD
        MainCommon.registerCvars(false)
        Netchan.Netchan_Init()
        cvars.find<Int>("port").forceSet(0)
        cvars.find<Int>("clientport").forceSet(0)
        host = EmbeddedCakeHost(root)
        host.initialize { if (remote) "Disconnect from remote first." else null }
    }

    @AfterEach
    fun cleanup() {
        host.dispose()
        NET.Config(false)
        EngineWriteRoot.setRoot(previousRoot)
        cvars.clear()
    }

    private fun start(mode: HostMode = HostMode.SINGLEPLAYER) {
        assertEquals(HostStartResult.Started, host.start(HostRequest(profile, "testbox", mode)))
    }

    private fun spawn(health: Int = 73) {
        val manager = host.server!!
        val client = manager.clients.single()
        client.state = ClientStates.CS_SPAWNED
        client.edict = manager.games.getValue("default").gameExports.getEdict(1)
        client.edict.getClient().playerState.stats[Defines.STAT_HEALTH] = health.toShort()
    }

    private fun entries() = assertInstanceOf(HostSaveCatalogResult.Available::class.java, host.listSaves()).entries

    @Test
    fun `no profile remote and disposed operations reject without accessing fallback storage`() {
        assertInstanceOf(HostSaveCatalogResult.Rejected::class.java, host.listSaves())
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.loadGame("slot"))
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.saveGame())
        host.activateProfile(profile)
        assertTrue(entries().isEmpty())
        assertNull(host.saveAccess().loadRejectionReason)
        assertNotNull(host.saveAccess().saveRejectionReason)
        remote = true
        assertNotNull(host.saveAccess().loadRejectionReason)
        assertInstanceOf(HostSaveCatalogResult.Rejected::class.java, host.listSaves())
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.loadGame("save0"))
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.saveGame("slot", true))
        assertFalse(Files.exists(root.resolve("first/save")))
        host.dispose()
        assertInstanceOf(HostSaveCatalogResult.Rejected::class.java, host.listSaves())
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.loadGame("save0"))
    }

    @Test
    fun `catalog and load follow selected profile while idle and use profile rather than shared root`() {
        start()
        ServerSaveFiles(root.resolve("first")).copy("save0", "console slot")
        host.stop("browse")
        assertEquals(setOf("save0", "console slot"), entries().map { it.slot }.toSet())
        EngineWriteRoot.setRoot(root.resolve("unrelated"))
        assertEquals(setOf("save0", "console slot"), entries().map { it.slot }.toSet())
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.loadGame("console slot"))
        EngineWriteRoot.setRoot(root.resolve("first"))
        host.activateProfile(profile.copy(id = "second"))
        assertTrue(entries().isEmpty())
        assertEquals("second", (host.listSaves() as HostSaveCatalogResult.Available).profileId)
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.loadGame("console slot"))
        host.activateProfile(profile)
        assertEquals(HostSaveResult.Completed("console slot"), host.loadGame("console slot"))
        assertEquals("testbox", host.server!!.games.getValue("default").mapName)
    }

    @Test
    fun `singleplayer save requires spawned living player and protects reserved slots and overwrite`() {
        start()
        assertNotNull(host.saveAccess().saveRejectionReason)
        spawn(0)
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.saveGame("dead"))
        spawn()
        assertNull(host.saveAccess().saveRejectionReason)
        for (slot in listOf("current", "save0", "../outside")) {
            assertInstanceOf(HostSaveResult.Rejected::class.java, host.saveGame(slot, true))
        }
        val created = assertInstanceOf(HostSaveResult.Completed::class.java, host.saveGame())
        assertTrue(created.slot.startsWith("manual-"))
        val file = root.resolve("first/save/${created.slot}/game.ssv.json")
        val before = Files.readAllBytes(file)
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.saveGame(created.slot))
        assertArrayEquals(before, Files.readAllBytes(file))
        assertEquals(HostSaveResult.Completed(created.slot), host.saveGame(created.slot, overwrite = true))
        host.stop("load idle")
        assertEquals(HostSaveResult.Completed(created.slot), host.loadGame(created.slot))
    }

    @Test
    fun `coop and deathmatch saves are visible but cannot be loaded through singleplayer API`() {
        for (mode in listOf(HostMode.COOP, HostMode.DEATHMATCH)) {
            start(mode)
            val manager = host.server!!
            manager.games.getValue("default").SV_WriteServerFile(true)
            ServerSaveFiles(root.resolve("first")).copy("current", "multiplayer")
            assertNotNull(host.saveAccess().saveRejectionReason)
            assertNotNull(host.saveAccess().loadRejectionReason)
            assertInstanceOf(HostSaveResult.Rejected::class.java, host.saveGame("slot"))
            host.stop("inspect")
            val entry = entries().single { it.slot == "multiplayer" }
            assertEquals(mode, entry.mode)
            assertNotNull(entry.loadRejectionReason)
            assertInstanceOf(HostSaveResult.Rejected::class.java, host.loadGame("multiplayer"))
            assertEquals(HostState.STOPPED, host.state)
        }
    }

    @Test
    fun `corrupt entries do not hide good saves and preflight rejection preserves live game`() {
        start()
        val manager = host.server!!
        val live = manager.games.getValue("default")
        val files = ServerSaveFiles(root.resolve("first"))
        files.copy("save0", "corrupt")
        Files.writeString(root.resolve("first/save/corrupt/server_mapcmd.ssv.json"), "bad json")
        assertNotNull(entries().single { it.slot == "corrupt" }.loadRejectionReason)
        assertNull(entries().single { it.slot == "save0" }.loadRejectionReason)
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.loadGame("corrupt"))
        // Browsing reads metadata only; authoritative preflight still rejects a corrupt game envelope.
        files.copy("save0", "bad-game")
        Files.writeString(root.resolve("first/save/bad-game/game.ssv.json"), "bad json")
        assertInstanceOf(HostSaveResult.Rejected::class.java, host.loadGame("bad-game"))
        assertSame(live, manager.games["default"])
    }

    @Test
    fun `typed save failure keeps live game and late load failure leaves reusable idle Host`() {
        start()
        spawn()
        assertEquals(HostSaveResult.Completed("checkpoint"), host.saveGame("checkpoint"))
        val manager = host.server!!
        val live = manager.games.getValue("default")
        val blocked = root.resolve("first/save/checkpoint/game.ssv.json")
        Files.delete(blocked)
        Files.createDirectory(blocked)
        Files.writeString(blocked.resolve("blocker"), "do not replace")
        assertInstanceOf(HostSaveResult.Failed::class.java, host.saveGame("checkpoint", true))
        assertSame(live, manager.games["default"])
        Files.delete(blocked.resolve("blocker"))
        Files.delete(blocked)
        assertEquals(HostSaveResult.Completed("checkpoint"), host.saveGame("checkpoint", true))
        val current = root.resolve("first/save/current/game.ssv.json")
        Files.delete(current)
        Files.createDirectory(current)
        Files.writeString(current.resolve("blocker"), "force late failure")
        assertInstanceOf(HostSaveResult.Failed::class.java, host.loadGame("checkpoint"))
        assertEquals(HostState.STOPPED, host.state)
        assertSame(manager, host.server)
        Files.delete(current.resolve("blocker"))
        Files.delete(current)
        assertEquals(HostSaveResult.Completed("checkpoint"), host.loadGame("checkpoint"))
    }

    @Test
    fun `catalog IO failure is distinct from an empty save list`() {
        host.activateProfile(profile)
        Files.writeString(root.resolve("first/save"), "not a directory")
        assertInstanceOf(HostSaveCatalogResult.Failed::class.java, host.listSaves())
    }
}
