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

// Created on 29.12.2003 by RST.
// $Id: GameSave.java,v 1.3 2004-08-22 14:25:12 salomo Exp $

package jake2.game;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.*;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.*;

public class GameSave extends GameFunc {

	public static field_t levelfields[]=
		{
			new field_t("changemap", F_LSTRING),
			new field_t("sight_client", F_EDICT),
			new field_t("sight_entity", F_EDICT),
			new field_t("sound_entity", F_EDICT),
			new field_t("sound2_entity", F_EDICT),
			new field_t(null, F_INT)};

	public static field_t clientfields[]=
		{
			new field_t("pers.weapon", F_ITEM),
			new field_t("pers.lastweapon", F_ITEM),
			new field_t("newweapon", F_ITEM),
			new field_t(null, F_INT)};

	public static void CreateEdicts() {
		g_edicts= new edict_t[game.maxentities];
		for (int i= 0; i < game.maxentities; i++)
			g_edicts[i]= new edict_t(i);
		SV_GAME.ge.edicts= g_edicts;
	}

	public static void CreateClients() {
		game.clients= new gclient_t[game.maxclients];
		for (int i= 0; i < game.maxclients; i++)
			game.clients[i]= new gclient_t(i);

	}

	/*
	============
	InitGame
	
	This will be called when the dll is first loaded, which
	only happens when a new game is started or a save game
	is loaded.
	============
	*/
	public static void InitGame() {
		gi.dprintf("==== InitGame ====\n");

		gun_x= gi.cvar("gun_x", "0", 0);
		gun_y= gi.cvar("gun_y", "0", 0);
		gun_z= gi.cvar("gun_z", "0", 0);

		//FIXME: sv_ prefix is wrong for these
		sv_rollspeed= gi.cvar("sv_rollspeed", "200", 0);
		sv_rollangle= gi.cvar("sv_rollangle", "2", 0);
		sv_maxvelocity= gi.cvar("sv_maxvelocity", "2000", 0);
		sv_gravity= gi.cvar("sv_gravity", "800", 0);

		// noset vars
		dedicated= gi.cvar("dedicated", "0", CVAR_NOSET);

		// latched vars
		sv_cheats= gi.cvar("cheats", "0", CVAR_SERVERINFO | CVAR_LATCH);
		gi.cvar("gamename", GAMEVERSION, CVAR_SERVERINFO | CVAR_LATCH);
		gi.cvar("gamedate", __DATE__, CVAR_SERVERINFO | CVAR_LATCH);

		maxclients= gi.cvar("maxclients", "4", CVAR_SERVERINFO | CVAR_LATCH);
		maxspectators= gi.cvar("maxspectators", "4", CVAR_SERVERINFO);
		deathmatch= gi.cvar("deathmatch", "0", CVAR_LATCH);
		coop= gi.cvar("coop", "0", CVAR_LATCH);
		skill= gi.cvar("skill", "0", CVAR_LATCH);
		maxentities= gi.cvar("maxentities", "1024", CVAR_LATCH);

		// change anytime vars
		dmflags= gi.cvar("dmflags", "0", CVAR_SERVERINFO);
		fraglimit= gi.cvar("fraglimit", "0", CVAR_SERVERINFO);
		timelimit= gi.cvar("timelimit", "0", CVAR_SERVERINFO);
		password= gi.cvar("password", "", CVAR_USERINFO);
		spectator_password= gi.cvar("spectator_password", "", CVAR_USERINFO);
		needpass= gi.cvar("needpass", "0", CVAR_SERVERINFO);
		filterban= gi.cvar("filterban", "1", 0);

		g_select_empty= gi.cvar("g_select_empty", "0", CVAR_ARCHIVE);

		run_pitch= gi.cvar("run_pitch", "0.002", 0);
		run_roll= gi.cvar("run_roll", "0.005", 0);
		bob_up= gi.cvar("bob_up", "0.005", 0);
		bob_pitch= gi.cvar("bob_pitch", "0.002", 0);
		bob_roll= gi.cvar("bob_roll", "0.002", 0);

		// flood control
		flood_msgs= gi.cvar("flood_msgs", "4", 0);
		flood_persecond= gi.cvar("flood_persecond", "4", 0);
		flood_waitdelay= gi.cvar("flood_waitdelay", "10", 0);

		// dm map list
		sv_maplist= gi.cvar("sv_maplist", "", 0);

		// items
		InitItems();

		game.helpmessage1= "";
		game.helpmessage2= "";

		// initialize all entities for this game
		game.maxentities= (int) maxentities.value;
		CreateEdicts();

		globals.edicts= g_edicts;
		globals.max_edicts= game.maxentities;

		// initialize all clients for this game
		game.maxclients= (int) maxclients.value;

		CreateClients();

		globals.num_edicts= game.maxclients + 1;
	}

	/*
	============
	WriteGame
	
	This will be called whenever the game goes to a new level,
	and when the user explicitly saves the game.
	
	Game information include cross level data, like multi level
	triggers, help computer info, and all client states.
	
	A single player death will automatically restore from the
	last save position.
	============
	*/
	public static void WriteGame(String filename, boolean autosave) {
		try {
			QuakeFile f;

			if (!autosave)
				SaveClientData();

			f= new QuakeFile(filename, "rw");

			if (f == null)
				gi.error("Couldn't write to " + filename);

			game.autosaved= autosave;
			game.write(f);
			game.autosaved= false;

			for (int i= 0; i < game.maxclients; i++)
				game.clients[i].write(f);

			fclose(f);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void ReadGame(String filename) {

		QuakeFile f= null;

		try {

			f= new QuakeFile(filename, "r");
			Com.Printf("loading game:" + filename);
			CreateEdicts();

			game.load(f);

			for (int i= 0; i < game.maxclients; i++) {
				game.clients[i]= new gclient_t(i);
				game.clients[i].read(f);
			}

			f.close();
		}

		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	=================
	WriteLevel
	
	=================
	*/
	static void WriteLevel(String filename) {
		try {
			int i;
			edict_t ent;
			QuakeFile f;

			f= new QuakeFile(filename, "rw");
			if (f == null)
				gi.error("Couldn't open for writing: " + filename);

			// write out level_locals_t
			level.write(f);

			// write out all the entities
			for (i= 0; i < globals.num_edicts; i++) {
				ent= g_edicts[i];
				if (!ent.inuse)
					continue;
				f.writeInt(i);
				ent.write(f);

			}

			i= -1;
			f.writeInt(-1);

			f.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	=================
	ReadLevel
	
	SpawnEntities will allready have been called on the
	level the same way it was when the level was saved.
	
	That is necessary to get the baselines
	set up identically.
	
	The server will have cleared all of the world links before
	calling ReadLevel.
	
	No clients are connected yet.
	=================
	*/
	static void ReadLevel(String filename) {
		try {
			edict_t ent;

			QuakeFile f= new QuakeFile(filename, "r");

			if (f == null)
				gi.error("Couldn't read level file " + filename);

			// wipe all the entities
			Game.CreateEdicts();

			globals.num_edicts= (int) maxclients.value + 1;

			// load the level locals
			level.read(f);

			// load all the entities
			while (true) {
				int entnum= f.readInt();
				if (entnum == -1)
					break;

				if (entnum >= globals.num_edicts)
					globals.num_edicts= entnum + 1;

				ent= g_edicts[entnum];
				System.out.println("readint ent" + entnum);
				ent.read(f);
				ent.cleararealinks();
				gi.linkentity(ent);
			}

			fclose(f);

			// mark all clients as unconnected
			for (int i= 0; i < maxclients.value; i++) {
				ent= g_edicts[i + 1];
				ent.client= game.clients[i];
				ent.client.pers.connected= false;
			}

			// do any load time things at this point
			for (int i= 0; i < globals.num_edicts; i++) {
				ent= g_edicts[i];

				if (!ent.inuse)
					continue;

				// fire any cross-level triggers
				if (ent.classname != null)
					if (strcmp(ent.classname, "target_crosslevel_target") == 0)
						ent.nextthink= level.time + ent.delay;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
