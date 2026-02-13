package org.demoth.cake.stages

import org.demoth.cake.stages.ingame.hud.LayoutCommandCompiler
import org.demoth.cake.stages.ingame.hud.LayoutExecutor
import org.demoth.cake.stages.ingame.hud.LayoutProgramCompiler
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutExecutorRemainingBranchesTest {
    @Test
    fun compilesPicnClientAndCtfWithLegacySemantics() {
        val context = createLayoutTestContext(
            playerNames = mapOf(
                1 to "PlayerOne",
                2 to "CurrentUser",
            )
        )
        val commands = try {
            LayoutCommandCompiler.evaluate(
                program = LayoutProgramCompiler.compile(
                    """
                    xv 0 yv 0 picn "i_help"
                    client 24 40 1 13 88 120
                    ctf 12 18 2 123 1001
                    """.trimIndent()
                ),
                serverFrame = 2,
                stats = ShortArray(64),
                screenWidth = 800,
                screenHeight = 600,
                gameConfig = context.gameConfig,
                playerIndex = 2,
            )
        } finally {
            context.dispose()
        }

        val expected = listOf(
            LayoutExecutor.LayoutCommand.Image(240, 180, null),
            LayoutExecutor.LayoutCommand.Text(296, 220, "PlayerOne", alt = true),
            LayoutExecutor.LayoutCommand.Text(296, 228, "Score: ", alt = false),
            LayoutExecutor.LayoutCommand.Text(352, 228, "13", alt = true),
            LayoutExecutor.LayoutCommand.Text(296, 236, "Ping:  88", alt = false),
            LayoutExecutor.LayoutCommand.Text(296, 244, "Time:  120", alt = false),
            LayoutExecutor.LayoutCommand.Image(264, 220, null),
            LayoutExecutor.LayoutCommand.Text(252, 198, "123 999 CurrentUser ", alt = true),
        )
        assertEquals(expected, commands)
    }
}
