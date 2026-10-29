package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.qcommon.Defines
import jake2.qcommon.network.demo.DemoReader
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.protocol.MessageBodyCodec
import jake2.qcommon.network.protocol.MessageDecodeResult
import jake2.qcommon.sizebuf_t
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import jake2.qcommon.exec.Cmd
import jake2.qcommon.network.ConnectionChannel
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.*
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.stages.ingame.Game3dScreen
import org.demoth.cake.stages.ingame.ClientEntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** External protocol-34 recording and installed assets remain outside git. Every application window is hidden. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GAME_TESTS", matches = "1")
abstract class CakeDemoPlaybackGlTest {
    protected abstract val embedded: Boolean
    @TempDir lateinit var temp: Path

    @Test
    @EnabledIfEnvironmentVariable(named = "CAKE_TEST_DEMO", matches = ".+")
    fun `console playback works with remote-only and embedded host without simulation`() {
        val basedir = Path.of(requireNotNull(System.getenv("CAKE_TEST_BASEDIR")))
        val demo = Path.of(requireNotNull(System.getenv("CAKE_TEST_DEMO")))
        val content = temp.resolve("content/baseq2")
        Files.createDirectories(content.resolve("demos"))
        val source = basedir.resolve("baseq2")
        Files.walk(source).use { entries ->
            entries.filter { !source.relativize(it).startsWith("demos") }.forEach {
                val target = content.resolve(source.relativize(it))
                if (Files.isDirectory(it)) Files.createDirectories(target)
                else Files.createSymbolicLink(target, it)
            }
        }
        val bytes = Files.readAllBytes(demo)
        Files.write(content.resolve("demos/check.dm2"), bytes)
        Files.write(content.resolve("demos/motion.dm2"), motionDemo(bytes))
        Files.write(content.resolve("demos/missing-assets.dm2"), missingAssetsDemo())
        Files.write(content.resolve("demos/truncated.dm2"), bytes.copyOf(bytes.size - 5))
        val unsupported = bytes.copyOf().apply { this[5] = 26 } // first record begins with svc_serverdata + protocol.
        Files.write(content.resolve("demos/unsupported.dm2"), unsupported)
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        var failure: Throwable? = null
        val delegate: CakeHost = if (embedded) EmbeddedCakeHost(temp.resolve(".cake")) else UnavailableCakeHost()
        var hostUpdates = 0
        val host = object : CakeHost by delegate {
            override fun update(elapsedMillis: Long) { hostUpdates++; delegate.update(elapsedMillis) }
        }
        try {
            CakeStartupBootstrap.ensureInitialized()
            CakeGameProfileStore().upsertProfile(CakeGameProfile("demo-test", content.parent.toString()))
            val startup = CakeStartupBootstrap.bootstrap()
            Lwjgl3Application(object : ApplicationAdapter() {
                private lateinit var cake: Cake
                private var stage = 0
                private var ticks = 0
                private var initialFrame = 0
                private var frameAdvanced = false
                private var deadline = System.nanoTime() + 30_000_000_000L
                private var updatesAtOpen = 0
                private var snapshotAtPause = 0
                private var timeAtPause = 0

                override fun create() {
                    try {
                        cake = Cake(startup, host = host)
                        cake.create()
                        updatesAtOpen = hostUpdates
                        Cmd.ExecuteString("demomap check")
                    } catch (error: Throwable) { fail(error) }
                }

                override fun render() {
                    if (failure != null) return
                    try {
                        cake.render()
                        ticks++
                        assertTrue(System.nanoTime() < deadline, "Playback timeout at stage $stage")
                        if (field(cake, "demoPlayback") != null) {
                            assertEquals(updatesAtOpen, hostUpdates, "Playback must not tick a Host")
                            assertEquals(HostState.STOPPED, host.state)
                            assertEquals(1, (field(cake, "netchan") as ConnectionChannel).outgoingSequence)
                            (delegate as? EmbeddedCakeHost)?.let {
                                assertTrue(it.server!!.games.isEmpty())
                                assertNull(it.udpEndpoint(Defines.NS_CLIENT))
                                assertNull(it.udpEndpoint(Defines.NS_SERVER))
                            }
                        }
                        val screen = field(cake, "game3dScreen") as? Game3dScreen
                        val entities = screen?.let { field(it, "entityManager") as ClientEntityManager }
                        val frame = entities?.currentFrame
                        when (stage) {
                            0 -> if (frame?.valid == true && state() == "ACTIVE") {
                                assertTrue(screen.demoPlayback)
                                assertTrue(screen.canRenderPresentationFrame())
                                initialFrame = frame.serverframe
                                val camera = field(screen, "camera") as com.badlogic.gdx.graphics.PerspectiveCamera
                                assertEquals(frame.playerstate.pmove.origin[0] * .125f + frame.playerstate.viewoffset[0], camera.position.x, .01f)
                                assertEquals(frame.playerstate.pmove.origin[1] * .125f + frame.playerstate.viewoffset[1], camera.position.y, .01f)
                                Cmd.ExecuteString("demopause")
                                snapshotAtPause = frame.serverframe
                                timeAtPause = field(screen, "demoTimeMillis") as Int
                                next()
                            }
                            1 -> {
                                assertEquals(snapshotAtPause, frame!!.serverframe)
                                assertEquals(timeAtPause, field(screen, "demoTimeMillis"))
                                if (ticks > 10) { Cmd.ExecuteString("demopause"); next() }
                            }
                            2 -> {
                                if (frame != null) {
                                    frameAdvanced = frameAdvanced || frame.serverframe > initialFrame
                                }
                                if (state() == "DISCONNECTED") {
                                    assertTrue(frameAdvanced, "Recorded frames must advance")
                                    assertTrue(initialFrame > 0)
                                    assertEquals(true, field(cake, "consoleVisible"))
                                    Cmd.ExecuteString("demomap motion")
                                    next()
                                }
                            }
                            3 -> if (state() == "ACTIVE" && ticks > 20) {
                                val current = frame!!.playerstate
                                val previous = entities.previousFrame!!.playerstate
                                val lerp = (entities.lerpAcc * 10).coerceIn(0f, 1f)
                                val camera = field(screen, "camera") as com.badlogic.gdx.graphics.PerspectiveCamera
                                val expectedX = (previous.pmove.origin[0] + lerp * (current.pmove.origin[0] - previous.pmove.origin[0])) * .125f
                                assertEquals(expectedX, camera.position.x, .01f, "Camera must follow recorded movement despite prediction flags allowing local prediction")
                                val yaw = (previous.viewangles[1] + lerp * (current.viewangles[1] - previous.viewangles[1])) * kotlin.math.PI / 180
                                assertEquals(kotlin.math.cos(yaw).toFloat(), camera.direction.x, .001f)
                                assertEquals(kotlin.math.sin(yaw).toFloat(), camera.direction.y, .001f)
                                Cmd.ExecuteString("demomap check") // replace an active file session.
                                next()
                            }
                            4 -> if (state() == "ACTIVE") {
                                Cmd.ExecuteString("demostop")
                                assertEquals("DISCONNECTED", state())
                                assertNull(field(cake, "game3dScreen"))
                                Cmd.ExecuteString("demomap unsupported")
                                next()
                            }
                            5 -> if (state() == "DISCONNECTED") {
                                assertEquals(true, field(cake, "consoleVisible"))
                                Cmd.ExecuteString("demomap truncated")
                                next()
                            }
                            6 -> if (state() == "DISCONNECTED") {
                                assertNull(field(cake, "demoPlayback"))
                                assertEquals(true, field(cake, "consoleVisible"))
                                Cmd.ExecuteString("demomap missing-assets")
                                next()
                            }
                            7 -> if (state() == "DISCONNECTED") {
                                assertNull(field(cake, "game3dScreen"))
                                assertEquals(true, field(cake, "consoleVisible"))
                                Cmd.ExecuteString("demomap missing")
                                assertEquals("DISCONNECTED", state())
                                Gdx.app.exit()
                            }
                        }
                    } catch (error: Throwable) { fail(error) }
                }

                private fun state() = field(cake, "networkState").toString()
                private fun next() { stage++; ticks = 0; deadline = System.nanoTime() + 30_000_000_000L }
                private fun fail(error: Throwable) { failure = error; Gdx.app.exit() }
                override fun dispose() {
                    try { if (::cake.isInitialized) cake.dispose() } catch (error: Throwable) { failure = failure ?: error }
                }
            }, Lwjgl3ApplicationConfiguration().apply {
                setInitialVisible(false)
                disableAudio(true)
                setWindowedMode(640, 480)
                setForegroundFPS(60)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
                setPauseWhenLostFocus(false)
                setPauseWhenMinimized(false)
            })
            failure?.let { throw it }
        } finally {
            host.dispose()
            System.setProperty("user.home", previousHome)
        }
    }

    /** Controlled view deltas supplement the untouched Yamagi interoperability recording. */
    private fun motionDemo(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var frame = 0
        DemoReader(ByteArrayInputStream(bytes)).use { reader ->
            while (true) {
                val record = reader.read()
                if (record == DemoReader.Record.End) break
                val messages = (MessageBodyCodec.decodeServerBody((record as DemoReader.Record.Body).bytes) as MessageDecodeResult.Decoded).messages
                val buffer = sizebuf_t(1400)
                messages.forEach { message ->
                    val modified = if (message is PlayerInfoMessage) {
                        frame++
                        message.copy(origin = PlayerDeltaVector(1000 + frame * 8, 0, 0),
                            movementFlags = 0, viewOffset = PlayerDeltaVector(0, 0, 0),
                            viewAngles = PlayerDeltaVector(0, frame * 1000, 0), kickAngles = PlayerDeltaVector(0, 0, 0))
                    } else message
                    modified.writeTo(buffer)
                }
                output.write(int(buffer.cursize))
                output.write(buffer.data, 0, buffer.cursize)
            }
        }
        output.write(int(-1))
        return output.toByteArray()
    }

    private fun missingAssetsDemo(): ByteArray {
        val buffer = sizebuf_t(1400)
        listOf(ServerDataMessage(34, 1, true, "baseq2", 0, "Missing content"),
            ConfigStringMessage(Defines.CS_MODELS + 1, "maps/cake-nonexistent-demo-map.bsp"),
            StuffTextMessage("precache\n")).forEach { it.writeTo(buffer) }
        return int(buffer.cursize) + buffer.data.copyOf(buffer.cursize) + int(-1)
    }

    private fun int(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply {
        isAccessible = true
    }.get(owner)
}

class CakeRemoteDemoPlaybackGlTest : CakeDemoPlaybackGlTest() {
    override val embedded = false
}

class CakeHostedDemoPlaybackGlTest : CakeDemoPlaybackGlTest() {
    override val embedded = true
}
