/*
 * M.java
 * Copyright (C) 2003
 * 
 * $Id: M.java,v 1.6 2003-12-04 21:04:35 rst Exp $
 */
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
package jake2.client;

import jake2.*;
import jake2.game.*;

import jake2.game.trace_t;
import jake2.server.SV;

/**
 * M
 */
public final class M {

	public static void Init() {
	}

	public static void M_CheckGround(edict_t ent) {
		float[] point = { 0, 0, 0 };
		trace_t trace;
	
		if ((ent.flags & (Defines.FL_SWIM | Defines.FL_FLY)) != 0)
			return;
	
		if (ent.velocity[2] > 100) {
			ent.groundentity = null;
			return;
		}
	
		//	   if the hull point one-quarter unit down is solid the entity is on ground
		point[0] = ent.s.origin[0];
		point[1] = ent.s.origin[1];
		point[2] = ent.s.origin[2] - 0.25f;
	
		trace = GameBase.gi.trace(ent.s.origin, ent.mins, ent.maxs, point, ent, Defines.MASK_MONSTERSOLID);
	
		// check steepness
		if (trace.plane.normal[2] < 0.7 && !trace.startsolid) {
			ent.groundentity = null;
			return;
		}
	
		//		ent.groundentity = trace.ent;
		//		ent.groundentity_linkcount = trace.ent.linkcount;
		//		if (!trace.startsolid && !trace.allsolid)
		//			VectorCopy (trace.endpos, ent.s.origin);
		if (!trace.startsolid && !trace.allsolid) {
			GameBase.VectorCopy(trace.endpos, ent.s.origin);
			ent.groundentity = trace.ent;
			ent.groundentity_linkcount = trace.ent.linkcount;
			ent.velocity[2] = 0;
		}
	}

	public static boolean M_CheckBottom(edict_t ent) {
		float[] mins = { 0, 0, 0 };
		float[] maxs = { 0, 0, 0 };
		float[] start = { 0, 0, 0 };
		float[] stop = { 0, 0, 0 };
	
		trace_t trace;
		int x, y;
		float mid, bottom;
	
		GameBase.VectorAdd(ent.s.origin, ent.mins, mins);
		GameBase.VectorAdd(ent.s.origin, ent.maxs, maxs);
	
		//	   if all of the points under the corners are solid world, don't bother
		//	   with the tougher checks
		//	   the corners must be within 16 of the midpoint
		start[2] = mins[2] - 1;
		for (x = 0; x <= 1; x++)
			for (y = 0; y <= 1; y++) {
				start[0] = x != 0 ? maxs[0] : mins[0];
				start[1] = y != 0 ? maxs[1] : mins[1];
				if (GameBase.gi.pointcontents(start) != Defines.CONTENTS_SOLID) {
					GameBase.c_no++;
					//
					//	   check it for real...
					//
					start[2] = mins[2];
	
					//	   the midpoint must be within 16 of the bottom
					start[0] = stop[0] = (mins[0] + maxs[0]) * 0.5f;
					start[1] = stop[1] = (mins[1] + maxs[1]) * 0.5f;
					stop[2] = start[2] - 2 * GameBase.STEPSIZE;
					trace = GameBase.gi.trace(start, GameBase.vec3_origin, GameBase.vec3_origin, stop, ent, Defines.MASK_MONSTERSOLID);
	
					if (trace.fraction == 1.0)
						return false;
					mid = bottom = trace.endpos[2];
	
					//	   the corners must be within 16 of the midpoint	
					for (x = 0; x <= 1; x++)
						for (y = 0; y <= 1; y++) {
							start[0] = stop[0] = x != 0 ? maxs[0] : mins[0];
							start[1] = stop[1] = y != 0 ? maxs[1] : mins[1];
	
							trace = GameBase.gi.trace(start, GameBase.vec3_origin, GameBase.vec3_origin, stop, ent, Defines.MASK_MONSTERSOLID);
	
							if (trace.fraction != 1.0 && trace.endpos[2] > bottom)
								bottom = trace.endpos[2];
							if (trace.fraction == 1.0 || mid - trace.endpos[2] > GameBase.STEPSIZE)
								return false;
						}
	
					GameBase.c_yes++;
					return true;
				}
			}
	
		GameBase.c_yes++;
		return true; // we got out easy
	}

	/*
	===============
	M_ChangeYaw
	
	===============
	*/
	public static void M_ChangeYaw(edict_t ent) {
		float ideal;
		float current;
		float move;
		float speed;
	
		current = GameBase.anglemod(ent.s.angles[Defines.YAW]);
		ideal = ent.ideal_yaw;
	
		if (current == ideal)
			return;
	
		move = ideal - current;
		speed = ent.yaw_speed;
		if (ideal > current) {
			if (move >= 180)
				move = move - 360;
		} else {
			if (move <= -180)
				move = move + 360;
		}
		if (move > 0) {
			if (move > speed)
				move = speed;
		} else {
			if (move < -speed)
				move = -speed;
		}
	
		ent.s.angles[Defines.YAW] = GameBase.anglemod(current + move);
	}

	/*
	======================
	M_MoveToGoal
	======================
	*/
	public static void M_MoveToGoal(edict_t ent, float dist) {
		edict_t goal = ent.goalentity;
	
		if (ent.groundentity == null && (ent.flags & (Defines.FL_FLY | Defines.FL_SWIM)) == 0)
			return;
	
		//	   if the next step hits the enemy, return immediately
		if (ent.enemy != null && SV.SV_CloseEnough(ent, ent.enemy, dist))
			return;
	
		//	   bump around...
		if ((GameBase.rand() & 3) == 1 || !SV.SV_StepDirection(ent, ent.ideal_yaw, dist)) {
			if (ent.inuse)
				SV.SV_NewChaseDir(ent, goal, dist);
		}
	}

	/*
	===============
	M_walkmove
	===============
	*/
	public static boolean M_walkmove(edict_t ent, float yaw, float dist) {
		float[] move = { 0, 0, 0 };
	
		if ((ent.groundentity == null) && (ent.flags & (Defines.FL_FLY | Defines.FL_SWIM)) == 0)
			return false;
	
		yaw = (float) (yaw * Math.PI * 2 / 360);
	
		move[0] = (float) Math.cos(yaw) * dist;
		move[1] = (float) Math.sin(yaw) * dist;
		move[2] = 0;
	
		return SV.SV_movestep(ent, move, true);
	}
	/** Stops the Flies. */
	public static EntThinkAdapter M_FliesOff= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.s.effects &= ~Defines.EF_FLIES;
			self.s.sound= 0;
			return true;
		}
	};
	/** Starts the Flies as setting the animation flag in the entity. */
	public static EntThinkAdapter M_FliesOn= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (self.waterlevel != 0)
				return true;
	
			self.s.effects |= Defines.EF_FLIES;
			self.s.sound= GameBase.gi.soundindex("infantry/inflies1.wav");
			self.think= M_FliesOff;
			self.nextthink= GameBase.level.time + 60;
			return true;
		}
	};
	/** Adds some flies after a random time */
	public static EntThinkAdapter M_FlyCheck= new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			if (self.waterlevel != 0)
				return true;
	
			if (GameBase.random() > 0.5)
				return true;
	
			self.think= M_FliesOn;
			self.nextthink= GameBase.level.time + 5 + 10 * GameBase.random();
			return true;
		}
	};
}
