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
// $Id: SV_CCMDS.java,v 1.17 2011-07-07 21:07:09 salomo Exp $

package jake2.server;

import jake2.qcommon.*;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.sys.Sys;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.QuakeFile;
import jake2.qcommon.util.Vargs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.List;

import static jake2.qcommon.Cmd.getArguments;
import static jake2.qcommon.Defines.ERR_DROP;
import static jake2.qcommon.Defines.PRINT_ALL;

public class SV_CCMDS {

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
	private static void SV_SetMaster_f(List<String> args) {
		int i, slot;

		// only dedicated servers send heartbeats
		if (Globals.dedicated.value == 0) {
			Com.Printf("Only dedicated servers use masters.\n");
			return;
		}

		// make sure the server is listed public
		Cvar.Set("public", "1");

		for (i = 1; i < Defines.MAX_MASTERS; i++)
			SV_MAIN.master_adr[i] = new netadr_t();

		slot = 1; // slot 0 will always contain the id master
		for (i = 1; i < args.size(); i++) {
			if (slot == Defines.MAX_MASTERS)
				break;

			if (!NET.StringToAdr(args.get(i), SV_MAIN.master_adr[i])) {
				Com.Printf("Bad address: " + args.get(i) + "\n");
				continue;
			}
			if (SV_MAIN.master_adr[slot].port == 0)
				SV_MAIN.master_adr[slot].port = Defines.PORT_MASTER;

			Com.Printf("Master server at " + NET.AdrToString(SV_MAIN.master_adr[slot]) + "\n");
			Com.Printf("Sending a ping.\n");

			Netchan.OutOfBandPrint(Defines.NS_SERVER, SV_MAIN.master_adr[slot], "ping");

			slot++;
		}

		SV_INIT.svs.last_heartbeat = -9999999;
	}
	/*
	==================
	SV_SetPlayer
	
	Sets sv_client and sv_player to the player with idnum Cmd.Argv(1)
	==================
	*/
	private static boolean SV_SetPlayer(List<String> args) {

		if (args.size() < 2)
			return false;

		String idOrName = args.get(1);

		// numeric values are just slot numbers
		if (idOrName.charAt(0) >= '0' && idOrName.charAt(0) <= '9') {
			int id = Lib.atoi(idOrName);
			if (id < 0 || id >= SV_MAIN.maxclients.value) {
				Com.Printf("Bad client slot: " + id + "\n");
				return false;
			}

			SV_MAIN.sv_client = SV_INIT.svs.clients[id];
			SV_USER.sv_player = SV_MAIN.sv_client.edict;
			if (ClientStates.CS_FREE == SV_MAIN.sv_client.state) {
				Com.Printf("Client " + id + " is not active\n");
				return false;
			}
			return true;
		}

		// check for a name match
		for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
			client_t cl = SV_INIT.svs.clients[i];
			if (ClientStates.CS_FREE == cl.state)
				continue;
            if (idOrName.equals(cl.name)) {
				SV_MAIN.sv_client = cl;
				SV_USER.sv_player = SV_MAIN.sv_client.edict;
				return true;
			}
		}

		Com.Printf("Userid " + idOrName + " is not on the server\n");
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
	
	/** Delete save files save/(number)/.  */
	private static void SV_WipeSavegame(String savename) {

	    Com.DPrintf("SV_WipeSaveGame(" + savename + ")\n");

		String name = FS.Gamedir() + "/save/" + savename + "/server.ssv";
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
	private static void CopyFile(String src, String dst) {
		RandomAccessFile f1, f2;
		int l = -1;
		byte buffer[] = new byte[65536];

		//Com.DPrintf("CopyFile (" + src + ", " + dst + ")\n");
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
	private static void SV_CopySaveGame(String src, String dst) {

		Com.DPrintf("SV_CopySaveGame(" + src + "," + dst + ")\n");

		SV_WipeSavegame(dst);

		// copy the savegame over
		String name = FS.Gamedir() + "/save/" + src + "/server.ssv";
		String name2 = FS.Gamedir() + "/save/" + dst + "/server.ssv";
		FS.CreatePath(name2);
		CopyFile(name, name2);

		name = FS.Gamedir() + "/save/" + src + "/game.ssv";
		name2 = FS.Gamedir() + "/save/" + dst + "/game.ssv";
		CopyFile(name, name2);

		String name1 = FS.Gamedir() + "/save/" + src + "/";
		name = FS.Gamedir() + "/save/" + src + "/*.sav";

		File found = Sys.FindFirst(name, 0, 0);

		while (found != null) {
			name = name1 + found.getName();
			name2 = FS.Gamedir() + "/save/" + dst + "/" + found.getName();

			CopyFile(name, name2);

			// change sav to sv2
			name = name.substring(0, name.length() - 3) + "sv2";
			name2 = name2.substring(0, name2.length() - 3) + "sv2";

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
	private static void SV_WriteLevelFile() {

		String name;
		QuakeFile f;

		Com.DPrintf("SV_WriteLevelFile()\n");

		name = FS.Gamedir() + "/save/current/" + SV_INIT.sv.name + ".sv2";

		try {
			f = new QuakeFile(name, "rw");

			for (int i = 0; i < Defines.MAX_CONFIGSTRINGS; i++)
				f.writeString(SV_INIT.sv.configstrings[i]);

			CM.CM_WritePortalState(f);
			f.close();
		}
		catch (Exception e) {
			Com.Printf("Failed to open " + name + "\n");
			e.printStackTrace();
		}

		name = FS.Gamedir() + "/save/current/" + SV_INIT.sv.name + ".sav";
		SV_GAME.gameExports.WriteLevel(name);
	}
	/*
	==============
	SV_ReadLevelFile
	
	==============
	*/
	static void SV_ReadLevelFile() {
		//char name[MAX_OSPATH];
		String name;
		QuakeFile f;

		Com.DPrintf("SV_ReadLevelFile()\n");

		name = FS.Gamedir() + "/save/current/" + SV_INIT.sv.name + ".sv2";
		try {
			f = new QuakeFile(name, "r");

			for (int n = 0; n < Defines.MAX_CONFIGSTRINGS; n++)
				SV_INIT.sv.configstrings[n] = f.readString();

			CM.CM_ReadPortalState(f);

			f.close();
		}
		catch (IOException e1) {
			Com.Printf("Failed to open " + name + "\n");
			e1.printStackTrace();
		}

		name = FS.Gamedir() + "/save/current/" + SV_INIT.sv.name + ".sav";
		SV_GAME.gameExports.ReadLevel(name);
	}
	/*
	==============
	SV_WriteServerFile
	
	==============
	*/
	private static void SV_WriteServerFile(boolean autosave) {
		QuakeFile f;
		String comment;

		Com.DPrintf("SV_WriteServerFile(" + (autosave ? "true" : "false") + ")\n");

		try {
			f = new QuakeFile(FS.Gamedir() + "/save/current/server.ssv", "rw");

			if (!autosave) {
				Calendar c = Calendar.getInstance();
				comment =
					Com.sprintf(
						"%2i:%2i %2i/%2i  ",
						new Vargs().add(c.get(Calendar.HOUR_OF_DAY)).add(c.get(Calendar.MINUTE)).add(
							c.get(Calendar.MONTH) + 1).add(
							c.get(Calendar.DAY_OF_MONTH)));
				comment += SV_INIT.sv.configstrings[Defines.CS_NAME];
			}
			else {
				// autosaved
				comment = "ENTERING " + SV_INIT.sv.configstrings[Defines.CS_NAME];
			}

			f.writeString(comment);
			f.writeString(SV_INIT.svs.mapcmd);

			// write the mapcmd

			// write all CVAR_LATCH cvars
			// these will be things like coop, skill, deathmatch, etc
			Cvar.eachCvarByFlags(Defines.CVAR_LATCH, var -> {
				if (var.name.length() >= Defines.MAX_OSPATH - 1 || var.string.length() >= 128 - 1) {
					Com.Printf("Cvar too long: " + var.name + " = " + var.string + "\n");

				} else
					try {
						f.writeString(var.name);
						f.writeString(var.string);
					} catch (IOException e2) {
						Com.Printf("Could not write cvar(" + var + " to " + (FS.Gamedir() + "/save/current/server.ssv"));
					}


			});

			// rst: for termination.
			f.writeString(null);
			f.close();
		}
		catch (Exception e) {
			Com.Printf("Couldn't write " + (FS.Gamedir() + "/save/current/server.ssv") + "\n");
		}

		// write game state
		SV_GAME.gameExports.WriteGame(FS.Gamedir() + "/save/current/game.ssv", autosave);
	}
	/*
	==============
	SV_ReadServerFile
	
	==============
	*/
	private static void SV_ReadServerFile() {
		String filename="", name = "", string, mapcmd;
		try {
			QuakeFile f;

			mapcmd = "";

			Com.DPrintf("SV_ReadServerFile()\n");

			filename = FS.Gamedir() + "/save/current/server.ssv";

			f = new QuakeFile(filename, "r");

			// read the comment field but ignore
			f.readString();

			// read the mapcmd
			mapcmd = f.readString();

			// read all CVAR_LATCH cvars
			// these will be things like coop, skill, deathmatch, etc
			while (true) {
				name = f.readString();
				if (name == null)
					break;
				string = f.readString();

				Com.DPrintf("Set " + name + " = " + string + "\n");
				Cvar.ForceSet(name, string);
			}

			f.close();

			// start a new game fresh with new cvars
			SV_INIT.SV_InitGame();

			SV_INIT.svs.mapcmd = mapcmd;

			// read game state
			filename = FS.Gamedir() + "/save/current/game.ssv";
			SV_GAME.gameExports.ReadGame(filename);
		}
		catch (Exception e) {
			Com.Printf("Couldn't read file " + filename + "\n");
			e.printStackTrace();
		}
	}
	//=========================================================

	/*
	==================
	SV_DemoMap_f
	
	Puts the server in demo mode on a specific map/cinematic
	==================
	*/
	private static void SV_DemoMap_f(List<String> args) {
		SV_INIT.SV_Map(true, args.size() >= 2 ? args.get(1) : "", false);
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
	private static void SV_GameMap_f(List<String> args) {

		if (args.size() != 2) {
			Com.Printf("USAGE: gamemap <map>\n");
			return;
		}

		String mapName = args.get(1);
		Com.DPrintf("SV_GameMap(" + mapName + ")\n");

		FS.CreatePath(FS.Gamedir() + "/save/current/");

		// check for clearing the current savegame
		if (mapName.charAt(0) == '*') {
			// wipe all the *.sav files
			SV_WipeSavegame("current");
		}
		else { // save the map just exited
			if (SV_INIT.sv.state == ServerStates.SS_GAME) {
				// clear all the client inuse flags before saving so that
				// when the level is re-entered, the clients will spawn
				// at spawn points instead of occupying body shells
				client_t cl;
				boolean[] savedInuse = new boolean[(int) SV_MAIN.maxclients.value];
				for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
					cl = SV_INIT.svs.clients[i];
					savedInuse[i] = cl.edict.inuse;
					cl.edict.inuse = false;
				}

				SV_WriteLevelFile();

				// we must restore these for clients to transfer over correctly
				for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
					cl = SV_INIT.svs.clients[i];
					cl.edict.inuse = savedInuse[i];

				}
			}
		}

		// start up the next map
		SV_INIT.SV_Map(false, mapName, false);

		// archive server state
		SV_INIT.svs.mapcmd = mapName;

		// copy off the level to the autosave slot
		if (0 == Globals.dedicated.value) {
			SV_WriteServerFile(true);
			SV_CopySaveGame("current", "save0");
		}
	}
	

	/** Print the memory used by the java vm. */
	private static void VM_Mem_f()
	{
		Com.Printf("vm memory:" + 
				(Runtime.getRuntime().totalMemory() - 
						Runtime.getRuntime().freeMemory()) + "\n" );
	}
	
	/*
	==================
	SV_Map_f
	
	Goes directly to a given map without any savegame archiving.
	For development work
	==================
	*/
	private static void SV_Map_f(List<String> args) {
		String mapName;
		//char expanded[MAX_QPATH];

		// if not a pcx, demo, or cinematic, check to make sure the level exists
		mapName = args.get(1);
		if (!mapName.contains(".")) {
			String mapPath = "maps/" + mapName + ".bsp";
			if (FS.LoadFile(mapPath) == null) {
				Com.Printf("Can't find " + mapPath + "\n");
				return;
			}
		}

		SV_INIT.sv.state = ServerStates.SS_DEAD; // don't save current level when changing

		SV_WipeSavegame("current");
		SV_GameMap_f(args);
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
	private static void SV_Loadgame_f(List<String> args) {

		if (args.size() != 2) {
			Com.Printf("USAGE: load <directory>\n");
			return;
		}

		Com.Printf("Loading game...\n");

		String saveGame = args.get(1);
		if (saveGame.contains("..") || saveGame.contains("/") || saveGame.contains("\\")) {
			Com.Printf("Bad save name.\n");
			return;
		}

		// make sure the server.ssv file exists
		String name = FS.Gamedir() + "/save/" + saveGame + "/server.ssv";
		RandomAccessFile f;
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

		SV_CopySaveGame(saveGame, "current");
		SV_ReadServerFile();

		// go to the map
		SV_INIT.sv.state = ServerStates.SS_DEAD; // don't save current level when changing
		SV_INIT.SV_Map(false, SV_INIT.svs.mapcmd, true);
	}
	/*
	==============
	SV_Savegame_f
	
	==============
	*/
	private static void SV_Savegame_f(List<String> args) {

		if (SV_INIT.sv.state != ServerStates.SS_GAME) {
			Com.Printf("You must be in a game to save.\n");
			return;
		}

		if (args.size() != 2) {
			Com.Printf("USAGE: save <directory>\n");
			return;
		}

		if (Cvar.VariableValue("deathmatch") != 0) {
			Com.Printf("Can't savegame in a deathmatch\n");
			return;
		}

		String saveGame = args.get(1);
		if ("current".equals(saveGame)) {
			Com.Printf("Can't save to 'current'\n");
			return;
		}

		if (SV_MAIN.maxclients.value == 1 && SV_INIT.svs.clients[0].edict.client.getPlayerState().stats[Defines.STAT_HEALTH] <= 0) {
			Com.Printf("\nCan't savegame while dead!\n");
			return;
		}

		if (saveGame.contains("..") || saveGame.contains("/") || saveGame.contains("\\")) {
			Com.Printf("Bad save name.\n");
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
		SV_CopySaveGame("current", saveGame);
		Com.Printf("Done.\n");
	}
	//===============================================================
	/*
	==================
	SV_Kick_f
	
	Kick a user off of the server
	==================
	*/
	private static void SV_Kick_f(List<String> args) {
		if (!SV_INIT.svs.initialized) {
			Com.Printf("No server running.\n");
			return;
		}

		if (args.size() != 2) {
			Com.Printf("Usage: kick <userid>\n");
			return;
		}

		if (!SV_SetPlayer(args))
			return;

		SV_SEND.SV_BroadcastPrintf(Defines.PRINT_HIGH, SV_MAIN.sv_client.name + " was kicked\n");
		// print directly, because the dropped client won't get the
		// SV_BroadcastPrintf message
		SV_SEND.SV_ClientPrintf(SV_MAIN.sv_client, Defines.PRINT_HIGH, "You were kicked from the game\n");
		SV_MAIN.SV_DropClient(SV_MAIN.sv_client);
		SV_MAIN.sv_client.lastmessage = SV_INIT.svs.realtime; // min case there is a funny zombie
	}
	/*
	================
	SV_Status_f
	================
	*/
	private static void SV_Status_f() {
		int i, j, l;
		client_t cl;
		String s;
		int ping;
		if (SV_INIT.svs.clients == null) {
			Com.Printf("No server running.\n");
			return;
		}
		Com.Printf("map              : " + SV_INIT.sv.name + "\n");

		Com.Printf("num score ping name            lastmsg address               qport \n");
		Com.Printf("--- ----- ---- --------------- ------- --------------------- ------\n");
		for (i = 0; i < SV_MAIN.maxclients.value; i++) {
			cl = SV_INIT.svs.clients[i];
			if (ClientStates.CS_FREE == cl.state)
				continue;

			Com.Printf("%3i ", new Vargs().add(i));
			Com.Printf("%5i ", new Vargs().add(cl.edict.client.getPlayerState().stats[Defines.STAT_FRAGS]));

			if (cl.state == ClientStates.CS_CONNECTED)
				Com.Printf("CNCT ");
			else if (cl.state == ClientStates.CS_ZOMBIE)
				Com.Printf("ZMBI ");
			else {
				ping = cl.ping < 9999 ? cl.ping : 9999;
				Com.Printf("%4i ", new Vargs().add(ping));
			}

			Com.Printf("%s", new Vargs().add(cl.name));
			l = 16 - cl.name.length();
			for (j = 0; j < l; j++)
				Com.Printf(" ");

			Com.Printf("%7i ", new Vargs().add(SV_INIT.svs.realtime - cl.lastmessage));

			s = NET.AdrToString(cl.netchan.remote_address);
			Com.Printf(s);
			l = 22 - s.length();
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
	private static void SV_ConSay_f(List<String> args) {
		client_t client;
		int j;
		String p;
		String text; // char[1024];

		if (args.size() < 2)
			return;

		text = "console: ";
		p = getArguments(args);

		if (p.charAt(0) == '"') {
			p = p.substring(1, p.length() - 1);
		}

		text += p;

		for (j = 0; j < SV_MAIN.maxclients.value; j++) {
			client = SV_INIT.svs.clients[j];
			if (client.state != ClientStates.CS_SPAWNED)
				continue;
			SV_SEND.SV_ClientPrintf(client, Defines.PRINT_CHAT, text + "\n");
		}
	}
	/*
	==================
	SV_Heartbeat_f
	==================
	*/
	private static void SV_Heartbeat_f() {
		SV_INIT.svs.last_heartbeat = -9999999;
	}
	/*
	===========
	SV_Serverinfo_f
	
	  Examine or change the serverinfo string
	===========
	*/
	private static void SV_Serverinfo_f() {
		Com.Printf("Server info settings:\n");
		Info.Print(Cvar.Serverinfo());
	}
	/*
	===========
	SV_DumpUser_f
	
	Examine all a users info strings
	===========
	*/
	private static void SV_DumpUser_f(List<String> args) {
		if (args.size() != 2) {
			Com.Printf("Usage: info <userid>\n");
			return;
		}

		if (!SV_SetPlayer(args))
			return;

		Com.Printf("userinfo\n");
		Com.Printf("--------\n");
		Info.Print(SV_MAIN.sv_client.userinfo);

	}
	/*
	==============
	SV_ServerRecord_f
	
	Begins server demo recording.  Every entity and every message will be
	recorded, but no playerinfo will be stored.  Primarily for demo merging.
	==============
	*/
	private static void SV_ServerRecord_f(List<String> args) {
		//char	name[MAX_OSPATH];
		byte[] buf_data = new byte[32768];
		sizebuf_t buf = new sizebuf_t();
		int len;
		int i;

		if (args.size() != 2) {
			Com.Printf("serverrecord <demoname>\n");
			return;
		}

		if (SV_INIT.svs.demofile != null) {
			Com.Printf("Already recording.\n");
			return;
		}

		if (SV_INIT.sv.state != ServerStates.SS_GAME) {
			Com.Printf("You must be in a level to record.\n");
			return;
		}

		//
		// open the demo file
		//
		String name = FS.Gamedir() + "/demos/" + args.get(1) + ".dm2";

		Com.Printf("recording to " + name + ".\n");
		FS.CreatePath(name);
		try {
			SV_INIT.svs.demofile = new RandomAccessFile(name, "rw");
		}
		catch (Exception e) {
			Com.Printf("ERROR: couldn't open.\n");
			return;
		}

		// setup a buffer to catch all multicasts
		SZ.Init(SV_INIT.svs.demo_multicast, SV_INIT.svs.demo_multicast_buf, SV_INIT.svs.demo_multicast_buf.length);

		//
		// write a single giant fake message with all the startup info
		//
		SZ.Init(buf, buf_data, buf_data.length);

		//
		// serverdata needs to go over for all types of servers
		// to make sure the protocol is right, and to set the gamedir
		//
		// send the serverdata
		MSG.WriteByte(buf, Defines.svc_serverdata);
		MSG.WriteLong(buf, Defines.PROTOCOL_VERSION);
		MSG.WriteLong(buf, SV_INIT.svs.spawncount);
		// 2 means server demo
		MSG.WriteByte(buf, 2); // demos are always attract loops
		MSG.WriteString(buf, Cvar.VariableString("gamedir"));
		MSG.WriteShort(buf, -1);
		// send full levelname
		MSG.WriteString(buf, SV_INIT.sv.configstrings[Defines.CS_NAME]);

		for (i = 0; i < Defines.MAX_CONFIGSTRINGS; i++)
			if (SV_INIT.sv.configstrings[i].length() == 0) {
				MSG.WriteByte(buf, Defines.svc_configstring);
				MSG.WriteShort(buf, i);
				MSG.WriteString(buf, SV_INIT.sv.configstrings[i]);
			}

		// write it to the demo file
		Com.DPrintf("signon message length: " + buf.cursize + "\n");
		len = EndianHandler.swapInt(buf.cursize);
		//fwrite(len, 4, 1, svs.demofile);
		//fwrite(buf.data, buf.cursize, 1, svs.demofile);
		try {
			SV_INIT.svs.demofile.writeInt(len);
			SV_INIT.svs.demofile.write(buf.data, 0, buf.cursize);
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
	private static void SV_ServerStop_f() {
		if (SV_INIT.svs.demofile == null) {
			Com.Printf("Not doing a serverrecord.\n");
			return;
		}
		try {
			SV_INIT.svs.demofile.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		SV_INIT.svs.demofile = null;
		Com.Printf("Recording completed.\n");
	}
	/*
	===============
	SV_KillServer_f
	
	Kick everyone off, possibly in preparation for a new game
	
	===============
	*/
	private static void SV_KillServer_f() {
		if (!SV_INIT.svs.initialized)
			return;
		SV_MAIN.SV_Shutdown("Server was killed.\n", false);
		NET.Config(false); // close network sockets
	}
	//===========================================================

	/*
	==================
	SV_InitOperatorCommands
	==================
	*/
	static void SV_InitOperatorCommands() {
		Cmd.AddCommand("heartbeat", (List<String> args) -> SV_Heartbeat_f());
		Cmd.AddCommand("kick", SV_CCMDS::SV_Kick_f);
		Cmd.AddCommand("status", (List<String> args) -> SV_Status_f());
		Cmd.AddCommand("serverinfo", (List<String> args) -> SV_Serverinfo_f());
		Cmd.AddCommand("dumpuser", SV_CCMDS::SV_DumpUser_f);
		Cmd.AddCommand("map", SV_CCMDS::SV_Map_f);
		Cmd.AddCommand("maplist", (List<String> args) -> {
			byte[] bytes = FS.LoadFile("maps.lst");
			if (bytes == null) {
				Com.Error(ERR_DROP, "Could not read maps.lst");
				return;
			}
			for (String line : new String(bytes).split("\n")){
				Com.Printf(PRINT_ALL, line.trim() + "\n");
			}
		});

		Cmd.AddCommand("demomap", SV_CCMDS::SV_DemoMap_f);
		Cmd.AddCommand("gamemap", SV_CCMDS::SV_GameMap_f);
		Cmd.AddCommand("setmaster", SV_CCMDS::SV_SetMaster_f);

		if (Globals.dedicated.value != 0)
			Cmd.AddCommand("say", SV_CCMDS::SV_ConSay_f);

		Cmd.AddCommand("serverrecord", SV_CCMDS::SV_ServerRecord_f);
		Cmd.AddCommand("serverstop", (List<String> args) -> SV_ServerStop_f());
		Cmd.AddCommand("save", SV_CCMDS::SV_Savegame_f);
		Cmd.AddCommand("load", SV_CCMDS::SV_Loadgame_f);
		Cmd.AddCommand("killserver", (List<String> args) -> SV_KillServer_f());
		Cmd.AddCommand("sv", args -> {
			if (SV_GAME.gameExports != null)
				SV_GAME.gameExports.ServerCommand(args);
		});
		Cmd.AddCommand("jvm_memory", (List<String> args) -> VM_Mem_f());

//		Cmd.AddCommand("spawnbot", new Command() {
//			public void execute() {
//				AdvancedBot.SP_Oak();
//			}
//		});
	}
}
