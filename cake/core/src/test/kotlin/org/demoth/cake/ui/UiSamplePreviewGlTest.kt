package org.demoth.cake.ui

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import jake2.qcommon.exec.Cmd
import org.demoth.cake.ui.sample.UiSamplePreview
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class UiSamplePreviewGlTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `sample commands preserve modes and capture two rendered frames per image`() {
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try {
                    verifyPreview()
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    Gdx.app.exit()
                }
            }
        }, Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(640, 480)
        })
        failure?.let { throw it }
    }

    private fun verifyPreview() {
        val application = Gdx.app
        var exitRequested = false
        Gdx.app = object : Application by application {
            override fun exit() { exitRequested = true }
        }
        val skin = Skin(Gdx.files.internal("../../assets/ui/uiskin.json"))
        val style = EngineUiStyle(skin)
        val modes = mutableListOf<Boolean>()
        lateinit var preview: UiSamplePreview
        preview = UiSamplePreview(
            onOpen = { game -> modes += game; preview.open(game, 720f, skin, style) },
            onClose = { preview.close() },
        )
        try {
            preview.registerCommands()
            Cmd.ExecuteString("ui_sample engine")
            assertTrue(preview.isOpen)
            val first = preview.inputProcessor as Stage
            Cmd.ExecuteString("ui_sample game")
            assertSame(first, preview.inputProcessor)
            assertEquals(listOf(false, true), modes)
            preview.resize(800, 600)
            assertEquals(800, first.viewport.screenWidth)
            preview.setReferenceHeight(960f)
            assertEquals(960f, first.viewport.worldHeight)
            Cmd.ExecuteString("ui_sample off")
            assertFalse(preview.isOpen)
            assertNull(preview.inputProcessor)
            assertFalse(preview.close())
            Cmd.ExecuteString("ui_sample")
            assertTrue(modes.last(), "Reopening remembers game mode")
            assertNotSame(first, preview.inputProcessor)

            val captures = temp.resolve("captures")
            Cmd.ExecuteString("ui_sample_capture both \"$captures\"")
            assertFalse(modes.last())
            preview.render(1f / 60f)
            assertFalse(Files.exists(captures.resolve("engine.png")))
            preview.render(1f / 60f)
            assertTrue(Files.exists(captures.resolve("engine.png")))
            assertTrue(modes.last())
            assertFalse(exitRequested)
            preview.render(1f / 60f)
            assertFalse(Files.exists(captures.resolve("game.png")))
            preview.render(1f / 60f)
            assertTrue(exitRequested)
            for (mode in listOf("engine", "game")) {
                val image = Pixmap(Gdx.files.absolute(captures.resolve("$mode.png").toString()))
                try {
                    assertEquals(Gdx.graphics.backBufferWidth, image.width)
                    assertEquals(Gdx.graphics.backBufferHeight, image.height)
                } finally {
                    image.dispose()
                }
            }

            // Capture errors must escape rendering rather than report a successful exit.
            exitRequested = false
            val failed = temp.resolve("failed")
            Files.createDirectories(failed.resolve("engine.png"))
            Cmd.ExecuteString("ui_sample_capture engine \"$failed\"")
            preview.render(1f / 60f)
            assertThrows(RuntimeException::class.java) { preview.render(1f / 60f) }
            assertFalse(exitRequested)

            val graphics = Gdx.graphics
            Gdx.graphics = object : Graphics by graphics {
                override fun getBackBufferWidth() = 0
            }
            try {
                assertThrows(IllegalStateException::class.java) {
                    writeScreenshot(Gdx.files.absolute(temp.resolve("zero.png").toString()))
                }
            } finally {
                Gdx.graphics = graphics
            }
        } finally {
            preview.dispose()
            style.dispose()
            skin.dispose()
            Gdx.app = application
        }
    }
}
