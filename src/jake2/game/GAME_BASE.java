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

/** Father of all Objects. */

package jake2.game;

public class GAME_BASE extends defs {
	static game_locals_t game;
	static level_locals_t level;
	static game_import_t gi;
	static game_export_t globals;
	static spawn_temp_t st;

	static int sm_meat_index;
	static int snd_fry;
	static int meansOfDeath;

	static edict_t g_edicts[];

	static cvar_t deathmatch;
	static cvar_t coop;
	static cvar_t dmflags;
	static cvar_t skill;
	static cvar_t fraglimit;
	static cvar_t timelimit;
	static cvar_t password;
	static cvar_t spectator_password;
	static cvar_t needpass;
	static cvar_t maxclients;
	static cvar_t maxspectators;
	static cvar_t maxentities;
	static cvar_t g_select_empty;
	static cvar_t dedicated;

	static cvar_t filterban;

	static cvar_t sv_maxvelocity;
	static cvar_t sv_gravity;

	static cvar_t sv_rollspeed;
	static cvar_t sv_rollangle;
	static cvar_t gun_x;
	static cvar_t gun_y;
	static cvar_t gun_z;

	static cvar_t run_pitch;
	static cvar_t run_roll;
	static cvar_t bob_up;
	static cvar_t bob_pitch;
	static cvar_t bob_roll;

	static cvar_t sv_cheats;

	static cvar_t flood_msgs;
	static cvar_t flood_persecond;
	static cvar_t flood_waitdelay;

	static cvar_t sv_maplist;

	//file_io
	//=====================================================================
	//g_phys

	///////////////////////////////////////
	static edict_t[] SV_TestEntityPosition(edict_t ent) {
		trace_t trace;
		int mask;

		if (ent.clipmask != 0)
			mask= ent.clipmask;
		else
			mask= MASK_SOLID;

		trace= gi.trace(ent.s.origin, ent.mins, ent.maxs, ent.s.origin, ent, mask);

		if (trace.startsolid)
			return g_edicts;

		return null;
	}

	///////////////////////////////////////
	static void SV_CheckVelocity(edict_t ent) {
		int i;

		//
		//	   bound velocity
		//
		for (i= 0; i < 3; i++) {
			if (ent.velocity[i] > sv_maxvelocity.value)
				ent.velocity[i]= sv_maxvelocity.value;
			else if (ent.velocity[i] < -sv_maxvelocity.value)
				ent.velocity[i]= -sv_maxvelocity.value;
		}
	}

	/**
	 * Runs thinking code for this frame if necessary.
	 */
	static boolean SV_RunThink(edict_t ent) {
		float thinktime;

		thinktime= ent.nextthink;
		if (thinktime <= 0)
			return true;
		if (thinktime > level.time + 0.001)
			return true;

		ent.nextthink= 0;

		if (ent.think == null)
			gi.error("NULL ent.think");

		ent.think.think(ent);

		return false;
	}

	/** 
	 * Two entities have touched, so run their touch functions.
	 */
	static void SV_Impact(edict_t e1, trace_t trace) {
		edict_t e2;
		// cplane_t	backplane;

		e2= trace.ent;

		if (e1.touch != null && e1.solid != SOLID_NOT)
			e1.touch.touch(e1, e2, trace.plane, trace.surface);

		if (e2.touch != null && e2.solid != SOLID_NOT)
			e2.touch.touch(e2, e1, null, null);
	}

	public final static float STOP_EPSILON= 0.1f;

	/**
	 * Slide off of the impacting object
	 * returns the blocked flags (1 = floor, 2 = step / wall).
	 */
	static int ClipVelocity(float[] in, float[] normal, float[] out, float overbounce) {
		float backoff;
		float change;
		int i, blocked;

		blocked= 0;
		if (normal[2] > 0)
			blocked |= 1; // floor
		if (normal[2] == 0.0f)
			blocked |= 2; // step

		backoff= DotProduct(in, normal) * overbounce;

		for (i= 0; i < 3; i++) {
			change= normal[i] * backoff;
			out[i]= in[i] - change;
			if (out[i] > -STOP_EPSILON && out[i] < STOP_EPSILON)
				out[i]= 0;
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
	public final static int MAX_CLIP_PLANES= 5;
	static float vec3_origin[]= { 0.0f, 0.0f, 0.0f };
	static int SV_FlyMove(edict_t ent, float time, int mask) {
		edict_t hit;
		int bumpcount, numbumps;
		float[] dir= { 0.0f, 0.0f, 0.0f };
		float d;
		int numplanes;
		float[][] planes= new float[3][MAX_CLIP_PLANES];
		float[] primal_velocity= { 0.0f, 0.0f, 0.0f };
		float[] original_velocity= { 0.0f, 0.0f, 0.0f };
		float[] new_velocity= { 0.0f, 0.0f, 0.0f };
		int i, j;
		trace_t trace;
		float[] end= { 0.0f, 0.0f, 0.0f };
		float time_left;
		int blocked;

		numbumps= 4;

		blocked= 0;
		VectorCopy(ent.velocity, original_velocity);
		VectorCopy(ent.velocity, primal_velocity);
		numplanes= 0;

		time_left= time;

		ent.groundentity= null;
		for (bumpcount= 0; bumpcount < numbumps; bumpcount++) {
			for (i= 0; i < 3; i++)
				end[i]= ent.s.origin[i] + time_left * ent.velocity[i];

			trace= gi.trace(ent.s.origin, ent.mins, ent.maxs, end, ent, mask);

			if (trace.allsolid) { // entity is trapped in another solid
				VectorCopy(vec3_origin, ent.velocity);
				return 3;
			}

			if (trace.fraction > 0) { // actually covered some distance
				VectorCopy(trace.endpos, ent.s.origin);
				VectorCopy(ent.velocity, original_velocity);
				numplanes= 0;
			}

			if (trace.fraction == 1)
				break; // moved the entire distance

			hit= trace.ent;

			if (trace.plane.normal[2] > 0.7) {
				blocked |= 1; // floor
				if (hit.solid == SOLID_BSP) {
					ent.groundentity= hit;
					ent.groundentity_linkcount= hit.linkcount;
				}
			}
			if (trace.plane.normal[2] == 0.0f) {
				blocked |= 2; // step
			}

			//
			//	   run the impact function
			//
			SV_Impact(ent, trace);
			if (!ent.inuse)
				break; // removed by the impact function

			time_left -= time_left * trace.fraction;

			// cliped to another plane
			if (numplanes >= MAX_CLIP_PLANES) { // this shouldn't really happen
				VectorCopy(vec3_origin, ent.velocity);
				return 3;
			}

			VectorCopy(trace.plane.normal, planes[numplanes]);
			numplanes++;

			//
			//	   modify original_velocity so it parallels all of the clip planes
			//
			for (i= 0; i < numplanes; i++) {
				ClipVelocity(original_velocity, planes[i], new_velocity, 1);

				for (j= 0; j < numplanes; j++)
					if ((j != i) && VectorCompare(planes[i], planes[j]) == 0.0f) {
						if (DotProduct(new_velocity, planes[j]) < 0)
							break; // not ok
					}
				if (j == numplanes)
					break;
			}

			if (i != numplanes) { // go along this plane
				VectorCopy(new_velocity, ent.velocity);
			} else { // go along the crease
				if (numplanes != 2) {
					//					gi.dprintf ("clip velocity, numplanes == %i\n",numplanes);
					VectorCopy(vec3_origin, ent.velocity);
					return 7;
				}
				CrossProduct(planes[0], planes[1], dir);
				d= DotProduct(dir, ent.velocity);
				VectorScale(dir, d, ent.velocity);
			}

			//
			//	   if original velocity is against the original velocity, stop dead
			//	   to avoid tiny occilations in sloping corners
			//
			if (DotProduct(ent.velocity, primal_velocity) <= 0) {
				VectorCopy(vec3_origin, ent.velocity);
				return blocked;
			}
		}

		return blocked;
	}

	/*
	============
	SV_AddGravity
	
	============
	*/
	static void SV_AddGravity(edict_t ent) {
		ent.velocity[2] -= ent.gravity * sv_gravity.value * FRAMETIME;
	}

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

	static EdictIterator G_Find(EdictIterator from, EdictFindFilter eff, String s) {
		if (from == null)
			from= new EdictIterator(0);
		else
			from.i++;

		for (; from.i < globals.num_edicts; from.i++) {
			from.o= g_edicts[from.i];

			if (!from.o.inuse)
				continue;

			if (eff.matches(from.o, s))
				return from;
		}
		return null;
	}

	/**
	 * 
	 * Returns entities that have origins within a spherical area.
	*/
	static EdictIterator findradius(EdictIterator from, float[] org, float rad) {
		float[] eorg= { 0, 0, 0 };
		int j;

		if (from == null)
			from= new EdictIterator(0);
		else
			from.i++;

		for (; from.i < globals.num_edicts; from.i++) {
			from.o= g_edicts[from.i];
			if (!from.o.inuse)
				continue;

			if (from.o.solid == SOLID_NOT)
				continue;

			for (j= 0; j < 3; j++)
				eorg[j]= org[j] - (from.o.s.origin[j] + (from.o.mins[j] + from.o.maxs[j]) * 0.5f);

			if (VectorLength(eorg) > rad)
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

	static int MAXCHOICES= 8;

	static edict_t G_PickTarget(String targetname) {
		int num_choices= 0;
		edict_t choice[]= new edict_t[MAXCHOICES];

		if (targetname == null) {
			gi.dprintf("G_PickTarget called with NULL targetname\n");
			return null;
		}

		EdictIterator es= null;

		while ((es= G_Find(es, findByTarget, targetname)) != null) {
			choice[num_choices++]= es.o;
			if (num_choices == MAXCHOICES)
				break;
		}

		if (num_choices == 0) {
			gi.dprintf("G_PickTarget: target " + targetname + " not found\n");
			return null;
		}

		return choice[rand() % num_choices];
	}

	/*
	=============
	TempVector
	
	This is just a convenience function
	for making temporary vectors for function calls
	=============
	*/
	static float tv_vecs[][]= new float[8][3];
	static int tv_index;

	static float[] tv(float x, float y, float z) {

		float[] v;

		// use an array so that multiple tempvectors won't collide
		// for a while
		v= tv_vecs[tv_index];
		tv_index= (tv_index++) & 7;

		v[0]= x;
		v[1]= y;
		v[2]= z;

		return v;
	}

	/*
	=============
	VectorToString
	
	This is just a convenience function
	for printing vectors
	=============
	*/
	static String vtos(float[] v) {
		return "(" + (int) v[0] + " " + (int) v[1] + " " + (int) v[2] + ")";
	}

	static float[] VEC_UP= { 0, -1, 0 };
	static float[] MOVEDIR_UP= { 0, 0, 1 };
	static float[] VEC_DOWN= { 0, -2, 0 };
	static float[] MOVEDIR_DOWN= { 0, 0, -1 };

	static void G_SetMovedir(float[] angles, float[] movedir) {
		if (VectorCompare(angles, VEC_UP) != 0) {
			VectorCopy(MOVEDIR_UP, movedir);
		} else if (VectorCompare(angles, VEC_DOWN) != 0) {
			VectorCopy(MOVEDIR_DOWN, movedir);
		} else {
			AngleVectors(angles, movedir, null, null);
		}

		VectorClear(angles);
	}

	static float vectoyaw(float[] vec) {
		float yaw;

		if (/*vec[YAW] == 0 &&*/
			vec[PITCH] == 0) {
			yaw= 0;
			if (vec[YAW] > 0)
				yaw= 90;
			else if (vec[YAW] < 0)
				yaw= -90;
		} else {

			yaw= (int) (Math.atan2(vec[YAW], vec[PITCH]) * 180 / Math.PI);
			if (yaw < 0)
				yaw += 360;
		}

		return yaw;
	}

	static void vectoangles(float[] value1, float[] angles) {
		float forward;
		float yaw, pitch;

		if (value1[1] == 0 && value1[0] == 0) {
			yaw= 0;
			if (value1[2] > 0)
				pitch= 90;
			else
				pitch= 270;
		} else {
			if (value1[0] != 0)
				yaw= (int) (Math.atan2(value1[1], value1[0]) * 180 / Math.PI);
			else if (value1[1] > 0)
				yaw= 90;
			else
				yaw= -90;
			if (yaw < 0)
				yaw += 360;

			forward= (float) Math.sqrt(value1[0] * value1[0] + value1[1] * value1[1]);
			pitch= (int) (Math.atan2(value1[2], forward) * 180 / Math.PI);
			if (pitch < 0)
				pitch += 360;
		}

		angles[PITCH]= -pitch;
		angles[YAW]= yaw;
		angles[ROLL]= 0;
	}

	static String G_CopyString(String in) {
		return new String(in);
	}

	/*
	============
	G_TouchTriggers
	
	============
	*/
	static void G_TouchTriggers(edict_t ent) {
		int i, num;
		edict_t touch[]= new edict_t[MAX_EDICTS], hit;

		// dead things don't activate triggers!
		if ((ent.client != null || (ent.svflags & SVF_MONSTER) != 0) && (ent.health <= 0))
			return;

		num= gi.BoxEdicts(ent.absmin, ent.absmax, touch, MAX_EDICTS, AREA_TRIGGERS);

		// be careful, it is possible to have an entity in this
		// list removed before we get to it (killtriggered)
		for (i= 0; i < num; i++) {
			hit= touch[i];
			if (!hit.inuse)
				continue;

			if (hit.touch == null)
				continue;

			hit.touch.touch(hit, ent, null, null);
		}
	}

	/**
	 * Does not change the entities velocity at all
	*/
	static trace_t SV_PushEntity(edict_t ent, float[] push) {
		trace_t trace;
		float[] start= { 0, 0, 0 };
		float[] end= { 0, 0, 0 };
		int mask;

		VectorCopy(ent.s.origin, start);
		VectorAdd(start, push, end);

		// FIXME: test this
		// a goto statement was replaced.
		boolean retry;

		do {
			if (ent.clipmask != 0)
				mask= ent.clipmask;
			else
				mask= MASK_SOLID;

			trace= gi.trace(start, ent.mins, ent.maxs, end, ent, mask);

			VectorCopy(trace.endpos, ent.s.origin);
			gi.linkentity(ent);

			retry= false;
			if (trace.fraction != 1.0) {
				SV_Impact(ent, trace);

				// if the pushed entity went away and the pusher is still there
				if (!trace.ent.inuse && ent.inuse) {
					// move the pusher back and try again
					VectorCopy(start, ent.s.origin);
					gi.linkentity(ent);
					//goto retry;
					retry= true;
				}
			}
		} while (retry);

		if (ent.inuse)
			G_TouchTriggers(ent);

		return trace;
	}

	static pushed_t pushed[]= new pushed_t[MAX_EDICTS];
	static int pushed_p;

	static edict_t obstacle;

	/*
	============
	SV_Push
	
	Objects need to be moved back on a failed push,
	otherwise riders would continue to slide.
	============
	*/
	static boolean SV_Push(edict_t pusher, float[] move, float[] amove) {
		int i, e;
		edict_t check, block[];
		float[] mins= { 0, 0, 0 };
		float[] maxs= { 0, 0, 0 };
		pushed_t p;
		float[] org= { 0, 0, 0 };
		float[] org2= { 0, 0, 0 };
		float[] move2= { 0, 0, 0 };
		float[] forward= { 0, 0, 0 };
		float[] right= { 0, 0, 0 };
		float[] up= { 0, 0, 0 };

		// clamp the move to 1/8 units, so the position will
		// be accurate for client side prediction
		for (i= 0; i < 3; i++) {
			float temp;
			temp= move[i] * 8.0f;
			if (temp > 0.0)
				temp += 0.5;
			else
				temp -= 0.5;
			move[i]= 0.125f * (int) temp;
		}

		// find the bounding box
		for (i= 0; i < 3; i++) {
			mins[i]= pusher.absmin[i] + move[i];
			maxs[i]= pusher.absmax[i] + move[i];
		}

		//	   we need this for pushing things later
		VectorSubtract(vec3_origin, amove, org);
		AngleVectors(org, forward, right, up);

		//	   save the pusher's original position
		pushed[pushed_p].ent= pusher;
		VectorCopy(pusher.s.origin, pushed[pushed_p].origin);
		VectorCopy(pusher.s.angles, pushed[pushed_p].angles);

		if (pusher.client != null)
			pushed[pushed_p].deltayaw= pusher.client.ps.pmove.delta_angles[YAW];

		pushed_p++;

		//	   move the pusher to it's final position
		VectorAdd(pusher.s.origin, move, pusher.s.origin);
		VectorAdd(pusher.s.angles, amove, pusher.s.angles);
		gi.linkentity(pusher);

		//	   see if any solid entities are inside the final position

		//check= g_edicts + 1;
		for (e= 1; e < globals.num_edicts; e++) {
			check= g_edicts[e];
			if (!check.inuse)
				continue;
			if (check.movetype == MOVETYPE_PUSH
				|| check.movetype == MOVETYPE_STOP
				|| check.movetype == MOVETYPE_NONE
				|| check.movetype == MOVETYPE_NOCLIP)
				continue;

			if (check.area.prev == null)
				continue; // not linked in anywhere

			// if the entity is standing on the pusher, it will definitely be moved
			if (check.groundentity != pusher) {
				// see if the ent needs to be tested
				if (check.absmin[0] >= maxs[0]
					|| check.absmin[1] >= maxs[1]
					|| check.absmin[2] >= maxs[2]
					|| check.absmax[0] <= mins[0]
					|| check.absmax[1] <= mins[1]
					|| check.absmax[2] <= mins[2])
					continue;

				// see if the ent's bbox is inside the pusher's final position
				if (SV_TestEntityPosition(check) == null)
					continue;
			}

			if ((pusher.movetype == MOVETYPE_PUSH) || (check.groundentity == pusher)) {
				// move this entity
				pushed[pushed_p].ent= check;
				VectorCopy(check.s.origin, pushed[pushed_p].origin);
				VectorCopy(check.s.angles, pushed[pushed_p].angles);
				pushed_p++;

				// try moving the contacted entity 
				VectorAdd(check.s.origin, move, check.s.origin);
				if (check.client != null) { // FIXME: doesn't rotate monsters?
					check.client.ps.pmove.delta_angles[YAW] += amove[YAW];
				}

				// figure movement due to the pusher's amove
				VectorSubtract(check.s.origin, pusher.s.origin, org);
				org2[0]= DotProduct(org, forward);
				org2[1]= -DotProduct(org, right);
				org2[2]= DotProduct(org, up);
				VectorSubtract(org2, org, move2);
				VectorAdd(check.s.origin, move2, check.s.origin);

				// may have pushed them off an edge
				if (check.groundentity != pusher)
					check.groundentity= null;

				block= SV_TestEntityPosition(check);
				if (block == null) { // pushed ok
					gi.linkentity(check);
					// impact?
					continue;
				}

				// if it is ok to leave in the old position, do it
				// this is only relevent for riding entities, not pushed
				// FIXME: this doesn't acount for rotation
				VectorSubtract(check.s.origin, move, check.s.origin);
				block= SV_TestEntityPosition(check);

				if (block == null) {
					pushed_p--;
					continue;
				}
			}

			// save off the obstacle so we can call the block function
			obstacle= check;

			// move back any entities we already moved
			// go backwards, so if the same entity was pushed
			// twice, it goes back to the original position
			for (int ip= pushed_p - 1; ip >= 0; ip--) {
				p= pushed[ip];
				VectorCopy(p.origin, p.ent.s.origin);
				VectorCopy(p.angles, p.ent.s.angles);
				if (p.ent.client != null) {
					p.ent.client.ps.pmove.delta_angles[YAW]= (short) p.deltayaw;
				}
				gi.linkentity(p.ent);
			}
			return false;
		}

		//	  FIXME: is there a better way to handle this?
		// see if anything we moved has touched a trigger
		for (int ip= pushed_p - 1; ip >= 0; ip--)
			G_TouchTriggers(pushed[ip].ent);

		return true;
	}

	/*
	================
	SV_Physics_Pusher
	
	Bmodel objects don't interact with each other, but
	push all box objects
	================
	*/
	static void SV_Physics_Pusher(edict_t ent) {
		float[] move= { 0, 0, 0 };
		float[] amove= { 0, 0, 0 };
		edict_t part, mv;

		// if not a team captain, so movement will be handled elsewhere
		if ((ent.flags & FL_TEAMSLAVE) != 0)
			return;

		// make sure all team slaves can move before commiting
		// any moves or calling any think functions
		// if the move is blocked, all moved objects will be backed out
		//	  retry:
		pushed_p= 0;
		for (part= ent; part != null; part= part.teamchain) {
			if (part.velocity[0] != 0
				|| part.velocity[1] != 0
				|| part.velocity[2] != 0
				|| part.avelocity[0] != 0
				|| part.avelocity[1] != 0
				|| part.avelocity[2] != 0) { // object is moving
				VectorScale(part.velocity, FRAMETIME, move);
				VectorScale(part.avelocity, FRAMETIME, amove);

				if (!SV_Push(part, move, amove))
					break; // move was blocked
			}
		}
		if (pushed_p > MAX_EDICTS)
			gi.error(ERR_FATAL, "pushed_p > &pushed[MAX_EDICTS], memory corrupted");

		if (part != null) {
			// the move failed, bump all nextthink times and back out moves
			for (mv= ent; mv != null; mv= mv.teamchain) {
				if (mv.nextthink > 0)
					mv.nextthink += FRAMETIME;
			}

			// if the pusher has a "blocked" function, call it
			// otherwise, just stay in place until the obstacle is gone
			if (part.blocked != null)
				part.blocked.blocked(part, obstacle);
		} else { // the move succeeded, so call all think functions
			for (part= ent; part != null; part= part.teamchain) {
				SV_RunThink(part);
			}
		}
	}

	//	  ==================================================================

	/*
	=============
	SV_Physics_None
	
	Non moving objects can only think
	=============
	*/
	static void SV_Physics_None(edict_t ent) {
		//	   regular thinking
		SV_RunThink(ent);
	}

	/*
	=============
	SV_Physics_Noclip
	
	A moving object that doesn't obey physics
	=============
	*/
	static void SV_Physics_Noclip(edict_t ent) {
		//	   regular thinking
		if (!SV_RunThink(ent))
			return;

		VectorMA(ent.s.angles, FRAMETIME, ent.avelocity, ent.s.angles);
		VectorMA(ent.s.origin, FRAMETIME, ent.velocity, ent.s.origin);

		gi.linkentity(ent);
	}

	/*
	==============================================================================
	
	TOSS / BOUNCE
	
	==============================================================================
	*/

	/*
	=============
	SV_Physics_Toss
	
	Toss, bounce, and fly movement.  When onground, do nothing.
	=============
	*/
	static void SV_Physics_Toss(edict_t ent) {
		trace_t trace;
		float[] move= { 0, 0, 0 };
		float backoff;
		edict_t slave;
		boolean wasinwater;
		boolean isinwater;
		float[] old_origin= { 0, 0, 0 };

		//	   regular thinking
		SV_RunThink(ent);

		// if not a team captain, so movement will be handled elsewhere
		if ((ent.flags & FL_TEAMSLAVE) != 0)
			return;

		if (ent.velocity[2] > 0)
			ent.groundentity= null;

		//	   check for the groundentity going away
		if (ent.groundentity != null)
			if (!ent.groundentity.inuse)
				ent.groundentity= null;

		//	   if onground, return without moving
		if (ent.groundentity != null)
			return;

		VectorCopy(ent.s.origin, old_origin);

		SV_CheckVelocity(ent);

		//	   add gravity
		if (ent.movetype != MOVETYPE_FLY && ent.movetype != MOVETYPE_FLYMISSILE)
			SV_AddGravity(ent);

		//	   move angles
		VectorMA(ent.s.angles, FRAMETIME, ent.avelocity, ent.s.angles);

		//	   move origin
		VectorScale(ent.velocity, FRAMETIME, move);
		trace= SV_PushEntity(ent, move);
		if (!ent.inuse)
			return;

		if (trace.fraction < 1) {
			if (ent.movetype == MOVETYPE_BOUNCE)
				backoff= 1.5f;
			else
				backoff= 1;

			ClipVelocity(ent.velocity, trace.plane.normal, ent.velocity, backoff);

			// stop if on ground
			if (trace.plane.normal[2] > 0.7) {
				if (ent.velocity[2] < 60 || ent.movetype != MOVETYPE_BOUNCE) {
					ent.groundentity= trace.ent;
					ent.groundentity_linkcount= trace.ent.linkcount;
					VectorCopy(vec3_origin, ent.velocity);
					VectorCopy(vec3_origin, ent.avelocity);
				}
			}

			//			if (ent.touch)
			//				ent.touch (ent, trace.ent, &trace.plane, trace.surface);
		}

		//	   check for water transition
		wasinwater= (ent.watertype & MASK_WATER) != 0;
		ent.watertype= gi.pointcontents(ent.s.origin);
		isinwater= (ent.watertype & MASK_WATER) != 0;

		if (isinwater)
			ent.waterlevel= 1;
		else
			ent.waterlevel= 0;

		if (!wasinwater && isinwater)
			gi.positioned_sound(
				old_origin,
				g_edicts,
				CHAN_AUTO,
				gi.soundindex("misc/h2ohit1.wav"),
				1,
				1,
				0);
		else if (wasinwater && !isinwater)
			gi.positioned_sound(
				ent.s.origin,
				g_edicts,
				CHAN_AUTO,
				gi.soundindex("misc/h2ohit1.wav"),
				1,
				1,
				0);

		//	   move teamslaves
		for (slave= ent.teamchain; slave != null; slave= slave.teamchain) {
			VectorCopy(ent.s.origin, slave.s.origin);
			gi.linkentity(slave);
		}
	}

	/*
	===============================================================================
	
	STEPPING MOVEMENT
	
	===============================================================================
	*/

	/*
	=============
	SV_Physics_Step
	
	Monsters freefall when they don't have a ground entity, otherwise
	all movement is done with discrete steps.
	
	This is also used for objects that have become still on the ground, but
	will fall if the floor is pulled out from under them.
	FIXME: is this true?
	=============
	*/

	//	  FIXME: hacked in for E3 demo

	static void SV_AddRotationalFriction(edict_t ent) {
		int n;
		float adjustment;

		VectorMA(ent.s.angles, FRAMETIME, ent.avelocity, ent.s.angles);
		adjustment= FRAMETIME * sv_stopspeed * sv_friction;
		for (n= 0; n < 3; n++) {
			if (ent.avelocity[n] > 0) {
				ent.avelocity[n] -= adjustment;
				if (ent.avelocity[n] < 0)
					ent.avelocity[n]= 0;
			} else {
				ent.avelocity[n] += adjustment;
				if (ent.avelocity[n] > 0)
					ent.avelocity[n]= 0;
			}
		}
	}

	static void M_CheckGround(edict_t ent) {
		float[] point= { 0, 0, 0 };
		trace_t trace;

		if ((ent.flags & (FL_SWIM | FL_FLY)) != 0)
			return;

		if (ent.velocity[2] > 100) {
			ent.groundentity= null;
			return;
		}

		//	   if the hull point one-quarter unit down is solid the entity is on ground
		point[0]= ent.s.origin[0];
		point[1]= ent.s.origin[1];
		point[2]= ent.s.origin[2] - 0.25f;

		trace= gi.trace(ent.s.origin, ent.mins, ent.maxs, point, ent, MASK_MONSTERSOLID);

		// check steepness
		if (trace.plane.normal[2] < 0.7 && !trace.startsolid) {
			ent.groundentity= null;
			return;
		}

		//		ent.groundentity = trace.ent;
		//		ent.groundentity_linkcount = trace.ent.linkcount;
		//		if (!trace.startsolid && !trace.allsolid)
		//			VectorCopy (trace.endpos, ent.s.origin);
		if (!trace.startsolid && !trace.allsolid) {
			VectorCopy(trace.endpos, ent.s.origin);
			ent.groundentity= trace.ent;
			ent.groundentity_linkcount= trace.ent.linkcount;
			ent.velocity[2]= 0;
		}
	}

	/*
	=============
	M_CheckBottom
	
	Returns false if any part of the bottom of the entity is off an edge that
	is not a staircase.
	
	=============
	*/
	static int c_yes, c_no;

	static int STEPSIZE= 18;

	static boolean M_CheckBottom(edict_t ent) {
		float[] mins= { 0, 0, 0 };
		float[] maxs= { 0, 0, 0 };
		float[] start= { 0, 0, 0 };
		float[] stop= { 0, 0, 0 };

		trace_t trace;
		int x, y;
		float mid, bottom;

		VectorAdd(ent.s.origin, ent.mins, mins);
		VectorAdd(ent.s.origin, ent.maxs, maxs);

		//	   if all of the points under the corners are solid world, don't bother
		//	   with the tougher checks
		//	   the corners must be within 16 of the midpoint
		start[2]= mins[2] - 1;
		for (x= 0; x <= 1; x++)
			for (y= 0; y <= 1; y++) {
				start[0]= x != 0 ? maxs[0] : mins[0];
				start[1]= y != 0 ? maxs[1] : mins[1];
				if (gi.pointcontents(start) != CONTENTS_SOLID) {
					c_no++;
					//
					//	   check it for real...
					//
					start[2]= mins[2];

					//	   the midpoint must be within 16 of the bottom
					start[0]= stop[0]= (mins[0] + maxs[0]) * 0.5f;
					start[1]= stop[1]= (mins[1] + maxs[1]) * 0.5f;
					stop[2]= start[2] - 2 * STEPSIZE;
					trace= gi.trace(start, vec3_origin, vec3_origin, stop, ent, MASK_MONSTERSOLID);

					if (trace.fraction == 1.0)
						return false;
					mid= bottom= trace.endpos[2];

					//	   the corners must be within 16 of the midpoint	
					for (x= 0; x <= 1; x++)
						for (y= 0; y <= 1; y++) {
							start[0]= stop[0]= x != 0 ? maxs[0] : mins[0];
							start[1]= stop[1]= y != 0 ? maxs[1] : mins[1];

							trace=
								gi.trace(
									start,
									vec3_origin,
									vec3_origin,
									stop,
									ent,
									MASK_MONSTERSOLID);

							if (trace.fraction != 1.0 && trace.endpos[2] > bottom)
								bottom= trace.endpos[2];
							if (trace.fraction == 1.0 || mid - trace.endpos[2] > STEPSIZE)
								return false;
						}

					c_yes++;
					return true;
				}
			}

		c_yes++;
		return true; // we got out easy
	}

	static void SV_Physics_Step(edict_t ent) {
		boolean wasonground;
		boolean hitsound= false;
		float vel[];
		float speed, newspeed, control;
		float friction;
		edict_t groundentity;
		int mask;

		// airborn monsters should always check for ground
		if (ent.groundentity == null)
			M_CheckGround(ent);

		groundentity= ent.groundentity;

		SV_CheckVelocity(ent);

		if (groundentity != null)
			wasonground= true;
		else
			wasonground= false;

		if (ent.avelocity[0] != 0 || ent.avelocity[1] != 0 || ent.avelocity[2] != 0)
			SV_AddRotationalFriction(ent);

		// add gravity except:
		//   flying monsters
		//   swimming monsters who are in the water
		if (!wasonground)
			if (0 == (ent.flags & FL_FLY))
				if (!((ent.flags & FL_SWIM) != 0 && (ent.waterlevel > 2))) {
					if (ent.velocity[2] < sv_gravity.value * -0.1)
						hitsound= true;
					if (ent.waterlevel == 0)
						SV_AddGravity(ent);
				}

		// friction for flying monsters that have been given vertical velocity
		if ((ent.flags & FL_FLY) != 0 && (ent.velocity[2] != 0)) {
			speed= Math.abs(ent.velocity[2]);
			control= speed < sv_stopspeed ? sv_stopspeed : speed;
			friction= sv_friction / 3;
			newspeed= speed - (FRAMETIME * control * friction);
			if (newspeed < 0)
				newspeed= 0;
			newspeed /= speed;
			ent.velocity[2] *= newspeed;
		}

		// friction for flying monsters that have been given vertical velocity
		if ((ent.flags & FL_SWIM) != 0 && (ent.velocity[2] != 0)) {
			speed= Math.abs(ent.velocity[2]);
			control= speed < sv_stopspeed ? sv_stopspeed : speed;
			newspeed= speed - (FRAMETIME * control * sv_waterfriction * ent.waterlevel);
			if (newspeed < 0)
				newspeed= 0;
			newspeed /= speed;
			ent.velocity[2] *= newspeed;
		}

		if (ent.velocity[2] != 0 || ent.velocity[1] != 0 || ent.velocity[0] != 0) {
			// apply friction
			// let dead monsters who aren't completely onground slide
			if ((wasonground) || 0 != (ent.flags & (FL_SWIM | FL_FLY)))
				if (!(ent.health <= 0.0 && !M_CheckBottom(ent))) {
					vel= ent.velocity;
					speed= (float) Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1]);
					if (speed != 0) {
						friction= sv_friction;

						control= speed < sv_stopspeed ? sv_stopspeed : speed;
						newspeed= speed - FRAMETIME * control * friction;

						if (newspeed < 0)
							newspeed= 0;
						newspeed /= speed;

						vel[0] *= newspeed;
						vel[1] *= newspeed;
					}
				}

			if ((ent.svflags & SVF_MONSTER) != 0)
				mask= MASK_MONSTERSOLID;
			else
				mask= MASK_SOLID;

			SV_FlyMove(ent, FRAMETIME, mask);

			gi.linkentity(ent);
			G_TouchTriggers(ent);
			if (!ent.inuse)
				return;

			if (ent.groundentity != null)
				if (!wasonground)
					if (hitsound)
						gi.sound(ent, 0, gi.soundindex("world/land.wav"), 1, 1, 0);
		}

		//	   regular thinking
		SV_RunThink(ent);
	}

	//	  ============================================================================
	/*
	================
	G_RunEntity
	
	================
	*/
	static void G_RunEntity(edict_t ent) {
		if (ent.prethink != null)
			ent.prethink.think(ent);

		switch ((int) ent.movetype) {
			case MOVETYPE_PUSH :
			case MOVETYPE_STOP :
				SV_Physics_Pusher(ent);
				break;
			case MOVETYPE_NONE :
				SV_Physics_None(ent);
				break;
			case MOVETYPE_NOCLIP :
				SV_Physics_Noclip(ent);
				break;
			case MOVETYPE_STEP :
				SV_Physics_Step(ent);
				break;
			case MOVETYPE_TOSS :
			case MOVETYPE_BOUNCE :
			case MOVETYPE_FLY :
			case MOVETYPE_FLYMISSILE :
				SV_Physics_Toss(ent);
				break;
			default :
				gi.error("SV_Physics: bad movetype " + (int) ent.movetype);
		}
	}

	/*
	=============
	SV_movestep
	
	Called by monster program code.
	The move will be adjusted for slopes and stairs, but if the move isn't
	possible, no move is done, false is returned, and
	pr_global_struct.trace_normal is set to the normal of the blocking wall
	=============
	*/
	//	  FIXME since we need to test end position contents here, can we avoid doing
	//	  it again later in catagorize position?
	static boolean SV_movestep(edict_t ent, float[] move, boolean relink) {
		float dz;
		float[] oldorg= { 0, 0, 0 };
		float[] neworg= { 0, 0, 0 };
		float[] end= { 0, 0, 0 };

		trace_t trace= new trace_t();
		int i;
		float stepsize;
		float[] test= { 0, 0, 0 };
		int contents;

		//	   try the move	
		VectorCopy(ent.s.origin, oldorg);
		VectorAdd(ent.s.origin, move, neworg);

		//	   flying monsters don't step up
		if ((ent.flags & (FL_SWIM | FL_FLY)) != 0) {
			// try one move with vertical motion, then one without
			for (i= 0; i < 2; i++) {
				VectorAdd(ent.s.origin, move, neworg);
				if (i == 0 && ent.enemy != null) {
					if (ent.goalentity == null)
						ent.goalentity= ent.enemy;
					dz= ent.s.origin[2] - ent.goalentity.s.origin[2];
					if (ent.goalentity.client != null) {
						if (dz > 40)
							neworg[2] -= 8;
						if (!((ent.flags & FL_SWIM) != 0 && (ent.waterlevel < 2)))
							if (dz < 30)
								neworg[2] += 8;
					} else {
						if (dz > 8)
							neworg[2] -= 8;
						else if (dz > 0)
							neworg[2] -= dz;
						else if (dz < -8)
							neworg[2] += 8;
						else
							neworg[2] += dz;
					}
				}
				trace= gi.trace(ent.s.origin, ent.mins, ent.maxs, neworg, ent, MASK_MONSTERSOLID);

				// fly monsters don't enter water voluntarily
				if ((ent.flags & FL_FLY) != 0) {
					if (ent.waterlevel == 0) {
						test[0]= trace.endpos[0];
						test[1]= trace.endpos[1];
						test[2]= trace.endpos[2] + ent.mins[2] + 1;
						contents= gi.pointcontents(test);
						if ((contents & MASK_WATER) != 0)
							return false;
					}
				}

				// swim monsters don't exit water voluntarily
				if ((ent.flags & FL_SWIM) != 0) {
					if (ent.waterlevel < 2) {
						test[0]= trace.endpos[0];
						test[1]= trace.endpos[1];
						test[2]= trace.endpos[2] + ent.mins[2] + 1;
						contents= gi.pointcontents(test);
						if ((contents & MASK_WATER) == 0)
							return false;
					}
				}

				if (trace.fraction == 1) {
					VectorCopy(trace.endpos, ent.s.origin);
					if (relink) {
						gi.linkentity(ent);
						G_TouchTriggers(ent);
					}
					return true;
				}

				if (ent.enemy == null)
					break;
			}

			return false;
		}

		//	   push down from a step height above the wished position
		if ((ent.monsterinfo.aiflags & AI_NOSTEP) == 0)
			stepsize= STEPSIZE;
		else
			stepsize= 1;

		neworg[2] += stepsize;
		VectorCopy(neworg, end);
		end[2] -= stepsize * 2;

		trace= gi.trace(neworg, ent.mins, ent.maxs, end, ent, MASK_MONSTERSOLID);

		if (trace.allsolid)
			return false;

		if (trace.startsolid) {
			neworg[2] -= stepsize;
			trace= gi.trace(neworg, ent.mins, ent.maxs, end, ent, MASK_MONSTERSOLID);
			if (trace.allsolid || trace.startsolid)
				return false;
		}

		// don't go in to water
		if (ent.waterlevel == 0) {
			test[0]= trace.endpos[0];
			test[1]= trace.endpos[1];
			test[2]= trace.endpos[2] + ent.mins[2] + 1;
			contents= gi.pointcontents(test);

			if ((contents & MASK_WATER) != 0)
				return false;
		}

		if (trace.fraction == 1) {
			// if monster had the ground pulled out, go ahead and fall
			if ((ent.flags & FL_PARTIALGROUND) != 0) {
				VectorAdd(ent.s.origin, move, ent.s.origin);
				if (relink) {
					gi.linkentity(ent);
					G_TouchTriggers(ent);
				}
				ent.groundentity= null;
				return true;
			}

			return false; // walked off an edge
		}

		//	   check point traces down for dangling corners
		VectorCopy(trace.endpos, ent.s.origin);

		if (!M_CheckBottom(ent)) {
			if ((ent.flags & FL_PARTIALGROUND) != 0) {
				// entity had floor mostly pulled out from underneath it
				// and is trying to correct
				if (relink) {
					gi.linkentity(ent);
					G_TouchTriggers(ent);
				}
				return true;
			}
			VectorCopy(oldorg, ent.s.origin);
			return false;
		}

		if ((ent.flags & FL_PARTIALGROUND) != 0) {
			ent.flags &= ~FL_PARTIALGROUND;
		}
		ent.groundentity= trace.ent;
		ent.groundentity_linkcount= trace.ent.linkcount;

		//	   the move is ok
		if (relink) {
			gi.linkentity(ent);
			G_TouchTriggers(ent);
		}
		return true;
	}

	/*
	===============
	M_ChangeYaw
	
	===============
	*/
	static void M_ChangeYaw(edict_t ent) {
		float ideal;
		float current;
		float move;
		float speed;

		current= anglemod(ent.s.angles[YAW]);
		ideal= ent.ideal_yaw;

		if (current == ideal)
			return;

		move= ideal - current;
		speed= ent.yaw_speed;
		if (ideal > current) {
			if (move >= 180)
				move= move - 360;
		} else {
			if (move <= -180)
				move= move + 360;
		}
		if (move > 0) {
			if (move > speed)
				move= speed;
		} else {
			if (move < -speed)
				move= -speed;
		}

		ent.s.angles[YAW]= anglemod(current + move);
	}

	/*
	======================
	SV_StepDirection
	
	Turns to the movement direction, and walks the current distance if
	facing it.
	
	======================
	*/
	static boolean SV_StepDirection(edict_t ent, float yaw, float dist) {
		float[] move= { 0, 0, 0 };
		float[] oldorigin= { 0, 0, 0 };
		float delta;

		ent.ideal_yaw= yaw;
		M_ChangeYaw(ent);

		yaw= (float) (yaw * Math.PI * 2 / 360);
		move[0]= (float) Math.cos(yaw) * dist;
		move[1]= (float) Math.sin(yaw) * dist;
		move[2]= 0;

		VectorCopy(ent.s.origin, oldorigin);
		if (SV_movestep(ent, move, false)) {
			delta= ent.s.angles[YAW] - ent.ideal_yaw;
			if (delta > 45 && delta < 315) { // not turned far enough, so don't take the step
				VectorCopy(oldorigin, ent.s.origin);
			}
			gi.linkentity(ent);
			G_TouchTriggers(ent);
			return true;
		}
		gi.linkentity(ent);
		G_TouchTriggers(ent);
		return false;
	}

	/*
	======================
	SV_FixCheckBottom
	
	======================
	*/
	static void SV_FixCheckBottom(edict_t ent) {
		ent.flags |= FL_PARTIALGROUND;
	}

	static short rand() {
		return (short) (Math.random() * 0x8000);
	}

	/*
	================
	SV_NewChaseDir
	
	================
	*/
	static int DI_NODIR= -1;
	static void SV_NewChaseDir(edict_t actor, edict_t enemy, float dist) {
		float deltax, deltay;
		float d[]= { 0, 0, 0 };
		float tdir, olddir, turnaround;

		//FIXME: how did we get here with no enemy
		if (enemy == null)
			return;

		olddir= anglemod((int) (actor.ideal_yaw / 45) * 45);
		turnaround= anglemod(olddir - 180);

		deltax= enemy.s.origin[0] - actor.s.origin[0];
		deltay= enemy.s.origin[1] - actor.s.origin[1];
		if (deltax > 10)
			d[1]= 0;
		else if (deltax < -10)
			d[1]= 180;
		else
			d[1]= DI_NODIR;
		if (deltay < -10)
			d[2]= 270;
		else if (deltay > 10)
			d[2]= 90;
		else
			d[2]= DI_NODIR;

		//	   try direct route
		if (d[1] != DI_NODIR && d[2] != DI_NODIR) {
			if (d[1] == 0)
				tdir= d[2] == 90 ? 45 : 315;
			else
				tdir= d[2] == 90 ? 135 : 215;

			if (tdir != turnaround && SV_StepDirection(actor, tdir, dist))
				return;
		}

		//	   try other directions
		if (((rand() & 3) & 1) != 0 || Math.abs(deltay) > Math.abs(deltax)) {
			tdir= d[1];
			d[1]= d[2];
			d[2]= tdir;
		}

		if (d[1] != DI_NODIR && d[1] != turnaround && SV_StepDirection(actor, d[1], dist))
			return;

		if (d[2] != DI_NODIR && d[2] != turnaround && SV_StepDirection(actor, d[2], dist))
			return;

		/* there is no direct path to the player, so pick another direction */

		if (olddir != DI_NODIR && SV_StepDirection(actor, olddir, dist))
			return;

		if ((rand() & 1) != 0) /*randomly determine direction of search*/ {
			for (tdir= 0; tdir <= 315; tdir += 45)
				if (tdir != turnaround && SV_StepDirection(actor, tdir, dist))
					return;
		} else {
			for (tdir= 315; tdir >= 0; tdir -= 45)
				if (tdir != turnaround && SV_StepDirection(actor, tdir, dist))
					return;
		}

		if (turnaround != DI_NODIR && SV_StepDirection(actor, turnaround, dist))
			return;

		actor.ideal_yaw= olddir; // can't move

		//	   if a bridge was pulled out from underneath a monster, it may not have
		//	   a valid standing position at all

		if (!M_CheckBottom(actor))
			SV_FixCheckBottom(actor);
	}

	/*
	======================
	SV_CloseEnough
	
	======================
	*/
	static boolean SV_CloseEnough(edict_t ent, edict_t goal, float dist) {
		int i;

		for (i= 0; i < 3; i++) {
			if (goal.absmin[i] > ent.absmax[i] + dist)
				return false;
			if (goal.absmax[i] < ent.absmin[i] - dist)
				return false;
		}
		return true;
	}

	/*
	======================
	M_MoveToGoal
	======================
	*/
	static void M_MoveToGoal(edict_t ent, float dist) {
		edict_t goal= ent.goalentity;

		if (ent.groundentity == null && (ent.flags & (FL_FLY | FL_SWIM)) == 0)
			return;

		//	   if the next step hits the enemy, return immediately
		if (ent.enemy != null && SV_CloseEnough(ent, ent.enemy, dist))
			return;

		//	   bump around...
		if ((rand() & 3) == 1 || !SV_StepDirection(ent, ent.ideal_yaw, dist)) {
			if (ent.inuse)
				SV_NewChaseDir(ent, goal, dist);
		}
	}

	/*
	===============
	M_walkmove
	===============
	*/
	static boolean M_walkmove(edict_t ent, float yaw, float dist) {
		float[] move= { 0, 0, 0 };

		if ((ent.groundentity == null) && (ent.flags & (FL_FLY | FL_SWIM)) == 0)
			return false;

		yaw= (float) (yaw * Math.PI * 2 / 360);

		move[0]= (float) Math.cos(yaw) * dist;
		move[1]= (float) Math.sin(yaw) * dist;
		move[2]= 0;

		return SV_movestep(ent, move, true);
	}

	//=====================================================================
	//monster
	//=====================================================================
	//player
	//=====================================================================

	// math
	//=====================================================================
	// these methods should run without touching. 

	static float DotProduct(float[] x, float[] y) {
		return x[0] * y[0] + x[1] * y[1] + x[2] * y[2];
	}

	static void VectorSubtract(float[] a, float[] b, float[] c) {
		c[0]= a[0] - b[0];
		c[1]= a[1] - b[1];
		c[2]= a[2] - b[2];
	}

	static void VectorAdd(float[] a, float[] b, float[] c) {
		c[0]= a[0] + b[0];
		c[1]= a[1] + b[1];
		c[2]= a[2] + b[2];
	}

	static void VectorCopy(float[] a, float[] b) {
		b[0]= a[0];
		b[1]= a[1];
		b[2]= a[2];
	}

	static void VectorClear(float[] a) {
		a[0]= a[1]= a[2]= 0;
	}

	static int VectorCompare(float[] v1, float[] v2) {
		if (v1[0] != v2[0] || v1[1] != v2[1] || v1[2] != v2[2])
			return 0;

		return 1;
	}

	static void VectorNegate(float[] a, float[] b) {
		b[0]= -a[0];
		b[1]= -a[1];
		b[2]= -a[2];
	}

	static void VectorSet(float[] v, float x, float y, float z) {
		v[0]= (x);
		v[1]= (y);
		v[2]= (z);
	}

	static void VectorMA(float[] veca, float scale, float[] vecb, float[] vecc) {
		vecc[0]= veca[0] + scale * vecb[0];
		vecc[1]= veca[1] + scale * vecb[1];
		vecc[2]= veca[2] + scale * vecb[2];
	}

	static float VectorNormalize(float[] v) {
		float length;

		length= VectorLength(v);
		if (length != 0.0f) {

			v[0] /= length;
			v[1] /= length;
			v[2] /= length;
		}
		return length;
	}

	static float VectorNormalize2(float[] v, float[] out) {
		float length, ilength;

		length= VectorLength(v);
		if (length != 0.0f) {
			out[0]= v[0] / length;
			out[1]= v[1] / length;
			out[2]= v[2] / length;
		}
		return length;
	}

	static float VectorLength(float v[]) {
		return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}

	static void VectorInverse(float[] v) {
		v[0]= -v[0];
		v[1]= -v[1];
		v[2]= -v[2];
	}

	static void VectorScale(float[] in, float scale, float[] out) {
		out[0]= in[0] * scale;
		out[1]= in[1] * scale;
		out[2]= in[2] * scale;
	}

	static int Q_log2(int val) {
		int answer= 0;
		while ((val >>= 1) > 0)
			answer++;
		return answer;
	}

	static void CrossProduct(float[] v1, float[] v2, float[] cross) {
		cross[0]= v1[1] * v2[2] - v1[2] * v2[1];
		cross[1]= v1[2] * v2[0] - v1[0] * v2[2];
		cross[2]= v1[0] * v2[1] - v1[1] * v2[0];
	}

	static void MatClear(float m[][]) {
		m[0][0]= m[0][1]= m[0][2]= m[1][0]= m[1][1]= m[1][2]= m[2][0]= m[2][1]= m[2][2]= 0.0f;
	}

	static void MatCopy(float src[][], float dst[][]) {
		dst[0][0]= src[0][0];
		dst[0][1]= src[0][1];
		dst[0][2]= src[0][2];

		dst[1][0]= src[1][0];
		dst[1][1]= src[1][1];
		dst[1][2]= src[1][2];

		dst[2][0]= src[2][0];
		dst[2][1]= src[2][1];
		dst[2][2]= src[2][2];
	}

	static void G_ProjectSource(
		float[] point,
		float[] distance,
		float[] forward,
		float[] right,
		float[] result) {
		result[0]= point[0] + forward[0] * distance[0] + right[0] * distance[1];
		result[1]= point[1] + forward[1] * distance[0] + right[1] * distance[1];
		result[2]= point[2] + forward[2] * distance[0] + right[2] * distance[1] + distance[2];
	}

	static void ProjectPointOnPlane(float[] dst, float[] p, float[] normal) {
		float d;
		float[] n= { 0.0f, 0.0f, 0.0f };
		float inv_denom;

		inv_denom= 1.0F / DotProduct(normal, normal);

		d= DotProduct(normal, p) * inv_denom;

		n[0]= normal[0] * inv_denom;
		n[1]= normal[1] * inv_denom;
		n[2]= normal[2] * inv_denom;

		dst[0]= p[0] - d * n[0];
		dst[1]= p[1] - d * n[1];
		dst[2]= p[2] - d * n[2];
	}

	static float DEG2RAD(float in) {
		return (in * (float) Math.PI) / 180.0f;
	}

	static float anglemod(float a) {
		return (float) (360.0 / 65536) * ((int) (a * (65536 / 360.0)) & 65535);
	}

	/** assumes "src" is normalized */
	static void PerpendicularVector(float[] dst, float[] src) {
		int pos;
		int i;
		float minelem= 1.0F;
		float tempvec[]= { 0.0f, 0.0f, 0.0f };

		// find the smallest magnitude axially aligned vector 
		for (pos= 0, i= 0; i < 3; i++) {
			if (Math.abs(src[i]) < minelem) {
				pos= i;
				minelem= Math.abs(src[i]);
			}
		}
		tempvec[0]= tempvec[1]= tempvec[2]= 0.0F;
		tempvec[pos]= 1.0F;

		// project the point onto the plane defined by src
		ProjectPointOnPlane(dst, tempvec, src);

		//normalize the result 
		VectorNormalize(dst);
	}

	static void AngleVectors(float[] angles, float[] forward, float[] right, float[] up) {
		float angle;
		float sr, sp, sy, cr, cp, cy;

		angle= (float) (angles[YAW] * (Math.PI * 2 / 360));
		sy= (float) Math.sin(angle);
		cy= (float) Math.cos(angle);
		angle= (float) (angles[PITCH] * (Math.PI * 2 / 360));
		sp= (float) Math.sin(angle);
		cp= (float) Math.cos(angle);
		angle= (float) (angles[ROLL] * (Math.PI * 2 / 360));
		sr= (float) Math.sin(angle);
		cr= (float) Math.cos(angle);

		if (forward != null) {
			forward[0]= cp * cy;
			forward[1]= cp * sy;
			forward[2]= -sp;
		}
		if (right != null) {
			right[0]= (-1 * sr * sp * cy + -1 * cr * -sy);
			right[1]= (-1 * sr * sp * sy + -1 * cr * cy);
			right[2]= -1 * sr * cp;
		}
		if (up != null) {
			up[0]= (cr * sp * cy + -sr * -sy);
			up[1]= (cr * sp * sy + -sr * cy);
			up[2]= cr * cp;
		}
	}

	/*
	================
	R_ConcatTransforms
	================
	*/
	static void R_ConcatTransforms(float in1[][], float in2[][], float out[][]) {
		out[0][0]= in1[0][0] * in2[0][0] + in1[0][1] * in2[1][0] + in1[0][2] * in2[2][0];
		out[0][1]= in1[0][0] * in2[0][1] + in1[0][1] * in2[1][1] + in1[0][2] * in2[2][1];
		out[0][2]= in1[0][0] * in2[0][2] + in1[0][1] * in2[1][2] + in1[0][2] * in2[2][2];
		out[0][3]=
			in1[0][0] * in2[0][3] + in1[0][1] * in2[1][3] + in1[0][2] * in2[2][3] + in1[0][3];
		out[1][0]= in1[1][0] * in2[0][0] + in1[1][1] * in2[1][0] + in1[1][2] * in2[2][0];
		out[1][1]= in1[1][0] * in2[0][1] + in1[1][1] * in2[1][1] + in1[1][2] * in2[2][1];
		out[1][2]= in1[1][0] * in2[0][2] + in1[1][1] * in2[1][2] + in1[1][2] * in2[2][2];
		out[1][3]=
			in1[1][0] * in2[0][3] + in1[1][1] * in2[1][3] + in1[1][2] * in2[2][3] + in1[1][3];
		out[2][0]= in1[2][0] * in2[0][0] + in1[2][1] * in2[1][0] + in1[2][2] * in2[2][0];
		out[2][1]= in1[2][0] * in2[0][1] + in1[2][1] * in2[1][1] + in1[2][2] * in2[2][1];
		out[2][2]= in1[2][0] * in2[0][2] + in1[2][1] * in2[1][2] + in1[2][2] * in2[2][2];
		out[2][3]=
			in1[2][0] * in2[0][3] + in1[2][1] * in2[1][3] + in1[2][2] * in2[2][3] + in1[2][3];
	}

	static void RotatePointAroundVector(float[] dst, float[] dir, float[] point, float degrees) {
		float m[][]= new float[3][3];
		float im[][]= new float[3][3];
		float zrot[][]= new float[3][3];
		float tmpmat[][]= new float[3][3];
		float rot[][]= new float[3][3];
		int i;
		float[] vr= { 0.0f, 0.0f, 0.0f };
		float[] vup= { 0.0f, 0.0f, 0.0f };
		float[] vf= { 0.0f, 0.0f, 0.0f };

		vf[0]= dir[0];
		vf[1]= dir[1];
		vf[2]= dir[2];

		PerpendicularVector(vr, dir);
		CrossProduct(vr, vf, vup);

		m[0][0]= vr[0];
		m[1][0]= vr[1];
		m[2][0]= vr[2];

		m[0][1]= vup[0];
		m[1][1]= vup[1];
		m[2][1]= vup[2];

		m[0][2]= vf[0];
		m[1][2]= vf[1];
		m[2][2]= vf[2];

		MatCopy(im, m);

		im[0][1]= m[1][0];
		im[0][2]= m[2][0];
		im[1][0]= m[0][1];
		im[1][2]= m[2][1];
		im[2][0]= m[0][2];
		im[2][1]= m[1][2];

		MatClear(zrot);

		zrot[0][0]= zrot[1][1]= zrot[2][2]= 1.0F;

		zrot[0][0]= (float) Math.cos(DEG2RAD(degrees));
		zrot[0][1]= (float) Math.sin(DEG2RAD(degrees));
		zrot[1][0]= - (float) Math.sin(DEG2RAD(degrees));
		zrot[1][1]= (float) Math.cos(DEG2RAD(degrees));

		R_ConcatRotations(m, zrot, tmpmat);
		R_ConcatRotations(tmpmat, im, rot);

		for (i= 0; i < 3; i++) {
			dst[i]= rot[i][0] * point[0] + rot[i][1] * point[1] + rot[i][2] * point[2];
		}
	}

	/**
	 * concatenates 2 matrices each [3][3].
	 */
	static void R_ConcatRotations(float in1[][], float in2[][], float out[][]) {
		out[0][0]= in1[0][0] * in2[0][0] + in1[0][1] * in2[1][0] + in1[0][2] * in2[2][0];
		out[0][1]= in1[0][0] * in2[0][1] + in1[0][1] * in2[1][1] + in1[0][2] * in2[2][1];
		out[0][2]= in1[0][0] * in2[0][2] + in1[0][1] * in2[1][2] + in1[0][2] * in2[2][2];
		out[1][0]= in1[1][0] * in2[0][0] + in1[1][1] * in2[1][0] + in1[1][2] * in2[2][0];
		out[1][1]= in1[1][0] * in2[0][1] + in1[1][1] * in2[1][1] + in1[1][2] * in2[2][1];
		out[1][2]= in1[1][0] * in2[0][2] + in1[1][1] * in2[1][2] + in1[1][2] * in2[2][2];
		out[2][0]= in1[2][0] * in2[0][0] + in1[2][1] * in2[1][0] + in1[2][2] * in2[2][0];
		out[2][1]= in1[2][0] * in2[0][1] + in1[2][1] * in2[1][1] + in1[2][2] * in2[2][1];
		out[2][2]= in1[2][0] * in2[0][2] + in1[2][1] * in2[1][2] + in1[2][2] * in2[2][2];
	}

	static float LerpAngle(float a2, float a1, float frac) {
		if (a1 - a2 > 180)
			a1 -= 360;
		if (a1 - a2 < -180)
			a1 += 360;
		return a2 + frac * (a1 - a2);
	}

	static void assert1(boolean cond) {
		if (!cond) {

			try {

				int a[]= null;
				int b= a[0];
			} catch (Exception e) {
				System.err.println("assertion failed!");
				e.printStackTrace();
			}

		}
	}

	//=====================================================================	
	/** 
	 stellt fest, auf welcher Seite sich die Kiste befindet, wenn die Ebene 
	 durch Entfernung und Senkrechten-Normale gegeben ist.    
	 erste Version mit v ec 3_t... */

	static int BoxOnPlaneSide(float emins[], float emaxs[], cplane_t p) {
		float dist1, dist2;
		int sides;

		//	   fast axial cases
		if (p.type < 3) {
			if (p.dist <= emins[p.type])
				return 1;
			if (p.dist >= emaxs[p.type])
				return 2;
			return 3;
		}

		//	   general case
		switch (p.signbits) {
			case 0 :
				dist1= p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				dist2= p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				break;
			case 1 :
				dist1= p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				dist2= p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				break;
			case 2 :
				dist1= p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				dist2= p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				break;
			case 3 :
				dist1= p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				dist2= p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				break;
			case 4 :
				dist1= p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				dist2= p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				break;
			case 5 :
				dist1= p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				dist2= p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				break;
			case 6 :
				dist1= p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				dist2= p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				break;
			case 7 :
				dist1= p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				dist2= p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				break;
			default :
				//TODO: error message.
				dist1= dist2= 0;

				break;
		}

		sides= 0;
		if (dist1 >= p.dist)
			sides= 1;
		if (dist2 < p.dist)
			sides |= 2;

		assert1(sides != 0);

		return sides;
	}

	//	this is the slow, general version
	static int BoxOnPlaneSide2(float[] emins, float[] emaxs, cplane_t p) {
		int i;
		float dist1, dist2;
		int sides;
		float corners[][]= new float[3][2];

		for (i= 0; i < 3; i++) {
			if (p.normal[i] < 0) {
				corners[0][i]= emins[i];
				corners[1][i]= emaxs[i];
			} else {
				corners[1][i]= emins[i];
				corners[0][i]= emaxs[i];
			}
		}
		dist1= DotProduct(p.normal, corners[0]) - p.dist;
		dist2= DotProduct(p.normal, corners[1]) - p.dist;
		sides= 0;
		if (dist1 >= 0)
			sides= 1;
		if (dist2 < 0)
			sides |= 2;

		return sides;
	}

	static void ClearBounds(float[] mins, float[] maxs) {
		mins[0]= mins[1]= mins[2]= 99999;
		maxs[0]= maxs[1]= maxs[2]= -99999;
	}

	static void AddPointToBounds(float[] v, float[] mins, float[] maxs) {
		int i;
		float val;

		for (i= 0; i < 3; i++) {
			val= v[i];
			if (val < mins[i])
				mins[i]= val;
			if (val > maxs[i])
				maxs[i]= val;
		}
	}

	static EdictFindFilter findByTarget= new EdictFindFilter() {
		public boolean matches(edict_t e, String s) {
			return e.targetname.equalsIgnoreCase(s);
		}
	};

	static float crandom() {
		return (float) (Math.random() - 0.5) * 2.0f;
	}

	static float random() {
		return (float) Math.random();
	}

	static int ANGLE2SHORT(float x) {
		return ((int) ((x) * 65536 / 360) & 65535);
	}

	static float SHORT2ANGLE(int x) {
		return ((x) * (360.0f / 65536));
	}

	//TODO: delete this and clean up quake.
	static int strcmp(String in1, String in2) {
		return in1.compareTo(in2);
	}
	
	static int stricmp(String in1, String in2) {
		return in1.compareToIgnoreCase(in2);
	}
	
	static int Q_stricmp(String in1, String in2) {
		return in1.compareToIgnoreCase(in2);
	}

	//TODO: delete this and clean up quake.	
	static int strncmp(String in1, String in2, int len) {
		int i1= Math.min(len, in1.length());
		int i2= Math.min(len, in2.length());

		if (i1 < i2)
			return -1;
		if (i1 > i2)
			return 1;

		for (int n= 0; n < i1; n++) {
			char c1= in1.charAt(n);
			char c2= in1.charAt(n);
			if (c1 < c2)
				return -1;
			if (c1 > c2)
				return 1;
		}
		return 0;
	}
}
