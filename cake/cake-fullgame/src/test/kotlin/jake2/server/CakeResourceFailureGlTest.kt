package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.GameExports
import jake2.qcommon.ServerEntity
import jake2.qcommon.exec.Cmd
import jake2.qcommon.filesystem.qfiles.Sp2Sprite
import jake2.qcommon.network.demo.DemoWriter
import jake2.qcommon.network.messages.server.ConfigStringMessage
import jake2.qcommon.network.messages.server.ServerDataMessage
import jake2.qcommon.network.messages.server.StuffTextMessage
import jake2.qcommon.network.protocol.PacketCodec
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.network.transport.SendResult
import jake2.qcommon.network.transport.UdpDatagramEndpoint
import jake2.qcommon.sizebuf_t
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.ClientNetworkState.DISCONNECTED
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.CakeHost
import org.demoth.cake.hosting.HostState
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.stages.console.ConsoleStage
import org.demoth.cake.testing.HiddenGlTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** Required content failures retire live precache or demo playback and leave Cake available. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeResourceFailureGlTest {
    @TempDir lateinit var root: Path

    @Test
    fun `malformed live and demo resources fail their sessions`() {
        val content = root.resolve("content/baseq2")
        Files.createDirectories(content.resolve("maps"))
        checkNotNull(javaClass.getResourceAsStream("/baseq2/maps/testbox.bsp")).use {
            Files.copy(it, content.resolve("maps/testbox.bsp"))
        }
        // Rendering does not read the collision brush lump.
        val brokenMap = Files.readAllBytes(content.resolve("maps/testbox.bsp"))
        ByteBuffer.wrap(brokenMap).order(ByteOrder.LITTLE_ENDIAN).putInt(8 + Defines.LUMP_BRUSHES * 8 + 4, 1)
        Files.write(content.resolve("maps/local-broken.bsp"), brokenMap)
        Files.write(content.resolve("maps/remote-broken.bsp"), brokenMap)
        Files.createDirectories(content.resolve("sprites"))
        Files.write(content.resolve("sprites/broken.sp2"), ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(Sp2Sprite.IDSPRITEHEADER)
            putInt(Sp2Sprite.SPRITE_VERSION + 1)
            putInt(1)
        }.array())
        Files.createDirectories(content.resolve("pics"))
        Files.write(content.resolve("pics/broken.pcx"), byteArrayOf(0))
        Files.createDirectories(content.resolve("demos"))
        val body = sizebuf_t(1400)
        ServerDataMessage(34, 1, true, "baseq2", 0, "Test world").writeTo(body)
        ConfigStringMessage(Defines.CS_MODELS + 1, "maps/testbox.bsp").writeTo(body)
        ConfigStringMessage(Defines.CS_IMAGES + 1, "broken").writeTo(body)
        StuffTextMessage("precache\n").writeTo(body)
        DemoWriter(Files.newOutputStream(content.resolve("demos/broken.dm2"))).use {
            it.write(PacketBytes.copyOf(body.data, body.cursize))
        }

        HiddenGlTest.run(home = root, timeoutSeconds = 20) {
            CakeGameProfileStore().upsertProfile(CakeGameProfile("load-failure", content.parent.toString()))
            val host = EmbeddedCakeHost(root.resolve(".cake"))
            val independentHost = object : CakeHost by host {
                // Allow a Host to be started independently of the remote client for this scenario.
                override fun initialize(startupRejection: () -> String?) = host.initialize { null }
            }
            val cake = Cake(CakeStartupBootstrap.bootstrap(), host = independentHost)
            cake.create()
            onCleanup(cake::dispose)
            val console = Cake::class.java.getDeclaredField("consoleStage").apply { isAccessible = true }.get(cake) as ConsoleStage
            suspend fun awaitMessage(message: String) {
                val found = CompletableFuture<Unit>()
                renderWith {
                    cake.render()
                    if (console.entries.any { message in it.text }) found.complete(Unit)
                }
                await(found)
            }

            Cmd.ExecuteString("map testbox")
            val game = checkNotNull(host.server?.games?.get("default"))
            game.configstring(Defines.CS_MODELS + 1 + game.cm.CM_NumInlineModels(), "sprites/broken.sp2")
            var begins = 0
            game.gameExports = object : GameExports by game.gameExports {
                override fun ClientBegin(ent: ServerEntity) { begins++ }
            }
            awaitMessage("Could not load resource 'sprites/broken.sp2': Unsupported SP2 version")
            assertEquals(0, begins)
            assertEquals(DISCONNECTED, cake.connectionState)
            assertEquals(HostState.STOPPED, host.state)
            assertNotNull(Cmd.FindCommand("map"))

            Cmd.ExecuteString("demomap broken")
            awaitMessage("Demo playback failed: Could not load resource 'pics/broken.pcx'")
            assertEquals(DISCONNECTED, cake.connectionState)
            assertNotNull(Cmd.FindCommand("map"))

            Cmd.ExecuteString("map testbox")
            val localGame = checkNotNull(host.server?.games?.get("default"))
            localGame.configstring(Defines.CS_MODELS + 1, "maps/local-broken.bsp")
            awaitMessage("Could not load resource 'maps/local-broken.bsp': Invalid brush lump size")
            assertEquals(DISCONNECTED, cake.connectionState)
            assertEquals(HostState.STOPPED, host.state)

            val remote = UdpDatagramEndpoint.bind(InetSocketAddress("127.0.0.1", 0))
            onCleanup(remote::close)
            Cmd.ExecuteString("connect ${remote.localAddress}")
            val server = checkNotNull(host.server)
            assertEquals(ServerHost.OperationResult.Completed, server.map("testbox"))
            val unrelatedGame = server.games.getValue("default")
            val clientAddress = checkNotNull(host.udpEndpoint(Defines.NS_CLIENT)).localAddress
            val destination = remote.localAddress.copy(port = clientAddress.port)
            val accept = PacketBytes.copyOf(byteArrayOf(-1, -1, -1, -1) + "client_connect".toByteArray())
            assertEquals(SendResult.Sent, remote.send(destination, accept))

            val payload = sizebuf_t(1400)
            ServerDataMessage(34, 1, false, "baseq2", 0, "Remote world").writeTo(payload)
            ConfigStringMessage(Defines.CS_MODELS + 1, "maps/remote-broken.bsp").writeTo(payload)
            for (index in 1 until unrelatedGame.cm.CM_NumInlineModels()) {
                ConfigStringMessage(Defines.CS_MODELS + 1 + index, "*$index").writeTo(payload)
            }
            StuffTextMessage("precache 1\n").writeTo(payload)
            val packet = PacketCodec.encodeSequenced(1, 0, null, PacketBytes.copyOf(payload.data, payload.cursize))
            assertEquals(SendResult.Sent, remote.send(destination, packet))
            awaitMessage("Could not load resource 'maps/remote-broken.bsp': Invalid brush lump size")
            assertEquals(DISCONNECTED, cake.connectionState)
            assertEquals(HostState.RUNNING, host.state)
            assertSame(unrelatedGame, server.games["default"])
        }
    }
}
