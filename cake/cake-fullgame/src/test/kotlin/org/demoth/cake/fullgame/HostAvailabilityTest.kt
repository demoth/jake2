package org.demoth.cake.fullgame

import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import org.demoth.cake.hosting.*
import org.demoth.cake.profile.CakeGameProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HostAvailabilityTest {
    @Test
    fun `host construction and invalid starts do not bootstrap server state`() {
        val cvars = Cvar.getInstance()
        val mapCommand = Cmd.FindCommand("map")
        val maxclients = cvars.find("maxclients")
        val hosts = listOf(UnavailableCakeHost(), EmbeddedCakeHost())
        assertEquals(listOf(HostAvailability.REMOTE_ONLY, HostAvailability.AVAILABLE), hosts.map { it.availability })
        for (host in hosts) {
            for (mode in HostMode.entries) {
                val result = host.start(HostRequest(CakeGameProfile("test", "/missing"), "base1", mode))
                assertInstanceOf(HostStartResult.Rejected::class.java, result)
            }
            host.update(100)
            host.setPaused(true)
            host.stop("test")
            host.stop("repeated stop")
            assertEquals(HostState.STOPPED, host.state)
        }
        assertSame(mapCommand, Cmd.FindCommand("map"))
        assertSame(maxclients, cvars.find("maxclients"))
        // Exercise the runtime factory dependency without constructing game/server globals.
        assertNotNull(Class.forName("jake2.game.GameExportsImpl").getConstructor(jake2.qcommon.GameImports::class.java))
    }
}
