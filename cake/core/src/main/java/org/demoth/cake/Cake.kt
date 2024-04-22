package org.demoth.cake

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.StretchViewport
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import ktx.app.KtxApplicationAdapter
import ktx.app.KtxInputAdapter
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.stages.ConsoleStage
import org.demoth.cake.stages.MainMenuStage

/**
 * Entrypoint for the client application
 *
 */
class Cake : KtxApplicationAdapter, KtxInputAdapter {
    private lateinit var menuStage: MainMenuStage
    private lateinit var consoleStage: ConsoleStage
    private var consoleVisible = false
    private var menuVisible = true

    init {
        Cmd.Init()
        Cvar.Init()
    }

    override fun create() {
        Scene2DSkin.defaultSkin = Skin(Gdx.files.internal("ui/uiskin.json"))
        val viewport = StretchViewport(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        menuStage = MainMenuStage(viewport) // fixme: cvar
        consoleStage = ConsoleStage(viewport)

        Gdx.input.inputProcessor = InputMultiplexer(
            this, // global input processor to control console and menu
            consoleStage,
            menuStage
        )

        Cmd.AddCommand("quit") {
            Gdx.app.exit()
        }
    }

    override fun render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f)

        if (consoleVisible) {
            consoleStage.act()
            consoleStage.draw()
        } else if (menuVisible) {
            menuStage.act()
            menuStage.draw()
        } // todo: else draw IngameScreen
    }

    // handle ESC for menu and F1 for console
    override fun keyUp(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.F1 -> {
                menuVisible = false
                consoleVisible = !consoleVisible
                if (consoleVisible) {
                    consoleStage.focus()
                }
                return true
            }
            Input.Keys.ESCAPE -> {
                consoleVisible = false
                menuVisible = !menuVisible
                return true
            }
            else -> return false
        }
    }

    override fun dispose() {
        menuStage.dispose()
        consoleStage.dispose()
    }
}