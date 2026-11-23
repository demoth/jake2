package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.Globals
import jake2.qcommon.GameExports
import jake2.qcommon.MainCommon
import jake2.qcommon.ServerEntity
import jake2.qcommon.ServerPlayerInfo
import jake2.qcommon.ServerStates
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.network.NetworkSettings
import jake2.qcommon.network.messages.NetworkMessage
import jake2.qcommon.network.messages.client.StringCmdMessage
import jake2.qcommon.network.messages.server.DownloadMessage
import jake2.qcommon.network.messages.server.PrintMessage
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.network.protocol.PacketCodec
import jake2.qcommon.network.runtime.NetworkTransport
import jake2.qcommon.network.transport.LoopbackLink
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import jake2.qcommon.vfs.EngineVfs
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.qcommon.vfs.VfsConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.lang.reflect.Proxy
import kotlin.random.Random

class ServerRequestValidationTest {
    @TempDir lateinit var root: Path
    private lateinit var previousRoot: Path
    private var previousState: ServerStates? = null
    private lateinit var transport: NetworkTransport
    private lateinit var host: ServerHost
    private lateinit var game: GameImportsImpl
    private lateinit var requester: ClientNetworkInfo
    private lateinit var healthy: ClientNetworkInfo
    private var sequence = 0
    private var simulationTicks = 0
    private val file = Random(0).nextBytes(2500)
    private val entity = object : ServerEntity(1) {
        override fun getOwner(): ServerEntity? = null
        override fun getClient(): ServerPlayerInfo? = null
    }

    @BeforeEach
    fun setup() {
        previousRoot = EngineWriteRoot.path()
        previousState = Globals.server_state
        Globals.server_state = ServerStates.SS_DEAD
        EngineWriteRoot.setRoot(root)
        Cvar.getInstance().clear()
        MainCommon.registerCvars(false)
        NetworkSettings.registerCvars()
        Cvar.getInstance().register("gamedir", CvarTypes.STRING, "", Defines.CVAR_SERVERINFO or Defines.CVAR_NOSET)
        val path = root.resolve("baseq2/models/test.bin")
        Files.createDirectories(path.parent)
        Files.write(path, file)
        EngineVfs.configure(VfsConfig(root, "baseq2", "", true, false, false, emptyList(), emptySet()))
        val link = LoopbackLink(64)
        transport = NetworkTransport(link.first, link.second.localAddress)
        val network = TransportServerNetwork(NetworkTransport(link.second, link.first.localAddress))
        host = ServerHost(network)
        game = GameImportsImpl(host, ChangeMapInfo("end.pcx", false, false)).apply {
            state = ServerStates.SS_PIC
            gameExports = Proxy.newProxyInstance(GameExports::class.java.classLoader, arrayOf(GameExports::class.java)) {
                    _, method, _ ->
                when (method.name) {
                    "G_RunFrame" -> { simulationTicks++; null }
                    "getEdict" -> entity
                    "getNumEdicts" -> 0
                    else -> error("Unexpected Game callback: ${method.name}")
                }
            } as GameExports
        }
        host.games[game.name] = game
        fun connection(qport: Int) = ClientNetworkInfo(network.newChannel()).apply {
            state = ClientStates.CS_CONNECTED
            gameName = game.name
            netchan.setup(netchan.remoteAddress, qport)
        }
        requester = connection(7)
        healthy = connection(8)
        host.clients.addAll(listOf(requester, healthy))
    }

    @AfterEach
    fun cleanup() {
        host.close()
        transport.close()
        Cvar.getInstance().clear()
        EngineWriteRoot.setRoot(previousRoot)
        Globals.server_state = previousState
    }

    private fun request(command: String, client: ClientNetworkInfo = requester): List<NetworkMessage> {
        client.netchan.clearQueuedReliable()
        val payload = sizebuf_t(2048).apply { StringCmdMessage(command).writeTo(this) }
        val packet = PacketCodec.encodeSequenced(++sequence, 0, client.netchan.qport,
            PacketBytes.copyOf(payload.data, payload.cursize))
        transport.send(transport.localPeer, packet)
        host.readPackets()
        return client.netchan.queuedReliable
    }

    private fun assertNextTickContinues() {
        assertTrue(request("new", healthy).any { it is ServerDataMessage })
        host.update(1000)
        assertEquals(1, simulationTicks)
        assertSame(game, host.games[game.name])
        assertEquals(ClientStates.CS_CONNECTED, healthy.state)
        assertEquals(ClientStates.CS_CONNECTED, requester.state)
    }

    @Test
    fun `malformed handshake requests are refused before changing handshake state`() {
        // Permissive integer conversion used to turn malformed input into a valid zero.
        game.spawncount = 0
        for ((command, limit) in listOf("configstrings" to Defines.MAX_CONFIGSTRINGS, "baselines" to Defines.MAX_EDICTS)) {
            val invalid = listOf(
                "$command ${game.spawncount} -1",
                "$command ${game.spawncount} ${limit + 1}",
                "$command ${game.spawncount} 2147483648",
                "$command ${game.spawncount} 1junk",
                "$command bad 0",
                "$command ${game.spawncount}",
                "$command ${game.spawncount} 0 extra",
                // Bad cursors must be refused even when a stale level would normally restart negotiation.
                "$command ${game.spawncount + 1} -1",
            )
            for (input in invalid) {
                assertTrue(request(input).single() is PrintMessage, input)
                assertEquals(ClientStates.CS_CONNECTED, requester.state, input)
                assertTrue(healthy.netchan.queuedReliable.isEmpty(), input)
            }
        }
        assertNextTickContinues()
    }

    @Test
    fun `invalid downloads preserve the current transfer and other clients next tick`() {
        val firstChunk = request("download models/test.bin").single() as DownloadMessage.Chunk
        assertTrue(firstChunk.percentage in 1..99, "Fixture must leave an active transfer")
        val invalid = listOf(
            "download",
            "download \"\"",
            "download models/test.bin -1",
            "download models/test.bin 2147483648",
            "download models/test.bin 1junk",
            "download models/test.bin ${file.size + 1}",
            "download models/test.bin 0 extra",
            "download models/missing.bin",
        )
        for (command in invalid) {
            assertEquals(DownloadMessage.Failure(), request(command).single(), command)
            assertTrue(healthy.netchan.queuedReliable.isEmpty(), command)
        }
        assertArrayEquals(file, receiveDownload(firstChunk, offset = 0))
        assertNextTickContinues()
    }

    @Test
    fun `downloads retain normal resume and end of file behavior`() {
        for (offset in listOf(0, 1200, file.size)) {
            val firstChunk = request("download models/test.bin $offset").single() as DownloadMessage.Chunk
            assertArrayEquals(file.copyOfRange(offset, file.size), receiveDownload(firstChunk, offset))
        }
    }

    private fun receiveDownload(firstChunk: DownloadMessage.Chunk, offset: Int): ByteArray {
        val contents = ByteArrayOutputStream()
        var chunk = firstChunk
        while (true) {
            contents.write(chunk.data.toByteArray())
            val position = offset + contents.size()
            assertTrue(position <= file.size, "Transfer exceeded the end of the file")
            assertEquals(position * 100 / file.size, chunk.percentage)
            if (chunk.percentage == 100) {
                assertTrue(request("nextdl").isEmpty(), "Completed transfer should send no more chunks")
                return contents.toByteArray()
            }
            assertTrue(chunk.data.size > 0, "An incomplete transfer must make progress")
            chunk = request("nextdl").single() as DownloadMessage.Chunk
        }
    }
}
