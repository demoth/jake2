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

// Created on 26.02.2004 by RST.
// $Id: GameFuncAdapters.java,v 1.3 2004-02-29 00:51:05 rst Exp $

package jake2.game;

import jake2.util.Lib;
import jake2.util.Math3D;
import jake2.Defines;
import jake2.Globals;
import jake2.util.*;
import jake2.util.*;

public class GameFuncAdapters
{

	/*
	=========================================================
	
	  PLATS
	
	  movement options:
	
	  linear
	  smooth start, hard stop
	  smooth start, smooth stop
	
	  start
	  end
	  acceleration
	  speed
	  deceleration
	  begin sound
	  end sound
	  target fired when reaching end
	  wait at end
	
	  object characteristics that use move segments
	  ---------------------------------------------
	  movetype_push, or movetype_stop
	  action when touched
	  action when blocked
	  action when used
		disabled?
	  auto trigger spawning
	
	
	=========================================================
	*/

	public final static int PLAT_LOW_TRIGGER = 1;
	public final static int STATE_TOP = 0;
	public final static int STATE_BOTTOM = 1;
	public final static int STATE_UP = 2;
	public final static int STATE_DOWN = 3;
	public final static int DOOR_START_OPEN = 1;
	public final static int DOOR_REVERSE = 2;
	public final static int DOOR_CRUSHER = 4;
	public final static int DOOR_NOMONSTER = 8;
	public final static int DOOR_TOGGLE = 32;
	public final static int DOOR_X_AXIS = 64;
	public final static int DOOR_Y_AXIS = 128;
	//
	//	   Support routines for movement (changes in origin using velocity)
	//

	static EntThinkAdapter Move_Done = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			Math3D.VectorClear(ent.velocity);
			ent.moveinfo.endfunc.think(ent);
			return true;
		}
	};
	static EntThinkAdapter Move_Final = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{

			if (ent.moveinfo.remaining_distance == 0)
			{
				Move_Done.think(ent);
				return true;
			}

			Math3D.VectorScale(ent.moveinfo.dir, ent.moveinfo.remaining_distance / Defines.FRAMETIME, ent.velocity);

			ent.think = Move_Done;
			ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
			return true;
		}
	};
	static EntThinkAdapter Move_Begin = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{

			float frames;

			if ((ent.moveinfo.speed * Defines.FRAMETIME) >= ent.moveinfo.remaining_distance)
			{
				Move_Final.think(ent);
				return true;
			}
			Math3D.VectorScale(ent.moveinfo.dir, ent.moveinfo.speed, ent.velocity);
			frames = (float) Math.floor((ent.moveinfo.remaining_distance / ent.moveinfo.speed) / Defines.FRAMETIME);
			ent.moveinfo.remaining_distance -= frames * ent.moveinfo.speed * Defines.FRAMETIME;
			ent.nextthink = GameBase.level.time + (frames * Defines.FRAMETIME);
			ent.think = Move_Final;
			return true;
		}
	};
	//
	//	   Support routines for angular movement (changes in angle using avelocity)
	//

	static EntThinkAdapter AngleMove_Done = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			Math3D.VectorClear(ent.avelocity);
			ent.moveinfo.endfunc.think(ent);
			return true;
		}
	};
	static EntThinkAdapter AngleMove_Final = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			float[] move = { 0, 0, 0 };

			if (ent.moveinfo.state == STATE_UP)
				Math3D.VectorSubtract(ent.moveinfo.end_angles, ent.s.angles, move);
			else
				Math3D.VectorSubtract(ent.moveinfo.start_angles, ent.s.angles, move);

			if (Math3D.VectorCompare(move, Globals.vec3_origin) != 0)
			{
				AngleMove_Done.think(ent);
				return true;
			}

			Math3D.VectorScale(move, 1.0f / Defines.FRAMETIME, ent.avelocity);

			ent.think = AngleMove_Done;
			ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
			return true;
		}
	};
	static EntThinkAdapter AngleMove_Begin = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			float[] destdelta = { 0, 0, 0 };
			float len;
			float traveltime;
			float frames;

			// set destdelta to the vector needed to move
			if (ent.moveinfo.state == STATE_UP)
				Math3D.VectorSubtract(ent.moveinfo.end_angles, ent.s.angles, destdelta);
			else
				Math3D.VectorSubtract(ent.moveinfo.start_angles, ent.s.angles, destdelta);

			// calculate length of vector
			len = Math3D.VectorLength(destdelta);

			// divide by speed to get time to reach dest
			traveltime = len / ent.moveinfo.speed;

			if (traveltime < Defines.FRAMETIME)
			{
				AngleMove_Final.think(ent);
				return true;
			}

			frames = (float) (Math.floor(traveltime / Defines.FRAMETIME));

			// scale the destdelta vector by the time spent traveling to get velocity
			Math3D.VectorScale(destdelta, 1.0f / traveltime, ent.avelocity);

			// set nextthink to trigger a think when dest is reached
			ent.nextthink = GameBase.level.time + frames * Defines.FRAMETIME;
			ent.think = AngleMove_Final;
			return true;
		}
	};
	static EntThinkAdapter Think_AccelMove = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			ent.moveinfo.remaining_distance -= ent.moveinfo.current_speed;

			if (ent.moveinfo.current_speed == 0) // starting or blocked
				GameFunc.plat_CalcAcceleratedMove(ent.moveinfo);

			GameFunc.plat_Accelerate(ent.moveinfo);

			// will the entire move complete on next frame?
			if (ent.moveinfo.remaining_distance <= ent.moveinfo.current_speed)
			{
				Move_Final.think(ent);
				return true;
			}

			Math3D.VectorScale(ent.moveinfo.dir, ent.moveinfo.current_speed * 10, ent.velocity);
			ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
			ent.think = Think_AccelMove;
			return true;
		}
	};
	static EntThinkAdapter plat_hit_top = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			if (0 == (ent.flags & Defines.FL_TEAMSLAVE))
			{
				if (ent.moveinfo.sound_end != 0)
					GameBase.gi.sound(
						ent,
						Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
						ent.moveinfo.sound_end,
						1,
						Defines.ATTN_STATIC,
						0);
				ent.s.sound = 0;
			}
			ent.moveinfo.state = STATE_TOP;

			ent.think = plat_go_down;
			ent.nextthink = GameBase.level.time + 3;
			return true;
		}
	};
	static EntThinkAdapter plat_hit_bottom = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{

			if (0 == (ent.flags & Defines.FL_TEAMSLAVE))
			{
				if (ent.moveinfo.sound_end != 0)
					GameBase.gi.sound(
						ent,
						Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
						ent.moveinfo.sound_end,
						1,
						Defines.ATTN_STATIC,
						0);
				ent.s.sound = 0;
			}
			ent.moveinfo.state = STATE_BOTTOM;
			return true;
		}
	};
	static EntThinkAdapter plat_go_down = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			if (0 == (ent.flags & Defines.FL_TEAMSLAVE))
			{
				if (ent.moveinfo.sound_start != 0)
					GameBase.gi.sound(
						ent,
						Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
						ent.moveinfo.sound_start,
						1,
						Defines.ATTN_STATIC,
						0);
				ent.s.sound = ent.moveinfo.sound_middle;
			}
			ent.moveinfo.state = STATE_DOWN;
			GameFunc.Move_Calc(ent, ent.moveinfo.end_origin, plat_hit_bottom);
			return true;
		}
	};
	static EntBlockedAdapter plat_blocked = new EntBlockedAdapter()
	{
		public void blocked(edict_t self, edict_t other)
		{
			if (0 == (other.svflags & Defines.SVF_MONSTER) && (null == other.client))
			{
				// give it a chance to go away on it's own terms (like gibs)
				GameUtil.T_Damage(
					other,
					self,
					self,
					Globals.vec3_origin,
					other.s.origin,
					Globals.vec3_origin,
					100000,
					1,
					0,
					Defines.MOD_CRUSH);
				// if it's still there, nuke it
				if (other != null)
					GameAI.BecomeExplosion1(other);
				return;
			}

			GameUtil.T_Damage(
				other,
				self,
				self,
				Globals.vec3_origin,
				other.s.origin,
				Globals.vec3_origin,
				self.dmg,
				1,
				0,
				Defines.MOD_CRUSH);

			if (self.moveinfo.state == STATE_UP)
				plat_go_down.think(self);
			else if (self.moveinfo.state == STATE_DOWN)
				GameFunc.plat_go_up(self);

		}
	};
	static EntUseAdapter Use_Plat = new EntUseAdapter()
	{
		public void use(edict_t ent, edict_t other, edict_t activator)
		{
			if (ent.think != null)
				return; // already down
			plat_go_down.think(ent);
		}
	};
	static EntTouchAdapter Touch_Plat_Center = new EntTouchAdapter()
	{
		public void touch(edict_t ent, edict_t other, cplane_t plane, csurface_t surf)
		{
			if (other.client == null)
				return;

			if (other.health <= 0)
				return;

			ent = ent.enemy; // now point at the plat, not the trigger
			if (ent.moveinfo.state == STATE_BOTTOM)
				GameFunc.plat_go_up(ent);
			else if (ent.moveinfo.state == STATE_TOP)
			{
				ent.nextthink = GameBase.level.time + 1; // the player is still on the plat, so delay going down
			}
		}
	};
	//	  ====================================================================

	/*QUAKED func_rotating (0 .5 .8) ? START_ON REVERSE X_AXIS Y_AXIS TOUCH_PAIN STOP ANIMATED ANIMATED_FAST
	You need to have an origin brush as part of this entity.  The center of that brush will be
	the point around which it is rotated. It will rotate around the Z axis by default.  You can
	check either the X_AXIS or Y_AXIS box to change that.
	
	"speed" determines how fast it moves; default value is 100.
	"dmg"	damage to inflict when blocked (2 default)
	
	REVERSE will cause the it to rotate in the opposite direction.
	STOP mean it will stop moving instead of pushing entities
	*/

	static EntBlockedAdapter rotating_blocked = new EntBlockedAdapter()
	{
		public void blocked(edict_t self, edict_t other)
		{
			GameUtil.T_Damage(
				other,
				self,
				self,
				Globals.vec3_origin,
				other.s.origin,
				Globals.vec3_origin,
				self.dmg,
				1,
				0,
				Defines.MOD_CRUSH);
		}
	};
	static EntTouchAdapter rotating_touch = new EntTouchAdapter()
	{
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			if (self.avelocity[0] != 0 || self.avelocity[1] != 0 || self.avelocity[2] != 0)
				GameUtil.T_Damage(
					other,
					self,
					self,
					Globals.vec3_origin,
					other.s.origin,
					Globals.vec3_origin,
					self.dmg,
					1,
					0,
					Defines.MOD_CRUSH);
		}
	};
	static EntUseAdapter rotating_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			if (0 == Math3D.VectorCompare(self.avelocity, Globals.vec3_origin))
			{
				self.s.sound = 0;
				Math3D.VectorClear(self.avelocity);
				self.touch = null;
			}
			else
			{
				self.s.sound = self.moveinfo.sound_middle;
				Math3D.VectorScale(self.movedir, self.speed, self.avelocity);
				if ((self.spawnflags & 16) != 0)
					self.touch = rotating_touch;
			}
		}
	};
	static EntThinkAdapter SP_func_rotating = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			ent.solid = Defines.SOLID_BSP;
			if ((ent.spawnflags & 32) != 0)
				ent.movetype = Defines.MOVETYPE_STOP;
			else
				ent.movetype = Defines.MOVETYPE_PUSH;

			// set the axis of rotation
			Math3D.VectorClear(ent.movedir);
			if ((ent.spawnflags & 4) != 0)
				ent.movedir[2] = 1.0f;
			else if ((ent.spawnflags & 8) != 0)
				ent.movedir[0] = 1.0f;
			else // Z_AXIS
				ent.movedir[1] = 1.0f;

			// check for reverse rotation
			if ((ent.spawnflags & 2) != 0)
				Math3D.VectorNegate(ent.movedir, ent.movedir);

			if (0 == ent.speed)
				ent.speed = 100;
			if (0 == ent.dmg)
				ent.dmg = 2;

			//		ent.moveinfo.sound_middle = "doors/hydro1.wav";

			ent.use = rotating_use;
			if (ent.dmg != 0)
				ent.blocked = rotating_blocked;

			if ((ent.spawnflags & 1) != 0)
				ent.use.use(ent, null, null);

			if ((ent.spawnflags & 64) != 0)
				ent.s.effects |= Defines.EF_ANIM_ALL;
			if ((ent.spawnflags & 128) != 0)
				ent.s.effects |= Defines.EF_ANIM_ALLFAST;

			GameBase.gi.setmodel(ent, ent.model);
			GameBase.gi.linkentity(ent);
			return true;
		}
	};
	/*
	======================================================================
	
	BUTTONS
	
	======================================================================
	*/

	/*QUAKED func_button (0 .5 .8) ?
	When a button is touched, it moves some distance in the direction of it's angle, triggers all of it's targets, waits some time, then returns to it's original position where it can be triggered again.
	
	"angle"		determines the opening direction
	"target"	all entities with a matching targetname will be used
	"speed"		override the default 40 speed
	"wait"		override the default 1 second wait (-1 = never return)
	"lip"		override the default 4 pixel lip remaining at end of move
	"health"	if set, the button must be killed instead of touched
	"sounds"
	1) silent
	2) steam metal
	3) wooden clunk
	4) metallic click
	5) in-out
	*/

	static EntThinkAdapter button_done = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{

			self.moveinfo.state = STATE_BOTTOM;
			self.s.effects &= ~Defines.EF_ANIM23;
			self.s.effects |= Defines.EF_ANIM01;
			return true;
		}
	};
	static EntThinkAdapter button_return = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.moveinfo.state = STATE_DOWN;

			GameFunc.Move_Calc(self, self.moveinfo.start_origin, button_done);

			self.s.frame = 0;

			if (self.health != 0)
				self.takedamage = Defines.DAMAGE_YES;
			return true;
		}
	};
	static EntThinkAdapter button_wait = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.moveinfo.state = STATE_TOP;
			self.s.effects &= ~Defines.EF_ANIM01;
			self.s.effects |= Defines.EF_ANIM23;

			GameUtil.G_UseTargets(self, self.activator);
			self.s.frame = 1;
			if (self.moveinfo.wait >= 0)
			{
				self.nextthink = GameBase.level.time + self.moveinfo.wait;
				self.think = button_return;
			}
			return true;
		}
	};
	static EntThinkAdapter button_fire = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.moveinfo.state == STATE_UP || self.moveinfo.state == STATE_TOP)
				return true;

			self.moveinfo.state = STATE_UP;
			if (self.moveinfo.sound_start != 0 && 0 == (self.flags & Defines.FL_TEAMSLAVE))
				GameBase.gi.sound(
					self,
					Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
					self.moveinfo.sound_start,
					1,
					Defines.ATTN_STATIC,
					0);
			GameFunc.Move_Calc(self, self.moveinfo.end_origin, button_wait);
			return true;
		}
	};
	static EntUseAdapter button_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.activator = activator;
			button_fire.think(self);
			return;
		}
	};
	static EntTouchAdapter button_touch = new EntTouchAdapter()
	{
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			if (null == other.client)
				return;

			if (other.health <= 0)
				return;

			self.activator = other;
			button_fire.think(self);

		}
	};
	static EntDieAdapter button_killed = new EntDieAdapter()
	{
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point)
		{
			self.activator = attacker;
			self.health = self.max_health;
			self.takedamage = Defines.DAMAGE_NO;
			button_fire.think(self);

		}
	};
	static EntThinkAdapter SP_func_button = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			float[] abs_movedir = { 0, 0, 0 };
			float dist;

			GameBase.G_SetMovedir(ent.s.angles, ent.movedir);
			ent.movetype = Defines.MOVETYPE_STOP;
			ent.solid = Defines.SOLID_BSP;
			GameBase.gi.setmodel(ent, ent.model);

			if (ent.sounds != 1)
				ent.moveinfo.sound_start = GameBase.gi.soundindex("switches/butn2.wav");

			if (0 == ent.speed)
				ent.speed = 40;
			if (0 == ent.accel)
				ent.accel = ent.speed;
			if (0 == ent.decel)
				ent.decel = ent.speed;

			if (0 == ent.wait)
				ent.wait = 3;
			if (0 == GameBase.st.lip)
				GameBase.st.lip = 4;

			Math3D.VectorCopy(ent.s.origin, ent.pos1);
			abs_movedir[0] = (float) Math.abs(ent.movedir[0]);
			abs_movedir[1] = (float) Math.abs(ent.movedir[1]);
			abs_movedir[2] = (float) Math.abs(ent.movedir[2]);
			dist = abs_movedir[0] * ent.size[0] + abs_movedir[1] * ent.size[1] + abs_movedir[2] * ent.size[2] - GameBase.st.lip;
			Math3D.VectorMA(ent.pos1, dist, ent.movedir, ent.pos2);

			ent.use = button_use;
			ent.s.effects |= Defines.EF_ANIM01;

			if (ent.health != 0)
			{
				ent.max_health = ent.health;
				ent.die = button_killed;
				ent.takedamage = Defines.DAMAGE_YES;
			}
			else if (null == ent.targetname)
				ent.touch = button_touch;

			ent.moveinfo.state = STATE_BOTTOM;

			ent.moveinfo.speed = ent.speed;
			ent.moveinfo.accel = ent.accel;
			ent.moveinfo.decel = ent.decel;
			ent.moveinfo.wait = ent.wait;
			Math3D.VectorCopy(ent.pos1, ent.moveinfo.start_origin);
			Math3D.VectorCopy(ent.s.angles, ent.moveinfo.start_angles);
			Math3D.VectorCopy(ent.pos2, ent.moveinfo.end_origin);
			Math3D.VectorCopy(ent.s.angles, ent.moveinfo.end_angles);

			GameBase.gi.linkentity(ent);
			return true;
		}
	};
	static EntThinkAdapter door_hit_top = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (0 == (self.flags & Defines.FL_TEAMSLAVE))
			{
				if (self.moveinfo.sound_end != 0)
					GameBase.gi.sound(
						self,
						Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
						self.moveinfo.sound_end,
						1,
						Defines.ATTN_STATIC,
						0);
				self.s.sound = 0;
			}
			self.moveinfo.state = STATE_TOP;
			if ((self.spawnflags & DOOR_TOGGLE) != 0)
				return true;
			if (self.moveinfo.wait >= 0)
			{
				self.think = door_go_down;
				self.nextthink = GameBase.level.time + self.moveinfo.wait;
			}
			return true;
		}
	};
	static EntThinkAdapter door_hit_bottom = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (0 == (self.flags & Defines.FL_TEAMSLAVE))
			{
				if (self.moveinfo.sound_end != 0)
					GameBase.gi.sound(
						self,
						Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
						self.moveinfo.sound_end,
						1,
						Defines.ATTN_STATIC,
						0);
				self.s.sound = 0;
			}
			self.moveinfo.state = STATE_BOTTOM;
			GameFunc.door_use_areaportals(self, false);
			return true;
		}
	};
	static EntThinkAdapter door_go_down = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (0 == (self.flags & Defines.FL_TEAMSLAVE))
			{
				if (self.moveinfo.sound_start != 0)
					GameBase.gi.sound(
						self,
						Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
						self.moveinfo.sound_start,
						1,
						Defines.ATTN_STATIC,
						0);
				self.s.sound = self.moveinfo.sound_middle;
			}
			if (self.max_health != 0)
			{
				self.takedamage = Defines.DAMAGE_YES;
				self.health = self.max_health;
			}

			self.moveinfo.state = STATE_DOWN;
			if (Lib.strcmp(self.classname, "func_door") == 0)
				GameFunc.Move_Calc(self, self.moveinfo.start_origin, door_hit_bottom);
			else if (Lib.strcmp(self.classname, "func_door_rotating") == 0)
				GameFunc.AngleMove_Calc(self, door_hit_bottom);
			return true;
		}
	};
	static EntUseAdapter door_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			edict_t ent;

			if ((self.flags & Defines.FL_TEAMSLAVE) != 0)
				return;

			if ((self.spawnflags & DOOR_TOGGLE) != 0)
			{
				if (self.moveinfo.state == STATE_UP || self.moveinfo.state == STATE_TOP)
				{
					// trigger all paired doors
					for (ent = self; ent != null; ent = ent.teamchain)
					{
						ent.message = null;
						ent.touch = null;
						door_go_down.think(ent);
					}
					return;
				}
			}

			// trigger all paired doors
			for (ent = self; ent != null; ent = ent.teamchain)
			{
				ent.message = null;
				ent.touch = null;
				GameFunc.door_go_up(ent, activator);
			}
		}
	};
	static EntTouchAdapter Touch_DoorTrigger = new EntTouchAdapter()
	{
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			if (other.health <= 0)
				return;

			if (0 == (other.svflags & Defines.SVF_MONSTER) && (null == other.client))
				return;

			if (0 != (self.owner.spawnflags & DOOR_NOMONSTER) && 0 != (other.svflags & Defines.SVF_MONSTER))
				return;

			if (GameBase.level.time < self.touch_debounce_time)
				return;
			self.touch_debounce_time = GameBase.level.time + 1.0f;

			door_use.use(self.owner, other, other);
		}
	};
	static EntThinkAdapter Think_CalcMoveSpeed = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			edict_t ent;
			float min;
			float time;
			float newspeed;
			float ratio;
			float dist;

			if ((self.flags & Defines.FL_TEAMSLAVE) != 0)
				return true; // only the team master does this

			// find the smallest distance any member of the team will be moving
			min = Math.abs(self.moveinfo.distance);
			for (ent = self.teamchain; ent != null; ent = ent.teamchain)
			{
				dist = Math.abs(ent.moveinfo.distance);
				if (dist < min)
					min = dist;
			}

			time = min / self.moveinfo.speed;

			// adjust speeds so they will all complete at the same time
			for (ent = self; ent != null; ent = ent.teamchain)
			{
				newspeed = Math.abs(ent.moveinfo.distance) / time;
				ratio = newspeed / ent.moveinfo.speed;
				if (ent.moveinfo.accel == ent.moveinfo.speed)
					ent.moveinfo.accel = newspeed;
				else
					ent.moveinfo.accel *= ratio;
				if (ent.moveinfo.decel == ent.moveinfo.speed)
					ent.moveinfo.decel = newspeed;
				else
					ent.moveinfo.decel *= ratio;
				ent.moveinfo.speed = newspeed;
			}
			return true;
		}
	};
	
	static EntThinkAdapter Think_SpawnDoorTrigger = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			edict_t other;
			float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 };

			if ((ent.flags & Defines.FL_TEAMSLAVE) != 0)
				return true; // only the team leader spawns a trigger

			Math3D.VectorCopy(ent.absmin, mins);
			Math3D.VectorCopy(ent.absmax, maxs);

			for (other = ent.teamchain; other != null; other = other.teamchain)
			{
				GameBase.AddPointToBounds(other.absmin, mins, maxs);
				GameBase.AddPointToBounds(other.absmax, mins, maxs);
			}

			// expand 
			mins[0] -= 60;
			mins[1] -= 60;
			maxs[0] += 60;
			maxs[1] += 60;

			other = GameUtil.G_Spawn();
			Math3D.VectorCopy(mins, other.mins);
			Math3D.VectorCopy(maxs, other.maxs);
			other.owner = ent;
			other.solid = Defines.SOLID_TRIGGER;
			other.movetype = Defines.MOVETYPE_NONE;
			other.touch = Touch_DoorTrigger;
			GameBase.gi.linkentity(other);

			if ((ent.spawnflags & DOOR_START_OPEN) != 0)
				GameFunc.door_use_areaportals(ent, true);

			Think_CalcMoveSpeed.think(ent);
			return true;
		}
	};
	static EntBlockedAdapter door_blocked = new EntBlockedAdapter()
	{
		public void blocked(edict_t self, edict_t other)
		{
			edict_t ent;

			if (0 == (other.svflags & Defines.SVF_MONSTER) && (null == other.client))
			{
				// give it a chance to go away on it's own terms (like gibs)
				GameUtil.T_Damage(
					other,
					self,
					self,
					Globals.vec3_origin,
					other.s.origin,
					Globals.vec3_origin,
					100000,
					1,
					0,
					Defines.MOD_CRUSH);
				// if it's still there, nuke it
				if (other != null)
					GameAI.BecomeExplosion1(other);
				return;
			}

			GameUtil.T_Damage(
				other,
				self,
				self,
				Globals.vec3_origin,
				other.s.origin,
				Globals.vec3_origin,
				self.dmg,
				1,
				0,
				Defines.MOD_CRUSH);

			if ((self.spawnflags & DOOR_CRUSHER) != 0)
				return;

			//	   if a door has a negative wait, it would never come back if blocked,
			//	   so let it just squash the object to death real fast
			if (self.moveinfo.wait >= 0)
			{
				if (self.moveinfo.state == STATE_DOWN)
				{
					for (ent = self.teammaster; ent != null; ent = ent.teamchain)
						GameFunc.door_go_up(ent, ent.activator);
				}
				else
				{
					for (ent = self.teammaster; ent != null; ent = ent.teamchain)
						door_go_down.think(ent);
				}
			}
		}
	};
	static EntDieAdapter door_killed = new EntDieAdapter()
	{
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point)
		{
			edict_t ent;

			for (ent = self.teammaster; ent != null; ent = ent.teamchain)
			{
				ent.health = ent.max_health;
				ent.takedamage = Defines.DAMAGE_NO;
			}
			door_use.use(self.teammaster, attacker, attacker);
		}
	};
	static EntTouchAdapter door_touch = new EntTouchAdapter()
	{
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			if (null == other.client)
				return;

			if (GameBase.level.time < self.touch_debounce_time)
				return;
			self.touch_debounce_time = GameBase.level.time + 5.0f;

			GameBase.gi.centerprintf(other, self.message);
			GameBase.gi.sound(other, Defines.CHAN_AUTO, GameBase.gi.soundindex("misc/talk1.wav"), 1, Defines.ATTN_NORM, 0);
		}
	};
	static EntThinkAdapter SP_func_door = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			float[] abs_movedir = { 0, 0, 0 };

			if (ent.sounds != 1)
			{
				ent.moveinfo.sound_start = GameBase.gi.soundindex("doors/dr1_strt.wav");
				ent.moveinfo.sound_middle = GameBase.gi.soundindex("doors/dr1_mid.wav");
				ent.moveinfo.sound_end = GameBase.gi.soundindex("doors/dr1_end.wav");
			}

			GameBase.G_SetMovedir(ent.s.angles, ent.movedir);
			ent.movetype = Defines.MOVETYPE_PUSH;
			ent.solid = Defines.SOLID_BSP;
			GameBase.gi.setmodel(ent, ent.model);

			ent.blocked = door_blocked;
			ent.use = door_use;

			if (0 == ent.speed)
				ent.speed = 100;
			if (GameBase.deathmatch.value != 0)
				ent.speed *= 2;

			if (0 == ent.accel)
				ent.accel = ent.speed;
			if (0 == ent.decel)
				ent.decel = ent.speed;

			if (0 == ent.wait)
				ent.wait = 3;
			if (0 == GameBase.st.lip)
				GameBase.st.lip = 8;
			if (0 == ent.dmg)
				ent.dmg = 2;

			// calculate second position
			Math3D.VectorCopy(ent.s.origin, ent.pos1);
			abs_movedir[0] = Math.abs(ent.movedir[0]);
			abs_movedir[1] = Math.abs(ent.movedir[1]);
			abs_movedir[2] = Math.abs(ent.movedir[2]);
			ent.moveinfo.distance =
				abs_movedir[0] * ent.size[0] + abs_movedir[1] * ent.size[1] + abs_movedir[2] * ent.size[2] - GameBase.st.lip;

			Math3D.VectorMA(ent.pos1, ent.moveinfo.distance, ent.movedir, ent.pos2);

			// if it starts open, switch the positions
			if ((ent.spawnflags & DOOR_START_OPEN) != 0)
			{
				Math3D.VectorCopy(ent.pos2, ent.s.origin);
				Math3D.VectorCopy(ent.pos1, ent.pos2);
				Math3D.VectorCopy(ent.s.origin, ent.pos1);
			}

			ent.moveinfo.state = STATE_BOTTOM;

			if (ent.health != 0)
			{
				ent.takedamage = Defines.DAMAGE_YES;
				ent.die = door_killed;
				ent.max_health = ent.health;
			}
			else if (ent.targetname != null && ent.message != null)
			{
				GameBase.gi.soundindex("misc/talk.wav");
				ent.touch = door_touch;
			}

			ent.moveinfo.speed = ent.speed;
			ent.moveinfo.accel = ent.accel;
			ent.moveinfo.decel = ent.decel;
			ent.moveinfo.wait = ent.wait;
			Math3D.VectorCopy(ent.pos1, ent.moveinfo.start_origin);
			Math3D.VectorCopy(ent.s.angles, ent.moveinfo.start_angles);
			Math3D.VectorCopy(ent.pos2, ent.moveinfo.end_origin);
			Math3D.VectorCopy(ent.s.angles, ent.moveinfo.end_angles);

			if ((ent.spawnflags & 16) != 0)
				ent.s.effects |= Defines.EF_ANIM_ALL;
			if ((ent.spawnflags & 64) != 0)
				ent.s.effects |= Defines.EF_ANIM_ALLFAST;

			// to simplify logic elsewhere, make non-teamed doors into a team of one
			if (null == ent.team)
				ent.teammaster = ent;

			GameBase.gi.linkentity(ent);

			ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
			if (ent.health != 0 || ent.targetname != null)
				ent.think = Think_CalcMoveSpeed;
			else
				ent.think = Think_SpawnDoorTrigger;
			return true;
		}
	};
	/*QUAKED func_door_rotating (0 .5 .8) ? START_OPEN REVERSE CRUSHER NOMONSTER ANIMATED TOGGLE X_AXIS Y_AXIS
	TOGGLE causes the door to wait in both the start and end states for a trigger event.
	
	START_OPEN	the door to moves to its destination when spawned, and operate in reverse.  It is used to temporarily or permanently close off an area when triggered (not useful for touch or takedamage doors).
	NOMONSTER	monsters will not trigger this door
	
	You need to have an origin brush as part of this entity.  The center of that brush will be
	the point around which it is rotated. It will rotate around the Z axis by default.  You can
	check either the X_AXIS or Y_AXIS box to change that.
	
	"distance" is how many degrees the door will be rotated.
	"speed" determines how fast the door moves; default value is 100.
	
	REVERSE will cause the door to rotate in the opposite direction.
	
	"message"	is printed when the door is touched if it is a trigger door and it hasn't been fired yet
	"angle"		determines the opening direction
	"targetname" if set, no touch field will be spawned and a remote button or trigger field activates the door.
	"health"	if set, door must be shot open
	"speed"		movement speed (100 default)
	"wait"		wait before returning (3 default, -1 = never return)
	"dmg"		damage to inflict when blocked (2 default)
	"sounds"
	1)	silent
	2)	light
	3)	medium
	4)	heavy
	*/

	static EntThinkAdapter SP_func_door_rotating = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			Math3D.VectorClear(ent.s.angles);

			// set the axis of rotation
			Math3D.VectorClear(ent.movedir);
			if ((ent.spawnflags & DOOR_X_AXIS) != 0)
				ent.movedir[2] = 1.0f;
			else if ((ent.spawnflags & DOOR_Y_AXIS) != 0)
				ent.movedir[0] = 1.0f;
			else // Z_AXIS
				ent.movedir[1] = 1.0f;

			// check for reverse rotation
			if ((ent.spawnflags & DOOR_REVERSE) != 0)
				Math3D.VectorNegate(ent.movedir, ent.movedir);

			if (0 == GameBase.st.distance)
			{
				GameBase.gi.dprintf(ent.classname + " at " + Lib.vtos(ent.s.origin) + " with no distance set\n");
				GameBase.st.distance = 90;
			}

			Math3D.VectorCopy(ent.s.angles, ent.pos1);
			Math3D.VectorMA(ent.s.angles, GameBase.st.distance, ent.movedir, ent.pos2);
			ent.moveinfo.distance = GameBase.st.distance;

			ent.movetype = Defines.MOVETYPE_PUSH;
			ent.solid = Defines.SOLID_BSP;
			GameBase.gi.setmodel(ent, ent.model);

			ent.blocked = door_blocked;
			ent.use = door_use;

			if (0 == ent.speed)
				ent.speed = 100;
			if (0 == ent.accel)
				ent.accel = ent.speed;
			if (0 == ent.decel)
				ent.decel = ent.speed;

			if (0 == ent.wait)
				ent.wait = 3;
			if (0 == ent.dmg)
				ent.dmg = 2;

			if (ent.sounds != 1)
			{
				ent.moveinfo.sound_start = GameBase.gi.soundindex("doors/dr1_strt.wav");
				ent.moveinfo.sound_middle = GameBase.gi.soundindex("doors/dr1_mid.wav");
				ent.moveinfo.sound_end = GameBase.gi.soundindex("doors/dr1_end.wav");
			}

			// if it starts open, switch the positions
			if ((ent.spawnflags & DOOR_START_OPEN) != 0)
			{
				Math3D.VectorCopy(ent.pos2, ent.s.angles);
				Math3D.VectorCopy(ent.pos1, ent.pos2);
				Math3D.VectorCopy(ent.s.angles, ent.pos1);
				Math3D.VectorNegate(ent.movedir, ent.movedir);
			}

			if (ent.health != 0)
			{
				ent.takedamage = Defines.DAMAGE_YES;
				ent.die = door_killed;
				ent.max_health = ent.health;
			}

			if (ent.targetname != null && ent.message != null)
			{
				GameBase.gi.soundindex("misc/talk.wav");
				ent.touch = door_touch;
			}

			ent.moveinfo.state = STATE_BOTTOM;
			ent.moveinfo.speed = ent.speed;
			ent.moveinfo.accel = ent.accel;
			ent.moveinfo.decel = ent.decel;
			ent.moveinfo.wait = ent.wait;
			Math3D.VectorCopy(ent.s.origin, ent.moveinfo.start_origin);
			Math3D.VectorCopy(ent.pos1, ent.moveinfo.start_angles);
			Math3D.VectorCopy(ent.s.origin, ent.moveinfo.end_origin);
			Math3D.VectorCopy(ent.pos2, ent.moveinfo.end_angles);

			if ((ent.spawnflags & 16) != 0)
				ent.s.effects |= Defines.EF_ANIM_ALL;

			// to simplify logic elsewhere, make non-teamed doors into a team of one
			if (ent.team == null)
				ent.teammaster = ent;

			GameBase.gi.linkentity(ent);

			ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
			if (ent.health != 0 || ent.targetname != null)
				ent.think = Think_CalcMoveSpeed;
			else
				ent.think = Think_SpawnDoorTrigger;
			return true;
		}
	};
	public final static int TRAIN_START_ON = 1;
	public final static int TRAIN_TOGGLE = 2;
	public final static int TRAIN_BLOCK_STOPS = 4;
	/*QUAKED func_train (0 .5 .8) ? START_ON TOGGLE BLOCK_STOPS
	Trains are moving platforms that players can ride.
	The targets origin specifies the min point of the train at each corner.
	The train spawns at the first target it is pointing at.
	If the train is the target of a button or trigger, it will not begin moving until activated.
	speed	default 100
	dmg		default	2
	noise	looping sound to play when the train is in motion
	
	*/

	static EntBlockedAdapter train_blocked = new EntBlockedAdapter()
	{

		public void blocked(edict_t self, edict_t other)
		{
			if (0 == (other.svflags & Defines.SVF_MONSTER) && (null == other.client))
			{
				// give it a chance to go away on it's own terms (like gibs)
				GameUtil.T_Damage(
					other,
					self,
					self,
					Globals.vec3_origin,
					other.s.origin,
					Globals.vec3_origin,
					100000,
					1,
					0,
					Defines.MOD_CRUSH);
				// if it's still there, nuke it
				if (other != null)
					GameAI.BecomeExplosion1(other);
				return;
			}

			if (GameBase.level.time < self.touch_debounce_time)
				return;

			if (self.dmg == 0)
				return;
			self.touch_debounce_time = GameBase.level.time + 0.5f;
			GameUtil.T_Damage(
				other,
				self,
				self,
				Globals.vec3_origin,
				other.s.origin,
				Globals.vec3_origin,
				self.dmg,
				1,
				0,
				Defines.MOD_CRUSH);
		}
	};
	static EntThinkAdapter train_wait = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.target_ent.pathtarget != null)
			{
				String savetarget;
				edict_t ent;

				ent = self.target_ent;
				savetarget = ent.target;
				ent.target = ent.pathtarget;
				GameUtil.G_UseTargets(ent, self.activator);
				ent.target = savetarget;

				// make sure we didn't get killed by a killtarget
				if (!self.inuse)
					return true;
			}

			if (self.moveinfo.wait != 0)
			{
				if (self.moveinfo.wait > 0)
				{
					self.nextthink = GameBase.level.time + self.moveinfo.wait;
					self.think = train_next;
				}
				else if (0 != (self.spawnflags & TRAIN_TOGGLE)) // && wait < 0
				{
					train_next.think(self);
					self.spawnflags &= ~TRAIN_START_ON;
					Math3D.VectorClear(self.velocity);
					self.nextthink = 0;
				}

				if (0 == (self.flags & Defines.FL_TEAMSLAVE))
				{
					if (self.moveinfo.sound_end != 0)
						GameBase.gi.sound(
							self,
							Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
							self.moveinfo.sound_end,
							1,
							Defines.ATTN_STATIC,
							0);
					self.s.sound = 0;
				}
			}
			else
			{
				train_next.think(self);
			}
			return true;
		}
	};
	static EntThinkAdapter train_next = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			edict_t ent = null;
			float[] dest = { 0, 0, 0 };
			boolean first;

			first = true;

			boolean dogoto = true;
			while (dogoto)
			{
				if (null == self.target)
				{
					//			gi.dprintf ("train_next: no next target\n");
					return true;
				}

				ent = GameBase.G_PickTarget(self.target);
				if (null == ent)
				{
					GameBase.gi.dprintf("train_next: bad target " + self.target + "\n");
					return true;
				}

				self.target = ent.target;
				dogoto = false;
				// check for a teleport path_corner
				if ((ent.spawnflags & 1) != 0)
				{
					if (!first)
					{
						GameBase.gi.dprintf(
							"connected teleport path_corners, see " + ent.classname + " at " + Lib.vtos(ent.s.origin) + "\n");
						return true;
					}
					first = false;
					Math3D.VectorSubtract(ent.s.origin, self.mins, self.s.origin);
					Math3D.VectorCopy(self.s.origin, self.s.old_origin);
					self.s.event = Defines.EV_OTHER_TELEPORT;
					GameBase.gi.linkentity(self);
					dogoto = true;
				}
			}
			self.moveinfo.wait = ent.wait;
			self.target_ent = ent;

			if (0 == (self.flags & Defines.FL_TEAMSLAVE))
			{
				if (self.moveinfo.sound_start != 0)
					GameBase.gi.sound(
						self,
						Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE,
						self.moveinfo.sound_start,
						1,
						Defines.ATTN_STATIC,
						0);
				self.s.sound = self.moveinfo.sound_middle;
			}

			Math3D.VectorSubtract(ent.s.origin, self.mins, dest);
			self.moveinfo.state = STATE_TOP;
			Math3D.VectorCopy(self.s.origin, self.moveinfo.start_origin);
			Math3D.VectorCopy(dest, self.moveinfo.end_origin);
			GameFunc.Move_Calc(self, dest, train_wait);
			self.spawnflags |= TRAIN_START_ON;
			return true;
		}
	};
	public static EntThinkAdapter func_train_find = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			edict_t ent;

			if (null == self.target)
			{
				GameBase.gi.dprintf("train_find: no target\n");
				return true;
			}
			ent = GameBase.G_PickTarget(self.target);
			if (null == ent)
			{
				GameBase.gi.dprintf("train_find: target " + self.target + " not found\n");
				return true;
			}
			self.target = ent.target;

			Math3D.VectorSubtract(ent.s.origin, self.mins, self.s.origin);
			GameBase.gi.linkentity(self);

			// if not triggered, start immediately
			if (null == self.targetname)
				self.spawnflags |= TRAIN_START_ON;

			if ((self.spawnflags & TRAIN_START_ON) != 0)
			{
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
				self.think = train_next;
				self.activator = self;
			}
			return true;
		}
	};
	public static EntUseAdapter train_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.activator = activator;

			if ((self.spawnflags & TRAIN_START_ON) != 0)
			{
				if (0 == (self.spawnflags & TRAIN_TOGGLE))
					return;
				self.spawnflags &= ~TRAIN_START_ON;
				Math3D.VectorClear(self.velocity);
				self.nextthink = 0;
			}
			else
			{
				if (self.target_ent != null)
					GameFunc.train_resume(self);
				else
					train_next.think(self);
			}
		}
	};
	/*QUAKED trigger_elevator (0.3 0.1 0.6) (-8 -8 -8) (8 8 8)
	*/
	static EntUseAdapter trigger_elevator_use = new EntUseAdapter()
	{

		public void use(edict_t self, edict_t other, edict_t activator)
		{
			edict_t target;

			if (0 != self.movetarget.nextthink)
			{
				//			gi.dprintf("elevator busy\n");
				return;
			}

			if (null == other.pathtarget)
			{
				GameBase.gi.dprintf("elevator used with no pathtarget\n");
				return;
			}

			target = GameBase.G_PickTarget(other.pathtarget);
			if (null == target)
			{
				GameBase.gi.dprintf("elevator used with bad pathtarget: " + other.pathtarget + "\n");
				return;
			}

			self.movetarget.target_ent = target;
			GameFunc.train_resume(self.movetarget);
		}
	};
	static EntThinkAdapter trigger_elevator_init = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (null == self.target)
			{
				GameBase.gi.dprintf("trigger_elevator has no target\n");
				return true;
			}
			self.movetarget = GameBase.G_PickTarget(self.target);
			if (null == self.movetarget)
			{
				GameBase.gi.dprintf("trigger_elevator unable to find target " + self.target + "\n");
				return true;
			}
			if (Lib.strcmp(self.movetarget.classname, "func_train") != 0)
			{
				GameBase.gi.dprintf("trigger_elevator target " + self.target + " is not a train\n");
				return true;
			}

			self.use = trigger_elevator_use;
			self.svflags = Defines.SVF_NOCLIENT;
			return true;
		}
	};
	static EntThinkAdapter SP_trigger_elevator = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.think = trigger_elevator_init;
			self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			return true;
		}
	};
	/*QUAKED func_timer (0.3 0.1 0.6) (-8 -8 -8) (8 8 8) START_ON
	"wait"			base time between triggering all targets, default is 1
	"random"		wait variance, default is 0
	
	so, the basic time between firing is a random time between
	(wait - random) and (wait + random)
	
	"delay"			delay before first firing when turned on, default is 0
	
	"pausetime"		additional delay used only the very first time
					and only if spawned with START_ON
	
	These can used but not touched.
	*/

	static EntThinkAdapter func_timer_think = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			GameUtil.G_UseTargets(self, self.activator);
			self.nextthink = GameBase.level.time + self.wait + Lib.crandom() * self.random;
			return true;
		}
	};
	static EntUseAdapter func_timer_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.activator = activator;

			// if on, turn it off
			if (self.nextthink != 0)
			{
				self.nextthink = 0;
				return;
			}

			// turn it on
			if (self.delay != 0)
				self.nextthink = GameBase.level.time + self.delay;
			else
				func_timer_think.think(self);
		}
	};
	/*QUAKED func_conveyor (0 .5 .8) ? START_ON TOGGLE
	Conveyors are stationary brushes that move what's on them.
	The brush should be have a surface with at least one current content enabled.
	speed	default 100
	*/

	static EntUseAdapter func_conveyor_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			if ((self.spawnflags & 1) != 0)
			{
				self.speed = 0;
				self.spawnflags &= ~1;
			}
			else
			{
				self.speed = self.count;
				self.spawnflags |= 1;
			}

			if (0 == (self.spawnflags & 2))
				self.count = 0;
		}
	};
	static EntThinkAdapter SP_func_conveyor = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{

			if (0 == self.speed)
				self.speed = 100;

			if (0 == (self.spawnflags & 1))
			{
				self.count = (int) self.speed;
				self.speed = 0;
			}

			self.use = func_conveyor_use;

			GameBase.gi.setmodel(self, self.model);
			self.solid = Defines.SOLID_BSP;
			GameBase.gi.linkentity(self);
			return true;
		}
	};
	/*QUAKED func_door_secret (0 .5 .8) ? always_shoot 1st_left 1st_down
	A secret door.  Slide back and then to the side.
	
	open_once		doors never closes
	1st_left		1st move is left of arrow
	1st_down		1st move is down from arrow
	always_shoot	door is shootebale even if targeted
	
	"angle"		determines the direction
	"dmg"		damage to inflic when blocked (default 2)
	"wait"		how long to hold in the open position (default 5, -1 means hold)
	*/

	public final static int SECRET_ALWAYS_SHOOT = 1;
	public final static int SECRET_1ST_LEFT = 2;
	public final static int SECRET_1ST_DOWN = 4;
	static EntUseAdapter door_secret_use = new EntUseAdapter()
	{

		public void use(edict_t self, edict_t other, edict_t activator)
		{
				// make sure we're not already moving
	if (0 == Math3D.VectorCompare(self.s.origin, Globals.vec3_origin))
				return;

			GameFunc.Move_Calc(self, self.pos1, door_secret_move1);
			GameFunc.door_use_areaportals(self, true);
		}
	};
	static EntThinkAdapter door_secret_move1 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.nextthink = GameBase.level.time + 1.0f;
			self.think = door_secret_move2;
			return true;
		}
	};
	static EntThinkAdapter door_secret_move2 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			GameFunc.Move_Calc(self, self.pos2, door_secret_move3);
			return true;
		}
	};
	static EntThinkAdapter door_secret_move3 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.wait == -1)
				return true;
			self.nextthink = GameBase.level.time + self.wait;
			self.think = door_secret_move4;
			return true;
		}
	};
	static EntThinkAdapter door_secret_move4 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			GameFunc.Move_Calc(self, self.pos1, door_secret_move5);
			return true;
		}
	};
	static EntThinkAdapter door_secret_move5 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.nextthink = GameBase.level.time + 1.0f;
			self.think = door_secret_move6;
			return true;
		}
	};
	static EntThinkAdapter door_secret_move6 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{

			GameFunc.Move_Calc(self, Globals.vec3_origin, door_secret_done);
			return true;
		}
	};
	static EntThinkAdapter door_secret_done = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (null == (self.targetname) || 0 != (self.spawnflags & SECRET_ALWAYS_SHOOT))
			{
				self.health = 0;
				self.takedamage = Defines.DAMAGE_YES;
			}
			GameFunc.door_use_areaportals(self, false);
			return true;
		}
	};
	static EntBlockedAdapter door_secret_blocked = new EntBlockedAdapter()
	{

		public void blocked(edict_t self, edict_t other)
		{
			if (0 == (other.svflags & Defines.SVF_MONSTER) && (null == other.client))
			{
				// give it a chance to go away on it's own terms (like gibs)
				GameUtil.T_Damage(
					other,
					self,
					self,
					Globals.vec3_origin,
					other.s.origin,
					Globals.vec3_origin,
					100000,
					1,
					0,
					Defines.MOD_CRUSH);
				// if it's still there, nuke it
				if (other != null)
					GameAI.BecomeExplosion1(other);
				return;
			}

			if (GameBase.level.time < self.touch_debounce_time)
				return;
			self.touch_debounce_time = GameBase.level.time + 0.5f;

			GameUtil.T_Damage(
				other,
				self,
				self,
				Globals.vec3_origin,
				other.s.origin,
				Globals.vec3_origin,
				self.dmg,
				1,
				0,
				Defines.MOD_CRUSH);
		}
	};
	static EntDieAdapter door_secret_die = new EntDieAdapter()
	{
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point)
		{
			self.takedamage = Defines.DAMAGE_NO;
			door_secret_use.use(self, attacker, attacker);
		}
	};
	static EntThinkAdapter SP_func_door_secret = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 }, up = { 0, 0, 0 };
			float side;
			float width;
			float length;

			ent.moveinfo.sound_start = GameBase.gi.soundindex("doors/dr1_strt.wav");
			ent.moveinfo.sound_middle = GameBase.gi.soundindex("doors/dr1_mid.wav");
			ent.moveinfo.sound_end = GameBase.gi.soundindex("doors/dr1_end.wav");

			ent.movetype = Defines.MOVETYPE_PUSH;
			ent.solid = Defines.SOLID_BSP;
			GameBase.gi.setmodel(ent, ent.model);

			ent.blocked = door_secret_blocked;
			ent.use = door_secret_use;

			if (null == (ent.targetname) || 0 != (ent.spawnflags & SECRET_ALWAYS_SHOOT))
			{
				ent.health = 0;
				ent.takedamage = Defines.DAMAGE_YES;
				ent.die = door_secret_die;
			}

			if (0 == ent.dmg)
				ent.dmg = 2;

			if (0 == ent.wait)
				ent.wait = 5;

			ent.moveinfo.accel = ent.moveinfo.decel = ent.moveinfo.speed = 50;

			// calculate positions
			Math3D.AngleVectors(ent.s.angles, forward, right, up);
			Math3D.VectorClear(ent.s.angles);
			side = 1.0f - (ent.spawnflags & SECRET_1ST_LEFT);
			if ((ent.spawnflags & SECRET_1ST_DOWN) != 0)
				width = Math.abs(Math3D.DotProduct(up, ent.size));
			else
				width = Math.abs(Math3D.DotProduct(right, ent.size));
			length = Math.abs(Math3D.DotProduct(forward, ent.size));
			if ((ent.spawnflags & SECRET_1ST_DOWN) != 0)
				Math3D.VectorMA(ent.s.origin, -1 * width, up, ent.pos1);
			else
				Math3D.VectorMA(ent.s.origin, side * width, right, ent.pos1);
			Math3D.VectorMA(ent.pos1, length, forward, ent.pos2);

			if (ent.health != 0)
			{
				ent.takedamage = Defines.DAMAGE_YES;
				ent.die = door_killed;
				ent.max_health = ent.health;
			}
			else if (ent.targetname != null && ent.message != null)
			{
				GameBase.gi.soundindex("misc/talk.wav");
				ent.touch = door_touch;
			}

			ent.classname = "func_door";

			GameBase.gi.linkentity(ent);
			return true;
		}
	};
	/*QUAKED func_killbox (1 0 0) ?
	Kills everything inside when fired, irrespective of protection.
	*/
	static EntUseAdapter use_killbox = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			GameUtil.KillBox(self);
		}
	};
	static EntThinkAdapter SP_func_killbox = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			GameBase.gi.setmodel(ent, ent.model);
			ent.use = use_killbox;
			ent.svflags = Defines.SVF_NOCLIENT;
			return true;
		}
	};
}
