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
// $Id: gclient_t.java,v 1.8 2004-02-29 00:51:05 rst Exp $

package jake2.game;

import jake2.qcommon.Com;
import jake2.util.Lib;

import java.awt.event.ItemListener;
import java.io.IOException;
import java.nio.ByteBuffer;

public class gclient_t {

	public gclient_t(int index) {
		this.index = index;
	}
	//	this structure is cleared on each PutClientInServer(),
	//	except for 'client->pers'

	// known to server
	public player_state_t ps = new player_state_t(); // communicated by server to clients
	public int ping;

	// private to game
	public client_persistant_t pers= new client_persistant_t();
	public client_respawn_t resp= new client_respawn_t();
	public pmove_state_t old_pmove= new pmove_state_t(); // for detecting out-of-pmove changes

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
	public float[] damage_from = { 0, 0, 0 }; // origin for vector calculation

	public float killer_yaw; // when dead, look at killer

	public int weaponstate;
	public float[] kick_angles = { 0, 0, 0 }; // weapon kicks
	public float[] kick_origin = { 0, 0, 0 };
	public float v_dmg_roll, v_dmg_pitch, v_dmg_time; // damage kicks
	public float fall_time, fall_value; // for view drop on fall
	public float damage_alpha;
	public float bonus_alpha;
	public float[] damage_blend = { 0, 0, 0 };
	public float[] v_angle = { 0, 0, 0 }; // aiming direction
	public float bobtime; // so off-ground doesn't change it
	public float[] oldviewangles = { 0, 0, 0 };
	public float[] oldvelocity = { 0, 0, 0 };

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
	public float flood_when[] = new float[10]; // when messages were said
	public int flood_whenhead; // head pointer for when said

	public float respawn_time; // can respawn when time > this

	public edict_t chase_target; // player we are chasing
	public boolean update_chase; // need to update chase info?

	public int index;

	public void clear() {
		player_state_t ps = new player_state_t();
		pers= new client_persistant_t();
		resp= new client_respawn_t();
		old_pmove= new pmove_state_t();
	}

	public void load(ByteBuffer bb) throws IOException{

		ps.load(bb);

		ping = bb.getInt();

		pers.load(bb);
		resp.load(bb);

		old_pmove.load(bb);

		showscores=bb.getInt()!=0;
		showinventory=bb.getInt()!=0;
		showhelp=bb.getInt()!=0;
		showhelpicon=bb.getInt()!=0;
		ammo_index=bb.getInt();

		buttons=bb.getInt();
		oldbuttons=bb.getInt();
		latched_buttons=bb.getInt();

		
		//weapon_thunk=bb.getInt()!=0;
		bb.getInt();
		//newweapon=GameTarget.itemlist[bb.getInt()];
		bb.getInt();

		damage_armor=bb.getInt();
		damage_parmor=bb.getInt();
		damage_blood=bb.getInt();
		damage_knockback=bb.getInt();

		damage_from[0]=bb.getFloat();
		damage_from[1]=bb.getFloat();
		damage_from[2]=bb.getFloat();

		killer_yaw=bb.getFloat();

		weaponstate=bb.getInt();

		kick_angles[0]=bb.getFloat();
		kick_angles[1]=bb.getFloat();
		kick_angles[2]=bb.getFloat();

		kick_origin[0]=bb.getFloat();
		kick_origin[1]=bb.getFloat();
		kick_origin[2]=bb.getFloat();

		v_dmg_roll=bb.getFloat();
		v_dmg_pitch=bb.getFloat();
		v_dmg_time=bb.getFloat();
		fall_time=bb.getFloat();
		fall_value=bb.getFloat();
		damage_alpha=bb.getFloat();
		bonus_alpha=bb.getFloat();

		damage_blend[0]=bb.getFloat();
		damage_blend[1]=bb.getFloat();
		damage_blend[2]=bb.getFloat();

		v_angle[0]=bb.getFloat();
		v_angle[1]=bb.getFloat();
		v_angle[2]=bb.getFloat();

		bobtime=bb.getFloat();

		oldviewangles[0]=bb.getFloat();
		oldviewangles[1]=bb.getFloat();
		oldviewangles[2]=bb.getFloat();

		oldvelocity[0]=bb.getFloat();
		oldvelocity[1]=bb.getFloat();
		oldvelocity[2]=bb.getFloat();

		next_drown_time=bb.getFloat();

		old_waterlevel=bb.getInt();
		breather_sound=bb.getInt();
		machinegun_shots=bb.getInt();
		anim_end=bb.getInt();
		anim_priority=bb.getInt();
		anim_duck=bb.getInt()!=0;
		anim_run=bb.getInt()!=0;

		quad_framenum=bb.getFloat();
		invincible_framenum=bb.getFloat();
		breather_framenum=bb.getFloat();
		enviro_framenum=bb.getFloat();

		grenade_blew_up= bb.getInt()!=0;
		grenade_time=bb.getFloat();
		silencer_shots=bb.getInt();
		weapon_sound=bb.getInt();
		pickup_msg_time=bb.getFloat();
		flood_locktill=bb.getFloat();
		flood_when [0]=bb.getFloat();
		flood_when [1]=bb.getFloat();
		flood_when [2]=bb.getFloat();
		flood_when [3]=bb.getFloat();
		flood_when [4]=bb.getFloat();
		flood_when [5]=bb.getFloat();
		flood_when [6]=bb.getFloat();
		flood_when [7]=bb.getFloat();
		flood_when [8]=bb.getFloat();
		flood_when [9]=bb.getFloat();
		flood_whenhead=bb.getInt();
		respawn_time=bb.getFloat();
		chase_target=GameUtil.g_edicts[bb.getInt()];
		update_chase=bb.getInt()!=0;
	}
	public void dump() {
		
		Com.Println("ping: " + ping);

		pers.dump();
		resp.dump();

		old_pmove.dump();

		Com.Println("showscores: " + showscores);
		Com.Println("showinventury: " + showinventory);
		Com.Println("showhelp: " + showhelp);
		Com.Println("showhelpicon: " + showhelpicon);
		Com.Println("ammoindex: " + ammo_index);

		Com.Println("buttons: " + buttons);
		Com.Println("oldbuttons: " + oldbuttons);
		Com.Println("latchedbuttons: " + latched_buttons);

		Com.Println("weaponthunk: " + weapon_thunk);

		Com.Println("newweapon: " + newweapon);

		Com.Println("damage_armor: " + damage_armor);
		Com.Println("damage_parmor: " + damage_parmor);
		Com.Println("damage_blood: " + damage_blood);
		Com.Println("damage_knockback: " + damage_knockback);

		Lib.printv("damage_from", damage_from);

		Com.Println("killer_yaw: " + killer_yaw);

		Com.Println("weaponstate: " + weaponstate);

		Lib.printv("kick_angles", kick_angles);
		Lib.printv("kick_origin", kick_origin);

		Com.Println("v_dmg_roll: " + v_dmg_roll);
		Com.Println("v_dmg_pitch: " + v_dmg_pitch);
		Com.Println("v_dmg_time: " + v_dmg_time);

		Com.Println("fall_time: " + fall_time);
		Com.Println("fall_value: " + fall_value);
		Com.Println("damage_alpha: " + damage_alpha);
		Com.Println("bonus_alpha: " + bonus_alpha);

		Lib.printv("damage_blend", damage_blend);

		Lib.printv("v_angle", v_angle);

		Com.Println("bobtime: " + bobtime);

		Lib.printv("oldviewangles", oldviewangles);
		Lib.printv("oldvelocity", oldvelocity);

		Com.Println("next_downtime: " + next_drown_time);

		Com.Println("old_waterlevel: " + old_waterlevel);
		Com.Println("breathersound: " + breather_sound);
		Com.Println("machinegun_shots: " + machinegun_shots);
		Com.Println("anim_end: " + anim_end);
		Com.Println("anim_priority: " + anim_priority);
		Com.Println("anim_duck: " + anim_duck);
		Com.Println("anim_run: " + anim_run);

		Com.Println("quad_framenum: " + quad_framenum);
		Com.Println("invincible_framenum: " + invincible_framenum);
		Com.Println("breather_framenum: " + breather_framenum);
		Com.Println("enviro_framenum: " + enviro_framenum);

		Com.Println("grenade_blew_up: " + grenade_blew_up);
		Com.Println("grenade_time: " + grenade_time);
		Com.Println("silencer_shots: " + silencer_shots);
		Com.Println("weapon_sound: " + weapon_sound);
		Com.Println("pickup_msg_time: " + pickup_msg_time);
		Com.Println("flood_locktill: " + flood_locktill);

		Lib.printv("flood_when", flood_when);

		Com.Println("flood_whenhead: " + flood_whenhead);
		Com.Println("respawn_time: " + respawn_time);
		Com.Println("chase_target: " + chase_target);
		Com.Println("update_chase: " + update_chase);

	}

}
