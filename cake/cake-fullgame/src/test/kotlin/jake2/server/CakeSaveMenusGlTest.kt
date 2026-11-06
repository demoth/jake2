package jake2.server

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.game.PlayerClient
import jake2.qcommon.Defines
import org.demoth.cake.Cake
import org.demoth.cake.ClientNetworkState
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.HostState
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.testing.HiddenGlTest
import org.demoth.cake.ui.menu.MenuScreen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Drives real controls through runtime automation; domain assertions stay in Kotlin. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GAME_TESTS", matches = "1")
class CakeSaveMenusGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `save menus confirm overwrite recover from death and load failure and follow profile switches`() {
        val content = requireNotNull(System.getenv("CAKE_TEST_BASEDIR"))
        HiddenGlTest.run(home = temp) {
            val host = EmbeddedCakeHost(temp.resolve(".cake"))
            onCleanup(host::dispose)
            CakeStartupBootstrap.ensureInitialized()
            CakeGameProfileStore().upsertProfile(CakeGameProfile("first", content))
            val startup = CakeStartupBootstrap.bootstrap(arguments = listOf("+set", "developer", "1"))
            val cake = Cake(startup, host = host)
            onCleanup(cake::dispose)
            cake.create()
            renderWith(cake::render)

            suspend fun run(commands: String) = await(cake.automation.runText(commands))
            suspend fun open(screen: String) = run("automation_open $screen; automation_await screen $screen")
            suspend fun click(id: String) = run("automation_click $id")
            suspend fun gameReady() = run("automation_await game_ready 30")
            fun game() = host.server!!.games.getValue("default").gameExports as GameExportsImpl
            fun player() = game().getEdict(1) as GameEntity
            fun state() = cake.menuState.savedGames
            fun button(id: String) = cake.automationUi.element(id) as TextButton
            fun preview() = cake.automationUi.element("save-preview-image") as Image
            lateinit var slot: String
            fun checkpoint() = temp.resolve(".cake/first/save/$slot/game.ssv.json")
            fun blocker() = temp.resolve(".cake/first/save/current/game.ssv.json")
            fun verifyPreview() {
                val image = checkpoint().parent.resolve("preview.png")
                assertTrue(Files.isRegularFile(image))
                val bytes = Files.readAllBytes(image)
                val pixels = Pixmap(bytes, 0, bytes.size)
                try {
                    assertEquals(320, pixels.width)
                    assertEquals(240, pixels.height)
                } finally { pixels.dispose() }
                assertNotNull(preview().drawable)
                assertFalse(Files.exists(temp.resolve(".cake/first/save/current/preview.png")))
                assertFalse(Files.exists(temp.resolve(".cake/first/save/autosave/preview.png")))
            }

            open("load_game")
            assertEquals(MenuScreen.LOAD_GAME, cake.menuState.activeScreen)
            assertTrue(state().entries.isEmpty())
            assertTrue(button("load-game").isDisabled)
            assertTrue(cake.isMenuVisible)
            cake.automationUi.stage.keyUp(Input.Keys.ESCAPE)
            run("map base1; automation_await game_ready 30")

            player().health = 73
            open("save_game")
            assertEquals(MenuScreen.SAVE_GAME, cake.menuState.activeScreen)
            click("save-new")
            assertEquals("Game saved.", state().statusMessage)
            slot = state().selectedSlot!!
            verifyPreview()
            val originalSave = Files.readString(checkpoint())
            player().health = 41

            click("save-overwrite")
            assertEquals(slot, state().pendingOverwriteSlot)
            click("cancel-save-confirmation")
            assertNull(state().pendingOverwriteSlot)
            assertEquals(originalSave, Files.readString(checkpoint()))
            click("save-overwrite")
            click("confirm-overwrite")
            assertEquals("Game saved.", state().statusMessage)
            assertNotEquals(originalSave, Files.readString(checkpoint()))
            verifyPreview()

            player().health = 5
            open("load_game")
            click("load-game")
            gameReady()
            assertEquals(41, player().health)
            assertFalse(cake.isMenuVisible)
            player().health = 0
            player().getClient().playerState.stats[Defines.STAT_HEALTH] = 0
            PlayerClient.respawn(player(), game())
            await(cake.automation.await("death opens paused Load Game") {
                cake.presentedMenuScreen == MenuScreen.LOAD_GAME && host.server!!.isPaused
            })
            assertEquals(MenuScreen.LOAD_GAME, cake.menuState.activeScreen)
            assertTrue(cake.isMenuVisible)
            assertTrue(host.server!!.isPaused)
            val frozenFrame = game().level.framenum
            open("save_game")
            assertEquals(frozenFrame, game().level.framenum)
            assertTrue(button("save-new").isDisabled)
            assertTrue(cake.menuState.saveAccess.saveRejectionReason!!.contains("dead"))

            open("load_game")
            // Valid source, but failure while replacing current must leave feedback visible.
            Files.delete(blocker())
            Files.createDirectory(blocker())
            Files.writeString(blocker().resolve("blocker"), "force copy failure")
            click("load-game")
            await(cake.automation.await("load failure feedback") { state().statusMessage.startsWith("Load failed:") })
            assertTrue(state().statusMessage.startsWith("Load failed:"))
            assertEquals(HostState.STOPPED, host.state)
            assertEquals(ClientNetworkState.DISCONNECTED, cake.connectionState)
            assertTrue(cake.isMenuVisible)
            assertFalse(cake.isConsoleVisible)
            Files.delete(blocker().resolve("blocker"))
            Files.delete(blocker())
            click("load-game")
            gameReady()
            assertEquals(41, player().health)

            run("disconnect; cake_profile_set second \"$content\"")
            open("load_game")
            assertEquals("second", state().profileId)
            assertNull(preview().drawable)
            assertTrue(state().entries.isEmpty())
            assertTrue(button("load-game").isDisabled)
            run("cake_profile_set first \"$content\"")
            open("load_game")
            assertEquals("first", state().profileId)
            assertTrue(state().entries.any { it.slot == slot })
            verifyPreview()
            click("load-game")
            gameReady()
            assertEquals(41, player().health)

            open("save_game")
            click("save-new")
            assertNotEquals(slot, state().selectedSlot)
            click("delete-save")
            assertNotNull(state().pendingDeleteSlot)
            click("cancel-save-confirmation")
            assertNull(state().pendingDeleteSlot)
            assertEquals(2, state().entries.count { !it.autosave })
            click("delete-save")
            click("confirm-delete")
            assertEquals("Saved game deleted.", state().statusMessage)
            assertEquals(listOf(slot), state().entries.filter { !it.autosave }.map { it.slot })
            assertTrue(Files.exists(checkpoint()))
            verifyPreview()
            run("automation_capture build/reports/save-menu-layout.png presentation")
        }
    }
}
