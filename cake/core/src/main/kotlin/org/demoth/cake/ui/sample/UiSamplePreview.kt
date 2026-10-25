package org.demoth.cake.ui.sample

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import jake2.qcommon.Com
import jake2.qcommon.exec.Cmd
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.writeScreenshot
import java.nio.file.Path

/** Owns the developer gallery and capture sequence, borrowing its skin and game style.
 * Application transitions use the menu bus; in-gallery mode switches stay local.
 */
class UiSamplePreview(
    private val menuEventBus: MenuEventBus,
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
                null -> menuEventBus.postIntent(MenuIntent.OpenUiSample())
                "engine" -> menuEventBus.postIntent(MenuIntent.OpenUiSample(false))
                "game" -> menuEventBus.postIntent(MenuIntent.OpenUiSample(true))
                "off" -> menuEventBus.postIntent(MenuIntent.CloseUiSample)
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
            val directory = Path.of(args[2]).toAbsolutePath().normalize()
            menuEventBus.postIntent(MenuIntent.CaptureUiSample(modes, directory))
        }
    }

    fun open(game: Boolean?, referenceHeight: Float, skin: Skin, style: GameUiStyle) {
        if (sample == null) {
            sample = UiSkinSample(referenceHeight, skin, style,
                onMode = ::showMode,
                onClose = { menuEventBus.postIntent(MenuIntent.CloseUiSample) })
        }
        showMode(game ?: gameMode)
    }

    private fun showMode(game: Boolean) {
        gameMode = game
        sample!!.showMode(game)
    }

    /** Called only after the application handles the capture request and opens the gallery. */
    fun startCapture(modes: List<Boolean>, directory: Path) {
        check(isOpen) { "Open the UI sample before starting capture" }
        check(capture == null) { "UI capture already running" }
        require(modes.isNotEmpty()) { "At least one capture mode is required" }
        val target = Gdx.files.absolute(directory.toString())
        target.mkdirs()
        check(target.isDirectory) { "Not a capture directory: $target" }
        showMode(modes.first())
        capture = Capture(target, ArrayDeque(modes))
    }

    fun close(): Boolean {
        capture = null
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
            showMode(capture.modes.first())
        }
    }

    override fun dispose() {
        close()
        capture = null
        Cmd.RemoveCommand("ui_sample")
        Cmd.RemoveCommand("ui_sample_capture")
    }
}
