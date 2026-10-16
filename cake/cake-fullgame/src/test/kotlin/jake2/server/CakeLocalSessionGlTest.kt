package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.qcommon.Defines
import jake2.game.GameExportsImpl
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.NET
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.HostState
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

/** Requires installed Quake 2 data via CAKE_TEST_BASEDIR; never writes to that installation. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GAME_TESTS", matches = "1")
class CakeLocalSessionGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `local gameplay transitions multiplayer peers and remote joining`() {
        val basedir = requireNotNull(System.getenv("CAKE_TEST_BASEDIR"))
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        val host = EmbeddedCakeHost(temp.resolve(".cake"))
        var failure: Throwable? = null
        val peers = mutableListOf<Process>()
        try {
            CakeStartupBootstrap.ensureInitialized()
            CakeGameProfileStore().upsertProfile(CakeGameProfile("local-test", basedir))
            val startup = CakeStartupBootstrap.bootstrap()
            Lwjgl3Application(object : ApplicationAdapter() {
                private lateinit var cake: Cake
                private var step = 0
                private var ticks = 0
                private var stageStart = System.nanoTime()
                private var origin = floatArrayOf()
                private var serverSocket: java.net.DatagramSocket? = null
                private lateinit var clientPeer: Process
                private lateinit var serverPeer: Process
                private val clientReady = temp.resolve("client-ready")
                private val serverReady = temp.resolve("server-ready")

                override fun create() {
                    try {
                        cake = Cake(startup, host = host)
                        cake.create()
                        Cbuf.AddText("host_start base1")
                    } catch (error: Throwable) { fail(error) }
                }

                private fun fail(error: Throwable) {
                    failure = error
                    Gdx.app.exit()
                }

                private fun next(command: String? = null) {
                    step++
                    ticks = 0
                    stageStart = System.nanoTime()
                    command?.let(Cbuf::AddText)
                }

                override fun render() {
                    if (failure != null) return
                    try {
                        cake.render()
                        assertTrue(System.nanoTime() - stageStart < 30_000_000_000L,
                            "Timed out at step $step, client=${state(cake)}, host=${host.state}")
                        ticks++
                        when (step) {
                            0 -> if (state(cake) == "ACTIVE") {
                                assertTrue(NET.ip_sockets.all { it == null })
                                origin = playerOrigin()
                                next("+forward")
                            }
                            1 -> if (ticks >= 45) {
                                Cmd.ExecuteString("-forward")
                                val moved = playerOrigin()
                                assertTrue(kotlin.math.abs(moved[0] - origin[0]) + kotlin.math.abs(moved[1] - origin[1]) > 1f,
                                    "Local movement must reach the server")
                                next("gamemap base2")
                            }
                            2 -> if (ticks > 2 && state(cake) == "ACTIVE" && mapName() == "base2") {
                                assertEquals(HostState.RUNNING, host.state)
                                assertTrue(NET.ip_sockets.all { it == null })
                                next("map base1")
                            }
                            3 -> if (ticks > 2 && state(cake) == "ACTIVE" && mapName() == "base1") {
                                next("reconnect")
                            }
                            4 -> if (ticks > 2 && state(cake) == "ACTIVE") {
                                next("host_stop")
                            }
                            5 -> {
                                assertEquals(HostState.STOPPED, host.state)
                                assertEquals("DISCONNECTED", state(cake))
                                next("host_start base1")
                            }
                            6 -> if (state(cake) == "ACTIVE") next("killserver")
                            7 -> {
                                assertEquals(HostState.STOPPED, host.state)
                                assertEquals("DISCONNECTED", state(cake))
                                assertTrue(NET.ip_channels.all { it == null })
                                Cvar.getInstance().find<Int>("port").forceSet(0)
                                next("host_start base1 coop")
                            }
                            8 -> if (state(cake) == "ACTIVE") {
                                serverSocket = NET.ip_sockets[Defines.NS_SERVER]!!
                                assertNull(NET.ip_sockets[Defines.NS_CLIENT])
                                clientPeer = launchPeer("client", basedir, clientReady, serverSocket!!.localPort)
                                peers.add(clientPeer)
                                next()
                            }
                            9 -> {
                                assertTrue(clientPeer.isAlive, "Thin-client peer exited; see ${temp.resolve("client.log")}")
                                if (Files.exists(clientReady) && spawnedClients() == 2) next("gamemap base2")
                            }
                            10 -> if (ticks > 2 && state(cake) == "ACTIVE" && mapName() == "base2" && spawnedClients() == 2) {
                                assertSame(serverSocket, NET.ip_sockets[Defines.NS_SERVER])
                                clientPeer.destroy()
                                next("host_start base1 deathmatch")
                            }
                            11 -> if (state(cake) == "ACTIVE") {
                                Files.deleteIfExists(clientReady)
                                clientPeer = launchPeer("client", basedir, clientReady,
                                    NET.ip_sockets[Defines.NS_SERVER]!!.localPort)
                                peers.add(clientPeer)
                                next()
                            }
                            12 -> {
                                assertTrue(clientPeer.isAlive)
                                if (Files.exists(clientReady) && spawnedClients() == 2) {
                                    clientPeer.destroy()
                                    serverPeer = launchPeer("server", basedir, serverReady)
                                    peers.add(serverPeer)
                                    next()
                                }
                            }
                            13 -> {
                                assertTrue(serverPeer.isAlive, "Remote server exited; see ${temp.resolve("server.log")}")
                                if (Files.exists(serverReady)) next("connect 127.0.0.1:${Files.readString(serverReady)}")
                            }
                            14 -> if (state(cake) == "ACTIVE") {
                                assertEquals(HostState.STOPPED, host.state)
                                assertNull(NET.ip_sockets[Defines.NS_SERVER])
                                assertNotNull(NET.ip_sockets[Defines.NS_CLIENT])
                                next("disconnect")
                            }
                            15 -> {
                                assertEquals("DISCONNECTED", state(cake))
                                assertTrue(NET.ip_channels.all { it == null })
                                next("reconnect")
                            }
                            16 -> if (state(cake) == "ACTIVE") next("disconnect")
                            17 -> {
                                assertEquals("DISCONNECTED", state(cake))
                                assertTrue(NET.ip_channels.all { it == null })
                                Gdx.app.exit()
                            }
                        }
                    } catch (error: Throwable) { fail(error) }
                }

                override fun dispose() {
                    try { if (::cake.isInitialized) cake.dispose() }
                    catch (error: Throwable) { failure = failure ?: error }
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
            peers.forEach { peer ->
                peer.destroy()
                if (!peer.waitFor(5, TimeUnit.SECONDS)) peer.destroyForcibly()
            }
            host.stop("test cleanup")
            System.setProperty("user.home", previousHome)
        }
    }

    private fun state(cake: Cake) = Cake::class.java.getDeclaredField("networkState").apply {
        isAccessible = true
    }.get(cake).toString()

    private fun mapName() = Cvar.getInstance().find<String>("mapname").typedValue
    private fun playerOrigin() = (SV_MAIN.games.getValue("default").gameExports as GameExportsImpl).getEdict(1).s.origin.copyOf()
    private fun spawnedClients() = SV_MAIN.games.getValue("default").serverMain.clients.count {
        it.state == ClientStates.CS_SPAWNED
    }

    private fun launchPeer(mode: String, basedir: String, ready: Path, port: Int = 0): Process {
        val urls = generateSequence(javaClass.classLoader) { it.parent }
            .filterIsInstance<URLClassLoader>().flatMap { it.urLs.asSequence() }
            .map { Path.of(it.toURI()).toString() }.distinct().toList()
        val classpath = (urls + System.getProperty("java.class.path").split(java.io.File.pathSeparator))
            .distinct().joinToString(java.io.File.pathSeparator)
        return ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--enable-native-access=ALL-UNNAMED", "-Duser.home=${temp.resolve(mode)}",
            "-cp", classpath, CakeSessionPeer::class.java.name, mode, basedir, ready.toString(), port.toString(),
        ).redirectErrorStream(true).redirectOutput(temp.resolve("$mode.log").toFile()).start()
    }
}
