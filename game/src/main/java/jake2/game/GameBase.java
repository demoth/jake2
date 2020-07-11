/*
 Copyright (C) 1997-2001 Id Software, Inc.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  

 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 */

// Created on 30.11.2003 by RST.

// $Id: GameBase.java,v 1.13 2006-01-21 21:53:31 salomo Exp $

/** Father of all GameObjects. */

package jake2.game;

import jake2.qcommon.*;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.StringTokenizer;

public class GameBase {
    private static SV sv;
    static PlayerView playerView;
    static cplane_t dummyplane = new cplane_t();

    public static game_locals_t game = new game_locals_t();

    public static level_locals_t level = new level_locals_t();

    // todo inject the same way as for game exports
    public static GameImports gi;

    public static spawn_temp_t st = new spawn_temp_t();

    static int sm_meat_index;

    static int snd_fry;

    static int meansOfDeath;

    static int num_edicts;

    /**
     * entity with index = 0 is always the worldspawn.
     * entities with indices 1..maxclients are the players
     * then go other stuff
     */
    public static SubgameEntity[] g_edicts = new SubgameEntity[Defines.MAX_EDICTS];
    static {
        for (int n = 0; n < Defines.MAX_EDICTS; n++)
            g_edicts[n] = new SubgameEntity(n);
    }

    public static cvar_t deathmatch = new cvar_t();

    public static cvar_t coop = new cvar_t();

    static cvar_t dmflags = new cvar_t();

    public static cvar_t skill; // = new cvar_t();

    private static cvar_t fraglimit = new cvar_t();

    private static cvar_t timelimit = new cvar_t();

    private static cvar_t password = new cvar_t();

    static cvar_t spectator_password = new cvar_t();

    private static cvar_t needpass = new cvar_t();

    static cvar_t maxspectators = new cvar_t();

    static cvar_t g_select_empty = new cvar_t();

    static cvar_t filterban = new cvar_t();

    static cvar_t sv_gravity = new cvar_t();

    static cvar_t sv_cheats = new cvar_t();

    static cvar_t flood_msgs = new cvar_t();

    static cvar_t flood_persecond = new cvar_t();

    static cvar_t flood_waitdelay = new cvar_t();

    private static cvar_t sv_maplist = new cvar_t();

    private final static float STOP_EPSILON = 0.1f;

    static void Init(GameImports gameImports) {

        playerView = new PlayerView(gameImports);

        sv = new SV(gameImports);

        gi = gameImports;
        ///////////////////////////////////
        // Initialize game related cvars
        ///////////////////////////////////
        sv_gravity = gameImports.cvar("sv_gravity", "800", 0);
        // latched vars
        sv_cheats = gameImports.cvar("cheats", "0", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        gameImports.cvar("gamename", Defines.GAMEVERSION,Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        gameImports.cvar("gamedate", Globals.__DATE__, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);

        maxspectators = gameImports.cvar("maxspectators", "4", Defines.CVAR_SERVERINFO);
        deathmatch = gameImports.cvar("deathmatch", "0", Defines.CVAR_LATCH);
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

        game.helpmessage1 = "";
        game.helpmessage2 = "";

        // items
        game.num_items = GameItemList.itemlist.length - 1;

        CreateEdicts(gameImports.cvar("maxentities", "1024", Defines.CVAR_LATCH).value);
        CreateClients(gameImports.cvar("maxclients", "4", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH).value);
    }

    // create the entities array and fill it with empty entities
    static void CreateEdicts(float max) {
        // initialize all entities for this game
        game.maxentities = (int) max;
        g_edicts = new SubgameEntity[game.maxentities];
        for (int i = 0; i < game.maxentities; i++)
            g_edicts[i] = new SubgameEntity(i);
    }

    // create the clients array and fill it with empty clients
    private static void CreateClients(float max) {
        // initialize all clients for this game
        game.maxclients = (int) max;

        game.clients = new gclient_t[game.maxclients];

        for (int i = 0; i < game.maxclients; i++)
            game.clients[i] = new gclient_t(i);

        // so far we have only clients, no other entities
        num_edicts = game.maxclients + 1;

    }

    /**
     * Slide off of the impacting object returns the blocked flags (1 = floor, 2 =
     * step / wall).
     */
    static int ClipVelocity(float[] in, float[] normal, float[] out,
                            float overbounce) {
        float backoff;
        float change;
        int i, blocked;

        blocked = 0;
        if (normal[2] > 0)
            blocked |= 1; // floor
        if (normal[2] == 0.0f)
            blocked |= 2; // step

        backoff = Math3D.DotProduct(in, normal) * overbounce;

        for (i = 0; i < 3; i++) {
            change = normal[i] * backoff;
            out[i] = in[i] - change;
            if (out[i] > -STOP_EPSILON && out[i] < STOP_EPSILON)
                out[i] = 0;
        }

        return blocked;
    }


    /**
     * Searches all active entities for the next one that holds the matching
     * string at fieldofs (use the FOFS() macro) in the structure.
     * 
     * Searches beginning at the edict after from, or the beginning if null null
     * will be returned if the end of the list is reached.
     * 
     */
    static EdictIterator G_Find(EdictIterator from, EdictFindFilter eff,
                                String s) {

        if (from == null)
            from = new EdictIterator(0);
        else
            from.i++;

        for (; from.i < num_edicts; from.i++) {
            from.o = g_edicts[from.i];
            if (from.o.classname == null) {
                gi.dprintf("edict with classname = null" + from.o.index);
            }

            if (!from.o.inuse)
                continue;

            if (eff.matches(from.o, s))
                return from;
        }

        return null;
    }

    // comfort version (rst)
    static edict_t G_FindEdict(EdictIterator from, EdictFindFilter eff,
                               String s) {
        EdictIterator ei = G_Find(from, eff, s);
        if (ei == null)
            return null;
        else
            return ei.o;
    }

    /**
     * Returns entities that have origins within a spherical area.
     */
    public static EdictIterator findradius(EdictIterator from, float[] org,
            float rad) {
        float[] eorg = { 0, 0, 0 };
        int j;

        if (from == null)
            from = new EdictIterator(0);
        else
            from.i++;

        for (; from.i < num_edicts; from.i++) {
            from.o = g_edicts[from.i];
            if (!from.o.inuse)
                continue;

            if (from.o.solid == Defines.SOLID_NOT)
                continue;

            for (j = 0; j < 3; j++)
                eorg[j] = org[j]
                        - (from.o.s.origin[j] + (from.o.mins[j] + from.o.maxs[j]) * 0.5f);

            if (Math3D.VectorLength(eorg) > rad)
                continue;
            return from;
        }

        return null;
    }

    /*
     * Searches all active entities for the next one that holds the matching
     * string at fieldofs (use the FOFS() macro) in the structure.
     *
     * Searches beginning at the edict after from, or the beginning if null null
     * will be returned if the end of the list is reached.
     */

    public static SubgameEntity G_PickTarget(String targetname) {
        int num_choices = 0;
        int MAXCHOICES = 8;
        SubgameEntity[] choice = new SubgameEntity[MAXCHOICES];

        if (targetname == null) {
            gi.dprintf("G_PickTarget called with null targetname\n");
            return null;
        }

        EdictIterator es = null;

        while ((es = G_Find(es, findByTarget, targetname)) != null) {
            choice[num_choices++] = es.o;
            if (num_choices == MAXCHOICES)
                break;
        }

        if (num_choices == 0) {
            gi.dprintf("G_PickTarget: target " + targetname + " not found\n");
            return null;
        }

        return choice[Lib.rand() % num_choices];
    }

    private static float[] VEC_UP = { 0, -1, 0 };

    private static float[] MOVEDIR_UP = { 0, 0, 1 };

    private static float[] VEC_DOWN = { 0, -2, 0 };

    private static float[] MOVEDIR_DOWN = { 0, 0, -1 };

    public static void G_SetMovedir(float[] angles, float[] movedir) {
        if (Math3D.VectorEquals(angles, VEC_UP)) {
            Math3D.VectorCopy(MOVEDIR_UP, movedir);
        } else if (Math3D.VectorEquals(angles, VEC_DOWN)) {
            Math3D.VectorCopy(MOVEDIR_DOWN, movedir);
        } else {
            Math3D.AngleVectors(angles, movedir, null, null);
        }

        Math3D.VectorClear(angles);
    }

    // todo: replace with returned collection from gi.BoxEdicts
    private static SubgameEntity touch[] = new SubgameEntity[Defines.MAX_EDICTS];

    static void G_TouchTriggers(SubgameEntity ent) {
        int i, num;
        SubgameEntity hit;

        // dead things don't activate triggers!
        if ((ent.getClient() != null || (ent.svflags & Defines.SVF_MONSTER) != 0)
                && (ent.health <= 0))
            return;

        num = gi.BoxEdicts(ent.absmin, ent.absmax, touch, Defines.MAX_EDICTS,
                Defines.AREA_TRIGGERS);

        // be careful, it is possible to have an entity in this
        // list removed before we get to it (killtriggered)
        for (i = 0; i < num; i++) {
            hit = touch[i];

            if (!hit.inuse)
                continue;

            if (hit.touch == null)
                continue;

            hit.touch.touch(hit, ent, dummyplane, null);
        }
    }

    static pushed_t pushed[] = new pushed_t[Defines.MAX_EDICTS];
    static {
        for (int n = 0; n < Defines.MAX_EDICTS; n++)
            pushed[n] = new pushed_t();
    }

    static int pushed_p;

    static SubgameEntity obstacle;

    static int c_yes, c_no;

    static int STEPSIZE = 18;

    /**
     * G_RunEntity
     */
    private static void G_RunEntity(SubgameEntity ent) {

        if (ent.prethink != null)
            ent.prethink.think(ent);

        switch (ent.movetype) {
        case GameDefines.MOVETYPE_PUSH:
        case GameDefines.MOVETYPE_STOP:
            SV.SV_Physics_Pusher(ent);
            break;
        case GameDefines.MOVETYPE_NONE:
            SV.SV_Physics_None(ent);
            break;
        case GameDefines.MOVETYPE_NOCLIP:
            SV.SV_Physics_Noclip(ent);
            break;
        case GameDefines.MOVETYPE_STEP:
            sv.SV_Physics_Step(ent);
            break;
        case GameDefines.MOVETYPE_TOSS:
        case GameDefines.MOVETYPE_BOUNCE:
        case GameDefines.MOVETYPE_FLY:
        case GameDefines.MOVETYPE_FLYMISSILE:
            sv.SV_Physics_Toss(ent);
            break;
        default:
            gi.error("SV_Physics: bad movetype " + (int) ent.movetype);
        }
    }

    static EdictFindFilter findByTarget = new EdictFindFilter() {
        public boolean matches(SubgameEntity e, String s) {
            if (e.targetname == null)
                return false;
            return e.targetname.equalsIgnoreCase(s);
        }
    };

    static EdictFindFilter findByClass = new EdictFindFilter() {
        public boolean matches(SubgameEntity e, String s) {
            return e.classname.equalsIgnoreCase(s);
        }
    };

    /**
     * ClientEndServerFrames.
     */
    private static void ClientEndServerFrames() {
        int i;
        SubgameEntity ent;

        // calc the player views now that all pushing
        // and damage has been added
        for (i = 0; i < game.maxclients; i++) {
            ent = g_edicts[1 + i];
            if (!ent.inuse || null == ent.getClient())
                continue;
            playerView.ClientEndServerFrame(ent);
        }

    }

    /**
     * Returns the created target changelevel.
     */
    private static SubgameEntity CreateTargetChangeLevel(String map) {
        SubgameEntity ent;

        ent = GameUtil.G_Spawn();
        ent.classname = "target_changelevel";
        level.nextmap = map;
        ent.map = level.nextmap;
        return ent;
    }

    /**
     * The timelimit or fraglimit has been exceeded.
     */
    private static void EndDMLevel() {
        //char * s, * t, * f;
        //static const char * seps = " ,\n\r";
        String seps = " ,\n\r";

        // stay on same level flag
        if (((int) dmflags.value & Defines.DF_SAME_LEVEL) != 0) {
            PlayerHud.BeginIntermission(CreateTargetChangeLevel(level.mapname));
            return;
        }

        // see if it's in the map list
        if (sv_maplist.string.length() > 0) {
            String s = sv_maplist.string;
            String f = null;
            StringTokenizer tk = new StringTokenizer(s, seps);
            
            while (tk.hasMoreTokens()){
                String t = tk.nextToken();

                // store first map
            	if (f == null)
            		f = t;
            	
                if (t.equalsIgnoreCase(level.mapname)) {
                    // it's in the list, go to the next one
                	if (!tk.hasMoreTokens()) {
                		// end of list, go to first one
                        if (f == null) // there isn't a first one, same level
                            PlayerHud.BeginIntermission(CreateTargetChangeLevel(level.mapname));
                        else
                            PlayerHud.BeginIntermission(CreateTargetChangeLevel(f));
                    } else
                        PlayerHud.BeginIntermission(CreateTargetChangeLevel(tk.nextToken()));
                    return;
                }
            }
        }

        //not in the map list
        if (level.nextmap.length() > 0) // go to a specific map
            PlayerHud.BeginIntermission(CreateTargetChangeLevel(level.nextmap));
        else { // search for a changelevel
            EdictIterator edit = null;
            edit = G_Find(edit, findByClass, "target_changelevel");
            if (edit == null) { // the map designer didn't include a
                                // changelevel,
                // so create a fake ent that goes back to the same level
                PlayerHud.BeginIntermission(CreateTargetChangeLevel(level.mapname));
                return;
            }
            SubgameEntity ent = edit.o;
            PlayerHud.BeginIntermission(ent);
        }
    }

    /**
     * CheckNeedPass.
     */
    private static void CheckNeedPass() {
        int need;

        // if password or spectator_password has changed, update needpass
        // as needed
        if (password.modified || spectator_password.modified) {
            password.modified = spectator_password.modified = false;

            need = 0;

            if ((password.string.length() > 0)
                    && 0 != Lib.Q_stricmp(password.string, "none"))
                need |= 1;
            if ((spectator_password.string.length() > 0)
                    && 0 != Lib.Q_stricmp(spectator_password.string, "none"))
                need |= 2;

            gi.cvar_set("needpass", "" + need);
        }
    }

    /**
     * CheckDMRules.
     */
    private static void CheckDMRules() {
        int i;
        gclient_t cl;

        if (level.intermissiontime != 0)
            return;

        if (0 == deathmatch.value)
            return;

        if (timelimit.value != 0) {
            if (level.time >= timelimit.value * 60) {
                gi.bprintf(Defines.PRINT_HIGH, "Timelimit hit.\n");
                EndDMLevel();
                return;
            }
        }

        if (fraglimit.value != 0) {
            for (i = 0; i < game.maxclients; i++) {
                cl = game.clients[i];
                if (!g_edicts[i + 1].inuse)
                    continue;

                if (cl.resp.score >= fraglimit.value) {
                    gi.bprintf(Defines.PRINT_HIGH, "Fraglimit hit.\n");
                    EndDMLevel();
                    return;
                }
            }
        }
    }

    /**
     * Exits a level.
     */
    private static void ExitLevel() {
        int i;
        SubgameEntity ent;

        String command = "gamemap \"" + level.changemap + "\"\n";
        gi.AddCommandString(command);
        level.changemap = null;
        level.exitintermission = false;
        level.intermissiontime = 0;
        ClientEndServerFrames();

        // clear some things before going to next level
        for (i = 0; i < game.maxclients; i++) {
            ent = g_edicts[1 + i];
            if (!ent.inuse)
                continue;
            gclient_t client = ent.getClient();
            if (ent.health > client.pers.max_health)
                ent.health = client.pers.max_health;
        }
    }

    static void G_RunFrame() {
        int i;
        SubgameEntity ent;

        level.framenum++;
        level.time = level.framenum * Defines.FRAMETIME;

        // choose a client for monsters to target this frame
        GameAI.AI_SetSightClient();

        // exit intermissions

        if (level.exitintermission) {
            ExitLevel();
            return;
        }

        //
        // treat each object in turn
        // even the world gets a chance to think
        //

        for (i = 0; i < num_edicts; i++) {
            ent = g_edicts[i];
            if (!ent.inuse)
                continue;

            level.current_entity = ent;

            Math3D.VectorCopy(ent.s.origin, ent.s.old_origin);

            // if the ground entity moved, make sure we are still on it
            if ((ent.groundentity != null)
                    && (ent.groundentity.linkcount != ent.groundentity_linkcount)) {
                ent.groundentity = null;
                if (0 == (ent.flags & (GameDefines.FL_SWIM | GameDefines.FL_FLY))
                        && (ent.svflags & Defines.SVF_MONSTER) != 0) {
                    M.M_CheckGround(ent);
                }
            }

            if (i > 0 && i <= game.maxclients) {
                PlayerClient.ClientBeginServerFrame(ent);
                continue;
            }

            G_RunEntity(ent);
        }

        // see if it is time to end a deathmatch
        CheckDMRules();

        // see if needpass needs updated
        CheckNeedPass();

        // build the playerstate_t structures for all players
        ClientEndServerFrames();
    }
}