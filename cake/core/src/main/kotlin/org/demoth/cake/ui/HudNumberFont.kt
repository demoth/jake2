package org.demoth.cake.ui

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import org.demoth.cake.stages.LayoutCoordinateMapper

interface HudNumberFont : Disposable {
    fun draw(spriteBatch: SpriteBatch, x: Int, y: Int, value: Short, width: Int, color: Int, screenHeight: Int)
}

class EngineHudNumberFont(private val fallbackFontProvider: () -> BitmapFont) : HudNumberFont {
    override fun draw(spriteBatch: SpriteBatch, x: Int, y: Int, value: Short, width: Int, color: Int, screenHeight: Int) {
        val gdxY = LayoutCoordinateMapper.textY(y, screenHeight).toFloat()
        fallbackFontProvider().draw(spriteBatch, "$value", x.toFloat(), gdxY)
    }

    override fun dispose() {
        // Font is owned by the engine skin.
    }
}
