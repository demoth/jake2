package jake2.game;

import jake2.qcommon.Defines;
import jake2.qcommon.GameImports;
import jake2.qcommon.Globals;
import jake2.qcommon.exec.cvar_t;

/*
 * "Cache" of cvars so we don't lookup them each frame
 */
public class GameCvars {
    public final cvar_t deathmatch;
    public final cvar_t sv_gravity;
    public final cvar_t sv_cheats;
    public final cvar_t maxspectators;
    public final cvar_t coop;
    public final cvar_t skill;
    public final cvar_t dmflags;
    public final cvar_t fraglimit;
    public final cvar_t timelimit;
    public final cvar_t password;
    public final cvar_t spectator_password;
    public final cvar_t needpass;
    public final cvar_t filterban; // todo boolean
    public final cvar_t g_select_empty;
    public final cvar_t flood_msgs;
    public final cvar_t flood_persecond;
    public final cvar_t flood_waitdelay;
    public final cvar_t sv_maplist;

    public GameCvars(GameImports gameImports) {
        ///////////////////////////////////
        // Initialize game related cvars
        ///////////////////////////////////
        sv_gravity = gameImports.cvar("sv_gravity", "800", 0);
        maxspectators = gameImports.cvar("maxspectators", "4", Defines.CVAR_SERVERINFO);

        // latched vars
        deathmatch = gameImports.cvar("deathmatch", "0", Defines.CVAR_LATCH);
        sv_cheats = gameImports.cvar("cheats", "0", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        gameImports.cvar("gamename", Defines.GAMEVERSION,Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        gameImports.cvar("gamedate", Globals.__DATE__, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        coop = gameImports.cvar("coop", "0", Defines.CVAR_LATCH);

        skill = gameImports.cvar("skill", "0", Defines.CVAR_LATCH);

        // change anytime vars
        dmflags = gameImports.cvar("dmflags", "0", Defines.CVAR_SERVERINFO);
        fraglimit = gameImports.cvar("fraglimit", "0", Defines.CVAR_SERVERINFO);
        timelimit = gameImports.cvar("timelimit", "0", Defines.CVAR_SERVERINFO);
        password = gameImports.cvar("password", "", Defines.CVAR_USERINFO);
        spectator_password = gameImports.cvar("spectator_password", "", Defines.CVAR_USERINFO);
        needpass = gameImports.cvar("needpass", "0", Defines.CVAR_SERVERINFO);
        filterban = gameImports.cvar("filterban", "1", 0);

        g_select_empty = gameImports.cvar("g_select_empty", "0", Defines.CVAR_ARCHIVE);

        // flood control
        flood_msgs = gameImports.cvar("flood_msgs", "4", 0);
        flood_persecond = gameImports.cvar("flood_persecond", "4", 0);
        flood_waitdelay = gameImports.cvar("flood_waitdelay", "10", 0);

        // dm map list
        sv_maplist = gameImports.cvar("sv_maplist", "", 0);

    }
}
