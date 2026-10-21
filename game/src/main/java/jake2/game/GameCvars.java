package jake2.game;

import jake2.qcommon.Defines;
import jake2.qcommon.GameImports;
import jake2.qcommon.Globals;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.exec.CvarTypes;
import java.util.List;

/*
 * "Cache" of cvars so we don't lookup them each frame
 */
public class GameCvars {
    public final cvar_t<Boolean> deathmatch;
    public final cvar_t<Float> sv_gravity;
    public final cvar_t<Boolean> sv_cheats;
    public final cvar_t<Integer> maxspectators;
    public final cvar_t<Boolean> coop;
    public final cvar_t<Integer> skill;
    public final cvar_t<Integer> dmflags;
    public final cvar_t<Integer> fraglimit;
    public final cvar_t<Float> timelimit;
    public final cvar_t<String> password;
    public final cvar_t<String> spectator_password;
    public final cvar_t<Integer> needpass;
    public final cvar_t<Boolean> filterban;
    public final cvar_t<Boolean> g_select_empty;
    public final cvar_t<Integer> flood_msgs;
    public final cvar_t<Float> flood_persecond;
    public final cvar_t<Float> flood_waitdelay;
    public final cvar_t<String> sv_maplist;

    public final cvar_t<Boolean> dedicated;
    public final cvar_t<Boolean> dev_blaster;
    public final cvar_t<Integer> maxclients;
    public final cvar_t<Integer> maxentities;

    public GameCvars(GameImports gameImports) {
        ///////////////////////////////////
        // Initialize game related cvars
        ///////////////////////////////////
        sv_gravity = gameImports.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, "Downward acceleration in game units per second squared; negative reverses gravity");
        maxspectators = gameImports.registerCvar("maxspectators", new CvarTypes.IntegerType(0, Defines.MAX_CLIENTS), 4, Defines.CVAR_SERVERINFO, "Maximum spectators in this game instance");

        // latched vars
        deathmatch = gameImports.findCvar("deathmatch", Boolean.class);
        sv_cheats = gameImports.findCvar("cheats", Boolean.class);
        gameImports.registerCvar("gamename", CvarTypes.STRING, Defines.GAMEVERSION, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH, null);
        gameImports.registerCvar("gamedate", CvarTypes.STRING, Globals.__DATE__, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH, null);
        coop = gameImports.findCvar("coop", Boolean.class);

        skill = gameImports.registerCvar("skill", new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3)), 0, Defines.CVAR_LATCH, "Difficulty: 0 easy, 1 medium, 2 hard, 3 nightmare");

        // change anytime vars
        dmflags = gameImports.findCvar("dmflags", Integer.class);
        fraglimit = gameImports.findCvar("fraglimit", Integer.class);
        timelimit = gameImports.findCvar("timelimit", Float.class);
        password = gameImports.findCvar("password", String.class);
        spectator_password = gameImports.findCvar("spectator_password", String.class);
        needpass = gameImports.findCvar("needpass", Integer.class);
        filterban = gameImports.registerCvar("filterban", CvarTypes.BOOLEAN, true, 0, "True blocks listed IP addresses; false allows only listed addresses");

        g_select_empty = gameImports.registerCvar("g_select_empty", CvarTypes.BOOLEAN, false, Defines.CVAR_ARCHIVE, "Allow selecting weapons with no ammunition");

        // flood control
        flood_msgs = gameImports.registerCvar("flood_msgs", new CvarTypes.IntegerType(0, 10), 4, 0, "Chat messages allowed per flood window; 0 disables flood protection");
        flood_persecond = gameImports.registerCvar("flood_persecond", new CvarTypes.FloatType(0f, null), 4f, 0, "Chat flood window in seconds");
        flood_waitdelay = gameImports.registerCvar("flood_waitdelay", new CvarTypes.FloatType(0f, null), 10f, 0, "Seconds to block chat after flooding");

        dedicated = gameImports.findCvar("dedicated", Boolean.class);
        maxclients = gameImports.findCvar("maxclients", Integer.class);
        maxentities = gameImports.registerCvar("maxentities", new CvarTypes.IntegerType(GameDefines.BODY_QUEUE_SIZE + 2, Defines.MAX_EDICTS),
                Defines.MAX_EDICTS, Defines.CVAR_LATCH, "Entity slots including world, clients and body queue");
        dev_blaster = gameImports.registerCvar("dev_blaster", CvarTypes.BOOLEAN, false, 0, "Show hit entity details instead of normal blaster impact handling");

        // dm map list
        sv_maplist = gameImports.registerCvar("sv_maplist", CvarTypes.STRING, "", 0, "Deathmatch map rotation; separate names with spaces, commas or newlines");

    }
}
