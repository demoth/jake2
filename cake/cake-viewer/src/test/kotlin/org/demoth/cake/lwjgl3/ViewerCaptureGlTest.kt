package org.demoth.cake.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import org.demoth.cake.viewer.CakeViewer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class ViewerCaptureGlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `capture writes a complete PNG after two rendered frames and exits`() {
        val input = Files.writeString(temp.resolve("text preview.txt"), "Cake Viewer capture\nSecond line")
        val output = temp.resolve("capture output.png")
        val viewer = CakeViewer(arrayOf(input.toString()), output)
        var frames = 0
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try { viewer.create() } catch (error: Throwable) { failure = error; Gdx.app.exit() }
            }
            override fun render() {
                if (failure != null) return
                try {
                    frames++
                    viewer.render()
                    if (frames == 1) assertFalse(Files.exists(output), "Capture must wait for a second draw")
                    if (frames == 2) {
                        assertTrue(Files.exists(output), "PNG must be written before exit")
                        val stage = Gdx.input.inputProcessor as Stage
                        assertTrue(labels(stage.root).any { it == input.fileName.toString() },
                            "The supplied file must be open when captured")
                    }
                } catch (error: Throwable) { failure = error; Gdx.app.exit() }
            }
            override fun dispose() { viewer.dispose() }
        }, Lwjgl3ApplicationConfiguration().apply {
            setWindowedMode(640, 480)
            setInitialVisible(false)
            disableAudio(true)
        })
        failure?.let { throw it }
        assertEquals(2, frames)
        val image = ImageIO.read(output.toFile())
        assertEquals(640, image.width)
        assertEquals(480, image.height)
    }

    private fun labels(actor: Actor): List<String> =
        (if (actor is Label) listOf(actor.text.toString()) else emptyList()) +
            (if (actor is Group) actor.children.flatMap(::labels) else emptyList())
}
