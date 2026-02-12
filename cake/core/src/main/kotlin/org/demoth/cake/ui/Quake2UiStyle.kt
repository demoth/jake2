package org.demoth.cake.ui

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import jake2.qcommon.Com

private const val QUAKE2_GAME_NAME = "baseq2"
private const val CONCHARS_PATH = "pics/conchars.pcx"

class Quake2UiStyle(private val concharsFont: com.badlogic.gdx.graphics.g2d.BitmapFont) : GameUiStyle {
    override val hudFont = concharsFont

    override fun dispose() {
        concharsFont.dispose()
    }
}

object GameUiStyleFactory {
    fun create(
        gameName: String,
        assetManager: AssetManager,
        skin: Skin,
    ): GameUiStyle {
        if (!gameName.equals(QUAKE2_GAME_NAME, ignoreCase = true)) {
            return EngineUiStyle(skin)
        }

        return try {
            if (!assetManager.isLoaded(CONCHARS_PATH, Texture::class.java)) {
                assetManager.load(CONCHARS_PATH, Texture::class.java)
                assetManager.finishLoadingAsset<Texture>(CONCHARS_PATH)
            }
            val concharsTexture = assetManager.get(CONCHARS_PATH, Texture::class.java)
            Quake2UiStyle(ConcharsFontLoader.createBitmapFont(concharsTexture))
        } catch (e: Exception) {
            Com.Warn("Failed to load Quake2 conchars font, using engine default style: ${e.message}")
            EngineUiStyle(skin)
        }
    }
}
