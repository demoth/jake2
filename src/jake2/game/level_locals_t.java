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
// $Id: level_locals_t.java,v 1.2 2003-11-29 13:28:28 rst Exp $

package jake2.game;

public class level_locals_t {
	//
	//	this structure is cleared as each map is entered
	//	it is read/written to the level.sav file for savegames
	//
	int framenum;
	float time;

	String level_name= ""; // the descriptive name (Outer Base, etc)
	String mapname= ""; // the server name (base1, etc)
	String nextmap= ""; // go here when fraglimit is hit

	// intermission state
	float intermissiontime; // time the intermission was started
	char changemap;
	int exitintermission;
	float[] intermission_origin= { 0, 0, 0 };
	float[] intermission_angle= { 0, 0, 0 };

	edict_t sight_client; // changed once each frame for coop games

	edict_t sight_entity;
	int sight_entity_framenum;
	edict_t sound_entity;
	int sound_entity_framenum;
	edict_t sound2_entity;
	int sound2_entity_framenum;

	int pic_health;

	int total_secrets;
	int found_secrets;

	int total_goals;
	int found_goals;

	int total_monsters;
	int killed_monsters;

	edict_t current_entity; // entity running from G_RunFrame
	int body_que; // dead bodies

	int power_cubes; // ugly necessity for coop
}
