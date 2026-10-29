package org.demoth.cake.profile

import jake2.qcommon.exec.Cvar

/** Profile-switch policy for the built-in game's startup inputs, not a registry owner.
 * Initial bootstrap already applies profile config and CLI overrides, so only live switches reset.
 * Keep this list aligned with ServerHost/GameCvars as gameplay options are added. Operational
 * settings (ports, RCON, timing, downloads), video and client preferences are application-owned here.
 */
internal object CakeHostStartupSettings {
    fun reset() {
        val cvars = Cvar.getInstance()
        for (name in names) cvars.resetInput(name)
    }

    private val names = listOf(
        "deathmatch", "coop", "maxclients", "hostname", "cheats", "dmflags",
        "fraglimit", "timelimit", "sv_airaccelerate", "password", "spectator_password",
        "skill", "sv_gravity", "maxspectators", "g_select_empty", "flood_msgs",
        "flood_persecond", "flood_waitdelay", "maxentities", "dev_blaster", "sv_maplist",
    )
}
