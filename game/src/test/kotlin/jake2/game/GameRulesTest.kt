package jake2.game

import jake2.qcommon.Defines
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Covers Game setting validation, entity capacity, and password rules published to clients. */
class GameRulesTest {
    private val imports = TestGameImports()

    @Test
    fun `Game settings reject unsafe values and allow signed gravity`() {
        val cvars = GameExportsImpl(imports).gameCvars
        assertTrue(cvars.skill.forceSet(3).accepted)
        assertFalse(cvars.skill.forceSet(4).accepted)
        assertEquals(3, cvars.skill.typedValue)
        assertTrue(cvars.flood_msgs.set(10).accepted)
        assertFalse(cvars.flood_msgs.set(11).accepted)
        assertFalse(cvars.flood_msgs.set(-1).accepted)
        assertEquals(10, cvars.flood_msgs.typedValue)
        assertFalse(cvars.flood_persecond.set(-1f).accepted)
        assertFalse(cvars.flood_waitdelay.set(Float.NaN).accepted)
        assertTrue(cvars.sv_gravity.set(-400.5f).accepted)
        assertEquals(-400.5f, cvars.sv_gravity.typedValue)
        assertFalse(imports.findCvar("sv_maxvelocity", Float::class.javaObjectType).set(-1f).accepted)
        assertFalse(cvars.maxentities.forceSet(Defines.MAX_EDICTS + 1).accepted)
    }

    @Test
    fun `entity budget always accommodates client and body slots`() {
        imports.findCvar("maxclients", Int::class.javaObjectType).set(32)
        imports.cvars.setResult("maxentities", "10")
        val game = GameExportsImpl(imports)
        assertEquals(32 + GameDefines.BODY_QUEUE_SIZE + 1, game.game.maxentities)
        assertEquals(game.game.maxentities, game.g_edicts.size)
        assertEquals(32, game.game.clients.size)
    }

    @Test
    fun `password settings govern player admission and the published requirement`() {
        val game = GameExportsImpl(imports)
        val cvars = game.gameCvars

        fun assertNeedPass(expected: Int) {
            // The running Game must publish the current values even without modified flags.
            cvars.password.modified = false
            cvars.spectator_password.modified = false
            game.G_RunFrame()
            assertEquals(expected, cvars.needpass.typedValue)
        }

        assertNeedPass(0)
        cvars.password.set("players")
        assertNeedPass(1)
        cvars.spectator_password.set("spectators")
        assertNeedPass(3)

        val player = game.g_edicts[1]
        assertFalse(PlayerClient.ClientConnect(player, "\\password\\spectators", game))
        assertFalse(PlayerClient.ClientConnect(player, "", game))
        assertTrue(PlayerClient.ClientConnect(player, "\\password\\players", game))

        cvars.password.set("NoNe")
        assertNeedPass(2)
        cvars.spectator_password.set("")
        assertNeedPass(0)
    }
}
