package org.demoth.cake

import jake2.qcommon.Defines.CVAR_ARCHIVE
import jake2.qcommon.Defines.CVAR_LATCH
import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.Defines.CVAR_USERINFO
import jake2.qcommon.exec.Cvar
import org.demoth.cake.stages.ingame.RenderTuningCvars

object CakeCvars {
    fun registerAll() {
        registerUserInfo()
        registerClient()
        registerDownloads()
        registerVideoMode()
    }

    fun registerVideoMode() {
        val cvars = Cvar.getInstance()
        cvars.Get("vid_fullscreen", "0", CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH, "Fullscreen mode")
        cvars.Get("vid_width", "1024", CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH, "Windowed mode width")
        cvars.Get("vid_height", "768", CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH, "Windowed mode height")
        cvars.Get("vid_vsync", "1", CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH, "Vertical sync")
    }

    private fun registerUserInfo() {
        val cvars = Cvar.getInstance()
        cvars.Get("password", "", CVAR_USERINFO or CVAR_ARCHIVE, "Server password")
        cvars.Get("spectator", "0", CVAR_USERINFO, "Request spectator mode")
        cvars.Get("name", "unnamed", CVAR_USERINFO or CVAR_ARCHIVE, "Player name")
        cvars.Get("skin", "male/grunt", CVAR_USERINFO or CVAR_ARCHIVE, "Player model and skin")
        cvars.Get("rate", "25000", CVAR_USERINFO or CVAR_ARCHIVE, "Network rate in bytes per second")
        cvars.Get("msg", "1", CVAR_USERINFO or CVAR_ARCHIVE, "Server message level")
        cvars.Get("hand", "0", CVAR_USERINFO or CVAR_ARCHIVE, "Weapon handedness")
        cvars.Get("fov", "90", CVAR_USERINFO or CVAR_ARCHIVE, "Player field of view")
        cvars.Get("gender", "male", CVAR_USERINFO or CVAR_ARCHIVE, "Player gender")
    }

    private fun registerClient() {
        val cvars = Cvar.getInstance()
        cvars.Get("rcon_password", "asdf", 0, "Remote console password")
        cvars.Get("rcon_address", "127.0.0.1", 0, "Remote console target address")
        cvars.Get("cl_vwep", "1", CVAR_ARCHIVE, "Draw other players' weapon models")
        cvars.Get("cl_debug_stufftext", "0", CVAR_ARCHIVE, "Log raw server stufftext commands")

        cvars.AddAlias("sensitivity", "in_sensitivity")
        cvars.Get("in_sensitivity", "80", CVAR_ARCHIVE or CVAR_OPTIONS, "Mouse sensitivity")
        cvars.Get("in_invert_mouse", "0", CVAR_ARCHIVE or CVAR_OPTIONS, "Invert mouse Y axis")
        cvars.Get("cl_run", "0", CVAR_ARCHIVE or CVAR_OPTIONS, "Always run by default")

        cvars.AddAlias("crosshair", "cl_crosshair")
        cvars.Get("cl_crosshair", "1", CVAR_ARCHIVE or CVAR_OPTIONS, "Crosshair preset")
        cvars.Get("cl_showfps", "0", CVAR_ARCHIVE or CVAR_OPTIONS, "FPS overlay mode")
        cvars.Get("s_volume", "0.7", CVAR_ARCHIVE or CVAR_OPTIONS, "Effects volume")

        RenderTuningCvars.register()
    }

    private fun registerDownloads() {
        val cvars = Cvar.getInstance()
        cvars.Get("allow_download", "1", CVAR_ARCHIVE, "Allow downloading missing game content from servers")
        cvars.Get("allow_download_maps", "1", CVAR_ARCHIVE, "Allow downloading missing maps from servers")
        cvars.Get("allow_download_models", "1", CVAR_ARCHIVE, "Allow downloading missing models from servers")
        cvars.Get("allow_download_sounds", "1", CVAR_ARCHIVE, "Allow downloading missing sounds from servers")
        cvars.Get("allow_download_players", "0", CVAR_ARCHIVE, "Allow downloading missing player models and skins from servers")
    }
}
