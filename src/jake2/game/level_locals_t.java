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

// Created on 20.11.2003 by RST
// $Id: level_locals_t.java,v 1.1 2004-07-07 19:59:25 hzi Exp $

package jake2.game;

public class level_locals_t {
	//
	//	this structure is cleared as each map is entered
	//	it is read/written to the level.sav file for savegames
	//
	public int framenum;
	public float time;

	public String level_name= ""; // the descriptive name (Outer Base, etc)
	public String mapname= ""; // the server name (base1, etc)
	public String nextmap= ""; // go here when fraglimit is hit

	// intermission state
	public float intermissiontime; // time the intermission was started
	public String changemap;
	public boolean exitintermission;
	public float[] intermission_origin= { 0, 0, 0 };
	public float[] intermission_angle= { 0, 0, 0 };

	public edict_t sight_client; // changed once each frame for coop games

	public edict_t sight_entity;
	public int sight_entity_framenum;
	public edict_t sound_entity;
	public int sound_entity_framenum;
	public edict_t sound2_entity;
	public int sound2_entity_framenum;

	public int pic_health;

	public int total_secrets;
	public int found_secrets;

	public int total_goals;
	public int found_goals;

	public int total_monsters;
	public int killed_monsters;

	public edict_t current_entity; // entity running from G_RunFrame
	public int body_que; // dead bodies

	public int power_cubes; // ugly necessity for coop
}
