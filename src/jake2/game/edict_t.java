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

package jake2.game;

public class edict_t
{
	//TODO: implement
	public void clear()
	{
	}

	entity_state_t s;
	gclient_t client;
	boolean inuse;
	int linkcount;

	// FIXME: move these fields to a server private sv_entity_t
	link_t area; // linked to a division node or leaf

	int num_clusters; // if -1, use headnode instead
	int clusternums[]= new int[defs.MAX_ENT_CLUSTERS];
	int headnode; // unused if num_clusters != -1
	int areanum, areanum2;

	//================================

	int svflags; // SVF_NOCLIENT, SVF_DEADMONSTER, SVF_MONSTER, etc
	float[] mins= new float[3];
	float[] maxs= new float[3];
	float[] absmin= new float[3];
	float[] absmax= new float[3];
	float[] size= new float[3];
	int solid;
	int clipmask;
	edict_t owner;

	// the game dll can add anything it wants after
	// this point in the structure
	// DO NOT MODIFY ANYTHING ABOVE THIS, THE SERVER
	// EXPECTS THE FIELDS IN THAT ORDER!

	//================================
	int movetype;
	int flags;

	//TODO:
	String model;
	float freetime; // sv.time when the object was freed

	//
	// only used locally in game, not by server
	//
	String message;
	String classname;
	int spawnflags;

	float timestamp;

	float angle; // set in qe3, -1 = up, -2 = down
	// TODO:
	// check * replacement with the "String"
	String target;
	String targetname;
	String killtarget;
	String team;
	String pathtarget;
	String deathtarget;
	String combattarget;

	edict_t target_ent;

	float speed, accel, decel;
	float[] movedir= {0,0,0};
	
	float[] pos1= {0,0,0};
	float[] pos2= {0,0,0};

	float[] velocity= {0,0,0};
	float[] avelocity= {0,0,0};
	int mass;
	float air_finished;
	float gravity; // per entity gravity multiplier (1.0 is normal)
	// use for lowgrav artifact, flares

	edict_t goalentity;
	edict_t movetarget;
	float yaw_speed;
	float ideal_yaw;

	float nextthink;


	EntThinkAdapter prethink;
	EntThinkAdapter think;
	EntBlockedAdapter blocked;
	EntTouchAdapter touch;
	EntUseAdapter use;
	EntPainAdapter pain;
	EntDieAdapter die;

	float touch_debounce_time; // are all these legit?  do we need more/less of them?
	float pain_debounce_time;
	float damage_debounce_time;
	float fly_sound_debounce_time; //move to clientinfo
	float last_move_time;

	int health;
	int max_health;
	int gib_health;
	int deadflag;
	int show_hostile;

	float powerarmor_time;

	//TODO: 
	//check char * replacement!
	String map;			// target_changelevel

	int viewheight; // height above origin where eyesight is determined
	int takedamage;
	int dmg;
	int radius_dmg;
	float dmg_radius;
	int sounds; //make this a spawntemp var?
	int count;

	edict_t chain;
	edict_t enemy;
	edict_t oldenemy;
	edict_t activator;
	edict_t groundentity;
	int groundentity_linkcount;
	edict_t teamchain;
	edict_t teammaster;

	edict_t mynoise; // can go in client only
	edict_t mynoise2;

	int noise_index;
	int noise_index2;
	float volume;
	float attenuation;

	// timing variables
	float wait;
	float delay; // before firing targets
	float random;

	float teleport_time;

	int watertype;
	int waterlevel;

	float[] move_origin= {0,0,0};
	
	float[] move_angles= {0,0,0};

	// move this to clientinfo?
	int light_level;

	int style; // also used as areaportal number

	gitem_t item; // for bonus items

	// common data blocks
	moveinfo_t moveinfo;
	monsterinfo_t monsterinfo;
}
