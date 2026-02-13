package org.demoth.cake.stages

import jake2.qcommon.Defines
import org.demoth.cake.stages.ingame.hud.LayoutClientInfo
import org.demoth.cake.stages.ingame.hud.LayoutCommandCompiler
import org.demoth.cake.stages.ingame.hud.LayoutDataProvider
import org.demoth.cake.stages.ingame.hud.LayoutExecutor
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutExecutorParityTest {
    private val provider = object : LayoutDataProvider {
        private val configStrings = mapOf(
            5 to "picked-up-item",
            9 to "ctf-flag"
        )
        private val namedPics = mapOf("i_help" to null)
        private val clients = mapOf(
            1 to LayoutClientInfo(name = "PlayerOne", icon = null),
            2 to LayoutClientInfo(name = "CurrentUser", icon = null),
        )

        override fun getImage(imageIndex: Int) = null
        override fun getConfigString(configIndex: Int): String? = configStrings[configIndex]
        override fun getNamedPic(picName: String) = namedPics[picName]
        override fun getClientInfo(clientIndex: Int): LayoutClientInfo? = clients[clientIndex]
        override fun getCurrentPlayerIndex(): Int = 2
    }

    @Test
    fun parityForPositionAndNumericBranches() {
        val stats = ShortArray(64)
        stats[1] = 77
        stats[Defines.STAT_HEALTH] = 14
        stats[Defines.STAT_AMMO] = 6
        stats[Defines.STAT_ARMOR] = 25
        stats[Defines.STAT_FLASHES] = 0

        val layout = """
            xl 8 yt 16 num 3 1
            xr -40 yb -24 hnum
            xv 24 yv 20 anum
            xv 40 yv 40 rnum
        """.trimIndent()

        val expected = LegacyLayoutCommandHarness.compileCommands(layout, 12, stats, 800, 600, provider)
        val actual = LayoutCommandCompiler.compile(layout, 12, stats, 800, 600, provider)

        assertEquals(expected, actual)
    }

    @Test
    fun parityForQuotedTokensCommentsAndIfControlFlow() {
        val stats = ShortArray(64)
        stats[3] = 0 // causes if block to skip until endif
        stats[7] = 9 // config index for stat_string

        val layout = """
            xl 10 yt 10 string "hello world" // ignored tail
            if 3 num 3 1 endif
            xl 15 yt 30 cstring2 "alt words"
            xl 20 yt 40 stat_string 7
        """.trimIndent()

        val expected = LegacyLayoutCommandHarness.compileCommands(layout, 1, stats, 640, 480, provider)
        val actual = LayoutCommandCompiler.compile(layout, 1, stats, 640, 480, provider)

        assertEquals(expected, actual)
    }

    @Test
    fun parityWhenAnumOrRnumHiddenDoesNotAbortLayout() {
        val stats = ShortArray(64)
        stats[Defines.STAT_AMMO] = -1
        stats[Defines.STAT_ARMOR] = 0

        val layout = """
            xl 1 yt 2 anum
            xl 3 yt 4 rnum
            xl 5 yt 6 string "after-hidden"
        """.trimIndent()

        val expected = LegacyLayoutCommandHarness.compileCommands(layout, 2, stats, 320, 240, provider)
        val actual = LayoutCommandCompiler.compile(layout, 2, stats, 320, 240, provider)

        assertEquals(expected, actual)
    }

    @Test
    fun parityForPicnAndClientAndCtfBranches() {
        val stats = ShortArray(64)
        val layout = """
            xv 0 yv 0 picn "i_help"
            client 24 40 1 13 88 120
            ctf 12 18 2 123 1001
        """.trimIndent()

        val expected = LegacyLayoutCommandHarness.compileCommands(layout, 2, stats, 800, 600, provider)
        val actual = LayoutCommandCompiler.compile(layout, 2, stats, 800, 600, provider)

        assertEquals(expected, actual)
    }

    @Test
    fun hnumBlinkDependsOnServerFrame() {
        val layout = "xl 0 yt 0 hnum"
        val stats = ShortArray(64)
        stats[Defines.STAT_HEALTH] = 10

        val frame4 = LayoutCommandCompiler.compile(layout, 4, stats, 640, 480, provider)
        val frame8 = LayoutCommandCompiler.compile(layout, 8, stats, 640, 480, provider)

        assertEquals(
            listOf(LayoutExecutor.LayoutCommand.Number(0, 0, 10, 3, 1)),
            frame4
        )
        assertEquals(
            listOf(LayoutExecutor.LayoutCommand.Number(0, 0, 10, 3, 0)),
            frame8
        )
    }

    @Test
    fun statStringDependsOnCurrentStatValue() {
        val layout = "xl 0 yt 0 stat_string 7"
        val statsA = ShortArray(64).apply { this[7] = 5 }
        val statsB = ShortArray(64).apply { this[7] = 9 }

        val commandsA = LayoutCommandCompiler.compile(layout, 1, statsA, 640, 480, provider)
        val commandsB = LayoutCommandCompiler.compile(layout, 1, statsB, 640, 480, provider)

        assertEquals(
            listOf(LayoutExecutor.LayoutCommand.Text(0, 0, "picked-up-item", alt = false)),
            commandsA
        )
        assertEquals(
            listOf(LayoutExecutor.LayoutCommand.Text(0, 0, "ctf-flag", alt = false)),
            commandsB
        )
    }
}
