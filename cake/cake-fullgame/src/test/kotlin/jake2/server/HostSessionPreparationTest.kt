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

    @ParameterizedTest
    @ValueSource(strings = ["game.ssv.json", "testbox.sav.json", "testbox.sv2.json"])
    fun `missing save components preserve Game and working save`(file: String) {
        save(false)
        Files.delete(root.resolve("save/slot/$file"))
        // Require a level archive by making this a manual save envelope.
        val gameFile = root.resolve("save/slot/game.ssv.json")
        if (Files.exists(gameFile)) Files.writeString(gameFile,
            Files.readString(gameFile).replace("\"autoSaved\":true", "\"autoSaved\":false"))
        val game = host.games.getValue("default")
        val original = Files.readString(root.resolve("save/current/game.ssv.json"))
        host.loadGame(listOf("load", "slot"))
        assertSame(game, host.games["default"])
        assertEquals(original, Files.readString(root.resolve("save/current/game.ssv.json")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["game.ssv.json", "testbox.sav.json", "testbox.sv2.json"])
    fun `malformed save components are rejected before replacement`(file: String) {
        save(false)
        Files.writeString(root.resolve("save/slot/$file"), "{ broken json")
        val game = host.games.getValue("default")
        host.loadGame(listOf("load", "slot"))
        assertSame(game, host.games["default"])
    }

    @Test
    fun `manual save requires current level even when both archive files are absent`() {
        save(false)
        Files.delete(root.resolve("save/slot/testbox.sav.json"))
        Files.delete(root.resolve("save/slot/testbox.sv2.json"))
        val path = root.resolve("save/slot/game.ssv.json")
        val json = Files.readString(path)
        assertTrue(json.contains("\"autoSaved\":true"))
        Files.writeString(path, json.replace("\"autoSaved\":true", "\"autoSaved\":false"))
        val game = host.games.getValue("default")
        host.loadGame(listOf("load", "slot"))
        assertSame(game, host.games["default"])
    }

    @ParameterizedTest
    @ValueSource(strings = ["version", "capacity", "setting"])
    fun `inconsistent metadata is rejected without modifying live settings`(damage: String) {
        save(false)
        val store = jake2.server.save.ServerSaveJsonStore.forWriteDir(root.toString())
        when (damage) {
            "version" -> {
                val file = root.resolve("save/slot/server_mapcmd.ssv.json")
                Files.writeString(file, Files.readString(file).replace("\"schemaVersion\":1", "\"schemaVersion\":99"))
            }
            else -> {
                val name = if (damage == "capacity") "maxclients" else "coop"
                val value = if (damage == "capacity") "4" else "invalid"
                store.writeLatchedCvars("slot", store.readLatchedCvars("slot").cvars().map {
                    if (it.name() == name) jake2.server.save.ServerSaveJsonStore.LatchedCvarSnapshot(name, value) else it
                })
            }
        }
        val game = host.games.getValue("default")
        host.loadGame(listOf("load", "slot"))
        assertSame(game, host.games["default"])
        assertEquals(1, cvars.find<Int>("maxclients").typedValue)
        assertFalse(cvars.find<Boolean>("coop").typedValue)
    }

    @Test
    fun `explicit load restores archived state even when level revisit is disabled`() {
        save(false)
        val original = host.games.getValue("default")
        original.configstrings[Defines.CS_NAME] = "Saved level name"
        original.SV_WriteLevelFile()
        ServerSaveFiles(root).copy("current", "slot")
        cvars.find<Boolean>("sv_noreload").forceSet(true)
        host.stop("test", false)
        host.loadGame(listOf("load", "slot"))
        assertEquals("Saved level name", host.games.getValue("default").configstrings[Defines.CS_NAME])
    }

    @Test
    fun `server level read failure stops before gameplay restoration`() {
        save(false)
        val game = host.games.getValue("default")
        val gameplay = game.gameExports
        var restoredGameplay = false
        game.gameExports = object : GameExports by gameplay {
            override fun ReadLevel(filename: String) { restoredGameplay = true }
        }
        Files.delete(root.resolve("save/current/testbox.sv2.json"))
        assertThrows(IllegalStateException::class.java) { game.readLevelFile() }
        assertFalse(restoredGameplay)
    }

    @Test
    fun `level entry autosave without archives remains loadable`() {
        host.map(listOf("map", "testbox"))
        host.stop("test", false)
        host.loadGame(listOf("load", "save0"))
        assertEquals("testbox", host.games.getValue("default").mapName)
    }

    @Test
    fun `gameplay IO failures propagate and failed save does not replace slot`() {
        save(true)
        val game = host.games.getValue("default")
        val original = Files.readString(root.resolve("save/slot/game.ssv.json"))
        assertThrows(IllegalStateException::class.java) {
            game.gameExports.readGameLocals(root.resolve("missing.json").toString())
        }
        assertThrows(IllegalStateException::class.java) {
            game.gameExports.ReadLevel(root.resolve("missing.json").toString())
        }
        Files.delete(root.resolve("save/current/game.ssv.json"))
        Files.createDirectory(root.resolve("save/current/game.ssv.json"))
        assertThrows(IllegalStateException::class.java) { game.saveGame(listOf("save", "slot")) }
        assertEquals(original, Files.readString(root.resolve("save/slot/game.ssv.json")))
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
