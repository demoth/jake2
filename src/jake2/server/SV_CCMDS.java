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

// Created on 18.01.2004 by RST.
// $Id: SV_CCMDS.java,v 1.13 2004-02-15 11:27:49 rst Exp $

package jake2.server;

import jake2.Globals;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.sys.NET;
import jake2.sys.Sys;
import jake2.util.Lib;
import jake2.util.Vargs;

import java.io.*;
import java.util.Date;

public class SV_CCMDS extends SV_ENTS {

	/*
	===============================================================================
	
	OPERATOR CONSOLE ONLY COMMANDS
	
	These commands can only be entered from stdin or by a remote operator datagram
	===============================================================================
	*/

	/*
	====================
	SV_SetMaster_f
	
	Specify a list of master servers
	====================
	*/
	public static void SV_SetMaster_f() {
		int i, slot;

		// only dedicated servers send heartbeats
		if (dedicated.value == 0) {
			Com.Printf("Only dedicated servers use masters.\n");
			return;
		}

		// make sure the server is listed public
		Cvar.Set("public", "1");

		for (i = 1; i < MAX_MASTERS; i++)
			//memset (&master_adr[i], 0, sizeof(master_adr[i]));
			master_adr[i] = new netadr_t();

		slot = 1; // slot 0 will always contain the id master
		for (i = 1; i < Cmd.Argc(); i++) {
			if (slot == MAX_MASTERS)
				break;

			if (!NET.StringToAdr(Cmd.Argv(i), master_adr[i])) {
				Com.Printf("Bad address: " + Cmd.Argv(i) + "\n");
				continue;
			}
			if (master_adr[slot].port == 0)
					master_adr[slot].port = //BigShort (PORT_MASTER);
	PORT_MASTER;

			Com.Printf("Master server at " + NET.AdrToString(master_adr[slot]) + "\n");

			Com.Printf("Sending a ping.\n");

			Netchan.OutOfBandPrint(NS_SERVER, master_adr[slot], "ping");

			slot++;
		}

		svs.last_heartbeat = -9999999;
	}

	/*
	==================
	SV_SetPlayer
	
	Sets sv_client and sv_player to the player with idnum Cmd.Argv(1)
	==================
	*/
	public static boolean SV_SetPlayer() {
		client_t cl;
		int i;
		int idnum;
		String s;

		if (Cmd.Argc() < 2)
			return false;

		s = Cmd.Argv(1);

		// numeric values are just slot numbers
		if (s.charAt(0) >= '0' && s.charAt(0) <= '9') {
			idnum = atoi(Cmd.Argv(1));
			if (idnum < 0 || idnum >= maxclients.value) {
				Com.Printf("Bad client slot: " + idnum + "\n");
				return false;
			}

			sv_client = svs.clients[idnum];
			sv_player = sv_client.edict;
			if (0 == sv_client.state) {
				Com.Printf("Client " + idnum + " is not active\n");
				return false;
			}
			return true;
		}

		// check for a name match
		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			if (0 == cl.state)
				continue;
			if (0 == strcmp(cl.name, s)) {
				sv_client = cl;
				sv_player = sv_client.edict;
				return true;
			}
		}

		Com.Printf("Userid " + s + " is not on the server\n");
		return false;
	}

	/*
	===============================================================================
	
	SAVEGAME FILES
	
	===============================================================================
	*/

	public static void remove(String name) {
		try {
			new File(name).delete();
		}
		catch (Exception e) {
		}
	}

	/*
	=====================
	SV_WipeSavegame
	
	Delete save/<XXX>/
	=====================
	*/
	public static void SV_WipeSavegame(String savename) {
		//char	name[MAX_OSPATH];
		//char	*s;

		String name, s;

		Com.DPrintf("SV_WipeSaveGame(" + savename + ")\n");

		name = FS.Gamedir() + "/save/" + savename + "/server.ssv";
		remove(name);

		name = FS.Gamedir() + "/save/" + savename + "/game.ssv";
		remove(name);

		name = FS.Gamedir() + "/save/" + savename + "/*.sav";

		File f = Sys.FindFirst(name, 0, 0);
		while (f != null) {
			f.delete();
			f = Sys.FindNext();
		}
		Sys.FindClose();

		name = FS.Gamedir() + "/save/" + savename + "/*.sv2";

		f = Sys.FindFirst(name, 0, 0);

		while (f != null) {
			f.delete();
			f = Sys.FindNext();
		}
		Sys.FindClose();
	}

	/*
	================
	CopyFile
	================
	*/
	public static void CopyFile(String src, String dst) {
		RandomAccessFile f1, f2;
		int l = -1;
		byte buffer[] = new byte[65536];

		Com.DPrintf("CopyFile (" + src + ", " + dst + ")\n");

		try {
			f1 = new RandomAccessFile(src, "r");
		}
		catch (Exception e) {
			return;
		}
		try {

			f2 = new RandomAccessFile(dst, "rw");
		}
		catch (Exception e) {
			try {
				f1.close();
			}
			catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return;
		}

		while (true) {

			try {
				l = f1.read(buffer, 0, 65536);
			}
			catch (IOException e1) {

				e1.printStackTrace();
			}
			if (l == -1)
				break;
			try {
				f2.write(buffer, 0, l);
			}
			catch (IOException e2) {

				e2.printStackTrace();
			}
		}

		try {
			f1.close();
		}
		catch (IOException e1) {

			e1.printStackTrace();
		}
		try {
			f2.close();
		}
		catch (IOException e2) {

			e2.printStackTrace();
		}
	}

	/*
	================
	SV_CopySaveGame
	================
	*/
	public static void SV_CopySaveGame(String src, String dst) {
		//char name[MAX_OSPATH], name2[MAX_OSPATH];
		int l, len;
		File found;

		String name, name2;

		Com.DPrintf("SV_CopySaveGame(" + src + "," + dst + ")\n");

		SV_WipeSavegame(dst);

		// copy the savegame over
		name = FS.Gamedir() + "/save/" + src + "/server.ssv";
		name2 = FS.Gamedir() + "/save/" + dst + "/server.ssv";
		FS.CreatePath(name2);
		CopyFile(name, name2);

		name = FS.Gamedir() + "/save/" + src + "/game.ssv";
		name2 = "/save/" + dst + "/game.ssv";
		CopyFile(name, name2);

		String name1 = FS.Gamedir() + "/save/" + src + "/";
		len = strlen(name1);
		name = FS.Gamedir() + "/save/" + src + "/*.sav";

		found = Sys.FindFirst(name, 0, 0);

		while (found != null) {
			name = name1 + '/' + found.getName();
			name2 = FS.Gamedir() + "/save/" + dst + "/" + found.getName();
			CopyFile(name, name2);

			// change sav to sv2
			//l = strlen(name);
			//strcpy(name + l - 3, "sv2");
			//l = strlen(name2);
			//strcpy(name2 + l - 3, "sv2");
			name = name.substring(0, name.length() - 3) + "sv2";
			name2 = name.substring(0, name2.length() - 3) + "sv2";

			CopyFile(name, name2);

			found = Sys.FindNext();
		}
		Sys.FindClose();
	}

	/*
	==============
	SV_WriteLevelFile
	
	==============
	*/
	public static void SV_WriteLevelFile() {
		//char name[MAX_OSPATH];
		//FILE * f;

		String name;
		RandomAccessFile f;

		Com.DPrintf("SV_WriteLevelFile()\n");

		name = FS.Gamedir() + "/save/current/" + sv.name + ".sv2";

		try {
			f = new RandomAccessFile(name, "rw");
		}
		catch (Exception e) {
			Com.Printf("Failed to open " + name + "\n");
			return;
		}
		try {
			//fwrite(sv.configstrings, sizeof(sv.configstrings), 1, f);
			for (int i = 0; i < sv.configstrings.length; i++)
				Lib.fwriteString(sv.configstrings[i], MAX_QPATH, f);

			CM.CM_WritePortalState(f);
			f.close();
		}
		catch (Exception e) {
			Com.Printf("IOError in SV_WriteLevelFile: " + e);
			e.printStackTrace();
		}

		name = FS.Gamedir() + "/save/current/" + sv.name + ".sav";
		ge.WriteLevel(name);
	}

	/*
	==============
	SV_ReadLevelFile
	
	==============
	*/
	public static void SV_ReadLevelFile() {
		//char name[MAX_OSPATH];
		String name;
		RandomAccessFile f;

		Com.DPrintf("SV_ReadLevelFile()\n");

		name = FS.Gamedir() + "/save/current/" + sv.name + ".sv2";
		try {
			f = new RandomAccessFile(name, "r");
		}
		catch (Exception e) {
			Com.Printf("Failed to open " + name + "\n");
			return;
		}
		//		FS.Read(sv.configstrings, sizeof(sv.configstrings), f);
		for (int n = 0; n < MAX_CONFIGSTRINGS; n++)
			sv.configstrings[n] = Lib.freadString(f, MAX_QPATH);

		CM.CM_ReadPortalState(f);

		try {
			f.close();
		}
		catch (IOException e1) {
			e1.printStackTrace();
		}

		name = FS.Gamedir() + "/save/current/" + sv.name + ".sav";
		ge.ReadLevel(name);
	}

	/*
	==============
	SV_WriteServerFile
	
	==============
	*/
	public static void SV_WriteServerFile(boolean autosave) {
		RandomAccessFile f;
		cvar_t var;
		//char name[MAX_OSPATH], string[128];
		//char comment[32];
		//time_t aclock;
		//struct tm * newtime;
		String name, string, comment;

		Com.DPrintf("SV_WriteServerFile(" + (autosave ? "true" : "false") + ")\n");

		name = FS.Gamedir() + "/save/current/server.ssv";
		try {
			f = new RandomAccessFile(name, "rw");
		} catch (FileNotFoundException e) {
			f = null;
		}
		if (f == null) {
			Com.Printf("Couldn't write " + name + "\n");
			return;
		}
		// write the comment field
		//memset(comment, 0, sizeof(comment));

		if (!autosave) {
			//time( aclock);
			//newtime = localtime( aclock);
			Date newtime = new Date();
			comment =
				Com.sprintf(
					"%2i:%2i %2i/%2i  ",
					new Vargs().add(newtime.getHours()).add(newtime.getMinutes()).add(newtime.getMonth() + 1).add(newtime.getDay()));
			comment += sv.configstrings[CS_NAME];
		}
		else { // autosaved
			comment = "ENTERING " + sv.configstrings[CS_NAME];
		}

		try {
			fwriteString(comment, 32, f);
			fwriteString(svs.mapcmd, MAX_TOKEN_CHARS, f);

		} catch (IOException e1) {}

		// write the mapcmd
		
		// write all CVAR_LATCH cvars
		// these will be things like coop, skill, deathmatch, etc
		for (var = Globals.cvar_vars; var != null; var = var.next) {
			if (0 == (var.flags & CVAR_LATCH))
				continue;
			if (strlen(var.name) >= MAX_OSPATH - 1 || strlen(var.string) >= 128 - 1) {
				Com.Printf("Cvar too long: " + var.name + " = " + var.string + "\n");
				continue;
			}
			//memset(name, 0, sizeof(name));
			//memset(string, 0, sizeof(string));
			name = var.name;
			string = var.string;
			try {
				fwriteString(name, MAX_OSPATH, f);
				fwriteString(string, 128, f);
			} catch (IOException e2) {}
			
		}

		try {
			f.close();
		} catch (IOException e2) {}

		// write game state
		name = FS.Gamedir() + "/save/current/game.ssv";
		ge.WriteGame(name, autosave);
	}

	/*
	==============
	SV_ReadServerFile
	
	==============
	*/
	public static void SV_ReadServerFile() {
		RandomAccessFile f;
		//char	name[MAX_OSPATH], string[128];
		//char	comment[32];
		//char	mapcmd[MAX_TOKEN_CHARS];

		String name, string, comment, mapcmd;

		Com.DPrintf("SV_ReadServerFile()\n");

		name = FS.Gamedir() + "/save/current/server.ssv";
		try {
			f = new RandomAccessFile(name, "r");
		}
		catch (FileNotFoundException e1) {
			Com.Printf("Couldn't read " + name + "\n");
			e1.printStackTrace();
			return;
		}
		// read the comment field
		comment = Lib.freadString(f, 32);

		// read the mapcmd
		mapcmd = Lib.freadString(f, MAX_TOKEN_CHARS);

		// read all CVAR_LATCH cvars
		// these will be things like coop, skill, deathmatch, etc
		while (true) {
			name = Lib.freadString(f, MAX_OSPATH);
			//if (!fread(name, 1, sizeof(name), f))
			if (name == null)
				break;
			string = Lib.freadString(f, 128);
			Com.DPrintf("Set " + name + " = " + string + "\n");
			Cvar.ForceSet(name, string);
		}

		try {
			f.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		// start a new game fresh with new cvars
		SV_InitGame();

		svs.mapcmd = mapcmd;

		// read game state
		name = FS.Gamedir() + "/save/current/game.ssv";
		ge.ReadGame(name);
	}

	//=========================================================

	/*
	==================
	SV_DemoMap_f
	
	Puts the server in demo mode on a specific map/cinematic
	==================
	*/
	public static void SV_DemoMap_f() {
		SV_Map(true, Cmd.Argv(1), false);
	}

	/*
	==================
	SV_GameMap_f
	
	Saves the state of the map just being exited and goes to a new map.
	
	If the initial character of the map string is '*', the next map is
	in a new unit, so the current savegame directory is cleared of
	map files.
	
	Example:
	
	*inter.cin+jail
	
	Clears the archived maps, plays the inter.cin cinematic, then
	goes to map jail.bsp.
	==================
	*/
	public static void SV_GameMap_f() {
		String map;
		int i;
		client_t cl;
		boolean savedInuse[];

		if (Cmd.Argc() != 2) {
			Com.Printf("USAGE: gamemap <map>\n");
			return;
		}

		Com.DPrintf("SV_GameMap(" + Cmd.Argv(1) + ")\n");

		FS.CreatePath(FS.Gamedir() + "/save/current/");

		// check for clearing the current savegame
		map = Cmd.Argv(1);
		if (map.charAt(0) == '*') {
			// wipe all the *.sav files
			SV_WipeSavegame("current");
		}
		else { // save the map just exited
			if (sv.state == ss_game) {
				// clear all the client inuse flags before saving so that
				// when the level is re-entered, the clients will spawn
				// at spawn points instead of occupying body shells
				savedInuse = new boolean[(int) maxclients.value];
				for (i = 0; i < maxclients.value; i++) {
					cl = svs.clients[i];
					savedInuse[i] = cl.edict.inuse;
					cl.edict.inuse = false;
				}

				SV_WriteLevelFile();

				// we must restore these for clients to transfer over correctly
				for (i = 0; i < maxclients.value; i++) {
					cl = svs.clients[i];
					cl.edict.inuse = savedInuse[i];

				}
				savedInuse = null;
			}
		}

		// start up the next map
		SV_Map(false, Cmd.Argv(1), false);

		// archive server state
		svs.mapcmd = Cmd.Argv(1);

		// copy off the level to the autosave slot
		if (0 == dedicated.value) {

			//TODO: SV_WriteServerFile!
			//SV_WriteServerFile(true);

			//SV_CopySaveGame("current", "save0");
		}
	}

	/*
	==================
	SV_Map_f
	
	Goes directly to a given map without any savegame archiving.
	For development work
	==================
	*/
	public static void SV_Map_f() {
		String map;
		//char expanded[MAX_QPATH];
		String expanded;

		// if not a pcx, demo, or cinematic, check to make sure the level exists
		map = Cmd.Argv(1);
		if (!strstr(map, ".")) {
			expanded = "maps/" + map + ".bsp";
			if (FS.LoadFile(expanded) == null) {
				Com.Printf("Can't find " + expanded + "\n");
				return;
			}
		}

		sv.state = ss_dead; // don't save current level when changing
		//TODO: RST: disabled for debugging
		//SV_WipeSavegame("current");
		SV_GameMap_f();
	}

	/*
	=====================================================================
	
	  SAVEGAMES
	
	=====================================================================
	*/

	/*
	==============
	SV_Loadgame_f
	
	==============
	*/
	public static void SV_Loadgame_f() {
		//char name[MAX_OSPATH];
		//FILE * f;
		//char * dir;

		String name;
		RandomAccessFile f;
		String dir;

		if (Cmd.Argc() != 2) {
			Com.Printf("USAGE: loadgame <directory>\n");
			return;
		}

		Com.Printf("Loading game...\n");

		dir = Cmd.Argv(1);
		if (strstr(dir, "..") || strstr(dir, "/") || strstr(dir, "\\")) {
			Com.Printf("Bad savedir.\n");
		}

		// make sure the server.ssv file exists
		name = FS.Gamedir() + "/save/" + Cmd.Argv(1) + "/server.ssv";
		try {
			f = new RandomAccessFile(name, "r");
		}
		catch (FileNotFoundException e) {
			Com.Printf("No such savegame: " + name + "\n");
			return;
		}

		try {
			f.close();
		}
		catch (IOException e1) {
			e1.printStackTrace();
		}

		SV_CopySaveGame(Cmd.Argv(1), "current");

		SV_ReadServerFile();

		// go to the map
		sv.state = ss_dead; // don't save current level when changing
		SV_INIT.SV_Map(false, svs.mapcmd, true);
	}

	/*
	==============
	SV_Savegame_f
	
	==============
	*/
	public static void SV_Savegame_f() {
		String dir;

		if (sv.state != ss_game) {
			Com.Printf("You must be in a game to save.\n");
			return;
		}

		if (Cmd.Argc() != 2) {
			Com.Printf("USAGE: savegame <directory>\n");
			return;
		}

		if (Cvar.VariableValue("deathmatch") != 0) {
			Com.Printf("Can't savegame in a deathmatch\n");
			return;
		}

		if (0 == strcmp(Cmd.Argv(1), "current")) {
			Com.Printf("Can't save to 'current'\n");
			return;
		}

		if (maxclients.value == 1 && svs.clients[0].edict.client.ps.stats[STAT_HEALTH] <= 0) {
			Com.Printf("\nCan't savegame while dead!\n");
			return;
		}

		dir = Cmd.Argv(1);
		if (strstr(dir, "..") || strstr(dir, "/") || strstr(dir, "\\")) {
			Com.Printf("Bad savedir.\n");
		}

		Com.Printf("Saving game...\n");

		// archive current level, including all client edicts.
		// when the level is reloaded, they will be shells awaiting
		// a connecting client
		SV_WriteLevelFile();

		// save server state
		try {
			SV_WriteServerFile(false);
		}
		catch (Exception e) {
			Com.Printf("IOError in SV_WriteServerFile: " + e);
		}

		// copy it off
		SV_CopySaveGame("current", dir);

		Com.Printf("Done.\n");
	}

	//===============================================================

	/*
	==================
	SV_Kick_f
	
	Kick a user off of the server
	==================
	*/
	public static void SV_Kick_f() {
		if (!svs.initialized) {
			Com.Printf("No server running.\n");
			return;
		}

		if (Cmd.Argc() != 2) {
			Com.Printf("Usage: kick <userid>\n");
			return;
		}

		if (!SV_SetPlayer())
			return;

		SV_BroadcastPrintf(PRINT_HIGH, sv_client.name + " was kicked\n");
		// print directly, because the dropped client won't get the
		// SV_BroadcastPrintf message
		SV_ClientPrintf(sv_client, PRINT_HIGH, "You were kicked from the game\n");
		SV_DropClient(sv_client);
		sv_client.lastmessage = svs.realtime; // min case there is a funny zombie
	}

	/*
	================
	SV_Status_f
	================
	*/
	public static void SV_Status_f() {
		int i, j, l;
		client_t cl;
		String s;
		int ping;
		if (svs.clients == null) {
			Com.Printf("No server running.\n");
			return;
		}
		Com.Printf("map              : " + sv.name + "\n");

		Com.Printf("num score ping name            lastmsg address               qport \n");
		Com.Printf("--- ----- ---- --------------- ------- --------------------- ------\n");
		for (i = 0; i < maxclients.value; i++) {
			cl = svs.clients[i];
			if (0 == cl.state)
				continue;

			Com.Printf("%3i ", new Vargs().add(i));
			Com.Printf("%5i ", new Vargs().add(cl.edict.client.ps.stats[STAT_FRAGS]));

			if (cl.state == cs_connected)
				Com.Printf("CNCT ");
			else if (cl.state == cs_zombie)
				Com.Printf("ZMBI ");
			else {
				ping = cl.ping < 9999 ? cl.ping : 9999;
				Com.Printf("%4i ", new Vargs().add(ping));
			}

			Com.Printf("%s", new Vargs().add(cl.name));
			l = 16 - strlen(cl.name);
			for (j = 0; j < l; j++)
				Com.Printf(" ");

			Com.Printf("%7i ", new Vargs().add(svs.realtime - cl.lastmessage));

			s = NET.AdrToString(cl.netchan.remote_address);
			Com.Printf(s);
			l = 22 - strlen(s);
			for (j = 0; j < l; j++)
				Com.Printf(" ");

			Com.Printf("%5i", new Vargs().add(cl.netchan.qport));

			Com.Printf("\n");
		}
		Com.Printf("\n");
	}

	/*
	==================
	SV_ConSay_f
	==================
	*/
	public static void SV_ConSay_f() {
		client_t client;
		int j;
		String p;
		String text; // char[1024];

		if (Cmd.Argc() < 2)
			return;

		text = "console: ";
		p = Cmd.Args();

		if (p.charAt(0) == '"') {
			p = p.substring(1, p.length() - 1);
		}

		text += p;

		for (j = 0; j < maxclients.value; j++) {
			client = svs.clients[j];
			if (client.state != cs_spawned)
				continue;
			SV_ClientPrintf(client, PRINT_CHAT, text + "\n");
		}
	}

	/*
	==================
	SV_Heartbeat_f
	==================
	*/
	public static void SV_Heartbeat_f() {
		svs.last_heartbeat = -9999999;
	}

	/*
	===========
	SV_Serverinfo_f
	
	  Examine or change the serverinfo string
	===========
	*/
	public static void SV_Serverinfo_f() {
		Com.Printf("Server info settings:\n");
		Info.Print(Cvar.Serverinfo());
	}

	/*
	===========
	SV_DumpUser_f
	
	Examine all a users info strings
	===========
	*/
	public static void SV_DumpUser_f() {
		if (Cmd.Argc() != 2) {
			Com.Printf("Usage: info <userid>\n");
			return;
		}

		if (!SV_SetPlayer())
			return;

		Com.Printf("userinfo\n");
		Com.Printf("--------\n");
		Info.Print(sv_client.userinfo);

	}

	/*
	==============
	SV_ServerRecord_f
	
	Begins server demo recording.  Every entity and every message will be
	recorded, but no playerinfo will be stored.  Primarily for demo merging.
	==============
	*/
	public static void SV_ServerRecord_f() {
		//char	name[MAX_OSPATH];
		String name;
		byte buf_data[] = new byte[32768];
		sizebuf_t buf = new sizebuf_t();
		int len;
		int i;

		if (Cmd.Argc() != 2) {
			Com.Printf("serverrecord <demoname>\n");
			return;
		}

		if (svs.demofile != null) {
			Com.Printf("Already recording.\n");
			return;
		}

		if (sv.state != ss_game) {
			Com.Printf("You must be in a level to record.\n");
			return;
		}

		//
		// open the demo file
		//
		name = FS.Gamedir() + "/demos/" + Cmd.Argv(1) + ".dm2";

		Com.Printf("recording to " + name + ".\n");
		FS.CreatePath(name);
		try {
			svs.demofile = new RandomAccessFile(name, "rw");
		}
		catch (Exception e) {
			Com.Printf("ERROR: couldn't open.\n");
			return;
		}

		// setup a buffer to catch all multicasts
		SZ.Init(svs.demo_multicast, svs.demo_multicast_buf, svs.demo_multicast_buf.length);

		//
		// write a single giant fake message with all the startup info
		//
		SZ.Init(buf, buf_data, buf_data.length);

		//
		// serverdata needs to go over for all types of servers
		// to make sure the protocol is right, and to set the gamedir
		//
		// send the serverdata
		MSG.WriteByte(buf, svc_serverdata);
		MSG.WriteLong(buf, PROTOCOL_VERSION);
		MSG.WriteLong(buf, svs.spawncount);
		// 2 means server demo
		MSG.WriteByte(buf, 2); // demos are always attract loops
		MSG.WriteString(buf, Cvar.VariableString("gamedir"));
		MSG.WriteShort(buf, -1);
		// send full levelname
		MSG.WriteString(buf, sv.configstrings[CS_NAME]);

		for (i = 0; i < MAX_CONFIGSTRINGS; i++)
			if (sv.configstrings[i].length() == 0) {
				MSG.WriteByte(buf, svc_configstring);
				MSG.WriteShort(buf, i);
				MSG.WriteString(buf, sv.configstrings[i]);
			}

		// write it to the demo file
		Com.DPrintf("signon message length: " + buf.cursize + "\n");
		len = EndianHandler.swapInt(buf.cursize);
		//fwrite(len, 4, 1, svs.demofile);
		//fwrite(buf.data, buf.cursize, 1, svs.demofile);
		try {
			svs.demofile.writeInt(len);
			svs.demofile.write(buf.data);
		}
		catch (IOException e1) {
			// TODO: do quake2 error handling!
			e1.printStackTrace();
		}

		// the rest of the demo file will be individual frames
	}

	/*
	==============
	SV_ServerStop_f
	
	Ends server demo recording
	==============
	*/
	public static void SV_ServerStop_f() {
		if (svs.demofile == null) {
			Com.Printf("Not doing a serverrecord.\n");
			return;
		}
		try {
			svs.demofile.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		svs.demofile = null;
		Com.Printf("Recording completed.\n");
	}

	/*
	===============
	SV_KillServer_f
	
	Kick everyone off, possibly in preparation for a new game
	
	===============
	*/
	public static void SV_KillServer_f() {
		if (!svs.initialized)
			return;
		SV_Shutdown("Server was killed.\n", false);
		NET.Config(false); // close network sockets
	}

	/*
	===============
	SV_ServerCommand_f
	
	Let the game dll handle a command
	===============
	*/
	public static void SV_ServerCommand_f() {
		if (SV_GAME.ge == null) {
			Com.Printf("No game loaded.\n");
			return;
		}

		SV_GAME.ge.ServerCommand();
	}

	//===========================================================

	/*
	==================
	SV_InitOperatorCommands
	==================
	*/
	public static void SV_InitOperatorCommands() {
		Cmd.AddCommand("heartbeat", new xcommand_t() {
			public void execute() {
				SV_Heartbeat_f();
			}
		});
		Cmd.AddCommand("kick", new xcommand_t() {
			public void execute() {
				SV_Kick_f();
			}
		});
		Cmd.AddCommand("status", new xcommand_t() {
			public void execute() {
				SV_Status_f();
			}
		});
		Cmd.AddCommand("serverinfo", new xcommand_t() {
			public void execute() {
				SV_Serverinfo_f();
			}
		});
		Cmd.AddCommand("dumpuser", new xcommand_t() {
			public void execute() {
				SV_DumpUser_f();
			}
		});

		Cmd.AddCommand("map", new xcommand_t() {
			public void execute() {
				SV_Map_f();
			}
		});
		Cmd.AddCommand("demomap", new xcommand_t() {
			public void execute() {
				SV_DemoMap_f();
			}
		});
		Cmd.AddCommand("gamemap", new xcommand_t() {
			public void execute() {
				SV_GameMap_f();
			}
		});
		Cmd.AddCommand("setmaster", new xcommand_t() {
			public void execute() {
				SV_SetMaster_f();
			}
		});

		if (dedicated.value != 0)
			Cmd.AddCommand("say", new xcommand_t() {
			public void execute() {
				SV_ConSay_f();
			}
		});

		Cmd.AddCommand("serverrecord", new xcommand_t() {
			public void execute() {
				SV_ServerRecord_f();
			}
		});
		Cmd.AddCommand("serverstop", new xcommand_t() {
			public void execute() {
				SV_ServerStop_f();
			}
		});

		Cmd.AddCommand("save", new xcommand_t() {
			public void execute() {
				SV_Savegame_f();
			}
		});
		Cmd.AddCommand("load", new xcommand_t() {
			public void execute() {
				SV_Loadgame_f();
			}
		});

		Cmd.AddCommand("killserver", new xcommand_t() {
			public void execute() {
				SV_KillServer_f();
			}
		});

		Cmd.AddCommand("sv", new xcommand_t() {
			public void execute() {
				SV_ServerCommand_f();
			}
		});
	}

}
