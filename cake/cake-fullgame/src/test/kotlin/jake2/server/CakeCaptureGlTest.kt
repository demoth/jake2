package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.automation.CaptureLayer
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.ui.menu.MenuScreen
import org.demoth.cake.ui.sample.UiSkinSample
import org.demoth.cake.ui.writeScreenshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeCaptureGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `capture crosses deferred menu transitions and selects composition boundaries`() {
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.toString())
        var failure: Throwable? = null
        try {
            CakeStartupBootstrap.ensureInitialized()
            CakeGameProfileStore().upsertProfile(CakeGameProfile("capture", temp.toString()))
            val startup = CakeStartupBootstrap.bootstrap(arguments = listOf("+set", "developer", "1"))
            Lwjgl3Application(object : ApplicationAdapter() {
                private var cake: Cake? = null
                override fun create() {
                    try {
                        val app = Cake(startup).also { cake = it; it.create() }
                        fun file(name: String) = Gdx.files.absolute(temp.resolve(name).toString())
                        fun request(name: String, layer: CaptureLayer = CaptureLayer.PRESENTATION) =
                            app.automationCaptures.request(file(name), layer)
                        app.render()
                        lateinit var deferred: CompletableFuture<FileHandle>
                        Cmd.AddCommand("automation_test_capture") { deferred = request("gallery.png") }
                        Cbuf.AddText("ui_sample engine\nautomation_test_capture\n")
                        app.render()
                        assertFalse(app.automationUi.stage is UiSkinSample)
                        assertFalse(deferred.isDone)
                        assertFalse(file("gallery.png").exists())
                        app.render()
                        assertInstanceOf(UiSkinSample::class.java, app.automationUi.stage)
                        assertTrue(deferred.isDone)
                        assertFalse(deferred.isCompletedExceptionally)
                        writeScreenshot(file("drawn-gallery.png"))
                        assertArrayEquals(file("drawn-gallery.png").readBytes(), file("gallery.png").readBytes(),
                            "Deferred capture must contain the newly drawn gallery")

                        val image = Pixmap(file("gallery.png"))
                        try {
                            assertEquals(Gdx.graphics.backBufferWidth, image.width)
                            assertEquals(Gdx.graphics.backBufferHeight, image.height)
                        } finally { image.dispose() }

                        app.automationUi.open(MenuScreen.OPTIONS)
                        val options = request("options.png")
                        app.render()
                        assertEquals(MenuScreen.OPTIONS, app.presentedMenuScreen)
                        assertTrue(options.isDone)
                        assertFalse(options.isCompletedExceptionally)
                        assertFalse(file("options.png").readBytes().contentEquals(file("gallery.png").readBytes()))

                        Cmd.ExecuteString("ui_sample engine")
                        app.render()
                        app.keyUp(Input.Keys.GRAVE)
                        assertTrue(app.isConsoleVisible)
                        val presentation = request("without-console.png")
                        val final = request("with-console.png", CaptureLayer.FINAL)
                        app.render()
                        assertTrue(presentation.isDone && final.isDone)
                        assertArrayEquals(file("gallery.png").readBytes(), file("without-console.png").readBytes())
                        assertFalse(file("with-console.png").readBytes().contentEquals(file("without-console.png").readBytes()))

                        app.keyUp(Input.Keys.GRAVE)
                        Cmd.ExecuteString("set net_debug_ping_ms 1")
                        val beforeDiagnostics = request("without-diagnostics.png")
                        val withDiagnostics = request("with-diagnostics.png", CaptureLayer.FINAL)
                        app.render()
                        assertTrue(beforeDiagnostics.isDone && withDiagnostics.isDone)
                        assertArrayEquals(file("gallery.png").readBytes(), file("without-diagnostics.png").readBytes())
                        assertFalse(file("with-diagnostics.png").readBytes().contentEquals(file("without-diagnostics.png").readBytes()))
                        Cmd.ExecuteString("set net_debug_ping_ms 0")

                        file("failure.png").mkdirs()
                        val failed = request("failure.png")
                        assertThrows(RuntimeException::class.java) { app.render() }
                        assertTrue(failed.isCompletedExceptionally)

                        val pending = request("disposed.png")
                        app.dispose()
                        cake = null
                        assertTrue(pending.isCancelled)
                        assertFalse(file("disposed.png").exists())
                    } catch (error: Throwable) { failure = error }
                    finally { Gdx.app.exit() }
                }

                override fun dispose() {
                    Cmd.RemoveCommand("automation_test_capture")
                    try { cake?.dispose() } catch (error: Throwable) { failure = failure ?: error }
                }
            }, Lwjgl3ApplicationConfiguration().apply {
                setInitialVisible(false)
                disableAudio(true)
                setWindowedMode(640, 480)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
            })
            failure?.let { throw it }
        } finally {
            System.setProperty("user.home", previousHome)
        }
    }
}
