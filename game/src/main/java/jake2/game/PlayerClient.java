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

// Created on 28.12.2003 by RST.

package jake2.game;

import jake2.game.adapters.EntDieAdapter;
import jake2.game.adapters.EntPainAdapter;
import jake2.game.adapters.EntThinkAdapter;
import jake2.game.items.GameItem;
import jake2.game.monsters.M_Player;
import jake2.qcommon.*;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.WeaponSoundMessage;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class PlayerClient {

    /**
     * player_die. 
     */
    static EntDieAdapter player_die = new EntDieAdapter() {
    	public String getID() { return "player_die"; }
        public void die(SubgameEntity self, SubgameEntity inflictor, SubgameEntity attacker,
                        int damage, float[] point, GameExportsImpl gameExports) {

            Math3D.VectorClear(self.avelocity);
    
            self.takedamage = Defines.DAMAGE_YES;
            self.movetype = GameDefines.MOVETYPE_TOSS;
    
            self.s.modelindex2 = 0; // remove linked weapon model
    
            self.s.angles[0] = 0;
            self.s.angles[2] = 0;
    
            self.s.sound = 0;
            gclient_t client = self.getClient();
            client.weapon_sound = 0;
    
            self.maxs[2] = -8;
    
            // self.solid = SOLID_NOT;
            self.svflags |= Defines.SVF_DEADMONSTER;

            if (self.deadflag == 0) {
                client.respawn_time = gameExports.level.time + 1.0f;
                PlayerClient.LookAtKiller(self, inflictor, attacker, gameExports.g_edicts[0]);
                client.getPlayerState().pmove.pm_type = Defines.PM_DEAD;
                ClientObituary(self, inflictor, attacker, gameExports);
                PlayerClient.TossClientWeapon(self, gameExports);
                if (gameExports.gameCvars.deathmatch.value != 0) {
                    gameExports.Help_f(self); // show scores
                }

                // clear inventory
                gameExports.items.forEach(it -> {
                    if (gameExports.gameCvars.coop.value != 0) {
                        if ((gameExports.items.get(it.index).flags & GameDefines.IT_KEY) != 0) {
                            // this is kind of ugly, but it's how we want to handle keys in coop
                            client.resp.coop_respawn.inventory[it.index] = client.pers.inventory[it.index];
                        }
                    }
                    client.pers.inventory[it.index] = 0;
                });
            }
    
            // remove powerups
            client.quad_framenum = 0;
            client.invincible_framenum = 0;
            client.breather_framenum = 0;
            client.enviro_framenum = 0;
            self.flags &= ~GameDefines.FL_POWER_ARMOR;
    
            if (self.health < -40) { // gib
                gameExports.gameImports
                        .sound(self, Defines.CHAN_BODY, gameExports.gameImports
                                .soundindex("misc/udeath.wav"), 1,
                                Defines.ATTN_NORM, 0);
                for (int n = 0; n < 4; n++)
                    GameMisc.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2",
                            damage, GameDefines.GIB_ORGANIC, gameExports);
                GameMisc.ThrowClientHead(self, damage, gameExports);
    
                self.takedamage = Defines.DAMAGE_NO;
            } else { // normal death
                if (self.deadflag == 0) {
    
                    gameExports.player_die_i = (gameExports.player_die_i + 1) % 3;
                    // start a death animation
                    client.anim_priority = Defines.ANIM_DEATH;
                    if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
                        self.s.frame = M_Player.FRAME_crdeath1 - 1;
                        client.anim_end = M_Player.FRAME_crdeath5;
                    } else
                        switch (gameExports.player_die_i) {
                        case 0:
                            self.s.frame = M_Player.FRAME_death101 - 1;
                            client.anim_end = M_Player.FRAME_death106;
                            break;
                        case 1:
                            self.s.frame = M_Player.FRAME_death201 - 1;
                            client.anim_end = M_Player.FRAME_death206;
                            break;
                        case 2:
                            self.s.frame = M_Player.FRAME_death301 - 1;
                            client.anim_end = M_Player.FRAME_death308;
                            break;
                        }
    
                    gameExports.gameImports.sound(self, Defines.CHAN_VOICE, gameExports.gameImports
                            .soundindex("*death" + ((Lib.rand() % 4) + 1)
                                    + ".wav"), 1, Defines.ATTN_NORM, 0);
                }
            }
    
            self.deadflag = GameDefines.DEAD_DEAD;
    
            gameExports.gameImports.linkentity(self);
        }
    };
    static EntThinkAdapter SP_FixCoopSpots = new EntThinkAdapter() {
    	public String getID() { return "SP_FixCoopSpots"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            float[] d = { 0, 0, 0 };

            SubgameEntity spot;
            EdictIterator es = null;
    
            while (true) {
                es = GameBase.G_Find(es, GameBase.findByClassName,
                        "info_player_start", gameExports);
    
                if (es == null)
                    return true;
                
                spot = es.o;
                
                if (spot.targetname == null)
                    continue;
                Math3D.VectorSubtract(self.s.origin, spot.s.origin, d);
                if (Math3D.VectorLength(d) < 384) {
                    if ((self.targetname == null)
                            || Lib.Q_stricmp(self.targetname, spot.targetname) != 0) {
                        // gi.dprintf("FixCoopSpots changed %s at %s targetname
                        // from %s to %s\n", self.classname,
                        // vtos(self.s.origin), self.targetname,
                        // spot.targetname);
                        self.targetname = spot.targetname;
                    }
                    return true;
                }
            }
        }
    };
    static EntThinkAdapter SP_CreateCoopSpots = new EntThinkAdapter() {
    	public String getID() { return "SP_CreateCoopSpots"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            if (Lib.Q_stricmp(gameExports.level.mapname, "security") == 0) {
                SubgameEntity spot = gameExports.G_Spawn();
                spot.classname = "info_player_coop";
                spot.s.origin[0] = 188 - 64;
                spot.s.origin[1] = -164;
                spot.s.origin[2] = 80;
                spot.targetname = "jail3";
                spot.s.angles[1] = 90;

                spot = gameExports.G_Spawn();
                spot.classname = "info_player_coop";
                spot.s.origin[0] = 188 + 64;
                spot.s.origin[1] = -164;
                spot.s.origin[2] = 80;
                spot.targetname = "jail3";
                spot.s.angles[1] = 90;

                spot = gameExports.G_Spawn();
                spot.classname = "info_player_coop";
                spot.s.origin[0] = 188 + 128;
                spot.s.origin[1] = -164;
                spot.s.origin[2] = 80;
                spot.targetname = "jail3";
                spot.s.angles[1] = 90;
            }
            return true;
        }
    };
    // player pain is handled at the end of the frame in P_DamageFeedback
    static EntPainAdapter player_pain = new EntPainAdapter() {
    	public String getID() { return "player_pain"; }
        public void pain(SubgameEntity self, SubgameEntity other, float kick, int damage, GameExportsImpl gameExports) {
        }
    };
    static EntDieAdapter body_die = new EntDieAdapter() {
    	public String getID() { return "body_die"; }
        public void die(SubgameEntity self, SubgameEntity inflictor, SubgameEntity attacker,
                        int damage, float[] point, GameExportsImpl gameExports) {
    
            int n;
    
            if (self.health < -40) {
                gameExports.gameImports.sound(self, Defines.CHAN_BODY,
                		gameExports.gameImports.soundindex("misc/udeath.wav"), 1, Defines.ATTN_NORM, 0);
                for (n = 0; n < 4; n++)
                    GameMisc.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage,
                            GameDefines.GIB_ORGANIC, gameExports);
                self.s.origin[2] -= 48;
                GameMisc.ThrowClientHead(self, damage, gameExports);
                self.takedamage = Defines.DAMAGE_NO;
            }
        }
    };

    /**
     * QUAKED info_player_start (1 0 0) (-16 -16 -24) (16 16 32) The normal
     * starting point for a level.
     */
    public static void SP_info_player_start(SubgameEntity self, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.coop.value == 0)
            return;
        if (Lib.Q_stricmp(gameExports.level.mapname, "security") == 0) {
            // invoke one of our gross, ugly, disgusting hacks
            self.think = PlayerClient.SP_CreateCoopSpots;
            self.nextthink = gameExports.level.time + Defines.FRAMETIME;
        }
    }

    /**
     * QUAKED info_player_deathmatch (1 0 1) (-16 -16 -24) (16 16 32) potential
     * spawning position for deathmatch games.
     */
    public static void SP_info_player_deathmatch(SubgameEntity self, GameExportsImpl gameExports) {
        if (0 == gameExports.gameCvars.deathmatch.value) {
            gameExports.freeEntity(self);
            return;
        }
        GameMisc.SP_misc_teleporter_dest.think(self, gameExports);
    }

    /**
     * QUAKED info_player_coop (1 0 1) (-16 -16 -24) (16 16 32) potential
     * spawning position for coop games.
     */

    public static void SP_info_player_coop(SubgameEntity self, GameExportsImpl gameExports) {
        if (0 == gameExports.gameCvars.coop.value) {
            gameExports.freeEntity(self);
            return;
        }

        if ((Lib.Q_stricmp(gameExports.level.mapname, "jail2") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "jail4") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "mine1") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "mine2") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "mine3") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "mine4") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "lab") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "boss1") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "fact3") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "biggun") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "space") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "command") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "power2") == 0)
                || (Lib.Q_stricmp(gameExports.level.mapname, "strike") == 0)) {
            // invoke one of our gross, ugly, disgusting hacks
            self.think = PlayerClient.SP_FixCoopSpots;
            self.nextthink = gameExports.level.time + Defines.FRAMETIME;
        }
    }

    /**
     * QUAKED info_player_intermission (1 0 1) (-16 -16 -24) (16 16 32) The
     * deathmatch intermission point will be at one of these Use 'angles'
     * instead of 'angle', so you can set pitch or roll as well as yaw. 'pitch
     * yaw roll'
     */
    public static void SP_info_player_intermission() {
    }

    public static void ClientObituary(SubgameEntity self,
                                      edict_t inflictor,
                                      SubgameEntity attacker,
                                      GameExportsImpl gameExports) {

        gclient_t attackerClient = attacker.getClient();
        if (gameExports.gameCvars.coop.value != 0 && attackerClient != null)
            self.meansOfDeath |= GameDefines.MOD_FRIENDLY_FIRE;

        gclient_t client = self.getClient();
        if (gameExports.gameCvars.deathmatch.value != 0 || gameExports.gameCvars.coop.value != 0) {
            boolean friendlyFire = (self.meansOfDeath & GameDefines.MOD_FRIENDLY_FIRE) != 0;
            int mod = self.meansOfDeath & ~GameDefines.MOD_FRIENDLY_FIRE;
            String message = null;
            String message2 = "";

            switch (mod) {
            case GameDefines.MOD_SUICIDE:
                message = "suicides";
                break;
            case GameDefines.MOD_FALLING:
                message = "cratered";
                break;
            case GameDefines.MOD_CRUSH:
                message = "was squished";
                break;
            case GameDefines.MOD_WATER:
                message = "sank like a rock";
                break;
            case GameDefines.MOD_SLIME:
                message = "melted";
                break;
            case GameDefines.MOD_LAVA:
                message = "does a back flip into the lava";
                break;
            case GameDefines.MOD_EXPLOSIVE:
            case GameDefines.MOD_BARREL:
                message = "blew up";
                break;
            case GameDefines.MOD_EXIT:
                message = "found a way out";
                break;
            case GameDefines.MOD_TARGET_LASER:
                message = "saw the light";
                break;
            case GameDefines.MOD_TARGET_BLASTER:
                message = "got blasted";
                break;
            case GameDefines.MOD_BOMB:
            case GameDefines.MOD_SPLASH:
            case GameDefines.MOD_TRIGGER_HURT:
                message = "was in the wrong place";
                break;
            }
            if (attacker == self) {
                switch (mod) {
                case GameDefines.MOD_HELD_GRENADE:
                    message = "tried to put the pin back in";
                    break;
                case GameDefines.MOD_HG_SPLASH:
                case GameDefines.MOD_G_SPLASH:
                    if (PlayerClient.IsNeutral(self))
                        message = "tripped on its own grenade";
                    else if (PlayerClient.IsFemale(self))
                        message = "tripped on her own grenade";
                    else
                        message = "tripped on his own grenade";
                    break;
                case GameDefines.MOD_R_SPLASH:
                    if (PlayerClient.IsNeutral(self))
                        message = "blew itself up";
                    else if (PlayerClient.IsFemale(self))
                        message = "blew herself up";
                    else
                        message = "blew himself up";
                    break;
                case GameDefines.MOD_BFG_BLAST:
                    message = "should have used a smaller gun";
                    break;
                default:
                    if (PlayerClient.IsNeutral(self))
                        message = "killed itself";
                    else if (PlayerClient.IsFemale(self))
                        message = "killed herself";
                    else
                        message = "killed himself";
                    break;
                }
            }
            if (message != null) {
                gameExports.gameImports.bprintf(Defines.PRINT_MEDIUM,
                        client.pers.netname + " " + message + ".\n");
                if (gameExports.gameCvars.deathmatch.value != 0)
                    client.resp.score--;
                self.enemy = null;
                return;
            }

            self.enemy = attacker;
            if (attacker != null && attackerClient != null) {
                switch (mod) {
                case GameDefines.MOD_BLASTER:
                    message = "was blasted by";
                    break;
                case GameDefines.MOD_SHOTGUN:
                    message = "was gunned down by";
                    break;
                case GameDefines.MOD_SSHOTGUN:
                    message = "was blown away by";
                    message2 = "'s super shotgun";
                    break;
                case GameDefines.MOD_MACHINEGUN:
                    message = "was machinegunned by";
                    break;
                case GameDefines.MOD_CHAINGUN:
                    message = "was cut in half by";
                    message2 = "'s chaingun";
                    break;
                case GameDefines.MOD_GRENADE:
                    message = "was popped by";
                    message2 = "'s grenade";
                    break;
                case GameDefines.MOD_G_SPLASH:
                    message = "was shredded by";
                    message2 = "'s shrapnel";
                    break;
                case GameDefines.MOD_ROCKET:
                    message = "ate";
                    message2 = "'s rocket";
                    break;
                case GameDefines.MOD_R_SPLASH:
                    message = "almost dodged";
                    message2 = "'s rocket";
                    break;
                case GameDefines.MOD_HYPERBLASTER:
                    message = "was melted by";
                    message2 = "'s hyperblaster";
                    break;
                case GameDefines.MOD_RAILGUN:
                    message = "was railed by";
                    break;
                case GameDefines.MOD_BFG_LASER:
                    message = "saw the pretty lights from";
                    message2 = "'s BFG";
                    break;
                case GameDefines.MOD_BFG_BLAST:
                    message = "was disintegrated by";
                    message2 = "'s BFG blast";
                    break;
                case GameDefines.MOD_BFG_EFFECT:
                    message = "couldn't hide from";
                    message2 = "'s BFG";
                    break;
                case GameDefines.MOD_HANDGRENADE:
                    message = "caught";
                    message2 = "'s handgrenade";
                    break;
                case GameDefines.MOD_HG_SPLASH:
                    message = "didn't see";
                    message2 = "'s handgrenade";
                    break;
                case GameDefines.MOD_HELD_GRENADE:
                    message = "feels";
                    message2 = "'s pain";
                    break;
                case GameDefines.MOD_TELEFRAG:
                    message = "tried to invade";
                    message2 = "'s personal space";
                    break;
                }
                if (message != null) {
                    gameExports.gameImports.bprintf(Defines.PRINT_MEDIUM,
                            client.pers.netname + " " + message + " "
                                    + attackerClient.pers.netname + " "
                                    + message2 + "\n");
                    if (gameExports.gameCvars.deathmatch.value != 0) {
                        if (friendlyFire)
                            attackerClient.resp.score--;
                        else
                            attackerClient.resp.score++;
                    }
                    return;
                }
            }
        }

        gameExports.gameImports.bprintf(Defines.PRINT_MEDIUM, client.pers.netname
                + " died.\n");
        if (gameExports.gameCvars.deathmatch.value != 0)
            client.resp.score--;
    }

    /**
     * This is only called when the game first initializes in single player, but
     * is called after each death and level change in deathmatch. 
     */
    public static void InitClientPersistant(gclient_t client, GameExportsImpl gameExports) {
        GameItem item;

        client.pers = new client_persistant_t();

        item = GameItems.FindItem("Blaster", gameExports);
        client.pers.selected_item = item.index;
        client.pers.inventory[client.pers.selected_item] = 1;

        /*
         * Give shotgun. item = FindItem("Shotgun"); client.pers.selected_item =
         * ITEM_INDEX(item); client.pers.inventory[client.pers.selected_item] =
         * 1;
         */

        client.pers.weapon = item;

        client.pers.health = 100;
        client.pers.max_health = 100;

        client.pers.max_bullets = 200;
        client.pers.max_shells = 100;
        client.pers.max_rockets = 50;
        client.pers.max_grenades = 50;
        client.pers.max_cells = 200;
        client.pers.max_slugs = 50;

        client.pers.connected = true;
    }

    public static void FetchClientEntData(SubgameEntity ent, GameExportsImpl gameExports) {
        gclient_t client = ent.getClient();
        ent.health = client.pers.health;
        ent.max_health = client.pers.max_health;
        ent.flags |= client.pers.savedFlags;
        if (gameExports.gameCvars.coop.value != 0)
            client.resp.score = client.pers.score;
    }

    /**
     * Returns the distance to the nearest player from the given spot.
     */
    static float PlayersRangeFromSpot(SubgameEntity spot, GameExportsImpl gameExports) {
        SubgameEntity player;
        float bestplayerdistance;
        float[] v = { 0, 0, 0 };
        int n;
        float playerdistance;

        bestplayerdistance = 9999999;

        for (n = 1; n <= gameExports.game.maxclients; n++) {
            player = gameExports.g_edicts[n];

            if (!player.inuse)
                continue;

            if (player.health <= 0)
                continue;

            Math3D.VectorSubtract(spot.s.origin, player.s.origin, v);
            playerdistance = Math3D.VectorLength(v);

            if (playerdistance < bestplayerdistance)
                bestplayerdistance = playerdistance;
        }

        return bestplayerdistance;
    }

    /**
     * Go to a random point, but NOT the two points closest to other players.
     * @param gameExports
     */
    public static SubgameEntity SelectRandomDeathmatchSpawnPoint(GameExportsImpl gameExports) {
        int count = 0;
        float range2;

        SubgameEntity spot;

        float range1 = range2 = 99999;
        SubgameEntity spot2;
        SubgameEntity spot1 = spot2 = null;

        EdictIterator es = null;

        while ((es = GameBase.G_Find(es, GameBase.findByClassName,
                "info_player_deathmatch", gameExports)) != null) {
            spot = es.o;
            count++;
            float range = PlayersRangeFromSpot(spot, gameExports);
            if (range < range1) {
                range1 = range;
                spot1 = spot;
            } else if (range < range2) {
                range2 = range;
                spot2 = spot;
            }
        }

        if (count == 0)
            return null;

        if (count <= 2) {
            spot1 = spot2 = null;
        } else
            count -= 2;

        int selection = Lib.rand() % count;

        spot = null;
        es = null;
        do {
            es = GameBase.G_Find(es, GameBase.findByClassName,
                    "info_player_deathmatch", gameExports);
            
            if (es == null) 
                break;
            
            spot = es.o;
            if (spot == spot1 || spot == spot2)
                selection++;
        } while (selection-- > 0);

        return spot;
    }

    /** 
	 * If turned on in the dmflags, select a spawn point far away from other players.
     * @param gameExports
     */
    static SubgameEntity SelectFarthestDeathmatchSpawnPoint(GameExportsImpl gameExports) {

        SubgameEntity spot;
        SubgameEntity bestspot = null;
        float bestdistance = 0;

        EdictIterator es = null;
        while ((es = GameBase.G_Find(es, GameBase.findByClassName, "info_player_deathmatch", gameExports)) != null) {
            spot = es.o;
            float bestplayerdistance = PlayersRangeFromSpot(spot, gameExports);

            if (bestplayerdistance > bestdistance) {
                bestspot = spot;
                bestdistance = bestplayerdistance;
            }
        }

        if (bestspot != null) {
            return bestspot;
        }

        // if there is a player just spawned on each and every start spot
        // we have no choice to turn one into a telefrag meltdown
        EdictIterator edit = GameBase.G_Find(null, GameBase.findByClassName, "info_player_deathmatch", gameExports);
        if (edit == null)
            return null;
        
        return edit.o;
    }

    
    public static SubgameEntity SelectDeathmatchSpawnPoint(GameExportsImpl gameExports) {
        if (0 != ((int) (gameExports.gameCvars.dmflags.value) & Defines.DF_SPAWN_FARTHEST))
            return SelectFarthestDeathmatchSpawnPoint(gameExports);
        else
            return SelectRandomDeathmatchSpawnPoint(gameExports);
    }

    public static SubgameEntity SelectCoopSpawnPoint(edict_t ent, GameExportsImpl gameExports) {

        //index = ent.client - game.clients;
        int index = ent.getClient().getIndex();

        // player 0 starts in normal player spawn point
        if (index == 0)
            return null;

        SubgameEntity spot;
        EdictIterator es = null;

        // assume there are four coop spots at each spawnpoint
        while (true) {

            es = GameBase.G_Find(es, GameBase.findByClassName, "info_player_coop", gameExports);
                    
            if (es == null)
                return null;
            
            spot = es.o;
                
            if (spot == null)
                return null; // we didn't have enough...

            String target = spot.targetname;
            if (target == null)
                target = "";
            if (Lib.Q_stricmp(gameExports.game.spawnpoint, target) == 0) {
                // this is a coop spawn point for one of the clients here
                index--;
                if (0 == index)
                    return spot; // this is it
            }
        }

    }

    /**
     * Chooses a player start, deathmatch start, coop start, etc.
     */
    public static void SelectSpawnPoint(edict_t ent, float[] origin, float[] angles, GameExportsImpl gameExports) {
        SubgameEntity spot = null;

        if (gameExports.gameCvars.deathmatch.value != 0)
            spot = SelectDeathmatchSpawnPoint(gameExports);
        else if (gameExports.gameCvars.coop.value != 0)
            spot = SelectCoopSpawnPoint(ent, gameExports);

        EdictIterator es = null;
        // find a single player start spot
        if (null == spot) {
            while ((es = GameBase.G_Find(es, GameBase.findByClassName, "info_player_start", gameExports)) != null) {
                spot = es.o;

                if (gameExports.game.spawnpoint.length() == 0
                        && spot.targetname == null)
                    break;

                if (gameExports.game.spawnpoint.length() == 0
                        || spot.targetname == null)
                    continue;

                if (Lib.Q_stricmp(gameExports.game.spawnpoint, spot.targetname) == 0)
                    break;
            }

            if (null == spot) {
                if (gameExports.game.spawnpoint.length() == 0) {
                    // there wasn't a spawnpoint without a
                    // target, so use any
                    es = GameBase.G_Find(es, GameBase.findByClassName, "info_player_start", gameExports);
                    
                    if (es != null)
                        spot = es.o;
                }
                if (null == spot) {
                    gameExports.gameImports.error("Couldn't find spawn point " + gameExports.game.spawnpoint + "\n");
                    return;
                }
            }
        }

        Math3D.VectorCopy(spot.s.origin, origin);
        origin[2] += 9;
        Math3D.VectorCopy(spot.s.angles, angles);
    }


    public static void InitBodyQue(GameExportsImpl gameExports) {

        gameExports.level.body_que = 0;
        for (int i = 0; i < GameDefines.BODY_QUEUE_SIZE; i++) {
            SubgameEntity ent = gameExports.G_Spawn();
            ent.classname = "bodyque";
        }
    }

    public static void CopyToBodyQue(SubgameEntity ent, GameExportsImpl gameExports) {

        // grab a body que and cycle to the next one
        int i = (int) gameExports.game.maxclients + gameExports.level.body_que + 1;
        SubgameEntity body = gameExports.g_edicts[i];
        gameExports.level.body_que = (gameExports.level.body_que + 1)
                % GameDefines.BODY_QUEUE_SIZE;

        // FIXME: send an effect on the removed body

        gameExports.gameImports.unlinkentity(ent);

        gameExports.gameImports.unlinkentity(body);
        body.s = ent.s.getClone();

        body.s.number = body.index;

        body.svflags = ent.svflags;
        Math3D.VectorCopy(ent.mins, body.mins);
        Math3D.VectorCopy(ent.maxs, body.maxs);
        Math3D.VectorCopy(ent.absmin, body.absmin);
        Math3D.VectorCopy(ent.absmax, body.absmax);
        Math3D.VectorCopy(ent.size, body.size);
        body.solid = ent.solid;
        body.clipmask = ent.clipmask;
        body.setOwner(ent.getOwner());
        body.movetype = ent.movetype;

        body.die = PlayerClient.body_die;
        body.takedamage = Defines.DAMAGE_YES;

        gameExports.gameImports.linkentity(body);
    }

    public static void respawn(SubgameEntity self, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.deathmatch.value != 0 || gameExports.gameCvars.coop.value != 0) {
            // spectator's don't leave bodies
            if (self.movetype != GameDefines.MOVETYPE_NOCLIP)
                CopyToBodyQue(self, gameExports);
            self.svflags &= ~Defines.SVF_NOCLIENT;
            PutClientInServer(self, gameExports);

            // add a teleportation effect
            self.s.event = Defines.EV_PLAYER_TELEPORT;

            // hold in place briefly
            gclient_t client = self.getClient();
            client.getPlayerState().pmove.pm_flags = Defines.PMF_TIME_TELEPORT;
            client.getPlayerState().pmove.pm_time = 14;

            client.respawn_time = gameExports.level.time;

            return;
        }

        // single player only
        // restart the entire server
        gameExports.gameImports.AddCommandString("menu_loadgame\n");
    }

    static boolean passwdOK(String i1, String i2) {
        if (i1.length() != 0 && !i1.equals("none") && !i1.equals(i2))
            return false;
        return true;
    }


    /**
     * Called when a player connects to a server or respawns in a deathmatch.
     */
    public static void PutClientInServer(SubgameEntity ent, GameExportsImpl gameExports) {
        float[] mins = { -16, -16, -24 };
        float[] maxs = { 16, 16, 32 };
        int index;
        float[] spawn_origin = { 0, 0, 0 }, spawn_angles = { 0, 0, 0 };
        gclient_t client;
        int i;
        client_persistant_t saved = new client_persistant_t();
        client_respawn_t resp = new client_respawn_t();

        // find a spawn point
        // do it before setting health back up, so farthest
        // ranging doesn't count this client
        SelectSpawnPoint(ent, spawn_origin, spawn_angles, gameExports);

        index = ent.index - 1;
        client = ent.getClient();

        // deathmatch wipes most client data every spawn
        if (gameExports.gameCvars.deathmatch.value != 0) {

            resp.set(client.resp);
            String userinfo = client.pers.userinfo;
            InitClientPersistant(client, gameExports);

            userinfo = ClientUserinfoChanged(ent, userinfo, gameExports);

        } else if (gameExports.gameCvars.coop.value != 0) {

            resp.set(client.resp);

            String userinfo = client.pers.userinfo;

            resp.coop_respawn.game_helpchanged = client.pers.game_helpchanged;
            resp.coop_respawn.helpchanged = client.pers.helpchanged;
            client.pers.set(resp.coop_respawn);
            userinfo = ClientUserinfoChanged(ent, userinfo, gameExports);
            if (resp.score > client.pers.score)
                client.pers.score = resp.score;
        } else {
            resp.clear();
        }

        // clear everything but the persistant data
        saved.set(client.pers);
        client.clear();
        client.pers.set(saved);
        if (client.pers.health <= 0)
            InitClientPersistant(client, gameExports);

        client.resp.set(resp);

        // copy some data from the client to the entity
        FetchClientEntData(ent, gameExports);

        // clear entity values
        ent.groundentity = null;
        ent.setClient(gameExports.game.clients[index]);
        ent.takedamage = Defines.DAMAGE_AIM;
        ent.movetype = GameDefines.MOVETYPE_WALK;
        ent.viewheight = 22;
        ent.inuse = true;
        ent.classname = "player";
        ent.mass = 200;
        ent.solid = Defines.SOLID_BBOX;
        ent.deadflag = GameDefines.DEAD_NO;
        ent.air_finished = gameExports.level.time + 12;
        ent.clipmask = Defines.MASK_PLAYERSOLID;
        ent.model = "players/male/tris.md2";
        ent.pain = PlayerClient.player_pain;
        ent.die = PlayerClient.player_die;
        ent.waterlevel = 0;
        ent.watertype = 0;
        ent.flags &= ~GameDefines.FL_NO_KNOCKBACK;
        ent.svflags &= ~Defines.SVF_DEADMONSTER;

        Math3D.VectorCopy(mins, ent.mins);
        Math3D.VectorCopy(maxs, ent.maxs);
        Math3D.VectorClear(ent.velocity);

        // clear playerstate values
        ent.getClient().getPlayerState().clear();

        client.getPlayerState().pmove.origin[0] = (short) (spawn_origin[0] * 8);
        client.getPlayerState().pmove.origin[1] = (short) (spawn_origin[1] * 8);
        client.getPlayerState().pmove.origin[2] = (short) (spawn_origin[2] * 8);

        if (gameExports.gameCvars.deathmatch.value != 0
                && 0 != ((int) gameExports.gameCvars.dmflags.value & Defines.DF_FIXED_FOV)) {
            client.getPlayerState().fov = 90;
        } else {
            client.getPlayerState().fov = Lib.atoi(Info.Info_ValueForKey(
                    client.pers.userinfo, "fov"));
            if (client.getPlayerState().fov < 1)
                client.getPlayerState().fov = 90;
            else if (client.getPlayerState().fov > 160)
                client.getPlayerState().fov = 160;
        }

        client.getPlayerState().gunindex = gameExports.gameImports
                .modelindex(client.pers.weapon.view_model);

        // clear entity state values
        ent.s.effects = 0;
        ent.s.modelindex = 255; // will use the skin specified model
        ent.s.modelindex2 = 255; // custom gun model
        // sknum is player num and weapon number
        // weapon number will be added in changeweapon
        ent.s.skinnum = ent.index - 1;

        ent.s.frame = 0;
        Math3D.VectorCopy(spawn_origin, ent.s.origin);
        ent.s.origin[2] += 1; // make sure off ground
        Math3D.VectorCopy(ent.s.origin, ent.s.old_origin);

        // set the delta angle
        for (i = 0; i < 3; i++) {
            client.getPlayerState().pmove.delta_angles[i] = (short) Math3D
                    .ANGLE2SHORT(spawn_angles[i] - client.resp.cmd_angles[i]);
        }

        ent.s.angles[Defines.PITCH] = 0;
        ent.s.angles[Defines.YAW] = spawn_angles[Defines.YAW];
        ent.s.angles[Defines.ROLL] = 0;
        Math3D.VectorCopy(ent.s.angles, client.getPlayerState().viewangles);
        Math3D.VectorCopy(ent.s.angles, client.v_angle);

        // spawn a spectator
        if (client.pers.spectator) {
            client.chase_target = null;

            client.resp.spectator = true;

            ent.movetype = GameDefines.MOVETYPE_NOCLIP;
            ent.solid = Defines.SOLID_NOT;
            ent.svflags |= Defines.SVF_NOCLIENT;
            ent.getClient().getPlayerState().gunindex = 0;
            gameExports.gameImports.linkentity(ent);
            return;
        } else
            client.resp.spectator = false;

        if (!GameUtil.KillBox(ent, gameExports)) { // could't spawn in?
        }

        gameExports.gameImports.linkentity(ent);

        // force the current weapon up
        client.newweapon = client.pers.weapon;
        PlayerWeapon.ChangeWeapon(ent, gameExports);
    }

    /**
     * A client has just connected to the server in deathmatch mode, so clear
     * everything out before starting them.
     */
    public static void ClientBeginDeathmatch(SubgameEntity ent, GameExportsImpl gameExports) {
        ent.G_InitEdict(ent.index);

        gclient_t client = ent.getClient();
        client.InitClientResp(gameExports);

        // locate ent at a spawn point
        PutClientInServer(ent, gameExports);

        if (gameExports.level.intermissiontime != 0) {
            PlayerHud.MoveClientToIntermission(ent, gameExports);
        } else {
            // send effect
            gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_LOGIN), MulticastTypes.MULTICAST_PVS);
        }

        gameExports.gameImports.bprintf(Defines.PRINT_HIGH, client.pers.netname
                + " entered the game\n");

        // make sure all view stuff is valid
        gameExports.playerView.ClientEndServerFrame(ent, gameExports);
    }

    static void ClientBegin(SubgameEntity ent, GameExportsImpl gameExports) {

        //ent.client = game.clients + (ent - g_edicts - 1);
        ent.setClient(gameExports.game.clients[ent.index - 1]);

        if (gameExports.gameCvars.deathmatch.value != 0) {
            ClientBeginDeathmatch(ent, gameExports);
            return;
        }

        // if there is already a body waiting for us (a loadgame), just
        // take it, otherwise spawn one from scratch
        gclient_t client = ent.getClient();
        if (ent.inuse == true) {
            // the client has cleared the client side viewangles upon
            // connecting to the server, which is different than the
            // state when the game is saved, so we need to compensate
            // with deltaangles
            for (int i = 0; i < 3; i++)
                client.getPlayerState().pmove.delta_angles[i] = (short) Math3D
                        .ANGLE2SHORT(client.getPlayerState().viewangles[i]);
        } else {
            // a spawn point will completely reinitialize the entity
            // except for the persistant data that was initialized at
            // ClientConnect() time
            ent.G_InitEdict(ent.index);
            ent.classname = "player";
            client.InitClientResp(gameExports);
            PutClientInServer(ent, gameExports);
        }

        if (gameExports.level.intermissiontime != 0) {
            PlayerHud.MoveClientToIntermission(ent, gameExports);
        } else {
            // send effect if in a multiplayer game
            if (gameExports.game.maxclients > 1) {
                gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_LOGIN), MulticastTypes.MULTICAST_PVS);

                gameExports.gameImports.bprintf(Defines.PRINT_HIGH, client.pers.netname
                        + " entered the game\n");
            }
        }

        // make sure all view stuff is valid
        gameExports.playerView.ClientEndServerFrame(ent, gameExports);
    }

    static String ClientUserinfoChanged(SubgameEntity ent, String userinfo, GameExportsImpl gameExports) {
        String s;
        int playernum;

        // check for malformed or illegal info strings
        if (!Info.Info_Validate(userinfo)) {
            return "\\name\\badinfo\\skin\\male/grunt";
        }

        // set name
        s = Info.Info_ValueForKey(userinfo, "name");

        gclient_t client = ent.getClient();
        client.pers.netname = s;

        // set spectator
        s = Info.Info_ValueForKey(userinfo, "spectator");
        // spectators are only supported in deathmatch
        if (gameExports.gameCvars.deathmatch.value != 0 && !s.equals("0"))
            client.pers.spectator = true;
        else
            client.pers.spectator = false;

        // set skin
        s = Info.Info_ValueForKey(userinfo, "skin");

        playernum = ent.index - 1;

        // combine name and skin into a configstring
        gameExports.gameImports.configstring(Defines.CS_PLAYERSKINS + playernum,
                client.pers.netname + "\\" + s);

        // fov
        if (gameExports.gameCvars.deathmatch.value != 0
                && 0 != ((int) gameExports.gameCvars.dmflags.value & Defines.DF_FIXED_FOV)) {
            client.getPlayerState().fov = 90;
        } else {
            client.getPlayerState().fov = Lib
                    .atoi(Info.Info_ValueForKey(userinfo, "fov"));
            if (client.getPlayerState().fov < 1)
                client.getPlayerState().fov = 90;
            else if (client.getPlayerState().fov > 160)
                client.getPlayerState().fov = 160;
        }

        // handedness
        s = Info.Info_ValueForKey(userinfo, "hand");
        if (s.length() > 0) {
            client.pers.hand = Lib.atoi(s);
        }

        // save off the userinfo in case we want to check something later
        client.pers.userinfo = userinfo;

        return userinfo;
    }

    /**
     * Run checks before the clients is allowed to connect and then connect the client
     * @return if client successfully connected
     */
    static boolean ClientConnect(SubgameEntity ent, String userinfo, GameExportsImpl gameExports) {
        // check to see if they are on the banned IP list
        String ip = Info.Info_ValueForKey(userinfo, "ip");
        if (GameSVCmds.SV_FilterPacket(ip, gameExports.gameCvars.filterban.value)) {
            Info.Info_SetValueForKey(userinfo, "rejmsg", "Banned.");
            return false;
        }

        // check for a spectator
        String spectator = Info.Info_ValueForKey(userinfo, "spectator");
        if (gameExports.gameCvars.deathmatch.value != 0 && spectator.length() != 0 && !"0".equals(spectator)) {

            // check for spectator password
            if (!passwdOK(gameExports.gameCvars.spectator_password.string, spectator)) {
                Info.Info_SetValueForKey(userinfo, "rejmsg", "Spectator password required or incorrect.");
                return false;
            }

            // check spectators limit
            int numspec;
            for (int i = numspec = 0; i < gameExports.game.maxclients; i++) {
                gclient_t other = gameExports.g_edicts[i + 1].getClient();
                if (gameExports.g_edicts[i + 1].inuse && other.pers.spectator)
                    numspec++;
            }

            if (numspec >= gameExports.gameCvars.maxspectators.value) {
                Info.Info_SetValueForKey(userinfo, "rejmsg", "Server spectator limit is full.");
                return false;
            }
        } else {
            // check for a password
            String password = Info.Info_ValueForKey(userinfo, "password");
            if (!passwdOK(gameExports.gameCvars.spectator_password.string, password)) {
                Info.Info_SetValueForKey(userinfo, "rejmsg", "Password required or incorrect.");
                return false;
            }
        }

        // they can connect
        ent.setClient(gameExports.game.clients[ent.index - 1]);

        // if there is already a body waiting for us (a loadgame), just
        // take it, otherwise spawn one from scratch
        gclient_t client = ent.getClient();
        if (!ent.inuse) {
            // clear the respawning variables
            client.InitClientResp(gameExports);
            if (!gameExports.game.autosaved || null == client.pers.weapon)
                InitClientPersistant(client, gameExports);
        }

        ClientUserinfoChanged(ent, userinfo, gameExports);

        if (gameExports.game.maxclients > 1)
            gameExports.gameImports.dprintf(client.pers.netname + " connected\n");

        ent.svflags = 0; // make sure we start with known default
        client.pers.connected = true;
        return true;
    }

    static void ClientDisconnect(SubgameEntity ent, GameImports gameImports) {

        gclient_t client = ent.getClient();
        if (client == null)
            return;

        gameImports.bprintf(Defines.PRINT_HIGH, client.pers.netname
                + " disconnected\n");

        // send effect
        gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_LOGOUT), MulticastTypes.MULTICAST_PVS);

        gameImports.unlinkentity(ent);
        ent.s.modelindex = 0;
        ent.solid = Defines.SOLID_NOT;
        ent.inuse = false;
        ent.classname = "disconnected";
        client.pers.connected = false;

        int playernum = ent.index - 1;
        gameImports.configstring(Defines.CS_PLAYERSKINS + playernum, "");
    }

    /*
     * static int CheckBlock(int c) 
     * { 
     * 		int v, i; 
     * 		v = 0; 
     * 		for (i = 0; i < c; i++)
     *			v += ((byte *) b)[i]; 
     *		return v; 
     * }
     * 
     * public static void PrintPmove(pmove_t * pm) 
     * { 
     *		unsigned c1, c2;
     * 
     * 		c1 = CheckBlock(&pm.s, sizeof(pm.s));
     * 		c2 = CheckBlock(&pm.cmd, sizeof(pm.cmd)); 
     *      Com_Printf("sv %3i:%i %i\n", pm.cmd.impulse, c1, c2); 
     * }
     */

    static void ClientThink(SubgameEntity ent, usercmd_t ucmd, GameExportsImpl gameExports) {

        gameExports.level.current_entity = ent;
        gclient_t client = ent.getClient();

        if (gameExports.level.intermissiontime != 0) {
            client.getPlayerState().pmove.pm_type = Defines.PM_FREEZE;
            // can exit intermission after five seconds
            if (gameExports.level.time > gameExports.level.intermissiontime + 5.0f
                    && 0 != (ucmd.buttons & Defines.BUTTON_ANY))
                gameExports.level.exitintermission = true;
            return;
        }

        gameExports.pm_passent = ent;

        SubgameEntity other;
        int i;
        if (client.chase_target != null) {

            client.resp.cmd_angles[0] = Math3D.SHORT2ANGLE(ucmd.angles[0]);
            client.resp.cmd_angles[1] = Math3D.SHORT2ANGLE(ucmd.angles[1]);
            client.resp.cmd_angles[2] = Math3D.SHORT2ANGLE(ucmd.angles[2]);

        } else {

            // set up for pmove
            pmove_t pm = new pmove_t();

            if (ent.movetype == GameDefines.MOVETYPE_NOCLIP)
                client.getPlayerState().pmove.pm_type = Defines.PM_SPECTATOR;
            else if (ent.s.modelindex != 255)
                client.getPlayerState().pmove.pm_type = Defines.PM_GIB;
            else if (ent.deadflag != 0)
                client.getPlayerState().pmove.pm_type = Defines.PM_DEAD;
            else
                client.getPlayerState().pmove.pm_type = Defines.PM_NORMAL;

            client.getPlayerState().pmove.gravity = (short) gameExports.gameCvars.sv_gravity.value;
            pm.s.set(client.getPlayerState().pmove);

            for (i = 0; i < 3; i++) {
                pm.s.origin[i] = (short) (ent.s.origin[i] * 8);
                pm.s.velocity[i] = (short) (ent.velocity[i] * 8);
            }

            if (client.old_pmove.equals(pm.s)) {
                pm.snapinitial = true;
                // gi.dprintf ("pmove changed!\n");
            }

            // this should be a copy
            pm.cmd.set(ucmd);

            // pmove doesn't need to know about passent and contentmask
            // adds default parms
            pm.trace = new pmove_t.TraceAdapter() {

                public trace_t trace(float[] start, float[] mins, float[] maxs,
                        float[] end) {
                    if (gameExports.pm_passent.health > 0)
                        return gameExports.gameImports.trace(start, mins, maxs, end, gameExports.pm_passent,
                                Defines.MASK_PLAYERSOLID);
                    else
                        return gameExports.gameImports.trace(start, mins, maxs, end, gameExports.pm_passent,
                                Defines.MASK_DEADSOLID);
                }

            };
            pm.pointcontents = gameExports.gameImports::getPointContents;

            // perform a pmove
            gameExports.gameImports.Pmove(pm);

            // save results of pmove
            client.getPlayerState().pmove.set(pm.s);
            client.old_pmove.set(pm.s);

            for (i = 0; i < 3; i++) {
                ent.s.origin[i] = pm.s.origin[i] * 0.125f;
                ent.velocity[i] = pm.s.velocity[i] * 0.125f;
            }

            Math3D.VectorCopy(pm.mins, ent.mins);
            Math3D.VectorCopy(pm.maxs, ent.maxs);

            client.resp.cmd_angles[0] = Math3D.SHORT2ANGLE(ucmd.angles[0]);
            client.resp.cmd_angles[1] = Math3D.SHORT2ANGLE(ucmd.angles[1]);
            client.resp.cmd_angles[2] = Math3D.SHORT2ANGLE(ucmd.angles[2]);

            if (ent.groundentity != null && null == pm.groundentity
                    && (pm.cmd.upmove >= 10) && (pm.waterlevel == 0)) {
                gameExports.gameImports.sound(ent, Defines.CHAN_VOICE, gameExports.gameImports
                        .soundindex("*jump1.wav"), 1, Defines.ATTN_NORM, 0);
                PlayerWeapon.PlayerNoise(ent, ent.s.origin, GameDefines.PNOISE_SELF, gameExports);
            }

            ent.viewheight = (int) pm.viewheight;
            ent.waterlevel = (int) pm.waterlevel;
            ent.watertype = pm.watertype;
            ent.groundentity = pm.groundentity;
            if (pm.groundentity != null)
                ent.groundentity_linkcount = pm.groundentity.linkcount;

            if (ent.deadflag != 0) {
                client.getPlayerState().viewangles[Defines.ROLL] = 40;
                client.getPlayerState().viewangles[Defines.PITCH] = -15;
                client.getPlayerState().viewangles[Defines.YAW] = client.killer_yaw;
            } else {
                Math3D.VectorCopy(pm.viewangles, client.v_angle);
                Math3D.VectorCopy(pm.viewangles, client.getPlayerState().viewangles);
            }

            gameExports.gameImports.linkentity(ent);

            if (ent.movetype != GameDefines.MOVETYPE_NOCLIP)
                GameBase.G_TouchTriggers(ent, gameExports);

            // touch other objects
            for (i = 0; i < pm.numtouch; i++) {
                other = (SubgameEntity) pm.touchents[i];
                int j;
                for (j = 0; j < i; j++)
                    if (pm.touchents[j] == other)
                        break;
                if (j != i)
                    continue; // duplicated
                if (other.touch == null)
                    continue;
                other.touch.touch(other, ent, GameBase.dummyplane, null, gameExports);
            }

        }

        client.oldbuttons = client.buttons;
        client.buttons = ucmd.buttons;
        client.latched_buttons |= client.buttons & ~client.oldbuttons;

        // save light level the player is standing on for
        // monster sighting AI
        ent.light_level = ucmd.lightlevel;

        // fire weapon from final position if needed
        if ((client.latched_buttons & Defines.BUTTON_ATTACK) != 0) {
            if (client.resp.spectator) {

                client.latched_buttons = 0;

                if (client.chase_target != null) {
                    client.chase_target = null;
                    client.getPlayerState().pmove.pm_flags &= ~Defines.PMF_NO_PREDICTION;
                } else
                    GameChase.GetChaseTarget(ent, gameExports);

            } else if (!client.weapon_thunk) {
                client.weapon_thunk = true;
                PlayerWeapon.Think_Weapon(ent, gameExports);
            }
        }

        if (client.resp.spectator) {
            if (ucmd.upmove >= 10) {
                if (0 == (client.getPlayerState().pmove.pm_flags & Defines.PMF_JUMP_HELD)) {
                    client.getPlayerState().pmove.pm_flags |= Defines.PMF_JUMP_HELD;
                    if (client.chase_target != null)
                        GameChase.ChaseNext(ent, gameExports);
                    else
                        GameChase.GetChaseTarget(ent, gameExports);
                }
            } else
                client.getPlayerState().pmove.pm_flags &= ~Defines.PMF_JUMP_HELD;
        }

        // update chase cam if being followed
        for (i = 1; i <= gameExports.game.maxclients; i++) {
            other = gameExports.g_edicts[i];
            gclient_t otherClient = other.getClient();
            if (other.inuse && otherClient.chase_target == ent)
                GameChase.UpdateChaseCam(other, gameExports);
        }
    }

    /**
     * Returns true, if the players gender flag was set to female. 
     */
    public static boolean IsFemale(SubgameEntity ent) {
        char info;

        gclient_t client = ent.getClient();
        if (client == null)
            return false;
    
        info = Info.Info_ValueForKey(client.pers.userinfo, "gender")
                .charAt(0);
        if (info == 'f' || info == 'F')
            return true;
        return false;
    }

    /**
     * Returns true, if the players gender flag was neither set to female nor to
     * male.
     */
    public static boolean IsNeutral(SubgameEntity ent) {
        char info;

        gclient_t client = ent.getClient();
        if (client == null)
            return false;
    
        info = Info.Info_ValueForKey(client.pers.userinfo, "gender")
                .charAt(0);
    
        if (info != 'f' && info != 'F' && info != 'm' && info != 'M')
            return true;
        return false;
    }

    /**
     * Changes the camera view to look at the killer.
     */
    public static void LookAtKiller(SubgameEntity self, edict_t inflictor,
                                    edict_t attacker, SubgameEntity world) {
        float dir[] = { 0, 0, 0 };
    
        gclient_t client = self.getClient();
        if (attacker != null && attacker != world && attacker != self) {
            Math3D.VectorSubtract(attacker.s.origin, self.s.origin, dir);
        } else if (inflictor != null && inflictor != world && inflictor != self) {
            Math3D.VectorSubtract(inflictor.s.origin, self.s.origin, dir);
        } else {
            client.killer_yaw = self.s.angles[Defines.YAW];
            return;
        }
    
        if (dir[0] != 0)
            client.killer_yaw = (float) (180 / Math.PI * Math.atan2(
                    dir[1], dir[0]));
        else {
            client.killer_yaw = 0;
            if (dir[1] > 0)
                client.killer_yaw = 90;
            else if (dir[1] < 0)
                client.killer_yaw = -90;
        }
        if (client.killer_yaw < 0)
            client.killer_yaw += 360;
    
    }
    
    
    /** 
     * Drop items and weapons in deathmatch games. 
     */ 
    public static void TossClientWeapon(SubgameEntity self, GameExportsImpl gameExports) {

        if (gameExports.gameCvars.deathmatch.value == 0)
            return;

        gclient_t client = self.getClient();
        GameItem item = client.pers.weapon;
        if (0 == client.pers.inventory[client.ammo_index])
            item = null;
        if (item != null && ("Blaster".equals(item.pickup_name)))
            item = null;

        boolean quad;
        if (0 == ((int) (gameExports.gameCvars.dmflags.value) & Defines.DF_QUAD_DROP))
            quad = false;
        else
            quad = (client.quad_framenum > (gameExports.level.framenum + 10));

        float spread;
        if (item != null && quad)
            spread = 22.5f;
        else
            spread = 0.0f;

        SubgameEntity drop;
        if (item != null) {
            client.v_angle[Defines.YAW] -= spread;
            drop = GameItems.Drop_Item(self, item, gameExports);
            client.v_angle[Defines.YAW] += spread;
            drop.spawnflags = GameDefines.DROPPED_PLAYER_ITEM;
        }
    
        if (quad) {
            client.v_angle[Defines.YAW] += spread;
            drop = GameItems.Drop_Item(self, GameItems
                    .FindItemByClassname("item_quad", gameExports), gameExports);
            client.v_angle[Defines.YAW] -= spread;
            drop.spawnflags |= GameDefines.DROPPED_PLAYER_ITEM;
    
            drop.touch = GameItems.Touch_Item;
            drop.nextthink = gameExports.level.time
                    + (client.quad_framenum - gameExports.level.framenum)
                    * Defines.FRAMETIME;
            drop.think = GameUtil.G_FreeEdictA;
        }
    }
}
