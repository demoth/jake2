package org.demoth.cake

import org.demoth.cake.hosting.HostMode
import org.demoth.cake.profile.CakeGameProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HostCommandParsingTest {
    private val profile = CakeGameProfile("test", "/game")

    @Test
    fun `console defaults and explicit options produce typed requests`() {
        assertEquals(HostMode.SINGLEPLAYER, parseHostRequest(listOf("host_start", "base1"), profile)?.mode)
        assertEquals(8, parseHostRequest(listOf("host_start", "q2dm1", "deathmatch"), profile)?.maxClients)
        val coop = parseHostRequest(listOf("host_start", "base1", "coop", "2", "3"), profile)!!
        assertEquals(profile, coop.profile)
        assertEquals(2, coop.skill)
        assertEquals(3, coop.maxClients)
    }

    @Test
    fun `invalid console arguments do not silently start a different mode`() {
        for (args in listOf(listOf("host_start"), listOf("host_start", "base1", "wrong"),
            listOf("host_start", "base1", "coop", "hard"),
            listOf("host_start", "base1", "coop", "4"),
            listOf("host_start", "base1", "coop", "1", "0"))) {
            assertNull(parseHostRequest(args, profile))
        }
    }
}
