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
// $Id: gclient_t.java,v 1.2 2003-11-29 13:28:29 rst Exp $

package jake2.game;

public class gclient_t {
	//	this structure is cleared on each PutClientInServer(),
	//	except for 'client->pers'

	// known to server
	player_state_t ps; // communicated by server to clients
	int ping;

	// private to game
	client_persistant_t pers;
	client_respawn_t resp;
	pmove_state_t old_pmove; // for detecting out-of-pmove changes

	boolean showscores; // set layout stat
	boolean showinventory; // set layout stat
	boolean showhelp;
	boolean showhelpicon;

	int ammo_index;

	int buttons;
	int oldbuttons;
	int latched_buttons;

	boolean weapon_thunk;

	gitem_t newweapon;

	// sum up damage over an entire frame, so
	// shotgun blasts give a single big kick
	int damage_armor; // damage absorbed by armor
	int damage_parmor; // damage absorbed by power armor
	int damage_blood; // damage taken out of health
	int damage_knockback; // impact damage
	float[] damage_from= { 0, 0, 0 }; // origin for vector calculation

	float killer_yaw; // when dead, look at killer

	int weaponstate;
	float[] kick_angles= { 0, 0, 0 }; // weapon kicks
	float[] kick_origin= { 0, 0, 0 };
	float v_dmg_roll, v_dmg_pitch, v_dmg_time; // damage kicks
	float fall_time, fall_value; // for view drop on fall
	float damage_alpha;
	float bonus_alpha;
	float[] damage_blend= { 0, 0, 0 };
	float[] v_angle= { 0, 0, 0 }; // aiming direction
	float bobtime; // so off-ground doesn't change it
	float[] oldviewangles= { 0, 0, 0 };
	float[] oldvelocity= { 0, 0, 0 };

	float next_drown_time;
	int old_waterlevel;
	int breather_sound;

	int machinegun_shots; // for weapon raising

	// animation vars
	int anim_end;
	int anim_priority;
	boolean anim_duck;
	boolean anim_run;

	// powerup timers
	float quad_framenum;
	float invincible_framenum;
	float breather_framenum;
	float enviro_framenum;

	boolean grenade_blew_up;
	float grenade_time;
	int silencer_shots;
	int weapon_sound;

	float pickup_msg_time;

	float flood_locktill; // locked from talking
	float flood_when[]= new float[10]; // when messages were said
	int flood_whenhead; // head pointer for when said

	float respawn_time; // can respawn when time > this

	edict_t chase_target; // player we are chasing
	boolean update_chase; // need to update chase info?
}
