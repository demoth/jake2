/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 18.11.2003 by RST.

package jake2.game;

import jake2.Defines;
import jake2.game.monsters.*;
import jake2.qcommon.Com;
import jake2.util.Lib;

public class GameSpawn {

    static EntThinkAdapter SP_item_health = new EntThinkAdapter() {
        public String getID(){ return "SP_item_health"; }
        public boolean think(edict_t ent) {
            GameItems.SP_item_health(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_item_health_small = new EntThinkAdapter() {
        public String getID(){ return "SP_item_health_small"; }
        public boolean think(edict_t ent) {
            GameItems.SP_item_health_small(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_item_health_large = new EntThinkAdapter() {
        public String getID(){ return "SP_item_health_large"; }
        public boolean think(edict_t ent) {
            GameItems.SP_item_health_large(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_item_health_mega = new EntThinkAdapter() {
        public String getID(){ return "SP_item_health_mega"; }
        public boolean think(edict_t ent) {
            GameItems.SP_item_health_mega(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_info_player_start = new EntThinkAdapter() {
        public String getID(){ return "SP_info_player_start"; }
        public boolean think(edict_t ent) {
            PlayerClient.SP_info_player_start(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_info_player_deathmatch = new EntThinkAdapter() {
        public String getID(){ return "SP_info_player_deathmatch"; }
        public boolean think(edict_t ent) {
            PlayerClient.SP_info_player_deathmatch(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_info_player_coop = new EntThinkAdapter() {
        public String getID(){ return "SP_info_player_coop"; }
        public boolean think(edict_t ent) {
            PlayerClient.SP_info_player_coop(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_info_player_intermission = new EntThinkAdapter() {
        public String getID(){ return "SP_info_player_intermission"; }
        public boolean think(edict_t ent) {
            PlayerClient.SP_info_player_intermission();
            return true;
        }
    };

    static EntThinkAdapter SP_func_plat = new EntThinkAdapter() {
        public String getID(){ return "SP_func_plat"; }
        public boolean think(edict_t ent) {
            GameFunc.SP_func_plat(ent);
            return true;
        }
    };


    static EntThinkAdapter SP_func_water = new EntThinkAdapter() {
        public String getID(){ return "SP_func_water"; }
        public boolean think(edict_t ent) {
            GameFunc.SP_func_water(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_func_train = new EntThinkAdapter() {
        public String getID(){ return "SP_func_train"; }
        public boolean think(edict_t ent) {
            GameFunc.SP_func_train(ent);
            return true;
        }
    };

    static EntThinkAdapter SP_func_clock = new EntThinkAdapter() {
        public String getID(){ return "SP_func_clock"; }
        public boolean think(edict_t ent) {
            GameMisc.SP_func_clock(ent);
            return true;
        }
    };

    /**
     * QUAKED worldspawn (0 0 0) ?
     * 
     * Only used for the world. "sky" environment map name "skyaxis" vector axis
     * for rotating sky "skyrotate" speed of rotation in degrees/second "sounds"
     * music cd track number "gravity" 800 is default gravity "message" text to
     * print at user logon
     */

    static EntThinkAdapter SP_worldspawn = new EntThinkAdapter() {
        public String getID(){ return "SP_worldspawn"; }

        public boolean think(edict_t ent) {
            ent.movetype = Defines.MOVETYPE_PUSH;
            ent.solid = Defines.SOLID_BSP;
            ent.inuse = true;
            // since the world doesn't use G_Spawn()
            ent.s.modelindex = 1;
            // world model is always index 1
            //---------------
            // reserve some spots for dead player bodies for coop / deathmatch
            PlayerClient.InitBodyQue();
            // set configstrings for items
            GameItems.SetItemNames();
            if (GameBase.st.nextmap != null)
                GameBase.level.nextmap = GameBase.st.nextmap;
            // make some data visible to the server
            if (ent.message != null && ent.message.length() > 0) {
                GameBase.gi.configstring(Defines.CS_NAME, ent.message);
                GameBase.level.level_name = ent.message;
            } else
                GameBase.level.level_name = GameBase.level.mapname;
            if (GameBase.st.sky != null && GameBase.st.sky.length() > 0)
                GameBase.gi.configstring(Defines.CS_SKY, GameBase.st.sky);
            else
                GameBase.gi.configstring(Defines.CS_SKY, "unit1_");
            GameBase.gi.configstring(Defines.CS_SKYROTATE, ""
                    + GameBase.st.skyrotate);
            GameBase.gi.configstring(Defines.CS_SKYAXIS, Lib
                    .vtos(GameBase.st.skyaxis));
            GameBase.gi.configstring(Defines.CS_CDTRACK, "" + ent.sounds);
            GameBase.gi.configstring(Defines.CS_MAXCLIENTS, ""
                    + (int) (GameBase.maxclients.value));
            // status bar program
            if (GameBase.deathmatch.value != 0)
                GameBase.gi.configstring(Defines.CS_STATUSBAR, "" + dm_statusbar);
            else
                GameBase.gi.configstring(Defines.CS_STATUSBAR, "" + single_statusbar);
            //---------------
            // help icon for statusbar
            GameBase.gi.imageindex("i_help");
            GameBase.level.pic_health = GameBase.gi.imageindex("i_health");
            GameBase.gi.imageindex("help");
            GameBase.gi.imageindex("field_3");
            if ("".equals(GameBase.st.gravity))
                GameBase.gi.cvar_set("sv_gravity", "800");
            else
                GameBase.gi.cvar_set("sv_gravity", GameBase.st.gravity);
            GameBase.snd_fry = GameBase.gi.soundindex("player/fry.wav");
            // standing in lava / slime
            GameItems.PrecacheItem(GameItems.FindItem("Blaster"));
            GameBase.gi.soundindex("player/lava1.wav");
            GameBase.gi.soundindex("player/lava2.wav");
            GameBase.gi.soundindex("misc/pc_up.wav");
            GameBase.gi.soundindex("misc/talk1.wav");
            GameBase.gi.soundindex("misc/udeath.wav");
            // gibs
            GameBase.gi.soundindex("items/respawn1.wav");
            // sexed sounds
            GameBase.gi.soundindex("*death1.wav");
            GameBase.gi.soundindex("*death2.wav");
            GameBase.gi.soundindex("*death3.wav");
            GameBase.gi.soundindex("*death4.wav");
            GameBase.gi.soundindex("*fall1.wav");
            GameBase.gi.soundindex("*fall2.wav");
            GameBase.gi.soundindex("*gurp1.wav");
            // drowning damage
            GameBase.gi.soundindex("*gurp2.wav");
            GameBase.gi.soundindex("*jump1.wav");
            // player jump
            GameBase.gi.soundindex("*pain25_1.wav");
            GameBase.gi.soundindex("*pain25_2.wav");
            GameBase.gi.soundindex("*pain50_1.wav");
            GameBase.gi.soundindex("*pain50_2.wav");
            GameBase.gi.soundindex("*pain75_1.wav");
            GameBase.gi.soundindex("*pain75_2.wav");
            GameBase.gi.soundindex("*pain100_1.wav");
            GameBase.gi.soundindex("*pain100_2.wav");
            // sexed models
            // THIS ORDER MUST MATCH THE DEFINES IN g_local.h
            // you can add more, max 15
            GameBase.gi.modelindex("#w_blaster.md2");
            GameBase.gi.modelindex("#w_shotgun.md2");
            GameBase.gi.modelindex("#w_sshotgun.md2");
            GameBase.gi.modelindex("#w_machinegun.md2");
            GameBase.gi.modelindex("#w_chaingun.md2");
            GameBase.gi.modelindex("#a_grenades.md2");
            GameBase.gi.modelindex("#w_glauncher.md2");
            GameBase.gi.modelindex("#w_rlauncher.md2");
            GameBase.gi.modelindex("#w_hyperblaster.md2");
            GameBase.gi.modelindex("#w_railgun.md2");
            GameBase.gi.modelindex("#w_bfg.md2");
            //-------------------
            GameBase.gi.soundindex("player/gasp1.wav");
            // gasping for air
            GameBase.gi.soundindex("player/gasp2.wav");
            // head breaking surface, not gasping
            GameBase.gi.soundindex("player/watr_in.wav");
            // feet hitting water
            GameBase.gi.soundindex("player/watr_out.wav");
            // feet leaving water
            GameBase.gi.soundindex("player/watr_un.wav");
            // head going underwater
            GameBase.gi.soundindex("player/u_breath1.wav");
            GameBase.gi.soundindex("player/u_breath2.wav");
            GameBase.gi.soundindex("items/pkup.wav");
            // bonus item pickup
            GameBase.gi.soundindex("world/land.wav");
            // landing thud
            GameBase.gi.soundindex("misc/h2ohit1.wav");
            // landing splash
            GameBase.gi.soundindex("items/damage.wav");
            GameBase.gi.soundindex("items/protect.wav");
            GameBase.gi.soundindex("items/protect4.wav");
            GameBase.gi.soundindex("weapons/noammo.wav");
            GameBase.gi.soundindex("infantry/inflies1.wav");
            GameBase.sm_meat_index = GameBase.gi
                    .modelindex("models/objects/gibs/sm_meat/tris.md2");
            GameBase.gi.modelindex("models/objects/gibs/arm/tris.md2");
            GameBase.gi.modelindex("models/objects/gibs/bone/tris.md2");
            GameBase.gi.modelindex("models/objects/gibs/bone2/tris.md2");
            GameBase.gi.modelindex("models/objects/gibs/chest/tris.md2");
            GameBase.gi.modelindex("models/objects/gibs/skull/tris.md2");
            GameBase.gi.modelindex("models/objects/gibs/head2/tris.md2");
            //
            // Setup light animation tables. 'a' is total darkness, 'z' is
            // doublebright.
            //
            // 0 normal
            GameBase.gi.configstring(Defines.CS_LIGHTS + 0, "m");
            // 1 FLICKER (first variety)
            GameBase.gi.configstring(Defines.CS_LIGHTS + 1,
                    "mmnmmommommnonmmonqnmmo");
            // 2 SLOW STRONG PULSE
            GameBase.gi.configstring(Defines.CS_LIGHTS + 2,
                    "abcdefghijklmnopqrstuvwxyzyxwvutsrqponmlkjihgfedcba");
            // 3 CANDLE (first variety)
            GameBase.gi.configstring(Defines.CS_LIGHTS + 3,
                    "mmmmmaaaaammmmmaaaaaabcdefgabcdefg");
            // 4 FAST STROBE
            GameBase.gi.configstring(Defines.CS_LIGHTS + 4, "mamamamamama");
            // 5 GENTLE PULSE 1
            GameBase.gi.configstring(Defines.CS_LIGHTS + 5,
                    "jklmnopqrstuvwxyzyxwvutsrqponmlkj");
            // 6 FLICKER (second variety)
            GameBase.gi
                    .configstring(Defines.CS_LIGHTS + 6, "nmonqnmomnmomomno");
            // 7 CANDLE (second variety)
            GameBase.gi.configstring(Defines.CS_LIGHTS + 7,
                    "mmmaaaabcdefgmmmmaaaammmaamm");
            // 8 CANDLE (third variety)
            GameBase.gi.configstring(Defines.CS_LIGHTS + 8,
                    "mmmaaammmaaammmabcdefaaaammmmabcdefmmmaaaa");
            // 9 SLOW STROBE (fourth variety)
            GameBase.gi.configstring(Defines.CS_LIGHTS + 9, "aaaaaaaazzzzzzzz");
            // 10 FLUORESCENT FLICKER
            GameBase.gi.configstring(Defines.CS_LIGHTS + 10,
                    "mmamammmmammamamaaamammma");
            // 11 SLOW PULSE NOT FADE TO BLACK
            GameBase.gi.configstring(Defines.CS_LIGHTS + 11,
                    "abcdefghijklmnopqrrqponmlkjihgfedcba");
            // styles 32-62 are assigned by the light program for switchable
            // lights
            // 63 testing
            GameBase.gi.configstring(Defines.CS_LIGHTS + 63, "a");
            return true;
        }
    };

    /** 
     * ED_NewString.
     */
    static String ED_NewString(String string) {

        int l = string.length();
        StringBuffer newb = new StringBuffer(l);

        for (int i = 0; i < l; i++) {
            char c = string.charAt(i);
            if (c == '\\' && i < l - 1) {
                c = string.charAt(++i);
                if (c == 'n')
                    newb.append('\n');
                else
                    newb.append('\\');
            } else
                newb.append(c);
        }

        return newb.toString();
    }

    /**
     * ED_ParseField
     * 
     * Takes a key/value pair and sets the binary values in an edict.
     */
    static void ED_ParseField(String key, String value, edict_t ent) {

        if (key.equals("nextmap"))
            Com.Println("nextmap: " + value);
        if (!GameBase.st.set(key, value))
            if (!ent.setField(key, value))
                GameBase.gi.dprintf("??? The key [" + key
                        + "] is not a field\n");

    }

    /**
     * ED_ParseEdict
     * 
     * Parses an edict out of the given string, returning the new position ed
     * should be a properly initialized empty edict.
     */

    static void ED_ParseEdict(Com.ParseHelp ph, edict_t ent) {

        boolean init;
        String keyname;
        String com_token;
        init = false;

        GameBase.st = new spawn_temp_t();
        while (true) {

            // parse key
            com_token = Com.Parse(ph);
            if (com_token.equals("}"))
                break;

            if (ph.isEof())
                GameBase.gi.error("ED_ParseEntity: EOF without closing brace");

            keyname = com_token;

            // parse value
            com_token = Com.Parse(ph);

            if (ph.isEof())
                GameBase.gi.error("ED_ParseEntity: EOF without closing brace");

            if (com_token.equals("}"))
                GameBase.gi.error("ED_ParseEntity: closing brace without data");

            init = true;
            // keynames with a leading underscore are used for utility comments,
            // and are immediately discarded by quake
            if (keyname.charAt(0) == '_')
                continue;

            ED_ParseField(keyname.toLowerCase(), com_token, ent);

        }

        if (!init) {
            GameUtil.G_ClearEdict(ent);
        }

        return;
    }

    /**
     * G_FindTeams
     * 
     * Chain together all entities with a matching team field.
     * 
     * All but the first will have the FL_TEAMSLAVE flag set. All but the last
     * will have the teamchain field set to the next one.
     */

    static void G_FindTeams() {
        edict_t e, e2, chain;
        int i, j;
        int c, c2;
        c = 0;
        c2 = 0;
        for (i = 1; i < GameBase.num_edicts; i++) {
            e = GameBase.g_edicts[i];

            if (!e.inuse)
                continue;
            if (e.team == null)
                continue;
            if ((e.flags & Defines.FL_TEAMSLAVE) != 0)
                continue;
            chain = e;
            e.teammaster = e;
            c++;
            c2++;
            
            for (j = i + 1; j < GameBase.num_edicts; j++) {
                e2 = GameBase.g_edicts[j];
                if (!e2.inuse)
                    continue;
                if (null == e2.team)
                    continue;
                if ((e2.flags & Defines.FL_TEAMSLAVE) != 0)
                    continue;
                if (0 == Lib.strcmp(e.team, e2.team)) {
                    c2++;
                    chain.teamchain = e2;
                    e2.teammaster = e;
                    chain = e2;
                    e2.flags |= Defines.FL_TEAMSLAVE;

                }
            }
        }
    }

    /**
     * SpawnEntities
     * 
     * Creates a server's entity / program execution context by parsing textual
     * entity definitions out of an ent file.
     */

    public static void SpawnEntities(String mapname, String entities,
            String spawnpoint) {
        
        Com.dprintln("SpawnEntities(), mapname=" + mapname);
        edict_t ent;
        int inhibit;
        String com_token;
        int i;
        float skill_level;
        //skill.value =2.0f;
        skill_level = (float) Math.floor(GameBase.skill.value);

        if (skill_level < 0)
            skill_level = 0;
        if (skill_level > 3)
            skill_level = 3;
        if (GameBase.skill.value != skill_level)
            GameBase.gi.cvar_forceset("skill", "" + skill_level);

        PlayerClient.SaveClientData();

        GameBase.level = new level_locals_t();
        for (int n = 0; n < GameBase.game.maxentities; n++) {
            GameBase.g_edicts[n] = new edict_t(n);
        }
        
        GameBase.level.mapname = mapname;
        GameBase.game.spawnpoint = spawnpoint;

        // set client fields on player ents
        for (i = 0; i < GameBase.game.maxclients; i++)
            GameBase.g_edicts[i + 1].client = GameBase.game.clients[i];

        ent = null;
        inhibit = 0; 

        Com.ParseHelp ph = new Com.ParseHelp(entities);

        while (true) { // parse the opening brace

            com_token = Com.Parse(ph);
            if (ph.isEof())
                break;
            if (!com_token.startsWith("{"))
                GameBase.gi.error("ED_LoadFromFile: found " + com_token
                        + " when expecting {");

            if (ent == null)
                ent = GameBase.g_edicts[0];
            else
                ent = GameUtil.G_Spawn();

            ED_ParseEdict(ph, ent);
            Com.DPrintf("spawning ent[" + ent.index + "], classname=" + 
                    ent.classname + ", flags= " + Integer.toHexString(ent.spawnflags));
            
            // yet another map hack
            if (0 == Lib.Q_stricmp(GameBase.level.mapname, "command")
                    && 0 == Lib.Q_stricmp(ent.classname, "trigger_once")
                    && 0 == Lib.Q_stricmp(ent.model, "*27"))
                ent.spawnflags &= ~Defines.SPAWNFLAG_NOT_HARD;

            // remove things (except the world) from different skill levels or
            // deathmatch
            if (ent != GameBase.g_edicts[0]) {
                if (GameBase.deathmatch.value != 0) {
                    if ((ent.spawnflags & Defines.SPAWNFLAG_NOT_DEATHMATCH) != 0) {
                        
                        Com.DPrintf("->inhibited.\n");
                        GameUtil.G_FreeEdict(ent);
                        inhibit++;
                        continue;
                    }
                } else {
                    if (/*
                         * ((coop.value) && (ent.spawnflags &
                         * SPAWNFLAG_NOT_COOP)) ||
                         */
                    ((GameBase.skill.value == 0) && (ent.spawnflags & Defines.SPAWNFLAG_NOT_EASY) != 0)
                            || ((GameBase.skill.value == 1) && (ent.spawnflags & Defines.SPAWNFLAG_NOT_MEDIUM) != 0)
                            || (((GameBase.skill.value == 2) || (GameBase.skill.value == 3)) && (ent.spawnflags & Defines.SPAWNFLAG_NOT_HARD) != 0)) {
                        
                        Com.DPrintf("->inhibited.\n");
                        GameUtil.G_FreeEdict(ent);
                        inhibit++;
                        
                        continue;
                    }
                }

                ent.spawnflags &= ~(Defines.SPAWNFLAG_NOT_EASY
                        | Defines.SPAWNFLAG_NOT_MEDIUM
                        | Defines.SPAWNFLAG_NOT_HARD
                        | Defines.SPAWNFLAG_NOT_COOP | Defines.SPAWNFLAG_NOT_DEATHMATCH);
            }
            ED_CallSpawn(ent);
            Com.DPrintf("\n");
        }
        Com.DPrintf("player skill level:" + GameBase.skill.value + "\n");
        Com.DPrintf(inhibit + " entities inhibited.\n");
        i = 1;
        G_FindTeams();
        PlayerTrail.Init();
    }

    static String single_statusbar = "yb	-24 " //	   health
            + "xv	0 " + "hnum " + "xv	50 " + "pic 0 " //	   ammo
            + "if 2 " + "	xv	100 " + "	anum " + "	xv	150 " + "	pic 2 "
            + "endif " //	   armor
            + "if 4 " + "	xv	200 " + "	rnum " + "	xv	250 " + "	pic 4 "
            + "endif " //	   selected item
            + "if 6 " + "	xv	296 " + "	pic 6 " + "endif " + "yb	-50 " //	   picked
            // up
            // item
            + "if 7 " + "	xv	0 " + "	pic 7 " + "	xv	26 " + "	yb	-42 "
            + "	stat_string 8 " + "	yb	-50 " + "endif "
            //	   timer
            + "if 9 " + "	xv	262 " + "	num	2	10 " + "	xv	296 " + "	pic	9 "
            + "endif "
            //		help / weapon icon
            + "if 11 " + "	xv	148 " + "	pic	11 " + "endif ";

    static String dm_statusbar = "yb	-24 " //	   health
            + "xv	0 " + "hnum " + "xv	50 " + "pic 0 " //	   ammo
            + "if 2 " + "	xv	100 " + "	anum " + "	xv	150 " + "	pic 2 "
            + "endif " //	   armor
            + "if 4 " + "	xv	200 " + "	rnum " + "	xv	250 " + "	pic 4 "
            + "endif " //	   selected item
            + "if 6 " + "	xv	296 " + "	pic 6 " + "endif " + "yb	-50 " //	   picked
            // up
            // item
            + "if 7 " + "	xv	0 " + "	pic 7 " + "	xv	26 " + "	yb	-42 "
            + "	stat_string 8 " + "	yb	-50 " + "endif "
            //	   timer
            + "if 9 " + "	xv	246 " + "	num	2	10 " + "	xv	296 " + "	pic	9 "
            + "endif "
            //		help / weapon icon
            + "if 11 " + "	xv	148 " + "	pic	11 " + "endif " //		frags
            + "xr	-50 " + "yt 2 " + "num 3 14 " //	   spectator
            + "if 17 " + "xv 0 " + "yb -58 " + "string2 \"SPECTATOR MODE\" "
            + "endif " //	   chase camera
            + "if 16 " + "xv 0 " + "yb -68 " + "string \"Chasing\" " + "xv 64 "
            + "stat_string 16 " + "endif ";

    static spawn_t spawns[] = {
            new spawn_t("item_health", SP_item_health),
            new spawn_t("item_health_small", SP_item_health_small),
            new spawn_t("item_health_large", SP_item_health_large),
            new spawn_t("item_health_mega", SP_item_health_mega),
            new spawn_t("info_player_start", SP_info_player_start),
            new spawn_t("info_player_deathmatch", SP_info_player_deathmatch),
            new spawn_t("info_player_coop", SP_info_player_coop),
            new spawn_t("info_player_intermission", SP_info_player_intermission),
            new spawn_t("func_plat", SP_func_plat),
            new spawn_t("func_button", GameFunc.SP_func_button),
            new spawn_t("func_door", GameFunc.SP_func_door),
            new spawn_t("func_door_secret", GameFunc.SP_func_door_secret),
            new spawn_t("func_door_rotating", GameFunc.SP_func_door_rotating),
            new spawn_t("func_rotating", GameFunc.SP_func_rotating),
            new spawn_t("func_train", SP_func_train),
            new spawn_t("func_water", SP_func_water),
            new spawn_t("func_conveyor", GameFunc.SP_func_conveyor),
            new spawn_t("func_areaportal", GameMisc.SP_func_areaportal),
            new spawn_t("func_clock", SP_func_clock),
            new spawn_t("func_wall", new EntThinkAdapter() {
        public String getID(){ return "func_wall"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_func_wall(ent);
                    return true;
                }
            }),
            new spawn_t("func_object", new EntThinkAdapter() {
        public String getID(){ return "SP_func_object"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_func_object(ent);
                    return true;
                }
            }),
            new spawn_t("func_timer", new EntThinkAdapter() {
        public String getID(){ return "SP_func_timer"; }
                public boolean think(edict_t ent) {
                    GameFunc.SP_func_timer(ent);
                    return true;
                }
            }),
            new spawn_t("func_explosive", new EntThinkAdapter() {
        public String getID(){ return "SP_func_explosive"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_func_explosive(ent);
                    return true;
                }
            }),
            new spawn_t("func_killbox", GameFunc.SP_func_killbox),
            new spawn_t("trigger_always", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_always"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_always(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_once", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_once"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_once(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_multiple", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_multiple"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_multiple(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_relay", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_relay"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_relay(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_push", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_push"; }
                
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_push(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_hurt", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_hurt"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_hurt(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_key", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_key"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_key(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_counter", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_counter"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_counter(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_elevator", GameFunc.SP_trigger_elevator),
            new spawn_t("trigger_gravity", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_gravity"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_gravity(ent);
                    return true;
                }
            }),
            new spawn_t("trigger_monsterjump", new EntThinkAdapter() {
        public String getID(){ return "SP_trigger_monsterjump"; }
                public boolean think(edict_t ent) {
                    GameTrigger.SP_trigger_monsterjump(ent);
                    return true;
                }
            }),
            new spawn_t("target_temp_entity", new EntThinkAdapter() {
        public String getID(){ return "SP_target_temp_entity"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_temp_entity(ent);
                    return true;
                }
            }),
            new spawn_t("target_speaker", new EntThinkAdapter() {
        public String getID(){ return "SP_target_speaker"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_speaker(ent);
                    return true;
                }
            }),
            new spawn_t("target_explosion", new EntThinkAdapter() {
        public String getID(){ return "SP_target_explosion"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_explosion(ent);
                    return true;
                }
            }),
            new spawn_t("target_changelevel", new EntThinkAdapter() {
        public String getID(){ return "SP_target_changelevel"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_changelevel(ent);
                    return true;
                }
            }),
            new spawn_t("target_secret", new EntThinkAdapter() {
        public String getID(){ return "SP_target_secret"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_secret(ent);
                    return true;
                }
            }),
            new spawn_t("target_goal", new EntThinkAdapter() {
        public String getID(){ return "SP_target_goal"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_goal(ent);
                    return true;
                }
            }),
            new spawn_t("target_splash", new EntThinkAdapter() {
        public String getID(){ return "SP_target_splash"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_splash(ent);
                    return true;
                }
            }),
            new spawn_t("target_spawner", new EntThinkAdapter() {
        public String getID(){ return "SP_target_spawner"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_spawner(ent);
                    return true;
                }
            }),
            new spawn_t("target_blaster", new EntThinkAdapter() {
        public String getID(){ return "SP_target_blaster"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_blaster(ent);
                    return true;
                }
            }),
            new spawn_t("target_crosslevel_trigger", new EntThinkAdapter() {
        public String getID(){ return "SP_target_crosslevel_trigger"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_crosslevel_trigger(ent);
                    return true;
                }
            }),
            new spawn_t("target_crosslevel_target", new EntThinkAdapter() {
        public String getID(){ return "SP_target_crosslevel_target"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_crosslevel_target(ent);
                    return true;
                }
            }),
            new spawn_t("target_laser", new EntThinkAdapter() {
        public String getID(){ return "SP_target_laser"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_laser(ent);
                    return true;
                }
            }),
            new spawn_t("target_help", new EntThinkAdapter() {
        public String getID(){ return "SP_target_help"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_help(ent);
                    return true;
                }
            }),
            new spawn_t("target_actor", new EntThinkAdapter() {
        public String getID(){ return "SP_target_actor"; }
                public boolean think(edict_t ent) {
                    M_Actor.SP_target_actor(ent);
                    return true;
                }
            }),
            new spawn_t("target_lightramp", new EntThinkAdapter() {
        public String getID(){ return "SP_target_lightramp"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_lightramp(ent);
                    return true;
                }
            }),
            new spawn_t("target_earthquake", new EntThinkAdapter() {
        public String getID(){ return "SP_target_earthquake"; }
                public boolean think(edict_t ent) {
                    GameTarget.SP_target_earthquake(ent);
                    return true;
                }
            }),
            new spawn_t("target_character", new EntThinkAdapter() {
        public String getID(){ return "SP_target_character"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_target_character(ent);
                    return true;
                }
            }),
            new spawn_t("target_string", new EntThinkAdapter() {
        public String getID(){ return "SP_target_string"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_target_string(ent);
                    return true;
                }
            }),
            new spawn_t("worldspawn", SP_worldspawn),
            new spawn_t("viewthing", new EntThinkAdapter() {
        public String getID(){ return "SP_viewthing"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_viewthing(ent);
                    return true;
                }
            }),
            new spawn_t("light", new EntThinkAdapter() {
        public String getID(){ return "SP_light"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_light(ent);
                    return true;
                }
            }),
            new spawn_t("light_mine1", new EntThinkAdapter() {
        public String getID(){ return "SP_light_mine1"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_light_mine1(ent);
                    return true;
                }
            }),
            new spawn_t("light_mine2", new EntThinkAdapter() {
        public String getID(){ return "SP_light_mine2"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_light_mine2(ent);
                    return true;
                }
            }),
            new spawn_t("info_null", new EntThinkAdapter() {
        public String getID(){ return "SP_info_null"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_info_null(ent);
                    return true;
                }
            }),
            new spawn_t("func_group", new EntThinkAdapter() {
        public String getID(){ return "SP_info_null"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_info_null(ent);
                    return true;
                }
            }),
            new spawn_t("info_notnull", new EntThinkAdapter() {
        public String getID(){ return "info_notnull"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_info_notnull(ent);
                    return true;
                }
            }),
            new spawn_t("path_corner", new EntThinkAdapter() {
        public String getID(){ return "SP_path_corner"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_path_corner(ent);
                    return true;
                }
            }),
            new spawn_t("point_combat", new EntThinkAdapter() {
        public String getID(){ return "SP_point_combat"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_point_combat(ent);
                    return true;
                }
            }),
            new spawn_t("misc_explobox", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_explobox"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_explobox(ent);
                    return true;
                }
            }),
            new spawn_t("misc_banner", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_banner"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_banner(ent);
                    return true;
                }
            }),
            new spawn_t("misc_satellite_dish", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_satellite_dish"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_satellite_dish(ent);
                    return true;
                }
            }),
            new spawn_t("misc_actor", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_actor"; }
                public boolean think(edict_t ent) {
                    M_Actor.SP_misc_actor(ent);
                    return false;
                }
            }),
            new spawn_t("misc_gib_arm", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_gib_arm"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_gib_arm(ent);
                    return true;
                }
            }),
            new spawn_t("misc_gib_leg", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_gib_leg"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_gib_leg(ent);
                    return true;
                }
            }),
            new spawn_t("misc_gib_head", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_gib_head"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_gib_head(ent);
                    return true;
                }
            }),
            new spawn_t("misc_insane", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_insane"; }
                public boolean think(edict_t ent) {
                    M_Insane.SP_misc_insane(ent);
                    return true;
                }
            }),
            new spawn_t("misc_deadsoldier", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_deadsoldier"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_deadsoldier(ent);
                    return true;
                }
            }),
            new spawn_t("misc_viper", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_viper"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_viper(ent);
                    return true;
                }
            }),
            new spawn_t("misc_viper_bomb", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_viper_bomb"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_viper_bomb(ent);
                    return true;
                }
            }),
            new spawn_t("misc_bigviper", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_bigviper"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_bigviper(ent);
                    return true;
                }
            }),
            new spawn_t("misc_strogg_ship", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_strogg_ship"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_strogg_ship(ent);
                    return true;
                }
            }),
            new spawn_t("misc_teleporter", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_teleporter"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_teleporter(ent);
                    return true;
                }
            }),
            new spawn_t("misc_teleporter_dest",
                    GameMisc.SP_misc_teleporter_dest),
            new spawn_t("misc_blackhole", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_blackhole"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_blackhole(ent);
                    return true;
                }
            }),
            new spawn_t("misc_eastertank", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_eastertank"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_eastertank(ent);
                    return true;
                }
            }),
            new spawn_t("misc_easterchick", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_easterchick"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_easterchick(ent);
                    return true;
                }
            }),
            new spawn_t("misc_easterchick2", new EntThinkAdapter() {
        public String getID(){ return "SP_misc_easterchick2"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_misc_easterchick2(ent);
                    return true;
                }
            }),
            new spawn_t("monster_berserk", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_berserk"; }
                public boolean think(edict_t ent) {
                    M_Berserk.SP_monster_berserk(ent);
                    return true;
                }
            }),
            new spawn_t("monster_gladiator", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_gladiator"; }
                public boolean think(edict_t ent) {
                    M_Gladiator.SP_monster_gladiator(ent);
                    return true;
                }
            }),
            new spawn_t("monster_gunner", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_gunner"; }
                public boolean think(edict_t ent) {
                    M_Gunner.SP_monster_gunner(ent);
                    return true;
                }
            }),
            new spawn_t("monster_infantry", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_infantry"; }
                public boolean think(edict_t ent) {
                    M_Infantry.SP_monster_infantry(ent);
                    return true;
                }
            }),
            new spawn_t("monster_soldier_light",
                    M_Soldier.SP_monster_soldier_light),
            new spawn_t("monster_soldier", M_Soldier.SP_monster_soldier),
            new spawn_t("monster_soldier_ss", M_Soldier.SP_monster_soldier_ss),
            new spawn_t("monster_tank", M_Tank.SP_monster_tank),
            new spawn_t("monster_tank_commander", M_Tank.SP_monster_tank),
            new spawn_t("monster_medic", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_medic"; }
                public boolean think(edict_t ent) {
                    M_Medic.SP_monster_medic(ent);
                    return true;
                }
            }), new spawn_t("monster_flipper", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_flipper"; }
                public boolean think(edict_t ent) {
                    M_Flipper.SP_monster_flipper(ent);
                    return true;
                }
            }), new spawn_t("monster_chick", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_chick"; }
                public boolean think(edict_t ent) {
                    M_Chick.SP_monster_chick(ent);
                    return true;
                }
            }),
            new spawn_t("monster_parasite", M_Parasite.SP_monster_parasite),
            new spawn_t("monster_flyer", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_flyer"; }
                public boolean think(edict_t ent) {
                    M_Flyer.SP_monster_flyer(ent);
                    return true;
                }
            }), new spawn_t("monster_brain", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_brain"; }
                public boolean think(edict_t ent) {
                    M_Brain.SP_monster_brain(ent);
                    return true;
                }
            }), new spawn_t("monster_floater", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_floater"; }
                public boolean think(edict_t ent) {
                    M_Float.SP_monster_floater(ent);
                    return true;
                }
            }), new spawn_t("monster_hover", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_hover"; }
                public boolean think(edict_t ent) {
                    M_Hover.SP_monster_hover(ent);
                    return true;
                }
            }), new spawn_t("monster_mutant", M_Mutant.SP_monster_mutant),
            new spawn_t("monster_supertank", M_Supertank.SP_monster_supertank),
            new spawn_t("monster_boss2", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_boss2"; }
                public boolean think(edict_t ent) {
                    M_Boss2.SP_monster_boss2(ent);
                    return true;
                }
            }), new spawn_t("monster_boss3_stand", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_boss3_stand"; }
                public boolean think(edict_t ent) {
                    M_Boss3.SP_monster_boss3_stand(ent);
                    return true;
                }
            }), new spawn_t("monster_jorg", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_jorg"; }
                public boolean think(edict_t ent) {
                    M_Boss31.SP_monster_jorg(ent);
                    return true;
                }
            }), new spawn_t("monster_commander_body", new EntThinkAdapter() {
        public String getID(){ return "SP_monster_commander_body"; }
                public boolean think(edict_t ent) {
                    GameMisc.SP_monster_commander_body(ent);
                    return true;
                }
            }), new spawn_t("turret_breach", new EntThinkAdapter() {
        public String getID(){ return "SP_turret_breach"; }
                public boolean think(edict_t ent) {
                    GameTurret.SP_turret_breach(ent);
                    return true;
                }
            }), new spawn_t("turret_base", new EntThinkAdapter() {
        public String getID(){ return "SP_turret_base"; }
                public boolean think(edict_t ent) {
                    GameTurret.SP_turret_base(ent);
                    return true;
                }
            }), new spawn_t("turret_driver", new EntThinkAdapter() {
        public String getID(){ return "SP_turret_driver"; }
                public boolean think(edict_t ent) {
                    GameTurret.SP_turret_driver(ent);
                    return true;
                }
            }), new spawn_t(null, null) };

    /**
     * ED_CallSpawn
     * 
     * Finds the spawn function for the entity and calls it.
     */
    public static void ED_CallSpawn(edict_t ent) {

        spawn_t s;
        gitem_t item;
        int i;
        if (null == ent.classname) {
            GameBase.gi.dprintf("ED_CallSpawn: null classname\n");
            return;
        } // check item spawn functions
        for (i = 1; i < GameBase.game.num_items; i++) {

            item = GameItemList.itemlist[i];

            if (item == null)
                GameBase.gi.error("ED_CallSpawn: null item in pos " + i);

            if (item.classname == null)
                continue;
            if (item.classname.equalsIgnoreCase(ent.classname)) { // found it
                GameItems.SpawnItem(ent, item);
                return;
            }
        } // check normal spawn functions

        for (i = 0; (s = spawns[i]) != null && s.name != null; i++) {
            if (s.name.equalsIgnoreCase(ent.classname)) { // found it

                if (s.spawn == null)
                    GameBase.gi.error("ED_CallSpawn: null-spawn on index=" + i);
                s.spawn.think(ent);
                return;
            }
        }
        GameBase.gi.dprintf(ent.classname + " doesn't have a spawn function\n");
    }
}