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

import jake2.*;
import jake2.util.*;

public class edict_t {
 
	public edict_t(int i) {
		s.number = i;
		index = i;
	}

	public void clear() {
	}

	// integrated entity state
	public entity_state_t s = new entity_state_t(this);
	public boolean inuse;
	public int linkcount;

	// FIXME: move these fields to a server private sv_entity_t
	public link_t area = new link_t(this); // linked to a division node or leaf

	public int num_clusters; // if -1, use headnode instead
	public int clusternums[] = new int[Defines.MAX_ENT_CLUSTERS];
	public int headnode; // unused if num_clusters != -1
	public int areanum, areanum2;

	//================================

	public int svflags; // SVF_NOCLIENT, SVF_DEADMONSTER, SVF_MONSTER, etc
	public float[] mins = { 0, 0, 0 };
	public float[] maxs = { 0, 0, 0 };
	public float[] absmin = { 0, 0, 0 };
	public float[] absmax = { 0, 0, 0 };
	public float[] size = { 0, 0, 0 };
	public int solid;
	public int clipmask;

	// the game dll can add anything it wants after
	// this point in the structure
	// DO NOT MODIFY ANYTHING ABOVE THIS, THE SERVER
	// EXPECTS THE FIELDS IN THAT ORDER!

	//================================
	public int movetype;
	public int flags;

	public String model = "";
	public float freetime; // sv.time when the object was freed

	//
	// only used locally in game, not by server
	//
	public String message = "";
	public String classname = "";
	public int spawnflags;

	public float timestamp;

	// set in qe3, -1 = up, -2 = down
	public float angle;

	public String target = null;
	public String targetname = null;
	public String killtarget = null;
	public String team = null;
	public String pathtarget = null;
	public String deathtarget = null;
	public String combattarget = null;

	public edict_t target_ent = null;

	public float speed, accel, decel;
	public float[] movedir = { 0, 0, 0 };

	public float[] pos1 = { 0, 0, 0 };
	public float[] pos2 = { 0, 0, 0 };

	public float[] velocity = { 0, 0, 0 };
	public float[] avelocity = { 0, 0, 0 };
	public int mass;
	public float air_finished;
	public float gravity; // per entity gravity multiplier (1.0 is normal)
	// use for lowgrav artifact, flares

	public edict_t goalentity = null;
	public edict_t movetarget = null;
	public float yaw_speed;
	public float ideal_yaw;

	public float nextthink;

	public EntThinkAdapter prethink = null;
	public EntThinkAdapter think = null;
	public EntBlockedAdapter blocked = null;
	public EntTouchAdapter touch = null;
	public EntUseAdapter use = null;
	public EntPainAdapter pain = null;
	public EntDieAdapter die = null;

	public float touch_debounce_time; // are all these legit?  do we need more/less of them?
	public float pain_debounce_time;
	public float damage_debounce_time;
	public float fly_sound_debounce_time; //move to clientinfo
	public float last_move_time;

	public int health;
	public int max_health;
	public int gib_health;
	public int deadflag;
	public int show_hostile;

	public float powerarmor_time;

	public String map = null;
	; // target_changelevel

	public int viewheight; // height above origin where eyesight is determined
	public int takedamage;
	public int dmg;
	public int radius_dmg;
	public float dmg_radius;
	public int sounds; //make this a spawntemp var?
	public int count;

	public edict_t chain = null;
	public edict_t enemy = null;
	public edict_t oldenemy = null;
	public edict_t activator = null;
	public edict_t groundentity = null;
	public int groundentity_linkcount;
	public edict_t teamchain = null;
	public edict_t teammaster = null;

	public edict_t mynoise = null; // can go in client only
	public edict_t mynoise2 = null;

	public int noise_index;
	public int noise_index2;
	public float volume;
	public float attenuation;

	// timing variables
	public float wait;
	public float delay; // before firing targets
	public float random;

	public float teleport_time;

	public int watertype;
	public int waterlevel;

	public float[] move_origin = { 0, 0, 0 };

	public float[] move_angles = { 0, 0, 0 };

	// move this to clientinfo?
	public int light_level;

	public int style; // also used as areaportal number

	public gitem_t item; // for bonus items

	// common integrated data blocks
	public moveinfo_t moveinfo = new moveinfo_t();
	public monsterinfo_t monsterinfo = new monsterinfo_t();

	public gclient_t client;
	public edict_t owner;
	
	public int index;	//introduced by rst
	

	/////////////////////////////////////////////////

	public boolean set(String key, String value) {

		if (key.equals("classname")) {
			classname = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("model")) {
			model = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("spawnflags")) {
			spawnflags = Lib.atoi(value);
			return true;
		} // F_INT),

		if (key.equals("speed")) {
			speed = Lib.atof(value);
			return true;
		} // F_FLOAT),

		if (key.equals("accel")) {
			accel = Lib.atof(value);
			return true;
		} // F_FLOAT),

		if (key.equals("decel")) {
			decel = Lib.atof(value);
			return true;
		} // F_FLOAT),

		if (key.equals("target")) {
			target = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("targetname")) {
			targetname = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("pathtarget")) {
			pathtarget = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("deathtarget")) {
			deathtarget = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),
		if (key.equals("killtarget")) {
			killtarget = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("combattarget")) {
			combattarget = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("message")) {
			message = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("team")) {
			team = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("wait")) {
			wait = Lib.atof(value);
			return true;
		} // F_FLOAT),

		if (key.equals("delay")) {
			delay = Lib.atof(value);
			return true;
		} // F_FLOAT),

		if (key.equals("random")) {
			random = Lib.atof(value);
			return true;
		} // F_FLOAT),

		if (key.equals("move_origin")) {
			move_origin = Lib.atov(value);
			return true;
		} // F_VECTOR),

		if (key.equals("move_angles")) {
			move_angles = Lib.atov(value);
			return true;
		} // F_VECTOR),

		if (key.equals("style")) {
			style = Lib.atoi(value);
			return true;
		} // F_INT),

		if (key.equals("count")) {
			count = Lib.atoi(value);
			return true;
		} // F_INT),

		if (key.equals("health")) {
			health = Lib.atoi(value);
			return true;
		} // F_INT),

		if (key.equals("sounds")) {
			sounds = Lib.atoi(value);
			return true;
		} // F_INT),

		if (key.equals("light")) {
			return true;
		} // F_IGNORE),

		if (key.equals("dmg")) {
			dmg = Lib.atoi(value);
			return true;
		} // F_INT),

		if (key.equals("mass")) {
			mass = Lib.atoi(value);
			return true;
		} // F_INT),

		if (key.equals("volume")) {
			volume = Lib.atof(value);
			return true;
		} // F_FLOAT),

		if (key.equals("attenuation")) {
			attenuation = Lib.atof(value);
			return true;
		} // F_FLOAT),

		if (key.equals("map")) {
			map = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING),

		if (key.equals("origin")) {
			s.origin = Lib.atov(value);
			return true;
		} // F_VECTOR),

		if (key.equals("angles")) {
			s.angles = Lib.atov(value);
			return true;
		} // F_VECTOR),

		if (key.equals("angle")) {
			s.angles = new float[] { 0, Lib.atof(value), 0 };
			return true;
		} // F_ANGLEHACK),

		/* --- NOSPAWN ---
		if (key.equals("goalentity")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		
		if (key.equals("movetarget")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		
		if (key.equals("enemy")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		
		if (key.equals("oldenemy")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		
		if (key.equals("activator")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		
		if (key.equals("groundentity")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		if (key.equals("teamchain")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		if (key.equals("teammaster")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		if (key.equals("owner")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		if (key.equals("mynoise")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		if (key.equals("mynoise2")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		if (key.equals("target_ent")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		if (key.equals("chain")) {
			return true;
		} // F_EDICT, FFL_NOSPAWN),
		if (key.equals("prethink")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("think")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("blocked")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("touch")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("use")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("pain")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("die")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("stand")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("idle")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("search")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("walk")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("run")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("dodge")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("attack")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("melee")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("sight")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("checkattack")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		if (key.equals("currentmove")) {
			return true;
		} // F_MMOVE, FFL_NOSPAWN),
		if (key.equals("endfunc")) {
			return true;
		} // F_FUNCTION, FFL_NOSPAWN),
		
		*/
		if (key.equals("item")) {
			Game.gi.error("ent.set(\"item\") called.");
			return true;
		} // F_ITEM)

		return false;
	}
}
