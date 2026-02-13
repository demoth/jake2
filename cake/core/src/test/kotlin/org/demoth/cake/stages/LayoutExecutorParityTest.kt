package org.demoth.cake.stages

import jake2.qcommon.Defines
import org.demoth.cake.stages.ingame.hud.LayoutCommandCompiler
import org.demoth.cake.stages.ingame.hud.LayoutExecutor
import org.demoth.cake.stages.ingame.hud.LayoutProgramCompiler
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutExecutorParityTest {
    private val legacyProvider = object : LegacyLayoutDataProvider {
        private val configStrings = mapOf(
            5 to "picked-up-item",
            9 to "ctf-flag"
        )
        private val clients = mapOf(
            1 to LegacyLayoutClientInfo(name = "PlayerOne", icon = null),
            2 to LegacyLayoutClientInfo(name = "CurrentUser", icon = null),
        )

        override fun getImage(imageIndex: Int) = null
        override fun getConfigString(configIndex: Int): String? = configStrings[configIndex]
        override fun getNamedPic(picName: String) = null
        override fun getClientInfo(clientIndex: Int): LegacyLayoutClientInfo? = clients[clientIndex]
        override fun getCurrentPlayerIndex(): Int = 2
    }

    private fun evaluate(layout: String, serverFrame: Int, stats: ShortArray, screenWidth: Int, screenHeight: Int): List<LayoutExecutor.LayoutCommand> {
        val context = createLayoutTestContext(
            configStrings = mapOf(
                5 to "picked-up-item",
                9 to "ctf-flag",
            ),
            playerNames = mapOf(
                1 to "PlayerOne",
                2 to "CurrentUser",
            )
        )
        return try {
            LayoutCommandCompiler.evaluate(
                program = LayoutProgramCompiler.compile(layout),
                serverFrame = serverFrame,
                stats = stats,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                gameConfig = context.gameConfig,
                playerIndex = 2,
            )
        } finally {
            context.dispose()
        }
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

        val expected = LegacyLayoutCommandHarness.compileCommands(layout, 12, stats, 800, 600, legacyProvider)
        val actual = evaluate(layout, 12, stats, 800, 600)

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

        val expected = LegacyLayoutCommandHarness.compileCommands(layout, 1, stats, 640, 480, legacyProvider)
        val actual = evaluate(layout, 1, stats, 640, 480)

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

        val expected = LegacyLayoutCommandHarness.compileCommands(layout, 2, stats, 320, 240, legacyProvider)
        val actual = evaluate(layout, 2, stats, 320, 240)

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

        val expected = LegacyLayoutCommandHarness.compileCommands(layout, 2, stats, 800, 600, legacyProvider)
        val actual = evaluate(layout, 2, stats, 800, 600)

        assertEquals(expected, actual)
    }

    @Test
    fun hnumBlinkDependsOnServerFrame() {
        val layout = "xl 0 yt 0 hnum"
        val stats = ShortArray(64)
        stats[Defines.STAT_HEALTH] = 10

        val frame4 = evaluate(layout, 4, stats, 640, 480)
        val frame8 = evaluate(layout, 8, stats, 640, 480)

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

        val commandsA = evaluate(layout, 1, statsA, 640, 480)
        val commandsB = evaluate(layout, 1, statsB, 640, 480)

        assertEquals(
            listOf(LayoutExecutor.LayoutCommand.Text(0, 0, "picked-up-item", alt = false)),
            commandsA
        )
        assertEquals(
            listOf(LayoutExecutor.LayoutCommand.Text(0, 0, "ctf-flag", alt = false)),
            commandsB
        )
    }

    @Test
    fun cachedProgramReevaluatesWithNewStatsAndViewport() {
        val program = LayoutProgramCompiler.compile("xr -40 yb -24 num 3 1")
        val context = createLayoutTestContext()
        try {
            val statsA = ShortArray(64).apply { this[1] = 77 }
            val statsB = ShortArray(64).apply { this[1] = 12 }

            val commandsA = LayoutCommandCompiler.evaluate(
                program = program,
                serverFrame = 1,
                stats = statsA,
                screenWidth = 800,
                screenHeight = 600,
                gameConfig = context.gameConfig,
            )
            val commandsB = LayoutCommandCompiler.evaluate(
                program = program,
                serverFrame = 1,
                stats = statsB,
                screenWidth = 1024,
                screenHeight = 768,
                gameConfig = context.gameConfig,
            )

            assertEquals(
                listOf(LayoutExecutor.LayoutCommand.Number(760, 576, 77, 3, 0)),
                commandsA
            )
            assertEquals(
                listOf(LayoutExecutor.LayoutCommand.Number(984, 744, 12, 3, 0)),
                commandsB
            )
        } finally {
            context.dispose()
        }
    }
}
