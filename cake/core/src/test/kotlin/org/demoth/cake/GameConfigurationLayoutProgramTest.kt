package org.demoth.cake

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import jake2.qcommon.Defines.CS_STATUSBAR
import org.demoth.cake.stages.ingame.hud.LayoutOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameConfigurationLayoutProgramTest {
    private val resolver = FileHandleResolver { _: String -> null }

    @Test
    fun compilesStatusbarLayoutIntoConfigResource() {
        val assetManager = AssetManager(resolver)
        try {
            val config = GameConfiguration(assetManager)
            config.applyConfigString(CS_STATUSBAR, "xl 8 yt 16 num 3 1")

            val compiled = config.getStatusBarLayoutProgram()
            assertNotNull(compiled)
            assertEquals(
                listOf(
                    LayoutOp.SetX(org.demoth.cake.stages.ingame.hud.LayoutXAnchor.LEFT, 8),
                    LayoutOp.SetY(org.demoth.cake.stages.ingame.hud.LayoutYAnchor.TOP, 16),
                    LayoutOp.Num(3, 1),
                ),
                compiled!!.ops
            )
        } finally {
            assetManager.dispose()
        }
    }

    @Test
    fun updatesCompiledStatusbarLayoutOnConfigUpdate() {
        val assetManager = AssetManager(resolver)
        try {
            val config = GameConfiguration(assetManager)
            config.applyConfigString(CS_STATUSBAR, "xl 8")
            config.applyConfigString(CS_STATUSBAR, "xr -40")

            val compiled = config.getStatusBarLayoutProgram()
            assertNotNull(compiled)
            assertEquals(
                listOf(LayoutOp.SetX(org.demoth.cake.stages.ingame.hud.LayoutXAnchor.RIGHT, -40)),
                compiled!!.ops
            )
        } finally {
            assetManager.dispose()
        }
    }

    @Test
    fun blankStatusbarLayoutDoesNotKeepCompiledProgram() {
        val assetManager = AssetManager(resolver)
        try {
            val config = GameConfiguration(assetManager)
            config.applyConfigString(CS_STATUSBAR, "xl 8")
            config.applyConfigString(CS_STATUSBAR, "")

            assertNull(config.getStatusBarLayoutProgram())
        } finally {
            assetManager.dispose()
        }
    }

    @Test
    fun compilesServerLayoutMessageTextOnSet() {
        val assetManager = AssetManager(resolver)
        try {
            val config = GameConfiguration(assetManager)
            config.layout = "xl 12 yt 34 string \"HELLO\""

            val compiled = config.getLayoutProgram()
            assertNotNull(compiled)
            assertEquals(
                listOf(
                    LayoutOp.SetX(org.demoth.cake.stages.ingame.hud.LayoutXAnchor.LEFT, 12),
                    LayoutOp.SetY(org.demoth.cake.stages.ingame.hud.LayoutYAnchor.TOP, 34),
                    LayoutOp.Text(text = "HELLO", alt = false, centered = false),
                ),
                compiled!!.ops
            )
        } finally {
            assetManager.dispose()
        }
    }
}
