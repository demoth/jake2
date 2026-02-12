package org.demoth.cake.ui

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import jake2.qcommon.Com
import org.demoth.cake.stages.LayoutCoordinateMapper

private val IDTECH2_GAME_NAMES = setOf("baseq2", "rogue", "xatrix", "ctf")
private const val CONCHARS_PATH = "pics/conchars.pcx"
private const val HUD_NUM_STYLES = 2
private const val HUD_NUM_FRAMES = 11
private const val HUD_NUM_MINUS = 10

private val HUD_NUMBER_GLYPHS = arrayOf(
    arrayOf("num_0", "num_1", "num_2", "num_3", "num_4", "num_5", "num_6", "num_7", "num_8", "num_9", "num_minus"),
    arrayOf("anum_0", "anum_1", "anum_2", "anum_3", "anum_4", "anum_5", "anum_6", "anum_7", "anum_8", "anum_9", "anum_minus"),
)

/**
 * IdTech2 HUD numeric font backed by per-digit picture assets.
 */
class IdTech2HudNumberFont(
    private val digitsByStyle: Array<Array<Texture>>,
) : HudNumberFont {
    private val glyphAdvance = digitsByStyle[0][0].width

    override fun draw(spriteBatch: SpriteBatch, x: Int, y: Int, value: Short, width: Int, color: Int, screenHeight: Int) {
        if (width < 1) return

        val clampedWidth = width.coerceAtMost(5)
        val text = value.toString()
        val charsToDraw = minOf(text.length, clampedWidth)
        val style = color.coerceIn(0, HUD_NUM_STYLES - 1)
        var drawX = x + 2 + glyphAdvance * (clampedWidth - charsToDraw)

        for (i in 0 until charsToDraw) {
            val frame = when (val ch = text[i]) {
                '-' -> HUD_NUM_MINUS
                in '0'..'9' -> ch - '0'
                else -> continue
            }
            val glyph = digitsByStyle[style][frame]
            val gdxY = LayoutCoordinateMapper.imageY(y, glyph.height, screenHeight)
            spriteBatch.draw(glyph, drawX.toFloat(), gdxY.toFloat())
            drawX += glyphAdvance
        }
    }

    override fun dispose() {
        // Textures are owned by AssetManager.
    }
}

class IdTech2UiStyle(
    override val hudFont: BitmapFont,
    override val hudNumberFont: HudNumberFont,
    private val onDispose: () -> Unit,
) : GameUiStyle {
    override fun dispose() {
        hudNumberFont.dispose()
        hudFont.dispose()
        onDispose()
    }
}

object GameUiStyleFactory {
    /**
     * Build game-specific HUD style resources.
     *
     * Quirk:
     * styles are recreated when serverdata changes, so each style instance must
     * acquire and release its own AssetManager references.
     */
    fun create(
        gameName: String,
        assetManager: AssetManager,
        skin: Skin,
    ): GameUiStyle {
        if (!IDTECH2_GAME_NAMES.contains(gameName.lowercase())) {
            return EngineUiStyle(skin)
        }

        val acquiredPaths = mutableListOf<String>()
        return try {
            val concharsTexture = loadTexture(assetManager, CONCHARS_PATH, acquiredPaths)
            val hudFont = ConcharsFontLoader.createBitmapFont(concharsTexture)

            val digitsByStyle = Array(HUD_NUM_STYLES) { style ->
                Array(HUD_NUM_FRAMES) { frame ->
                    loadTexture(assetManager, "pics/${HUD_NUMBER_GLYPHS[style][frame]}.pcx", acquiredPaths)
                }
            }
            val hudNumberFont = IdTech2HudNumberFont(digitsByStyle)

            IdTech2UiStyle(
                hudFont = hudFont,
                hudNumberFont = hudNumberFont,
                onDispose = { unloadTextures(assetManager, acquiredPaths) },
            )
        } catch (e: Exception) {
            unloadTextures(assetManager, acquiredPaths)
            Com.Warn("Failed to load IdTech2 UI style, using engine default style: ${e.message}")
            EngineUiStyle(skin)
        }
    }

    private fun loadTexture(assetManager: AssetManager, path: String, acquiredPaths: MutableList<String>): Texture {
        // Always acquire one refcount slot for this UI style instance.
        // AssetManager will increment refcount when already loaded.
        assetManager.load(path, Texture::class.java)
        assetManager.finishLoadingAsset<Texture>(path)
        acquiredPaths += path
        return assetManager.get(path, Texture::class.java)
    }

    private fun unloadTextures(assetManager: AssetManager, paths: List<String>) {
        // Release in reverse order to mirror acquisition sequence.
        for (path in paths.asReversed()) {
            if (assetManager.isLoaded(path, Texture::class.java)) {
                assetManager.unload(path)
            }
        }
    }
}
