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
// $Id: monsterinfo_t.java,v 1.1 2004-07-07 19:59:26 hzi Exp $

package jake2.game;

public class monsterinfo_t {

	public mmove_t currentmove;
	public int aiflags;
	public int nextframe;
	public float scale;

	public EntThinkAdapter stand;
	public EntThinkAdapter idle;
	public EntThinkAdapter search;
	public EntThinkAdapter walk;
	public EntThinkAdapter run;

	public EntDodgeAdapter dodge;

	public EntThinkAdapter attack;
	public EntThinkAdapter melee;

	public EntInteractAdapter sight;

	public EntThinkAdapter checkattack;

	public float pausetime;
	public float attack_finished;

	public float[] saved_goal= { 0, 0, 0 };
	public float search_time;
	public float trail_time;
	public float[] last_sighting= { 0, 0, 0 };
	public int attack_state;
	public int lefty;
	public float idle_time;
	public int linkcount;

	public int power_armor_type;
	public int power_armor_power;

}
