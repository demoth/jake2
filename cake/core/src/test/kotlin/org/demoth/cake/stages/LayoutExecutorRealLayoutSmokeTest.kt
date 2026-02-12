package org.demoth.cake.stages

import jake2.qcommon.Defines
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutExecutorRealLayoutSmokeTest {
    private val provider = object : LayoutDataProvider {
        override fun getImage(imageIndex: Int) = null
        override fun getConfigString(configIndex: Int): String? = "cfg-$configIndex"
    }

    @Test
    fun compilesSinglePlayerStatusbarLayout() {
        val stats = ShortArray(64)
        stats[2] = 1
        stats[4] = 1
        stats[6] = 1
        stats[7] = 1
        stats[8] = 5
        stats[9] = 1
        stats[10] = 42
        stats[11] = 1
        stats[Defines.STAT_HEALTH] = 99
        stats[Defines.STAT_AMMO] = 20
        stats[Defines.STAT_ARMOR] = 50

        val commands = LayoutCommandCompiler.compile(
            SINGLE_STATUSBAR_LAYOUT,
            serverFrame = 10,
            stats = stats,
            screenWidth = 800,
            screenHeight = 600,
            dataProvider = provider,
        )

        assertTrue(commands.isNotEmpty())
        assertTrue(commands.any { it is LayoutExecutor.LayoutCommand.Number })
        assertTrue(commands.any { it is LayoutExecutor.LayoutCommand.Image })
    }

    @Test
    fun compilesDeathmatchStatusbarLayoutWithSpectatorAndChaseStrings() {
        val stats = ShortArray(64)
        stats[2] = 1
        stats[4] = 1
        stats[6] = 1
        stats[7] = 1
        stats[8] = 5
        stats[9] = 1
        stats[10] = 42
        stats[11] = 1
        stats[14] = 12
        stats[16] = 33
        stats[17] = 1
        stats[Defines.STAT_HEALTH] = 99
        stats[Defines.STAT_AMMO] = 20
        stats[Defines.STAT_ARMOR] = 50

        val commands = LayoutCommandCompiler.compile(
            DEATHMATCH_STATUSBAR_LAYOUT,
            serverFrame = 10,
            stats = stats,
            screenWidth = 800,
            screenHeight = 600,
            dataProvider = provider,
        )

        assertTrue(commands.any {
            it is LayoutExecutor.LayoutCommand.Text && it.text == "SPECTATOR MODE" && it.alt
        })
        assertTrue(commands.any {
            it is LayoutExecutor.LayoutCommand.Text && it.text == "Chasing" && !it.alt
        })
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
