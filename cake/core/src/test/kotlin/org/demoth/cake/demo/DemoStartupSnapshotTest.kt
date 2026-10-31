package org.demoth.cake.demo

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.messages.server.SpawnBaselineMessage
import org.demoth.cake.CakeCvars
import org.demoth.cake.config.GameConfiguration
import org.demoth.cake.stages.ingame.ClientEntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DemoStartupSnapshotTest {
    @Test fun `startup capture copies live configstrings and received baselines without loading resources`() {
        CakeCvars.registerEntities()
        val assets = AssetManager(FileHandleResolver(::FileHandle), false)
        val config = GameConfiguration(assets)
        val entities = ClientEntityManager()
        try {
            config.applyConfigString(0, "first")
            config.applyConfigString(1, "")
            val baseline = entity_state_t(17).apply { modelindex = 3; origin[0] = 32f }
            entities.processBaselineMessage(SpawnBaselineMessage(baseline))
            val configs = config.snapshotConfigStrings()
            val baselines = entities.snapshotBaselines()
            config.applyConfigString(0, "second")
            baseline.modelindex = 4
            baseline.origin[0] = 64f
            entities.processBaselineMessage(SpawnBaselineMessage(baseline))
            assertEquals("first", configs.single().config)
            assertEquals(17, baselines.single().entityState.index)
            assertEquals(3, baselines.single().entityState.modelIndex)
            assertEquals(32f, baselines.single().entityState.origin.x)
            assertEquals("second", config.snapshotConfigStrings().single().config)
            assertEquals(4, entities.snapshotBaselines().single().entityState.modelIndex)
            assertEquals(0, assets.loadedAssets)
        } finally { entities.dispose(); assets.dispose() }
    }
}
