package org.demoth.cake.stages

import org.demoth.cake.stages.ingame.hud.LayoutOp
import org.demoth.cake.stages.ingame.hud.LayoutProgramCompiler
import org.demoth.cake.stages.ingame.hud.LayoutXAnchor
import org.demoth.cake.stages.ingame.hud.LayoutYAnchor
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutProgramCompilerTest {
    @Test
    fun compilesSupportedOpsIntoProgram() {
        val program = LayoutProgramCompiler.compile(
            """
            xl 8 yt 16 num 3 1
            xr -40 yb -24 hnum
            xv 0 yv 0 picn "i_help"
            if 7 xv 26 yb -42 stat_string 8 endif
            client 24 40 1 13 88 120
            ctf 12 18 2 123 1001
            cstring2 "ALT"
            """.trimIndent()
        )

        val expected = listOf(
            LayoutOp.SetX(LayoutXAnchor.LEFT, 8),
            LayoutOp.SetY(LayoutYAnchor.TOP, 16),
            LayoutOp.Num(width = 3, statIndex = 1),
            LayoutOp.SetX(LayoutXAnchor.RIGHT, -40),
            LayoutOp.SetY(LayoutYAnchor.BOTTOM, -24),
            LayoutOp.HNum,
            LayoutOp.SetX(LayoutXAnchor.VIEW, 0),
            LayoutOp.SetY(LayoutYAnchor.VIEW, 0),
            LayoutOp.Picn("i_help"),
            LayoutOp.IfStat(
                statIndex = 7,
                body = listOf(
                    LayoutOp.SetX(LayoutXAnchor.VIEW, 26),
                    LayoutOp.SetY(LayoutYAnchor.BOTTOM, -42),
                    LayoutOp.StatString(8),
                )
            ),
            LayoutOp.Client(24, 40, 1, 13, 88, 120),
            LayoutOp.Ctf(12, 18, 2, 123, 1001),
            LayoutOp.Text(text = "ALT", alt = true, centered = true),
        )

        assertEquals(expected, program.ops)
    }

    @Test
    fun ignoresUnknownTokensAndComments() {
        val program = LayoutProgramCompiler.compile(
            """
            foo bar
            xl 1 // comment tail
            yt 2
            """.trimIndent()
        )

        assertEquals(
            listOf(
                LayoutOp.SetX(LayoutXAnchor.LEFT, 1),
                LayoutOp.SetY(LayoutYAnchor.TOP, 2),
            ),
            program.ops
        )
    }
}
