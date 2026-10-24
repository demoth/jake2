package org.demoth.cake.ui.sample

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import jake2.qcommon.Com
import jake2.qcommon.exec.Cmd
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.writeScreenshot
import java.nio.file.Path

/** Owns the developer gallery and capture sequence, borrowing its skin and game style.
 * Open/close requests go through the application so visibility and input policy stay there.
 */
class UiSamplePreview(
    private val onOpen: (Boolean) -> Unit,
    private val onClose: () -> Unit,
) : Disposable {
    private var sample: UiSkinSample? = null
    private data class Capture(val directory: FileHandle, val modes: ArrayDeque<Boolean>, var frames: Int = 2)
    private var capture: Capture? = null
    private var gameMode = false

    val isOpen: Boolean get() = sample != null
    val inputProcessor: InputProcessor? get() = sample

    fun registerCommands() {
        Cmd.AddCommand("ui_sample", "(internal) Preview UI styles: ui_sample [engine|game|off]") { args ->
            if (args.size > 2) {
                Com.Printf("Usage: ui_sample [engine|game|off]\n")
                return@AddCommand
            }
            when (args.getOrNull(1)) {
                null -> onOpen(gameMode)
                "engine" -> onOpen(false)
                "game" -> onOpen(true)
                "off" -> onClose()
                else -> Com.Printf("Usage: ui_sample [engine|game|off]\n")
            }
        }

        Cmd.AddCommand("ui_sample_capture", "Capture engine/game/both styles to a directory, then exit") { args ->
            require(args.size == 3) { "Usage: ui_sample_capture <engine|game|both> <directory>" }
            val modes = when (args[1]) {
                "engine" -> listOf(false)
                "game" -> listOf(true)
                "both" -> listOf(false, true)
                else -> throw IllegalArgumentException("Expected engine, game or both")
            }
            check(capture == null) { "UI capture already running" }
            val directory = Gdx.files.absolute(Path.of(args[2]).toAbsolutePath().normalize().toString())
            directory.mkdirs()
            check(directory.isDirectory) { "Not a capture directory: $directory" }
            onOpen(modes.first())
            capture = Capture(directory, ArrayDeque(modes))
        }
    }

    fun open(game: Boolean, referenceHeight: Float, skin: Skin, style: GameUiStyle) {
        if (sample == null) {
            sample = UiSkinSample(referenceHeight, skin, style, onMode = onOpen, onClose = onClose)
        }
        gameMode = game
        sample!!.showMode(game)
    }

    fun close(): Boolean {
        val active = sample ?: return false
        sample = null
        active.dispose()
        return true
    }

    fun resize(width: Int, height: Int) {
        sample?.resize(width, height)
    }

    fun setReferenceHeight(height: Float) {
        sample?.setReferenceHeight(height)
    }

    fun render(deltaSeconds: Float) {
        val active = sample ?: return
        ScreenUtils.clear(0.08f, 0.08f, 0.08f, 1f)
        active.viewport.apply()
        active.act(deltaSeconds)
        active.draw()
        captureFrame()
    }

    private fun captureFrame() {
        val capture = this.capture ?: return
        if (--capture.frames > 0) return
        val mode = if (capture.modes.removeFirst()) "game" else "engine"
        writeScreenshot(capture.directory.child("$mode.png"))
        if (capture.modes.isEmpty()) {
            this.capture = null
            Gdx.app.exit()
        } else {
            capture.frames = 2
            onOpen(capture.modes.first())
        }
    }

    override fun dispose() {
        close()
        capture = null
        Cmd.RemoveCommand("ui_sample")
        Cmd.RemoveCommand("ui_sample_capture")
    }
}
