package org.demoth.cake.ui

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import jake2.qcommon.Com
import org.demoth.cake.stages.ingame.hud.LayoutCoordinateMapper

private const val CONCHARS_PATH = "pics/conchars.pcx"
private const val MENU_IN_SOUND_PATH = "misc/menu1.wav"
private const val MENU_OUT_SOUND_PATH = "misc/menu3.wav"
private const val HUD_NUM_STYLES = 2
private const val HUD_NUM_FRAMES = 11
private const val HUD_NUM_MINUS = 10

private val HUD_NUMBER_GLYPHS = arrayOf(
    arrayOf("num_0", "num_1", "num_2", "num_3", "num_4", "num_5", "num_6", "num_7", "num_8", "num_9", "num_minus"),
    arrayOf("anum_0", "anum_1", "anum_2", "anum_3", "anum_4", "anum_5", "anum_6", "anum_7", "anum_8", "anum_9", "anum_minus"),
)

/**
 * IdTech2 HUD numeric font backed by per-digit picture assets.
 *
 * Legacy counterpart:
 * `client/SCR.DrawField` + `SCR.sb_nums` glyph tables.
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
    override val menuWidgets: MenuWidgetStyles,
    override val menuSounds: MenuSoundStyles,
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
     * Build shared content style resources for the current resolver context.
     *
     * Quirk:
     * styles are shared by HUD and menus, and are recreated only when the effective content key changes,
     * so each style instance must
     * acquire and release its own AssetManager references.
     *
     * Legacy counterpart:
     * `client/SCR.TouchPics` pre-registering HUD assets.
     */
    fun create(
        gameName: String,
        assetManager: AssetManager,
        skin: Skin,
    ): GameUiStyle {
        val menuSoundPaths = mutableListOf<String>()
        val menuSounds = loadMenuSounds(assetManager, menuSoundPaths)
        // Always try IdTech2 HUD assets first. CakeFileResolver resolves them through
        // mod -> baseq2 layers, so for example, map-only mods inherit baseq2 HUD assets automatically.
        val hudAssetPaths = mutableListOf<String>()
        return try {
            val concharsTexture = loadTexture(assetManager, CONCHARS_PATH, hudAssetPaths)
            val hudFont = ConcharsFontLoader.createBitmapFont(concharsTexture)

            val digitsByStyle = Array(HUD_NUM_STYLES) { style ->
                Array(HUD_NUM_FRAMES) { frame ->
                    loadTexture(assetManager, "pics/${HUD_NUMBER_GLYPHS[style][frame]}.pcx", hudAssetPaths)
                }
            }
            val hudNumberFont = IdTech2HudNumberFont(digitsByStyle)

            IdTech2UiStyle(
                hudFont = hudFont,
                hudNumberFont = hudNumberFont,
                menuWidgets = createMenuWidgetStyles(skin, hudFont, concharsTexture),
                menuSounds = menuSounds,
                onDispose = { unloadAssets(assetManager, hudAssetPaths + menuSoundPaths) },
            )
        } catch (e: Exception) {
            unloadAssets(assetManager, hudAssetPaths)
            Com.Warn("Failed to load IdTech2 UI style for game '$gameName', using engine default style: ${e.message}")
            EngineUiStyle(
                skin = skin,
                menuSounds = menuSounds,
                onDispose = { unloadAssets(assetManager, menuSoundPaths) },
            )
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

    private fun loadMenuSounds(assetManager: AssetManager, acquiredPaths: MutableList<String>): MenuSoundStyles {
        return MenuSoundStyles(
            enterSubmenu = loadSoundIfPresent(assetManager, MENU_IN_SOUND_PATH, acquiredPaths),
            exitSubmenu = loadSoundIfPresent(assetManager, MENU_OUT_SOUND_PATH, acquiredPaths),
        )
    }

    private fun loadSoundIfPresent(assetManager: AssetManager, path: String, acquiredPaths: MutableList<String>): Sound? {
        return try {
            assetManager.load(path, Sound::class.java)
            assetManager.finishLoadingAsset<Sound>(path)
            acquiredPaths += path
            assetManager.get(path, Sound::class.java)
        } catch (_: Exception) {
            if (assetManager.isLoaded(path)) {
                assetManager.unload(path)
            }
            null
        }
    }

    private fun unloadAssets(assetManager: AssetManager, paths: List<String>) {
        // Release in reverse order to mirror acquisition sequence.
        for (path in paths.asReversed()) {
            if (assetManager.isLoaded(path)) {
                assetManager.unload(path)
            }
        }
    }
}
