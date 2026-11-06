package org.demoth.cake.automation

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.ScreenUtils
import jake2.qcommon.Globals
import jake2.qcommon.MainCommon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class AutomationCapturesGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `captures wait for eligible drawing and preserve failures and cancellation`() {
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try { verifyCaptures() } catch (error: Throwable) { failure = error }
                finally { Gdx.app.exit() }
            }
        }, Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(640, 480)
        })
        failure?.let { throw it }
    }

    private fun verifyCaptures() {
        MainCommon.registerCvars(false)
        val developer = Globals.developer.typedValue
        val application = Gdx.app
        var exitRequested = false
        Gdx.app = object : Application by application {
            override fun exit() { exitRequested = true }
        }
        val captures = AutomationCaptures()
        fun file(name: String) = Gdx.files.absolute(temp.resolve(name).toString())
        fun assertImage(name: String, color: Color) {
            val image = Pixmap(file(name))
            try {
                assertEquals(Gdx.graphics.backBufferWidth, image.width)
                assertEquals(Gdx.graphics.backBufferHeight, image.height)
                assertEquals(Color.rgba8888(color), image.getPixel(image.width / 2, image.height / 2))
            } finally { image.dispose() }
        }
        try {
            Globals.developer.forceSet(false)
            assertThrows(IllegalStateException::class.java) { captures.request(file("disabled.png")) }
            Globals.developer.forceSet(true)
            val presentation = captures.request(file("presentation.png"))
            val final = captures.request(file("final.png"), CaptureLayer.FINAL)
            captures.afterDraw(CaptureLayer.PRESENTATION)
            assertFalse(presentation.isDone, "A request must first cross the frame boundary")
            assertFalse(file("presentation.png").exists())

            captures.beginFrame()
            ScreenUtils.clear(Color.RED)
            captures.afterDraw(CaptureLayer.PRESENTATION)
            assertTrue(presentation.isDone)
            assertEquals(file("presentation.png"), presentation.join())
            assertImage("presentation.png", Color.RED)
            assertFalse(final.isDone)
            ScreenUtils.clear(Color.GREEN)
            captures.afterDraw(CaptureLayer.FINAL)
            assertTrue(final.isDone)
            assertEquals(file("final.png"), final.join())
            assertImage("final.png", Color.GREEN)

            // Commands run during rendering too. Their captures must wait for next frame's updates.
            val deferred = captures.request(file("next-frame.png"))
            captures.afterDraw(CaptureLayer.PRESENTATION)
            assertFalse(deferred.isDone)
            captures.beginFrame()
            ScreenUtils.clear(Color.BLUE)
            captures.afterDraw(CaptureLayer.PRESENTATION)
            assertTrue(deferred.isDone)
            assertImage("next-frame.png", Color.BLUE)

            val zeroSized = captures.request(file("restored.png"))
            val graphics = Gdx.graphics
            Gdx.graphics = object : Graphics by graphics {
                override fun getBackBufferWidth() = 0
            }
            try {
                repeat(2) {
                    captures.beginFrame()
                    captures.afterDraw(CaptureLayer.PRESENTATION)
                }
                assertFalse(zeroSized.isDone)
                assertFalse(file("restored.png").exists())
            } finally { Gdx.graphics = graphics }
            captures.beginFrame()
            captures.afterDraw(CaptureLayer.PRESENTATION)
            assertTrue(zeroSized.isDone)

            val cancelled = captures.request(file("cancelled.png"))
            cancelled.cancel(false)
            captures.beginFrame()
            captures.afterDraw(CaptureLayer.PRESENTATION)
            assertFalse(file("cancelled.png").exists())
            val disabled = captures.request(file("disabled-mid-frame.png"))
            Globals.developer.forceSet(false)
            captures.afterDraw(CaptureLayer.PRESENTATION)
            assertTrue(disabled.isCancelled)
            assertThrows(CancellationException::class.java) { disabled.join() }
            assertFalse(file("disabled-mid-frame.png").exists())
            Globals.developer.forceSet(true)

            file("failure.png").mkdirs()
            val failed = captures.request(file("failure.png"))
            captures.beginFrame()
            val error = assertThrows(RuntimeException::class.java) { captures.afterDraw(CaptureLayer.PRESENTATION) }
            assertTrue(failed.isCompletedExceptionally)
            assertSame(error, assertThrows(CompletionException::class.java) { failed.join() }.cause)
            // A failed write must not be retried by another render hook.
            captures.afterDraw(CaptureLayer.PRESENTATION)

            val shutdown = captures.request(file("shutdown.png"))
            captures.cancelAll("Test host disposed")
            assertTrue(shutdown.isCancelled)
            captures.beginFrame()
            captures.afterDraw(CaptureLayer.PRESENTATION)
            assertFalse(file("shutdown.png").exists())
            assertFalse(exitRequested, "Capture completion, cancellation and failure do not own app exit")
        } finally {
            captures.cancelAll("Test finished")
            Globals.developer.forceSet(developer)
            Gdx.app = application
        }
    }
}
