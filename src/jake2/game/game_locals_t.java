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

// Created on 31.10.2003 by RST.
// $Id: game_locals_t.java,v 1.6 2004-02-04 20:33:35 rst Exp $

package jake2.game;

import jake2.Defines;
import jake2.qcommon.Com;
import jake2.util.Lib;

import java.io.IOException;
import java.nio.ByteBuffer;

public class game_locals_t extends Defines {
	//
	//	this structure is left intact through an entire game
	//	it should be initialized at dll load time, and read/written to
	//	the server.ssv file for savegames
	//

	public String helpmessage1="";
	public String helpmessage2="";
	public int helpchanged; // flash F1 icon if non 0, play sound
	// and increment only if 1, 2, or 3

	public gclient_t clients[] = new gclient_t[MAX_CLIENTS];

	// can't store spawnpoint in level, because
	// it would get overwritten by the savegame restore
	public String spawnpoint = ""; // needed for coop respawns

	// store latched cvars here that we want to get at often
	public int maxclients;
	public int maxentities;

	// cross level triggers
	public int serverflags;

	// items
	public int num_items;
	public boolean autosaved;

	public void load(ByteBuffer bb) throws IOException {
		String date = Lib.readString(bb, 16);

		helpmessage1 = Lib.readString(bb, 512);
		helpmessage2 = Lib.readString(bb, 512);

		helpchanged = bb.getInt();
		// gclient_t*
		bb.getInt();
		spawnpoint = Lib.readString(bb, 512);
		maxclients = bb.getInt();
		maxentities = bb.getInt();
		serverflags = bb.getInt();
		num_items = bb.getInt();
		autosaved = bb.getInt() != 0;

	}

	public void dump() {

		Com.Println("String helpmessage1: " + helpmessage1);
		Com.Println("String helpmessage2: " + helpmessage2);

		Com.Println("spawnpoit: " + spawnpoint);
		Com.Println("maxclients: " + maxclients);
		Com.Println("maxentities: " + maxentities);
		Com.Println("serverflags: " + serverflags);
		Com.Println("numitems: " + num_items);
		Com.Println("autosaved: " + autosaved);

		for (int i = 0; i < maxclients; i++)
			clients[i].dump();
	}
}
