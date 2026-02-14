package org.demoth.cake.stages

import org.demoth.cake.stages.ingame.hud.LayoutClientInfo
import org.demoth.cake.stages.ingame.hud.LayoutParser
import org.demoth.cake.stages.ingame.hud.LayoutDataProvider
import org.demoth.cake.stages.ingame.hud.Hud
import org.junit.Assert.assertEquals
import org.junit.Test

class HudRemainingBranchesTest {
    private val provider = object : LayoutDataProvider {
        override fun getImage(imageIndex: Int) = null
        override fun getConfigString(configIndex: Int): String? = null
        override fun getNamedPic(picName: String) = null
        override fun getClientInfo(clientIndex: Int): LayoutClientInfo? = when (clientIndex) {
            1 -> LayoutClientInfo(name = "PlayerOne", icon = null)
            2 -> LayoutClientInfo(name = "CurrentUser", icon = null)
            else -> null
        }

        override fun getCurrentPlayerIndex(): Int = 2
    }

    @Test
    fun compilesPicnClientAndCtfWithLegacySemantics() {
        val commands = LayoutParser.compile(
            layout = """
                xv 0 yv 0 picn "i_help"
                client 24 40 1 13 88 120
                ctf 12 18 2 123 1001
            """.trimIndent(),
            serverFrame = 2,
            stats = ShortArray(64),
            screenWidth = 800,
            screenHeight = 600,
            dataProvider = provider,
        )

        val expected = listOf(
            Hud.LayoutCommand.Image(240, 180, null),
            Hud.LayoutCommand.Text(296, 220, "PlayerOne", alt = true),
            Hud.LayoutCommand.Text(296, 228, "Score: ", alt = false),
            Hud.LayoutCommand.Text(352, 228, "13", alt = true),
            Hud.LayoutCommand.Text(296, 236, "Ping:  88", alt = false),
            Hud.LayoutCommand.Text(296, 244, "Time:  120", alt = false),
            Hud.LayoutCommand.Image(264, 220, null),
            Hud.LayoutCommand.Text(252, 198, "123 999 CurrentUser ", alt = true),
        )
        assertEquals(expected, commands)
    }
}
