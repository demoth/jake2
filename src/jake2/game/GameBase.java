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

// Created on 30.11.2003 by RST.
// $Id: GameBase.java,v 1.13 2004-01-09 18:30:56 rst Exp $

/** Father of all Objects. */

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.server.*;
import jake2.util.*;

public class GameBase extends Globals {
	public static game_locals_t game= new game_locals_t();
	public static level_locals_t level = new level_locals_t();
	public static game_import_t gi = new game_import_t();
	public static game_export_t globals = new game_export_t();
	public static spawn_temp_t st;

	public static int sm_meat_index;
	public static int snd_fry;
	public static int meansOfDeath;

	public static edict_t g_edicts[];

	public static cvar_t deathmatch= new cvar_t();
	public static cvar_t coop= new cvar_t();
	public static cvar_t dmflags= new cvar_t();
	public static cvar_t skill= new cvar_t();
	public static cvar_t fraglimit= new cvar_t();
	public static cvar_t timelimit= new cvar_t();
	public static cvar_t password= new cvar_t();
	public static cvar_t spectator_password= new cvar_t();
	public static cvar_t needpass= new cvar_t();
	public static cvar_t maxclients= new cvar_t();
	public static cvar_t maxspectators= new cvar_t();
	public static cvar_t maxentities = new cvar_t();
	public static cvar_t g_select_empty= new cvar_t();
	public static cvar_t dedicated= new cvar_t();

	public static cvar_t filterban= new cvar_t();

	public static cvar_t sv_maxvelocity= new cvar_t();
	public static cvar_t sv_gravity= new cvar_t();

	public static cvar_t sv_rollspeed= new cvar_t();
	public static cvar_t sv_rollangle= new cvar_t();
	public static cvar_t gun_x= new cvar_t();
	public static cvar_t gun_y= new cvar_t();
	public static cvar_t gun_z= new cvar_t();

	public static cvar_t run_pitch= new cvar_t();
	public static cvar_t run_roll= new cvar_t();
	public static cvar_t bob_up= new cvar_t();
	public static cvar_t bob_pitch= new cvar_t();
	public static cvar_t bob_roll= new cvar_t();

	public static cvar_t sv_cheats= new cvar_t();

	public static cvar_t flood_msgs= new cvar_t();
	public static cvar_t flood_persecond= new cvar_t();
	public static cvar_t flood_waitdelay= new cvar_t();

	public static cvar_t sv_maplist= new cvar_t();

	public final static float STOP_EPSILON = 0.1f;

	field_t fields_ent[] =
		new field_t[] {
			new field_t("classname", F_LSTRING),
			new field_t("model", F_LSTRING),
			new field_t("spawnflags", F_INT),
			new field_t("speed", F_FLOAT),
			new field_t("accel", F_FLOAT),
			new field_t("decel", F_FLOAT),
			new field_t("target", F_LSTRING),
			new field_t("targetname", F_LSTRING),
			new field_t("pathtarget", F_LSTRING),
			new field_t("deathtarget", F_LSTRING),
			new field_t("killtarget", F_LSTRING),
			new field_t("combattarget", F_LSTRING),
			new field_t("message", F_LSTRING),
			new field_t("team", F_LSTRING),
			new field_t("wait", F_FLOAT),
			new field_t("delay", F_FLOAT),
			new field_t("random", F_FLOAT),
			new field_t("move_origin", F_VECTOR),
			new field_t("move_angles", F_VECTOR),
			new field_t("style", F_INT),
			new field_t("count", F_INT),
			new field_t("health", F_INT),
			new field_t("sounds", F_INT),
			new field_t("light", F_IGNORE),
			new field_t("dmg", F_INT),
			new field_t("mass", F_INT),
			new field_t("volume", F_FLOAT),
			new field_t("attenuation", F_FLOAT),
			new field_t("map", F_LSTRING),
			new field_t("origin", F_VECTOR),
			new field_t("angles", F_VECTOR),
			new field_t("angle", F_ANGLEHACK),
			new field_t("goalentity", F_EDICT, FFL_NOSPAWN),
			new field_t("movetarget", F_EDICT, FFL_NOSPAWN),
			new field_t("enemy", F_EDICT, FFL_NOSPAWN),
			new field_t("oldenemy", F_EDICT, FFL_NOSPAWN),
			new field_t("activator", F_EDICT, FFL_NOSPAWN),
			new field_t("groundentity", F_EDICT, FFL_NOSPAWN),
			new field_t("teamchain", F_EDICT, FFL_NOSPAWN),
			new field_t("teammaster", F_EDICT, FFL_NOSPAWN),
			new field_t("owner", F_EDICT, FFL_NOSPAWN),
			new field_t("mynoise", F_EDICT, FFL_NOSPAWN),
			new field_t("mynoise2", F_EDICT, FFL_NOSPAWN),
			new field_t("target_ent", F_EDICT, FFL_NOSPAWN),
			new field_t("chain", F_EDICT, FFL_NOSPAWN),
			
			new field_t("prethink", F_FUNCTION, FFL_NOSPAWN),
			new field_t("think", F_FUNCTION, FFL_NOSPAWN),
			new field_t("blocked", F_FUNCTION, FFL_NOSPAWN),
			new field_t("touch", F_FUNCTION, FFL_NOSPAWN),
			new field_t("use", F_FUNCTION, FFL_NOSPAWN),
			new field_t("pain", F_FUNCTION, FFL_NOSPAWN),
			new field_t("die", F_FUNCTION, FFL_NOSPAWN),
			new field_t("stand", F_FUNCTION, FFL_NOSPAWN),
			new field_t("idle", F_FUNCTION, FFL_NOSPAWN),
			new field_t("search", F_FUNCTION, FFL_NOSPAWN),
			new field_t("walk", F_FUNCTION, FFL_NOSPAWN),
			new field_t("run", F_FUNCTION, FFL_NOSPAWN),
			new field_t("dodge", F_FUNCTION, FFL_NOSPAWN),
			new field_t("attack", F_FUNCTION, FFL_NOSPAWN),
			new field_t("melee", F_FUNCTION, FFL_NOSPAWN),
			new field_t("sight", F_FUNCTION, FFL_NOSPAWN),
			new field_t("checkattack", F_FUNCTION, FFL_NOSPAWN),
			
			new field_t("currentmove", F_MMOVE, FFL_NOSPAWN),
			
			new field_t("endfunc", F_FUNCTION, FFL_NOSPAWN),
			new field_t("item", F_ITEM)
		//need for item field in edict struct, FFL_SPAWNTEMP item will be skipped on saves
	};

	// temp spawn vars -- only valid when the spawn function is called
	field_t fields_st[] =
		{
			new field_t("lip", F_INT, FFL_SPAWNTEMP),
			new field_t("distance", F_INT, FFL_SPAWNTEMP),
			new field_t("height", F_INT, FFL_SPAWNTEMP),
			new field_t("noise", F_LSTRING, FFL_SPAWNTEMP),
			new field_t("pausetime", F_FLOAT, FFL_SPAWNTEMP),
			new field_t("item", F_LSTRING, FFL_SPAWNTEMP),
			new field_t("gravity", F_LSTRING, FFL_SPAWNTEMP),
			new field_t("sky", F_LSTRING, FFL_SPAWNTEMP),
			new field_t("skyrotate", F_FLOAT, FFL_SPAWNTEMP),
			new field_t("skyaxis", F_VECTOR, FFL_SPAWNTEMP),
			new field_t("minyaw", F_FLOAT, FFL_SPAWNTEMP),
			new field_t("maxyaw", F_FLOAT, FFL_SPAWNTEMP),
			new field_t("minpitch", F_FLOAT, FFL_SPAWNTEMP),
			new field_t("maxpitch", F_FLOAT, FFL_SPAWNTEMP),
			new field_t("nextmap", F_LSTRING, FFL_SPAWNTEMP),
			};

	/**
	 * Slide off of the impacting object
	 * returns the blocked flags (1 = floor, 2 = step / wall).
	 */
	public static int ClipVelocity(float[] in, float[] normal, float[] out, float overbounce) {
		float backoff;
		float change;
		int i, blocked;

		blocked = 0;
		if (normal[2] > 0)
			blocked |= 1; // floor
		if (normal[2] == 0.0f)
			blocked |= 2; // step

		backoff = Math3D.DotProduct(in, normal) * overbounce;

		for (i = 0; i < 3; i++) {
			change = normal[i] * backoff;
			out[i] = in[i] - change;
			if (out[i] > -STOP_EPSILON && out[i] < STOP_EPSILON)
				out[i] = 0;
		}

		return blocked;
	}

	/**
	SV_FlyMove
	
	The basic solid body movement clip that slides along multiple planes
	Returns the clipflags if the velocity was modified (hit something solid)
	1 = floor
	2 = wall / step
	4 = dead stop
	*/
	public final static int MAX_CLIP_PLANES = 5;
	public static float vec3_origin[] = { 0.0f, 0.0f, 0.0f };
	/*
	=============
	G_Find
	
	Searches all active entities for the next one that holds
	the matching string at fieldofs (use the FOFS() macro) in the structure.
	
	Searches beginning at the edict after from, or the beginning if NULL
	NULL will be returned if the end of the list is reached.
	
	=============
	*/

	/** 
	 * Finds an edict.
	 * Call with null as from parameter to search from array beginning.
	 */

	public static EdictIterator G_Find(EdictIterator from, EdictFindFilter eff, String s) {
		if (from == null)
			from = new EdictIterator(0);
		else
			from.i++;

		for (; from.i < globals.num_edicts; from.i++) {
			from.o = g_edicts[from.i];

			if (!from.o.inuse)
				continue;

			if (eff.matches(from.o, s))
				return from;
		}
		from.o = null;
		from.i = 0;
		return from;
	}

	/**
	 * 
	 * Returns entities that have origins within a spherical area.
	*/
	public static EdictIterator findradius(EdictIterator from, float[] org, float rad) {
		float[] eorg = { 0, 0, 0 };
		int j;

		if (from == null)
			from = new EdictIterator(0);
		else
			from.i++;

		for (; from.i < globals.num_edicts; from.i++) {
			from.o = g_edicts[from.i];
			if (!from.o.inuse)
				continue;

			if (from.o.solid == SOLID_NOT)
				continue;

			for (j = 0; j < 3; j++)
				eorg[j] = org[j] - (from.o.s.origin[j] + (from.o.mins[j] + from.o.maxs[j]) * 0.5f);

			if (Math3D.VectorLength(eorg) > rad)
				continue;
			return from;
		}

		return null;
	}

	/**
	 * Searches all active entities for the next one that holds
	 * the matching string at fieldofs (use the FOFS() macro) in the structure.
	 *
	 *	Searches beginning at the edict after from, or the beginning if NULL 
	 *	NULL will be returned if the end of the list is reached.
	 */

	public static int MAXCHOICES = 8;

	public static edict_t G_PickTarget(String targetname) {
		int num_choices = 0;
		edict_t choice[] = new edict_t[MAXCHOICES];

		if (targetname == null) {
			gi.dprintf("G_PickTarget called with NULL targetname\n");
			return null;
		}

		EdictIterator es = null;

		while ((es = G_Find(es, findByTarget, targetname)) != null) {
			choice[num_choices++] = es.o;
			if (num_choices == MAXCHOICES)
				break;
		}

		if (num_choices == 0) {
			gi.dprintf("G_PickTarget: target " + targetname + " not found\n");
			return null;
		}

		return choice[Lib.rand() % num_choices];
	}

	public static float[] VEC_UP = { 0, -1, 0 };
	public static float[] MOVEDIR_UP = { 0, 0, 1 };
	public static float[] VEC_DOWN = { 0, -2, 0 };
	public static float[] MOVEDIR_DOWN = { 0, 0, -1 };

	public static void G_SetMovedir(float[] angles, float[] movedir) {
		if (Math3D.VectorCompare(angles, VEC_UP) != 0) {
			Math3D.VectorCopy(MOVEDIR_UP, movedir);
		}
		else if (Math3D.VectorCompare(angles, VEC_DOWN) != 0) {
			Math3D.VectorCopy(MOVEDIR_DOWN, movedir);
		}
		else {
			Math3D.AngleVectors(angles, movedir, null, null);
		}

		Math3D.VectorClear(angles);
	}

	public static String G_CopyString(String in) {
		return new String(in);
	}

	/*
	============
	G_TouchTriggers
	
	============
	*/
	public static void G_TouchTriggers(edict_t ent) {
		int i, num;
		edict_t touch[] = new edict_t[MAX_EDICTS], hit;

		// dead things don't activate triggers!
		if ((ent.client != null || (ent.svflags & SVF_MONSTER) != 0) && (ent.health <= 0))
			return;

		num = gi.BoxEdicts(ent.absmin, ent.absmax, touch, MAX_EDICTS, AREA_TRIGGERS);

		// be careful, it is possible to have an entity in this
		// list removed before we get to it (killtriggered)
		for (i = 0; i < num; i++) {
			hit = touch[i];
			if (!hit.inuse)
				continue;

			if (hit.touch == null)
				continue;

			hit.touch.touch(hit, ent, null, null);
		}
	}

	public static pushed_t pushed[] = new pushed_t[MAX_EDICTS];
	public static int pushed_p;

	public static edict_t obstacle;

	/*
	=============
	M_CheckBottom
	
	Returns false if any part of the bottom of the entity is off an edge that
	is not a staircase.
	
	=============
	*/
	public static int c_yes, c_no;

	public static int STEPSIZE = 18;

	//	  ============================================================================
	/*
	================
	G_RunEntity
	
	================
	*/
	public static void G_RunEntity(edict_t ent) {
		if (ent.prethink != null)
			ent.prethink.think(ent);

		switch ((int) ent.movetype) {
			case MOVETYPE_PUSH :
			case MOVETYPE_STOP :
				SV.SV_Physics_Pusher(ent);
				break;
			case MOVETYPE_NONE :
				SV.SV_Physics_None(ent);
				break;
			case MOVETYPE_NOCLIP :
				SV.SV_Physics_Noclip(ent);
				break;
			case MOVETYPE_STEP :
				SV.SV_Physics_Step(ent);
				break;
			case MOVETYPE_TOSS :
			case MOVETYPE_BOUNCE :
			case MOVETYPE_FLY :
			case MOVETYPE_FLYMISSILE :
				SV.SV_Physics_Toss(ent);
				break;
			default :
				gi.error("SV_Physics: bad movetype " + (int) ent.movetype);
		}
	}

	/*
	================
	SV_NewChaseDir
	
	================
	*/
	public static int DI_NODIR = -1;
	public static void assert1(boolean cond) {
		if (!cond) {

			try {

				int a[] = null;
				int b = a[0];
			}
			catch (Exception e) {
				System.err.println("assertion failed!");
				e.printStackTrace();
			}

		}
	}

	public static void ClearBounds(float[] mins, float[] maxs) {
		mins[0] = mins[1] = mins[2] = 99999;
		maxs[0] = maxs[1] = maxs[2] = -99999;
	}

	public static void AddPointToBounds(float[] v, float[] mins, float[] maxs) {
		int i;
		float val;

		for (i = 0; i < 3; i++) {
			val = v[i];
			if (val < mins[i])
				mins[i] = val;
			if (val > maxs[i])
				maxs[i] = val;
		}
	}

	public static EdictFindFilter findByTarget = new EdictFindFilter() {
		public boolean matches(edict_t e, String s) {
			return e.targetname.equalsIgnoreCase(s);
		}
	};
	
	public static EdictFindFilter findByClass = new EdictFindFilter() {
		public boolean matches(edict_t e, String s) {
			return e.classname.equalsIgnoreCase(s);
		}
	};

}