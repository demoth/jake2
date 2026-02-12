package org.demoth.cake.ui

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiStyleFactoryTest {
    @Test
    fun usesEngineStyleForUnknownGame() {
        val style = GameUiStyleFactory.create(
            gameName = "unknown",
            assetManager = AssetManager(),
            skin = Skin(),
        )

        assertTrue(style is EngineUiStyle)
    }

    @Test
    fun fallsBackToEngineStyleWhenConcharsCannotBeLoaded() {
        val style = GameUiStyleFactory.create(
            gameName = "baseq2",
            assetManager = AssetManager(),
            skin = Skin(),
        )

        assertTrue(style is EngineUiStyle)
    }
}
