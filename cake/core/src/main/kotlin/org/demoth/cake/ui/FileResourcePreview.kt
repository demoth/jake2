package org.demoth.cake.ui

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align

/** Shared read-only text and cinematic presentation for loose, archive, and profile resources. */
internal class FileResourcePreview(private val assets: AssetManager, private val styles: ContentBrowserStyles) {
    private val resources = PreviewResources(assets)
    var actor: Actor? = null
        private set

    fun open(path: String): Actor {
        close()
        try {
            val file = requireNotNull(assets.fileHandleResolver.resolve(path)) { "Missing resource: $path" }
            actor = if (path.substringAfterLast('.').lowercase() == "cin") {
                val palette = resources.load("q2palette.bin", Any::class.java) as IntArray
                resources.own(CinematicPreview(file.readBytes(), palette, styles))
            } else {
                // Bound label layout work even for unexpectedly large configuration/log files.
                val bytes = file.read().use { it.readNBytes(MAX_TEXT_BYTES + 1) }
                val text = bytes.copyOf(minOf(bytes.size, MAX_TEXT_BYTES)).toString(Charsets.UTF_8)
                    .removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
                val literal = if (styles.label.font.data.markupEnabled) text.replace("[", "[[") else text
                Table().apply {
                    val label = Label(literal, styles.label).apply { setAlignment(Align.topLeft) }
                    add(ScrollPane(label, styles.scrollPane).apply scroll@{
                        setFadeScrollBars(false)
                        setFlickScroll(false)
                        addListener(object : com.badlogic.gdx.scenes.scene2d.InputListener() {
                            override fun mouseMoved(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: Float, y: Float): Boolean {
                                stage?.scrollFocus = this@scroll
                                event.stop()
                                return true
                            }
                        })
                    // The host supplies the viewport size; do not let an outer browser
                    // ScrollPane expand this one to the entire document's preferred size.
                    }).prefSize(0f, 0f).grow().row()
                    if (bytes.size > MAX_TEXT_BYTES) add(Label("Showing the first 1 MiB.", styles.label)).left()
                }
            }
            return actor!!
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun keyDown(keycode: Int): Boolean = (actor as? CinematicPreview)?.keyDown(keycode) == true

    fun close() {
        actor?.remove()
        actor = null
        resources.close()
    }

    companion object {
        val extensions = setOf("cfg", "txt", "log", "ini", "csv", "json", "xml", "md", "shader", "ent", "lst", "cin")
        private const val MAX_TEXT_BYTES = 1024 * 1024
    }
}
