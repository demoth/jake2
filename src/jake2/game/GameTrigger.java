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
// $Id: GameTrigger.java,v 1.2 2004-07-08 15:58:44 hzi Exp $

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.Lib;
import jake2.util.Math3D;

public class GameTrigger extends M_Player {

	public static void InitTrigger(edict_t self) {
		if (Math3D.VectorCompare(self.s.angles, vec3_origin) != 0)
			G_SetMovedir(self.s.angles, self.movedir);

		self.solid = SOLID_TRIGGER;
		self.movetype = MOVETYPE_NONE;
		gi.setmodel(self, self.model);
		self.svflags = SVF_NOCLIENT;
	}

	// the trigger was just activated
	// ent.activator should be set to the activator so it can be held through a delay
	// so wait for the delay time before firing
	public static void multi_trigger(edict_t ent) {
		if (ent.nextthink != 0)
			return; // already been triggered

		G_UseTargets(ent, ent.activator);

		if (ent.wait > 0) {
			ent.think = GameTriggerAdapters.multi_wait;
			ent.nextthink = level.time + ent.wait;
		}
		else { // we can't just remove (self) here, because this is a touch function
			// called while looping through area links...
			ent.touch = null;
			ent.nextthink = level.time + FRAMETIME;
			ent.think = GameUtilAdapters.G_FreeEdictA;
		}
	}

	public static void SP_trigger_multiple(edict_t ent) {
		if (ent.sounds == 1)
			ent.noise_index = gi.soundindex("misc/secret.wav");
		else if (ent.sounds == 2)
			ent.noise_index = gi.soundindex("misc/talk.wav");
		else if (ent.sounds == 3)
			ent.noise_index = gi.soundindex("misc/trigger1.wav");

		if (ent.wait == 0)
			ent.wait = 0.2f;
			
		ent.touch = GameTriggerAdapters.Touch_Multi;
		ent.movetype = MOVETYPE_NONE;
		ent.svflags |= SVF_NOCLIENT;

		if ((ent.spawnflags & 4) != 0) {
			ent.solid = SOLID_NOT;
			ent.use = GameTriggerAdapters.trigger_enable;
		}
		else {
			ent.solid = SOLID_TRIGGER;
			ent.use = GameTriggerAdapters.Use_Multi;
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

	public static void SP_trigger_relay(edict_t self) {
		self.use = GameTriggerAdapters.trigger_relay_use;
	}

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

		self.use = GameTriggerAdapters.trigger_key_use;
	}

	public static void SP_trigger_counter(edict_t self) {
		self.wait = -1;
		if (0 == self.count)
			self.count = 2;

		self.use = GameTriggerAdapters.trigger_counter_use;
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

	/*QUAKED trigger_push (.5 .5 .5) ? PUSH_ONCE
	Pushes the player
	"speed"		defaults to 1000
	*/
	public static void SP_trigger_push(edict_t self) {
		InitTrigger(self);
		GameTriggerAdapters.windsound = gi.soundindex("misc/windfly.wav");
		self.touch = GameTriggerAdapters.trigger_push_touch;
		if (0 == self.speed)
			self.speed = 1000;
		gi.linkentity(self);
	}

	public static void SP_trigger_hurt(edict_t self) {
		InitTrigger(self);

		self.noise_index = gi.soundindex("world/electro.wav");
		self.touch = GameTriggerAdapters.hurt_touch;

		if (0 == self.dmg)
			self.dmg = 5;

		if ((self.spawnflags & 1) != 0)
			self.solid = SOLID_NOT;
		else
			self.solid = SOLID_TRIGGER;

		if ((self.spawnflags & 2) != 0)
			self.use = GameTriggerAdapters.hurt_use;

		gi.linkentity(self);
	}

	public static void SP_trigger_gravity(edict_t self) {
		if (st.gravity == null) {
			gi.dprintf("trigger_gravity without gravity set at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		InitTrigger(self);
		self.gravity = atoi(st.gravity);
		self.touch = GameTriggerAdapters.trigger_gravity_touch;
	}

	public static void SP_trigger_monsterjump(edict_t self) {
		if (0 == self.speed)
			self.speed = 200;
		if (0 == st.height)
			st.height = 200;
		if (self.s.angles[YAW] == 0)
			self.s.angles[YAW] = 360;
		InitTrigger(self);
		self.touch = GameTriggerAdapters.trigger_monsterjump_touch;
		self.movedir[2] = st.height;
	}

}
