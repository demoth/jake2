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
// $Id: GameBase.java,v 1.7 2003-12-04 21:04:35 rst Exp $

/** Father of all Objects. */

package jake2.game;

import jake2.*;
import jake2.*;
import jake2.client.M;
import jake2.server.SV;

public class GameBase extends Globals {
	public static game_locals_t game;
	public static level_locals_t level;
	public static game_import_t gi;
	public static game_export_t globals;
	public static spawn_temp_t st;

	public static int sm_meat_index;
	public static int snd_fry;
	public static int meansOfDeath;

	public static edict_t g_edicts[];

	public static cvar_t deathmatch;
	public static cvar_t coop;
	public static cvar_t dmflags;
	public static cvar_t skill;
	public static cvar_t fraglimit;
	public static cvar_t timelimit;
	public static cvar_t password;
	public static cvar_t spectator_password;
	public static cvar_t needpass;
	public static cvar_t maxclients;
	public static cvar_t maxspectators;
	public static cvar_t maxentities;
	public static cvar_t g_select_empty;
	public static cvar_t dedicated;

	public static cvar_t filterban;

	public static cvar_t sv_maxvelocity;
	public static cvar_t sv_gravity;

	public static cvar_t sv_rollspeed;
	public static cvar_t sv_rollangle;
	public static cvar_t gun_x;
	public static cvar_t gun_y;
	public static cvar_t gun_z;

	public static cvar_t run_pitch;
	public static cvar_t run_roll;
	public static cvar_t bob_up;
	public static cvar_t bob_pitch;
	public static cvar_t bob_roll;

	public static cvar_t sv_cheats;

	public static cvar_t flood_msgs;
	public static cvar_t flood_persecond;
	public static cvar_t flood_waitdelay;

	public static cvar_t sv_maplist;

	public final static float STOP_EPSILON = 0.1f;

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

		backoff = DotProduct(in, normal) * overbounce;

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
		return null;
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

		return choice[rand() % num_choices];
	}

	/*
	=============
	TempVector
	
	This is just a convenience function
	for making temporary vectors for function calls
	=============
	*/
	public static float tv_vecs[][] = new float[8][3];
	public static int tv_index;

	public static float[] tv(float x, float y, float z) {

		float[] v;

		// use an array so that multiple tempvectors won't collide
		// for a while
		v = tv_vecs[tv_index];
		tv_index = (tv_index++) & 7;

		v[0] = x;
		v[1] = y;
		v[2] = z;

		return v;
	}

	/*
	=============
	VectorToString
	
	This is just a convenience function
	for printing vectors
	=============
	*/
	public static String vtos(float[] v) {
		return "(" + (int) v[0] + " " + (int) v[1] + " " + (int) v[2] + ")";
	}

	public static float[] VEC_UP = { 0, -1, 0 };
	public static float[] MOVEDIR_UP = { 0, 0, 1 };
	public static float[] VEC_DOWN = { 0, -2, 0 };
	public static float[] MOVEDIR_DOWN = { 0, 0, -1 };

	public static void G_SetMovedir(float[] angles, float[] movedir) {
		if (VectorCompare(angles, VEC_UP) != 0) {
			VectorCopy(MOVEDIR_UP, movedir);
		} else if (VectorCompare(angles, VEC_DOWN) != 0) {
			VectorCopy(MOVEDIR_DOWN, movedir);
		} else {
			AngleVectors(angles, movedir, null, null);
		}

		VectorClear(angles);
	}

	public static float vectoyaw(float[] vec) {
		float yaw;

		if (/*vec[YAW] == 0 &&*/
			vec[PITCH] == 0) {
			yaw = 0;
			if (vec[YAW] > 0)
				yaw = 90;
			else if (vec[YAW] < 0)
				yaw = -90;
		} else {

			yaw = (int) (Math.atan2(vec[YAW], vec[PITCH]) * 180 / Math.PI);
			if (yaw < 0)
				yaw += 360;
		}

		return yaw;
	}

	public static void vectoangles(float[] value1, float[] angles) {
		float forward;
		float yaw, pitch;

		if (value1[1] == 0 && value1[0] == 0) {
			yaw = 0;
			if (value1[2] > 0)
				pitch = 90;
			else
				pitch = 270;
		} else {
			if (value1[0] != 0)
				yaw = (int) (Math.atan2(value1[1], value1[0]) * 180 / Math.PI);
			else if (value1[1] > 0)
				yaw = 90;
			else
				yaw = -90;
			if (yaw < 0)
				yaw += 360;

			forward = (float) Math.sqrt(value1[0] * value1[0] + value1[1] * value1[1]);
			pitch = (int) (Math.atan2(value1[2], forward) * 180 / Math.PI);
			if (pitch < 0)
				pitch += 360;
		}

		angles[PITCH] = -pitch;
		angles[YAW] = yaw;
		angles[ROLL] = 0;
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

	public static short rand() {
		return (short) (Math.random() * 0x8000);
	}

	/*
	================
	SV_NewChaseDir
	
	================
	*/
	public static int DI_NODIR = -1;
	//=====================================================================
	//monster
	//=====================================================================
	//player
	//=====================================================================

	// math
	//=====================================================================
	// these methods should run without touching. 

	public static float DotProduct(float[] x, float[] y) {
		return x[0] * y[0] + x[1] * y[1] + x[2] * y[2];
	}

	public static void VectorSubtract(float[] a, float[] b, float[] c) {
		c[0] = a[0] - b[0];
		c[1] = a[1] - b[1];
		c[2] = a[2] - b[2];
	}

	public static void VectorAdd(float[] a, float[] b, float[] c) {
		c[0] = a[0] + b[0];
		c[1] = a[1] + b[1];
		c[2] = a[2] + b[2];
	}

	public static void VectorCopy(float[] a, float[] b) {
		b[0] = a[0];
		b[1] = a[1];
		b[2] = a[2];
	}

	public static void VectorClear(float[] a) {
		a[0] = a[1] = a[2] = 0;
	}

	public static int VectorCompare(float[] v1, float[] v2) {
		if (v1[0] != v2[0] || v1[1] != v2[1] || v1[2] != v2[2])
			return 0;

		return 1;
	}

	public static void VectorNegate(float[] a, float[] b) {
		b[0] = -a[0];
		b[1] = -a[1];
		b[2] = -a[2];
	}

	public static void VectorSet(float[] v, float x, float y, float z) {
		v[0] = (x);
		v[1] = (y);
		v[2] = (z);
	}

	public static void VectorMA(float[] veca, float scale, float[] vecb, float[] vecc) {
		vecc[0] = veca[0] + scale * vecb[0];
		vecc[1] = veca[1] + scale * vecb[1];
		vecc[2] = veca[2] + scale * vecb[2];
	}

	public static float VectorNormalize(float[] v) {
		float length;

		length = VectorLength(v);
		if (length != 0.0f) {

			v[0] /= length;
			v[1] /= length;
			v[2] /= length;
		}
		return length;
	}

	public static float VectorNormalize2(float[] v, float[] out) {
		float length, ilength;

		length = VectorLength(v);
		if (length != 0.0f) {
			out[0] = v[0] / length;
			out[1] = v[1] / length;
			out[2] = v[2] / length;
		}
		return length;
	}

	public static float VectorLength(float v[]) {
		return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}

	public static void VectorInverse(float[] v) {
		v[0] = -v[0];
		v[1] = -v[1];
		v[2] = -v[2];
	}

	public static void VectorScale(float[] in, float scale, float[] out) {
		out[0] = in[0] * scale;
		out[1] = in[1] * scale;
		out[2] = in[2] * scale;
	}

	public static int Q_log2(int val) {
		int answer = 0;
		while ((val >>= 1) > 0)
			answer++;
		return answer;
	}

	public static void CrossProduct(float[] v1, float[] v2, float[] cross) {
		cross[0] = v1[1] * v2[2] - v1[2] * v2[1];
		cross[1] = v1[2] * v2[0] - v1[0] * v2[2];
		cross[2] = v1[0] * v2[1] - v1[1] * v2[0];
	}

	public static void MatClear(float m[][]) {
		m[0][0] = m[0][1] = m[0][2] = m[1][0] = m[1][1] = m[1][2] = m[2][0] = m[2][1] = m[2][2] = 0.0f;
	}

	public static void MatCopy(float src[][], float dst[][]) {
		dst[0][0] = src[0][0];
		dst[0][1] = src[0][1];
		dst[0][2] = src[0][2];

		dst[1][0] = src[1][0];
		dst[1][1] = src[1][1];
		dst[1][2] = src[1][2];

		dst[2][0] = src[2][0];
		dst[2][1] = src[2][1];
		dst[2][2] = src[2][2];
	}

	public static void G_ProjectSource(float[] point, float[] distance, float[] forward, float[] right, float[] result) {
		result[0] = point[0] + forward[0] * distance[0] + right[0] * distance[1];
		result[1] = point[1] + forward[1] * distance[0] + right[1] * distance[1];
		result[2] = point[2] + forward[2] * distance[0] + right[2] * distance[1] + distance[2];
	}

	public static void ProjectPointOnPlane(float[] dst, float[] p, float[] normal) {
		float d;
		float[] n = { 0.0f, 0.0f, 0.0f };
		float inv_denom;

		inv_denom = 1.0F / DotProduct(normal, normal);

		d = DotProduct(normal, p) * inv_denom;

		n[0] = normal[0] * inv_denom;
		n[1] = normal[1] * inv_denom;
		n[2] = normal[2] * inv_denom;

		dst[0] = p[0] - d * n[0];
		dst[1] = p[1] - d * n[1];
		dst[2] = p[2] - d * n[2];
	}

	public static float DEG2RAD(float in) {
		return (in * (float) Math.PI) / 180.0f;
	}

	public static float anglemod(float a) {
		return (float) (360.0 / 65536) * ((int) (a * (65536 / 360.0)) & 65535);
	}

	/** assumes "src" is normalized */
	public static void PerpendicularVector(float[] dst, float[] src) {
		int pos;
		int i;
		float minelem = 1.0F;
		float tempvec[] = { 0.0f, 0.0f, 0.0f };

		// find the smallest magnitude axially aligned vector 
		for (pos = 0, i = 0; i < 3; i++) {
			if (Math.abs(src[i]) < minelem) {
				pos = i;
				minelem = Math.abs(src[i]);
			}
		}
		tempvec[0] = tempvec[1] = tempvec[2] = 0.0F;
		tempvec[pos] = 1.0F;

		// project the point onto the plane defined by src
		ProjectPointOnPlane(dst, tempvec, src);

		//normalize the result 
		VectorNormalize(dst);
	}

	public static void AngleVectors(float[] angles, float[] forward, float[] right, float[] up) {
		float angle;
		float sr, sp, sy, cr, cp, cy;

		angle = (float) (angles[YAW] * (Math.PI * 2 / 360));
		sy = (float) Math.sin(angle);
		cy = (float) Math.cos(angle);
		angle = (float) (angles[PITCH] * (Math.PI * 2 / 360));
		sp = (float) Math.sin(angle);
		cp = (float) Math.cos(angle);
		angle = (float) (angles[ROLL] * (Math.PI * 2 / 360));
		sr = (float) Math.sin(angle);
		cr = (float) Math.cos(angle);

		if (forward != null) {
			forward[0] = cp * cy;
			forward[1] = cp * sy;
			forward[2] = -sp;
		}
		if (right != null) {
			right[0] = (-1 * sr * sp * cy + -1 * cr * -sy);
			right[1] = (-1 * sr * sp * sy + -1 * cr * cy);
			right[2] = -1 * sr * cp;
		}
		if (up != null) {
			up[0] = (cr * sp * cy + -sr * -sy);
			up[1] = (cr * sp * sy + -sr * cy);
			up[2] = cr * cp;
		}
	}

	/*
	================
	R_ConcatTransforms
	================
	*/
	public static void R_ConcatTransforms(float in1[][], float in2[][], float out[][]) {
		out[0][0] = in1[0][0] * in2[0][0] + in1[0][1] * in2[1][0] + in1[0][2] * in2[2][0];
		out[0][1] = in1[0][0] * in2[0][1] + in1[0][1] * in2[1][1] + in1[0][2] * in2[2][1];
		out[0][2] = in1[0][0] * in2[0][2] + in1[0][1] * in2[1][2] + in1[0][2] * in2[2][2];
		out[0][3] = in1[0][0] * in2[0][3] + in1[0][1] * in2[1][3] + in1[0][2] * in2[2][3] + in1[0][3];
		out[1][0] = in1[1][0] * in2[0][0] + in1[1][1] * in2[1][0] + in1[1][2] * in2[2][0];
		out[1][1] = in1[1][0] * in2[0][1] + in1[1][1] * in2[1][1] + in1[1][2] * in2[2][1];
		out[1][2] = in1[1][0] * in2[0][2] + in1[1][1] * in2[1][2] + in1[1][2] * in2[2][2];
		out[1][3] = in1[1][0] * in2[0][3] + in1[1][1] * in2[1][3] + in1[1][2] * in2[2][3] + in1[1][3];
		out[2][0] = in1[2][0] * in2[0][0] + in1[2][1] * in2[1][0] + in1[2][2] * in2[2][0];
		out[2][1] = in1[2][0] * in2[0][1] + in1[2][1] * in2[1][1] + in1[2][2] * in2[2][1];
		out[2][2] = in1[2][0] * in2[0][2] + in1[2][1] * in2[1][2] + in1[2][2] * in2[2][2];
		out[2][3] = in1[2][0] * in2[0][3] + in1[2][1] * in2[1][3] + in1[2][2] * in2[2][3] + in1[2][3];
	}

	public static void RotatePointAroundVector(float[] dst, float[] dir, float[] point, float degrees) {
		float m[][] = new float[3][3];
		float im[][] = new float[3][3];
		float zrot[][] = new float[3][3];
		float tmpmat[][] = new float[3][3];
		float rot[][] = new float[3][3];
		int i;
		float[] vr = { 0.0f, 0.0f, 0.0f };
		float[] vup = { 0.0f, 0.0f, 0.0f };
		float[] vf = { 0.0f, 0.0f, 0.0f };

		vf[0] = dir[0];
		vf[1] = dir[1];
		vf[2] = dir[2];

		PerpendicularVector(vr, dir);
		CrossProduct(vr, vf, vup);

		m[0][0] = vr[0];
		m[1][0] = vr[1];
		m[2][0] = vr[2];

		m[0][1] = vup[0];
		m[1][1] = vup[1];
		m[2][1] = vup[2];

		m[0][2] = vf[0];
		m[1][2] = vf[1];
		m[2][2] = vf[2];

		MatCopy(im, m);

		im[0][1] = m[1][0];
		im[0][2] = m[2][0];
		im[1][0] = m[0][1];
		im[1][2] = m[2][1];
		im[2][0] = m[0][2];
		im[2][1] = m[1][2];

		MatClear(zrot);

		zrot[0][0] = zrot[1][1] = zrot[2][2] = 1.0F;

		zrot[0][0] = (float) Math.cos(DEG2RAD(degrees));
		zrot[0][1] = (float) Math.sin(DEG2RAD(degrees));
		zrot[1][0] = - (float) Math.sin(DEG2RAD(degrees));
		zrot[1][1] = (float) Math.cos(DEG2RAD(degrees));

		R_ConcatRotations(m, zrot, tmpmat);
		R_ConcatRotations(tmpmat, im, rot);

		for (i = 0; i < 3; i++) {
			dst[i] = rot[i][0] * point[0] + rot[i][1] * point[1] + rot[i][2] * point[2];
		}
	}

	/**
	 * concatenates 2 matrices each [3][3].
	 */
	public static void R_ConcatRotations(float in1[][], float in2[][], float out[][]) {
		out[0][0] = in1[0][0] * in2[0][0] + in1[0][1] * in2[1][0] + in1[0][2] * in2[2][0];
		out[0][1] = in1[0][0] * in2[0][1] + in1[0][1] * in2[1][1] + in1[0][2] * in2[2][1];
		out[0][2] = in1[0][0] * in2[0][2] + in1[0][1] * in2[1][2] + in1[0][2] * in2[2][2];
		out[1][0] = in1[1][0] * in2[0][0] + in1[1][1] * in2[1][0] + in1[1][2] * in2[2][0];
		out[1][1] = in1[1][0] * in2[0][1] + in1[1][1] * in2[1][1] + in1[1][2] * in2[2][1];
		out[1][2] = in1[1][0] * in2[0][2] + in1[1][1] * in2[1][2] + in1[1][2] * in2[2][2];
		out[2][0] = in1[2][0] * in2[0][0] + in1[2][1] * in2[1][0] + in1[2][2] * in2[2][0];
		out[2][1] = in1[2][0] * in2[0][1] + in1[2][1] * in2[1][1] + in1[2][2] * in2[2][1];
		out[2][2] = in1[2][0] * in2[0][2] + in1[2][1] * in2[1][2] + in1[2][2] * in2[2][2];
	}

	public static float LerpAngle(float a2, float a1, float frac) {
		if (a1 - a2 > 180)
			a1 -= 360;
		if (a1 - a2 < -180)
			a1 += 360;
		return a2 + frac * (a1 - a2);
	}

	public static void assert1(boolean cond) {
		if (!cond) {

			try {

				int a[] = null;
				int b = a[0];
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

	public static int BoxOnPlaneSide(float emins[], float emaxs[], cplane_t p) {
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
				dist1 = p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				dist2 = p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				break;
			case 1 :
				dist1 = p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				dist2 = p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				break;
			case 2 :
				dist1 = p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				dist2 = p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				break;
			case 3 :
				dist1 = p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				dist2 = p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				break;
			case 4 :
				dist1 = p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				dist2 = p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				break;
			case 5 :
				dist1 = p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				dist2 = p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				break;
			case 6 :
				dist1 = p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				dist2 = p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				break;
			case 7 :
				dist1 = p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				dist2 = p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				break;
			default :
				//TODO: error message.
				dist1 = dist2 = 0;

				break;
		}

		sides = 0;
		if (dist1 >= p.dist)
			sides = 1;
		if (dist2 < p.dist)
			sides |= 2;

		assert1(sides != 0);

		return sides;
	}

	//	this is the slow, general version
	public static int BoxOnPlaneSide2(float[] emins, float[] emaxs, cplane_t p) {
		int i;
		float dist1, dist2;
		int sides;
		float corners[][] = new float[3][2];

		for (i = 0; i < 3; i++) {
			if (p.normal[i] < 0) {
				corners[0][i] = emins[i];
				corners[1][i] = emaxs[i];
			} else {
				corners[1][i] = emins[i];
				corners[0][i] = emaxs[i];
			}
		}
		dist1 = DotProduct(p.normal, corners[0]) - p.dist;
		dist2 = DotProduct(p.normal, corners[1]) - p.dist;
		sides = 0;
		if (dist1 >= 0)
			sides = 1;
		if (dist2 < 0)
			sides |= 2;

		return sides;
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

	public static float crandom() {
		return (float) (Math.random() - 0.5) * 2.0f;
	}

	public static float random() {
		return (float) Math.random();
	}

	public static int ANGLE2SHORT(float x) {
		return ((int) ((x) * 65536 / 360) & 65535);
	}

	public static float SHORT2ANGLE(int x) {
		return ((x) * (360.0f / 65536));
	}

	//TODO: delete this and clean up quake.
	public static int strcmp(String in1, String in2) {
		return in1.compareTo(in2);
	}

	public static int stricmp(String in1, String in2) {
		return in1.compareToIgnoreCase(in2);
	}

	public static int Q_stricmp(String in1, String in2) {
		return in1.compareToIgnoreCase(in2);
	}

	//TODO: delete this and clean up quake.	
	public static int strncmp(String in1, String in2, int len) {
		int i1 = Math.min(len, in1.length());
		int i2 = Math.min(len, in2.length());

		if (i1 < i2)
			return -1;
		if (i1 > i2)
			return 1;

		for (int n = 0; n < i1; n++) {
			char c1 = in1.charAt(n);
			char c2 = in1.charAt(n);
			if (c1 < c2)
				return -1;
			if (c1 > c2)
				return 1;
		}
		return 0;
	}

	public static float atof(String in) {
		float res = 0;

		try {
			res = Float.parseFloat(in);
		} catch (Exception e) {
		}

		return res;
	}
}