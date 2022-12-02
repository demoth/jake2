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

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.func.*;
import jake2.game.items.GameItem;
import jake2.game.items.GameItems;
import jake2.game.monsters.*;
import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.EntityParserKt;
import jake2.qcommon.edict_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.*;
import java.util.stream.Collectors;

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

            if (ent.st.nextmap != null)
                gameExports.level.nextmap = ent.st.nextmap;
            // make some data visible to the server
            if (ent.message != null && ent.message.length() > 0) {
                gameExports.gameImports.configstring(Defines.CS_NAME, ent.message);
                gameExports.level.level_name = ent.message;
            } else
                gameExports.level.level_name = gameExports.level.mapname;
            if (ent.st.sky != null && ent.st.sky.length() > 0)
                gameExports.gameImports.configstring(Defines.CS_SKY, ent.st.sky);
            else
                gameExports.gameImports.configstring(Defines.CS_SKY, "unit1_");
            gameExports.gameImports.configstring(Defines.CS_SKYROTATE, ""
                    + ent.st.skyrotate);
            gameExports.gameImports.configstring(Defines.CS_SKYAXIS, Lib
                    .vtos(ent.st.skyaxis));
            gameExports.gameImports.configstring(Defines.CS_CDTRACK, "" + ent.sounds);
            gameExports.gameImports.configstring(Defines.CS_MAXCLIENTS, ""
                    + (int) (gameExports.game.maxclients));
            // status bar program
            if (gameExports.gameCvars.deathmatch.value != 0)
                gameExports.gameImports.configstring(Defines.CS_STATUSBAR, "" + dm_statusbar);
            else
                gameExports.gameImports.configstring(Defines.CS_STATUSBAR, "" + single_statusbar);
            //---------------
            // help icon for statusbar
            gameExports.gameImports.imageindex("i_help");
            gameExports.level.pic_health = gameExports.gameImports.imageindex("i_health");
            gameExports.gameImports.imageindex("help");
            gameExports.gameImports.imageindex("field_3");
            if ("".equals(ent.st.gravity))
                gameExports.gameImports.cvar_set("sv_gravity", "800");
            else
                gameExports.gameImports.cvar_set("sv_gravity", ent.st.gravity);

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
    private static final Map<String, SpawnInterface> spawns;

    private static void addSpawnAdapter(String id, EntThinkAdapter adapter) {
        spawns.put(id, adapter::think);
    }
    
    static {
        spawns = new HashMap<>();

        addSpawnAdapter("item_health", SP_item_health);
        addSpawnAdapter("item_health_small", SP_item_health_small);
        addSpawnAdapter("item_health_large", SP_item_health_large);
        addSpawnAdapter("item_health_mega", SP_item_health_mega);
        spawns.put("info_player_start", InfoEntitiesKt::infoPlayerStart);
        spawns.put("info_player_deathmatch", InfoEntitiesKt::infoPlayerDeathmatch);
        spawns.put("info_player_coop", InfoEntitiesKt::infoPlayerCoop);
        spawns.put("info_player_intermission", (self, game) -> {});
        spawns.put("func_plat", PlatKt::funcPlat);
        spawns.put("func_button", ButtonKt::funcButton);
        spawns.put("func_door", DoorKt::funcDoor);
        spawns.put("func_door_secret", DoorKt::funcDoorSecret);
        spawns.put("func_door_rotating", DoorKt::funcDoorRotating);
        spawns.put("func_rotating", RotatingKt::funcRotating);
        spawns.put("func_train", TrainKt::funcTrain);
        spawns.put("func_water", WaterKt::funcWater);
        spawns.put("func_conveyor", ConveyorKt::funcConveyor);
        spawns.put("func_areaportal", AreaportalKt::funcAreaPortal);
        spawns.put("func_clock", ClockKt::funcClock);
        spawns.put("func_wall", WallKt::funcWall);
        spawns.put("func_object", Func_objectKt::funcObject);
        spawns.put("func_timer", TimerKt::funcTimer);
        spawns.put("func_explosive", ExplosiveKt::funcExplosive);
        spawns.put("func_killbox", KillboxKt::funcKillbox);
        spawns.put("trigger_always", TriggersKt::triggerAlways);
        spawns.put("trigger_once", TriggersKt::triggerOnce);
        spawns.put("trigger_multiple", TriggersKt::triggerMultiple);
        spawns.put("trigger_relay", TriggersKt::triggerRelay);
        spawns.put("trigger_push", TriggersKt::triggerPush);
        spawns.put("trigger_hurt", TriggersKt::triggerHurt);
        spawns.put("trigger_key", TriggersKt::triggerKey);
        spawns.put("trigger_counter", TriggersKt::triggerCounter);
        spawns.put("trigger_elevator", TrainKt::triggerElevator);
        spawns.put("trigger_gravity", TriggersKt::triggerGravity);
        spawns.put("trigger_monsterjump", TriggersKt::triggerMonsterJump);
        spawns.put("target_temp_entity", TargetEntitiesKt::targetTempEntity);
        spawns.put("target_speaker", TargetEntitiesKt::targetSpeaker);
        spawns.put("target_explosion", TargetEntitiesKt::targetExplosion);
        addSpawnAdapter("target_changelevel", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_changelevel";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_changelevel(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_secret", TargetEntitiesKt::targetSecret);
        spawns.put("target_goal", TargetEntitiesKt::targetGoal);
        spawns.put("target_splash", TargetEntitiesKt::targetSplash);
        spawns.put("target_spawner", TargetEntitiesKt::targetSpawner);
        spawns.put("target_blaster", TargetEntitiesKt::targetBlaster);
        addSpawnAdapter("target_crosslevel_trigger", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_crosslevel_trigger";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_crosslevel_trigger(ent);
                return true;
            }
        });
        addSpawnAdapter("target_crosslevel_target", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_crosslevel_target";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_crosslevel_target(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("target_laser", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_laser";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTarget.SP_target_laser(ent, gameExports);
                return true;
            }
        });
        spawns.put("target_help", TargetEntitiesKt::targetHelp);
        spawns.put("target_lightramp", TargetEntitiesKt::targetLightramp);
        spawns.put("target_earthquake", TargetEntitiesKt::targetEarthquake);
        addSpawnAdapter("target_character", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_character";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_target_character(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("target_string", new EntThinkAdapter() {
            public String getID() {
                return "SP_target_string";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_target_string(ent);
                return true;
            }
        });
        addSpawnAdapter("worldspawn", SP_worldspawn);
        addSpawnAdapter("viewthing", new EntThinkAdapter() {
            public String getID() {
                return "SP_viewthing";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_viewthing(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("light", new EntThinkAdapter() {
            public String getID() {
                return "SP_light";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_light(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("light_mine1", new EntThinkAdapter() {
            public String getID() {
                return "SP_light_mine1";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_light_mine1(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("light_mine2", new EntThinkAdapter() {
            public String getID() {
                return "SP_light_mine2";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_light_mine2(ent, gameExports);
                return true;
            }
        });

        /*
         * QUAKED info_null (0 0.5 0) (-4 -4 -4) (4 4 4)
         * Used as a positional target for spotlights, etc.
         */
        spawns.put("info_null", (self, game) -> game.freeEntity(self));
        spawns.put("func_group", (self, game) -> game.freeEntity(self));
        spawns.put("info_notnull", InfoEntitiesKt::infoNotNull);
        addSpawnAdapter("path_corner", new EntThinkAdapter() {
            public String getID() {
                return "SP_path_corner";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_path_corner(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("point_combat", new EntThinkAdapter() {
            public String getID() {
                return "SP_point_combat";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_point_combat(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_explobox", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_explobox";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_explobox(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_banner", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_banner";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_banner(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_satellite_dish", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_satellite_dish";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_satellite_dish(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_gib_arm", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_gib_arm";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_gib_arm(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_gib_leg", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_gib_leg";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_gib_leg(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_gib_head", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_gib_head";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_gib_head(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_insane", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_insane";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Insane.SP_misc_insane(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_deadsoldier", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_deadsoldier";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_deadsoldier(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_viper", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_viper";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_viper(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_viper_bomb", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_viper_bomb";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_viper_bomb(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_bigviper", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_bigviper";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_bigviper(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_strogg_ship", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_strogg_ship";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_strogg_ship(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_teleporter", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_teleporter";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_teleporter(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_teleporter_dest", GameMisc.SP_misc_teleporter_dest);
        addSpawnAdapter("misc_blackhole", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_blackhole";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_blackhole(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_eastertank", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_eastertank";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_eastertank(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_easterchick", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_easterchick";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_easterchick(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("misc_easterchick2", new EntThinkAdapter() {
            public String getID() {
                return "SP_misc_easterchick2";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_misc_easterchick2(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_berserk", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_berserk";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Berserk.SP_monster_berserk(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_gladiator", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_gladiator";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Gladiator.SP_monster_gladiator(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_gunner", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_gunner";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Gunner.SP_monster_gunner(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_infantry", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_infantry";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Infantry.SP_monster_infantry(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_soldier_light", M_Soldier.SP_monster_soldier_light);
        addSpawnAdapter("monster_soldier", M_Soldier.SP_monster_soldier);
        addSpawnAdapter("monster_soldier_ss", M_Soldier.SP_monster_soldier_ss);
        addSpawnAdapter("monster_tank", M_Tank.SP_monster_tank);
        addSpawnAdapter("monster_tank_commander", M_Tank.SP_monster_tank);
        addSpawnAdapter("monster_medic", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_medic";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Medic.SP_monster_medic(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_flipper", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_flipper";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Flipper.SP_monster_flipper(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_chick", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_chick";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Chick.SP_monster_chick(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_parasite", M_Parasite.SP_monster_parasite);
        addSpawnAdapter("monster_flyer", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_flyer";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Flyer.SP_monster_flyer(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_brain", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_brain";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Brain.SP_monster_brain(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_floater", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_floater";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Float.SP_monster_floater(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_hover", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_hover";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Hover.SP_monster_hover(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_mutant", M_Mutant.SP_monster_mutant);
        addSpawnAdapter("monster_supertank", M_Supertank.SP_monster_supertank);
        addSpawnAdapter("monster_boss2", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_boss2";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Hornet.SP_monster_boss2(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_boss3_stand", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_boss3_stand";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Makron_Idle.SP_monster_boss3_stand(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_jorg", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_jorg";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                M_Makron_Jorg.SP_monster_jorg(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("monster_commander_body", new EntThinkAdapter() {
            public String getID() {
                return "SP_monster_commander_body";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameMisc.SP_monster_commander_body(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("turret_breach", new EntThinkAdapter() {
            public String getID() {
                return "SP_turret_breach";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTurret.SP_turret_breach(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("turret_base", new EntThinkAdapter() {
            public String getID() {
                return "SP_turret_base";
            }

            public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
                GameTurret.SP_turret_base(ent, gameExports);
                return true;
            }
        });
        addSpawnAdapter("turret_driver", new EntThinkAdapter() {
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
        if (!ent.st.set(key, value))
            if (!ent.setField(key, value))
                gameExports.gameImports.dprintf("??? The key [" + key + "] is not a field\n");

    }

    /**
     * ED_ParseEdict
     * <p>
     * Parses an edict out of the given string, returning the new position ed
     * should be a properly initialized empty edict.
     */
    @Deprecated
    private static void ED_ParseEdict(Com.ParseHelp ph, SubgameEntity ent, GameExportsImpl gameExports) {

        String keyname;
        String com_token;
        boolean init = false;

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
            G_ClearEdict(ent, gameExports);
        }
    }

    private static void G_ClearEdict(edict_t ent, GameExportsImpl gameExports) {
        gameExports.g_edicts[ent.index] = new SubgameEntity(ent.index);
    }


    /**
     * G_FindTeams
     * <p>
     * Chain together all entities with a matching team field.
     * <p>
     * All but the first will have the FL_TEAMSLAVE flag set. All but the last
     * will have the teamchain field set to the next one.
     *
     * fixme: n^2 complexity
     */
    private static void G_FindTeams(GameExportsImpl game) {
        var teams = new HashMap<String, ArrayList<SubgameEntity>>();
        // TODO check that num_edicts is not more than g_edicts.size()
        // TODO rename fields: teamchain -> next, teamslave -> teammember, teammaster -> teamlead

        for (int i = 1; i < game.num_edicts; i++) {
            SubgameEntity e = game.g_edicts[i];

            if (!e.inuse)
                continue;
            if (e.team == null)
                continue;
            if ((e.flags & GameDefines.FL_TEAMSLAVE) != 0)
                throw new RuntimeException("Some flags are already set. why?");

            var team = teams.get(e.team);

            if (team == null) {
                // teamlead
                e.teammaster = e;
                team = new ArrayList<>();
                team.add(e);
                teams.put(e.team, team);
            } else if (team.size() == 0) {
                throw new RuntimeException("Array exists but empty. why?");
            } else {
                // member
                e.teammaster = team.get(0);
                e.flags |= GameDefines.FL_TEAMSLAVE;
                team.get(team.size() - 1).teamchain = e;
                team.add(e);
            }
        }
    }

    static void SpawnEntities(String mapname, String entities, String spawnpoint, GameExportsImpl gameExports) {

        // todo: split into different functions

        gameExports.level.mapname = mapname;
        gameExports.game.spawnpoint = spawnpoint;

        final float skill = normalizeSkillCvar(gameExports);

        // set client fields on player ents
        for (int i = 0; i < gameExports.game.maxclients; i++)
            gameExports.g_edicts[i + 1].setClient(gameExports.game.clients[i]);

        EntityParserKt.parseEntities(entities).forEach(map -> {

            final SubgameEntity ent;
            if ("worldspawn".equals(map.get("classname")))
                ent = gameExports.g_edicts[0];
            else
                ent = gameExports.G_Spawn();

            map.forEach((key, value) -> ED_ParseField(key, value, ent, gameExports));


            // todo: add description
            // yet another map hack
            if ("command".equalsIgnoreCase(gameExports.level.mapname)
                    && "trigger_once".equalsIgnoreCase(ent.classname)
                    && "*27".equalsIgnoreCase(ent.model))
                ent.spawnflags &= ~GameDefines.SPAWNFLAG_NOT_HARD;

            boolean spawned = true;
            if (!ent.classname.equals("worldspawn")) {
                if (gameExports.gameCvars.deathmatch.value != 0) {
                    if ((ent.spawnflags & GameDefines.SPAWNFLAG_NOT_DEATHMATCH) != 0) {
                        spawned = false;
                    }
                } else {
                    // fixme: similar check for SPAWNFLAG_NOT_COOP?
                    if (skill == 0 && (ent.spawnflags & GameDefines.SPAWNFLAG_NOT_EASY) != 0
                                    || skill == 1 && (ent.spawnflags & GameDefines.SPAWNFLAG_NOT_MEDIUM) != 0
                                    // SPAWNFLAG_NOT_HARD implies not hard+ also
                                    || (skill == 2 || skill == 3) && (ent.spawnflags & GameDefines.SPAWNFLAG_NOT_HARD) != 0) {
                        spawned = false;
                    }
                }

                // reset difficulty spawnflags
                ent.spawnflags &= ~(GameDefines.SPAWNFLAG_NOT_EASY
                        | GameDefines.SPAWNFLAG_NOT_MEDIUM
                        | GameDefines.SPAWNFLAG_NOT_HARD
                        | GameDefines.SPAWNFLAG_NOT_COOP
                        | GameDefines.SPAWNFLAG_NOT_DEATHMATCH);
            }
            if (spawned) {
                gameExports.gameImports.dprintf("spawning ent[" + ent.index + "], classname=" +
                        ent.classname + ", flags= " + Integer.toHexString(ent.spawnflags) + "\n");
                ED_CallSpawn(ent, gameExports);
            } else {
                gameExports.freeEntity(ent);
            }
        });

        gameExports.gameImports.dprintf("player skill level:" + skill + "\n");
        G_FindTeams(gameExports);
        gameExports.playerTrail.Init();
    }

    private static float normalizeSkillCvar(GameExportsImpl gameExports) {
        final float skill = gameExports.gameCvars.skill.value;
        float skillNormalized = (float) Math.floor(skill);

        if (skillNormalized < 0)
            skillNormalized = 0;
        if (skillNormalized > 3)
            skillNormalized = 3;
        if (skill != skillNormalized)
            gameExports.gameImports.cvar_forceset("skill", "" + skillNormalized);
        return skill;
    }

    /**
     * Finds the spawn function for the entity and calls it.
     */
    public static void ED_CallSpawn(SubgameEntity ent, GameExportsImpl gameExports) {

        if (null == ent.classname) {
            gameExports.gameImports.dprintf("ED_CallSpawn: null classname\n");
            return;
        }

        // check item spawn functions
        var item = gameExports.items.stream()
                .filter(it -> ent.classname.equals(it.classname))
                .findFirst();
        if (item.isPresent()) {
            GameItems.SpawnItem(ent, item.get(), gameExports);
            return;
        }

        // check normal spawn functions
        var spawn = spawns.get(ent.classname.toLowerCase());
        if (spawn != null) {
            spawn.spawn(ent, gameExports);
            ent.st = null;
        } else {
            gameExports.gameImports.dprintf(ent.classname + " doesn't have a spawn function\n");
        }
    }


    static String[] mobs = { "monster_berserk", "monster_gladiator", "monster_gunner", "monster_infantry", "monster_soldier_light", "monster_soldier", "monster_soldier_ss", "monster_tank", "monster_tank_commander", "monster_medic", "monster_chick", "monster_parasite", "monster_flyer", "monster_brain", "monster_floater", "monster_mutant"};
    // for debugging and testing
    static void SpawnRandomMonster(SubgameEntity ent, GameExportsImpl gameExports){
        final int index = Lib.rand() % mobs.length;
        SpawnNewEntity(ent, Arrays.asList("spawn", mobs[index]), gameExports);

    }

    static void SpawnNewEntity(SubgameEntity creator, List<String> args, GameExportsImpl gameExports) {
        String className;
        if (args.size() >= 2)
            className = args.get(1);
        else {
            gameExports.gameImports.dprintf("usage: spawn <classname>\n");
            return;
        }

        if (gameExports.gameCvars.deathmatch.value != 0 && gameExports.gameCvars.sv_cheats.value == 0) {
            gameExports.gameImports.cprintf(creator, Defines.PRINT_HIGH,
                    "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        gameExports.gameImports.dprintf("Spawning " + className + " at " + Lib.vtofs(creator.s.origin) + ", " + Lib.vtofs(creator.s.angles) + "\n");

        var spawn = spawns.get(className);
        GameItem gitem_t = GameItems.FindItemByClassname(className, gameExports);
        if (spawn != null || gitem_t != null) {
            SubgameEntity newThing = gameExports.G_Spawn();

            putInFrontOfCreator(creator, newThing);

            newThing.classname = className;
            gameExports.gameImports.linkentity(newThing);
            if (spawn != null)
                spawn.spawn(newThing, gameExports);
            else
                GameItems.SpawnItem(newThing, gitem_t, gameExports);

            gameExports.gameImports.dprintf("Spawned!\n");
        }
    }

    private static void putInFrontOfCreator(SubgameEntity creator, SubgameEntity newThing) {
        float[] location = creator.s.origin;
        float[] offset = {0,0,0};
        float[] forward = { 0, 0, 0 };
        Math3D.AngleVectors(creator.s.angles, forward, null, null);
        Math3D.VectorNormalize(forward);
        Math3D.VectorScale(forward, 128, offset);
        Math3D.VectorAdd(location, offset, offset);
        newThing.s.origin = offset;
        newThing.s.angles[Defines.YAW] = creator.s.angles[Defines.YAW];
    }

    /**
     * Makes sense only for point entities
     */
    public static void createEntity(SubgameEntity creator, List<String> args, GameExportsImpl gameExports) {
        // hack: join back all the arguments, quoting keys and values
        // no comments are expected here
        String entities = args.stream()
                .skip(1)
                .filter(s -> !s.equals("}") && !s.equals("{"))
                .map(s -> '"' + s + '"')
                .collect(Collectors.joining(" ", "{", "}"));

        // actually we expect 1 entity
        EntityParserKt.parseEntities(entities).forEach(entity -> {
            SubgameEntity newThing = gameExports.G_Spawn();
            entity.forEach((key, value) -> ED_ParseField(key, value, newThing, gameExports));
            putInFrontOfCreator(creator, newThing);
            ED_CallSpawn(newThing, gameExports);
        });
    }
}
