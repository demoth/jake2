package jake2.server

import jake2.qcommon.*
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.messages.server.ConfigStringMessage
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.network.messages.server.SpawnBaselineMessage
import jake2.qcommon.network.messages.server.StuffTextMessage
import jake2.qcommon.vfs.EngineWriteRoot
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ServerHandshakeTest {
    @TempDir lateinit var root: Path
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private lateinit var network: ServerNetwork
    private lateinit var host: ServerHost
    private lateinit var game: GameImportsImpl
    private lateinit var gameplay: Gameplay
    private lateinit var client: ClientNetworkInfo
    private val levelName = "Handshake fixture"
    // Sparse data spans several packets and includes the last usable index of each kind.
    private val expectedConfigstrings = listOf(ConfigStringMessage(Defines.CS_NAME, levelName)) +
        (0..23).map { ConfigStringMessage(Defines.CS_GENERAL + it * 3, "value-$it ${"x".repeat(96)}") } +
        ConfigStringMessage(Defines.MAX_CONFIGSTRINGS - 1, "last-config")
    private val expectedBaselines = ((1..640 step 2).toList() + (Defines.MAX_EDICTS - 1))
        .map { it to (1 + it % 31) }

    // Handshake tests need a callback counter, without loading a map or game assets.
    private class Gameplay : GameExports {
        val entity = object : ServerEntity(1) {
            override fun getOwner(): ServerEntity? = null
            override fun getClient(): ServerPlayerInfo? = null
        }
        var begins = 0
        var connects = 0
        var onBegin: () -> Unit = {}
        override fun ClientBegin(ent: ServerEntity) { begins++; onBegin() }
        override fun ClientConnect(ent: ServerEntity, userinfo: String): Boolean { connects++; return true }
        override fun ClientUserinfoChanged(ent: ServerEntity, userinfo: String) = userinfo
        override fun getEdict(index: Int) = entity
        override fun getNumEdicts() = 2
        override fun ClientCommand(ent: ServerEntity, args: List<String>) {}
        override fun G_RunFrame() {}
        override fun WriteGame(filename: String, autosave: Boolean) {}
        override fun readGameLocals(filename: String) {}
        override fun WriteLevel(filename: String) {}
        override fun ReadLevel(filename: String) {}
        override fun SpawnEntities(mapname: String, entities: String, spawnpoint: String) {}
        override fun ClientDisconnect(ent: ServerEntity) {}
        override fun ClientThink(ent: ServerEntity, cmd: usercmd_t) {}
        override fun fromPrevious(oldGame: GameExports, spawnPoint: String) {}
        override fun SaveClientData() {}
    }

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        EngineWriteRoot.setRoot(root)
        Globals.server_state = ServerStates.SS_DEAD
        Cvar.getInstance().clear()
        MainCommon.registerCvars(false)
        Cvar.getInstance().register("gamedir", CvarTypes.STRING, "baseq2", 0)
        network = TransportServerNetwork.standalone()
        host = ServerHost(network)
        client = host.clients.single()
        gameplay = Gameplay()
        game = game(gameplay, 41)
        admit()
    }

    @AfterEach
    fun cleanup() {
        host.close()
        Cvar.getInstance().clear()
        EngineWriteRoot.setRoot(previousRoot)
        Globals.server_state = previousState
    }

    private fun game(exports: Gameplay, count: Int) =
        GameImportsImpl(host, ChangeMapInfo("test.pcx", false, false)).apply {
            name = "default"
            state = ServerStates.SS_GAME
            spawncount = count
            gameExports = exports
            host.games[name] = this
            configstrings.fill(null)
            for (config in expectedConfigstrings) configstrings[config.index] = config.config
            for ((index, model) in expectedBaselines) {
                baselines[index].index = index
                baselines[index].modelindex = model
            }
        }

    private fun admit() {
        host.handleConnectionlessPacket(client.netchan.remoteAddress,
            "connect ${Defines.PROTOCOL_VERSION} 7 0 \\name\\handshake")
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        client.netchan.clearQueuedReliable()
    }

    private fun request(text: String): List<NetworkMessage> {
        client.netchan.clearQueuedReliable()
        host.handleClientMessages(client, listOf(StringCmdMessage(text)))
        return client.netchan.queuedReliable
    }

    private fun continuation(messages: List<NetworkMessage>): String? =
        messages.filterIsInstance<StuffTextMessage>().lastOrNull()?.text?.trim()

    private fun command(text: String): String? = continuation(request(text))

    // Act on the server's actual continuation commands, as a protocol-34 client does.
    private fun completeNegotiation(
        initial: String = command("new")!!,
        onChunk: (List<NetworkMessage>) -> Unit = {},
    ): String {
        var next = initial
        repeat(1000) {
            if (next.startsWith("precache ")) return next
            assertTrue(next.startsWith("cmd "), next)
            val messages = request(next.removePrefix("cmd "))
            next = continuation(messages) ?: fail("No continuation for $next")
            onChunk(messages)
        }
        return fail("Negotiation did not complete")
    }

    private fun assertNotBegun() {
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        assertEquals(0, gameplay.begins)
    }

    @Test
    fun `client receives complete map data before gameplay can begin`() {
        assertNull(command("configstrings ${game.spawncount} 0"))
        assertNull(command("baselines ${game.spawncount} 0"))
        command("begin ${game.spawncount}")
        assertNotBegun()
        val initial = request("new")
        assertEquals(ServerDataMessage(34, game.spawncount, false, "baseq2", 0, levelName),
            initial.filterIsInstance<ServerDataMessage>().single())
        assertNull(command("baselines ${game.spawncount} 0"))

        // A terminal cursor cannot claim unsent configstrings or baselines.
        assertTrue(request("configstrings ${game.spawncount} ${Defines.MAX_CONFIGSTRINGS}").isEmpty())
        assertTrue(request("baselines ${game.spawncount} ${Defines.MAX_EDICTS}").isEmpty())
        command("begin ${game.spawncount}")
        assertNotBegun()

        val receivedConfigs = mutableListOf<ConfigStringMessage>()
        val receivedModels = mutableListOf<Pair<Int, Int>>()
        var configChunks = 0
        var baselineChunks = 0
        val precache = completeNegotiation(continuation(initial)!!) { messages ->
            val configChunk = messages.filterIsInstance<ConfigStringMessage>()
            val baselineChunk = messages.filterIsInstance<SpawnBaselineMessage>()
            receivedConfigs += configChunk
            receivedModels += baselineChunk.map { it.entityState.index to it.entityState.modelIndex }
            val firstConfigChunk = configChunk.isNotEmpty() && ++configChunks == 1
            val firstBaselineChunk = baselineChunk.isNotEmpty() && ++baselineChunks == 1
            if (firstConfigChunk || firstBaselineChunk) {
                command("begin ${game.spawncount}")
                assertNotBegun()
            }
        }
        assertTrue(configChunks > 1 && baselineChunks > 1, "Fixture must span multiple chunks of each kind")
        assertEquals(expectedConfigstrings, receivedConfigs.sortedBy { it.index })
        assertEquals(expectedBaselines, receivedModels.sortedBy { it.first })
        assertEquals("precache ${game.spawncount}", precache)
        command("begin ${game.spawncount}")
        assertEquals(ClientStates.CS_SPAWNED, client.state)
        assertEquals(1, gameplay.begins)
    }

    @Test
    fun `malformed begin cannot consume a ready transition even when map count is zero`() {
        game.spawncount = 0
        completeNegotiation()
        for (text in listOf("begin", "begin 0.0", "begin 2147483648", "begin 0 extra")) {
            command(text)
            assertNotBegun()
        }
        command("begin 0")
        assertEquals(1, gameplay.begins)
    }

    @Test
    fun `released clients cannot begin with retained ready progress`() {
        completeNegotiation()
        for (state in listOf(ClientStates.CS_FREE, ClientStates.CS_ZOMBIE)) {
            client.state = state
            command("begin ${game.spawncount}")
            assertEquals(state, client.state)
            assertEquals(0, gameplay.begins)
        }
    }

    @Test
    fun `duplicate and reentrant begin and new cannot repeat gameplay initialization`() {
        completeNegotiation()
        gameplay.onBegin = { command("begin ${game.spawncount}") }
        host.handleClientMessages(client, List(3) { StringCmdMessage("begin ${game.spawncount}") })
        assertEquals(1, gameplay.begins)
        assertEquals(ClientStates.CS_SPAWNED, client.state)
        assertNull(command("new"))
        command("begin ${game.spawncount}")
        assertEquals(1, gameplay.begins)
    }

    @Test
    fun `covered chunks can be retried without losing completed negotiation`() {
        val first = command("new")!!.removePrefix("cmd ")
        val firstReply = request(first)
        assertEquals(firstReply, request(first))
        var firstBaselineReply: List<NetworkMessage>? = null
        completeNegotiation(continuation(firstReply)!!) { messages ->
            if (firstBaselineReply == null && messages.any { it is SpawnBaselineMessage }) {
                firstBaselineReply = messages
            }
        }
        assertEquals(firstReply, request(first))
        assertEquals(firstBaselineReply, request("baselines ${game.spawncount} 0"))
        assertEquals("cmd baselines ${game.spawncount} 0",
            command("configstrings ${game.spawncount} ${Defines.MAX_CONFIGSTRINGS}"))
        assertEquals("precache ${game.spawncount}", command("baselines ${game.spawncount} ${Defines.MAX_EDICTS}"))
        command("begin ${game.spawncount}")
        assertEquals(1, gameplay.begins)
        assertEquals(ClientStates.CS_SPAWNED, client.state)
    }

    @Test
    fun `new restarts a pending negotiation before begin`() {
        completeNegotiation()
        val next = command("new")!!
        command("begin ${game.spawncount}")
        assertNotBegun()
        completeNegotiation(next)
        command("begin ${game.spawncount}")
        assertEquals(1, gameplay.begins)
    }

    @Test
    fun `delayed prior-map begin cannot initialize the replacement game`() {
        completeNegotiation()
        val previousCount = game.spawncount
        val replacementGameplay = Gameplay()
        game = game(replacementGameplay, previousCount + 1)
        client.edict = replacementGameplay.entity

        // Neither retained old progress nor the old spawn count admits the new Game.
        command("begin ${game.spawncount}")
        assertEquals(0, replacementGameplay.begins)
        val next = command("begin $previousCount")!!
        assertTrue(next.startsWith("cmd configstrings ${game.spawncount} "))
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        assertEquals(0, replacementGameplay.begins)
        command("begin ${game.spawncount}")
        assertEquals(0, replacementGameplay.begins)
        completeNegotiation(next)
        command("begin ${game.spawncount}")
        assertEquals(1, replacementGameplay.begins)
        command("begin $previousCount")
        assertEquals(1, replacementGameplay.begins)
        assertEquals(0, gameplay.begins)
    }

    @Test
    fun `accepted reconnect in reused slot requires fresh negotiation even on the same map`() {
        completeNegotiation()
        admit()
        assertEquals(2, gameplay.connects)
        command("begin ${game.spawncount}")
        assertNotBegun()
        completeNegotiation()
        command("begin ${game.spawncount}")
        assertEquals(1, gameplay.begins)

        admit()
        command("begin ${game.spawncount}")
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        assertEquals(1, gameplay.begins)
        completeNegotiation()
        command("begin ${game.spawncount}")
        assertEquals(2, gameplay.begins)
    }

    @Test
    fun `map creation and cinematic invalidation share distinct host generations`() {
        val first = GameImportsImpl(host, ChangeMapInfo("first.pcx", false, false))
        val cinematic = GameImportsImpl(host, ChangeMapInfo("intro.cin+next.pcx", false, false)).apply {
            name = "default"
            state = ServerStates.SS_CINEMATIC
            gameExports = gameplay
        }
        host.games[cinematic.name] = cinematic
        assertEquals(first.spawncount + 1, cinematic.spawncount)
        val originalCount = cinematic.spawncount
        command("nextserver $originalCount")
        assertEquals(originalCount + 1, cinematic.spawncount)
        command("nextserver $originalCount")
        command("nextserver ${cinematic.spawncount}")
        assertEquals(originalCount + 1, cinematic.spawncount, "Repeated completion cannot consume another generation")
        assertNull(cinematic.takeNextMap())
        val replacement = GameImportsImpl(host, ChangeMapInfo("next.pcx", false, false))
        assertEquals(cinematic.spawncount + 1, replacement.spawncount)
    }
}
