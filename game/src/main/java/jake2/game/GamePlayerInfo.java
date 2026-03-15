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

package jake2.game;

import jake2.game.items.GameItem;
import jake2.qcommon.ServerPlayerInfo;
import jake2.qcommon.ServerEntity;
import jake2.qcommon.player_state_t;
import jake2.qcommon.pmove_state_t;

/**
 * Represents game side information about the player.
 * (former edict_t)
 */
public class GamePlayerInfo implements ServerPlayerInfo {

	public GamePlayerInfo(int index)
	{
		this.setIndex(index);
	}
	//	this structure is cleared on each PutClientInServer(),
	//	except for 'client->pers'

	// known to server
	private player_state_t ps = new player_state_t(); // communicated by server to clients
	private int ping;

	// private to game
	public client_persistant_t pers = new client_persistant_t();
	client_respawn_t resp = new client_respawn_t();
	pmove_state_t old_pmove = new pmove_state_t(); // for detecting out-of-pmove changes

	boolean showscores; // set layout stat
	boolean showinventory; // set layout stat
	boolean showhelp;
	private boolean showhelpicon;

	/**
	 * Item index for the selected weapon
	 */
	int ammo_index;

	int buttons;
	int oldbuttons;
	int latched_buttons;

	boolean weapon_thunk;

	public GameItem newweapon;

	// sum up damage over an entire frame, so
	// shotgun blasts give a single big kick
	int damage_armor; // damage absorbed by armor
	int damage_parmor; // damage absorbed by power armor
	int damage_blood; // damage taken out of health
	int damage_knockback; // impact damage
	float[] damage_from = { 0, 0, 0 }; // origin for vector calculation

	float killer_yaw; // when dead, look at killer

	WeaponStates weaponstate = WeaponStates.WEAPON_READY;
	float[] kick_angles = { 0, 0, 0 }; // weapon kicks
	float[] kick_origin = { 0, 0, 0 };
	float v_dmg_roll, v_dmg_pitch, v_dmg_time; // damage kicks
	float fall_time, fall_value; // for view drop on fall
	float damage_alpha;
	public float bonus_alpha;
	float[] damage_blend = { 0, 0, 0 };
	public float[] v_angle = { 0, 0, 0 }; // aiming direction
	float bobtime; // so off-ground doesn't change it
	float[] oldviewangles = { 0, 0, 0 };
	float[] oldvelocity = { 0, 0, 0 };

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
	public float quad_framenum;
	public float invincible_framenum;
	public float breather_framenum;
	public float enviro_framenum;

	boolean grenade_blew_up;
	float grenade_time;
	public int silencer_shots;
	int weapon_sound;

	public float pickup_msg_time;

	float flood_locktill; // locked from talking
	float flood_when[] = new float[10]; // when messages were said
	int flood_whenhead; // head pointer for when said

	float respawn_time; // can respawn when time > this

	public GameEntity chase_target; // player we are chasing
	boolean update_chase; // need to update chase info?

	private int index;

    public void InitClientResp(GameExportsImpl gameExports) {
        //memset(& client.resp, 0, sizeof(client.resp));
        this.resp.clear(); //  ok.
        this.resp.enterframe = gameExports.level.framenum;
        this.resp.coop_respawn.set(this.pers);
    }

    /** Clears the game client structure. */
	public void clear()
	{
		setPing(0);
	
		pers = new client_persistant_t();
		resp = new client_respawn_t();
		old_pmove = new pmove_state_t();
		
		showscores = false; // set layout stat
		showinventory = false; // set layout stat
		showhelp = false;
		showhelpicon = false;

		ammo_index = 0;
		
		buttons = oldbuttons = latched_buttons = 0;
		weapon_thunk = false;
		newweapon = null;
		damage_armor = 0;
		damage_parmor = 0;
		damage_blood = 0;
		damage_knockback = 0;
		
		killer_yaw = 0;
		damage_from = new float[3];
		weaponstate = WeaponStates.WEAPON_READY;
		kick_angles = new float[3];
		kick_origin = new float[3];
		v_dmg_roll = v_dmg_pitch = v_dmg_time = 0;
		fall_time = fall_value = 0;
		damage_alpha = 0;
		bonus_alpha = 0;
		damage_blend = new float[3];
		v_angle = new float[3];
		bobtime = 0;

		oldviewangles = new float[3];

		oldvelocity = new float[3];

		next_drown_time = 0;

		old_waterlevel = 0;
		
		breather_sound = 0;
		machinegun_shots = 0;
		
		anim_end = 0;
		anim_priority = 0;
		anim_duck = false;
		anim_run = false;
		
		// powerup timers
		quad_framenum = 0;
		invincible_framenum = 0;
		breather_framenum = 0;
		enviro_framenum = 0;

		grenade_blew_up = false;
		grenade_time = 0;
		silencer_shots = 0;
		weapon_sound = 0;

		pickup_msg_time = 0;

		flood_locktill = 0; // locked from talking
		flood_when  = new float[10]; // when messages were said
		flood_whenhead = 0; // head pointer for when said

		respawn_time = 0; // can respawn when time > this

		chase_target = null; // player we are chasing
		update_chase = false; // need to update chase info?
	}

	@Override
	public player_state_t getPlayerState() {
		return ps;
	}

	public void setPs(player_state_t ps) {
		this.ps = ps;
	}

	@Override
	public int getPing() {
		return ping;
	}

	@Override
	public void setPing(int ping) {
		this.ping = ping;
	}

	@Override
	public int getIndex() {
		return index;
	}

	public boolean isShowHelpIcon() {
		return showhelpicon;
	}

	public void setShowHelpIcon(boolean showHelpIcon) {
		this.showhelpicon = showHelpIcon;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}
