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
// $Id: client_respawn_t.java,v 1.3 2003-12-28 19:52:35 rst Exp $

package jake2.game;


public class client_respawn_t
// client data that stays across deathmatch respawns
{
	client_persistant_t coop_respawn; // what to set client->pers to on a respawn
	int enterframe; // level.framenum the client entered the game
	int score; // frags, etc
	float cmd_angles[] = { 0, 0, 0 }; // angles sent over in the last command

	boolean spectator; // client is a spectator
	
	public void clear()
	{
		coop_respawn = null;
		enterframe =0;
		score =0;
		cmd_angles  = new float[3];
		spectator = false;
	}
}
