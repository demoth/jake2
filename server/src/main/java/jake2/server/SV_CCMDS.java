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
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.filesystem.QuakeFile;
import jake2.qcommon.sys.Sys;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.List;

public class SV_CCMDS {
	/*
	===============================================================================
	
	OPERATOR CONSOLE ONLY COMMANDS
	
	These commands can only be entered from stdin or by a remote operator datagram
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

		String name = FS.getWriteDir() + "/save/" + savename + "/server.ssv";
		remove(name);

		name = FS.getWriteDir() + "/save/" + savename + "/game.ssv";
		remove(name);

		name = FS.getWriteDir() + "/save/" + savename + "/*.sav";

		File f = Sys.FindFirst(name, 0, 0);
		while (f != null) {
			f.delete();
			f = Sys.FindNext();
		}
		Sys.FindClose();

		name = FS.getWriteDir() + "/save/" + savename + "/*.sv2";

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
	static void SV_CopySaveGame(String src, String dst) {

		Com.DPrintf("SV_CopySaveGame(" + src + "," + dst + ")\n");

		SV_WipeSavegame(dst);

		// copy the savegame over
		String name = FS.getWriteDir() + "/save/" + src + "/server.ssv";
		String name2 = FS.getWriteDir() + "/save/" + dst + "/server.ssv";
		FS.CreatePath(name2);
		CopyFile(name, name2);

		name = FS.getWriteDir() + "/save/" + src + "/game.ssv";
		name2 = FS.getWriteDir() + "/save/" + dst + "/game.ssv";
		CopyFile(name, name2);

		String name1 = FS.getWriteDir() + "/save/" + src + "/";
		name = FS.getWriteDir() + "/save/" + src + "/*.sav";

		File found = Sys.FindFirst(name, 0, 0);

		while (found != null) {
			name = name1 + found.getName();
			name2 = FS.getWriteDir() + "/save/" + dst + "/" + found.getName();

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
	static void SV_WriteLevelFile(GameImportsImpl gameImports) {

		Com.DPrintf("SV_WriteLevelFile()\n");

		String name = FS.getWriteDir() + "/save/current/" + SV_INIT.gameImports.sv.name + ".sv2";

		try {
			QuakeFile f = new QuakeFile(name, "rw");

			for (int i = 0; i < Defines.MAX_CONFIGSTRINGS; i++)
				f.writeString(gameImports.sv.configstrings[i]);

			CM.CM_WritePortalState(f);
			f.close();
		}
		catch (Exception e) {
			Com.Printf("Failed to open " + name + "\n");
			e.printStackTrace();
		}

		name = FS.getWriteDir() + "/save/current/" + gameImports.sv.name + ".sav";
		gameImports.gameExports.WriteLevel(name);
	}
	/*
	==============
	SV_ReadLevelFile
	
	==============
	*/
	static void SV_ReadLevelFile(String saveName, GameImportsImpl gameImports) {

		Com.DPrintf("SV_ReadLevelFile()\n");

		String name = FS.getWriteDir() + "/save/current/" + saveName + ".sv2";
		try {
			QuakeFile f = new QuakeFile(name, "r");

			for (int n = 0; n < Defines.MAX_CONFIGSTRINGS; n++)
				gameImports.sv.configstrings[n] = f.readString();

			CM.CM_ReadPortalState(f);

			f.close();
		}
		catch (IOException e1) {
			Com.Printf("Failed to open " + name + "\n");
			e1.printStackTrace();
		}

		name = FS.getWriteDir() + "/save/current/" + saveName + ".sav";
		gameImports.gameExports.ReadLevel(name);
	}

	/*
	 * SV_WriteServerFile.
	 * Save contains 2 steps: server state information (server.ssv) and game state information (game.ssv).
	 * Server state contains:
	 * 		comment (date)
	 * 		mapcommand
	 * 		latched cvars
	 *
	 * Game state saving is delegated to the game module
	 */
	static void SV_WriteServerFile(boolean autosave, GameImportsImpl gameImports) {

		Com.DPrintf("SV_WriteServerFile(autosave:" + autosave + ")\n");

		final String saveFile = FS.getWriteDir() + "/save/current/server.ssv";
		try {
			QuakeFile f = new QuakeFile(saveFile, "rw");

			final String comment;
			if (autosave) {
				comment = "Autosave in " + gameImports.sv.configstrings[Defines.CS_NAME];
			} else {
				comment = new Date().toString() + " " + gameImports.sv.configstrings[Defines.CS_NAME];
			}

			f.writeString(comment);
			f.writeString(gameImports.svs.mapcmd);

			// write all CVAR_LATCH cvars
			// these will be things like coop, skill, deathmatch, etc
			Cvar.eachCvarByFlags(Defines.CVAR_LATCH, var -> {
					try {
						f.writeString(var.name);
						f.writeString(var.string);
					} catch (IOException e) {
						Com.Printf("Could not write cvar(" + var + " to " + saveFile + ", " + e.getMessage());
					}
			});

			// rst: for termination.
			f.writeString(null);
			f.close();
		} catch (Exception e) {
			Com.Printf("Couldn't write " + saveFile + ", " + e.getMessage() + "\n");
		}

		// write game state
		gameImports.gameExports.WriteGame(FS.getWriteDir() + "/save/current/game.ssv", autosave);
	}

	/*
	 * 	SV_ReadServerFile
	 */
	private static void SV_ReadServerFile(GameImportsImpl gameImports) {
		String filename = "";
		try {

			Com.DPrintf("SV_ReadServerFile()\n");

			filename = FS.getWriteDir() + "/save/current/server.ssv";

			QuakeFile f = new QuakeFile(filename, "r");

			// read the comment field
			Com.DPrintf("SV_ReadServerFile: Loading save: " + f.readString() + "\n");


			// read the mapcmd
			String mapcmd = f.readString();

			// read all CVAR_LATCH cvars
			// these will be things like coop, skill, deathmatch, etc
			while (true) {
				String name = f.readString();
				if (name == null)
					break;
				String value = f.readString();

				Com.DPrintf("Set " + name + " = " + value + "\n");
				Cvar.ForceSet(name, value);
			}

			f.close();

			// start a new game fresh with new cvars
			SV_INIT.SV_InitGame();

			// fixme: SV_INIT.gameImports is changed after SV_INIT.SV_InitGame();
			gameImports.svs.mapcmd = mapcmd;

			// read game state
			filename = FS.getWriteDir() + "/save/current/game.ssv";
			gameImports.gameExports.readGameLocals(filename);
		} catch (Exception e) {
			Com.Printf("Couldn't read file " + filename + ", " + e.getMessage() + "\n");
		}
	}
	//=========================================================

	/*
	==================
	SV_DemoMap_f
	
	Puts the server in demo mode on a specific map/cinematic
	==================
	*/
	static void SV_DemoMap_f(List<String> args) {
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
	static void SV_GameMap_f(List<String> args) {

		if (args.size() != 2) {
			Com.Printf("USAGE: gamemap <map>\n");
			return;
		}

		String mapName = args.get(1);
		Com.DPrintf("SV_GameMap(" + mapName + ")\n");

		FS.CreatePath(FS.getWriteDir() + "/save/current/");

		// check for clearing the current savegame
		if (mapName.charAt(0) == '*') {
			// wipe all the *.sav files
			SV_WipeSavegame("current");
		}
		else { // save the map just exited
			// todo: init gameImports in a proper place
			if (SV_INIT.gameImports != null && SV_INIT.gameImports.sv.state == ServerStates.SS_GAME) {
				// clear all the client inuse flags before saving so that
				// when the level is re-entered, the clients will spawn
				// at spawn points instead of occupying body shells
				client_t cl;
				boolean[] savedInuse = new boolean[(int) SV_MAIN.maxclients.value];
				for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
					cl = SV_INIT.gameImports.svs.clients[i];
					savedInuse[i] = cl.edict.inuse;
					cl.edict.inuse = false;
				}

				SV_WriteLevelFile(SV_INIT.gameImports);

				// we must restore these for clients to transfer over correctly
				for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
					cl = SV_INIT.gameImports.svs.clients[i];
					cl.edict.inuse = savedInuse[i];

				}
			}
		}

		// start up the next map
		SV_INIT.SV_Map(false, mapName, false);

		// archive server state
		SV_INIT.gameImports.svs.mapcmd = mapName;

		// copy off the level to the autosave slot
		if (0 == Globals.dedicated.value) {
			SV_WriteServerFile(true, SV_INIT.gameImports);
			SV_CopySaveGame("current", "save0");
		}
	}

	/** Print the memory used by the java vm. */
	static void VM_Mem_f(List<String> args)
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
	static void SV_Map_f(List<String> args) {
		String mapName;
		//char expanded[MAX_QPATH];
		if (args.size() < 2) {
			Com.Printf("usage: map <map_name>\n");
			return;
		}

		// if not a pcx, demo, or cinematic, check to make sure the level exists
		mapName = args.get(1);
		if (!mapName.contains(".")) {
			String mapPath = "maps/" + mapName + ".bsp";
			if (FS.LoadFile(mapPath) == null) {
				Com.Printf("Can't find " + mapPath + "\n");
				return;
			}
		}

		if (SV_INIT.gameImports != null)
			SV_INIT.gameImports.sv.state = ServerStates.SS_DEAD; // don't save current level when changing

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
	static void SV_Loadgame_f(List<String> args) {

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
		String name = FS.getWriteDir() + "/save/" + saveGame + "/server.ssv";
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
		SV_ReadServerFile(SV_INIT.gameImports);

		// go to the map
		SV_INIT.SV_Map(false, SV_INIT.gameImports.svs.mapcmd, true);
	}
	/*
	==================
	SV_InitOperatorCommands
	==================
	*/
	static void SV_InitOperatorCommands(final GameImportsImpl gameImports) {

		// remove and add new versions of this commands


//		Cmd.AddCommand("spawnbot", new Command() {
//			public void execute() {
//				AdvancedBot.SP_Oak();
//			}
//		});
	}
}
