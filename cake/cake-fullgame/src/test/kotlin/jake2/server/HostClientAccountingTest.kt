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
    fun `failed disconnect callback still releases connection and is not repeated`() {
        val client = bind(0, first)
        val peer = bind(1, first)
        client.state = ClientStates.CS_SPAWNED
        client.download = byteArrayOf(1)
        client.downloadsize = 1
        client.downloadcount = 1
        client.unreliable.add(jake2.qcommon.network.messages.server.StuffTextMessage("stale"))
        val failure = IllegalStateException("broken callback")
        var callbacks = 0
        first.gameExports = object : GameExports by first.gameExports {
            override fun ClientDisconnect(ent: ServerEntity) {
                callbacks++
                throw failure
            }
        }
        assertSame(failure, assertThrows(IllegalStateException::class.java) { host.dropClient(client) })
        assertEquals(ClientStates.CS_ZOMBIE, client.state)
        assertNull(client.download)
        assertEquals(0, client.downloadsize)
        assertEquals(0, client.downloadcount)
        assertNull(client.edict)
        assertTrue(client.unreliable.isEmpty())
        assertEquals(first.name, client.gameName, "Zombie routing remains until disconnect is delivered")
        assertEquals(ClientStates.CS_CONNECTED, peer.state)
        host.dropClient(client)
        assertEquals(1, callbacks)
    }

    @Test
    fun `reliable overflow disconnects only offending connection and peers still receive messages`() {
        val client = bind(0, first)
        val peer = bind(1, first)
        client.netchan.reliablePending.add(
            jake2.qcommon.network.messages.server.StuffTextMessage("x".repeat(Defines.MAX_MSGLEN)))
        peer.netchan.reliablePending.add(jake2.qcommon.network.messages.server.StuffTextMessage("peer"))
        val previousSequence = peer.netchan.outgoing_sequence
        host.sendClientMessages()
        assertEquals(ClientStates.CS_ZOMBIE, client.state)
        assertEquals(ClientStates.CS_CONNECTED, peer.state)
        assertTrue(peer.netchan.outgoing_sequence > previousSequence)
        assertSame(first, host.games[first.name])
        assertSame(second, host.games[second.name])
    }

    @Test
    fun `rejected client packet cannot execute trailing commands`() {
        val client = bind(0, first)
        val peer = bind(1, first)
        val invalid = object : jake2.qcommon.network.messages.client.ClientMessage(
            jake2.qcommon.network.messages.client.ClientMessageType.CLC_BAD) {
            override fun writeProperties(buffer: sizebuf_t) = Unit
            override fun parse(buffer: sizebuf_t) = Unit
            override fun getSize() = 1
        }
        host.handleClientMessages(client, listOf(invalid,
            jake2.qcommon.network.messages.client.UserInfoMessage("\\name\\must_not_apply")))
        assertEquals(ClientStates.CS_ZOMBIE, client.state)
        assertEquals("", client.userinfo)
        assertEquals(ClientStates.CS_CONNECTED, peer.state)
        assertSame(first, host.games[first.name])
    }

    @Test
    fun `console kick escalates gameplay failure to its Game and continues command buffer`() {
        val client = bind(0, first)
        val other = bind(1, second)
        client.state = ClientStates.CS_SPAWNED
        first.gameExports = object : GameExports by first.gameExports {
            override fun ClientDisconnect(ent: ServerEntity) { first.error("disconnect failed") }
        }
        var continued = false
        jake2.qcommon.exec.Cmd.AddCommand("test_after_kick") { continued = true }
        try {
            jake2.qcommon.exec.Cbuf.AddText("kick 0; test_after_kick")
            jake2.qcommon.exec.Cbuf.Execute()
            assertTrue(continued)
            assertFalse(host.games.containsKey(first.name))
            assertEquals(ClientStates.CS_FREE, client.state)
            assertSame(second, host.games[second.name])
            assertEquals(ClientStates.CS_CONNECTED, other.state)
        } finally {
            jake2.qcommon.exec.Cmd.RemoveCommand("test_after_kick")
        }
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
    fun `challenge eviction follows issuance order despite global clock reversal`() {
        val previousTime = Globals.curtime
        val loopback = java.net.InetAddress.getByName("127.0.0.1")
        java.net.DatagramSocket(0, loopback).use { receiver ->
            receiver.soTimeout = 2000
            fun reply(): String {
                val packet = java.net.DatagramPacket(ByteArray(2048), 2048)
                receiver.receive(packet)
                return String(packet.data, 4, packet.length - 4).trim()
            }
            val address = netadr_t().apply {
                type = NetAddrType.NA_IP
                ip = loopback.address
                port = receiver.localPort
            }
            cvars.find<Int>("port").forceSet(0)
            try {
                assertTrue(NET.configureServer(true))
                Globals.curtime = 1000
                host.handleConnectionlessPacket(address, "getchallenge")
                val issued = reply()
                val challenge = issued.substringAfterLast(' ')
                NET.configureServer(false)
                // No UDP socket while filling synthetic remote addresses: no external packets.
                Globals.curtime = -1000
                repeat(Defines.MAX_CHALLENGES - 1) { index ->
                    val remote = netadr_t().apply {
                        type = NetAddrType.NA_IP
                        ip = byteArrayOf(192.toByte(), 0, (index / 256).toByte(), (index % 256).toByte())
                    }
                    host.handleConnectionlessPacket(remote, "getchallenge")
                }
                assertTrue(NET.configureServer(true))
                host.handleConnectionlessPacket(address, "getchallenge")
                assertEquals(issued, reply(), "Repeated requests retain the existing challenge")
                NET.configureServer(false)
                host.handleConnectionlessPacket(netadr_t().apply {
                    type = NetAddrType.NA_IP
                    ip = byteArrayOf(198.toByte(), 51, 100, 1)
                }, "getchallenge")
                assertTrue(NET.configureServer(true))
                host.handleConnectionlessPacket(address,
                    "connect ${Defines.PROTOCOL_VERSION} 7 $challenge \\name\\tester")
                assertTrue(reply().contains("No challenge for address"))
            } finally {
                Globals.curtime = previousTime
                NET.configureServer(false)
            }
        }
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
