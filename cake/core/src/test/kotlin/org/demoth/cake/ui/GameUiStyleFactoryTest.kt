package org.demoth.cake.ui

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.List as Scene2dList
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Array
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class GameUiStyleFactoryTest {
    @Test
    fun fallsBackToEngineStyleForUnknownGameWhenIdTech2AssetsCannotBeLoaded() {
        val style = GameUiStyleFactory.create(
            gameName = "unknown",
            assetManager = AssetManager(),
            skin = createTestSkin(),
        )

        assertTrue(style is EngineUiStyle)
    }

    @Test
    fun fallsBackToEngineStyleWhenConcharsCannotBeLoaded() {
        val style = GameUiStyleFactory.create(
            gameName = "baseq2",
            assetManager = AssetManager(),
            skin = createTestSkin(),
        )

        assertTrue(style is EngineUiStyle)
    }

    private fun createTestSkin(): Skin {
        val font = BitmapFont(
            BitmapFont.BitmapFontData(),
            Array<TextureRegion>().apply { add(TextureRegion()) },
            false,
        )
        val emptyDrawable = TextureRegionDrawable(TextureRegion())
        return Skin().apply {
            add("default", font, BitmapFont::class.java)
            add("default", Label.LabelStyle(font, Color.WHITE))
            add("default", TextButton.TextButtonStyle().also { it.font = font })
            add(
                "default",
                TextField.TextFieldStyle().also {
                    it.font = font
                    it.messageFont = font
                    it.cursor = emptyDrawable
                    it.selection = emptyDrawable
                },
            )
            add(
                "default",
                ScrollPane.ScrollPaneStyle().also {
                    it.background = emptyDrawable
                    it.vScroll = emptyDrawable
                    it.vScrollKnob = emptyDrawable
                },
            )
            add(
                "default",
                Scene2dList.ListStyle().also {
                    it.font = font
                    it.fontColorSelected = Color.WHITE
                    it.fontColorUnselected = Color.WHITE
                    it.selection = emptyDrawable
                },
            )
            add(
                "default",
                SelectBox.SelectBoxStyle().also {
                    it.font = font
                    it.fontColor = Color.WHITE
                    it.listStyle = get(Scene2dList.ListStyle::class.java)
                    it.scrollStyle = get(ScrollPane.ScrollPaneStyle::class.java)
                    it.background = emptyDrawable
                },
            )
        }
    }

    companion object {
        private var app: HeadlessApplication? = null

        @BeforeAll
        @JvmStatic
        fun setupGdx() {
            if (Gdx.app == null) {
                app = HeadlessApplication(EmptyListener(), HeadlessApplicationConfiguration())
            }
        }

        @AfterAll
        @JvmStatic
        fun teardownGdx() {
            app?.exit()
            app = null
        }
    }

    private class EmptyListener : ApplicationListener {
        override fun create() = Unit
        override fun resize(width: Int, height: Int) = Unit
        override fun render() = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun dispose() = Unit
    }
}
