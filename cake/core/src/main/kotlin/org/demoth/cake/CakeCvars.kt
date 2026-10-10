package org.demoth.cake

import com.badlogic.gdx.graphics.glutils.HdpiMode
import jake2.qcommon.Defines.CVAR_ARCHIVE
import jake2.qcommon.Defines.CVAR_LATCH
import jake2.qcommon.Defines.CVAR_NOSET
import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.Defines.CVAR_USERINFO
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import org.demoth.cake.stages.ingame.RenderTuningCvars

object CakeCvars {
    fun registerAll() {
        val registry = Cvar.getInstance()
        registerUserInfo(registry)
        registerClient(registry)
        registerDownloads(registry)
        registerVideoMode(registry)
    }

    fun registerVideoMode(cvars: Cvar) {
        val flags = CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH
        cvars.Get("vid_fullscreen", "0", flags, "Fullscreen: 0 windowed, 1 fullscreen (restart required)")
        cvars.register(
            "vid_hidpi", CvarTypes.enumeration<HdpiMode>(), HdpiMode.Pixels, CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH,
            "Coordinate units: Pixels or Logical; differs when OS window and framebuffer sizes differ (restart required)"
        )
        cvars.Get("vid_windowed_width", "1024", flags, "Windowed width in OS window coordinates (restart required)")
        cvars.Get("vid_windowed_height", "768", flags, "Windowed height in OS window coordinates (restart required)")
        cvars.register(
            "vid_fullscreen_modes", CvarTypes.STRING, "", CVAR_NOSET,
            "Supported primary-monitor modes; populated by the desktop launcher",
        )
        cvars.register(
            "vid_fullscreen_mode",
            CvarTypes.StringType("desktop|[1-9][0-9]*x[1-9][0-9]*@[1-9][0-9]*"),
            "desktop", flags,
            "Fullscreen mode: desktop or WIDTHxHEIGHT@HZ; see vid_fullscreen_modes (restart required)",
        )
        cvars.Get("vid_depthbits", "24", flags, "Requested scene/window depth bits: 16, 24, 32 (restart required)")
        cvars.Get("vid_samples", "4", flags, "Requested scene/window MSAA samples: 0, 2, 4, 8 (restart required)")
        cvars.Get("vid_vsync", "1", flags, "Vertical sync (restart required)")
    }

    private fun registerUserInfo(cvars: Cvar) {
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

    private fun registerClient(cvars: Cvar) {
        cvars.Get("rcon_password", "asdf", 0, "Remote console password")
        cvars.Get("rcon_address", "127.0.0.1", 0, "Remote console target address")
        cvars.Get("cl_vwep", "1", CVAR_ARCHIVE, "Draw other players' weapon models")
        cvars.Get("cl_debug_stufftext", "0", CVAR_ARCHIVE, "Log raw server stufftext commands")

        registerInput(cvars)

        cvars.AddAlias("crosshair", "cl_crosshair")
        cvars.Get("cl_crosshair", "1", CVAR_ARCHIVE or CVAR_OPTIONS, "Crosshair preset")
        cvars.Get("cl_showfps", "0", CVAR_ARCHIVE or CVAR_OPTIONS, "FPS overlay mode")
        cvars.Get("cl_ui_size", "720", CVAR_ARCHIVE or CVAR_OPTIONS,
            "Virtual game UI reference height (minimum 640); lower values enlarge menus and HUD; applies immediately")
        cvars.Get("s_volume", "0.7", CVAR_ARCHIVE or CVAR_OPTIONS, "Effects volume")
        cvars.Get("cl_maxfps", "62", CVAR_ARCHIVE or CVAR_OPTIONS, "Client command and update cadence")

        RenderTuningCvars.register()
    }

    fun registerInput(cvars: Cvar = Cvar.getInstance()) {
        cvars.AddAlias("sensitivity", "in_sensitivity")
        cvars.Get("in_sensitivity", "80", CVAR_ARCHIVE or CVAR_OPTIONS, "Mouse sensitivity")
        cvars.register(
            "in_invert_mouse", CvarTypes.BOOLEAN, false,
            CVAR_ARCHIVE or CVAR_OPTIONS, "Invert mouse Y axis",
        )
        cvars.Get("cl_run", "0", CVAR_ARCHIVE or CVAR_OPTIONS, "Always run by default")
    }

    private fun registerDownloads(cvars: Cvar) {
        cvars.Get("allow_download", "1", CVAR_ARCHIVE, "Allow downloading missing game content from servers")
        cvars.Get("allow_download_maps", "1", CVAR_ARCHIVE, "Allow downloading missing maps from servers")
        cvars.Get("allow_download_models", "1", CVAR_ARCHIVE, "Allow downloading missing models from servers")
        cvars.Get("allow_download_sounds", "1", CVAR_ARCHIVE, "Allow downloading missing sounds from servers")
        cvars.Get("allow_download_players", "0", CVAR_ARCHIVE, "Allow downloading missing player models and skins from servers")
    }
}
