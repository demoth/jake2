package org.demoth.cake

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.viewport.StretchViewport
import ktx.app.KtxApplicationAdapter
import ktx.scene2d.Scene2DSkin

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
class Cake : KtxApplicationAdapter {
    private lateinit var batch: SpriteBatch
    private lateinit var image: Texture
    private lateinit var menuStage: MainMenuStage

    override fun create() {
        Scene2DSkin.defaultSkin = Skin(Gdx.files.internal("ui/uiskin.json"))
        batch = SpriteBatch()
        image = Texture("libgdx.png")
        menuStage = MainMenuStage(StretchViewport(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())) // fixme: cvar
    }

    override fun render() {
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.begin()
        batch.draw(image, 140f, 210f)
        batch.end()
        menuStage.act()
        menuStage.draw()
    }

    override fun dispose() {
        batch.dispose()
        image.dispose()
        menuStage.dispose()
    }
}