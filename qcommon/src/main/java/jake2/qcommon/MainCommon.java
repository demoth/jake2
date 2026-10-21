package jake2.qcommon;

import jake2.qcommon.util.Vargs;

import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarTypes;

import static jake2.qcommon.Defines.*;

/**
 * Common procedures for main functions
 */
@SuppressWarnings("deprecation")
public class MainCommon {
    /** Process-wide declarations, before config loading or subsystem construction. */
    public static void registerCvars(boolean dedicatedMode) {
        Cvar cvars = Cvar.getInstance();
        Globals.dedicated = cvars.register("dedicated", CvarTypes.BOOLEAN, dedicatedMode, CVAR_NOSET);
        Globals.developer = cvars.register("developer", CvarTypes.BOOLEAN, false, CVAR_ARCHIVE, "Print developer diagnostics");
        Globals.host_speeds = cvars.register("host_speeds", CvarTypes.BOOLEAN, false, 0, "Legacy frame timing diagnostics; currently unused");
        Globals.showtrace = cvars.register("showtrace", CvarTypes.BOOLEAN, false, 0, "Print collision trace, point-content and brush-test counts per frame");
        Globals.fixedtime = cvars.register("fixedtime", new CvarTypes.IntegerType(0, null), 0, 0, "Override frame duration in milliseconds; 0 disables the override");
        Globals.timescale = cvars.register("timescale", new CvarTypes.FloatType(0f, null), 0f, 0, "Multiply elapsed frame time; 0 disables scaling, fixedtime takes precedence");
        cvars.register("version", CvarTypes.STRING, "1.0.0", CVAR_SERVERINFO | CVAR_NOSET);
        cvars.register("cddir", CvarTypes.STRING, "", CVAR_ARCHIVE, "Legacy soundtrack directory; unused by Cake");
        cvars.register("map_noareas", CvarTypes.BOOLEAN, false, 0, "Ignore area portals and treat all map areas as connected for debugging");
        cvars.register("flushmap", CvarTypes.BOOLEAN, false, 0, "Reload collision map data even when loading the same server map");
        cvars.register("rcon_password", CvarTypes.STRING, "", 0, "Remote console password; empty disables remote console access");
        cvars.register("password", CvarTypes.STRING, "", CVAR_USERINFO | CVAR_ARCHIVE, "Server password");
        registerDownloads(cvars);
    }

    /** Shared download policy used by both the client and server. */
    public static void registerDownloads(Cvar cvars) {
        cvars.register("allow_download", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Allow client downloads and server file transfers");
        cvars.register("allow_download_maps", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Allow map downloads and server file transfers");
        cvars.register("allow_download_models", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Allow model downloads and server file transfers");
        cvars.register("allow_download_sounds", CvarTypes.BOOLEAN, true, CVAR_ARCHIVE, "Allow sound downloads and server file transfers");
        cvars.register("allow_download_players", CvarTypes.BOOLEAN, false, CVAR_ARCHIVE, "Allow player model and skin downloads and server file transfers");
    }

    public static void debugLogTraces() {
        if (Globals.showtrace.getTypedValue()) {
            Com.Printf("%4i traces  %4i points\n", new Vargs(2).add(Globals.c_traces).add(Globals.c_pointcontents));
            Globals.c_traces= 0;
            Globals.c_brush_traces= 0;
            Globals.c_pointcontents= 0;
        }
    }

    public static int adjustTime(int msec) {
        if (Globals.fixedtime.getTypedValue() != 0) {
            msec= Globals.fixedtime.getTypedValue();
        } else if (Globals.timescale.getTypedValue() != 0.0f) {
            msec *= Globals.timescale.getTypedValue();
            if (msec < 1)
                msec = 1;
        }
        return msec;
    }

}
