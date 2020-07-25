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

import jake2.game.monsters.*;
import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jake2.game.GameUtil.G_Spawn;

public class GameSpawn {

    private static EntThinkAdapter SP_item_health = new EntThinkAdapter() {
        public String getID() {
            return "SP_item_health";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            GameItems.SP_item_health(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_item_health_small = new EntThinkAdapter() {
        public String getID() {
            return "SP_item_health_small";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            GameItems.SP_item_health_small(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_item_health_large = new EntThinkAdapter() {
        public String getID() {
            return "SP_item_health_large";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            GameItems.SP_item_health_large(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_item_health_mega = new EntThinkAdapter() {
        public String getID() {
            return "SP_item_health_mega";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            GameItems.SP_item_health_mega(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_info_player_start = new EntThinkAdapter() {
        public String getID() {
            return "SP_info_player_start";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            PlayerClient.SP_info_player_start(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_info_player_deathmatch = new EntThinkAdapter() {
        public String getID() {
            return "SP_info_player_deathmatch";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            PlayerClient.SP_info_player_deathmatch(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_info_player_coop = new EntThinkAdapter() {
        public String getID() {
            return "SP_info_player_coop";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            PlayerClient.SP_info_player_coop(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_info_player_intermission = new EntThinkAdapter() {
        public String getID() {
            return "SP_info_player_intermission";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            PlayerClient.SP_info_player_intermission();
            return true;
        }
    };

    private static EntThinkAdapter SP_func_plat = new EntThinkAdapter() {
        public String getID() {
            return "SP_func_plat";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            GameFunc.SP_func_plat(ent, gameExports);
            return true;
        }
    };


    private static EntThinkAdapter SP_func_water = new EntThinkAdapter() {
        public String getID() {
            return "SP_func_water";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            GameFunc.SP_func_water(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_func_train = new EntThinkAdapter() {
        public String getID() {
            return "SP_func_train";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            GameFunc.SP_func_train(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter SP_func_clock = new EntThinkAdapter() {
        public String getID() {
            return "SP_func_clock";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            GameMisc.SP_func_clock(ent, gameExports);
            return true;
        }
    };
    private static final String single_statusbar = "yb	-24 " //	   health
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
    private static final String dm_statusbar = "yb	-24 " //	   health
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
    /**
     * QUAKED worldspawn (0 0 0) ?
     * <p>
     * Only used for the world. "sky" environment map name "skyaxis" vector axis
     * for rotating sky "skyrotate" speed of rotation in degrees/second "sounds"
     * music cd track number "gravity" 800 is default gravity "message" text to
     * print at user logon
     */

    private static EntThinkAdapter SP_worldspawn = new EntThinkAdapter() {
        public String getID() {
            return "SP_worldspawn";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            ent.movetype = GameDefines.MOVETYPE_PUSH;
            ent.solid = Defines.SOLID_BSP;
            ent.inuse = true;
            // since the world doesn't use G_Spawn()
            ent.s.modelindex = 1;
            // world model is always index 1
            //---------------
            // reserve some spots for dead player bodies for coop / deathmatch
            PlayerClient.InitBodyQue(gameExports);
            // set configstrings for items
            GameItems.SetItemNames(gameExports);

            if (gameExports.st.nextmap != null)
                gameExports.level.nextmap = gameExports.st.nextmap;
            // make some data visible to the server
            if (ent.message != null && ent.message.length() > 0) {
                gameExports.gameImports.configstring(Defines.CS_NAME, ent.message);
                gameExports.level.level_name = ent.message;
            } else
                gameExports.level.level_name = gameExports.level.mapname;
            if (gameExports.st.sky != null && gameExports.st.sky.length() > 0)
                gameExports.gameImports.configstring(Defines.CS_SKY, gameExports.st.sky);
            else
                gameExports.gameImports.configstring(Defines.CS_SKY, "unit1_");
            gameExports.gameImports.configstring(Defines.CS_SKYROTATE, ""
                    + gameExports.st.skyrotate);
            gameExports.gameImports.configstring(Defines.CS_SKYAXIS, Lib
                    .vtos(gameExports.st.skyaxis));
            gameExports.gameImports.configstring(Defines.CS_CDTRACK, "" + ent.sounds);
            gameExports.gameImports.configstring(Defines.CS_MAXCLIENTS, ""
                    + (int) (gameExports.game.maxclients));
            // status bar program
            if (gameExports.cvarCache.deathmatch.value != 0)
                gameExports.gameImports.configstring(Defines.CS_STATUSBAR, "" + dm_statusbar);
            else
                gameExports.gameImports.configstring(Defines.CS_STATUSBAR, "" + single_statusbar);
            //---------------
            // help icon for statusbar
            gameExports.gameImports.imageindex("i_help");
            gameExports.level.pic_health = gameExports.gameImports.imageindex("i_health");
            gameExports.gameImports.imageindex("help");
            gameExports.gameImports.imageindex("field_3");
            if ("".equals(gameExports.st.gravity))
                gameExports.gameImports.cvar_set("sv_gravity", "800");
            else
                gameExports.gameImports.cvar_set("sv_gravity", gameExports.st.gravity);

            // standing in lava / slime
            gameExports.gameImports.soundindex("player/fry.wav");

            // starter weapon
            GameItems.PrecacheItem(GameItems.FindItem("Blaster", gameExports), gameExports);

            gameExports.gameImports.soundindex("player/lava1.wav");
            gameExports.gameImports.soundindex("player/lava2.wav");
            gameExports.gameImports.soundindex("misc/pc_up.wav");
            gameExports.gameImports.soundindex("misc/talk1.wav");
            gameExports.gameImports.soundindex("misc/udeath.wav");
            // gibs
            gameExports.gameImports.soundindex("items/respawn1.wav");
            // sexed sounds
            gameExports.gameImports.soundindex("*death1.wav");
            gameExports.gameImports.soundindex("*death2.wav");
            gameExports.gameImports.soundindex("*death3.wav");
            gameExports.gameImports.soundindex("*death4.wav");
            gameExports.gameImports.soundindex("*fall1.wav");
            gameExports.gameImports.soundindex("*fall2.wav");
            gameExports.gameImports.soundindex("*gurp1.wav");
            // drowning damage
            gameExports.gameImports.soundindex("*gurp2.wav");
            gameExports.gameImports.soundindex("*jump1.wav");
            // player jump
            gameExports.gameImports.soundindex("*pain25_1.wav");
            gameExports.gameImports.soundindex("*pain25_2.wav");
            gameExports.gameImports.soundindex("*pain50_1.wav");
            gameExports.gameImports.soundindex("*pain50_2.wav");
            gameExports.gameImports.soundindex("*pain75_1.wav");
            gameExports.gameImports.soundindex("*pain75_2.wav");
            gameExports.gameImports.soundindex("*pain100_1.wav");
            gameExports.gameImports.soundindex("*pain100_2.wav");
            // sexed models
            // THIS ORDER MUST MATCH THE DEFINES IN g_local.h
            // you can add more, max 15
            gameExports.gameImports.modelindex("#w_blaster.md2");
            gameExports.gameImports.modelindex("#w_shotgun.md2");
            gameExports.gameImports.modelindex("#w_sshotgun.md2");
            gameExports.gameImports.modelindex("#w_machinegun.md2");
            gameExports.gameImports.modelindex("#w_chaingun.md2");
            gameExports.gameImports.modelindex("#a_grenades.md2");
            gameExports.gameImports.modelindex("#w_glauncher.md2");
            gameExports.gameImports.modelindex("#w_rlauncher.md2");
            gameExports.gameImports.modelindex("#w_hyperblaster.md2");
            gameExports.gameImports.modelindex("#w_railgun.md2");
            gameExports.gameImports.modelindex("#w_bfg.md2");
            //-------------------
            gameExports.gameImports.soundindex("player/gasp1.wav");
            // gasping for air
            gameExports.gameImports.soundindex("player/gasp2.wav");
            // head breaking surface, not gasping
            gameExports.gameImports.soundindex("player/watr_in.wav");
            // feet hitting water
            gameExports.gameImports.soundindex("player/watr_out.wav");
            // feet leaving water
            gameExports.gameImports.soundindex("player/watr_un.wav");
            // head going underwater
            gameExports.gameImports.soundindex("player/u_breath1.wav");
            gameExports.gameImports.soundindex("player/u_breath2.wav");
            gameExports.gameImports.soundindex("items/pkup.wav");
            // bonus item pickup
            gameExports.gameImports.soundindex("world/land.wav");
            // landing thud
            gameExports.gameImports.soundindex("misc/h2ohit1.wav");
            // landing splash
            gameExports.gameImports.soundindex("items/damage.wav");
            gameExports.gameImports.soundindex("items/protect.wav");
            gameExports.gameImports.soundindex("items/protect4.wav");
            gameExports.gameImports.soundindex("weapons/noammo.wav");
            gameExports.gameImports.soundindex("infantry/inflies1.wav");
            gameExports.gameImports.modelindex("models/objects/gibs/sm_meat/tris.md2");
            gameExports.gameImports.modelindex("models/objects/gibs/arm/tris.md2");
            gameExports.gameImports.modelindex("models/objects/gibs/bone/tris.md2");
            gameExports.gameImports.modelindex("models/objects/gibs/bone2/tris.md2");
            gameExports.gameImports.modelindex("models/objects/gibs/chest/tris.md2");
            gameExports.gameImports.modelindex("models/objects/gibs/skull/tris.md2");
            gameExports.gameImports.modelindex("models/objects/gibs/head2/tris.md2");
            //
            // Setup light animation tables. 'a' is total darkness, 'z' is
            // doublebright.
            //
            // 0 normal
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 0, "m");
            // 1 FLICKER (first variety)
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 1,
                    "mmnmmommommnonmmonqnmmo");
            // 2 SLOW STRONG PULSE
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 2,
                    "abcdefghijklmnopqrstuvwxyzyxwvutsrqponmlkjihgfedcba");
            // 3 CANDLE (first variety)
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 3,
                    "mmmmmaaaaammmmmaaaaaabcdefgabcdefg");
            // 4 FAST STROBE
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 4, "mamamamamama");
            // 5 GENTLE PULSE 1
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 5,
                    "jklmnopqrstuvwxyzyxwvutsrqponmlkj");
            // 6 FLICKER (second variety)
            gameExports.gameImports
                    .configstring(Defines.CS_LIGHTS + 6, "nmonqnmomnmomomno");
            // 7 CANDLE (second variety)
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 7,
                    "mmmaaaabcdefgmmmmaaaammmaamm");
            // 8 CANDLE (third variety)
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 8,
                    "mmmaaammmaaammmabcdefaaaammmmabcdefmmmaaaa");
            // 9 SLOW STROBE (fourth variety)
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 9, "aaaaaaaazzzzzzzz");
            // 10 FLUORESCENT FLICKER
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 10,
                    "mmamammmmammamamaaamammma");
            // 11 SLOW PULSE NOT FADE TO BLACK
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 11,
                    "abcdefghijklmnopqrrqponmlkjihgfedcba");
            // styles 32-62 are assigned by the light program for switchable
            // lights
            // 63 testing
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + 63, "a");
            return true;
        }
    };
    private static final Map<String, EntThinkAdapter> spawns;

    static {
        spawns = new HashMap<>();

        spawns.put("item_health", SP_item_health);
        spawns.put("item_health_small", SP_item_health_small);
        spawns.put("item_health_large", SP_item_health_large);
        spawns.put("item_health_mega", SP_item_health_mega);
        spawns.put("info_player_start", SP_info_player_start);
        spawns.put("info_player_deathmatch", SP_info_player_deathmatch);
        spawns.put("info_player_coop", SP_info_player_coop);
        spawns.put("info_player_intermission", SP_info_player_intermission);
        spawns.put("func_plat", SP_func_plat);
        spawns.put("func_button", GameFunc.SP_func_button);
        spawns.put("func_door", GameFunc.SP_func_door);
        spawns.put("func_door_secret", GameFunc.SP_func_door_secret);
        spawns.put("func_door_rotating", GameFunc.SP_func_door_rotating);
        spawns.put("func_rotating", GameFunc.SP_func_rotating);
        spawns.put("func_train", SP_func_train);
        spawns.put("func_water", SP_func_water);
        spawns.put("func_conveyor", GameFunc.SP_func_conveyor);
        spawns.put("func_areaportal", GameMisc.SP_func_areaportal);
        spawns.put("func_clock", SP_func_clock);
        spawns.put("func_wall", new EntThinkAdapter() {
            public String getID() {
                return "func_wall";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_func_wall(ent, gameExports);
                return true;
            }
        });
        spawns.put("func_object", new EntThinkAdapter() {
            public String getID() {
                return "SP_func_object";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_func_object(ent, gameExports);
                return true;
            }
        });
        spawns.put("func_timer", new EntThinkAdapter() {
            public String getID() {
                return "SP_func_timer";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameFunc.SP_func_timer(ent, gameExports);
                return true;
            }
        });
        spawns.put("func_explosive", new EntThinkAdapter() {
            public String getID() {
                return "SP_func_explosive";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_func_explosive(ent, gameExports);
                return true;
            }
        });
        spawns.put("func_killbox", GameFunc.SP_func_killbox);
        spawns.put("trigger_always", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_always";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_always(ent, gameExports);
                return true;
            }
        });
        spawns.put("trigger_once", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_once";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_once(ent, gameExports);
                return true;
            }
        });
        spawns.put("trigger_multiple", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_multiple";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_multiple(ent, gameExports);
                return true;
            }
        });
        spawns.put("trigger_relay", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_relay";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_relay(ent);
                return true;
            }
        });
        spawns.put("trigger_push", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_push";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_push(ent, gameExports);
                return true;
            }
        });
        spawns.put("trigger_hurt", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_hurt";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_hurt(ent, gameExports);
                return true;
            }
        });
        spawns.put("trigger_key", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_key";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_key(ent, gameExports);
                return true;
            }
        });
        spawns.put("trigger_counter", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_counter";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_counter(ent);
                return true;
            }
        });
        spawns.put("trigger_elevator", GameFunc.SP_trigger_elevator);
        spawns.put("trigger_gravity", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_gravity";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_gravity(ent, gameExports);
                return true;
            }
        });
        spawns.put("trigger_monsterjump", new EntThinkAdapter() {
            public String getID() {
                return "SP_trigger_monsterjump";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTrigger.SP_trigger_monsterjump(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_temp_entity", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_temp_entity";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_temp_entity(ent);
                return true;
            }
        });
        spawns.put("target_speaker", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_speaker";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_speaker(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_explosion", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_explosion";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_explosion(ent);
                return true;
            }
        });
        spawns.put("target_changelevel", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_changelevel";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_changelevel(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_secret", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_secret";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_secret(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_goal", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_goal";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_goal(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_splash", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_splash";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_splash(ent);
                return true;
            }
        });
        spawns.put("target_spawner", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_spawner";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_spawner(ent);
                return true;
            }
        });
        spawns.put("target_blaster", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_blaster";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_blaster(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_crosslevel_trigger", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_crosslevel_trigger";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_crosslevel_trigger(ent);
                return true;
            }
        });
        spawns.put("target_crosslevel_target", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_crosslevel_target";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_crosslevel_target(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_laser", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_laser";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_laser(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_help", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_help";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_help(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_actor", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_actor";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Actor.SP_target_actor(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_lightramp", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_lightramp";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_lightramp(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_earthquake", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_earthquake";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_earthquake(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_character", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_character";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_target_character(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_string", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_string";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_target_string(ent);
                return true;
            }
        });
        spawns.put("worldspawn", SP_worldspawn);
        spawns.put("viewthing", new EntThinkAdapter() {
            public String getID() {
                return "SP_viewthing";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_viewthing(ent, gameExports);
                return true;
            }
        });
        spawns.put("light", new EntThinkAdapter() {
            public String getID() {
                return "SP_light";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_light(ent, gameExports);
                return true;
            }
        });
        spawns.put("light_mine1", new EntThinkAdapter() {
            public String getID() {
                return "SP_light_mine1";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_light_mine1(ent, gameExports);
                return true;
            }
        });
        spawns.put("light_mine2", new EntThinkAdapter() {
            public String getID() {
                return "SP_light_mine2";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_light_mine2(ent, gameExports);
                return true;
            }
        });
        spawns.put("info_null", new EntThinkAdapter() {
            public String getID() {
                return "SP_info_null";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_info_null(ent, gameExports);
                return true;
            }
        });
        spawns.put("func_group", new EntThinkAdapter() {
            public String getID() {
                return "SP_info_null";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_info_null(ent, gameExports);
                return true;
            }
        });
        spawns.put("info_notnull", new EntThinkAdapter() {
            public String getID() {
                return "info_notnull";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_info_notnull(ent);
                return true;
            }
        });
        spawns.put("path_corner", new EntThinkAdapter() {
            public String getID() {
                return "SP_path_corner";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_path_corner(ent, gameExports);
                return true;
            }
        });
        spawns.put("point_combat", new EntThinkAdapter() {
            public String getID() {
                return "SP_point_combat";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_point_combat(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_explobox", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_explobox";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_explobox(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_banner", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_banner";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_banner(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_satellite_dish", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_satellite_dish";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_satellite_dish(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_actor", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_actor";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Actor.SP_misc_actor(ent, gameExports);
                return false;
            }
        });
        spawns.put("misc_gib_arm", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_gib_arm";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_gib_arm(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_gib_leg", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_gib_leg";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_gib_leg(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_gib_head", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_gib_head";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_gib_head(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_insane", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_insane";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Insane.SP_misc_insane(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_deadsoldier", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_deadsoldier";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_deadsoldier(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_viper", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_viper";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_viper(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_viper_bomb", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_viper_bomb";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_viper_bomb(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_bigviper", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_bigviper";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_bigviper(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_strogg_ship", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_strogg_ship";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_strogg_ship(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_teleporter", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_teleporter";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_teleporter(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_teleporter_dest", GameMisc.SP_misc_teleporter_dest);
        spawns.put("misc_blackhole", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_blackhole";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_blackhole(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_eastertank", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_eastertank";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_eastertank(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_easterchick", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_easterchick";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_easterchick(ent, gameExports);
                return true;
            }
        });
        spawns.put("misc_easterchick2", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_easterchick2";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_easterchick2(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_berserk", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_berserk";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Berserk.SP_monster_berserk(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_gladiator", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_gladiator";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Gladiator.SP_monster_gladiator(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_gunner", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_gunner";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Gunner.SP_monster_gunner(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_infantry", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_infantry";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Infantry.SP_monster_infantry(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_soldier_light", M_Soldier.SP_monster_soldier_light);
        spawns.put("monster_soldier", M_Soldier.SP_monster_soldier);
        spawns.put("monster_soldier_ss", M_Soldier.SP_monster_soldier_ss);
        spawns.put("monster_tank", M_Tank.SP_monster_tank);
        spawns.put("monster_tank_commander", M_Tank.SP_monster_tank);
        spawns.put("monster_medic", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_medic";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Medic.SP_monster_medic(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_flipper", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_flipper";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Flipper.SP_monster_flipper(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_chick", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_chick";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Chick.SP_monster_chick(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_parasite", M_Parasite.SP_monster_parasite);
        spawns.put("monster_flyer", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_flyer";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Flyer.SP_monster_flyer(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_brain", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_brain";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Brain.SP_monster_brain(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_floater", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_floater";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Float.SP_monster_floater(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_hover", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_hover";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Hover.SP_monster_hover(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_mutant", M_Mutant.SP_monster_mutant);
        spawns.put("monster_supertank", M_Supertank.SP_monster_supertank);
        spawns.put("monster_boss2", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_boss2";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Boss2.SP_monster_boss2(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_boss3_stand", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_boss3_stand";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Boss3.SP_monster_boss3_stand(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_jorg", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_jorg";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Boss31.SP_monster_jorg(ent, gameExports);
                return true;
            }
        });
        spawns.put("monster_commander_body", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_commander_body";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_monster_commander_body(ent, gameExports);
                return true;
            }
        });
        spawns.put("turret_breach", new EntThinkAdapter() {
            public String getID() {
                return "SP_turret_breach";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTurret.SP_turret_breach(ent, gameExports);
                return true;
            }
        });
        spawns.put("turret_base", new EntThinkAdapter() {
            public String getID() {
                return "SP_turret_base";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTurret.SP_turret_base(ent, gameExports);
                return true;
            }
        });
        spawns.put("turret_driver", new EntThinkAdapter() {
            public String getID() {
                return "SP_turret_driver";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTurret.SP_turret_driver(ent, gameExports);
                return true;
            }
        });
    }

    /**
     * ED_ParseField
     * <p>
     * Takes a key/value pair and sets the binary values in an edict.
     */
    private static void ED_ParseField(String key, String value, SubgameEntity ent, GameExportsImpl gameExports) {

        if (key.equals("nextmap"))
            gameExports.gameImports.dprintf("nextmap: " + value);
        if (!gameExports.st.set(key, value))
            if (!ent.setField(key, value))
                gameExports.gameImports.dprintf("??? The key [" + key
                        + "] is not a field\n");

    }

    /**
     * ED_ParseEdict
     * <p>
     * Parses an edict out of the given string, returning the new position ed
     * should be a properly initialized empty edict.
     */

    private static void ED_ParseEdict(Com.ParseHelp ph, SubgameEntity ent, GameExportsImpl gameExports) {

        boolean init;
        String keyname;
        String com_token;
        init = false;

        gameExports.st = new spawn_temp_t();
        while (true) {

            // parse key
            com_token = Com.Parse(ph);
            if (com_token.equals("}"))
                break;

            if (ph.isEof())
                gameExports.gameImports.error("ED_ParseEntity: EOF without closing brace");

            keyname = com_token;

            // parse value
            com_token = Com.Parse(ph);

            if (ph.isEof())
                gameExports.gameImports.error("ED_ParseEntity: EOF without closing brace");

            if (com_token.equals("}"))
                gameExports.gameImports.error("ED_ParseEntity: closing brace without data");

            init = true;
            // keynames with a leading underscore are used for utility comments,
            // and are immediately discarded by quake
            if (keyname.charAt(0) == '_')
                continue;

            ED_ParseField(keyname.toLowerCase(), com_token, ent, gameExports);

        }

        if (!init) {
            GameUtil.G_ClearEdict(ent, gameExports);
        }
    }

    /**
     * G_FindTeams
     * <p>
     * Chain together all entities with a matching team field.
     * <p>
     * All but the first will have the FL_TEAMSLAVE flag set. All but the last
     * will have the teamchain field set to the next one.
     *
     * Warning: n^2 complexity
     */
    private static void G_FindTeams(GameExportsImpl gameExports) {
        for (int i = 1; i < gameExports.num_edicts; i++) {
            SubgameEntity e = gameExports.g_edicts[i];

            if (!e.inuse)
                continue;
            if (e.team == null)
                continue;
            if ((e.flags & GameDefines.FL_TEAMSLAVE) != 0)
                continue;
            SubgameEntity chain = e;
            e.teammaster = e;

            for (int j = i + 1; j < gameExports.num_edicts; j++) {
                SubgameEntity e2 = gameExports.g_edicts[j];
                if (!e2.inuse)
                    continue;
                if (null == e2.team)
                    continue;
                if ((e2.flags & GameDefines.FL_TEAMSLAVE) != 0)
                    continue;
                if (e.team.equals(e2.team)) {
                    chain.teamchain = e2;
                    e2.teammaster = e;
                    chain = e2;
                    e2.flags |= GameDefines.FL_TEAMSLAVE;

                }
            }
        }
    }

    static void SpawnEntities(String mapname, String entities, String spawnpoint, GameExportsImpl gameExports) {

        Com.dprintln("SpawnEntities(), mapname=" + mapname);
        SubgameEntity ent;
        int inhibit;
        String com_token;
        int i;
        float skill_level;
        //skill.value =2.0f;
        skill_level = (float) Math.floor(gameExports.cvarCache.skill.value);

        if (skill_level < 0)
            skill_level = 0;
        if (skill_level > 3)
            skill_level = 3;
        if (gameExports.cvarCache.skill.value != skill_level)
            gameExports.gameImports.cvar_forceset("skill", "" + skill_level);

        PlayerClient.SaveClientData(gameExports);

        gameExports.level = new level_locals_t();
        for (int n = 0; n < gameExports.game.maxentities; n++) {
            gameExports.g_edicts[n] = new SubgameEntity(n);
        }

        gameExports.level.mapname = mapname;
        gameExports.game.spawnpoint = spawnpoint;

        // set client fields on player ents
        for (i = 0; i < gameExports.game.maxclients; i++)
            gameExports.g_edicts[i + 1].setClient(gameExports.game.clients[i]);

        ent = null;
        inhibit = 0;

        Com.ParseHelp ph = new Com.ParseHelp(entities);

        while (true) { // parse the opening brace

            com_token = Com.Parse(ph);
            if (ph.isEof())
                break;
            if (!com_token.startsWith("{"))
                gameExports.gameImports.error("ED_LoadFromFile: found " + com_token
                        + " when expecting {");

            if (ent == null)
                ent = gameExports.g_edicts[0];
            else
                ent = G_Spawn(gameExports);

            ED_ParseEdict(ph, ent, gameExports);
            Com.DPrintf("spawning ent[" + ent.index + "], classname=" +
                    ent.classname + ", flags= " + Integer.toHexString(ent.spawnflags));

            // yet another map hack
            if (0 == Lib.Q_stricmp(gameExports.level.mapname, "command")
                    && 0 == Lib.Q_stricmp(ent.classname, "trigger_once")
                    && 0 == Lib.Q_stricmp(ent.model, "*27"))
                ent.spawnflags &= ~GameDefines.SPAWNFLAG_NOT_HARD;

            // remove things (except the world) from different skill levels or
            // deathmatch
            if (ent != gameExports.g_edicts[0]) {
                if (gameExports.cvarCache.deathmatch.value != 0) {
                    if ((ent.spawnflags & GameDefines.SPAWNFLAG_NOT_DEATHMATCH) != 0) {

                        Com.DPrintf("->inhibited.\n");
                        GameUtil.G_FreeEdict(ent, gameExports);
                        inhibit++;
                        continue;
                    }
                } else {
                    if (/*
                     * ((coop.value) && (ent.spawnflags &
                     * SPAWNFLAG_NOT_COOP)) ||
                     */
                            ((gameExports.cvarCache.skill.value == 0) && (ent.spawnflags & GameDefines.SPAWNFLAG_NOT_EASY) != 0)
                                    || ((gameExports.cvarCache.skill.value == 1) && (ent.spawnflags & GameDefines.SPAWNFLAG_NOT_MEDIUM) != 0)
                                    || (((gameExports.cvarCache.skill.value == 2) || (gameExports.cvarCache.skill.value == 3)) && (ent.spawnflags & GameDefines.SPAWNFLAG_NOT_HARD) != 0)) {

                        Com.DPrintf("->inhibited.\n");
                        GameUtil.G_FreeEdict(ent, gameExports);
                        inhibit++;

                        continue;
                    }
                }

                ent.spawnflags &= ~(GameDefines.SPAWNFLAG_NOT_EASY
                        | GameDefines.SPAWNFLAG_NOT_MEDIUM
                        | GameDefines.SPAWNFLAG_NOT_HARD
                        | GameDefines.SPAWNFLAG_NOT_COOP | GameDefines.SPAWNFLAG_NOT_DEATHMATCH);
            }
            ED_CallSpawn(ent, gameExports);
            Com.DPrintf("\n");
        }
        Com.DPrintf("player skill level:" + gameExports.cvarCache.skill.value + "\n");
        Com.DPrintf(inhibit + " entities inhibited.\n");
        G_FindTeams(gameExports);
        gameExports.playerTrail.Init();
    }

    /**
     * Finds the spawn function for the entity and calls it.
     */
    public static void ED_CallSpawn(SubgameEntity ent, GameExportsImpl gameExports) {

        if (null == ent.classname) {
            gameExports.gameImports.dprintf("ED_CallSpawn: null classname\n");
            return;
        } // check item spawn functions
        for (int i = 1; i < gameExports.game.num_items; i++) {

            gitem_t item = gameExports.items.itemlist[i];

            if (item == null)
                gameExports.gameImports.error("ED_CallSpawn: null item in pos " + i);

            if (item.classname == null)
                continue;
            if (item.classname.equalsIgnoreCase(ent.classname)) { // found it
                GameItems.SpawnItem(ent, item, gameExports);
                return;
            }
        } // check normal spawn functions

        EntThinkAdapter spawn = spawns.get(ent.classname.toLowerCase());
        if (spawn != null) {
            spawn.think(ent, gameExports);
        } else {
            gameExports.gameImports.dprintf(ent.classname + " doesn't have a spawn function\n");
        }
    }

    static void SpawnNewEntity(SubgameEntity creator, List<String> args, GameExportsImpl gameExports) {
        String className;
        if (args.size() >= 2)
            className = args.get(1);
        else {
            gameExports.gameImports.dprintf("usage: spawn <classname>\n");
            return;
        }

        if (gameExports.cvarCache.deathmatch.value != 0 && gameExports.cvarCache.sv_cheats.value == 0) {
            gameExports.gameImports.cprintf(creator, Defines.PRINT_HIGH,
                    "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        gameExports.gameImports.dprintf("Spawning " + className + " at " + Lib.vtofs(creator.s.origin) + ", " + Lib.vtofs(creator.s.angles) + "\n");

        EntThinkAdapter spawn = spawns.get(className);
        gitem_t gitem_t = GameItems.FindItemByClassname(className, gameExports);
        if (spawn != null || gitem_t != null) {
            float[] location = creator.s.origin;
            SubgameEntity newThing = G_Spawn(gameExports);

            float[] offset = {0,0,0};
            float[] forward = { 0, 0, 0 };

            // only works if damage point is in front
            Math3D.AngleVectors(creator.s.angles, forward, null, null);

            Math3D.VectorNormalize(forward);
            Math3D.VectorScale(forward, 128, offset);
            Math3D.VectorAdd(location, offset, offset);

            newThing.s.origin = offset;

            Math3D.VectorCopy(creator.s.angles, newThing.s.angles);

            newThing.classname = className;
            gameExports.gameImports.linkentity(newThing);
            if (spawn != null)
                spawn.think(newThing, gameExports);
            else
                GameItems.SpawnItem(newThing, gitem_t, gameExports);

            gameExports.gameImports.dprintf("Spawned!\n");
        }

    }
}