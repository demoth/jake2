package org.demoth.cake

import com.badlogic.gdx.graphics.glutils.HdpiMode
import jake2.qcommon.Defines.CVAR_ARCHIVE
import jake2.qcommon.Defines.CVAR_LATCH
import jake2.qcommon.Defines.CVAR_NOSET
import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.Defines.CVAR_USERINFO
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import org.demoth.cake.stages.DebugGraphStage
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
        cvars.register("vid_fullscreen", CvarTypes.BOOLEAN, false, flags, "Fullscreen: 0 windowed, 1 fullscreen (restart required)")
        cvars.register(
            "vid_hidpi", CvarTypes.enumeration<HdpiMode>(), HdpiMode.Pixels, CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH,
            "Coordinate units: Pixels or Logical; differs when OS window and framebuffer sizes differ (restart required)"
        )
        cvars.register("vid_windowed_width", CvarTypes.IntegerType(min = 1), 1024, flags, "Windowed width in OS window coordinates (restart required)")
        cvars.register("vid_windowed_height", CvarTypes.IntegerType(min = 1), 768, flags, "Windowed height in OS window coordinates (restart required)")
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
        cvars.register("vid_depthbits", CvarTypes.ChoiceType(listOf(16, 24, 32)), 24, flags, "Requested scene/window depth bits: 16, 24, 32 (restart required)")
        cvars.register("vid_samples", CvarTypes.ChoiceType(listOf(0, 2, 4, 8)), 4, flags, "Requested scene/window MSAA samples: 0, 2, 4, 8 (restart required)")
        cvars.register("vid_vsync", CvarTypes.BOOLEAN, true, flags, "Vertical sync (restart required)")
    }

    private fun registerUserInfo(cvars: Cvar) {
        cvars.register("password", CvarTypes.STRING, "", CVAR_USERINFO or CVAR_ARCHIVE, "Server password")
        cvars.register("spectator", CvarTypes.STRING, "0", CVAR_USERINFO, "Spectator request: 0 to play, 1 or spectator password to spectate")
        cvars.register("name", CvarTypes.STRING, "unnamed", CVAR_USERINFO or CVAR_ARCHIVE, "Player name")
        cvars.register("skin", CvarTypes.STRING, "male/grunt", CVAR_USERINFO or CVAR_ARCHIVE, "Player model and skin")
        cvars.register("rate", CvarTypes.INTEGER, 25000, CVAR_USERINFO or CVAR_ARCHIVE, "Network rate in bytes per second")
        cvars.register("msg", CvarTypes.INTEGER, 1, CVAR_USERINFO or CVAR_ARCHIVE, "Server message level")
        cvars.register("hand", CvarTypes.ChoiceType(listOf(0, 1, 2)), 0, CVAR_USERINFO or CVAR_ARCHIVE, "Weapon handedness")
        cvars.register("fov", CvarTypes.IntegerType(1, 160), 90, CVAR_USERINFO or CVAR_ARCHIVE, "Player field of view")
        cvars.register("gender", CvarTypes.STRING, "male", CVAR_USERINFO or CVAR_ARCHIVE, "Player gender")
    }

    private fun registerClient(cvars: Cvar) {
        cvars.register("rcon_password", CvarTypes.STRING, "asdf", 0, "Remote console password")
        cvars.register("rcon_address", CvarTypes.STRING, "127.0.0.1", 0, "Remote console target address")
        cvars.register("cl_debug_stufftext", CvarTypes.BOOLEAN, false, CVAR_ARCHIVE, "Log raw server stufftext commands")

        registerInput(cvars)

        registerHud(cvars)
        registerAudio(cvars)
        registerAssets(cvars)
        registerEntities(cvars)
        DebugGraphStage.metricDefinitions.forEach { metric ->
            cvars.register(metric.name, CvarTypes.BOOLEAN, false, 0, metric.description)
        }
        cvars.register("cl_maxfps", CvarTypes.FloatType(10f, 125f), 62f, CVAR_ARCHIVE or CVAR_OPTIONS, "Client command and update cadence")

        RenderTuningCvars.register()
    }

    fun registerHud(cvars: Cvar) {
        cvars.AddAlias("crosshair", "cl_crosshair")
        cvars.register("cl_crosshair", CvarTypes.ChoiceType(listOf(0, 1, 2, 3)), 1, CVAR_ARCHIVE or CVAR_OPTIONS, "Crosshair preset")
        cvars.register("cl_showfps", CvarTypes.ChoiceType(listOf(0, 1, 2, 3)), 0, CVAR_ARCHIVE or CVAR_OPTIONS, "FPS overlay mode")
        cvars.register("cl_ui_size", CvarTypes.FloatType(min = 640f), 720f, CVAR_ARCHIVE or CVAR_OPTIONS,
            "Virtual game UI reference height (minimum 640); lower values enlarge menus and HUD; applies immediately")
    }

    fun registerInput(cvars: Cvar = Cvar.getInstance()) {
        cvars.AddAlias("sensitivity", "in_sensitivity")
        cvars.register("in_sensitivity", CvarTypes.FLOAT, 80f, CVAR_ARCHIVE or CVAR_OPTIONS, "Mouse sensitivity")
        cvars.register(
            "in_invert_mouse", CvarTypes.BOOLEAN, false,
            CVAR_ARCHIVE or CVAR_OPTIONS, "Invert mouse Y axis",
        )
        cvars.register("cl_run", CvarTypes.BOOLEAN, false, CVAR_ARCHIVE or CVAR_OPTIONS, "Always run by default")
        // Protocol byte packing remains at the move-command boundary.
        cvars.register("r_lightlevel", CvarTypes.IntegerType(0, 255), 150, 0, "Light level sent with move commands") // TODO: verify a proper server side fix
    }

    fun registerDownloads(cvars: Cvar) {
        cvars.register("allow_download", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Allow downloading missing game content from servers")
        cvars.register("allow_download_maps", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Allow downloading missing maps from servers")
        cvars.register("allow_download_models", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Allow downloading missing models from servers")
        cvars.register("allow_download_sounds", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Allow downloading missing sounds from servers")
        cvars.register("allow_download_players", CvarTypes.BOOLEAN, false, CVAR_ARCHIVE, "Allow downloading missing player models and skins from servers")
    }

    fun registerAudio(cvars: Cvar = Cvar.getInstance()) {
        cvars.register("s_volume", CvarTypes.FloatType(0f, 1f), 0.7f, CVAR_ARCHIVE or CVAR_OPTIONS, "Effects volume")
        cvars.register("s_maxvoices", CvarTypes.IntegerType(1, 128), 32, CVAR_ARCHIVE or CVAR_OPTIONS,
            "Maximum simultaneous Cake gameplay voices")
    }

    fun registerAssets(cvars: Cvar = Cvar.getInstance()) {
        cvars.register("fs_debug_loaders", CvarTypes.BOOLEAN, false, CVAR_ARCHIVE, "Log asset loader fallback decisions")
    }

    internal fun registerEntities(cvars: Cvar = Cvar.getInstance()) {
        cvars.register("r_drawsky", CvarTypes.BOOLEAN, true, 0, "Draw the sky layer")
        cvars.register("r_drawbeams", CvarTypes.BOOLEAN, true, 0, "Draw beam effects")
        cvars.register("r_drawsprites", CvarTypes.BOOLEAN, true, 0, "Draw sprite entities")
        cvars.register("r_drawentities", CvarTypes.BOOLEAN, true, 0, "Draw model entities")
        cvars.register("cl_gun", CvarTypes.BOOLEAN, true, 0, "Draw the view weapon model")
        cvars.register("cl_vwep", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Draw other players' weapon models")
        cvars.register("r_debug", CvarTypes.BOOLEAN, false, 0, "Show debug origin marker")
    }
}
