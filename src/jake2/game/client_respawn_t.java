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
// $Id: client_respawn_t.java,v 1.3 2004-07-08 20:24:29 hzi Exp $

package jake2.game;

import jake2.qcommon.Com;
import jake2.util.Lib;

import java.io.IOException;
import java.nio.ByteBuffer;

public class client_respawn_t
// client data that stays across deathmatch respawns
{
	client_persistant_t coop_respawn = new client_persistant_t(); // what to set client->pers to on a respawn
	int enterframe; // level.framenum the client entered the game
	int score; // frags, etc
	float cmd_angles[] = { 0, 0, 0 }; // angles sent over in the last command
	boolean spectator; // client is a spectator

	public void set(client_respawn_t from)
	{
		coop_respawn.set(from.coop_respawn);
		enterframe = from.enterframe;
		score = from.score;
		cmd_angles = Lib.clone(from.cmd_angles);
		spectator = from.spectator;
	}

	//ok
	public void clear()
	{
		coop_respawn = new client_persistant_t();
		enterframe = 0;
		score = 0;
		cmd_angles = new float[3];
		spectator = false;
	}

	public void load(ByteBuffer bb) throws IOException
	{
		coop_respawn.load(bb);
		enterframe = bb.getInt();
		score = bb.getInt();
		cmd_angles[0] = bb.getFloat();
		cmd_angles[1] = bb.getFloat();
		cmd_angles[2] = bb.getFloat();
		spectator = bb.getInt() != 0;
	}

	public void dump()
	{
		coop_respawn.dump();
		Com.Println("enterframe: " + enterframe);
		Com.Println("score: " + score);
		Lib.printv("cmd_angles", cmd_angles);
		Com.Println("spectator: " + spectator);
	}
}
