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
// $Id: GameSave.java,v 1.10 2004-02-04 18:10:55 rst Exp $

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

	public static field_t levelfields[] =
		{
			new field_t("changemap", F_LSTRING),
			new field_t("sight_client", F_EDICT),
			new field_t("sight_entity", F_EDICT),
			new field_t("sound_entity", F_EDICT),
			new field_t("sound2_entity", F_EDICT),
			new field_t(null, F_INT)};

	public static field_t clientfields[] =
		{
			new field_t("pers.weapon", F_ITEM),
			new field_t("pers.lastweapon", F_ITEM),
			new field_t("newweapon", F_ITEM),
			new field_t(null, F_INT)};

	public static void CreateEdicts() {
		g_edicts = new edict_t[game.maxentities];
		for (int i = 0; i < game.maxentities; i++)
			g_edicts[i] = new edict_t(i);
	}

	public static void CreateClients() {
		game.clients = new gclient_t[game.maxclients];
		for (int i = 0; i < game.maxclients; i++)
			game.clients[i] = new gclient_t(i);

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

		gun_x = gi.cvar("gun_x", "0", 0);
		gun_y = gi.cvar("gun_y", "0", 0);
		gun_z = gi.cvar("gun_z", "0", 0);

		//FIXME: sv_ prefix is wrong for these
		sv_rollspeed = gi.cvar("sv_rollspeed", "200", 0);
		sv_rollangle = gi.cvar("sv_rollangle", "2", 0);
		sv_maxvelocity = gi.cvar("sv_maxvelocity", "2000", 0);
		sv_gravity = gi.cvar("sv_gravity", "800", 0);

		// noset vars
		dedicated = gi.cvar("dedicated", "0", CVAR_NOSET);

		// latched vars
		sv_cheats = gi.cvar("cheats", "0", CVAR_SERVERINFO | CVAR_LATCH);
		gi.cvar("gamename", GAMEVERSION, CVAR_SERVERINFO | CVAR_LATCH);
		gi.cvar("gamedate", __DATE__, CVAR_SERVERINFO | CVAR_LATCH);

		maxclients = gi.cvar("maxclients", "4", CVAR_SERVERINFO | CVAR_LATCH);
		maxspectators = gi.cvar("maxspectators", "4", CVAR_SERVERINFO);
		deathmatch = gi.cvar("deathmatch", "0", CVAR_LATCH);
		coop = gi.cvar("coop", "0", CVAR_LATCH);
		skill = gi.cvar("skill", "1", CVAR_LATCH);
		maxentities = gi.cvar("maxentities", "1024", CVAR_LATCH);

		// change anytime vars
		dmflags = gi.cvar("dmflags", "0", CVAR_SERVERINFO);
		fraglimit = gi.cvar("fraglimit", "0", CVAR_SERVERINFO);
		timelimit = gi.cvar("timelimit", "0", CVAR_SERVERINFO);
		password = gi.cvar("password", "", CVAR_USERINFO);
		spectator_password = gi.cvar("spectator_password", "", CVAR_USERINFO);
		needpass = gi.cvar("needpass", "0", CVAR_SERVERINFO);
		filterban = gi.cvar("filterban", "1", 0);

		g_select_empty = gi.cvar("g_select_empty", "0", CVAR_ARCHIVE);

		run_pitch = gi.cvar("run_pitch", "0.002", 0);
		run_roll = gi.cvar("run_roll", "0.005", 0);
		bob_up = gi.cvar("bob_up", "0.005", 0);
		bob_pitch = gi.cvar("bob_pitch", "0.002", 0);
		bob_roll = gi.cvar("bob_roll", "0.002", 0);

		// flood control
		flood_msgs = gi.cvar("flood_msgs", "4", 0);
		flood_persecond = gi.cvar("flood_persecond", "4", 0);
		flood_waitdelay = gi.cvar("flood_waitdelay", "10", 0);

		// dm map list
		sv_maplist = gi.cvar("sv_maplist", "", 0);

		// items
		InitItems();

		//Com_sprintf (game.helpmessage1, sizeof(game.helpmessage1), "");
		game.helpmessage1 = "";

		//Com_sprintf (game.helpmessage2, sizeof(game.helpmessage2), "");
		game.helpmessage2 = "";

		// initialize all entities for this game
		game.maxentities = (int) maxentities.value;

		//g_edicts = gi.TagMalloc(game.maxentities * sizeof(g_edicts[0]), TAG_GAME);
		CreateEdicts();

		globals.edicts = g_edicts;
		globals.max_edicts = game.maxentities;

		// initialize all clients for this game
		game.maxclients = (int) maxclients.value;

		//game.clients = gi.TagMalloc(game.maxclients * sizeof(game.clients[0]), TAG_GAME);
		CreateClients();

		globals.num_edicts = game.maxclients + 1;
	}

	////=========================================================
	//
	//public static void WriteField1 (FILE *f, field_t field, byte base)
	//{
	//	void		*p;
	//	int			len;
	//	int			index;
	//
	//	if (field.flags & FFL_SPAWNTEMP)
	//		return;
	//
	//	p = (void *)(base + field.ofs);
	//	switch (field.type)
	//	{
	//	case F_INT:
	//	case F_FLOAT:
	//	case F_ANGLEHACK:
	//	case F_VECTOR:
	//	case F_IGNORE:
	//		break;
	//
	//	case F_LSTRING:
	//	case F_GSTRING:
	//		if ( *(char **)p )
	//			len = strlen(*(char **)p) + 1;
	//		else
	//			len = 0;
	//		*(int *)p = len;
	//		break;
	//	case F_EDICT:
	//		if ( *(edict_t **)p == NULL)
	//			index = -1;
	//		else
	//			index = *(edict_t **)p - g_edicts;
	//		*(int *)p = index;
	//		break;
	//	case F_CLIENT:
	//		if ( *(gclient_t **)p == NULL)
	//			index = -1;
	//		else
	//			index = *(gclient_t **)p - game.clients;
	//		*(int *)p = index;
	//		break;
	//	case F_ITEM:
	//		if ( *(edict_t **)p == NULL)
	//			index = -1;
	//		else
	//			index = *(gitem_t **)p - itemlist;
	//		*(int *)p = index;
	//		break;
	//
	//	//relative to code segment
	//	case F_FUNCTION:
	//		if (*(byte **)p == NULL)
	//			index = 0;
	//		else
	//			index = *(byte **)p - ((byte *)InitGame);
	//		*(int *)p = index;
	//		break;
	//
	//	//relative to data segment
	//	case F_MMOVE:
	//		if (*(byte **)p == NULL)
	//			index = 0;
	//		else
	//			index = *(byte **)p - (byte *)&mmove_reloc;
	//		*(int *)p = index;
	//		break;
	//
	//	default:
	//		gi.error ("WriteEdict: unknown field type");
	//	}
	//}
	//
	//
	//void WriteField2 (FILE *f, field_t *field, byte *base)
	//{
	//	int			len;
	//	void		*p;
	//
	//	if (field.flags & FFL_SPAWNTEMP)
	//		return;
	//
	//	p = (void *)(base + field.ofs);
	//	switch (field.type)
	//	{
	//	case F_LSTRING:
	//		if ( *(char **)p )
	//		{
	//			len = strlen(*(char **)p) + 1;
	//			fwrite (*(char **)p, len, 1, f);
	//		}
	//		break;
	//	}
	//}
	//
	//void ReadField (FILE *f, field_t *field, byte *base)
	//{
	//	void		*p;
	//	int			len;
	//	int			index;
	//
	//	if (field.flags & FFL_SPAWNTEMP)
	//		return;
	//
	//	p = (void *)(base + field.ofs);
	//	switch (field.type)
	//	{
	//	case F_INT:
	//	case F_FLOAT:
	//	case F_ANGLEHACK:
	//	case F_VECTOR:
	//	case F_IGNORE:
	//		break;
	//
	//	case F_LSTRING:
	//		len = *(int *)p;
	//		if (!len)
	//			*(char **)p = NULL;
	//		else
	//		{
	//			*(char **)p = gi.TagMalloc (len, TAG_LEVEL);
	//			fread (*(char **)p, len, 1, f);
	//		}
	//		break;
	//	case F_EDICT:
	//		index = *(int *)p;
	//		if ( index == -1 )
	//			*(edict_t **)p = NULL;
	//		else
	//			*(edict_t **)p = &g_edicts[index];
	//		break;
	//	case F_CLIENT:
	//		index = *(int *)p;
	//		if ( index == -1 )
	//			*(gclient_t **)p = NULL;
	//		else
	//			*(gclient_t **)p = &game.clients[index];
	//		break;
	//	case F_ITEM:
	//		index = *(int *)p;
	//		if ( index == -1 )
	//			*(gitem_t **)p = NULL;
	//		else
	//			*(gitem_t **)p = &itemlist[index];
	//		break;
	//
	//	//relative to code segment
	//	case F_FUNCTION:
	//		index = *(int *)p;
	//		if ( index == 0 )
	//			*(byte **)p = NULL;
	//		else
	//			*(byte **)p = ((byte *)InitGame) + index;
	//		break;
	//
	//	//relative to data segment
	//	case F_MMOVE:
	//		index = *(int *)p;
	//		if (index == 0)
	//			*(byte **)p = NULL;
	//		else
	//			*(byte **)p = (byte *)&mmove_reloc + index;
	//		break;
	//
	//	default:
	//		gi.error ("ReadEdict: unknown field type");
	//	}
	//}
	//
	////=========================================================
	//
	///*
	//==============
	//WriteClient
	//
	//All pointer variables (except function pointers) must be handled specially.
	//==============
	//*/
	//void WriteClient (FILE *f, gclient_t *client)
	//{
	//	field_t		*field;
	//	gclient_t	temp;
	//	
	//	// all of the ints, floats, and vectors stay as they are
	//	temp = *client;
	//
	//	// change the pointers to lengths or indexes
	//	for (field=clientfields ; field.name ; field++)
	//	{
	//		WriteField1 (f, field, (byte *)&temp);
	//	}
	//
	//	// write the block
	//	fwrite (&temp, sizeof(temp), 1, f);
	//
	//	// now write any allocated data following the edict
	//	for (field=clientfields ; field.name ; field++)
	//	{
	//		WriteField2 (f, field, (byte *)client);
	//	}
	//}
	//
	/*
	==============
	ReadClient
	
	All pointer variables (except function pointers) must be handled specially.
	==============
	*/

	//} //

	/*
	//============
	//WriteGame
	//
	//This will be called whenever the game goes to a new level,
	//and when the user explicitly saves the game.
	//
	//Game information include cross level data, like multi level
	//triggers, help computer info, and all client states.
	//
	//A single player death will automatically restore from the
	//last save position.
	//============
	//*/
	//public static void WriteGame (String filename, boolean autosave)
	//{
	//	FILE	*f;
	//	int		i;
	//	char	str[16];
	//
	//	if (!autosave)
	//		SaveClientData ();
	//
	//	f = fopen (filename, "wb");
	//	if (!f)
	//		gi.error ("Couldn't open %s", filename);
	//
	//	memset (str, 0, sizeof(str));
	//	strcpy (str, __DATE__);
	//	fwrite (str, sizeof(str), 1, f);
	//
	//	game.autosaved = autosave;
	//	fwrite (&game, sizeof(game), 1, f);
	//	game.autosaved = false;
	//
	//	for (i=0 ; i<game.maxclients ; i++)
	//		WriteClient (f, &game.clients[i]);
	//
	//	fclose (f);
	//}
	//

	public static void ReadGame(String filename) {

		RandomAccessFile f = null;

		try {

			f = new RandomAccessFile(filename, "r");

			byte buf[] = new byte[(int) f.length()];

			Com.Printf("loading game:" + filename);

			f.readFully(buf);

			ByteBuffer bb = ByteBuffer.wrap(buf);

			bb.order(ByteOrder.LITTLE_ENDIAN);

			CreateEdicts();

			game.load(bb);
			//game.dump();

			for (int i = 0; i < game.maxclients; i++) {
				game.clients[i] = new gclient_t(i);
				game.clients[i].load(bb);
			}
			
			Com.Println("");
			Com.Println("file length:" + f.length());
			Com.Println("processed bytes:" + bb.position());

		}

		catch (Exception e) {
			e.printStackTrace();
			//gi.error ("File problems in "+ filename);
		} //if (!f)
		//	gi.error ("Couldn't open %s", filename);
		//	
		//		fread (str, sizeof(str), 1, f);
		//		if (strcmp (str, __DATE__))
		//		{
		//			fclose (f);
		//			gi.error ("Savegame from an older version.\n");
		//		}
		//	
		//		CreateEdicts();
		//	
		//		fread (game, sizeof(game), 1, f);
		//		
		//		CreateClients();
		//		
		//		for (i=0 ; i<game.maxclients ; i++)
		//			ReadClient (f, game.clients[i]);
		//	
		//		fclose (f);
		try {
			f.close();
		}
		catch (IOException e) {
			Com.Error(Defines.ERR_DROP, "Unable to load savegame");
		}
	}

	//
	////==========================================================
	//
	//
	///*
	//==============
	//WriteEdict
	//
	//All pointer variables (except function pointers) must be handled specially.
	//==============
	//*/
	//void WriteEdict (FILE *f, edict_t *ent)
	//{
	//	field_t		*field;
	//	edict_t		temp;
	//
	//	// all of the ints, floats, and vectors stay as they are
	//	temp = *ent;
	//
	//	// change the pointers to lengths or indexes
	//	for (field=fields ; field.name ; field++)
	//	{
	//		WriteField1 (f, field, (byte *)&temp);
	//	}
	//
	//	// write the block
	//	fwrite (&temp, sizeof(temp), 1, f);
	//
	//	// now write any allocated data following the edict
	//	for (field=fields ; field.name ; field++)
	//	{
	//		WriteField2 (f, field, (byte *)ent);
	//	}
	//
	//}
	//
	///*
	//==============
	//WriteLevelLocals
	//
	//All pointer variables (except function pointers) must be handled specially.
	//==============
	//*/
	//void WriteLevelLocals (FILE *f)
	//{
	//	field_t		*field;
	//	level_locals_t		temp;
	//
	//	// all of the ints, floats, and vectors stay as they are
	//	temp = level;
	//
	//	// change the pointers to lengths or indexes
	//	for (field=levelfields ; field.name ; field++)
	//	{
	//		WriteField1 (f, field, (byte *)&temp);
	//	}
	//
	//	// write the block
	//	fwrite (&temp, sizeof(temp), 1, f);
	//
	//	// now write any allocated data following the edict
	//	for (field=levelfields ; field.name ; field++)
	//	{
	//		WriteField2 (f, field, (byte *)&level);
	//	}
	//}
	//
	//
	///*
	//==============
	//ReadEdict
	//
	//All pointer variables (except function pointers) must be handled specially.
	//==============
	//*/
	//void ReadEdict (FILE *f, edict_t *ent)
	//{
	//	field_t		*field;
	//
	//	fread (ent, sizeof(*ent), 1, f);
	//
	//	for (field=fields ; field.name ; field++)
	//	{
	//		ReadField (f, field, (byte *)ent);
	//	}
	//}
	//
	///*
	//==============
	//ReadLevelLocals
	//
	//All pointer variables (except function pointers) must be handled specially.
	//==============
	//*/
	//void ReadLevelLocals (FILE *f)
	//{
	//	field_t		*field;
	//
	//	fread (&level, sizeof(level), 1, f);
	//
	//	for (field=levelfields ; field.name ; field++)
	//	{
	//		ReadField (f, field, (byte *)&level);
	//	}
	//}
	//
	///*
	//=================
	//WriteLevel
	//
	//=================
	//*/
	//void WriteLevel (char *filename)
	//{
	//	int		i;
	//	edict_t	*ent;
	//	FILE	*f;
	//	void	*base;
	//
	//	f = fopen (filename, "wb");
	//	if (!f)
	//		gi.error ("Couldn't open %s", filename);
	//
	//	// write out edict size for checking
	//	i = sizeof(edict_t);
	//	fwrite (&i, sizeof(i), 1, f);
	//
	//	// write out a function pointer for checking
	//	base = (void *)InitGame;
	//	fwrite (&base, sizeof(base), 1, f);
	//
	//	// write out level_locals_t
	//	WriteLevelLocals (f);
	//
	//	// write out all the entities
	//	for (i=0 ; i<globals.num_edicts ; i++)
	//	{
	//		ent = &g_edicts[i];
	//		if (!ent.inuse)
	//			continue;
	//		fwrite (&i, sizeof(i), 1, f);
	//		WriteEdict (f, ent);
	//	}
	//	i = -1;
	//	fwrite (&i, sizeof(i), 1, f);
	//
	//	fclose (f);
	//}
	//
	//
	///*
	//=================
	//ReadLevel
	//
	//SpawnEntities will allready have been called on the
	//level the same way it was when the level was saved.
	//
	//That is necessary to get the baselines
	//set up identically.
	//
	//The server will have cleared all of the world links before
	//calling ReadLevel.
	//
	//No clients are connected yet.
	//=================
	//*/
	//void ReadLevel (char *filename)
	//{
	//	int		entnum;
	//	FILE	*f;
	//	int		i;
	//	void	*base;
	//	edict_t	*ent;
	//
	//	f = fopen (filename, "rb");
	//	if (!f)
	//		gi.error ("Couldn't open %s", filename);
	//
	//	// free any dynamic memory allocated by loading the level
	//	// base state
	//	gi.FreeTags (TAG_LEVEL);
	//
	//	// wipe all the entities
	//	memset (g_edicts, 0, game.maxentities*sizeof(g_edicts[0]));
	//	globals.num_edicts = maxclients.value+1;
	//
	//	// check edict size
	//	fread (&i, sizeof(i), 1, f);
	//	if (i != sizeof(edict_t))
	//	{
	//		fclose (f);
	//		gi.error ("ReadLevel: mismatched edict size");
	//	}
	//
	//	// check function pointer base address
	//	fread (&base, sizeof(base), 1, f);
	//#ifdef _WIN32
	//	if (base != (void *)InitGame)
	//	{
	//		fclose (f);
	//		gi.error ("ReadLevel: function pointers have moved");
	//	}
	//#else
	//	gi.dprintf("Function offsets %d\n", ((byte *)base) - ((byte *)InitGame));
	//#endif
	//
	//	// load the level locals
	//	ReadLevelLocals (f);
	//
	//	// load all the entities
	//	while (1)
	//	{
	//		if (fread (&entnum, sizeof(entnum), 1, f) != 1)
	//		{
	//			fclose (f);
	//			gi.error ("ReadLevel: failed to read entnum");
	//		}
	//		if (entnum == -1)
	//			break;
	//		if (entnum >= globals.num_edicts)
	//			globals.num_edicts = entnum+1;
	//
	//		ent = &g_edicts[entnum];
	//		ReadEdict (f, ent);
	//
	//		// let the server rebuild world links for this ent
	//		memset (&ent.area, 0, sizeof(ent.area));
	//		gi.linkentity (ent);
	//	}
	//
	//	fclose (f);
	//
	//	// mark all clients as unconnected
	//	for (i=0 ; i<maxclients.value ; i++)
	//	{
	//		ent = &g_edicts[i+1];
	//		ent.client = game.clients + i;
	//		ent.client.pers.connected = false;
	//	}
	//
	//	// do any load time things at this point
	//	for (i=0 ; i<globals.num_edicts ; i++)
	//	{
	//		ent = &g_edicts[i];
	//
	//		if (!ent.inuse)
	//			continue;
	//
	//		// fire any cross-level triggers
	//		if (ent.classname)
	//			if (strcmp(ent.classname, "target_crosslevel_target") == 0)
	//				ent.nextthink = level.time + ent.delay;
	//	}
	//}

}
