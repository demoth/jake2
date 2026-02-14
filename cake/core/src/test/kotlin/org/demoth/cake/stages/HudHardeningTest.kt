package org.demoth.cake.stages

import org.demoth.cake.stages.ingame.hud.Hud
import org.demoth.cake.stages.ingame.hud.LayoutDataProvider
import org.demoth.cake.stages.ingame.hud.LayoutParser
import org.junit.Assert.assertEquals
import org.junit.Test

class HudHardeningTest {
    private val provider = object : LayoutDataProvider {
        override fun getImage(imageIndex: Int) = null
        override fun getConfigString(configIndex: Int): String? = null
        override fun getNamedPic(picName: String) = null
    }

    @Test
    fun invalidStatIndicesAreSkippedAndParsingContinues() {
        val commands = LayoutParser.compile(
            layout = """
                xl 1 yt 1 num 3 999
                xl 2 yt 2 pic 888
                if 777 string hidden endif
                xl 3 yt 3 stat_string 555
                xl 4 yt 4 string ok
            """.trimIndent(),
            serverFrame = 1,
            stats = ShortArray(64),
            screenWidth = 640,
            screenHeight = 480,
            dataProvider = provider,
        )

        assertEquals(
            listOf(Hud.LayoutCommand.Text(4, 4, "ok", alt = false)),
            commands,
        )
    }

    @Test
    fun invalidClientCommandsAreSkippedAndSubsequentCommandsStillRun() {
        val commands = LayoutParser.compile(
            layout = """
                client 24 40 999 13 88 120
                ctf 12 18 999 123 100
                xl 5 yt 6 string after
            """.trimIndent(),
            serverFrame = 1,
            stats = ShortArray(64),
            screenWidth = 800,
            screenHeight = 600,
            dataProvider = provider,
        )

        assertEquals(
            listOf(Hud.LayoutCommand.Text(5, 6, "after", alt = false)),
            commands,
        )
    }
}
