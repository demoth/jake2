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
// $Id: monsterinfo_t.java,v 1.2 2003-11-29 13:28:28 rst Exp $

package jake2.game;

public class monsterinfo_t {

	mmove_t currentmove;
	int aiflags;
	int nextframe;
	float scale;

	EntThinkAdapter stand;
	EntThinkAdapter idle;
	EntThinkAdapter search;
	EntThinkAdapter walk;
	EntThinkAdapter run;

	EntDodgeAdapter dodge;

	EntThinkAdapter attack;
	EntThinkAdapter melee;

	EntInteractAdapter sight;

	EntThinkAdapter checkattack;

	float pausetime;
	float attack_finished;

	float[] saved_goal= { 0, 0, 0 };
	float search_time;
	float trail_time;
	float[] last_sighting= { 0, 0, 0 };
	int attack_state;
	int lefty;
	float idle_time;
	int linkcount;

	int power_armor_type;
	int power_armor_power;

}
