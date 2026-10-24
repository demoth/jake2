package org.demoth.cake.ui

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import org.demoth.cake.stages.ingame.SceneFrameBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@EnabledIfEnvironmentVariable(named = "CAKE_VIDEO_GL_TESTS", matches = "1")
class ConcharsAtlasGlTest {
    @Test
    fun scaledAtlasCellsAndFontDoNotSampleAdjacentGlyphs() {
        var failure: Throwable? = null
        Lwjgl3Application(object : ApplicationAdapter() {
            override fun create() {
                try {
                    verifyAtlasSampling()
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    Gdx.app.exit()
                }
            }
        }, Lwjgl3ApplicationConfiguration().apply {
            setInitialVisible(false)
            disableAudio(true)
            setWindowedMode(256, 256)
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
        })
        failure?.let { throw it }
    }

    private fun verifyAtlasSampling() {
        val pixmap = Pixmap(128, 128, Pixmap.Format.RGBA8888).apply {
            setColor(0f, 1f, 0f, 1f)
            fill()
            setColor(1f, 0f, 0f, 1f)
            fillRectangle(8, 8, 8, 8)
            fillRectangle(13 * 8, 8, 8, 8) // Separator start, glyph 29.
            setColor(0f, 0f, 1f, 1f)
            fillRectangle(15 * 8, 8, 8, 8) // Separator end, glyph 31; middle stays green.
        }
        val texture = Texture(pixmap)
        pixmap.dispose()
        val batch = SpriteBatch()
        val target = SceneFrameBuffer.create(256, 256, 24, 4)
        var region = TextureRegion(texture, 8, 8, 8, 8)
        val font = ConcharsFontLoader.createBitmapFont(texture)
        val menuFont = ConcharsFontLoader.createBitmapFont(texture)
        try {
            // Menu scaling must not change HUD metrics or require another atlas texture.
            assertNotSame(font.data, menuFont.data)
            assertSame(font.region.texture, menuFont.region.texture)
            menuFont.data.setScale(3f)
            assertEquals(24f, menuFont.lineHeight)
            assertEquals(8f, font.lineHeight)
            assertEquals(8, font.data.getGlyph('A').xadvance)
            val skin = Skin(Gdx.files.internal("../../assets/ui/uiskin.json"))
            try {
                val engine = EngineUiStyle(skin)
                val quake = createMenuWidgetStyles(skin, font, texture)
                assertEquals(24f, skin.getFont("default").lineHeight, "Raw engine UI retains native font size")
                assertEquals(8f, engine.menuWidgets.label.font.capHeight, 0.001f)
                assertEquals(38f / 3f, engine.menuWidgets.label.font.lineHeight, 0.001f)
                assertEquals(8f, quake.label.font.lineHeight)
                assertEquals(24f, skin.getDrawable("console-panel").topHeight, "Raw panel is not mutated")
                for (styles in listOf(engine.menuWidgets, quake)) {
                    assertEquals(8f, styles.containerPanel.topHeight, 0.001f)
                    assertEquals(8f, styles.button.up.topHeight, 0.001f)
                    assertEquals(4f, styles.controlPanel.topHeight, 0.001f)
                    assertEquals(16f, styles.controlPanel.minHeight, 0.001f)
                    assertEquals(8f, styles.slider.knob.minHeight, 0.001f)
                    assertEquals(24f, styles.separatorHorizontal.minWidth, 0.001f)
                    assertEquals(styles.separatorHorizontal.minHeight, styles.separatorVertical.minWidth)
                    assertEquals(24f, styles.separatorVertical.minHeight, 0.001f)
                    assertEquals(8f, styles.tree.plus.minWidth, 0.001f)
                    assertEquals(8f, styles.tree.plus.minHeight, 0.001f)
                    assertEquals(8f, styles.tree.minus.minWidth, 0.001f)
                    assertEquals(8f, styles.tree.minus.minHeight, 0.001f)
                    assertNotNull(styles.tree.selection)
                    assertNotNull(styles.tree.over)
                    assertNull(styles.tree.background)
                }
                assertEquals(16f, skin.get(Tree.TreeStyle::class.java).plus.minWidth, "Raw engine tree mark is unchanged")
                assertEquals((13 * 8 + 0.5f) / 128f, (quake.tree.plus as TextureRegionDrawable).region.u)
                assertSame(quake.tree.selection, quake.textField.selection, "Game text selection uses the tree's green fill")
                assertSame(quake.tree.selection, quake.list.selection)
                assertSame(quake.tree.selection, quake.selectBox.listStyle.selection)
                assertSame(quake.separatorVertical, quake.scrollPane.vScrollKnob)
                assertSame(quake.separatorVertical, quake.selectBox.scrollStyle.vScrollKnob)
                assertNull(quake.scrollPane.vScroll, "Game scrollbar has no separate track backdrop")
                assertNull(quake.selectBox.scrollStyle.vScroll)
                verifySeparators(quake, batch, target)
                engine.dispose()
            } finally {
                skin.dispose()
            }
            assertTrue(target.samples > 0)
            batch.projectionMatrix.setToOrtho2D(0f, 0f, 128f, 128f)
            fun greenPixels(multisampling: Boolean): Int {
                target.renderTarget.begin()
                if (multisampling) Gdx.gl.glEnable(0x809D) else Gdx.gl.glDisable(0x809D)
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
                batch.begin()
                for (row in 0..9) {
                    val offset = row * 0.05f
                    batch.draw(region, 10f + offset, row * 12f + offset, 100f, 8f)
                }
                batch.end()
                target.renderTarget.end()
                target.resolve()
                target.resolved.begin()
                val pixels = Pixmap.createFromFrameBuffer(0, 0, 256, 256)
                val count = (0 until 256).sumOf { x ->
                    (0 until 256).count { y -> ((pixels.getPixel(x, y) ushr 16) and 255) > 0 }
                }
                pixels.dispose()
                target.resolved.end()
                return count
            }
            assertTrue(greenPixels(true) > 0, "Unprotected UVs should reproduce adjacent-cell bleeding")
            region = ConcharsFontLoader.glyphRegion(texture, 17)
            assertEquals(8, region.regionWidth)
            assertEquals(8, region.regionHeight)
            assertEquals(0, greenPixels(true), "Inset UI must sample only its own atlas cell with MSAA")
            assertEquals(0, greenPixels(false), "Inset UI must sample only its own atlas cell without MSAA")
            val glyph = font.data.getGlyph(17.toChar())
            assertEquals(8, glyph.width)
            assertEquals(8, glyph.height)
            assertEquals(8, glyph.xadvance)
            region = TextureRegion(texture).apply { setRegion(glyph.u, glyph.v, glyph.u2, glyph.v2) }
            assertEquals(0, greenPixels(true), "Font UVs must also stay inside their glyph cell")
        } finally {
            Gdx.gl.glEnable(0x809D)
            target.dispose()
            batch.dispose()
            font.dispose()
            menuFont.dispose()
            texture.dispose()
        }
    }

    private fun verifySeparators(styles: MenuWidgetStyles, batch: SpriteBatch, target: SceneFrameBuffer) {
        target.renderTarget.begin()
        Gdx.gl.glDisable(0x809D)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.projectionMatrix.setToOrtho2D(0f, 0f, 128f, 128f)
        batch.begin()
        styles.separatorHorizontal.draw(batch, 10f, 20f, 80f, 8f)
        styles.separatorVertical.draw(batch, 110f, 20f, 8f, 80f)
        batch.end()
        target.renderTarget.end()
        target.resolve()
        target.resolved.begin()
        val pixels = Pixmap.createFromFrameBuffer(0, 0, 256, 256)
        try {
            fun rgb(x: Int, y: Int) = pixels.getPixel(x * 2, y * 2).ushr(8)
            assertEquals(0xff0000, rgb(17, 24), "Horizontal start cap remains eight units wide")
            assertEquals(0x00ff00, rgb(19, 24))
            assertEquals(0x00ff00, rgb(81, 24))
            assertEquals(0x0000ff, rgb(83, 24), "Horizontal end cap remains eight units wide")
            assertEquals(0xff0000, rgb(114, 99), "Rotation puts the start cap at the top")
            assertEquals(0x00ff00, rgb(114, 91))
            assertEquals(0x00ff00, rgb(114, 29))
            assertEquals(0x0000ff, rgb(114, 21), "Rotation puts the end cap at the bottom")
            assertEquals(0, rgb(109, 60), "Vertical separator stays inside its bounds")
            assertEquals(0, rgb(119, 60))
        } finally {
            pixels.dispose()
            target.resolved.end()
        }
    }
}
