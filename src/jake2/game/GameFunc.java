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

// Created on 18.11.2003 by RST.
// $Id: GameFunc.java,v 1.4 2004-09-10 19:02:53 salomo Exp $

package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.qcommon.Com;
import jake2.util.*;
import jake2.util.*;

public class GameFunc extends PlayerView
{

	static void Move_Calc(edict_t ent, float[] dest, EntThinkAdapter func)
	{
		Math3D.VectorClear(ent.velocity);
		Math3D.VectorSubtract(dest, ent.s.origin, ent.moveinfo.dir);
		ent.moveinfo.remaining_distance = Math3D.VectorNormalize(ent.moveinfo.dir);
		
		ent.moveinfo.endfunc = func;

		if (ent.moveinfo.speed == ent.moveinfo.accel && ent.moveinfo.speed == ent.moveinfo.decel)
		{
			if (level.current_entity == ((ent.flags & FL_TEAMSLAVE) != 0 ? ent.teammaster : ent))
			{
				GameFuncAdapters.Move_Begin.think(ent);
			}
			else
			{
				ent.nextthink = level.time + FRAMETIME;
				ent.think = GameFuncAdapters.Move_Begin;
			}
		}
		else
		{
			// accelerative
			ent.moveinfo.current_speed = 0;
			ent.think = GameFuncAdapters.Think_AccelMove;
			ent.nextthink = level.time + FRAMETIME;
		}
	}

	static void AngleMove_Calc(edict_t ent, EntThinkAdapter func)
	{
		Math3D.VectorClear(ent.avelocity);
		ent.moveinfo.endfunc = func;
		if (level.current_entity == ((ent.flags & FL_TEAMSLAVE) != 0 ? ent.teammaster : ent))
		{
			GameFuncAdapters.AngleMove_Begin.think(ent);
		}
		else
		{
			ent.nextthink = level.time + FRAMETIME;
			ent.think = GameFuncAdapters.AngleMove_Begin;
		}
	}

	/*
	==============
	Think_AccelMove
	
	The team has completed a frame of movement, so
	change the speed for the next frame
	==============
	*/
	static float AccelerationDistance(float target, float rate)
	{
		return target * ((target / rate) + 1) / 2;
	};

	static void plat_CalcAcceleratedMove(moveinfo_t moveinfo)
	{
		float accel_dist;
		float decel_dist;

		moveinfo.move_speed = moveinfo.speed;

		if (moveinfo.remaining_distance < moveinfo.accel)
		{
			moveinfo.current_speed = moveinfo.remaining_distance;
			return;
		}

		accel_dist = AccelerationDistance(moveinfo.speed, moveinfo.accel);
		decel_dist = AccelerationDistance(moveinfo.speed, moveinfo.decel);

		if ((moveinfo.remaining_distance - accel_dist - decel_dist) < 0)
		{
			float f;

			f = (moveinfo.accel + moveinfo.decel) / (moveinfo.accel * moveinfo.decel);
			moveinfo.move_speed = (float) ((-2 + Math.sqrt(4 - 4 * f * (-2 * moveinfo.remaining_distance))) / (2 * f));
			decel_dist = AccelerationDistance(moveinfo.move_speed, moveinfo.decel);
		}

		moveinfo.decel_distance = decel_dist;
	};

	static void plat_Accelerate(moveinfo_t moveinfo)
	{
		// are we decelerating?
		if (moveinfo.remaining_distance <= moveinfo.decel_distance)
		{
			if (moveinfo.remaining_distance < moveinfo.decel_distance)
			{
				if (moveinfo.next_speed != 0)
				{
					moveinfo.current_speed = moveinfo.next_speed;
					moveinfo.next_speed = 0;
					return;
				}
				if (moveinfo.current_speed > moveinfo.decel)
					moveinfo.current_speed -= moveinfo.decel;
			}
			return;
		}

		// are we at full speed and need to start decelerating during this move?
		if (moveinfo.current_speed == moveinfo.move_speed)
			if ((moveinfo.remaining_distance - moveinfo.current_speed) < moveinfo.decel_distance)
			{
				float p1_distance;
				float p2_distance;
				float distance;

				p1_distance = moveinfo.remaining_distance - moveinfo.decel_distance;
				p2_distance = moveinfo.move_speed * (1.0f - (p1_distance / moveinfo.move_speed));
				distance = p1_distance + p2_distance;
				moveinfo.current_speed = moveinfo.move_speed;
				moveinfo.next_speed = moveinfo.move_speed - moveinfo.decel * (p2_distance / distance);
				return;
			}

		// are we accelerating?
		if (moveinfo.current_speed < moveinfo.speed)
		{
			float old_speed;
			float p1_distance;
			float p1_speed;
			float p2_distance;
			float distance;

			old_speed = moveinfo.current_speed;

			// figure simple acceleration up to move_speed
			moveinfo.current_speed += moveinfo.accel;
			if (moveinfo.current_speed > moveinfo.speed)
				moveinfo.current_speed = moveinfo.speed;

			// are we accelerating throughout this entire move?
			if ((moveinfo.remaining_distance - moveinfo.current_speed) >= moveinfo.decel_distance)
				return;

			// during this move we will accelrate from current_speed to move_speed
			// and cross over the decel_distance; figure the average speed for the
			// entire move
			p1_distance = moveinfo.remaining_distance - moveinfo.decel_distance;
			p1_speed = (old_speed + moveinfo.move_speed) / 2.0f;
			p2_distance = moveinfo.move_speed * (1.0f - (p1_distance / p1_speed));
			distance = p1_distance + p2_distance;
			moveinfo.current_speed = (p1_speed * (p1_distance / distance)) + (moveinfo.move_speed * (p2_distance / distance));
			moveinfo.next_speed = moveinfo.move_speed - moveinfo.decel * (p2_distance / distance);
			return;
		}

		// we are at constant velocity (move_speed)
		return;
	};

	static void plat_go_up(edict_t ent)
	{
		if (0 == (ent.flags & FL_TEAMSLAVE))
		{
			if (ent.moveinfo.sound_start != 0)
				gi.sound(ent, CHAN_NO_PHS_ADD + CHAN_VOICE, ent.moveinfo.sound_start, 1, ATTN_STATIC, 0);
			ent.s.sound = ent.moveinfo.sound_middle;
		}
		ent.moveinfo.state = GameFuncAdapters.STATE_UP;
		Move_Calc(ent, ent.moveinfo.start_origin, GameFuncAdapters.plat_hit_top);
	}

	static void plat_spawn_inside_trigger(edict_t ent)
	{
		edict_t trigger;
		float[] tmin = { 0, 0, 0 }, tmax = { 0, 0, 0 };

		//
		//	   middle trigger
		//	
		trigger = G_Spawn();
		trigger.touch = GameFuncAdapters.Touch_Plat_Center;
		trigger.movetype = MOVETYPE_NONE;
		trigger.solid = SOLID_TRIGGER;
		trigger.enemy = ent;

		tmin[0] = ent.mins[0] + 25;
		tmin[1] = ent.mins[1] + 25;
		tmin[2] = ent.mins[2];

		tmax[0] = ent.maxs[0] - 25;
		tmax[1] = ent.maxs[1] - 25;
		tmax[2] = ent.maxs[2] + 8;

		tmin[2] = tmax[2] - (ent.pos1[2] - ent.pos2[2] + st.lip);

		if ((ent.spawnflags & GameFuncAdapters.PLAT_LOW_TRIGGER) != 0)
			tmax[2] = tmin[2] + 8;

		if (tmax[0] - tmin[0] <= 0)
		{
			tmin[0] = (ent.mins[0] + ent.maxs[0]) * 0.5f;
			tmax[0] = tmin[0] + 1;
		}
		if (tmax[1] - tmin[1] <= 0)
		{
			tmin[1] = (ent.mins[1] + ent.maxs[1]) * 0.5f;
			tmax[1] = tmin[1] + 1;
		}

		Math3D.VectorCopy(tmin, trigger.mins);
		Math3D.VectorCopy(tmax, trigger.maxs);

		gi.linkentity(trigger);
	}

	/*QUAKED func_plat (0 .5 .8) ? PLAT_LOW_TRIGGER
	speed	default 150
	
	Plats are always drawn in the extended position, so they will light correctly.
	
	If the plat is the target of another trigger or button, it will start out disabled in the extended position until it is trigger, when it will lower and become a normal plat.
	
	"speed"	overrides default 200.
	"accel" overrides default 500
	"lip"	overrides default 8 pixel lip
	
	If the "height" key is set, that will determine the amount the plat moves, instead of being implicitly determoveinfoned by the model's height.
	
	Set "sounds" to one of the following:
	1) base fast
	2) chain slow
	*/
	static void SP_func_plat(edict_t ent)
	{
		Math3D.VectorClear(ent.s.angles);
		ent.solid = SOLID_BSP;
		ent.movetype = MOVETYPE_PUSH;

		gi.setmodel(ent, ent.model);

		ent.blocked = GameFuncAdapters.plat_blocked;

		if (0 == ent.speed)
			ent.speed = 20;
		else
			ent.speed *= 0.1;

		if (ent.accel == 0)
			ent.accel = 5;
		else
			ent.accel *= 0.1;

		if (ent.decel == 0)
			ent.decel = 5;
		else
			ent.decel *= 0.1;

		if (ent.dmg == 0)
			ent.dmg = 2;

		if (st.lip == 0)
			st.lip = 8;

		// pos1 is the top position, pos2 is the bottom
		Math3D.VectorCopy(ent.s.origin, ent.pos1);
		Math3D.VectorCopy(ent.s.origin, ent.pos2);
		if (st.height != 0)
			ent.pos2[2] -= st.height;
		else
			ent.pos2[2] -= (ent.maxs[2] - ent.mins[2]) - st.lip;

		ent.use = GameFuncAdapters.Use_Plat;

		plat_spawn_inside_trigger(ent); // the "start moving" trigger	

		if (ent.targetname != null)
		{
			ent.moveinfo.state = GameFuncAdapters.STATE_UP;
		}
		else
		{
			Math3D.VectorCopy(ent.pos2, ent.s.origin);
			gi.linkentity(ent);
			ent.moveinfo.state = GameFuncAdapters.STATE_BOTTOM;
		}

		ent.moveinfo.speed = ent.speed;
		ent.moveinfo.accel = ent.accel;
		ent.moveinfo.decel = ent.decel;
		ent.moveinfo.wait = ent.wait;
		Math3D.VectorCopy(ent.pos1, ent.moveinfo.start_origin);
		Math3D.VectorCopy(ent.s.angles, ent.moveinfo.start_angles);
		Math3D.VectorCopy(ent.pos2, ent.moveinfo.end_origin);
		Math3D.VectorCopy(ent.s.angles, ent.moveinfo.end_angles);

		ent.moveinfo.sound_start = gi.soundindex("plats/pt1_strt.wav");
		ent.moveinfo.sound_middle = gi.soundindex("plats/pt1_mid.wav");
		ent.moveinfo.sound_end = gi.soundindex("plats/pt1_end.wav");
	}

	/*
	======================================================================
	
	DOORS
	
	  spawn a trigger surrounding the entire team unless it is
	  already targeted by another
	
	======================================================================
	*/

	/*QUAKED func_door (0 .5 .8) ? START_OPEN x CRUSHER NOMONSTER ANIMATED TOGGLE ANIMATED_FAST
	TOGGLE		wait in both the start and end states for a trigger event.
	START_OPEN	the door to moves to its destination when spawned, and operate in reverse.  It is used to temporarily or permanently close off an area when triggered (not useful for touch or takedamage doors).
	NOMONSTER	monsters will not trigger this door
	
	"message"	is printed when the door is touched if it is a trigger door and it hasn't been fired yet
	"angle"		determines the opening direction
	"targetname" if set, no touch field will be spawned and a remote button or trigger field activates the door.
	"health"	if set, door must be shot open
	"speed"		movement speed (100 default)
	"wait"		wait before returning (3 default, -1 = never return)
	"lip"		lip remaining at end of move (8 default)
	"dmg"		damage to inflict when blocked (2 default)
	"sounds"
	1)	silent
	2)	light
	3)	medium
	4)	heavy
	*/

	static void door_use_areaportals(edict_t self, boolean open)
	{
		edict_t t = null;

		if (self.target == null)
			return;

		EdictIterator edit = null;

		while ((edit = G_Find(edit, findByTarget, self.target)) != null)
		{
			t = edit.o;
			if (Lib.Q_stricmp(t.classname, "func_areaportal") == 0)
			{
				gi.SetAreaPortalState(t.style, open);
			}
		}
	}

	static void door_go_up(edict_t self, edict_t activator)
	{
		if (self.moveinfo.state == GameFuncAdapters.STATE_UP)
			return; // already going up

		if (self.moveinfo.state == GameFuncAdapters.STATE_TOP)
		{
			// reset top wait time
			if (self.moveinfo.wait >= 0)
				self.nextthink = level.time + self.moveinfo.wait;
			return;
		}

		if (0 == (self.flags & FL_TEAMSLAVE))
		{
			if (self.moveinfo.sound_start != 0)
				gi.sound(self, CHAN_NO_PHS_ADD + CHAN_VOICE, self.moveinfo.sound_start, 1, ATTN_STATIC, 0);
			self.s.sound = self.moveinfo.sound_middle;
		}
		self.moveinfo.state = GameFuncAdapters.STATE_UP;
		if (Lib.strcmp(self.classname, "func_door") == 0)
			Move_Calc(self, self.moveinfo.end_origin, GameFuncAdapters.door_hit_top);
		else if (Lib.strcmp(self.classname, "func_door_rotating") == 0)
			AngleMove_Calc(self, GameFuncAdapters.door_hit_top);

		G_UseTargets(self, activator);
		door_use_areaportals(self, true);
	}

	/*QUAKED func_water (0 .5 .8) ? START_OPEN
	func_water is a moveable water brush.  It must be targeted to operate.  Use a non-water texture at your own risk.
	
	START_OPEN causes the water to move to its destination when spawned and operate in reverse.
	
	"angle"		determines the opening direction (up or down only)
	"speed"		movement speed (25 default)
	"wait"		wait before returning (-1 default, -1 = TOGGLE)
	"lip"		lip remaining at end of move (0 default)
	"sounds"	(yes, these need to be changed)
	0)	no sound
	1)	water
	2)	lava
	*/

	static void SP_func_water(edict_t self)
	{
		float[] abs_movedir = { 0, 0, 0 };

		G_SetMovedir(self.s.angles, self.movedir);
		self.movetype = MOVETYPE_PUSH;
		self.solid = SOLID_BSP;
		gi.setmodel(self, self.model);

		switch (self.sounds)
		{
			default :
				break;

			case 1 : // water
				self.moveinfo.sound_start = gi.soundindex("world/mov_watr.wav");
				self.moveinfo.sound_end = gi.soundindex("world/stp_watr.wav");
				break;

			case 2 : // lava
				self.moveinfo.sound_start = gi.soundindex("world/mov_watr.wav");
				self.moveinfo.sound_end = gi.soundindex("world/stp_watr.wav");
				break;
		}

		// calculate second position
		Math3D.VectorCopy(self.s.origin, self.pos1);
		abs_movedir[0] = Math.abs(self.movedir[0]);
		abs_movedir[1] = Math.abs(self.movedir[1]);
		abs_movedir[2] = Math.abs(self.movedir[2]);
		self.moveinfo.distance =
			abs_movedir[0] * self.size[0] + abs_movedir[1] * self.size[1] + abs_movedir[2] * self.size[2] - st.lip;
		Math3D.VectorMA(self.pos1, self.moveinfo.distance, self.movedir, self.pos2);

		// if it starts open, switch the positions
		if ((self.spawnflags & GameFuncAdapters.DOOR_START_OPEN) != 0)
		{
			Math3D.VectorCopy(self.pos2, self.s.origin);
			Math3D.VectorCopy(self.pos1, self.pos2);
			Math3D.VectorCopy(self.s.origin, self.pos1);
		}

		Math3D.VectorCopy(self.pos1, self.moveinfo.start_origin);
		Math3D.VectorCopy(self.s.angles, self.moveinfo.start_angles);
		Math3D.VectorCopy(self.pos2, self.moveinfo.end_origin);
		Math3D.VectorCopy(self.s.angles, self.moveinfo.end_angles);

		self.moveinfo.state = GameFuncAdapters.STATE_BOTTOM;

		if (0 == self.speed)
			self.speed = 25;
		self.moveinfo.accel = self.moveinfo.decel = self.moveinfo.speed = self.speed;

		if (0 == self.wait)
			self.wait = -1;
		self.moveinfo.wait = self.wait;

		self.use = GameFuncAdapters.door_use;

		if (self.wait == -1)
			self.spawnflags |= GameFuncAdapters.DOOR_TOGGLE;

		self.classname = "func_door";

		gi.linkentity(self);
	}

	static void train_resume(edict_t self)
	{
		edict_t ent;
		float[] dest = { 0, 0, 0 };

		ent = self.target_ent;

		Math3D.VectorSubtract(ent.s.origin, self.mins, dest);
		self.moveinfo.state = GameFuncAdapters.STATE_TOP;
		Math3D.VectorCopy(self.s.origin, self.moveinfo.start_origin);
		Math3D.VectorCopy(dest, self.moveinfo.end_origin);
		Move_Calc(self, dest, GameFuncAdapters.train_wait);
		self.spawnflags |= GameFuncAdapters.TRAIN_START_ON;

	}

	static void SP_func_train(edict_t self)
	{
		self.movetype = MOVETYPE_PUSH;

		Math3D.VectorClear(self.s.angles);
		self.blocked = GameFuncAdapters.train_blocked;
		if ((self.spawnflags & GameFuncAdapters.TRAIN_BLOCK_STOPS) != 0)
			self.dmg = 0;
		else
		{
			if (0 == self.dmg)
				self.dmg = 100;
		}
		self.solid = SOLID_BSP;
		gi.setmodel(self, self.model);

		if (st.noise != null)
			self.moveinfo.sound_middle = gi.soundindex(st.noise);

		if (0 == self.speed)
			self.speed = 100;

		self.moveinfo.speed = self.speed;
		self.moveinfo.accel = self.moveinfo.decel = self.moveinfo.speed;

		self.use = GameFuncAdapters.train_use;

		gi.linkentity(self);

		if (self.target != null)
		{
			// start trains on the second frame, to make sure their targets have had
			// a chance to spawn
			self.nextthink = level.time + FRAMETIME;
			self.think = GameFuncAdapters.func_train_find;
		}
		else
		{
			gi.dprintf("func_train without a target at " + Lib.vtos(self.absmin) + "\n");
		}
	}

	static void SP_func_timer(edict_t self)
	{
		if (0 == self.wait)
			self.wait = 1.0f;

		self.use = GameFuncAdapters.func_timer_use;
		self.think = GameFuncAdapters.func_timer_think;

		if (self.random >= self.wait)
		{
			self.random = self.wait - FRAMETIME;
			gi.dprintf("func_timer at " + Lib.vtos(self.s.origin) + " has random >= wait\n");
		}

		if ((self.spawnflags & 1) != 0)
		{
			self.nextthink = level.time + 1.0f + st.pausetime + self.delay + self.wait + Lib.crandom() * self.random;
			self.activator = self;
		}

		self.svflags = SVF_NOCLIENT;
	}

}
