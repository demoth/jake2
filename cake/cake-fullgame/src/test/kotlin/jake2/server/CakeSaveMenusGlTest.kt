package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.game.PlayerClient
import jake2.qcommon.Defines
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.HostState
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.stages.MenuStages
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuScreen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Clicks real menu widgets and follows the real singleplayer respawn command through Cbuf. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GAME_TESTS", matches = "1")
class CakeSaveMenusGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `save menus confirm overwrite recover from death and load failure and follow profile switches`() {
        val content = requireNotNull(System.getenv("CAKE_TEST_BASEDIR"))
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        val host = EmbeddedCakeHost(temp.resolve(".cake"))
        var failure: Throwable? = null
        try {
            CakeStartupBootstrap.ensureInitialized()
            CakeGameProfileStore().upsertProfile(CakeGameProfile("first", content))
            val startup = CakeStartupBootstrap.bootstrap()
            Lwjgl3Application(object : ApplicationAdapter() {
                private lateinit var cake: Cake
                private lateinit var bus: MenuEventBus
                private var step = 0
                private var ticks = 0
                private var frozenFrame = 0
                private lateinit var slot: String
                private lateinit var originalSave: String
                private val deadline = System.nanoTime() + 90_000_000_000L

                override fun create() {
                    try {
                        cake = Cake(startup, host = host)
                        cake.create()
                        bus = field("menuEventBus") as MenuEventBus
                        Cmd.ExecuteString("menu_loadgame")
                    } catch (error: Throwable) { fail(error) }
                }

                private fun field(name: String): Any? = Cake::class.java.getDeclaredField(name).apply {
                    isAccessible = true
                }.get(cake)
                private fun game() = host.server!!.games.getValue("default").gameExports as GameExportsImpl
                private fun player() = game().getEdict(1) as GameEntity
                private fun active() = field("networkState").toString() == "ACTIVE" && ticks > 3
                private fun state() = bus.latestState().savedGames
                private fun stage() = (field("menuStages") as MenuStages).inputProcessor as Stage
                private fun button(name: String): TextButton = requireNotNull(stage().root.findActor(name))
                private fun click(name: String) {
                    val button = button(name)
                    assertFalse(button.isDisabled, "Button $name disabled at step $step")
                    button.listeners.filterIsInstance<ClickListener>().forEach { it.clicked(InputEvent(), 0f, 0f) }
                }
                private fun next() { step++; ticks = 0 }
                private fun checkpoint() = temp.resolve(".cake/first/save/$slot/game.ssv.json")
                private fun blocker() = temp.resolve(".cake/first/save/current/game.ssv.json")
                private fun fail(error: Throwable) { failure = error; Gdx.app.exit() }

                override fun render() {
                    if (failure != null) return
                    try {
                        assertTrue(System.nanoTime() < deadline, "Timed out at step $step")
                        cake.render()
                        ticks++
                        when (step) {
                            0 -> {
                                assertEquals(MenuScreen.LOAD_GAME, bus.latestState().activeScreen)
                                assertTrue(state().entries.isEmpty())
                                assertTrue(button("load-game").isDisabled)
                                assertEquals(true, field("menuVisible"))
                                stage().keyUp(Input.Keys.ESCAPE)
                                Cbuf.AddText("map base1")
                                next()
                            }
                            1 -> if (active()) {
                                player().health = 73
                                Cmd.ExecuteString("menu_savegame")
                                next()
                            }
                            2 -> {
                                assertEquals(MenuScreen.SAVE_GAME, bus.latestState().activeScreen)
                                click("save-new")
                                next()
                            }
                            3 -> {
                                assertEquals("Game saved.", state().statusMessage)
                                slot = state().selectedSlot!!
                                originalSave = Files.readString(checkpoint())
                                player().health = 41
                                click("save-overwrite")
                                next()
                            }
                            4 -> {
                                assertEquals(slot, state().pendingOverwriteSlot)
                                click("cancel-overwrite")
                                next()
                            }
                            5 -> {
                                assertNull(state().pendingOverwriteSlot)
                                assertEquals(originalSave, Files.readString(checkpoint()))
                                click("save-overwrite")
                                next()
                            }
                            6 -> { click("confirm-overwrite"); next() }
                            7 -> {
                                assertEquals("Game saved.", state().statusMessage)
                                assertNotEquals(originalSave, Files.readString(checkpoint()))
                                player().health = 5
                                Cmd.ExecuteString("menu_loadgame")
                                next()
                            }
                            8 -> { click("load-game"); next() }
                            9 -> if (active()) {
                                assertEquals(41, player().health)
                                assertEquals(false, field("menuVisible"))
                                player().health = 0
                                player().getClient().playerState.stats[Defines.STAT_HEALTH] = 0
                                PlayerClient.respawn(player(), game())
                                next()
                            }
                            10 -> if (ticks > 2) {
                                assertEquals(MenuScreen.LOAD_GAME, bus.latestState().activeScreen)
                                assertEquals(true, field("menuVisible"))
                                assertTrue(host.server!!.isPaused)
                                frozenFrame = game().level.framenum
                                Cmd.ExecuteString("menu_savegame")
                                next()
                            }
                            11 -> {
                                assertEquals(frozenFrame, game().level.framenum)
                                assertTrue(button("save-new").isDisabled)
                                assertTrue(bus.latestState().saveAccess.saveRejectionReason!!.contains("dead"))
                                Cmd.ExecuteString("menu_loadgame")
                                next()
                            }
                            12 -> {
                                // Valid source, but failure while replacing current must leave feedback visible.
                                Files.delete(blocker())
                                Files.createDirectory(blocker())
                                Files.writeString(blocker().resolve("blocker"), "force copy failure")
                                click("load-game")
                                next()
                            }
                            13 -> if (ticks > 2) {
                                assertTrue(state().statusMessage.startsWith("Load failed:"))
                                assertEquals(HostState.STOPPED, host.state)
                                assertEquals("DISCONNECTED", field("networkState").toString())
                                assertEquals(true, field("menuVisible"))
                                assertEquals(false, field("consoleVisible"))
                                Files.delete(blocker().resolve("blocker"))
                                Files.delete(blocker())
                                click("load-game")
                                next()
                            }
                            14 -> if (active()) {
                                assertEquals(41, player().health)
                                Cmd.ExecuteString("disconnect")
                                Cmd.ExecuteString("cake_profile_set second \"$content\"")
                                Cmd.ExecuteString("menu_loadgame")
                                next()
                            }
                            15 -> {
                                assertEquals("second", state().profileId)
                                assertTrue(state().entries.isEmpty())
                                assertTrue(button("load-game").isDisabled)
                                Cmd.ExecuteString("cake_profile_set first \"$content\"")
                                Cmd.ExecuteString("menu_loadgame")
                                next()
                            }
                            16 -> {
                                assertEquals("first", state().profileId)
                                assertTrue(state().entries.any { it.slot == slot })
                                click("load-game")
                                next()
                            }
                            17 -> if (active()) {
                                assertEquals(41, player().health)
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
