package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import org.demoth.cake.viewer.CakeViewer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/** Uses the fullgame test resources for the viewer's bundled skin and shaders. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeViewerMd2GlTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `loose and archive models share pane controls and release them on replacement`() {
        val fixture = listOf(Path.of("../core/src/test/resources/_missing.md2"),
            Path.of("cake/core/src/test/resources/_missing.md2")).first(Files::exists)
        temp = Files.createDirectories(temp.resolve("content"))
        val model = Files.readAllBytes(fixture)
        // Keep the existing geometry/animation fixture, with two self-contained skin dependencies.
        for ((index, name) in listOf("a.pcx", "b.pcx").withIndex()) {
            model.fill(0, 68 + index * 64, 68 + (index + 1) * 64)
            name.toByteArray().copyInto(model, 68 + index * 64)
        }
        val entries = linkedMapOf("model.md2" to model, "a.pcx" to pcx(0xff0000), "b.pcx" to pcx(0x00ff00))
        entries.forEach { (name, bytes) -> Files.write(temp.resolve(name), bytes) }
        val pak = temp.resolve("models.pak")
        writePak(pak, entries)
        val broken = Files.write(temp.resolve("broken.md2"), byteArrayOf(0))
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            private var viewer: CakeViewer? = null
            override fun create() {
                try {
                    val app = CakeViewer(arrayOf(temp.resolve("model.md2").toString())).also {
                        viewer = it
                        it.create()
                    }
                    app.render()
                    val stage = Gdx.input.inputProcessor as Stage // No fly-camera multiplexer for MD2.
                    val pane = actors(stage.root).single { it.javaClass.simpleName == "Md2PreviewPane" }
                    assertTrue(pane.width > 0 && pane.height > 0)
                    val selector = actors(stage.root).filterIsInstance<SelectBox<*>>().single()
                    assertEquals(2, selector.items.size)
                    selector.selectedIndex = 1
                    stage.keyboardFocus = pane
                    stage.keyDown(Input.Keys.RIGHT)
                    app.render()
                    assertTrue(labels(stage).any { it.startsWith("Frame 2/200") })
                    selector.showScrollPane()
                    stage.keyDown(Input.Keys.ESCAPE) // Close dropdown, not the viewer.
                    repeat(3) { stage.act(0.5f) }
                    assertFalse(selector.scrollPane.hasParent())
                    app.resize(800, 600)
                    app.render()
                    assertTrue(pane.width > 0 && pane.height > 0)

                    app.openFile(pak.toString())
                    app.render()
                    assertNull(pane.stage)
                    val fileLabel = actors(stage.root).filterIsInstance<Label>()
                        .first { it.text.toString() == "model.md2" }
                    var tile: Actor = fileLabel
                    while (tile !is Button) tile = tile.parent
                    tile.listeners.filterIsInstance<ClickListener>().forEach {
                        it.tapCount = 2
                        it.clicked(InputEvent(), 0f, 0f)
                    }
                    app.render()
                    val archivePane = actors(stage.root).single { it.javaClass.simpleName == "Md2PreviewPane" }
                    assertEquals(2, actors(stage.root).filterIsInstance<SelectBox<*>>().single().items.size)
                    stage.keyboardFocus = archivePane
                    stage.keyDown(Input.Keys.ESCAPE)
                    app.render()
                    assertNull(archivePane.stage)

                    app.openFile(broken.toString())
                    app.render()
                    app.openFile(temp.resolve("model.md2").toString())
                    app.render()
                    assertEquals(1, actors((Gdx.input.inputProcessor as Stage).root)
                        .count { it.javaClass.simpleName == "Md2PreviewPane" })
                    assertEquals(0, Gdx.gl.glGetError())
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    Gdx.app.exit()
                }
            }
            override fun dispose() {
                try { viewer?.dispose() } catch (error: Throwable) { failure = failure ?: error }
            }
        }, Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(1024, 768)
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
        })
        failure?.let { throw it }
    }

    private fun actors(actor: Actor): List<Actor> =
        listOf(actor) + if (actor is Group) actor.children.flatMap(::actors) else emptyList()
    private fun labels(stage: Stage) = actors(stage.root).filterIsInstance<Label>().map { it.text.toString() }

    private fun pcx(rgb: Int): ByteArray = ByteArray(128 + 1 + 1 + 768).apply {
        this[0] = 10; this[1] = 5; this[2] = 1; this[3] = 8
        this[65] = 1; this[66] = 1
        this[128] = 1; this[129] = 12
        this[133] = (rgb shr 16).toByte(); this[134] = (rgb shr 8).toByte(); this[135] = rgb.toByte()
    }

    private fun writePak(path: Path, entries: Map<String, ByteArray>) {
        val directory = 12 + entries.values.sumOf { it.size }
        val buffer = ByteBuffer.allocate(directory + entries.size * 64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("PACK".toByteArray()).putInt(directory).putInt(entries.size * 64)
        entries.values.forEach { buffer.put(it) }
        var offset = 12
        entries.forEach { (name, bytes) ->
            val field = ByteArray(56)
            name.toByteArray().copyInto(field)
            buffer.put(field).putInt(offset).putInt(bytes.size)
            offset += bytes.size
        }
        Files.write(path, buffer.array())
    }
}
