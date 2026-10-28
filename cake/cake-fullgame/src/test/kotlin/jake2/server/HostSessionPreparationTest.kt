package jake2.server

import jake2.qcommon.*
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
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
    private lateinit var network: HostNetworkFixture
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
        network = HostNetworkFixture()
        host = ServerHost(network.server)
    }

    @AfterEach
    fun cleanup() {
        host.close()
        network.close()
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
    fun `deathmatch map and gamemap never create or overwrite autosave`(existingSingleplayerSave: Boolean) {
        if (existingSingleplayerSave) {
            assertEquals(ServerHost.OperationResult.Completed, host.map("testbox"))
            host.stop("start deathmatch", false)
        }
        val autosave = root.resolve("save/autosave")
        fun contents(): Map<String, List<Byte>> = if (!Files.exists(autosave)) emptyMap() else
            Files.walk(autosave).use { paths -> paths.filter(Files::isRegularFile).toList()
                .associate { autosave.relativize(it).toString() to Files.readAllBytes(it).toList() } }
        val before = contents()
        assertEquals(existingSingleplayerSave, before.isNotEmpty())
        cvars.find<Boolean>("deathmatch").forceSet(true)
        assertEquals(ServerHost.OperationResult.Completed, host.map("testbox"))
        assertTrue(host.games.getValue("default").isDeathmatch)
        assertEquals(before, contents())
        assertEquals(ServerHost.OperationResult.Completed, host.changeMap("testbox2"))
        assertEquals("testbox2", host.games.getValue("default").mapName)
        assertEquals(before, contents())
        assertEquals(existingSingleplayerSave, Files.exists(autosave))
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.saveGame("manual-dm"))
        assertFalse(Files.exists(root.resolve("save/manual-dm")))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `load after stop prepares saved capacity before Game allocation`(coop: Boolean) {
        save(coop)
        host.stop("test", false)
        network.server.configure(false)
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
        assertEquals(coop, network.serverTransport.udpEndpoint != null)
        // Exercise the snapshot ring allocated from the prepared client capacity.
        host.clients[0].edict = loaded.gameExports.getEdict(1)
        loaded.buildClientFrameMessages(host.clients[0])
    }

    @Test
    fun `fresh Host loads save and can restart again`() {
        save(false)
        host.close()
        network.close()
        network = HostNetworkFixture()
        host = ServerHost(network.server)
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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `restoration keeps removed entities absent and restores surviving references`(load: Boolean) {
        host.map(listOf("map", "testbox2"))
        val original = host.games.getValue("default")
        val gameplay = original.gameExports as jake2.game.GameExportsImpl
        fun entity(classname: String) = gameplay.g_edicts.first { it.inuse && it.classname == classname }
        val removed = entity("ammo_bullets")
        val survivor = entity("ammo_cells")
        val target = entity("ammo_shells")
        val removedIndex = removed.index
        val survivorIndex = survivor.index
        val targetIndex = target.index
        assertTrue(removedIndex < targetIndex, "Removed slot must be inside the restored entity range")
        survivor.health = 73
        survivor.enemy = target
        gameplay.game.clients[0].chase_target = target
        gameplay.level.sound_entity = target
        gameplay.freeEntity(removed)
        original.SV_WriteLevelFile()
        original.SV_WriteServerFile(true)
        ServerSaveFiles(root).copy("current", "slot")

        if (load) {
            host.loadGame(listOf("load", "slot"))
        } else {
            host.changeMap(listOf("gamemap", "testbox"))
            host.changeMap(listOf("gamemap", "testbox2"))
        }

        val restored = host.games.getValue("default")
        assertFalse(restored.gameExports.getEdict(removedIndex).inuse)
        val restoredSurvivor = restored.gameExports.getEdict(survivorIndex) as jake2.game.GameEntity
        assertTrue(restoredSurvivor.inuse)
        assertEquals(73, restoredSurvivor.health)
        assertSame(restored.gameExports.getEdict(targetIndex), restoredSurvivor.enemy)
        if (load) {
            val restoredGameplay = restored.gameExports as jake2.game.GameExportsImpl
            assertSame(restored.gameExports.getEdict(targetIndex), restoredGameplay.game.clients[0].chase_target)
            assertSame(restored.gameExports.getEdict(targetIndex), restoredGameplay.level.sound_entity)
        }
        assertEquals(0, restored.baselines[removedIndex].modelindex)
    }

    @Test
    fun `revisited moving entity retains movement component and completes movement`() {
        host.map(listOf("map", "testbox"))
        val original = host.games.getValue("default")
        val gameplay = original.gameExports as jake2.game.GameExportsImpl
        val mover = gameplay.G_Spawn()
        mover.classname = "saved_mover"
        mover.movetype = jake2.game.GameDefines.MOVETYPE_PUSH
        val movement = jake2.game.components.MoveInfo(speed = 100f, accel = 100f, decel = 100f)
        mover.components[jake2.game.components.MoveInfo::class.java.name] = movement
        val completed = jake2.game.adapters.SuperAdapter.registerThink("test_saved_mover_done") { self, _ ->
            self.health = 73
            true
        }
        jake2.game.func.startMovement(mover, jake2.qcommon.math.Vector3f(100f, 0f, 0f), completed, gameplay)
        movement.remaining_distance = 5f
        mover.think.action = jake2.game.adapters.SuperAdapter.think("move_final")
        mover.think.nextTime = gameplay.level.time + Defines.FRAMETIME
        val index = mover.index
        host.changeMap(listOf("gamemap", "testbox2"))
        host.changeMap(listOf("gamemap", "testbox"))
        val restored = host.games.getValue("default").gameExports.getEdict(index) as jake2.game.GameEntity
        assertEquals(73, restored.health)
        assertNotNull(restored.components[jake2.game.components.MoveInfo::class.java.name])
    }

    @Test
    fun `explicit load restores archived state`() {
        save(false)
        val original = host.games.getValue("default")
        original.configstrings[Defines.CS_NAME] = "Saved level name"
        original.SV_WriteLevelFile()
        ServerSaveFiles(root).copy("current", "slot")
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
        host.loadGame(listOf("load", "autosave"))
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
        assertInstanceOf(ServerHost.OperationResult.Failed::class.java, host.saveGame("slot"))
        assertEquals(original, Files.readString(root.resolve("save/slot/game.ssv.json")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["copy", "game", "level", "idle"])
    fun `late load failure leaves idle Host and allows retry`(stage: String) {
        save(true)
        // A shared-state failure must also discard unrelated Games, not leave half a Host alive.
        val other = host.createGameInstance(ChangeMapInfo("testbox2", false, false), null)
        host.games[other.name] = other
        host.clients[1].gameName = other.name
        cvars.find<Boolean>("paused").forceSet(true)
        assertNotNull(network.serverTransport.udpEndpoint)
        if (stage == "idle") {
            host.stop("test", false)
            network.server.configure(false)
        }
        val path = when (stage) {
            "copy" -> root.resolve("save/current/game.ssv.json")
            "game" -> root.resolve("save/slot/game.ssv.json")
            else -> root.resolve("save/slot/testbox.sav.json")
        }
        val original = Files.readString(path)
        when (stage) {
            "copy" -> {
                Files.delete(path)
                Files.createDirectory(path)
                Files.writeString(path.resolve("blocker"), "nonempty directory")
            }
            // These have valid envelopes, but invalid typed gameplay data/reference indices.
            "game" -> Files.writeString(path, original.replace("\"playerState\":", "\"missingPlayerState\":"))
            else -> Files.writeString(path, original.replace("\"entityNumber\":0", "\"entityNumber\":999999"))
        }
        var deferredRan = false
        jake2.qcommon.exec.Cmd.AddCommand("test_failed_load_deferred") { deferredRan = true }
        try {
            jake2.qcommon.exec.Cbuf.AddText("test_failed_load_deferred")
            jake2.qcommon.exec.Cbuf.CopyToDefer()
            val result = host.loadGame("slot")
            assertInstanceOf(ServerHost.OperationResult.Failed::class.java, result)
            assertTrue(host.games.isEmpty())
            assertTrue(host.clients.isEmpty())
            assertFalse(host.isPaused)
            assertNull(network.serverTransport.udpEndpoint)
            assertEquals(ServerStates.SS_DEAD, Globals.server_state)
            assertEquals("", cvars.find<String>("mapname").typedValue)
            assertNotNull(jake2.qcommon.exec.Cmd.FindCommand("load"))
            host.update(100)
            jake2.qcommon.exec.Cbuf.InsertFromDefer()
            jake2.qcommon.exec.Cbuf.Execute()
            assertFalse(deferredRan)

            if (stage == "copy") {
                Files.delete(path.resolve("blocker"))
                Files.delete(path)
            }
            Files.writeString(path, original)
            assertEquals(ServerHost.OperationResult.Completed, host.loadGame("slot"))
            assertEquals("testbox", host.games.getValue("default").mapName)
        } finally {
            jake2.qcommon.exec.Cmd.RemoveCommand("test_failed_load_deferred")
        }
    }

    @Test
    fun `load port failure leaves reusable idle Host`() {
        save(true)
        host.stop("test", false)
        network.server.configure(false)
        java.net.DatagramSocket(0).use { occupied ->
            cvars.find<Int>("port").forceSet(occupied.localPort)
            assertInstanceOf(ServerHost.OperationResult.Failed::class.java, host.loadGame("slot"))
            assertTrue(host.games.isEmpty())
            assertTrue(host.clients.isEmpty())
            assertNull(network.serverTransport.udpEndpoint)
        }
        cvars.find<Int>("port").forceSet(0)
        assertEquals(ServerHost.OperationResult.Completed, host.loadGame("slot"))
    }

    @Test
    fun `console load failure does not abort following startup command`() {
        save(false)
        val file = root.resolve("save/slot/testbox.sav.json")
        Files.writeString(file, Files.readString(file).replace("\"entityNumber\":0", "\"entityNumber\":999999"))
        jake2.qcommon.exec.Cbuf.AddText("load slot; map testbox2")
        jake2.qcommon.exec.Cbuf.Execute()
        assertEquals("testbox2", host.games.getValue("default").mapName)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "*", "missing", "unsupported.dm2"])
    fun `invalid map requests preserve live Game and workspace`(level: String) {
        save(false)
        val game = host.games.getValue("default")
        val client = host.clients[0]
        val metadata = Files.readString(root.resolve("save/current/server_mapcmd.ssv.json"))
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.map(level))
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.changeMap(level))
        assertSame(game, host.games["default"])
        assertSame(client, host.clients[0])
        assertEquals(ServerStates.SS_GAME, game.state)
        assertEquals(metadata, Files.readString(root.resolve("save/current/server_mapcmd.ssv.json")))
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 2])
    fun `invalid targeted gamemap leaves session untouched`(clientIndex: Int) {
        save(false)
        val game = host.games.getValue("default")
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.changeMap("testbox2", clientIndex))
        assertSame(game, host.games["default"])
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `map autosave failure leaves idle Host and permits restart`(newSession: Boolean) {
        save(false)
        val blocked = root.resolve("save/autosave")
        jake2.server.save.deleteSaveSlot(root, "autosave")
        Files.writeString(blocked, "not a checkpoint directory")
        val result = if (newSession) host.map("testbox2") else host.changeMap("testbox2")
        assertInstanceOf(ServerHost.OperationResult.Failed::class.java, result)
        assertTrue(host.games.isEmpty())
        assertTrue(host.clients.isEmpty())
        assertFalse(host.isPaused)
        assertNull(network.serverTransport.udpEndpoint)
        assertEquals(ServerStates.SS_DEAD, Globals.server_state)
        assertNotNull(jake2.qcommon.exec.Cmd.FindCommand("map"))
        Files.delete(blocked)
        assertEquals(ServerHost.OperationResult.Completed, host.map("testbox"))
    }

    @Test
    fun `gamemap port failure is recoverable without global shutdown callbacks`() {
        cvars.find<Boolean>("coop").forceSet(true)
        var globalShutdown = false
        val previous = jake2.qcommon.exec.Cmd.FindCommand("cl_drop")
        jake2.qcommon.exec.Cmd.AddCommand("cl_drop", true) { globalShutdown = true }
        try {
            java.net.DatagramSocket(0).use { occupied ->
                cvars.find<Int>("port").forceSet(occupied.localPort)
                assertInstanceOf(ServerHost.OperationResult.Failed::class.java, host.changeMap("testbox"))
                assertTrue(host.games.isEmpty())
                assertTrue(host.clients.isEmpty())
                assertFalse(globalShutdown)
            }
            cvars.find<Int>("port").forceSet(0)
            assertEquals(ServerHost.OperationResult.Completed, host.changeMap("testbox"))
        } finally {
            jake2.qcommon.exec.Cmd.RemoveCommand("cl_drop")
            if (previous != null) jake2.qcommon.exec.Cmd.AddCommand("cl_drop", previous.description, previous.function)
        }
    }

    @Test
    fun `stop owns cleanup and preserves final packets and client socket`() {
        save(true)
        network.clearLocalTraffic()
        cvars.find<Int>("clientport").forceSet(0)
        assertTrue(network.client.configure(true))
        val clientSocket = network.clientTransport.udpEndpoint!!
        val serverEndpoint = network.serverTransport.udpEndpoint!!
        val mapCommand = jake2.qcommon.exec.Cmd.FindCommand("map")
        val client = host.clients[0]
        client.state = ClientStates.CS_CONNECTED
        client.netchan.setup(jake2.qcommon.network.netadr_t(), 0)
        network.sendLocal(Defines.NS_CLIENT, 8, ByteArray(8))
        cvars.find<Boolean>("paused").forceSet(true)
        var deferredRan = false
        jake2.qcommon.exec.Cmd.AddCommand("test_stopped_deferred") { deferredRan = true }
        try {
            jake2.qcommon.exec.Cbuf.AddText("test_stopped_deferred")
            jake2.qcommon.exec.Cbuf.CopyToDefer()
            host.stop("test stop", false)
            assertFalse(serverEndpoint.isOpen)
            assertNull(network.serverTransport.udpEndpoint)
            assertSame(clientSocket, network.clientTransport.udpEndpoint)
            assertTrue(clientSocket.isOpen)
            assertTrue(host.games.isEmpty())
            assertTrue(host.clients.isEmpty())
            assertFalse(host.isPaused)
            assertEquals("", cvars.find<String>("mapname").typedValue)
            assertSame(mapCommand, jake2.qcommon.exec.Cmd.FindCommand("map"))
            assertNull(network.receiveLocal(Defines.NS_SERVER))
            assertNotNull(network.receiveLocal(Defines.NS_CLIENT))
            assertNotNull(network.receiveLocal(Defines.NS_CLIENT))
            host.stop("again", false)
            assertNull(network.receiveLocal(Defines.NS_CLIENT))
            jake2.qcommon.exec.Cbuf.InsertFromDefer()
            jake2.qcommon.exec.Cbuf.Execute()
            assertFalse(deferredRan)
            host.close()
            host.close()
            assertNull(jake2.qcommon.exec.Cmd.FindCommand("map"))
        } finally {
            jake2.qcommon.exec.Cmd.RemoveCommand("test_stopped_deferred")
            network.client.configure(false)
            network.clearLocalTraffic()
        }
    }

    @Test
    fun `killserver preserves queued gamemap startup`() {
        save(true)
        val mapCommand = jake2.qcommon.exec.Cmd.FindCommand("map")
        jake2.qcommon.exec.Cbuf.AddText("killserver; gamemap testbox2")
        jake2.qcommon.exec.Cbuf.Execute()
        assertEquals("testbox2", host.games.getValue("default").mapName)
        assertSame(mapCommand, jake2.qcommon.exec.Cmd.FindCommand("map"))
        assertNotNull(network.serverTransport.udpEndpoint)
    }

    @Test
    fun `notification failure still cleans session and close releases commands`() {
        save(true)
        val client = host.clients[0]
        client.state = ClientStates.CS_CONNECTED
        network.failTransmissions(client) // Fault injection at final packet transmission.
        assertThrows(IllegalStateException::class.java) { host.close() }
        assertTrue(host.games.isEmpty())
        assertTrue(host.clients.isEmpty())
        assertNull(network.serverTransport.udpEndpoint)
        assertFalse(host.isPaused)
        assertNull(jake2.qcommon.exec.Cmd.FindCommand("map"))
        host.close()
        network.close()
    }

    @ParameterizedTest
    @ValueSource(strings = ["server_mapcmd.ssv.json", "game.ssv.json", "destination"])
    fun `save failures preserve live Game and can be retried`(stage: String) {
        save(true)
        val game = host.games.getValue("default")
        val client = host.clients[0]
        val blocked = if (stage == "destination") root.resolve("save/slot")
            else root.resolve("save/current/$stage")
        if (stage == "destination") {
            jake2.server.save.deleteSaveSlot(root, "slot")
            Files.writeString(blocked, "not a checkpoint directory")
        } else {
            Files.delete(blocked)
            Files.createDirectory(blocked)
            Files.writeString(blocked.resolve("blocker"), "prevent replacement")
        }
        val result = host.saveGame("slot")
        assertInstanceOf(ServerHost.OperationResult.Failed::class.java, result)
        assertSame(game, host.games["default"])
        assertSame(client, host.clients[0])
        assertTrue(host.isRunning)
        assertNotNull(network.serverTransport.udpEndpoint)
        host.update(100)
        if (stage != "destination") Files.delete(blocked.resolve("blocker"))
        Files.delete(blocked)
        assertEquals(ServerHost.OperationResult.Completed, host.saveGame("slot"))
        assertEquals(ServerHost.OperationResult.Completed, host.loadGame("slot"))
    }

    @Test
    fun `save eligibility rejects idle unspawned dead and deathmatch sessions`() {
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.saveGame("slot"))
        host.map("testbox")
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.saveGame("slot"))
        val game = host.games.getValue("default")
        val client = host.clients[0]
        client.state = ClientStates.CS_SPAWNED
        client.edict = game.gameExports.getEdict(1)
        client.edict.getClient().playerState.stats[Defines.STAT_HEALTH] = 0
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.saveGame("slot"))
        client.edict.getClient().playerState.stats[Defines.STAT_HEALTH] = 100
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.saveGame("current"))
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.saveGame("../bad"))
        assertFalse(Files.exists(root.resolve("save/slot")))
        assertEquals(ServerHost.OperationResult.Completed, host.saveGame("slot"))
        client.state = ClientStates.CS_FREE
        cvars.find<Boolean>("deathmatch").forceSet(true)
        host.map("testbox")
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.saveGame("dm"))
        assertFalse(Files.exists(root.resolve("save/dm")))
    }

    @Test
    fun `console save failure reports failure and continues queued commands`() {
        save(true)
        val blocked = root.resolve("save/current/game.ssv.json")
        Files.delete(blocked)
        Files.createDirectory(blocked)
        val output = StringBuilder()
        val sink = Com.ConsoleSink { _, message -> output.append(message) }
        var continued = false
        jake2.qcommon.exec.Cmd.AddCommand("test_after_save") { continued = true }
        Com.SetConsoleSink(sink)
        try {
            jake2.qcommon.exec.Cbuf.AddText("save slot; test_after_save")
            jake2.qcommon.exec.Cbuf.Execute()
            assertTrue(continued)
            assertTrue(output.contains("Save failed:"))
            assertFalse(output.contains("Done."))
            assertTrue(host.isRunning)
        } finally {
            Com.ClearConsoleSink(sink)
            jake2.qcommon.exec.Cmd.RemoveCommand("test_after_save")
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `missing map list preserves Host and continues queued commands`(running: Boolean) {
        if (running) save(true)
        assertNull(EngineVfs.loadBytes("maps.lst"), "Fixture must have no optional map list")
        val games = host.games.toMap()
        val clients = host.clients.toList()
        val socket = network.serverTransport.udpEndpoint
        val output = StringBuilder()
        val sink = Com.ConsoleSink { _, message -> output.append(message) }
        var continued = false
        jake2.qcommon.exec.Cmd.AddCommand("test_after_listmaps") { continued = true }
        Com.SetConsoleSink(sink)
        try {
            jake2.qcommon.exec.Cbuf.AddText("listmaps; test_after_listmaps")
            jake2.qcommon.exec.Cbuf.Execute()

            assertTrue(output.contains("Could not read maps.lst"))
            assertTrue(continued)
            assertEquals(running, host.isRunning)
            assertEquals(games, host.games)
            assertEquals(clients, host.clients.toList())
            assertSame(socket, network.serverTransport.udpEndpoint)
        } finally {
            Com.ClearConsoleSink(sink)
            jake2.qcommon.exec.Cmd.RemoveCommand("test_after_listmaps")
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `Game failure during save or archival stops unhealthy Game`(archive: Boolean) {
        save(true)
        val game = host.games.getValue("default")
        val failure = GameFailure(game, "invalid gameplay persistence state")
        game.gameExports = object : GameExports by game.gameExports {
            override fun WriteLevel(filename: String) { throw failure }
        }
        val result = if (archive) host.changeMap("testbox2") else host.saveGame("broken")
        assertInstanceOf(ServerHost.OperationResult.Failed::class.java, result)
        assertSame(failure, (result as ServerHost.OperationResult.Failed).cause)
        assertFalse(host.isRunning)
        assertTrue(host.clients.isEmpty())
        assertEquals(ServerHost.OperationResult.Completed, host.map("testbox"))
    }

    @Test
    fun `malformed BSP propagates Game failure without global client drop`() {
        val content = root.resolve("content")
        val map = content.resolve("baseq2/maps/broken.bsp")
        Files.createDirectories(map.parent)
        val bytes = Files.readAllBytes(Path.of("src/test/resources/baseq2/maps/testbox.bsp"))
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(8 + Defines.LUMP_TEXINFO * 8 + 4, 1)
        Files.write(map, bytes)
        EngineVfs.configure(VfsConfig(content, "baseq2", null,
            true, false, false, emptyList(), setOf("pak")))
        val previous = jake2.qcommon.exec.Cmd.FindCommand("cl_drop")
        var clientDropped = false
        jake2.qcommon.exec.Cmd.AddCommand("cl_drop", true) { clientDropped = true }
        try {
            val result = host.map("broken")
            assertInstanceOf(ServerHost.OperationResult.Failed::class.java, result)
            assertInstanceOf(GameFailure::class.java, (result as ServerHost.OperationResult.Failed).cause, result.cause.stackTraceToString())
            assertTrue(result.reason.contains("funny lump size"))
            assertFalse(clientDropped)
            assertFalse(host.isRunning)
            assertNotNull(jake2.qcommon.exec.Cmd.FindCommand("map"))
        } finally {
            jake2.qcommon.exec.Cmd.RemoveCommand("cl_drop")
            previous?.let { jake2.qcommon.exec.Cmd.AddCommand("cl_drop", it.function) }
        }
    }

    @Test
    fun `unreadable allocation metadata preserves running session`() {
        save(false)
        val game = host.games.getValue("default")
        val client = host.clients[0]
        Files.writeString(root.resolve("save/slot/server_latched_cvars.ssv.json"), "invalid")
        assertInstanceOf(ServerHost.OperationResult.Rejected::class.java, host.loadGame("slot"))
        assertSame(game, host.games["default"])
        assertSame(client, host.clients[0])
    }
}
