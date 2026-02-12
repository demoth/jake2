package org.demoth.cake.stages

import jake2.qcommon.Defines
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutExecutorParityTest {
    private val provider = object : LayoutDataProvider {
        private val configStrings = mapOf(
            5 to "picked-up-item",
            9 to "ctf-flag"
        )

        override fun getImage(imageIndex: Int) = null
        override fun getConfigString(configIndex: Int): String? = configStrings[configIndex]
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
}
