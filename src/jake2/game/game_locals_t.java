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
// $Id: game_locals_t.java,v 1.2 2003-11-29 13:28:29 rst Exp $

package jake2.game;

public class game_locals_t {
	//
	//	this structure is left intact through an entire game
	//	it should be initialized at dll load time, and read/written to
	//	the server.ssv file for savegames
	//

	String helpmessage1;
	String helpmessage2;
	int helpchanged; // flash F1 icon if non 0, play sound
	// and increment only if 1, 2, or 3

	gclient_t clients[]; // [maxclients]

	// can't store spawnpoint in level, because
	// it would get overwritten by the savegame restore
	char spawnpoint[]= new char[512]; // needed for coop respawns

	// store latched cvars here that we want to get at often
	int maxclients;
	int maxentities;

	// cross level triggers
	int serverflags;

	// items
	int num_items;

	boolean autosaved;
}
