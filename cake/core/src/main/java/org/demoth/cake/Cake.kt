package org.demoth.cake

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.viewport.StretchViewport
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import ktx.app.KtxApplicationAdapter
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.stages.ConsoleStage
import org.demoth.cake.stages.MainMenuStage

/**
 * Entrypoint for the client application
 *
 */
class Cake : KtxApplicationAdapter {
    private lateinit var batch: SpriteBatch
    private lateinit var image: Texture
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
        batch = SpriteBatch()
        image = Texture("libgdx.png")
        val viewport = StretchViewport(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        menuStage = MainMenuStage(viewport) // fixme: cvar
        consoleStage = ConsoleStage(viewport)

        Gdx.input.inputProcessor = InputMultiplexer(
            consoleStage,
            menuStage
        )

        Cmd.AddCommand("quit") {
            Gdx.app.exit()
        }
    }

    override fun render() {
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.begin()
        batch.draw(image, 140f, 210f)
        batch.end()


        if (consoleVisible) {
            consoleStage.act()
            consoleStage.draw()
        } else {
            consoleStage.unfocusAll()
        }

        if (menuVisible) {
            menuStage.act()
            menuStage.draw()
            menuStage.unfocusAll()
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.GRAVE)) {
            menuVisible = false
            consoleVisible = !consoleVisible
            if (consoleVisible) {
                consoleStage.focus()
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            consoleVisible = false
            menuVisible = !menuVisible
        }
    }

    override fun dispose() {
        batch.dispose()
        image.dispose()
        menuStage.dispose()
        consoleStage.dispose()
    }
}