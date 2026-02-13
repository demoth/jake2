package org.demoth.cake.stages

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import jake2.qcommon.Defines.CS_PLAYERSKINS
import org.demoth.cake.GameConfiguration

internal data class LayoutTestContext(
    val assetManager: AssetManager,
    val gameConfig: GameConfiguration,
) {
    fun dispose() {
        assetManager.dispose()
    }
}

internal fun createLayoutTestContext(
    configStrings: Map<Int, String> = emptyMap(),
    playerNames: Map<Int, String> = emptyMap(),
): LayoutTestContext {
    val assetManager = AssetManager(FileHandleResolver { null })
    val gameConfig = GameConfiguration(assetManager)

    configStrings.forEach { (index, value) ->
        gameConfig.applyConfigString(index, value)
    }
    playerNames.forEach { (clientIndex, playerName) ->
        gameConfig.applyConfigString(CS_PLAYERSKINS + clientIndex, "$playerName\\male/grunt")
    }
    return LayoutTestContext(assetManager, gameConfig)
}
