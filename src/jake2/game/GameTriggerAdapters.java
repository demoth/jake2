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
// $Id: GameTriggerAdapters.java,v 1.2 2004-08-22 15:46:19 salomo Exp $

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.*;

public class GameTriggerAdapters {

	// the wait time has passed, so set back up for another activation
	public static EntThinkAdapter multi_wait = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
	
			ent.nextthink = 0;
			return true;
		}
	};
	static EntUseAdapter Use_Multi = new EntUseAdapter() {
		public void use(edict_t ent, edict_t other, edict_t activator) {
			ent.activator = activator;
			GameTrigger.multi_trigger(ent);
		}
	};
	static EntTouchAdapter Touch_Multi = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			if (other.client != null) {
				if ((self.spawnflags & 2) != 0)
					return;
			}
			else if ((other.svflags & Defines.SVF_MONSTER) != 0) {
				if (0 == (self.spawnflags & 1))
					return;
			}
			else
				return;
	
			if (0 == Math3D.VectorCompare(self.movedir, Globals.vec3_origin)) {
				float[] forward = { 0, 0, 0 };
	
				Math3D.AngleVectors(other.s.angles, forward, null, null);
				if (Math3D.DotProduct(forward, self.movedir) < 0)
					return;
			}
	
			self.activator = other;
			GameTrigger.multi_trigger(self);
		}
	};
	/*QUAKED trigger_multiple (.5 .5 .5) ? MONSTER NOT_PLAYER TRIGGERED
	Variable sized repeatable trigger.  Must be targeted at one or more entities.
	If "delay" is set, the trigger waits some time after activating before firing.
	"wait" : Seconds between triggerings. (.2 default)
	sounds
	1)	secret
	2)	beep beep
	3)	large switch
	4)
	set "message" to text string
	*/
	static EntUseAdapter trigger_enable = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			self.solid = Defines.SOLID_TRIGGER;
			self.use = Use_Multi;
			GameBase.gi.linkentity(self);
		}
	};
	/*QUAKED trigger_relay (.5 .5 .5) (-8 -8 -8) (8 8 8)
	This fixed size trigger cannot be touched, it can only be fired by other events.
	*/
	public static EntUseAdapter trigger_relay_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			GameUtil.G_UseTargets(self, activator);
		}
	};
	/*
	==============================================================================
	
	trigger_key
	
	==============================================================================
	*/
	
	/*QUAKED trigger_key (.5 .5 .5) (-8 -8 -8) (8 8 8)
	A relay trigger that only fires it's targets if player has the proper key.
	Use "item" to specify the required key, for example "key_data_cd"
	*/
	
	static EntUseAdapter trigger_key_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			int index;
	
			if (self.item == null)
				return;
			if (activator.client == null)
				return;
	
			index = GameUtil.ITEM_INDEX(self.item);
			if (activator.client.pers.inventory[index] == 0) {
				if (GameBase.level.time < self.touch_debounce_time)
					return;
				self.touch_debounce_time = GameBase.level.time + 5.0f;
				GameBase.gi.centerprintf(activator, "You need the " + self.item.pickup_name);
				GameBase.gi.sound(activator, Defines.CHAN_AUTO, GameBase.gi.soundindex("misc/keytry.wav"), 1, Defines.ATTN_NORM, 0);
				return;
			}
	
			GameBase.gi.sound(activator, Defines.CHAN_AUTO, GameBase.gi.soundindex("misc/keyuse.wav"), 1, Defines.ATTN_NORM, 0);
			if (GameBase.coop.value != 0) {
				int player;
				edict_t ent;
	
				if (Lib.strcmp(self.item.classname, "key_power_cube") == 0) {
					int cube;
	
					for (cube = 0; cube < 8; cube++)
						if ((activator.client.pers.power_cubes & (1 << cube)) != 0)
							break;
					for (player = 1; player <= GameBase.game.maxclients; player++) {
						ent = GameBase.g_edicts[player];
						if (!ent.inuse)
							continue;
						if (null == ent.client)
							continue;
						if ((ent.client.pers.power_cubes & (1 << cube)) != 0) {
							ent.client.pers.inventory[index]--;
							ent.client.pers.power_cubes &= ~(1 << cube);
						}
					}
				}
				else {
					for (player = 1; player <= GameBase.game.maxclients; player++) {
						ent = GameBase.g_edicts[player];
						if (!ent.inuse)
							continue;
						if (ent.client == null)
							continue;
						ent.client.pers.inventory[index] = 0;
					}
				}
			}
			else {
				activator.client.pers.inventory[index]--;
			}
	
			GameUtil.G_UseTargets(self, activator);
	
			self.use = null;
		}
	};
	/*
	==============================================================================
	
	trigger_counter
	
	==============================================================================
	*/
	
	/*QUAKED trigger_counter (.5 .5 .5) ? nomessage
	Acts as an intermediary for an action that takes multiple inputs.
	
	If nomessage is not set, t will print "1 more.. " etc when triggered and "sequence complete" when finished.
	
	After the counter has been triggered "count" times (default 2), it will fire all of it's targets and remove itself.
	*/
	static EntUseAdapter trigger_counter_use = new EntUseAdapter() {
	
		public void use(edict_t self, edict_t other, edict_t activator) {
			if (self.count == 0)
				return;
	
			self.count--;
	
			if (self.count == 0) {
				if (0 == (self.spawnflags & 1)) {
					GameBase.gi.centerprintf(activator, self.count + " more to go...");
					GameBase.gi.sound(activator, Defines.CHAN_AUTO, GameBase.gi.soundindex("misc/talk1.wav"), 1, Defines.ATTN_NORM, 0);
				}
				return;
			}
	
			if (0 == (self.spawnflags & 1)) {
				GameBase.gi.centerprintf(activator, "Sequence completed!");
				GameBase.gi.sound(activator, Defines.CHAN_AUTO, GameBase.gi.soundindex("misc/talk1.wav"), 1, Defines.ATTN_NORM, 0);
			}
			self.activator = activator;
			GameTrigger.multi_trigger(self);
		}
	};
	/*
	==============================================================================
	
	trigger_push
	
	==============================================================================
	*/
	
	public static final int PUSH_ONCE = 1;
	public static int windsound;
	static EntTouchAdapter trigger_push_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			if (Lib.strcmp(other.classname, "grenade") == 0) {
				Math3D.VectorScale(self.movedir, self.speed * 10, other.velocity);
			}
			else if (other.health > 0) {
				Math3D.VectorScale(self.movedir, self.speed * 10, other.velocity);
	
				if (other.client != null) {
					// don't take falling damage immediately from this
					Math3D.VectorCopy(other.velocity, other.client.oldvelocity);
					if (other.fly_sound_debounce_time < GameBase.level.time) {
						other.fly_sound_debounce_time = GameBase.level.time + 1.5f;
						GameBase.gi.sound(other, Defines.CHAN_AUTO, windsound, 1, Defines.ATTN_NORM, 0);
					}
				}
			}
			if ((self.spawnflags & PUSH_ONCE) != 0)
				GameUtil.G_FreeEdict(self);
		}
	};
	/*
	==============================================================================
	
	trigger_hurt
	
	==============================================================================
	*/
	
	/*QUAKED trigger_hurt (.5 .5 .5) ? START_OFF TOGGLE SILENT NO_PROTECTION SLOW
	Any entity that touches this will be hurt.
	
	It does dmg points of damage each server frame
	
	SILENT			supresses playing the sound
	SLOW			changes the damage rate to once per second
	NO_PROTECTION	*nothing* stops the damage
	
	"dmg"			default 5 (whole numbers only)
	
	*/
	static EntUseAdapter hurt_use = new EntUseAdapter() {
	
		public void use(edict_t self, edict_t other, edict_t activator) {
			if (self.solid == Defines.SOLID_NOT)
				self.solid = Defines.SOLID_TRIGGER;
			else
				self.solid = Defines.SOLID_NOT;
			GameBase.gi.linkentity(self);
	
			if (0 == (self.spawnflags & 2))
				self.use = null;
		}
	};
	static EntTouchAdapter hurt_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			int dflags;
	
			if (other.takedamage == 0)
				return;
	
			if (self.timestamp > GameBase.level.time)
				return;
	
			if ((self.spawnflags & 16) != 0)
				self.timestamp = GameBase.level.time + 1;
			else
				self.timestamp = GameBase.level.time + Defines.FRAMETIME;
	
			if (0 == (self.spawnflags & 4)) {
				if ((GameBase.level.framenum % 10) == 0)
					GameBase.gi.sound(other, Defines.CHAN_AUTO, self.noise_index, 1, Defines.ATTN_NORM, 0);
			}
	
			if ((self.spawnflags & 8) != 0)
				dflags = Defines.DAMAGE_NO_PROTECTION;
			else
				dflags = 0;
			GameUtil.T_Damage(other, self, self, Globals.vec3_origin, other.s.origin, Globals.vec3_origin, self.dmg, self.dmg, dflags, Defines.MOD_TRIGGER_HURT);
		}
	};
	/*
	==============================================================================
	
	trigger_gravity
	
	==============================================================================
	*/
	
	/*QUAKED trigger_gravity (.5 .5 .5) ?
	Changes the touching entites gravity to
	the value of "gravity".  1.0 is standard
	gravity for the level.
	*/
	
	static EntTouchAdapter trigger_gravity_touch = new EntTouchAdapter() {
	
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			other.gravity = self.gravity;
		}
	};
	/*
	==============================================================================
	
	trigger_monsterjump
	
	==============================================================================
	*/
	
	/*QUAKED trigger_monsterjump (.5 .5 .5) ?
	Walking monsters that touch this will jump in the direction of the trigger's angle
	"speed" default to 200, the speed thrown forward
	"height" default to 200, the speed thrown upwards
	*/
	
	static EntTouchAdapter trigger_monsterjump_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			if ((other.flags & (Defines.FL_FLY | Defines.FL_SWIM)) != 0)
				return;
			if ((other.svflags & Defines.SVF_DEADMONSTER) != 0)
				return;
			if (0 == (other.svflags & Defines.SVF_MONSTER))
				return;
	
			// set XY even if not on ground, so the jump will clear lips
			other.velocity[0] = self.movedir[0] * self.speed;
			other.velocity[1] = self.movedir[1] * self.speed;
	
			if (other.groundentity != null)
				return;
	
			other.groundentity = null;
			other.velocity[2] = self.movedir[2];
		}
	};
}
