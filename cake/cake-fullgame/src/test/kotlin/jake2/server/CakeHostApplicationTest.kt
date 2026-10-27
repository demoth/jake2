package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.game.GameExportsImpl
import jake2.qcommon.Globals
import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.EngineWriteRoot
import jake2.qcommon.network.Netchan
import jake2.qcommon.Defines.NS_SERVER
import jake2.qcommon.network.netadr_t
import jake2.qcommon.network.messages.ConnectionlessCommand
import org.demoth.cake.Cake
import org.demoth.cake.ui.menu.MenuBackend
import org.demoth.cake.ui.menu.HostGameFormState
import org.demoth.cake.CakeCvars
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

/** Exercises application lifecycle and handshake boundaries without displaying a window. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeHostApplicationTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `application ticks while undrawable and stops on remote join and exit`() {
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        var failure: Throwable? = null
        val host = EmbeddedCakeHost(temp.resolve(".cake"))
        try {
            CakeStartupBootstrap.ensureInitialized()
            Cvar.getInstance().clear()
            MainCommon.registerCvars(false)
            CakeCvars.registerAll()
            Netchan.Netchan_Init()
            val cvars = Cvar.getInstance()
            cvars.find<Int>("port").forceSet(0)
            cvars.find<Int>("clientport").forceSet(0)
            Globals.fixedtime.forceSet(100)
            CakeGameProfileStore().upsertProfile(CakeGameProfile(
                "application-test", Path.of("src/test/resources").toAbsolutePath().toString(),
            ))
            val startup = CakeStartupBootstrap.bootstrap()
            Lwjgl3Application(object : ApplicationAdapter() {
                private var cake: Cake? = null
                private var step = 0
                private var frame = 0
                private lateinit var initialHost: ServerHost

                override fun create() {
                    try {
                        cake = Cake(startup, host = host).also { it.create() }
                        initialHost = host.server!!
                        assertEquals(HostState.STOPPED, host.state)
                        assertNotNull(Cmd.FindCommand("map"))
                        // Hosted storage rejection must precede persisted profile selection.
                        val originalRoot = EngineWriteRoot.path()
                        Files.writeString(temp.resolve(".cake/blocked"), "not a directory")
                        val content = Path.of("src/test/resources").toAbsolutePath()
                        Cvar.getInstance().find<String>("hostname").forceSet("keep on failure")
                        Cmd.ExecuteString("cake_profile_set blocked \"$content\"")
                        assertEquals("application-test", CakeGameProfileStore().readSelectedProfileId())
                        assertEquals(originalRoot, EngineWriteRoot.path())
                        assertEquals("keep on failure", Cvar.getInstance().find<String>("hostname").typedValue)
                    } catch (error: Throwable) {
                        failure = error
                        Gdx.app.exit()
                    }
                }

                override fun render() {
                    if (failure != null) return
                    try {
                        when (step++) {
                            0 -> {
                                val backend = Cake::class.java.getDeclaredMethod("createMenuBackend").apply {
                                    isAccessible = true
                                }.invoke(cake) as MenuBackend
                                assertEquals("Starting game...", backend.startHosting(false,
                                    HostGameFormState(map = "testbox", coop = true, maxClients = "4")))
                                assertEquals("DISCONNECTED", clientState(cake!!))
                                cake!!.render()
                                assertEquals(HostState.RUNNING, host.state)
                                frame = gameFrame(host)
                            }
                            1 -> {
                                val graphics = Gdx.graphics
                                Gdx.graphics = object : Graphics by graphics {
                                    override fun getBackBufferWidth() = 0
                                    override fun getBackBufferHeight() = 0
                                }
                                try { cake!!.render() } finally { Gdx.graphics = graphics }
                                assertEquals(frame + 1, gameFrame(host), "Menus and an undrawable window must not stop coop")
                            }
                            2 -> {
                                Cbuf.AddText("connect 127.0.0.1:9")
                                cake!!.render()
                                assertEquals(HostState.STOPPED, host.state)
                                assertSame(initialHost, host.server)
                            }
                            3 -> {
                                // An old local handshake must not attach while targeting a UDP server.
                                Netchan.sendConnectionlessPacket(NS_SERVER, netadr_t(), ConnectionlessCommand.client_connect, "")
                                cake!!.render()
                                assertEquals("CONNECTING", clientState(cake!!))
                                val current = temp.resolve(".cake/application-test/save/current/game.ssv.json")
                                val before = Files.readAllBytes(current)
                                Cbuf.AddText("map testbox2; gamemap *testbox2; load save0")
                                cake!!.render()
                                assertEquals("CONNECTING", clientState(cake!!))
                                assertEquals(HostState.STOPPED, host.state)
                                assertArrayEquals(before, Files.readAllBytes(current))
                                Cbuf.AddText("disconnect; map missing")
                                cake!!.render()
                                assertEquals(HostState.STOPPED, host.state)
                            }
                            4 -> {
                                // Nor may it resurrect a disconnected client after rejected startup.
                                Netchan.sendConnectionlessPacket(NS_SERVER, netadr_t(), ConnectionlessCommand.client_connect, "")
                                cake!!.render()
                                assertEquals("DISCONNECTED", clientState(cake!!))
                                Cbuf.AddText("set coop 0; set deathmatch 0; set maxclients 1; map testbox")
                                cake!!.render()
                                assertEquals(HostState.RUNNING, host.state)
                                assertSame(initialHost, host.server)
                                frame = gameFrame(host)
                            }
                            5 -> {
                                cake!!.keyUp(Input.Keys.GRAVE)
                                cake!!.render()
                                assertEquals(frame, gameFrame(host), "The console pauses singleplayer")
                                Gdx.app.exit()
                            }
                        }
                    } catch (error: Throwable) {
                        failure = error
                        Gdx.app.exit()
                    }
                }

                override fun dispose() {
                    try { cake?.dispose() } catch (error: Throwable) { failure = failure ?: error }
                }
            }, Lwjgl3ApplicationConfiguration().apply {
                setInitialVisible(false)
                disableAudio(true)
                setWindowedMode(320, 240)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
                setPauseWhenLostFocus(false)
                setPauseWhenMinimized(false)
            })
            failure?.let { throw it }
            assertEquals(HostState.STOPPED, host.state, "Application disposal stops the host")
            assertNull(host.server)
            assertNull(Cmd.FindCommand("map"))
        } finally {
            host.dispose()
            Globals.fixedtime.forceSet(0)
            System.setProperty("user.home", previousHome)
        }
    }

    private fun gameFrame(host: EmbeddedCakeHost) = (host.server!!.games.getValue("default").gameExports as GameExportsImpl).level.framenum
    private fun clientState(cake: Cake) = Cake::class.java.getDeclaredField("networkState").apply {
        isAccessible = true
    }.get(cake).toString()
}
