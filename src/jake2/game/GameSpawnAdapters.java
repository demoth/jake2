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

// Created on 26.02.2004 by RST.
// $Id: GameSpawnAdapters.java,v 1.1 2004-07-08 15:58:44 hzi Exp $

package jake2.game;

import jake2.Defines;
import jake2.util.*;
import jake2.qcommon.*;

public class GameSpawnAdapters {

	static EntThinkAdapter SP_item_health = new EntThinkAdapter() {public boolean think(edict_t ent){GameAI.SP_item_health(ent);return true;}};
	static EntThinkAdapter SP_item_health_small = new EntThinkAdapter() {public boolean think(edict_t ent){ GameAI.SP_item_health_small(ent);return true;}};
	static EntThinkAdapter SP_item_health_large = new EntThinkAdapter() {public boolean think(edict_t ent){GameAI.SP_item_health_large(ent); return true;}};
	static EntThinkAdapter SP_item_health_mega = new EntThinkAdapter() {public boolean think(edict_t ent){GameAI.SP_item_health_mega(ent); return true;}};
	static EntThinkAdapter SP_info_player_start = new EntThinkAdapter() {public boolean think(edict_t ent){ PlayerClient.SP_info_player_start(ent);return true;}};
	static EntThinkAdapter SP_info_player_deathmatch = new EntThinkAdapter() {public boolean think(edict_t ent){ PlayerClient.SP_info_player_deathmatch(ent);return true;}};
	static EntThinkAdapter SP_info_player_coop = new EntThinkAdapter() {public boolean think(edict_t ent){PlayerClient.SP_info_player_coop(ent); return true;}};
	static EntThinkAdapter SP_info_player_intermission = new EntThinkAdapter() {public boolean think(edict_t ent){PlayerClient.SP_info_player_intermission(); return true;}};
	static EntThinkAdapter SP_func_plat = new EntThinkAdapter() {public boolean think(edict_t ent){GameFunc.SP_func_plat(ent); return true;}};
		//static EntThinkAdapter SP_func_rotating = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_button = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_door = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_door_secret = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_door_rotating = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	 	static EntThinkAdapter SP_func_water = new EntThinkAdapter() {public boolean think(edict_t ent){GameFunc.SP_func_water(ent); return true;}};
	static EntThinkAdapter SP_func_train = new EntThinkAdapter() {public boolean think(edict_t ent){GameFunc.SP_func_train(ent); return true;}};
	//	static EntThinkAdapter SP_func_conveyor = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_wall = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_object = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_explosive = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_timer = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	//	static EntThinkAdapter SP_func_areaportal = new EntThinkAdapter() {public boolean think(edict_t ent){ return true;}};
	 	static EntThinkAdapter SP_func_clock = new EntThinkAdapter() {public boolean think(edict_t ent){GameMisc.SP_func_clock(ent); return true;}};
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
			GameAI.SetItemNames();
			if (GameBase.st.nextmap != null)
				GameBase.level.nextmap = GameBase.st.nextmap;
			// make some data visible to the server
			if (ent.message != null && ent.message.length() > 0) {
				GameBase.gi.configstring(Defines.CS_NAME, ent.message);
				GameBase.level.level_name = ent.message;
			}
			else
				GameBase.level.level_name = GameBase.level.mapname;
			if (GameBase.st.sky != null && GameBase.st.sky.length() > 0)
				GameBase.gi.configstring(Defines.CS_SKY, GameBase.st.sky);
			else
				GameBase.gi.configstring(Defines.CS_SKY, "unit1_");
			GameBase.gi.configstring(Defines.CS_SKYROTATE, "" + GameBase.st.skyrotate);
			GameBase.gi.configstring(Defines.CS_SKYAXIS, Lib.vtos(GameBase.st.skyaxis));
			GameBase.gi.configstring(Defines.CS_CDTRACK, "" + ent.sounds);
			GameBase.gi.configstring(Defines.CS_MAXCLIENTS, "" + (int) (GameBase.maxclients.value));
			// status bar program
			if (GameBase.deathmatch.value != 0)
				GameBase.gi.configstring(Defines.CS_STATUSBAR, "" + GameSpawn.dm_statusbar);
			else
				GameBase.gi.configstring(Defines.CS_STATUSBAR, "" + GameSpawn.single_statusbar);
			//---------------
			// help icon for statusbar
			GameBase.gi.imageindex("i_help");
			GameBase.level.pic_health = GameBase.gi.imageindex("i_health");
			GameBase.gi.imageindex("help");
			GameBase.gi.imageindex("field_3");
			if (GameBase.st.gravity != null)
				GameBase.gi.cvar_set("sv_gravity", "800");
			else
				GameBase.gi.cvar_set("sv_gravity", GameBase.st.gravity);
			GameBase.snd_fry = GameBase.gi.soundindex("player/fry.wav");
			// standing in lava / slime
			GameAI.PrecacheItem(GameUtil.FindItem("Blaster"));
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
			GameBase.sm_meat_index = GameBase.gi.modelindex("models/objects/gibs/sm_meat/tris.md2");
			GameBase.gi.modelindex("models/objects/gibs/arm/tris.md2");
			GameBase.gi.modelindex("models/objects/gibs/bone/tris.md2");
			GameBase.gi.modelindex("models/objects/gibs/bone2/tris.md2");
			GameBase.gi.modelindex("models/objects/gibs/chest/tris.md2");
			GameBase.gi.modelindex("models/objects/gibs/skull/tris.md2");
			GameBase.gi.modelindex("models/objects/gibs/head2/tris.md2");
			//
			//	   Setup light animation tables. 'a' is total darkness, 'z' is doublebright.
			//
			// 0 normal
			GameBase.gi.configstring(Defines.CS_LIGHTS + 0, "m");
			// 1 FLICKER (first variety)
			GameBase.gi.configstring(Defines.CS_LIGHTS + 1, "mmnmmommommnonmmonqnmmo");
			// 2 SLOW STRONG PULSE
			GameBase.gi.configstring(Defines.CS_LIGHTS + 2, "abcdefghijklmnopqrstuvwxyzyxwvutsrqponmlkjihgfedcba");
			// 3 CANDLE (first variety)
			GameBase.gi.configstring(Defines.CS_LIGHTS + 3, "mmmmmaaaaammmmmaaaaaabcdefgabcdefg");
			// 4 FAST STROBE
			GameBase.gi.configstring(Defines.CS_LIGHTS + 4, "mamamamamama");
			// 5 GENTLE PULSE 1
			GameBase.gi.configstring(Defines.CS_LIGHTS + 5, "jklmnopqrstuvwxyzyxwvutsrqponmlkj");
			// 6 FLICKER (second variety)
			GameBase.gi.configstring(Defines.CS_LIGHTS + 6, "nmonqnmomnmomomno");
			// 7 CANDLE (second variety)
			GameBase.gi.configstring(Defines.CS_LIGHTS + 7, "mmmaaaabcdefgmmmmaaaammmaamm");
			// 8 CANDLE (third variety)
			GameBase.gi.configstring(Defines.CS_LIGHTS + 8, "mmmaaammmaaammmabcdefaaaammmmabcdefmmmaaaa");
			// 9 SLOW STROBE (fourth variety)
			GameBase.gi.configstring(Defines.CS_LIGHTS + 9, "aaaaaaaazzzzzzzz");
			// 10 FLUORESCENT FLICKER
			GameBase.gi.configstring(Defines.CS_LIGHTS + 10, "mmamammmmammamamaaamammma");
			// 11 SLOW PULSE NOT FADE TO BLACK
			GameBase.gi.configstring(Defines.CS_LIGHTS + 11, "abcdefghijklmnopqrrqponmlkjihgfedcba");
			// styles 32-62 are assigned by the light program for switchable lights
			// 63 testing
			GameBase.gi.configstring(Defines.CS_LIGHTS + 63, "a");
			return true;
		}
	};
}
