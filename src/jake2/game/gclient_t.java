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
// $Id: gclient_t.java,v 1.4 2003-12-28 19:52:35 rst Exp $

package jake2.game;

public class gclient_t {
	//	this structure is cleared on each PutClientInServer(),
	//	except for 'client->pers'

	// known to server
	public player_state_t ps = new player_state_t(); // communicated by server to clients
	public int ping;

	// private to game
	public client_persistant_t pers;
	public client_respawn_t resp;
	public pmove_state_t old_pmove; // for detecting out-of-pmove changes

	public boolean showscores; // set layout stat
	public boolean showinventory; // set layout stat
	public boolean showhelp;
	public boolean showhelpicon;

	public int ammo_index;

	public int buttons;
	public int oldbuttons;
	public int latched_buttons;

	public boolean weapon_thunk;

	public gitem_t newweapon;

	// sum up damage over an entire frame, so
	// shotgun blasts give a single big kick
	public int damage_armor; // damage absorbed by armor
	public int damage_parmor; // damage absorbed by power armor
	public int damage_blood; // damage taken out of health
	public int damage_knockback; // impact damage
	public float[] damage_from= { 0, 0, 0 }; // origin for vector calculation

	public float killer_yaw; // when dead, look at killer

	public int weaponstate;
	public float[] kick_angles= { 0, 0, 0 }; // weapon kicks
	public float[] kick_origin= { 0, 0, 0 };
	public float v_dmg_roll, v_dmg_pitch, v_dmg_time; // damage kicks
	public float fall_time, fall_value; // for view drop on fall
	public float damage_alpha;
	public float bonus_alpha;
	public float[] damage_blend= { 0, 0, 0 };
	public float[] v_angle= { 0, 0, 0 }; // aiming direction
	public float bobtime; // so off-ground doesn't change it
	public float[] oldviewangles= { 0, 0, 0 };
	public float[] oldvelocity= { 0, 0, 0 };

	public float next_drown_time;
	public int old_waterlevel;
	public int breather_sound;

	public int machinegun_shots; // for weapon raising

	// animation vars
	public int anim_end;
	public int anim_priority;
	public boolean anim_duck;
	public boolean anim_run;

	// powerup timers
	public float quad_framenum;
	public float invincible_framenum;
	public float breather_framenum;
	public float enviro_framenum;

	public boolean grenade_blew_up;
	public float grenade_time;
	public int silencer_shots;
	public int weapon_sound;

	public float pickup_msg_time;

	public float flood_locktill; // locked from talking
	public float flood_when[]= new float[10]; // when messages were said
	public int flood_whenhead; // head pointer for when said

	public float respawn_time; // can respawn when time > this

	public edict_t chase_target; // player we are chasing
	public boolean update_chase; // need to update chase info?
	
	//TODO: 
	public int index;
	
	public void clear()
	{
	}
	
}
