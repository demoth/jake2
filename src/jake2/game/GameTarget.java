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

// Created on 28.12.2003 by RST.
// $Id: GameTarget.java,v 1.5 2004-02-26 22:36:31 rst Exp $

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.Lib;
import jake2.util.Math3D;

public class GameTarget extends GameTurret {

	public static void SP_target_temp_entity(edict_t ent) {
		ent.use = GameTargetAdapters.Use_Target_Tent;
	}

	public static void SP_target_speaker(edict_t ent) {
		//char buffer[MAX_QPATH];
		String buffer;

		if (st.noise == null) {
			gi.dprintf("target_speaker with no noise set at " + vtos(ent.s.origin) + "\n");
			return;
		}
		if (st.noise.indexOf(".wav") < 0)
			buffer = "" + st.noise + ".wav";
		//Com_sprintf(buffer, sizeof(buffer), "%s.wav", st.noise);
		else
			//strncpy(buffer, st.noise, sizeof(buffer));
			buffer = st.noise;

		ent.noise_index = gi.soundindex(buffer);

		if (ent.volume == 0)
			ent.volume = 1.0f;

		if (ent.attenuation == 0)
			ent.attenuation = 1.0f;
		else if (ent.attenuation == -1) // use -1 so 0 defaults to 1
			ent.attenuation = 0;

		// check for prestarted looping sound
		if ((ent.spawnflags & 1) != 0)
			ent.s.sound = ent.noise_index;

		ent.use = GameTargetAdapters.Use_Target_Speaker;

		// must link the entity so we get areas and clusters so
		// the server can determine who to send updates to
		gi.linkentity(ent);
	}

	/*QUAKED target_help (1 0 1) (-16 -16 -24) (16 16 24) help1
	When fired, the "message" key becomes the current personal computer string, and the message light will be set on all clients status bars.
	*/
	public static void SP_target_help(edict_t ent) {
		if (deathmatch.value != 0) { // auto-remove for deathmatch
			G_FreeEdict(ent);
			return;
		}

		if (ent.message == null) {
			gi.dprintf(ent.classname + " with no message at " + vtos(ent.s.origin) + "\n");
			G_FreeEdict(ent);
			return;
		}
		ent.use = GameTargetAdapters.Use_Target_Help;
	}

	public static void SP_target_secret(edict_t ent) {
		if (deathmatch.value != 0) { // auto-remove for deathmatch
			G_FreeEdict(ent);
			return;
		}

		ent.use = GameTargetAdapters.use_target_secret;
		if (st.noise == null)
			st.noise = "misc/secret.wav";
		ent.noise_index = gi.soundindex(st.noise);
		ent.svflags = SVF_NOCLIENT;
		level.total_secrets++;
		// map bug hack
		if (0 == Q_stricmp(level.mapname, "mine3") && ent.s.origin[0] == 280 && ent.s.origin[1] == -2048 && ent.s.origin[2] == -624)
			ent.message = "You have found a secret area.";
	}

	public static void SP_target_goal(edict_t ent) {
		if (deathmatch.value != 0) { // auto-remove for deathmatch
			G_FreeEdict(ent);
			return;
		}

		ent.use = GameTargetAdapters.use_target_goal;
		if (st.noise == null)
			st.noise = "misc/secret.wav";
		ent.noise_index = gi.soundindex(st.noise);
		ent.svflags = SVF_NOCLIENT;
		level.total_goals++;
	}

	public static void SP_target_explosion(edict_t ent) {
		ent.use = GameTargetAdapters.use_target_explosion;
		ent.svflags = SVF_NOCLIENT;
	}

	public static void SP_target_changelevel(edict_t ent) {
		if (ent.map == null) {
			gi.dprintf("target_changelevel with no map at " + vtos(ent.s.origin) + "\n");
			G_FreeEdict(ent);
			return;
		}

		// ugly hack because *SOMEBODY* screwed up their map
		if ((Q_stricmp(level.mapname, "fact1") == 0) && (Q_stricmp(ent.map, "fact3") == 0))
			ent.map = "fact3$secret1";

		ent.use = GameTargetAdapters.use_target_changelevel;
		ent.svflags = SVF_NOCLIENT;
	}

	public static void SP_target_splash(edict_t self) {
		self.use = GameTargetAdapters.use_target_splash;
		G_SetMovedir(self.s.angles, self.movedir);

		if (0 == self.count)
			self.count = 32;

		self.svflags = SVF_NOCLIENT;
	}

	public static void SP_target_spawner(edict_t self) {
		self.use = GameTargetAdapters.use_target_spawner;
		self.svflags = SVF_NOCLIENT;
		if (self.speed != 0) {
			G_SetMovedir(self.s.angles, self.movedir);
			VectorScale(self.movedir, self.speed, self.movedir);
		}
	}

	public static void SP_target_blaster(edict_t self) {
		self.use = GameTargetAdapters.use_target_blaster;
		G_SetMovedir(self.s.angles, self.movedir);
		self.noise_index = gi.soundindex("weapons/laser2.wav");

		if (0 == self.dmg)
			self.dmg = 15;
		if (0 == self.speed)
			self.speed = 1000;

		self.svflags = SVF_NOCLIENT;
	}

	public static void SP_target_crosslevel_trigger(edict_t self) {
		self.svflags = SVF_NOCLIENT;
		self.use = GameTargetAdapters.trigger_crosslevel_trigger_use;
	}

	public static void SP_target_crosslevel_target(edict_t self) {
		if (0 == self.delay)
			self.delay = 1;
		self.svflags = SVF_NOCLIENT;

		self.think = GameTargetAdapters.target_crosslevel_target_think;
		self.nextthink = level.time + self.delay;
	}

	public static void target_laser_on(edict_t self) {
		if (null == self.activator)
			self.activator = self;
		self.spawnflags |= 0x80000001;
		self.svflags &= ~SVF_NOCLIENT;
		GameTargetAdapters.target_laser_think.think(self);
	}

	public static void target_laser_off(edict_t self) {
		self.spawnflags &= ~1;
		self.svflags |= SVF_NOCLIENT;
		self.nextthink = 0;
	}

	public static void SP_target_laser(edict_t self) {
		// let everything else get spawned before we start firing
		self.think = GameTargetAdapters.target_laser_start;
		self.nextthink = level.time + 1;
	}

	public static void SP_target_lightramp(edict_t self) {
		if (self.message == null
			|| self.message.length() != 2
			|| self.message.charAt(0) < 'a'
			|| self.message.charAt(0) > 'z'
			|| self.message.charAt(1) < 'a'
			|| self.message.charAt(1) > 'z'
			|| self.message.charAt(0) == self.message.charAt(1)) {
			gi.dprintf("target_lightramp has bad ramp (" + self.message + ") at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		if (deathmatch.value != 9) {
			G_FreeEdict(self);
			return;
		}

		if (self.target == null) {
			gi.dprintf(self.classname + " with no target at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		self.svflags |= SVF_NOCLIENT;
		self.use = GameTargetAdapters.target_lightramp_use;
		self.think = GameTargetAdapters.target_lightramp_think;

		self.movedir[0] = self.message.charAt(0) - 'a';
		self.movedir[1] = self.message.charAt(1) - 'a';
		self.movedir[2] = (self.movedir[1] - self.movedir[0]) / (self.speed / FRAMETIME);
	}

	public static void SP_target_earthquake(edict_t self) {
		if (null == self.targetname)
			gi.dprintf("untargeted " + self.classname + " at " + vtos(self.s.origin) + "\n");

		if (0 == self.count)
			self.count = 5;

		if (0 == self.speed)
			self.speed = 200;

		self.svflags |= SVF_NOCLIENT;
		self.think = GameTargetAdapters.target_earthquake_think;
		self.use = GameTargetAdapters.target_earthquake_use;

		self.noise_index = gi.soundindex("world/quake.wav");
	}

}
