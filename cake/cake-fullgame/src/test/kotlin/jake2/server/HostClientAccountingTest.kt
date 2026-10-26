package jake2.server

import jake2.game.GameEntity
import jake2.game.GamePlayerInfo
import jake2.qcommon.*
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.*
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.vfs.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HostClientAccountingTest {
    @TempDir lateinit var root: Path
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private lateinit var host: ServerHost
    private lateinit var first: GameImportsImpl
    private lateinit var second: GameImportsImpl
    private val cvars = Cvar.getInstance()

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        Globals.server_state = ServerStates.SS_DEAD
        cvars.clear()
        MainCommon.registerCvars(false)
        Netchan.Netchan_Init()
        drainPackets()
        EngineVfs.configure(VfsConfig(Path.of("src/test/resources"), "baseq2", null,
            true, false, false, emptyList(), setOf("pak")))
        EngineWriteRoot.setRoot(root)
        host = ServerHost()
        assertTrue(host.startMap("testbox"))
        host.games.clear()
        cvars.find<Int>("maxclients").forceSet(2)
        host.resetClients()
        first = createGame("default")
        second = createGame("second")
        first.frameNum = 5
        second.frameNum = 100
        host.update(100)
    }

    private fun createGame(name: String): GameImportsImpl {
        val game = host.createGameInstance(ChangeMapInfo("testbox", false, false), null)
        game.name = name
        host.games[name] = game
        return game
    }

    private fun bind(index: Int, game: GameImportsImpl): ClientNetworkInfo = host.clients[index].apply {
        gameName = game.name
        edict = game.gameExports.getEdict(index + 1)
        (edict as GameEntity).setClient(GamePlayerInfo(index))
        edict.getClient().playerState.pmove.origin[2] = (64 * 8).toShort()
        lastmessage = 100
        lastconnect = 100
        rate = 100000
        state = ClientStates.CS_CONNECTED
        netchan.setup(Defines.NS_SERVER, netadr_t(), index)
    }

    @AfterEach
    fun cleanup() {
        host.clients.forEach { it.state = ClientStates.CS_FREE }
        host.close()
        drainPackets()
        EngineWriteRoot.setRoot(previousRoot)
        Globals.server_state = previousState
        cvars.clear()
    }

    private fun drainPackets() {
        for (side in listOf(Defines.NS_CLIENT, Defines.NS_SERVER)) {
            while (NET.receiveNetworkPacket(null, null, NET.loopbacks[side], side == Defines.NS_SERVER) != null) { }
        }
    }

    @Test
    fun `timeout survives Game clock reset and expires at Host boundary`() {
        val client = bind(0, first)
        host.timeout.forceSet(0.5f)
        first.realtime = -10000
        host.update(500)
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        assertEquals(100L, client.lastmessage)
        // Simulate transfer to a newly allocated Game with a different clock.
        client.gameName = second.name
        second.realtime = 0
        host.update(1)
        assertEquals(ClientStates.CS_FREE, client.state)
    }

    @Test
    fun `targeted map change preserves connection timestamps`() {
        val client = bind(0, first)
        host.update(400)
        host.changeMap(listOf("gamemap", "testbox2", "1"))
        assertNotEquals(first.name, client.gameName)
        assertEquals("testbox2", host.games.getValue(client.gameName).mapName)
        assertSame(second, host.games[second.name])
        assertEquals(100L, client.lastmessage)
        assertEquals(100L, client.lastconnect)
        host.timeout.forceSet(0.5f)
        host.update(100)
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        host.update(1)
        assertEquals(ClientStates.CS_FREE, client.state)
    }

    @Test
    fun `dropping starts a full Host zombie grace period`() {
        val client = bind(0, first)
        cvars.find<Float>("zombietime").forceSet(0.5f)
        host.update(400)
        host.dropClient(client)
        assertEquals(500L, client.lastmessage)
        host.update(500)
        assertEquals(ClientStates.CS_ZOMBIE, client.state)
        first.realtime = -10000
        host.update(1)
        assertEquals(ClientStates.CS_FREE, client.state)
    }

    @Test
    fun `snapshots use Game identity but Host send time and rate bucket`() {
        val client = bind(0, first)
        client.state = ClientStates.CS_SPAWNED
        first.realtime = -9000
        host.sendClientMessages()
        val snapshot = first.frameNum
        assertEquals(100L, client.frames[snapshot and Defines.UPDATE_MASK].senttime)
        assertTrue(client.message_size[1] > 0)
        assertEquals(0, client.message_size[snapshot % Defines.RATE_MESSAGES])

        client.state = ClientStates.CS_CONNECTED
        host.update(100)
        first.realtime = 0
        val move = MoveMessage(false, snapshot, usercmd_t(), usercmd_t(), usercmd_t(), 0)
        host.handleClientMessages(client, listOf(move))
        client.state = ClientStates.CS_SPAWNED
        host.updateClientPings()
        assertEquals(100, client.ping)
    }

    @Test
    fun `rate history expires by Host frames and movement budget ignores Game age`() {
        val a = bind(0, first)
        val b = bind(1, second)
        a.netchan.remote_address.type = NetAddrType.NA_IP
        a.rate = 1
        a.message_size[1] = 100
        repeat(10) {
            host.update(100)
            host.shouldRateLimit(a)
        }
        assertTrue(a.message_size.all { it == 0 })
        assertFalse(host.shouldRateLimit(a))
        a.commandMsec = 10
        b.commandMsec = 20
        repeat(4) { host.update(100) }
        assertEquals(10, a.commandMsec)
        assertEquals(20, b.commandMsec)
        host.update(100) // Host frame 16; neither Game frame is a multiple of 16.
        assertEquals(1800, a.commandMsec)
        assertEquals(1800, b.commandMsec)
    }

    @Test
    fun `remote reconnect uses Host acceptance time across Game clock changes`() {
        // Loopback transport with a nonlocal port exercises remote admission without a UDP socket.
        val address = netadr_t().apply { port = 12345 }
        host.handleConnectionlessPacket(address, "getchallenge")
        val reply = NET.receiveNetworkPacket(null, null, NET.loopbacks[Defines.NS_CLIENT], false)!!.connectionlessMessage
        val challenge = reply.trim().substringAfterLast(' ')
        val connect = "connect ${Defines.PROTOCOL_VERSION} 7 $challenge \\name\\tester"
        first.realtime = -9000
        host.handleConnectionlessPacket(address, connect)
        val client = host.clients[0]
        assertEquals(ClientStates.CS_CONNECTED, client.state)
        assertEquals(100L, client.lastconnect)
        drainPackets()
        cvars.find<Float>("sv_reconnect_limit").forceSet(0.5f)
        host.update(499)
        first.realtime = 50000
        host.handleConnectionlessPacket(address, connect)
        assertEquals(100L, client.lastconnect, "Reconnect before the boundary must be rejected")
        host.update(1)
        host.handleConnectionlessPacket(address, connect)
        assertEquals(600L, client.lastconnect)
        assertEquals(600L, client.lastmessage)
    }
}
