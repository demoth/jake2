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
// $Id: game_export_t.java,v 1.12 2004-06-03 21:32:51 rst Exp $

package jake2.game;

import jake2.qcommon.Com;

//
//functions exported by the game subsystem
//

public class game_export_t
{

	public int apiversion;

	// the init function will only be called when a game starts,
	// not each time a level is loaded.  Persistant data for clients
	// and the server can be allocated in init
	public void Init()
	{
		Game.InitGame();
	}
	public void Shutdown()
	{
		Game.ShutdownGame();
	}

	// each new level entered will cause a call to SpawnEntities
	public void SpawnEntities(String mapname, String entstring, String spawnpoint)
	{
		Game.SpawnEntities(mapname, entstring, spawnpoint);
	}

	// Read/Write Game is for storing persistant cross level information
	// about the world state and the clients.
	// WriteGame is called every time a level is exited.
	// ReadGame is called on a loadgame.
	public void WriteGame(String filename, boolean autosave)
	{
		// TODO WriteGame not implemented.
		Com.Println("WriteGame not implemented.");
	}

	public void ReadGame(String filename)
	{
		Game.ReadGame(filename);
	}

	// ReadLevel is called after the default map information has been
	// loaded with SpawnEntities
	public void WriteLevel(String filename)
	{
		// TODO WriteLevel not implemented.
		Com.Println("WriteLevel not implemented.");
	}

	public void ReadLevel(String filename)
	{
		// TODO ReadLevel not implemented.
		Com.Println("ReadLevel not implemented.");
	}

	public boolean ClientConnect(edict_t ent, String userinfo)
	{
		return PlayerClient.ClientConnect(ent, userinfo);
	}
	public void ClientBegin(edict_t ent)
	{
		PlayerClient.ClientBegin(ent);
	}
	public void ClientUserinfoChanged(edict_t ent, String userinfo)
	{
		PlayerClient.ClientUserinfoChanged(ent, userinfo);
	}
	public void ClientDisconnect(edict_t ent)
	{
		PlayerClient.ClientDisconnect(ent);
	}
	public void ClientCommand(edict_t ent)
	{
		PlayerClient.ClientCommand(ent);
	}

	public void ClientThink(edict_t ent, usercmd_t cmd)
	{
		PlayerClient.ClientThink(ent, cmd);
	}

	public void RunFrame()
	{
		Game.G_RunFrame();
	}

	// ServerCommand will be called when an "sv <command>" command is issued on the
	// server console.
	// the game can issue gi.argc() / gi.argv() commands to get the rest
	// of the parameters
	public void ServerCommand()
	{
		Game.ServerCommand();
	}

	//
	// global variables shared between game and server
	//

	// the edict array is allocated in the game dll so it
	// can vary in size from one game to another.

	// the size will be fixed when ge.Init() is called
	public edict_t edicts[] = Game.g_edicts;
	public int num_edicts; // current number, <= max_edicts
	public int max_edicts;
}
