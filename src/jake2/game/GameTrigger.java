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

// Created on 27.12.2003 by RST.
// $Id: GameTrigger.java,v 1.1 2003-12-27 21:33:50 rst Exp $

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.Math3D;

public class GameTrigger extends GamePWeapon {

	public static void InitTrigger(edict_t self) {
		if (Math3D.VectorCompare(self.s.angles, vec3_origin) != 0)
			G_SetMovedir(self.s.angles, self.movedir);

		self.solid = SOLID_TRIGGER;
		self.movetype = MOVETYPE_NONE;
		gi.setmodel(self, self.model);
		self.svflags = SVF_NOCLIENT;
	}

	// the wait time has passed, so set back up for another activation
	public static EntThinkAdapter multi_wait = new EntThinkAdapter() {
		public boolean think(edict_t ent) {

			ent.nextthink = 0;
			return true;
		}
	};

	// the trigger was just activated
	// ent.activator should be set to the activator so it can be held through a delay
	// so wait for the delay time before firing
	public static void multi_trigger(edict_t ent) {
		if (ent.nextthink != 0)
			return; // already been triggered

		G_UseTargets(ent, ent.activator);

		if (ent.wait > 0) {
			ent.think = multi_wait;
			ent.nextthink = level.time + ent.wait;
		}
		else { // we can't just remove (self) here, because this is a touch function
			// called while looping through area links...
			ent.touch = null;
			ent.nextthink = level.time + FRAMETIME;
			ent.think = G_FreeEdictA;
		}
	}

	static EntUseAdapter Use_Multi = new EntUseAdapter() {
		public void use(edict_t ent, edict_t other, edict_t activator) {
			ent.activator = activator;
			multi_trigger(ent);
		}
	};

	static EntTouchAdapter Touch_Multi = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			if (other.client != null) {
				if ((self.spawnflags & 2) != 0)
					return;
			}
			else if ((other.svflags & SVF_MONSTER) != 0) {
				if (0 == (self.spawnflags & 1))
					return;
			}
			else
				return;

			if (0 == VectorCompare(self.movedir, vec3_origin)) {
				float[] forward = { 0, 0, 0 };

				AngleVectors(other.s.angles, forward, null, null);
				if (DotProduct(forward, self.movedir) < 0)
					return;
			}

			self.activator = other;
			multi_trigger(self);
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
			self.solid = SOLID_TRIGGER;
			self.use = Use_Multi;
			gi.linkentity(self);
		}
	};
	public static void SP_trigger_multiple(edict_t ent) {
		if (ent.sounds == 1)
			ent.noise_index = gi.soundindex("misc/secret.wav");
		else if (ent.sounds == 2)
			ent.noise_index = gi.soundindex("misc/talk.wav");
		else if (ent.sounds == 3)
			ent.noise_index = gi.soundindex("misc/trigger1.wav");

		if (ent.wait == 0)
			ent.wait = 0.2f;
		ent.touch = Touch_Multi;
		ent.movetype = MOVETYPE_NONE;
		ent.svflags |= SVF_NOCLIENT;

		if ((ent.spawnflags & 4) != 0) {
			ent.solid = SOLID_NOT;
			ent.use = trigger_enable;
		}
		else {
			ent.solid = SOLID_TRIGGER;
			ent.use = Use_Multi;
		}

		if (0 == Math3D.VectorCompare(ent.s.angles, vec3_origin))
			G_SetMovedir(ent.s.angles, ent.movedir);

		gi.setmodel(ent, ent.model);
		gi.linkentity(ent);
	}

	/*QUAKED trigger_once (.5 .5 .5) ? x x TRIGGERED
	Triggers once, then removes itself.
	You must set the key "target" to the name of another object in the level that has a matching "targetname".
	
	If TRIGGERED, this trigger must be triggered before it is live.
	
	sounds
	 1)	secret
	 2)	beep beep
	 3)	large switch
	 4)
	
	"message"	string to be displayed when triggered
	*/

	public static void SP_trigger_once(edict_t ent) {
		// make old maps work because I messed up on flag assignments here
		// triggered was on bit 1 when it should have been on bit 4
		if ((ent.spawnflags & 1) != 0) {
			float[] v = { 0, 0, 0 };

			VectorMA(ent.mins, 0.5f, ent.size, v);
			ent.spawnflags &= ~1;
			ent.spawnflags |= 4;
			gi.dprintf("fixed TRIGGERED flag on " + ent.classname + " at " + vtos(v) + "\n");
		}

		ent.wait = -1;
		SP_trigger_multiple(ent);
	}

	/*QUAKED trigger_relay (.5 .5 .5) (-8 -8 -8) (8 8 8)
	This fixed size trigger cannot be touched, it can only be fired by other events.
	*/
	public static EntUseAdapter trigger_relay_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			G_UseTargets(self, activator);
		}
	};

	public static void SP_trigger_relay(edict_t self) {
		self.use = trigger_relay_use;
	}

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

			index = ITEM_INDEX(self.item);
			if (activator.client.pers.inventory[index] == 0) {
				if (level.time < self.touch_debounce_time)
					return;
				self.touch_debounce_time = level.time + 5.0f;
				gi.centerprintf(activator, "You need the " + self.item.pickup_name);
				gi.sound(activator, CHAN_AUTO, gi.soundindex("misc/keytry.wav"), 1, ATTN_NORM, 0);
				return;
			}

			gi.sound(activator, CHAN_AUTO, gi.soundindex("misc/keyuse.wav"), 1, ATTN_NORM, 0);
			if (coop.value != 0) {
				int player;
				edict_t ent;

				if (strcmp(self.item.classname, "key_power_cube") == 0) {
					int cube;

					for (cube = 0; cube < 8; cube++)
						if ((activator.client.pers.power_cubes & (1 << cube)) != 0)
							break;
					for (player = 1; player <= game.maxclients; player++) {
						ent = g_edicts[player];
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
					for (player = 1; player <= game.maxclients; player++) {
						ent = g_edicts[player];
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

			G_UseTargets(self, activator);

			self.use = null;
		}
	};

	public static void SP_trigger_key(edict_t self) {
		if (st.item == null) {
			gi.dprintf("no key item for trigger_key at " + vtos(self.s.origin) + "\n");
			return;
		}
		self.item = FindItemByClassname(st.item);

		if (null == self.item) {
			gi.dprintf("item " + st.item + " not found for trigger_key at " + vtos(self.s.origin) + "\n");
			return;
		}

		if (self.target == null) {
			gi.dprintf(self.classname + " at " + vtos(self.s.origin) + " has no target\n");
			return;
		}

		gi.soundindex("misc/keytry.wav");
		gi.soundindex("misc/keyuse.wav");

		self.use = trigger_key_use;
	}

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
					gi.centerprintf(activator, self.count + " more to go...");
					gi.sound(activator, CHAN_AUTO, gi.soundindex("misc/talk1.wav"), 1, ATTN_NORM, 0);
				}
				return;
			}

			if (0 == (self.spawnflags & 1)) {
				gi.centerprintf(activator, "Sequence completed!");
				gi.sound(activator, CHAN_AUTO, gi.soundindex("misc/talk1.wav"), 1, ATTN_NORM, 0);
			}
			self.activator = activator;
			multi_trigger(self);
		}
	};

	public static void SP_trigger_counter(edict_t self) {
		self.wait = -1;
		if (0 == self.count)
			self.count = 2;

		self.use = trigger_counter_use;
	}

	/*
	==============================================================================
	
	trigger_always
	
	==============================================================================
	*/

	/*QUAKED trigger_always (.5 .5 .5) (-8 -8 -8) (8 8 8)
	This trigger will always fire.  It is activated by the world.
	*/
	public static void SP_trigger_always(edict_t ent) {
		// we must have some delay to make sure our use targets are present
		if (ent.delay < 0.2f)
			ent.delay = 0.2f;
		G_UseTargets(ent, ent);
	}

	/*
	==============================================================================
	
	trigger_push
	
	==============================================================================
	*/

	public static final int PUSH_ONCE = 1;

	public static int windsound;

	static EntTouchAdapter trigger_push_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			if (strcmp(other.classname, "grenade") == 0) {
				VectorScale(self.movedir, self.speed * 10, other.velocity);
			}
			else if (other.health > 0) {
				VectorScale(self.movedir, self.speed * 10, other.velocity);

				if (other.client != null) {
					// don't take falling damage immediately from this
					VectorCopy(other.velocity, other.client.oldvelocity);
					if (other.fly_sound_debounce_time < level.time) {
						other.fly_sound_debounce_time = level.time + 1.5f;
						gi.sound(other, CHAN_AUTO, windsound, 1, ATTN_NORM, 0);
					}
				}
			}
			if ((self.spawnflags & PUSH_ONCE) != 0)
				G_FreeEdict(self);
		}
	};

	/*QUAKED trigger_push (.5 .5 .5) ? PUSH_ONCE
	Pushes the player
	"speed"		defaults to 1000
	*/
	public static void SP_trigger_push(edict_t self) {
		InitTrigger(self);
		windsound = gi.soundindex("misc/windfly.wav");
		self.touch = trigger_push_touch;
		if (0 == self.speed)
			self.speed = 1000;
		gi.linkentity(self);
	}

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
			if (self.solid == SOLID_NOT)
				self.solid = SOLID_TRIGGER;
			else
				self.solid = SOLID_NOT;
			gi.linkentity(self);

			if (0 == (self.spawnflags & 2))
				self.use = null;
		}
	};
	static EntTouchAdapter hurt_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			int dflags;

			if (other.takedamage == 0)
				return;

			if (self.timestamp > level.time)
				return;

			if ((self.spawnflags & 16) != 0)
				self.timestamp = level.time + 1;
			else
				self.timestamp = level.time + FRAMETIME;

			if (0 == (self.spawnflags & 4)) {
				if ((level.framenum % 10) == 0)
					gi.sound(other, CHAN_AUTO, self.noise_index, 1, ATTN_NORM, 0);
			}

			if ((self.spawnflags & 8) != 0)
				dflags = DAMAGE_NO_PROTECTION;
			else
				dflags = 0;
			T_Damage(other, self, self, vec3_origin, other.s.origin, vec3_origin, self.dmg, self.dmg, dflags, MOD_TRIGGER_HURT);
		}
	};

	public static void SP_trigger_hurt(edict_t self) {
		InitTrigger(self);

		self.noise_index = gi.soundindex("world/electro.wav");
		self.touch = hurt_touch;

		if (0 == self.dmg)
			self.dmg = 5;

		if ((self.spawnflags & 1) != 0)
			self.solid = SOLID_NOT;
		else
			self.solid = SOLID_TRIGGER;

		if ((self.spawnflags & 2) != 0)
			self.use = hurt_use;

		gi.linkentity(self);
	}

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

	public static void SP_trigger_gravity(edict_t self) {
		if (st.gravity == null) {
			gi.dprintf("trigger_gravity without gravity set at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		InitTrigger(self);
		self.gravity = atoi(st.gravity);
		self.touch = trigger_gravity_touch;
	}

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
			if ((other.flags & (FL_FLY | FL_SWIM)) != 0)
				return;
			if ((other.svflags & SVF_DEADMONSTER) != 0)
				return;
			if (0 == (other.svflags & SVF_MONSTER))
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

	public static void SP_trigger_monsterjump(edict_t self) {
		if (0 == self.speed)
			self.speed = 200;
		if (0 == st.height)
			st.height = 200;
		if (self.s.angles[YAW] == 0)
			self.s.angles[YAW] = 360;
		InitTrigger(self);
		self.touch = trigger_monsterjump_touch;
		self.movedir[2] = st.height;
	}

}
