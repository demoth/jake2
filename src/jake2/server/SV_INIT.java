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

// Created on 14.01.2004 by RST.
// $Id: SV_INIT.java,v 1.3 2004-01-20 22:25:06 rst Exp $

package jake2.server;

import java.io.IOException;
import java.io.RandomAccessFile;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.util.Lib;

public class SV_INIT extends SV_GAME {

	public static server_static_t svs = new server_static_t(); // persistant server info
	public static server_t sv = new server_t(); // local server

	/*
	================
	SV_FindIndex
	
	================
	*/
	public static int SV_FindIndex(String name, int start, int max, boolean create) {
		int i;

		if (name == null || name.length() == 0)
			return 0;

		for (i = 1; i < max && sv.configstrings[start + i] != null; i++)
			if (0 == Lib.strcmp(sv.configstrings[start + i], name))
				return i;

		if (!create)
			return 0;

		if (i == max)
			Com.Error(ERR_DROP, "*Index: overflow");

		//strncpy (sv.configstrings[start+i], name, sizeof(sv.configstrings[i]));
		sv.configstrings[start + i] = name;

		if (sv.state != Defines.ss_loading) { // send the update to everyone
			SZ.Clear(sv.multicast);
			MSG.WriteChar(sv.multicast, svc_configstring);
			MSG.WriteShort(sv.multicast, start + i);
			MSG.WriteString(sv.multicast, name);
			SV_SEND.SV_Multicast(vec3_origin, MULTICAST_ALL_R);
		}

		return i;
	}

	public static int SV_ModelIndex(String name) {
		return SV_FindIndex(name, CS_MODELS, MAX_MODELS, true);
	}

	public static int SV_SoundIndex(String name) {
		return SV_FindIndex(name, CS_SOUNDS, MAX_SOUNDS, true);
	}

	public static int SV_ImageIndex(String name) {
		return SV_FindIndex(name, CS_IMAGES, MAX_IMAGES, true);
	}

	/*
	================
	SV_CreateBaseline
	
	Entity baselines are used to compress the update messages
	to the clients -- only the fields that differ from the
	baseline will be transmitted
	================
	*/
	public static void SV_CreateBaseline() {
		edict_t svent;
		int entnum;

		for (entnum = 1; entnum < ge.num_edicts; entnum++) {
			//svent = EDICT_NUM(entnum);
			svent = ge.edicts[entnum];

			if (!svent.inuse)
				continue;
			if (0 == svent.s.modelindex && 0 == svent.s.sound && 0 == svent.s.effects)
				continue;
			svent.s.number = entnum;

			//
			// take current state as baseline
			//
			VectorCopy(svent.s.origin, svent.s.old_origin);
			sv.baselines[entnum] = svent.s;
		}
	}

	/*
	=================
	SV_CheckForSavegame
	=================
	*/
	public static void SV_CheckForSavegame() {
		//char		name[MAX_OSPATH];
		String name;
		//FILE		*f;
		RandomAccessFile f;

		int i;

		if (SV_MAIN.sv_noreload.value != 0)
			return;

		if (Cvar.VariableValue("deathmatch") != 0)
			return;

		name = FS.Gamedir() + "/save/current/" + sv.name + ".sav";
		try {
			f = new RandomAccessFile(name, "rb");
		}

		catch (Exception e) {
			return; // no savegame
		}

		try {
			f.close();
		}
		catch (IOException e1) {
			e1.printStackTrace();
		}

		SV_WORLD.SV_ClearWorld();

		// get configstrings and areaportals
		SV_CCMDS.SV_ReadLevelFile();

		if (!sv.loadgame) { // coming back to a level after being in a different
			// level, so run it for ten seconds

			// rlava2 was sending too many lightstyles, and overflowing the
			// reliable data. temporarily changing the server state to loading
			// prevents these from being passed down.
			int previousState; // PGM

			previousState = sv.state; // PGM
			sv.state = ss_loading; // PGM
			for (i = 0; i < 100; i++)
				ge.RunFrame();

			sv.state = previousState; // PGM
		}
	}

//	/*
//	================
//	SV_SpawnServer
//	
//	Change the server to a new map, taking all connected
//	clients along with it.
//	
//	================
//	*/
//	public static void SV_SpawnServer(
//		String server,
//		String spawnpoint,
//		server_state_t serverstate,
//		boolean attractloop,
//		boolean loadgame) {
//		int i;
//		int checksum;
//
//		if (attractloop)
//			Cvar.Set("paused", "0");
//
//		Com.Printf("------- Server Initialization -------\n");
//
//		Com.DPrintf("SpawnServer: " + server + "\n");
//		if (sv.demofile != null)
//			try {
//				sv.demofile.close();
//			}
//			catch (Exception e) {
//			}
//
//		svs.spawncount++; // any partially connected client will be
//		// restarted
//		sv.state = ss_dead;
//		Com.SetServerState(sv.state);
//
//		// wipe the entire per-level structure
//		memset(sv, 0, sizeof(sv));
//		
//		svs.realtime = 0;
//		sv.loadgame = loadgame;
//		sv.attractloop = attractloop;
//
//		// save name for levels that don't set message
//		strcpy(sv.configstrings[CS_NAME], server);
//		if (Cvar_VariableValue("deathmatch")) {
//			sprintf(sv.configstrings[CS_AIRACCEL], "%g", sv_airaccelerate.value);
//			pm_airaccelerate = sv_airaccelerate.value;
//		}
//		else {
//			strcpy(sv.configstrings[CS_AIRACCEL], "0");
//			pm_airaccelerate = 0;
//		}
//
//		SZ.Init(sv.multicast, sv.multicast_buf, sv.multicast_buf.length);
//
//		sv.name = server;
//
//		// leave slots at start for clients only
//		for (i = 0; i < maxclients.value; i++) {
//			// needs to reconnect
//			if (svs.clients[i].state > cs_connected)
//				svs.clients[i].state = cs_connected;
//			svs.clients[i].lastframe = -1;
//		}
//
//		sv.time = 1000;
//
//		strcpy(sv.name, server);
//		strcpy(sv.configstrings[CS_NAME], server);
//
//		if (serverstate != ss_game) {
//			sv.models[1] = CM.CM_LoadMap("", false, checksum); // no real map
//		}
//		else {
//			sv.configstrings[CS_MODELS + 1] = "maps/" + server + ".bsp";
//			sv.models[1] = CM.CM_LoadMap(sv.configstrings[CS_MODELS + 1], false, checksum);
//		}
//		sv.configstrings[CS_MAPCHECKSUM] = checksum;
//
//		//
//		// clear physics interaction links
//		//
//		SV_WORLD.SV_ClearWorld();
//
//		for (i = 1; i < CM.CM_NumInlineModels(); i++) {
//			sv.configstrings[CS_MODELS + 1 + i] = "*" + i;
//			sv.models[i + 1] = CM.CM_InlineModel(sv.configstrings[CS_MODELS + 1 + i]);
//		}
//
//		//
//		// spawn the rest of the entities on the map
//		//	
//
//		// precache and static commands can be issued during
//		// map initialization
//		sv.state = ss_loading;
//		Com_SetServerState(sv.state);
//
//		// load and spawn all other entities
//		ge.SpawnEntities(sv.name, CM_EntityString(), spawnpoint);
//
//		// run two frames to allow everything to settle
//		ge.RunFrame();
//		ge.RunFrame();
//
//		// all precaches are complete
//		sv.state = serverstate;
//		Com_SetServerState(sv.state);
//
//		// create a baseline for more efficient communications
//		SV_CreateBaseline();
//
//		// check for a savegame
//		SV_CheckForSavegame();
//
//		// set serverinfo variable
//		Cvar_FullSet("mapname", sv.name, CVAR_SERVERINFO | CVAR_NOSET);
//
//		Com_Printf("-------------------------------------\n");
//	}
//TODO: U R G EN T !!!
	/*
	==============
	SV_InitGame
	
	A brand new game has been started
	==============
	*/
	public static void SV_InitGame() {
//		int i;
//		edict_t * ent;
//		char idmaster[32];
//
//		if (svs.initialized) {
//			// cause any connected clients to reconnect
//			SV_Shutdown("Server restarted\n", true);
//		}
//		else {
//			// make sure the client is down
//			CL_Drop();
//			SCR_BeginLoadingPlaque();
//		}
//
//		// get any latched variable changes (maxclients, etc)
//		Cvar_GetLatchedVars();
//
//		svs.initialized = true;
//
//		if (Cvar_VariableValue("coop") && Cvar_VariableValue("deathmatch")) {
//			Com_Printf("Deathmatch and Coop both set, disabling Coop\n");
//			Cvar_FullSet("coop", "0", CVAR_SERVERINFO | CVAR_LATCH);
//		}
//
//		// dedicated servers are can't be single player and are usually DM
//		// so unless they explicity set coop, force it to deathmatch
//		if (dedicated.value) {
//			if (!Cvar_VariableValue("coop"))
//				Cvar_FullSet("deathmatch", "1", CVAR_SERVERINFO | CVAR_LATCH);
//		}
//
//		// init clients
//		if (Cvar_VariableValue("deathmatch")) {
//			if (maxclients.value <= 1)
//				Cvar_FullSet("maxclients", "8", CVAR_SERVERINFO | CVAR_LATCH);
//			else if (maxclients.value > MAX_CLIENTS)
//				Cvar_FullSet("maxclients", va("%i", MAX_CLIENTS), CVAR_SERVERINFO | CVAR_LATCH);
//		}
//		else if (Cvar_VariableValue("coop")) {
//			if (maxclients.value <= 1 || maxclients.value > 4)
//				Cvar_FullSet("maxclients", "4", CVAR_SERVERINFO | CVAR_LATCH);
//
//		}
//		else // non-deathmatch, non-coop is one player
//			{
//			Cvar_FullSet("maxclients", "1", CVAR_SERVERINFO | CVAR_LATCH);
//		}
//
//		svs.spawncount = rand();
//		svs.clients = Z_Malloc(sizeof(client_t) * maxclients.value);
//		svs.num_client_entities = maxclients.value * UPDATE_BACKUP * 64;
//		svs.client_entities = Z_Malloc(sizeof(entity_state_t) * svs.num_client_entities);
//
//		// init network stuff
//		NET_Config((maxclients.value > 1));
//
//		// heartbeats will always be sent to the id master
//		svs.last_heartbeat = -99999; // send immediately
//		Com_sprintf(idmaster, sizeof(idmaster), "192.246.40.37:%i", PORT_MASTER);
//		NET_StringToAdr(idmaster, & master_adr[0]);
//
//		// init game
//		SV_InitGameProgs();
//		for (i = 0; i < maxclients.value; i++) {
//			ent = EDICT_NUM(i + 1);
//			ent.s.number = i + 1;
//			svs.clients[i].edict = ent;
//			memset(& svs.clients[i].lastcmd, 0, sizeof(svs.clients[i].lastcmd));
//		}
	}

	/*
	======================
	SV_Map
	
	  the full syntax is:
	
	  map [*]<map>$<startspot>+<nextserver>
	
	command from the console or progs.
	Map can also be a.cin, .pcx, or .dm2 file
	Nextserver is used to allow a cinematic to play, then proceed to
	another level:
	
		map tram.cin+jail_e3
	======================
	*/
	public static void SV_Map(boolean attractloop, String levelstring, boolean loadgame) {
//		//char	level[MAX_QPATH];
//		//char	*ch;
//		int l;
//		//char	spawnpoint[MAX_QPATH];
//
//		String level, ch, spawnpoint;
//
//		sv.loadgame = loadgame;
//		sv.attractloop = attractloop;
//
//		if (sv.state == ss_dead && !sv.loadgame)
//			SV_InitGame(); // the game is just starting
//
//		level = levelstring;
//
//		// if there is a + in the map, set nextserver to the remainder
//
//		//was:
//		//	ch = strstr(level, "+");
//		//	if (ch)
//		//	{
//		//		*ch = 0;
//		//			Cvar_Set ("nextserver", va("gamemap \"%s\"", ch+1));
//		//	}
//		//	else
//		//		Cvar_Set ("nextserver", "");
//
//		int c = level.indexOf('+');
//		if (c != -1) {
//
//		}
//		else {
//			Cvar.Set("nextserver", "");
//		}
//
//		//ZOID special hack for end game screen in coop mode
//		if (Cvar_VariableValue("coop") && !Q_stricmp(level, "victory.pcx"))
//			Cvar_Set("nextserver", "gamemap \"*base1\"");
//
//		// if there is a $, use the remainder as a spawnpoint
//		ch = strstr(level, "$");
//		if (ch) {
//			* ch = 0;
//			strcpy(spawnpoint, ch + 1);
//		}
//		else
//			spawnpoint[0] = 0;
//
//		// skip the end-of-unit flag if necessary
//		if (level[0] == '*')
//			strcpy(level, level + 1);
//
//		l = strlen(level);
//		if (l > 4 && !strcmp(level + l - 4, ".cin")) {
//			SCR_BeginLoadingPlaque(); // for local system
//			SV_BroadcastCommand("changing\n");
//			SV_SpawnServer(level, spawnpoint, ss_cinematic, attractloop, loadgame);
//		}
//		else if (l > 4 && !strcmp(level + l - 4, ".dm2")) {
//			SCR_BeginLoadingPlaque(); // for local system
//			SV_BroadcastCommand("changing\n");
//			SV_SpawnServer(level, spawnpoint, ss_demo, attractloop, loadgame);
//		}
//		else if (l > 4 && !strcmp(level + l - 4, ".pcx")) {
//			SCR_BeginLoadingPlaque(); // for local system
//			SV_BroadcastCommand("changing\n");
//			SV_SpawnServer(level, spawnpoint, ss_pic, attractloop, loadgame);
//		}
//		else {
//			SCR_BeginLoadingPlaque(); // for local system
//			SV_BroadcastCommand("changing\n");
//			SV_SendClientMessages();
//			SV_SpawnServer(level, spawnpoint, ss_game, attractloop, loadgame);
//			Cbuf_CopyToDefer();
//		}
//
//		SV_BroadcastCommand("reconnect\n");
	}
}
