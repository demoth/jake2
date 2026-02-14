package org.demoth.cake.stages.ingame.hud

import jake2.qcommon.Defines
import org.junit.Assert
import org.junit.Test

class HudTest {
    private sealed interface TestCommand {
        data class Image(val x: Int, val y: Int, val texture: Any?) : TestCommand
        data class Text(val x: Int, val y: Int, val text: String, val alt: Boolean, val centerWidth: Int? = null) : TestCommand
        data class Number(val x: Int, val y: Int, val value: Short, val width: Int, val color: Int) : TestCommand
    }

    private val provider = object : LayoutDataProvider {
        private val configStrings = mapOf(
            5 to "picked-up-item",
            9 to "ctf-flag",
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

    private fun collectCommands(
        layout: String,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
    ): List<TestCommand> {
        val commands = mutableListOf<TestCommand>()
        executeLayoutScript(
            layout = layout,
            serverFrame = serverFrame,
            stats = stats,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            dataProvider = provider,
            onImage = { x, y, texture -> commands += TestCommand.Image(x, y, texture) },
            onText = { x, y, text, alt, centerWidth ->
                commands += TestCommand.Text(x, y, text, alt, centerWidth)
            },
            onNumber = { x, y, value, width, color -> commands += TestCommand.Number(x, y, value, width, color) },
        )
        return commands
    }

    @Test
    fun parsesPositionAndNumericBranches() {
        val stats = ShortArray(64)
        stats[Defines.STAT_HEALTH] = 14
        stats[Defines.STAT_AMMO] = 6
        stats[Defines.STAT_ARMOR] = 25

        val commands = collectCommands(
            layout = """
                xl 8 yt 16 num 3 1
                xr -40 yb -24 hnum
                xv 24 yv 20 anum
                xv 40 yv 40 rnum
            """.trimIndent(),
            serverFrame = 12,
            stats = stats,
            screenWidth = 800,
            screenHeight = 600,
        )

        Assert.assertEquals(
            listOf(
                TestCommand.Number(8, 16, 14, 3, 0),
                TestCommand.Number(760, 576, 14, 3, 1),
                TestCommand.Number(264, 200, 6, 3, 0),
                TestCommand.Number(280, 220, 25, 3, 0),
            ),
            commands,
        )
    }

    @Test
    fun parsesQuotedTokensCommentsAndIfControlFlow() {
        val stats = ShortArray(64)
        stats[3] = 0
        stats[7] = 9

        val commands = collectCommands(
            layout = """
                xl 10 yt 10 string "hello world" // ignored tail
                if 3 num 3 1 endif
                xl 15 yt 30 cstring2 "alt words"
                xl 20 yt 40 stat_string 7
            """.trimIndent(),
            serverFrame = 1,
            stats = stats,
            screenWidth = 640,
            screenHeight = 480,
        )

        Assert.assertEquals(
            listOf(
                TestCommand.Text(10, 10, "hello world", alt = false),
                TestCommand.Text(15, 30, "alt words", alt = true, centerWidth = 320),
                TestCommand.Text(20, 40, "ctf-flag", alt = false),
            ),
            commands,
        )
    }

    @Test
    fun parsesPicnClientAndCtfBranches() {
        val commands = collectCommands(
            layout = """
                xv 0 yv 0 picn "i_help"
                client 24 40 1 13 88 120
                ctf 12 18 2 123 1001
            """.trimIndent(),
            serverFrame = 2,
            stats = ShortArray(64),
            screenWidth = 800,
            screenHeight = 600,
        )

        Assert.assertEquals(
            listOf(
                TestCommand.Image(240, 180, null),
                TestCommand.Text(296, 220, "PlayerOne", alt = true),
                TestCommand.Text(296, 228, "Score: ", alt = false),
                TestCommand.Text(352, 228, "13", alt = true),
                TestCommand.Text(296, 236, "Ping:  88", alt = false),
                TestCommand.Text(296, 244, "Time:  120", alt = false),
                TestCommand.Image(264, 220, null),
                TestCommand.Text(252, 198, "123 999 CurrentUser ", alt = true),
            ),
            commands,
        )
    }

    @Test
    fun hnumBlinkDependsOnServerFrame() {
        val stats = ShortArray(64)
        stats[Defines.STAT_HEALTH] = 10

        val frame4 = collectCommands(
            layout = "xl 0 yt 0 hnum",
            serverFrame = 4,
            stats = stats,
            screenWidth = 640,
            screenHeight = 480,
        )
        val frame8 = collectCommands(
            layout = "xl 0 yt 0 hnum",
            serverFrame = 8,
            stats = stats,
            screenWidth = 640,
            screenHeight = 480,
        )

        Assert.assertEquals(listOf(TestCommand.Number(0, 0, 10, 3, 1)), frame4)
        Assert.assertEquals(listOf(TestCommand.Number(0, 0, 10, 3, 0)), frame8)
    }

    @Test
    fun statStringDependsOnCurrentStatValue() {
        val commandsA = collectCommands(
            layout = "xl 0 yt 0 stat_string 7",
            serverFrame = 1,
            stats = ShortArray(64).apply { this[7] = 5 },
            screenWidth = 640,
            screenHeight = 480,
        )
        val commandsB = collectCommands(
            layout = "xl 0 yt 0 stat_string 7",
            serverFrame = 1,
            stats = ShortArray(64).apply { this[7] = 9 },
            screenWidth = 640,
            screenHeight = 480,
        )

        Assert.assertEquals(listOf(TestCommand.Text(0, 0, "picked-up-item", alt = false)), commandsA)
        Assert.assertEquals(listOf(TestCommand.Text(0, 0, "ctf-flag", alt = false)), commandsB)
    }

    @Test
    fun skipsInvalidIndicesAndContinuesParsing() {
        val commands = collectCommands(
            layout = """
                xl 1 yt 1 num 3 999
                xl 2 yt 2 pic 888
                if 777 string hidden endif
                xl 3 yt 3 stat_string 555
                client 24 40 999 13 88 120
                ctf 12 18 999 123 100
                xl 4 yt 4 string ok
            """.trimIndent(),
            serverFrame = 1,
            stats = ShortArray(64),
            screenWidth = 640,
            screenHeight = 480,
        )

        Assert.assertEquals(
            listOf(TestCommand.Text(4, 4, "ok", alt = false)),
            commands,
        )
    }

    @Test
    fun parsesRealStatusbarLayouts() {
        val singleStats = ShortArray(64).apply {
            this[2] = 1
            this[4] = 1
            this[6] = 1
            this[7] = 1
            this[8] = 5
            this[9] = 1
            this[10] = 42
            this[11] = 1
            this[Defines.STAT_HEALTH] = 99
            this[Defines.STAT_AMMO] = 20
            this[Defines.STAT_ARMOR] = 50
        }

        val singleCommands = collectCommands(
            layout = SINGLE_STATUSBAR_LAYOUT,
            serverFrame = 10,
            stats = singleStats,
            screenWidth = 800,
            screenHeight = 600,
        )
        Assert.assertTrue(singleCommands.isNotEmpty())
        Assert.assertTrue(singleCommands.any { it is TestCommand.Number })
        Assert.assertTrue(singleCommands.any { it is TestCommand.Image })

        val dmStats = ShortArray(64).apply {
            this[2] = 1
            this[4] = 1
            this[6] = 1
            this[7] = 1
            this[8] = 5
            this[9] = 1
            this[10] = 42
            this[11] = 1
            this[14] = 12
            this[16] = 33
            this[17] = 1
            this[Defines.STAT_HEALTH] = 99
            this[Defines.STAT_AMMO] = 20
            this[Defines.STAT_ARMOR] = 50
        }

        val dmCommands = collectCommands(
            layout = DEATHMATCH_STATUSBAR_LAYOUT,
            serverFrame = 10,
            stats = dmStats,
            screenWidth = 800,
            screenHeight = 600,
        )
        Assert.assertTrue(dmCommands.any { it is TestCommand.Text && it.text == "SPECTATOR MODE" && it.alt })
        Assert.assertTrue(dmCommands.any { it is TestCommand.Text && it.text == "Chasing" && !it.alt })
    }

    companion object {
        // Sourced from game/src/main/java/jake2/game/GameSpawn.java
        private const val SINGLE_STATUSBAR_LAYOUT = """
            yb -24 xv 0 hnum xv 50 pic 0
            if 2 xv 100 anum xv 150 pic 2 endif
            if 4 xv 200 rnum xv 250 pic 4 endif
            if 6 xv 296 pic 6 endif
            yb -50
            if 7 xv 0 pic 7 xv 26 yb -42 stat_string 8 yb -50 endif
            if 9 xv 262 num 2 10 xv 296 pic 9 endif
            if 11 xv 148 pic 11 endif
        """

        // Sourced from game/src/main/java/jake2/game/GameSpawn.java
        private const val DEATHMATCH_STATUSBAR_LAYOUT = """
            yb -24 xv 0 hnum xv 50 pic 0
            if 2 xv 100 anum xv 150 pic 2 endif
            if 4 xv 200 rnum xv 250 pic 4 endif
            if 6 xv 296 pic 6 endif
            yb -50
            if 7 xv 0 pic 7 xv 26 yb -42 stat_string 8 yb -50 endif
            if 9 xv 246 num 2 10 xv 296 pic 9 endif
            if 11 xv 148 pic 11 endif
            xr -50 yt 2 num 3 14
            if 17 xv 0 yb -58 string2 "SPECTATOR MODE" endif
            if 16 xv 0 yb -68 string "Chasing" xv 64 stat_string 16 endif
        """
    }
}
