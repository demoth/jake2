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
// $Id: GameSpawn.java,v 1.8 2003-12-27 21:33:50 rst Exp $

package jake2.game;

import jake2.util.*;
import jake2.qcommon.*;

public class GameSpawn extends Game {

	static EntThinkAdapter SP_item_health = null;
	static EntThinkAdapter SP_item_health_small = null;
	static EntThinkAdapter SP_item_health_large = null;
	static EntThinkAdapter SP_item_health_mega = null;

	static EntThinkAdapter SP_info_player_start = null;
	static EntThinkAdapter SP_info_player_deathmatch = null;
	static EntThinkAdapter SP_info_player_coop = null;
	static EntThinkAdapter SP_info_player_intermission = null;

	static EntThinkAdapter SP_func_plat = null;
	static EntThinkAdapter SP_func_rotating = null;
	static EntThinkAdapter SP_func_button = null;
	static EntThinkAdapter SP_func_door = null;
	static EntThinkAdapter SP_func_door_secret = null;
	static EntThinkAdapter SP_func_door_rotating = null;
	static EntThinkAdapter SP_func_water = null;
	static EntThinkAdapter SP_func_train = null;
	static EntThinkAdapter SP_func_conveyor = null;
	static EntThinkAdapter SP_func_wall = null;
	static EntThinkAdapter SP_func_object = null;
	static EntThinkAdapter SP_func_explosive = null;
	static EntThinkAdapter SP_func_timer = null;
	static EntThinkAdapter SP_func_areaportal = null;
	static EntThinkAdapter SP_func_clock = null;
	static EntThinkAdapter SP_func_killbox = null;

	static EntThinkAdapter SP_trigger_always = null;
	static EntThinkAdapter SP_trigger_once = null;
	static EntThinkAdapter SP_trigger_multiple = null;
	static EntThinkAdapter SP_trigger_relay = null;
	static EntThinkAdapter SP_trigger_push = null;
	static EntThinkAdapter SP_trigger_hurt = null;
	static EntThinkAdapter SP_trigger_key = null;
	static EntThinkAdapter SP_trigger_counter = null;
	static EntThinkAdapter SP_trigger_elevator = null;
	static EntThinkAdapter SP_trigger_gravity = null;
	static EntThinkAdapter SP_trigger_monsterjump = null;

	static EntThinkAdapter SP_target_temp_entity = null;
	static EntThinkAdapter SP_target_speaker = null;
	static EntThinkAdapter SP_target_explosion = null;
	static EntThinkAdapter SP_target_changelevel = null;
	static EntThinkAdapter SP_target_secret = null;
	static EntThinkAdapter SP_target_goal = null;
	static EntThinkAdapter SP_target_splash = null;
	static EntThinkAdapter SP_target_spawner = null;
	static EntThinkAdapter SP_target_blaster = null;
	static EntThinkAdapter SP_target_crosslevel_trigger = null;
	static EntThinkAdapter SP_target_crosslevel_target = null;
	static EntThinkAdapter SP_target_laser = null;
	static EntThinkAdapter SP_target_help = null;
	static EntThinkAdapter SP_target_actor = null;
	static EntThinkAdapter SP_target_lightramp = null;
	static EntThinkAdapter SP_target_earthquake = null;
	static EntThinkAdapter SP_target_character = null;
	static EntThinkAdapter SP_target_string = null;

	//static EntThinkAdapter SP_worldspawn = null;
	static EntThinkAdapter SP_viewthing = null;

	static EntThinkAdapter SP_light = null;
	static EntThinkAdapter SP_light_mine1 = null;
	static EntThinkAdapter SP_light_mine2 = null;
	static EntThinkAdapter SP_info_null = null;
	static EntThinkAdapter SP_info_notnull = null;
	static EntThinkAdapter SP_path_corner = null;
	static EntThinkAdapter SP_point_combat = null;

	static EntThinkAdapter SP_misc_explobox = null;
	static EntThinkAdapter SP_misc_banner = null;
	static EntThinkAdapter SP_misc_satellite_dish = null;
	static EntThinkAdapter SP_misc_actor = null;
	static EntThinkAdapter SP_misc_gib_arm = null;
	static EntThinkAdapter SP_misc_gib_leg = null;
	static EntThinkAdapter SP_misc_gib_head = null;
	static EntThinkAdapter SP_misc_insane = null;
	static EntThinkAdapter SP_misc_deadsoldier = null;
	static EntThinkAdapter SP_misc_viper = null;
	static EntThinkAdapter SP_misc_viper_bomb = null;
	static EntThinkAdapter SP_misc_bigviper = null;
	static EntThinkAdapter SP_misc_strogg_ship = null;
	static EntThinkAdapter SP_misc_teleporter = null;
	static EntThinkAdapter SP_misc_teleporter_dest = null;
	static EntThinkAdapter SP_misc_blackhole = null;
	static EntThinkAdapter SP_misc_eastertank = null;
	static EntThinkAdapter SP_misc_easterchick = null;
	static EntThinkAdapter SP_misc_easterchick2 = null;

	static EntThinkAdapter SP_monster_berserk = null;
	static EntThinkAdapter SP_monster_gladiator = null;
	static EntThinkAdapter SP_monster_gunner = null;
	static EntThinkAdapter SP_monster_infantry = null;
	static EntThinkAdapter SP_monster_soldier_light = null;
	static EntThinkAdapter SP_monster_soldier = null;
	static EntThinkAdapter SP_monster_soldier_ss = null;
	static EntThinkAdapter SP_monster_tank = null;
	static EntThinkAdapter SP_monster_medic = null;
	static EntThinkAdapter SP_monster_flipper = null;
	static EntThinkAdapter SP_monster_chick = null;
	static EntThinkAdapter SP_monster_parasite = null;
	static EntThinkAdapter SP_monster_flyer = null;
	static EntThinkAdapter SP_monster_brain = null;
	static EntThinkAdapter SP_monster_floater = null;
	static EntThinkAdapter SP_monster_hover = null;
	static EntThinkAdapter SP_monster_mutant = null;
	static EntThinkAdapter SP_monster_supertank = null;
	static EntThinkAdapter SP_monster_boss2 = null;
	static EntThinkAdapter SP_monster_jorg = null;
	static EntThinkAdapter SP_monster_boss3_stand = null;

	static EntThinkAdapter SP_monster_commander_body = null;

	static EntThinkAdapter SP_turret_breach = null;
	static EntThinkAdapter SP_turret_base = null;
	static EntThinkAdapter SP_turret_driver = null;

	/*
	=============
	ED_NewString
	=============
	*/
	static String ED_NewString(String string) {

		//String newb, new_p;
		int i, l;

		l = Lib.strlen(string) + 1;
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
		field_t f;
		byte b;
		float v;
		float[] vec = { 0, 0, 0 };

		if (!st.set(key, value))
			if (!ent.set(key, value))
				gi.dprintf(key + " is not a field\n");



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
	 
	static String ED_ParseEdict(String data, edict_t ent) {
		
		boolean init;
		String keyname;
		String com_token;
		init = false;
		
		//memset(& st, 0, sizeof(st));
		//	   go through all the dictionary pairs
		
		Com.ParseHelp ph = new Com.ParseHelp(data);
		
		
		while (true) { // parse key
			com_token = Com.Parse(ph);
			if (com_token.charAt(0) == '}')
				break;
				
			if (ph.isEof())
				gi.error("ED_ParseEntity: EOF without closing brace");
				
			
			//strncpy(keyname, com_token, sizeof(keyname) - 1);
			keyname = com_token;
			
			// parse value				
			com_token = Com.Parse(ph);
			
			if (ph.isEof())
				gi.error("ED_ParseEntity: EOF without closing brace");
				
			if (com_token.charAt(0) == '}')
				gi.error("ED_ParseEntity: closing brace without data");
				
			init = true;
			// keynames with a leading underscore are used for utility comments,
			// and are immediately discarded by quake
			if (keyname.charAt(0) == '_')
				continue;
			ED_ParseField(keyname, com_token, ent);
		}
	
		if (!init)
			ent.clear();

		return data;
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
		
		gi.dprintf("" + c + " teams with " + c2 + " entities\n");
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
			skill_level = (float) Math.floor(skill.value);
			if (skill_level < 0)
				skill_level = 0;
			if (skill_level > 3)
				skill_level = 3;
			if (skill.value != skill_level)
				gi.cvar_forceset("skill", "" + skill_level);
	
	//		SaveClientData();
			
			//level.clear();
	//		memset(g_edicts, 0, game.maxentities * sizeof(g_edicts[0]));
	//		strncpy(level.mapname, mapname, sizeof(level.mapname) - 1);
	//		strncpy(game.spawnpoint, spawnpoint, sizeof(game.spawnpoint) - 1);
	//		// set client fields on player ents
	//		for (i = 0; i < game.maxclients; i++)
	//			g_edicts[i + 1].client = game.clients + i;
	//		ent = null;
	//		inhibit = 0; //	   parse ents
	//		while (1) { // parse the opening brace	
	//			com_token = COM_Parse(entities);
	//			if (entities == null)
	//				break;
	//			if (com_token[0] != '{')
	//				gi.error("ED_LoadFromFile: found %s when expecting {", com_token);
	//			if (!ent)
	//				ent = g_edicts;
	//			else
	//				ent = G_Spawn();
	//			entities = ED_ParseEdict(entities, ent);
	//			// yet another map hack
	//			if (!Q_stricmp(level.mapname, "command") && !Q_stricmp(ent.classname, "trigger_once") && !Q_stricmp(ent.model, "*27"))
	//				ent.spawnflags &= ~SPAWNFLAG_NOT_HARD;
	//			// remove things (except the world) from different skill levels or deathmatch
	//			if (ent != g_edicts) {
	//				if (deathmatch.value) {
	//					if (ent.spawnflags & SPAWNFLAG_NOT_DEATHMATCH) {
	//						G_FreeEdict(ent);
	//						inhibit++;
	//						continue;
	//					}
	//				}
	//				else {
	//					if (/* ((coop.value) && (ent.spawnflags & SPAWNFLAG_NOT_COOP)) || */
	//						((skill.value == 0) && (ent.spawnflags & SPAWNFLAG_NOT_EASY))
	//							|| ((skill.value == 1) && (ent.spawnflags & SPAWNFLAG_NOT_MEDIUM))
	//							|| (((skill.value == 2) || (skill.value == 3)) && (ent.spawnflags & SPAWNFLAG_NOT_HARD))) {
	//						G_FreeEdict(ent);
	//						inhibit++;
	//						continue;
	//					}
	//				}
	//
	//				ent.spawnflags
	//					&= ~(SPAWNFLAG_NOT_EASY | SPAWNFLAG_NOT_MEDIUM | SPAWNFLAG_NOT_HARD | SPAWNFLAG_NOT_COOP | SPAWNFLAG_NOT_DEATHMATCH);
	//			}
	//
	//			ED_CallSpawn(ent);
	//		}
	//
	//		gi.dprintf(inhibit + " entities inhibited\n");
	//		//TODO: insert a log4j!
	//		//# ifdef DEBUG 
	//		i = 1;
	//		ent = EDICT_NUM(i);
	//		while (i < globals.num_edicts) {
	//			if (ent.inuse != 0 || ent.inuse != 1)
	//				Com_DPrintf("Invalid entity %d\n", i);
	//			i++;
	//			ent++;
	//		} //# endif 
			G_FindTeams();
			PlayerTrail.Init();
		}

		// E C L I P S E   D R E C K M I S T   F O R M A T T E R !

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

	static void InitBodyQue() {
		int i;
		edict_t ent;
		level.body_que = 0;
		for (i = 0; i < BODY_QUEUE_SIZE; i++) {
			ent = G_Spawn();
			ent.classname = "bodyque";
		}
	}

	/*QUAKED worldspawn (0 0 0) ?
		
		Only used for the world.
		"sky"	environment map name
		"skyaxis"	vector axis for rotating sky
		"skyrotate"	speed of rotation in degrees/second
		"sounds"	music cd track number
		"gravity"	800 is default gravity
		"message"	text to print at user logon
		*/

	static EntThinkAdapter SP_worldspawn = new EntThinkAdapter() {

		public boolean think(edict_t ent) {
			ent.movetype = MOVETYPE_PUSH;
			ent.solid = SOLID_BSP;
			ent.inuse = true;
			// since the world doesn't use G_Spawn()
			ent.s.modelindex = 1;
			// world model is always index 1
			//---------------
			// reserve some spots for dead player bodies for coop / deathmatch
			InitBodyQue();
			// set configstrings for items
			SetItemNames();
			if (st.nextmap != null)
				level.nextmap = st.nextmap;
			// make some data visible to the server
			if (ent.message != null && ent.message.length() > 0) {
				gi.configstring(CS_NAME, ent.message);
				level.level_name = ent.message;
			}
			else
				level.level_name = level.mapname;
			if (st.sky != null && st.sky.length() > 0)
				gi.configstring(CS_SKY, st.sky);
			else
				gi.configstring(CS_SKY, "unit1_");
			gi.configstring(CS_SKYROTATE, "" + st.skyrotate);
			gi.configstring(CS_SKYAXIS, Lib.vtos(st.skyaxis));
			gi.configstring(CS_CDTRACK, "" + ent.sounds);
			gi.configstring(CS_MAXCLIENTS, "" + (int) (maxclients.value));
			// status bar program
			if (deathmatch.value != 0)
				gi.configstring(CS_STATUSBAR, "" + dm_statusbar);
			else
				gi.configstring(CS_STATUSBAR, "" + single_statusbar);
			//---------------
			// help icon for statusbar
			gi.imageindex("i_help");
			level.pic_health = gi.imageindex("i_health");
			gi.imageindex("help");
			gi.imageindex("field_3");
			if (st.gravity != null)
				gi.cvar_set("sv_gravity", "800");
			else
				gi.cvar_set("sv_gravity", st.gravity);
			snd_fry = gi.soundindex("player/fry.wav");
			// standing in lava / slime
			PrecacheItem(FindItem("Blaster"));
			gi.soundindex("player/lava1.wav");
			gi.soundindex("player/lava2.wav");
			gi.soundindex("misc/pc_up.wav");
			gi.soundindex("misc/talk1.wav");
			gi.soundindex("misc/udeath.wav");
			// gibs
			gi.soundindex("items/respawn1.wav");
			// sexed sounds
			gi.soundindex("*death1.wav");
			gi.soundindex("*death2.wav");
			gi.soundindex("*death3.wav");
			gi.soundindex("*death4.wav");
			gi.soundindex("*fall1.wav");
			gi.soundindex("*fall2.wav");
			gi.soundindex("*gurp1.wav");
			// drowning damage
			gi.soundindex("*gurp2.wav");
			gi.soundindex("*jump1.wav");
			// player jump
			gi.soundindex("*pain25_1.wav");
			gi.soundindex("*pain25_2.wav");
			gi.soundindex("*pain50_1.wav");
			gi.soundindex("*pain50_2.wav");
			gi.soundindex("*pain75_1.wav");
			gi.soundindex("*pain75_2.wav");
			gi.soundindex("*pain100_1.wav");
			gi.soundindex("*pain100_2.wav");
			// sexed models
			// THIS ORDER MUST MATCH THE DEFINES IN g_local.h
			// you can add more, max 15
			gi.modelindex("#w_blaster.md2");
			gi.modelindex("#w_shotgun.md2");
			gi.modelindex("#w_sshotgun.md2");
			gi.modelindex("#w_machinegun.md2");
			gi.modelindex("#w_chaingun.md2");
			gi.modelindex("#a_grenades.md2");
			gi.modelindex("#w_glauncher.md2");
			gi.modelindex("#w_rlauncher.md2");
			gi.modelindex("#w_hyperblaster.md2");
			gi.modelindex("#w_railgun.md2");
			gi.modelindex("#w_bfg.md2");
			//-------------------
			gi.soundindex("player/gasp1.wav");
			// gasping for air
			gi.soundindex("player/gasp2.wav");
			// head breaking surface, not gasping
			gi.soundindex("player/watr_in.wav");
			// feet hitting water
			gi.soundindex("player/watr_out.wav");
			// feet leaving water
			gi.soundindex("player/watr_un.wav");
			// head going underwater
			gi.soundindex("player/u_breath1.wav");
			gi.soundindex("player/u_breath2.wav");
			gi.soundindex("items/pkup.wav");
			// bonus item pickup
			gi.soundindex("world/land.wav");
			// landing thud
			gi.soundindex("misc/h2ohit1.wav");
			// landing splash
			gi.soundindex("items/damage.wav");
			gi.soundindex("items/protect.wav");
			gi.soundindex("items/protect4.wav");
			gi.soundindex("weapons/noammo.wav");
			gi.soundindex("infantry/inflies1.wav");
			sm_meat_index = gi.modelindex("models/objects/gibs/sm_meat/tris.md2");
			gi.modelindex("models/objects/gibs/arm/tris.md2");
			gi.modelindex("models/objects/gibs/bone/tris.md2");
			gi.modelindex("models/objects/gibs/bone2/tris.md2");
			gi.modelindex("models/objects/gibs/chest/tris.md2");
			gi.modelindex("models/objects/gibs/skull/tris.md2");
			gi.modelindex("models/objects/gibs/head2/tris.md2");
			//
			//	   Setup light animation tables. 'a' is total darkness, 'z' is doublebright.
			//
			// 0 normal
			gi.configstring(CS_LIGHTS + 0, "m");
			// 1 FLICKER (first variety)
			gi.configstring(CS_LIGHTS + 1, "mmnmmommommnonmmonqnmmo");
			// 2 SLOW STRONG PULSE
			gi.configstring(CS_LIGHTS + 2, "abcdefghijklmnopqrstuvwxyzyxwvutsrqponmlkjihgfedcba");
			// 3 CANDLE (first variety)
			gi.configstring(CS_LIGHTS + 3, "mmmmmaaaaammmmmaaaaaabcdefgabcdefg");
			// 4 FAST STROBE
			gi.configstring(CS_LIGHTS + 4, "mamamamamama");
			// 5 GENTLE PULSE 1
			gi.configstring(CS_LIGHTS + 5, "jklmnopqrstuvwxyzyxwvutsrqponmlkj");
			// 6 FLICKER (second variety)
			gi.configstring(CS_LIGHTS + 6, "nmonqnmomnmomomno");
			// 7 CANDLE (second variety)
			gi.configstring(CS_LIGHTS + 7, "mmmaaaabcdefgmmmmaaaammmaamm");
			// 8 CANDLE (third variety)
			gi.configstring(CS_LIGHTS + 8, "mmmaaammmaaammmabcdefaaaammmmabcdefmmmaaaa");
			// 9 SLOW STROBE (fourth variety)
			gi.configstring(CS_LIGHTS + 9, "aaaaaaaazzzzzzzz");
			// 10 FLUORESCENT FLICKER
			gi.configstring(CS_LIGHTS + 10, "mmamammmmammamamaaamammma");
			// 11 SLOW PULSE NOT FADE TO BLACK
			gi.configstring(CS_LIGHTS + 11, "abcdefghijklmnopqrrqponmlkjihgfedcba");
			// styles 32-62 are assigned by the light program for switchable lights
			// 63 testing
			gi.configstring(CS_LIGHTS + 63, "a");
			return true;
		}
	};
	static spawn_t spawns[] =
		{
			new spawn_t("item_health", SP_item_health),
			new spawn_t("item_health_small", SP_item_health_small),
			new spawn_t("item_health_large", SP_item_health_large),
			new spawn_t("item_health_mega", SP_item_health_mega),
			new spawn_t("info_player_start", SP_info_player_start),
			new spawn_t("info_player_deathmatch", SP_info_player_deathmatch),
			new spawn_t("info_player_coop", SP_info_player_coop),
			new spawn_t("info_player_intermission", SP_info_player_intermission),
			new spawn_t("func_plat", SP_func_plat),
			new spawn_t("func_button", SP_func_button),
			new spawn_t("func_door", SP_func_door),
			new spawn_t("func_door_secret", SP_func_door_secret),
			new spawn_t("func_door_rotating", SP_func_door_rotating),
			new spawn_t("func_rotating", SP_func_rotating),
			new spawn_t("func_train", SP_func_train),
			new spawn_t("func_water", SP_func_water),
			new spawn_t("func_conveyor", SP_func_conveyor),
			new spawn_t("func_areaportal", SP_func_areaportal),
			new spawn_t("func_clock", SP_func_clock),
			new spawn_t("func_wall", SP_func_wall),
			new spawn_t("func_object", SP_func_object),
			new spawn_t("func_timer", SP_func_timer),
			new spawn_t("func_explosive", SP_func_explosive),
			new spawn_t("func_killbox", SP_func_killbox),
			new spawn_t("trigger_always", SP_trigger_always),
			new spawn_t("trigger_once", SP_trigger_once),
			new spawn_t("trigger_multiple", SP_trigger_multiple),
			new spawn_t("trigger_relay", SP_trigger_relay),
			new spawn_t("trigger_push", SP_trigger_push),
			new spawn_t("trigger_hurt", SP_trigger_hurt),
			new spawn_t("trigger_key", SP_trigger_key),
			new spawn_t("trigger_counter", SP_trigger_counter),
			new spawn_t("trigger_elevator", SP_trigger_elevator),
			new spawn_t("trigger_gravity", SP_trigger_gravity),
			new spawn_t("trigger_monsterjump", SP_trigger_monsterjump),
			new spawn_t("target_temp_entity", SP_target_temp_entity),
			new spawn_t("target_speaker", SP_target_speaker),
			new spawn_t("target_explosion", SP_target_explosion),
			new spawn_t("target_changelevel", SP_target_changelevel),
			new spawn_t("target_secret", SP_target_secret),
			new spawn_t("target_goal", SP_target_goal),
			new spawn_t("target_splash", SP_target_splash),
			new spawn_t("target_spawner", SP_target_spawner),
			new spawn_t("target_blaster", SP_target_blaster),
			new spawn_t("target_crosslevel_trigger", SP_target_crosslevel_trigger),
			new spawn_t("target_crosslevel_target", SP_target_crosslevel_target),
			new spawn_t("target_laser", SP_target_laser),
			new spawn_t("target_help", SP_target_help),
			new spawn_t("target_actor", SP_target_actor),
			new spawn_t("target_lightramp", SP_target_lightramp),
			new spawn_t("target_earthquake", SP_target_earthquake),
			new spawn_t("target_character", SP_target_character),
			new spawn_t("target_string", SP_target_string),
			new spawn_t("worldspawn", SP_worldspawn),
			new spawn_t("viewthing", SP_viewthing),
			new spawn_t("light", SP_light),
			new spawn_t("light_mine1", SP_light_mine1),
			new spawn_t("light_mine2", SP_light_mine2),
			new spawn_t("info_null", SP_info_null),
			new spawn_t("func_group", SP_info_null),
			new spawn_t("info_notnull", SP_info_notnull),
			new spawn_t("path_corner", SP_path_corner),
			new spawn_t("point_combat", SP_point_combat),
			new spawn_t("misc_explobox", SP_misc_explobox),
			new spawn_t("misc_banner", SP_misc_banner),
			new spawn_t("misc_satellite_dish", SP_misc_satellite_dish),
			new spawn_t("misc_actor", SP_misc_actor),
			new spawn_t("misc_gib_arm", SP_misc_gib_arm),
			new spawn_t("misc_gib_leg", SP_misc_gib_leg),
			new spawn_t("misc_gib_head", SP_misc_gib_head),
			new spawn_t("misc_insane", SP_misc_insane),
			new spawn_t("misc_deadsoldier", SP_misc_deadsoldier),
			new spawn_t("misc_viper", SP_misc_viper),
			new spawn_t("misc_viper_bomb", SP_misc_viper_bomb),
			new spawn_t("misc_bigviper", SP_misc_bigviper),
			new spawn_t("misc_strogg_ship", SP_misc_strogg_ship),
			new spawn_t("misc_teleporter", SP_misc_teleporter),
			new spawn_t("misc_teleporter_dest", SP_misc_teleporter_dest),
			new spawn_t("misc_blackhole", SP_misc_blackhole),
			new spawn_t("misc_eastertank", SP_misc_eastertank),
			new spawn_t("misc_easterchick", SP_misc_easterchick),
			new spawn_t("misc_easterchick2", SP_misc_easterchick2),
			new spawn_t("monster_berserk", SP_monster_berserk),
			new spawn_t("monster_gladiator", SP_monster_gladiator),
			new spawn_t("monster_gunner", SP_monster_gunner),
			new spawn_t("monster_infantry", SP_monster_infantry),
			new spawn_t("monster_soldier_light", SP_monster_soldier_light),
			new spawn_t("monster_soldier", SP_monster_soldier),
			new spawn_t("monster_soldier_ss", SP_monster_soldier_ss),
			new spawn_t("monster_tank", SP_monster_tank),
			new spawn_t("monster_tank_commander", SP_monster_tank),
			new spawn_t("monster_medic", SP_monster_medic),
			new spawn_t("monster_flipper", SP_monster_flipper),
			new spawn_t("monster_chick", SP_monster_chick),
			new spawn_t("monster_parasite", SP_monster_parasite),
			new spawn_t("monster_flyer", SP_monster_flyer),
			new spawn_t("monster_brain", SP_monster_brain),
			new spawn_t("monster_floater", SP_monster_floater),
			new spawn_t("monster_hover", SP_monster_hover),
			new spawn_t("monster_mutant", SP_monster_mutant),
			new spawn_t("monster_supertank", SP_monster_supertank),
			new spawn_t("monster_boss2", SP_monster_boss2),
			new spawn_t("monster_boss3_stand", SP_monster_boss3_stand),
			new spawn_t("monster_jorg", SP_monster_jorg),
			new spawn_t("monster_commander_body", SP_monster_commander_body),
			new spawn_t("turret_breach", SP_turret_breach),
			new spawn_t("turret_base", SP_turret_base),
			new spawn_t("turret_driver", SP_turret_driver),
			new spawn_t(null, null)};
	/*
	===============
	ED_CallSpawn
	
	Finds the spawn function for the entity and calls it
	===============
	*/
	static void ED_CallSpawn(edict_t ent) {
		spawn_t s;
		gitem_t item;
		int i;
		if (null == ent.classname) {
			gi.dprintf("ED_CallSpawn: null classname\n");
			return;
		} // check item spawn functions
		for (i = 0; i < game.num_items; i++) {
			item = itemlist[i];
			if (item.classname == null)
				continue;
			if (0 == Lib.strcmp(item.classname, ent.classname)) { // found it
				SpawnItem(ent, item);
				return;
			}
		} // check normal spawn functions
		for (i = 0, s = spawns[i]; s.name != null; i++) {
			if (0 == Lib.strcmp(s.name, ent.classname)) { // found it
				s.spawn.think(ent);
				return;
			}
		}
		gi.dprintf(ent.classname + " doesn't have a spawn function\n");
	}

}
