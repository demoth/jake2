package org.demoth.cake.ui

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import org.demoth.cake.stages.LayoutCoordinateMapper

/**
 * Draws numeric HUD fields using game-specific glyph sets.
 */
interface HudNumberFont : Disposable {
    /**
     * Draw a right-aligned numeric field at IdTech2 HUD coordinates.
     */
    fun draw(spriteBatch: SpriteBatch, x: Int, y: Int, value: Short, width: Int, color: Int, screenHeight: Int)
}

/**
 * Fallback numeric renderer that uses the engine default font.
 */
class EngineHudNumberFont(private val fallbackFontProvider: () -> BitmapFont) : HudNumberFont {
    override fun draw(spriteBatch: SpriteBatch, x: Int, y: Int, value: Short, width: Int, color: Int, screenHeight: Int) {
        val gdxY = LayoutCoordinateMapper.textY(y, screenHeight).toFloat()
        fallbackFontProvider().draw(spriteBatch, "$value", x.toFloat(), gdxY)
    }

    override fun dispose() {
        // Font is owned by the engine skin.
    }
}
