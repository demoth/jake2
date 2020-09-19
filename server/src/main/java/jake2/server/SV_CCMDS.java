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

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.ServerStates;
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
	static void SV_WipeSavegame(String savename) {

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

			gameImports.cm.CM_ReadPortalState(f);

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
	 * 	SV_ReadServerFile
	 */
	static String SV_ReadServerFile(GameImportsImpl gameImports) {
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
				Cvar.getInstance().ForceSet(name, value);
			}

			f.close();

			// read game state
			filename = FS.getWriteDir() + "/save/current/game.ssv";
			gameImports.gameExports.readGameLocals(filename);
			return mapcmd;
		} catch (Exception e) {
			Com.Printf("Couldn't read file " + filename + ", " + e.getMessage() + "\n");
		}
		return null;
	}
	//=========================================================

	/** Print the memory used by the java vm. */
	static void VM_Mem_f(List<String> args)
	{
		Com.Printf("vm memory:" + 
				(Runtime.getRuntime().totalMemory() - 
						Runtime.getRuntime().freeMemory()) + "\n" );
	}
	

}
