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
// $Id: game_export_t.java,v 1.6 2004-01-18 10:39:34 rst Exp $

package jake2.game;


//
//functions exported by the game subsystem
//

public class game_export_t {

	public int apiversion;

	// the init function will only be called when a game starts,
	// not each time a level is loaded.  Persistant data for clients
	// and the server can be allocated in init
	public void Init() {
	}
	public void Shutdown() {
	}

	// each new level entered will cause a call to SpawnEntities
	public void SpawnEntities(String mapname, String entstring, String spawnpoint) {
	}

	// Read/Write Game is for storing persistant cross level information
	// about the world state and the clients.
	// WriteGame is called every time a level is exited.
	// ReadGame is called on a loadgame.
	public void WriteGame(String filename, boolean autosave) {
	}
	public void ReadGame(String filename) {
	}

	// ReadLevel is called after the default map information has been
	// loaded with SpawnEntities
	public void WriteLevel(String filename) {
	}

	public void ReadLevel(String filename) {
	}

	public boolean ClientConnect(edict_t ent, String userinfo) {
		return false;
	}
	public void ClientBegin(edict_t ent) {
	}
	public void ClientUserinfoChanged(edict_t ent, String userinfo) {
	}
	public void ClientDisconnect(edict_t ent) {
	}
	public void ClientCommand(edict_t ent) {
	}
	public void ClientThink(edict_t ent, usercmd_t cmd) {
	}

	public void RunFrame() {
	}

	// ServerCommand will be called when an "sv <command>" command is issued on the
	// server console.
	// The game can issue gi.argc() / gi.argv() commands to get the rest
	// of the parameters
	public void ServerCommand() {
	}

	//
	// global variables shared between game and server
	//

	// The edict array is allocated in the game dll so it
	// can vary in size from one game to another.
	// 
	// The size will be fixed when ge->Init() is called
	public edict_t edicts[];
	public int edict_size;
	public int num_edicts; // current number, <= max_edicts
	public int max_edicts;

}
