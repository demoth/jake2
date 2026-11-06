package jake2.server

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import jake2.qcommon.Globals
import jake2.qcommon.exec.Cmd
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.ui.menu.MenuScreen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeAutomationGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `runtime scripts capture themes drive assertions and respect lifecycle barriers`() {
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        var failure: Throwable? = null
        val script = temp.resolve("gallery script.cfg")
        Files.writeString(script, """
            automation_open ui_sample engine
            automation_await screen ui_sample
            automation_frames 2
            automation_capture engine.png
            runner_verify_capture engine
            automation_open ui_sample game
            automation_frames 2
            automation_capture game.png
            runner_verify_capture game
        """.trimIndent())
        try {
            CakeStartupBootstrap.ensureInitialized()
            CakeGameProfileStore().upsertProfile(CakeGameProfile("automation", temp.toString()))
            val startup = CakeStartupBootstrap.bootstrap(arguments = listOf(
                "+set", "developer", "1", "+set", "cfg_autosave", "0",
                "+automation_run", script.toString(), "exit"))
            Lwjgl3Application(object : ApplicationAdapter() {
                private var cake: Cake? = null
                override fun create() {
                    val application = Gdx.app
                    var exited = false
                    Gdx.app = object : Application by application {
                        override fun exit() { exited = true }
                    }
                    try {
                        val app = Cake(startup).also { cake = it; it.create() }
                        val captured = mutableListOf<String>()
                        Cmd.AddCommand("runner_verify_capture") { args ->
                            assertTrue(Files.isRegularFile(temp.resolve("${args[1]}.png")), "Capture is a write barrier")
                            captured += args[1]
                        }
                        fun finish(result: CompletableFuture<Unit>) {
                            repeat(30) { if (!result.isDone) app.render() }
                            assertTrue(result.isDone, "Sequence did not complete")
                            result.join()
                        }
                        assertNotNull(Cmd.FindCommand("automation_run"), "Commands exist before startup execution")
                        repeat(30) { if (!exited) app.render() }
                        assertTrue(exited, "Batch run must request exit after both images are written")
                        assertEquals(listOf("engine", "game"), captured)
                        assertTrue(app.automation.lastResult!!.isDone)
                        app.automation.lastResult!!.join()
                        assertFalse(Files.readAllBytes(temp.resolve("engine.png")).contentEquals(Files.readAllBytes(temp.resolve("game.png"))))

                        exited = false
                        val sample = temp.resolve("assets/automation/ui-sample.cfg")
                        Files.createDirectories(sample.parent)
                        Files.copy(Path.of("../../assets/automation/ui-sample.cfg"), sample)
                        finish(app.automation.runScript(sample))
                        assertTrue(Files.isRegularFile(temp.resolve("build/captures/ui-sample/engine.png")))
                        assertTrue(Files.isRegularFile(temp.resolve("build/captures/ui-sample/game.png")))
                        finish(app.automation.runText("automation_open options; automation_await screen options"))
                        assertEquals(MenuScreen.OPTIONS, app.presentedMenuScreen)
                        assertFalse(exited, "Screenshot-free interactive sequences leave the client running")
                        finish(app.automation.await("options presented") { app.presentedMenuScreen == MenuScreen.OPTIONS })

                        finish(app.automation.runText("""
                            automation_open ui_sample engine
                            automation_await element sample-action enabled
                            automation_click sample-action
                            automation_capture action.png
                        """.trimIndent(), directory = temp))
                        fun labels(group: Group): List<String> = group.children.flatMap {
                            when (it) {
                                is Label -> listOf(it.text.toString())
                                is Group -> labels(it)
                                else -> emptyList()
                            }
                        }
                        assertTrue(labels(app.automationUi.stage.root).contains("Clicks: 1"))
                        assertTrue(Files.isRegularFile(temp.resolve("action.png")))

                        val frames = app.automation.runText("automation_frames 1")
                        val graphics = Gdx.graphics
                        Gdx.graphics = object : Graphics by graphics {
                            override fun getBackBufferWidth() = 0
                        }
                        try { repeat(5) { app.render() }; assertFalse(frames.isDone) }
                        finally { Gdx.graphics = graphics }
                        finish(frames)

                        val disabled = app.automation.runText("set developer 0; runner_verify_capture unexpected")
                        app.render()
                        assertTrue(disabled.isCancelled)
                        assertNull(Cmd.FindCommand("automation_run"))
                        assertNull(Cmd.FindCommand("automation_click"))
                        assertEquals(listOf("engine", "game"), captured)
                        Globals.developer.forceSet(true)
                        app.render()
                        assertNotNull(Cmd.FindCommand("automation_run"))

                        Files.createDirectory(temp.resolve("failure.png"))
                        val failed = app.automation.runText("automation_capture failure.png\nrunner_verify_capture unexpected", directory = temp)
                        assertThrows(RuntimeException::class.java) { app.render() }
                        assertTrue(failed.isCompletedExceptionally)
                        assertEquals(listOf("engine", "game"), captured)
                        assertFalse(exited)
                    } catch (error: Throwable) { failure = error }
                    finally { Gdx.app = application; Gdx.app.exit() }
                }

                override fun dispose() {
                    Cmd.RemoveCommand("runner_verify_capture")
                    try { cake?.dispose() } catch (error: Throwable) { failure = failure ?: error }
                }
            }, Lwjgl3ApplicationConfiguration().apply {
                setInitialVisible(false)
                disableAudio(true)
                setWindowedMode(640, 480)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
            })
            failure?.let { throw it }
        } finally { System.setProperty("user.home", previousHome) }
    }
}
