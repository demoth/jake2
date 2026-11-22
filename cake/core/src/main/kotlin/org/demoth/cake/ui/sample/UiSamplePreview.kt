package org.demoth.cake.ui.sample

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import jake2.qcommon.Com
import jake2.qcommon.exec.Cmd
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.GameUiStyle

/** Owns the developer gallery, borrowing its skin and game style.
 * Application transitions use the menu bus; in-gallery mode switches stay local.
 */
class UiSamplePreview(
    private val menuEventBus: MenuEventBus,
) : Disposable {
    private var sample: UiSkinSample? = null
    private var gameMode = false

    val isOpen: Boolean get() = sample != null
    val inputProcessor: InputProcessor? get() = sample

    fun registerCommands() {
        Cmd.AddCommand("ui_sample", "(internal) Preview UI styles: ui_sample [engine|game|off]") { args ->
            if (args.size > 2) {
                Com.Print("Usage: ui_sample [engine|game|off]")
                return@AddCommand
            }
            when (args.getOrNull(1)) {
                null -> menuEventBus.postIntent(MenuIntent.OpenUiSample())
                "engine" -> menuEventBus.postIntent(MenuIntent.OpenUiSample(false))
                "game" -> menuEventBus.postIntent(MenuIntent.OpenUiSample(true))
                "off" -> menuEventBus.postIntent(MenuIntent.CloseUiSample)
                else -> Com.Print("Usage: ui_sample [engine|game|off]")
            }
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
    }

    override fun dispose() {
        close()
        Cmd.RemoveCommand("ui_sample")
    }
}
