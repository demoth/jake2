package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.game.GameExportsImpl
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.HostState
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.ui.menu.HostGameFormState
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.MenuScreen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Exercises content-defined New Game scripts through the rendered menu and real client handshake. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GAME_TESTS", matches = "1")
class CakeNewGameGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `menu reports alias errors and can retry a waiting campaign script`() {
        val installation = Path.of(requireNotNull(System.getenv("CAKE_TEST_BASEDIR")))
        val content = Files.createDirectories(temp.resolve("content"))
        Files.createSymbolicLink(content.resolve("baseq2"), installation.resolve("baseq2"))
        val scripts = mapOf(
            "missing" to "",
            "nomap" to "alias newgame \"killserver; wait\"",
            "badmap" to "alias newgame \"killserver; wait; gamemap *missing-map\"",
            "campaign" to "alias newgame \"killserver; wait; wait; gamemap *base1\"",
        )
        for ((mod, script) in scripts) {
            Files.writeString(Files.createDirectories(content.resolve(mod)).resolve("default.cfg"), script)
        }
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        val host = EmbeddedCakeHost()
        var failure: Throwable? = null
        try {
            CakeGameProfileStore().upsertProfile(CakeGameProfile("missing", content.toString(), "missing"))
            val startup = CakeStartupBootstrap.bootstrap()
            Lwjgl3Application(object : ApplicationAdapter() {
                private lateinit var cake: Cake
                private lateinit var bus: MenuEventBus
                private lateinit var manager: ServerHost
                private var step = 0
                private var ticks = 0
                private var frame = 0
                private val deadline = System.nanoTime() + 30_000_000_000L

                override fun create() {
                    try {
                        cake = Cake(startup, host = host)
                        cake.create()
                        bus = field("menuEventBus") as MenuEventBus
                        manager = host.server!!
                        startFromMenu()
                        cake.render()
                        assertTrue(bus.latestState().singleplayer.statusMessage.contains("does not define newgame"))
                        assertEquals(HostState.STOPPED, host.state)
                        assertEquals("DISCONNECTED", state())

                        for (mod in listOf("nomap", "badmap")) {
                            select(mod)
                            startFromMenu()
                            cake.render()
                            if (mod == "badmap") assertEquals(HostState.STARTING, host.state)
                            assertEquals("DISCONNECTED", state(), "wait must not attach a client prematurely")
                            cake.render() // Alias finishes without a running map; Cake handles HostFailureException.
                            cake.render() // The queued HostingFailed intent reaches the menu.
                            assertEquals(HostState.STOPPED, host.state)
                            assertEquals("DISCONNECTED", state())
                            assertEquals(MenuScreen.NEW_GAME, bus.latestState().activeScreen)
                            assertTrue(bus.latestState().singleplayer.statusMessage.contains("did not start a map"))
                            assertEquals(true, field("menuVisible"))
                            assertSame(manager, host.server)
                        }
                        select("campaign")
                        startFromMenu()
                        repeat(2) {
                            cake.render()
                            assertEquals(HostState.STARTING, host.state)
                            assertEquals("DISCONNECTED", state())
                        }
                    } catch (error: Throwable) { fail(error) }
                }

                private fun field(name: String): Any? = Cake::class.java.getDeclaredField(name).apply {
                    isAccessible = true
                }.get(cake)
                private fun state() = field("networkState").toString()
                private fun game() = manager.games.getValue("default").gameExports as GameExportsImpl
                private fun select(mod: String) = Cmd.ExecuteString("cake_profile_set $mod \"$content\" $mod")
                private fun startFromMenu() {
                    bus.postIntent(MenuIntent.OpenHostingSetup(true))
                    bus.postIntent(MenuIntent.StartHosting(true, HostGameFormState(skill = 2)))
                }
                private fun fail(error: Throwable) { failure = error; Gdx.app.exit() }

                override fun render() {
                    if (failure != null) return
                    try {
                        assertTrue(System.nanoTime() < deadline, "Timed out at step $step, client=${state()}")
                        cake.render()
                        when (step) {
                            0 -> if (state() == "ACTIVE") {
                                assertSame(manager, host.server)
                                assertEquals("base1", Cvar.getInstance().find<String>("mapname").typedValue)
                                assertEquals(2, game().gameCvars.skill.typedValue)
                                assertFalse(game().gameCvars.coop.typedValue)
                                assertFalse(game().gameCvars.deathmatch.typedValue)
                                assertEquals(1, manager.clients.size)
                                assertEquals(false, field("menuVisible"))
                                cake.keyUp(Input.Keys.ESCAPE)
                                frame = game().level.framenum
                                step++
                            }
                            1 -> {
                                assertEquals(frame, game().level.framenum, "Singleplayer menu pauses simulation")
                                if (++ticks == 3) {
                                    cake.keyUp(Input.Keys.ESCAPE)
                                    cake.pause()
                                    ticks = 0
                                    step++
                                }
                            }
                            2 -> {
                                assertEquals(frame, game().level.framenum, "Focus loss keeps singleplayer paused")
                                if (++ticks == 3) {
                                    cake.resume()
                                    step++
                                }
                            }
                            3 -> if (game().level.framenum > frame) {
                                assertEquals("ACTIVE", state())
                                Cmd.ExecuteString("disconnect")
                                assertEquals(HostState.STOPPED, host.state)
                                assertSame(manager, host.server)
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
            host.dispose()
            System.setProperty("user.home", previousHome)
        }
    }
}
