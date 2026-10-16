package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.game.GameExportsImpl
import jake2.qcommon.Globals
import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.Netchan
import org.demoth.cake.Cake
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

/** Exercises the real application loop without displaying a window or attaching a client. */
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

                override fun create() {
                    try {
                        cake = Cake(startup, host = host).also { it.create() }
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
                                Cbuf.AddText("host_start testbox coop")
                                cake!!.render()
                                assertEquals(HostState.RUNNING, host.state)
                                frame = gameFrame()
                            }
                            1 -> {
                                val graphics = Gdx.graphics
                                Gdx.graphics = object : Graphics by graphics {
                                    override fun getBackBufferWidth() = 0
                                    override fun getBackBufferHeight() = 0
                                }
                                try { cake!!.render() } finally { Gdx.graphics = graphics }
                                assertEquals(frame + 1, gameFrame(), "Menus and an undrawable window must not stop coop")
                            }
                            2 -> {
                                Cbuf.AddText("connect 127.0.0.1:9")
                                cake!!.render()
                                assertEquals(HostState.STOPPED, host.state)
                            }
                            3 -> {
                                Cbuf.AddText("host_start missing")
                                cake!!.render()
                                assertEquals(HostState.STOPPED, host.state)
                            }
                            4 -> {
                                Cbuf.AddText("host_start testbox")
                                cake!!.render()
                                assertEquals(HostState.RUNNING, host.state)
                                frame = gameFrame()
                            }
                            5 -> {
                                cake!!.render()
                                assertEquals(frame, gameFrame(), "The main menu pauses singleplayer")
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
        } finally {
            host.stop("test cleanup")
            Globals.fixedtime.forceSet(0)
            System.setProperty("user.home", previousHome)
        }
    }

    private fun gameFrame() = (SV_MAIN.games.getValue("default").gameExports as GameExportsImpl).level.framenum
}
