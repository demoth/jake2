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

// Created on 18.11.2003 by RST.
// $Id: GameSpawn.java,v 1.24 2004-06-03 21:32:51 rst Exp $

package jake2.game;

import jake2.Defines;
import jake2.util.*;
import jake2.qcommon.*;

public class GameSpawn extends GameSave {

//	static EntThinkAdapter SP_func_killbox = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//
//	static EntThinkAdapter SP_trigger_always = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_once = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_multiple = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_relay = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_push = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_hurt = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_key = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_counter = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_elevator = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_gravity = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_trigger_monsterjump = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//
//	static EntThinkAdapter SP_target_temp_entity = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_speaker = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_explosion = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_changelevel = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_secret = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_goal = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_splash = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_spawner = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_blaster = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_crosslevel_trigger = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_crosslevel_target = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_laser = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_help = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_actor = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_lightramp = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_earthquake = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_character = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_target_string = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//
//	//static EntThinkAdapter SP_worldspawn = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_viewthing = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//
//	static EntThinkAdapter SP_light = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_light_mine1 = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_light_mine2 = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_info_null = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_info_notnull = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_path_corner = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_point_combat = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//
//	static EntThinkAdapter SP_misc_explobox = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_banner = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_satellite_dish = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_actor = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_gib_arm = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_gib_leg = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_gib_head = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_insane = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_deadsoldier = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_viper = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_viper_bomb = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_bigviper = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_strogg_ship = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_teleporter = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_teleporter_dest = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_blackhole = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_eastertank = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_easterchick = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_misc_easterchick2 = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//
//	static EntThinkAdapter SP_monster_berserk = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_gladiator = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_gunner = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_infantry = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_soldier_light = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_soldier = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_soldier_ss = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_tank = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_medic = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_flipper = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_chick = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_parasite = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_flyer = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_brain = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_floater = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_hover = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_mutant = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_supertank = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_boss2 = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_jorg = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_monster_boss3_stand = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//
//	static EntThinkAdapter SP_monster_commander_body = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//
//	static EntThinkAdapter SP_turret_breach = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_turret_base = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
//	static EntThinkAdapter SP_turret_driver = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};

	/*
	=============
	ED_NewString
	=============
	*/
	static String ED_NewString(String string) {

		//String newb, new_p;
		int i, l;

		l = string.length() + 1;
		//newb = gi.TagMalloc(l, TAG_LEVEL);

		StringBuffer newb = new StringBuffer(l);

		for (i = 0; i < l - 1; i++) {
			char c;

			c = string.charAt(i);
			if (c == '\\' && i < l - 1) {
				c = string.charAt(i++);
				if (c == 'n')
					newb.append('\n');
				else
					newb.append('\\');
			}
			else
				newb.append(c);
		}

		return newb.toString();
	}

	/*
	===============
	ED_ParseField
	
	Takes a key/value pair and sets the binary values
	in an edict
	===============
	*/
	static void ED_ParseField(String key, String value, edict_t ent) {
		field_t f1;
		byte b;
		float v;
		float[] vec = { 0, 0, 0 };

		if (key.equals("nextmap"))
			Com.p("nextmap: " + value);
		if (!st.set(key, value))
			if (!ent.set(key, value))
				gi.dprintf("??? The key [" + key + "] is not a field\n");



		/** OLD CODE, delegated to ent.set(...) and st.set(...) 
		
		for (f = fields; f.name; f++) {
			if (!(f.flags & FFL_NOSPAWN) && !Q_stricmp(f.name, key)) {
				// found it
				if (f.flags & FFL_SPAWNTEMP)
					b = (byte *) & st;
				else
					b = (byte *) ent;
		
				switch (f.type) {
					case F_LSTRING :
						* (String *) (b + f.ofs) = ED_NewString(value);
						break;
					case F_VECTOR :
						sscanf(value, "%f %f %f", & vec[0], & vec[1], & vec[2]);
						((float *) (b + f.ofs))[0] = vec[0];
						((float *) (b + f.ofs))[1] = vec[1];
						((float *) (b + f.ofs))[2] = vec[2];
						break;
					case F_INT :
						* (int *) (b + f.ofs) = atoi(value);
						break;
					case F_FLOAT :
						* (float *) (b + f.ofs) = atof(value);
						break;
					case F_ANGLEHACK :
						v = atof(value);
						((float *) (b + f.ofs))[0] = 0;
						((float *) (b + f.ofs))[1] = v;
						((float *) (b + f.ofs))[2] = 0;
						break;
					case F_IGNORE :
						break;
				}
				return;
			}
		}
		gi.dprintf("%s is not a field\n", key);
		
		*/
	}

	/*
		====================
		ED_ParseEdict
		
		Parses an edict out of the given string, returning the new position
		ed should be a properly initialized empty edict.
		====================
	*/
	 
	static void ED_ParseEdict(Com.ParseHelp ph, edict_t ent) {
		
		boolean init;
		String keyname;
		String com_token;
		init = false;
		
		st = new spawn_temp_t();		
		while (true) { 

			// parse key			
			com_token = Com.Parse(ph);
			if (com_token.equals("}"))
				break;
			
			if (ph.isEof())
				gi.error("ED_ParseEntity: EOF without closing brace");
				
			keyname = com_token;
			
			// parse value
			com_token = Com.Parse(ph);
				
			if (ph.isEof())
				gi.error("ED_ParseEntity: EOF without closing brace");

			if (com_token.equals("}"))
				gi.error("ED_ParseEntity: closing brace without data");
				
			init = true;
			// keynames with a leading underscore are used for utility comments,
			// and are immediately discarded by quake
			if (keyname.charAt(0) == '_')
				continue;
				
			ED_ParseField(keyname, com_token, ent);
			
		}
	
		if (!init)
		{
			GameUtil.G_ClearEdict(ent);
		}

		return;
	}
	
	/*
		================
		G_FindTeams
		
		Chain together all entities with a matching team field.
		
		All but the first will have the FL_TEAMSLAVE flag set.
		All but the last will have the teamchain field set to the next one
		================
	*/

	static void G_FindTeams() {
		edict_t e, e2, chain;
		int i, j;
		int c, c2;
		c = 0;
		c2 = 0;
		for (i = 1; i < globals.num_edicts; i++) {
			e = g_edicts[i];

			if (!e.inuse)
				continue;
			if (e.team ==  null)
				continue;
			if ((e.flags & FL_TEAMSLAVE) != 0)
				continue;
			chain = e;
			e.teammaster = e;
			c++;
			c2++;
			//Com.Printf("Team:" + e.team+" entity: " + e.index + "\n");
			for (j = i + 1; j < globals.num_edicts; j++) {
				e2 = g_edicts[j];
				if (!e2.inuse)
					continue;
				if (null == e2.team)
					continue;
				if ((e2.flags & FL_TEAMSLAVE) != 0)
					continue;
				if (0 == Lib.strcmp(e.team, e2.team)) {
					c2++;
					chain.teamchain = e2;
					e2.teammaster = e;
					chain = e2;
					e2.flags |= FL_TEAMSLAVE;
					
				}
			}
		}		
		//gi.dprintf("" + c + " teams with " + c2 + " entities\n");
	}

	/*
			==============
			SpawnEntities
					
			Creates a server's entity / program execution context by
			parsing textual entity definitions out of an ent file.
			==============
	*/

		static void SpawnEntities(String mapname, String entities, String spawnpoint) {
			edict_t ent;
			int inhibit;
			String com_token;
			int i;
			float skill_level;
			//skill.value =2.0f;
			skill_level = (float) Math.floor(skill.value);
			
			if (skill_level < 0)
				skill_level = 0;
			if (skill_level > 3)
				skill_level = 3;
			if (skill.value != skill_level)
				gi.cvar_forceset("skill", "" + skill_level);
	
			PlayerClient.SaveClientData();
			
			//level.clear();
			level = new level_locals_t();
			for (int n=0; n < game.maxentities; n++)
			{
				g_edicts[n] = new edict_t(n);
			}
			//memset(g_edicts, 0, game.maxentities * sizeof(g_edicts[0]));
			level.mapname = mapname;
			game.spawnpoint = spawnpoint;
			// set client fields on player ents
			for (i = 0; i < game.maxclients; i++)
				g_edicts[i + 1].client = game.clients[i];
				
			ent = null;
			inhibit = 0; //	   parse ents
			//Com.Printf("========================\n");
			//Com.Printf("entities(" + entities.length() + ") = \n" + entities + "\n");
			//Com.Printf("========================\n");
			
			Com.ParseHelp ph = new Com.ParseHelp(entities);
			
			//Com.DPrintf("* * *     die scheiss edict- nummer stimmen nicht ???     * * *  \n");
			while (true) { // parse the opening brace
					
				com_token = Com.Parse(ph);
				if (ph.isEof())
					break;
				if (!com_token.startsWith("{"))
					gi.error("ED_LoadFromFile: found "+com_token+" when expecting {");
					
				if (ent==null)
					ent = g_edicts[0];
				else
					ent = G_Spawn();
					
				Com.DPrintf("===\n");	
				
				Com.DPrintf("allocated new edict:" + ent.index + "\n");
				ED_ParseEdict(ph, ent);
				Com.DPrintf("ent.classname:" + ent.classname + "\n");
				Com.DPrintf("ent.spawnflags:" + Integer.toHexString(ent.spawnflags) + "\n");
				// yet another map hack
				if (0==Q_stricmp(level.mapname, "command") && 0==Q_stricmp(ent.classname, "trigger_once") && 
					0==Q_stricmp(ent.model, "*27"))
					ent.spawnflags &= ~SPAWNFLAG_NOT_HARD;
					
				// remove things (except the world) from different skill levels or deathmatch
				if (ent != g_edicts[0]) {
					if (deathmatch.value!=0) {
						if ((ent.spawnflags & SPAWNFLAG_NOT_DEATHMATCH)!=0) {
							G_FreeEdict(ent);
							inhibit++;
							continue;
						}
					}
					else {
						if (/* ((coop.value) && (ent.spawnflags & SPAWNFLAG_NOT_COOP)) || */
							((skill.value == 0) && (ent.spawnflags & SPAWNFLAG_NOT_EASY)!=0)
								|| ((skill.value == 1) && (ent.spawnflags & SPAWNFLAG_NOT_MEDIUM)!=0)
								|| (((skill.value == 2) || (skill.value == 3)) && (ent.spawnflags & SPAWNFLAG_NOT_HARD)!=0)) {
							G_FreeEdict(ent);
							inhibit++;
							continue;
						}
					}
	
					ent.spawnflags
						&= ~(SPAWNFLAG_NOT_EASY | SPAWNFLAG_NOT_MEDIUM | SPAWNFLAG_NOT_HARD | SPAWNFLAG_NOT_COOP | SPAWNFLAG_NOT_DEATHMATCH);
				}
	
				ED_CallSpawn(ent);
			}
			//gi.dprintf("player skill level:" +skill.value + "\n");
			//gi.dprintf(inhibit + " entities inhibited\n");
			i = 1;
			G_FindTeams();
			PlayerTrail.Init();
		}

		static String single_statusbar = "yb	-24 " //	   health
		+"xv	0 " + "hnum " + "xv	50 " + "pic 0 " //	   ammo
		+"if 2 " + "	xv	100 " + "	anum " + "	xv	150 " + "	pic 2 " + "endif " //	   armor
		+"if 4 " + "	xv	200 " + "	rnum " + "	xv	250 " + "	pic 4 " + "endif " //	   selected item
		+"if 6 " + "	xv	296 " + "	pic 6 " + "endif " + "yb	-50 " //	   picked up item
	+"if 7 " + "	xv	0 " + "	pic 7 " + "	xv	26 " + "	yb	-42 " + "	stat_string 8 " + "	yb	-50 " + "endif "
		//	   timer
	+"if 9 " + "	xv	262 " + "	num	2	10 " + "	xv	296 " + "	pic	9 " + "endif "
		//		help / weapon icon 
	+"if 11 " + "	xv	148 " + "	pic	11 " + "endif ";

		static String dm_statusbar = "yb	-24 " //	   health
		+"xv	0 " + "hnum " + "xv	50 " + "pic 0 " //	   ammo
		+"if 2 " + "	xv	100 " + "	anum " + "	xv	150 " + "	pic 2 " + "endif " //	   armor
		+"if 4 " + "	xv	200 " + "	rnum " + "	xv	250 " + "	pic 4 " + "endif " //	   selected item
		+"if 6 " + "	xv	296 " + "	pic 6 " + "endif " + "yb	-50 " //	   picked up item
	+"if 7 " + "	xv	0 " + "	pic 7 " + "	xv	26 " + "	yb	-42 " + "	stat_string 8 " + "	yb	-50 " + "endif "
		//	   timer
	+"if 9 " + "	xv	246 " + "	num	2	10 " + "	xv	296 " + "	pic	9 " + "endif "
		//		help / weapon icon 
		+"if 11 " + "	xv	148 " + "	pic	11 " + "endif " //		frags
		+"xr	-50 " + "yt 2 " + "num 3 14 " //	   spectator
		+"if 17 " + "xv 0 " + "yb -58 " + "string2 \"SPECTATOR MODE\" " + "endif " //	   chase camera
	+"if 16 " + "xv 0 " + "yb -68 " + "string \"Chasing\" " + "xv 64 " + "stat_string 16 " + "endif ";


	static spawn_t spawns[] =
		{
			new spawn_t("item_health", GameSpawnAdapters.SP_item_health),
			new spawn_t("item_health_small", GameSpawnAdapters.SP_item_health_small),
			new spawn_t("item_health_large", GameSpawnAdapters.SP_item_health_large),
			new spawn_t("item_health_mega", GameSpawnAdapters.SP_item_health_mega),
			new spawn_t("info_player_start", GameSpawnAdapters.SP_info_player_start),
			new spawn_t("info_player_deathmatch", GameSpawnAdapters.SP_info_player_deathmatch),
			new spawn_t("info_player_coop", GameSpawnAdapters.SP_info_player_coop),
			new spawn_t("info_player_intermission", GameSpawnAdapters.SP_info_player_intermission),
			new spawn_t("func_plat", GameSpawnAdapters.SP_func_plat),
			new spawn_t("func_button", GameFuncAdapters.SP_func_button),
			new spawn_t("func_door", GameFuncAdapters.SP_func_door),
			new spawn_t("func_door_secret", GameFuncAdapters.SP_func_door_secret),
			new spawn_t("func_door_rotating", GameFuncAdapters.SP_func_door_rotating),
			new spawn_t("func_rotating", GameFuncAdapters.SP_func_rotating),
			new spawn_t("func_train", GameSpawnAdapters.SP_func_train),
			new spawn_t("func_water", GameSpawnAdapters.SP_func_water),
			new spawn_t("func_conveyor", GameFuncAdapters.SP_func_conveyor),
			new spawn_t("func_areaportal", GameMiscAdapters.SP_func_areaportal),
			new spawn_t("func_clock", GameSpawnAdapters.SP_func_clock),
			new spawn_t("func_wall",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_func_wall(ent);return true;}}),
			new spawn_t("func_object",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_func_object(ent);return true;}}),
			new spawn_t("func_timer",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_func_timer(ent);return true;}}),
			new spawn_t("func_explosive",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_func_explosive(ent);return true;}}),
			new spawn_t("func_killbox",   GameFuncAdapters.SP_func_killbox),
			new spawn_t("trigger_always",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_always(ent);return true;}}),
			new spawn_t("trigger_once",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_once(ent);return true;}}),
			new spawn_t("trigger_multiple",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_multiple(ent);return true;}}),
			new spawn_t("trigger_relay",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_relay(ent);return true;}}),
			new spawn_t("trigger_push",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_push(ent);return true;}}),
			new spawn_t("trigger_hurt",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_hurt(ent);return true;}}),
			new spawn_t("trigger_key",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_key(ent);return true;}}),
			new spawn_t("trigger_counter", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_trigger_counter(ent);return true;}}),
			new spawn_t("trigger_elevator",  GameFuncAdapters.SP_trigger_elevator ),
			new spawn_t("trigger_gravity",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_gravity(ent);return true;}}),
			new spawn_t("trigger_monsterjump",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_trigger_monsterjump(ent);return true;}}),
			new spawn_t("target_temp_entity",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_temp_entity(ent);return true;}}),
			new spawn_t("target_speaker",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_speaker(ent);return true;}}),
			new spawn_t("target_explosion",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_explosion(ent);return true;}}),
			new spawn_t("target_changelevel",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_changelevel(ent);return true;}}),
			new spawn_t("target_secret",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_secret(ent);return true;}}),
			new spawn_t("target_goal",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_goal(ent);return true;}}),
			new spawn_t("target_splash",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_splash(ent);return true;}}),
			new spawn_t("target_spawner",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_spawner(ent);return true;}}),
			new spawn_t("target_blaster", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_target_blaster(ent);return true;}}),
			new spawn_t("target_crosslevel_trigger",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_crosslevel_trigger(ent);return true;}}),
			new spawn_t("target_crosslevel_target",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_crosslevel_target(ent);return true;}}),
			new spawn_t("target_laser",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_laser(ent);return true;}}),
			new spawn_t("target_help",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_help(ent);return true;}}),
			new spawn_t("target_actor",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Actor.SP_target_actor(ent); return true;}}),
			new spawn_t("target_lightramp",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_lightramp(ent);return true;}}),
			new spawn_t("target_earthquake",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_earthquake(ent);return true;}}),
			new spawn_t("target_character",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_target_character(ent);return true;}}),
			new spawn_t("target_string", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_target_string(ent);return true;}}),
			new spawn_t("worldspawn",  GameSpawnAdapters.SP_worldspawn ),
			new spawn_t("viewthing", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_viewthing(ent);return true;}}),
			new spawn_t("light",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_light(ent);return true;}}),
			new spawn_t("light_mine1", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_light_mine1(ent);return true;}}),
			new spawn_t("light_mine2",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_light_mine2(ent);return true;}}),
			new spawn_t("info_null",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_info_null(ent);return true;}}),
			new spawn_t("func_group",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_info_null(ent);return true;}}),
			new spawn_t("info_notnull",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_info_notnull(ent);return true;}}),
			new spawn_t("path_corner",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_path_corner(ent);return true;}}),
			new spawn_t("point_combat",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_point_combat(ent);return true;}}),
			new spawn_t("misc_explobox",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_explobox(ent);return true;}}),
			new spawn_t("misc_banner",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_banner(ent);return true;}}),
			new spawn_t("misc_satellite_dish",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_satellite_dish(ent);return true;}}),
			new spawn_t("misc_actor",    new EntThinkAdapter() {public boolean think(edict_t ent){M_Actor.SP_misc_actor(ent); return false;}}),
			new spawn_t("misc_gib_arm", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_misc_gib_arm(ent);return true;}}),
			new spawn_t("misc_gib_leg",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_gib_leg(ent);return true;}}),
			new spawn_t("misc_gib_head",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_gib_head(ent);return true;}}),
			new spawn_t("misc_insane",    new EntThinkAdapter() {public boolean think(edict_t ent){M_Insane.SP_misc_insane(ent); return true;}}),
			new spawn_t("misc_deadsoldier",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_deadsoldier(ent);return true;}}),
			new spawn_t("misc_viper",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_viper(ent);return true;}}),
			new spawn_t("misc_viper_bomb", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_misc_viper_bomb(ent);return true;}}),
			new spawn_t("misc_bigviper",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_bigviper(ent);return true;}}),
			new spawn_t("misc_strogg_ship",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_strogg_ship(ent);return true;}}),
			new spawn_t("misc_teleporter", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_misc_teleporter(ent);return true;}}),
			new spawn_t("misc_teleporter_dest",  GameMiscAdapters.SP_misc_teleporter_dest ),
			new spawn_t("misc_blackhole", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_misc_blackhole(ent);return true;}}),
			new spawn_t("misc_eastertank", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_misc_eastertank(ent);return true;}}),
			new spawn_t("misc_easterchick",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_easterchick(ent);return true;}}),
			new spawn_t("misc_easterchick2",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_misc_easterchick2(ent);return true;}}),
			new spawn_t("monster_berserk",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Berserk.SP_monster_berserk(ent);return true;}}),
			new spawn_t("monster_gladiator",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Gladiator.SP_monster_gladiator(ent);return true;}}),
			new spawn_t("monster_gunner",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Gunner.SP_monster_gunner(ent);return true;}}),
			new spawn_t("monster_infantry",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Infantry.SP_monster_infantry(ent);return true;}}),
			new spawn_t("monster_soldier_light",   M_SoldierAdapters.SP_monster_soldier_light),
			new spawn_t("monster_soldier",    M_SoldierAdapters.SP_monster_soldier),
			new spawn_t("monster_soldier_ss",    M_SoldierAdapters.SP_monster_soldier_ss),
			new spawn_t("monster_tank",   M_Tank.SP_monster_tank),
			new spawn_t("monster_tank_commander",    M_Tank.SP_monster_tank),
			new spawn_t("monster_medic",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Medic.SP_monster_medic(ent);return true;}}),
			new spawn_t("monster_flipper",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Flipper.SP_monster_flipper(ent);return true;}}),
			new spawn_t("monster_chick",  new EntThinkAdapter() {public boolean think(edict_t ent){ M_Chick.SP_monster_chick(ent);return true;}}),
			new spawn_t("monster_parasite",    M_Parasite.SP_monster_parasite ),
			new spawn_t("monster_flyer",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Flyer.SP_monster_flyer(ent);return true;}}),
			new spawn_t("monster_brain",  new EntThinkAdapter() {public boolean think(edict_t ent){ M_Brain.SP_monster_brain(ent);return true;}}),
			new spawn_t("monster_floater",  new EntThinkAdapter() {public boolean think(edict_t ent){M_Float.SP_monster_floater(ent);return true;}}),
			new spawn_t("monster_hover", new EntThinkAdapter() {public boolean think(edict_t ent){  M_Hover.SP_monster_hover(ent);return true;}}),
			new spawn_t("monster_mutant",  M_Mutant.SP_monster_mutant),
			new spawn_t("monster_supertank", M_Supertank.SP_monster_supertank),
			new spawn_t("monster_boss2",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Boss2.SP_monster_boss2(ent);return true;}}),
			new spawn_t("monster_boss3_stand",   new EntThinkAdapter() {public boolean think(edict_t ent){M_Boss3.SP_monster_boss3_stand(ent);return true;}}),
			new spawn_t("monster_jorg",  new EntThinkAdapter() {public boolean think(edict_t ent){M_Boss31.SP_monster_jorg(ent);return true;}}),
			new spawn_t("monster_commander_body", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_monster_commander_body(ent);return true;}}),
			new spawn_t("turret_breach", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_turret_breach(ent);return true;}}),
			new spawn_t("turret_base", new EntThinkAdapter() {public boolean think(edict_t ent){Game. SP_turret_base(ent);return true;}}),
			new spawn_t("turret_driver",  new EntThinkAdapter() {public boolean think(edict_t ent){Game.SP_turret_driver(ent);return true;}}),
			new spawn_t(null, null)};
	/*
	===============
	ED_CallSpawn
	
	Finds the spawn function for the entity and calls it
	===============
	*/
	public static void ED_CallSpawn(edict_t ent) {
		
		spawn_t s;
		gitem_t item;
		int i;
		if (null == ent.classname) {
			gi.dprintf("ED_CallSpawn: null classname\n");
			return;
		} // check item spawn functions
		for (i = 1; i < game.num_items; i++) {
			
			item = GameAI.itemlist[i];
			
			if (item == null)
				gi.error("ED_CallSpawn: null item in pos " + i);
				
			if (item.classname == null)
				continue;
			if (0 == Lib.stricmp(item.classname, ent.classname)) { // found it
				SpawnItem(ent, item);
				return;
			}
		} // check normal spawn functions
	
		for (i=0; (s = spawns[i]) !=null && s.name != null; i++) {
			if (0 == Lib.stricmp(s.name, ent.classname)) { // found it
				
				if (s.spawn == null)
					gi.error("ED_CallSpawn: null-spawn on index=" + i);
				s.spawn.think(ent);
				return;
			}
		}
		gi.dprintf(ent.classname + " doesn't have a spawn function\n");
	}
}
