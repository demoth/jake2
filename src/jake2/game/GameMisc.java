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
// $Id: GameMisc.java,v 1.2 2004-02-22 21:45:47 hoz Exp $

package jake2.game;

import java.util.Date;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;

public class GameMisc extends GameTrigger {

	/*QUAKED func_group (0 0 0) ?
	Used to group brushes together just for editor convenience.
	*/

	//=====================================================

	static EntUseAdapter Use_Areaportal = new EntUseAdapter() {
		public void use(edict_t ent, edict_t other, edict_t activator) {
			ent.count ^= 1; // toggle state
			//	gi.dprintf ("portalstate: %i = %i\n", ent.style, ent.count);
			gi.SetAreaPortalState(ent.style, ent.count!=0);
		}
	};

	/*QUAKED func_areaportal (0 0 0) ?
	
	This is a non-visible object that divides the world into
	areas that are seperated when this portal is not activated.
	Usually enclosed in the middle of a door.
	*/

	static EntThinkAdapter SP_func_areaportal = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			ent.use = Use_Areaportal;
			ent.count = 0; // always start closed;
			return true;
		}
	};

	/*QUAKED path_corner (.5 .3 0) (-8 -8 -8) (8 8 8) TELEPORT
	Target: next path corner
	Pathtarget: gets used when an entity that has
		this path_corner targeted touches it
	*/
	static EntTouchAdapter path_corner_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			float[] v={0,0,0};
			edict_t next;

			if (other.movetarget != self)
				return;

			if (other.enemy != null)
				return;

			if (self.pathtarget != null) {
				String savetarget;

				savetarget = self.target;
				self.target = self.pathtarget;
				G_UseTargets(self, other);
				self.target = savetarget;
			}

			if (self.target != null)
				next = G_PickTarget(self.target);
			else
				next = null;

			if ((next != null) && (next.spawnflags & 1) != 0) {
				VectorCopy(next.s.origin, v);
				v[2] += next.mins[2];
				v[2] -= other.mins[2];
				VectorCopy(v, other.s.origin);
				next = G_PickTarget(next.target);
				other.s.event = EV_OTHER_TELEPORT;
			}

			other.goalentity = other.movetarget = next;

			if (self.wait != 0) {
				other.monsterinfo.pausetime = level.time + self.wait;
				other.monsterinfo.stand.think(other);
				return;
			}

			if (other.movetarget == null) {
				other.monsterinfo.pausetime = level.time + 100000000;
				other.monsterinfo.stand.think(other);
			}
			else {
				VectorSubtract(other.goalentity.s.origin, other.s.origin, v);
				other.ideal_yaw = vectoyaw(v);
			}
		}
	};

	public static void SP_path_corner(edict_t self) {
		if (self.targetname == null) {
			gi.dprintf("path_corner with no targetname at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		self.solid = SOLID_TRIGGER;
		self.touch = path_corner_touch;
		VectorSet(self.mins, -8, -8, -8);
		VectorSet(self.maxs, 8, 8, 8);
		self.svflags |= SVF_NOCLIENT;
		gi.linkentity(self);
	}

	/*QUAKED point_combat (0.5 0.3 0) (-8 -8 -8) (8 8 8) Hold
	Makes this the target of a monster and it will head here
	when first activated before going after the activator.  If
	hold is selected, it will stay here.
	*/
	static EntTouchAdapter point_combat_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			edict_t activator;

			if (other.movetarget != self)
				return;

			if (self.target != null) {
				other.target = self.target;
				other.goalentity = other.movetarget = G_PickTarget(other.target);
				if (null == other.goalentity) {
					gi.dprintf(self.classname + " at " + vtos(self.s.origin) + " target " + self.target + " does not exist\n");
					other.movetarget = self;
				}
				self.target = null;
			}
			else if ((self.spawnflags & 1) != 0 && 0 == (other.flags & (FL_SWIM | FL_FLY))) {
				other.monsterinfo.pausetime = level.time + 100000000;
				other.monsterinfo.aiflags |= AI_STAND_GROUND;
				other.monsterinfo.stand.think(other);
			}

			if (other.movetarget == self) {
				other.target = null;
				other.movetarget = null;
				other.goalentity = other.enemy;
				other.monsterinfo.aiflags &= ~AI_COMBAT_POINT;
			}

			if (self.pathtarget != null) {
				String savetarget;

				savetarget = self.target;
				self.target = self.pathtarget;
				if (other.enemy != null && other.enemy.client != null)
					activator = other.enemy;
				else if (other.oldenemy != null && other.oldenemy.client != null)
					activator = other.oldenemy;
				else if (other.activator != null && other.activator.client != null)
					activator = other.activator;
				else
					activator = other;
				G_UseTargets(self, activator);
				self.target = savetarget;
			}
		}
	};

	public static void SP_point_combat(edict_t self) {
		if (deathmatch.value != 0) {
			G_FreeEdict(self);
			return;
		}
		self.solid = SOLID_TRIGGER;
		self.touch = point_combat_touch;
		VectorSet(self.mins, -8, -8, -16);
		VectorSet(self.maxs, 8, 8, 16);
		self.svflags = SVF_NOCLIENT;
		gi.linkentity(self);
	};

	/*QUAKED viewthing (0 .5 .8) (-8 -8 -8) (8 8 8)
	Just for the debugging level.  Don't use
	*/
	public static EntThinkAdapter TH_viewthing = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			ent.s.frame = (ent.s.frame + 1) % 7;
			ent.nextthink = level.time + FRAMETIME;
			return true;
		}
	};

	public static void SP_viewthing(edict_t ent) {
		gi.dprintf("viewthing spawned\n");

		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		ent.s.renderfx = RF_FRAMELERP;
		VectorSet(ent.mins, -16, -16, -24);
		VectorSet(ent.maxs, 16, 16, 32);
		ent.s.modelindex = gi.modelindex("models/objects/banner/tris.md2");
		gi.linkentity(ent);
		ent.nextthink = level.time + 0.5f;
		ent.think = TH_viewthing;
		return;
	}

	/*QUAKED info_null (0 0.5 0) (-4 -4 -4) (4 4 4)
	Used as a positional target for spotlights, etc.
	*/
	public static void SP_info_null(edict_t self) {
		G_FreeEdict(self);
	};

	/*QUAKED info_notnull (0 0.5 0) (-4 -4 -4) (4 4 4)
	Used as a positional target for lightning.
	*/
	public static void SP_info_notnull(edict_t self) {
		VectorCopy(self.s.origin, self.absmin);
		VectorCopy(self.s.origin, self.absmax);
	};

	/*QUAKED light (0 1 0) (-8 -8 -8) (8 8 8) START_OFF
	Non-displayed light.
	Default light value is 300.
	Default style is 0.
	If targeted, will toggle between on and off.
	Default _cone value is 10 (used to set size of light for spotlights)
	*/

	public static final int START_OFF = 1;

	static EntUseAdapter light_use = new EntUseAdapter() {

		public void use(edict_t self, edict_t other, edict_t activator) {
			if ((self.spawnflags & START_OFF) != 0) {
				gi.configstring(CS_LIGHTS + self.style, "m");
				self.spawnflags &= ~START_OFF;
			}
			else {
				gi.configstring(CS_LIGHTS + self.style, "a");
				self.spawnflags |= START_OFF;
			}
		}
	};

	public static void SP_light(edict_t self) {
		// no targeted lights in deathmatch, because they cause global messages
		if (null == self.targetname || deathmatch.value != 0) {
			G_FreeEdict(self);
			return;
		}

		if (self.style >= 32) {
			self.use = light_use;
			if ((self.spawnflags & START_OFF) != 0)
				gi.configstring(CS_LIGHTS + self.style, "a");
			else
				gi.configstring(CS_LIGHTS + self.style, "m");
		}
	}

	/*QUAKED func_wall (0 .5 .8) ? TRIGGER_SPAWN TOGGLE START_ON ANIMATED ANIMATED_FAST
	This is just a solid wall if not inhibited
	
	TRIGGER_SPAWN	the wall will not be present until triggered
					it will then blink in to existance; it will
					kill anything that was in it's way
	
	TOGGLE			only valid for TRIGGER_SPAWN walls
					this allows the wall to be turned on and off
	
	START_ON		only valid for TRIGGER_SPAWN walls
					the wall will initially be present
	*/

	static EntUseAdapter func_wall_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			if (self.solid == SOLID_NOT) {
				self.solid = SOLID_BSP;
				self.svflags &= ~SVF_NOCLIENT;
				KillBox(self);
			}
			else {
				self.solid = SOLID_NOT;
				self.svflags |= SVF_NOCLIENT;
			}
			gi.linkentity(self);

			if (0 == (self.spawnflags & 2))
				self.use = null;
		}
	};

	public static void SP_func_wall(edict_t self) {
		self.movetype = MOVETYPE_PUSH;
		gi.setmodel(self, self.model);

		if ((self.spawnflags & 8) != 0)
			self.s.effects |= EF_ANIM_ALL;
		if ((self.spawnflags & 16) != 0)
			self.s.effects |= EF_ANIM_ALLFAST;

		// just a wall
		if ((self.spawnflags & 7) == 0) {
			self.solid = SOLID_BSP;
			gi.linkentity(self);
			return;
		}

		// it must be TRIGGER_SPAWN
		if (0 == (self.spawnflags & 1)) {
			//		gi.dprintf("func_wall missing TRIGGER_SPAWN\n");
			self.spawnflags |= 1;
		}

		// yell if the spawnflags are odd
		if ((self.spawnflags & 4) != 0) {
			if (0 == (self.spawnflags & 2)) {
				gi.dprintf("func_wall START_ON without TOGGLE\n");
				self.spawnflags |= 2;
			}
		}

		self.use = func_wall_use;
		if ((self.spawnflags & 4) != 0) {
			self.solid = SOLID_BSP;
		}
		else {
			self.solid = SOLID_NOT;
			self.svflags |= SVF_NOCLIENT;
		}
		gi.linkentity(self);
	}

	/*QUAKED func_object (0 .5 .8) ? TRIGGER_SPAWN ANIMATED ANIMATED_FAST
	This is solid bmodel that will fall if it's support it removed.
	*/
	static EntTouchAdapter func_object_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
				// only squash thing we fall on top of
	if (plane == null)
				return;
			if (plane.normal[2] < 1.0)
				return;
			if (other.takedamage == DAMAGE_NO)
				return;
			T_Damage(other, self, self, vec3_origin, self.s.origin, vec3_origin, self.dmg, 1, 0, MOD_CRUSH);
		}
	};

	static EntThinkAdapter func_object_release = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.movetype = MOVETYPE_TOSS;
			self.touch = func_object_touch;
			return true;
		}
	};

	static EntUseAdapter func_object_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			self.solid = SOLID_BSP;
			self.svflags &= ~SVF_NOCLIENT;
			self.use = null;
			KillBox(self);
			func_object_release.think(self);
		}
	};

	public static void SP_func_object(edict_t self) {
		gi.setmodel(self, self.model);

		self.mins[0] += 1;
		self.mins[1] += 1;
		self.mins[2] += 1;
		self.maxs[0] -= 1;
		self.maxs[1] -= 1;
		self.maxs[2] -= 1;

		if (self.dmg == 0)
			self.dmg = 100;

		if (self.spawnflags == 0) {
			self.solid = SOLID_BSP;
			self.movetype = MOVETYPE_PUSH;
			self.think = func_object_release;
			self.nextthink = level.time + 2 * FRAMETIME;
		}
		else {
			self.solid = SOLID_NOT;
			self.movetype = MOVETYPE_PUSH;
			self.use = func_object_use;
			self.svflags |= SVF_NOCLIENT;
		}

		if ((self.spawnflags & 2) != 0)
			self.s.effects |= EF_ANIM_ALL;
		if ((self.spawnflags & 4) != 0)
			self.s.effects |= EF_ANIM_ALLFAST;

		self.clipmask = MASK_MONSTERSOLID;

		gi.linkentity(self);
	}

	/*QUAKED func_explosive (0 .5 .8) ? Trigger_Spawn ANIMATED ANIMATED_FAST
	Any brush that you want to explode or break apart.  If you want an
	ex0plosion, set dmg and it will do a radius explosion of that amount
	at the center of the bursh.
	
	If targeted it will not be shootable.
	
	health defaults to 100.
	
	mass defaults to 75.  This determines how much debris is emitted when
	it explodes.  You get one large chunk per 100 of mass (up to 8) and
	one small chunk per 25 of mass (up to 16).  So 800 gives the most.
	*/
	static EntDieAdapter func_explosive_explode = new EntDieAdapter() {

		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {
			float[] origin={0,0,0};
			float[] chunkorigin={0,0,0};
			float[] size={0,0,0};
			int count;
			int mass;

			// bmodel origins are (0 0 0), we need to adjust that here
			VectorScale(self.size, 0.5f, size);
			VectorAdd(self.absmin, size, origin);
			VectorCopy(origin, self.s.origin);

			self.takedamage = DAMAGE_NO;

			if (self.dmg != 0)
				T_RadiusDamage(self, attacker, self.dmg, null, self.dmg + 40, MOD_EXPLOSIVE);

			VectorSubtract(self.s.origin, inflictor.s.origin, self.velocity);
			VectorNormalize(self.velocity);
			VectorScale(self.velocity, 150, self.velocity);

			// start chunks towards the center
			VectorScale(size, 0.5f, size);

			mass = self.mass;
			if (0 == mass)
				mass = 75;

			// big chunks
			if (mass >= 100) {
				count = mass / 100;
				if (count > 8)
					count = 8;
				while (count-- != 0) {
					chunkorigin[0] = origin[0] + crandom() * size[0];
					chunkorigin[1] = origin[1] + crandom() * size[1];
					chunkorigin[2] = origin[2] + crandom() * size[2];
					ThrowDebris(self, "models/objects/debris1/tris.md2", 1, chunkorigin);
				}
			}

			// small chunks
			count = mass / 25;
			if (count > 16)
				count = 16;
			while (count-- != 0) {
				chunkorigin[0] = origin[0] + crandom() * size[0];
				chunkorigin[1] = origin[1] + crandom() * size[1];
				chunkorigin[2] = origin[2] + crandom() * size[2];
				ThrowDebris(self, "models/objects/debris2/tris.md2", 2, chunkorigin);
			}

			G_UseTargets(self, attacker);

			if (self.dmg != 0)
				BecomeExplosion1(self);
			else
				G_FreeEdict(self);
		}
	};

	static EntUseAdapter func_explosive_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			func_explosive_explode.die(self, self, other, self.health, vec3_origin);
		}
	};

	static EntUseAdapter func_explosive_spawn = new EntUseAdapter() {

		public void use(edict_t self, edict_t other, edict_t activator) {
			self.solid = SOLID_BSP;
			self.svflags &= ~SVF_NOCLIENT;
			self.use = null;
			KillBox(self);
			gi.linkentity(self);
		}
	};

	public static void SP_func_explosive(edict_t self) {
		if (deathmatch.value != 0) { // auto-remove for deathmatch
			G_FreeEdict(self);
			return;
		}

		self.movetype = MOVETYPE_PUSH;

		gi.modelindex("models/objects/debris1/tris.md2");
		gi.modelindex("models/objects/debris2/tris.md2");

		gi.setmodel(self, self.model);

		if ((self.spawnflags & 1) != 0) {
			self.svflags |= SVF_NOCLIENT;
			self.solid = SOLID_NOT;
			self.use = func_explosive_spawn;
		}
		else {
			self.solid = SOLID_BSP;
			if (self.targetname != null)
				self.use = func_explosive_use;
		}

		if ((self.spawnflags & 2) != 0)
			self.s.effects |= EF_ANIM_ALL;
		if ((self.spawnflags & 4) != 0)
			self.s.effects |= EF_ANIM_ALLFAST;

		if (self.use != func_explosive_use) {
			if (self.health == 0)
				self.health = 100;
			self.die = func_explosive_explode;
			self.takedamage = DAMAGE_YES;
		}

		gi.linkentity(self);
	}

	/*QUAKED misc_explobox (0 .5 .8) (-16 -16 0) (16 16 40)
	Large exploding box.  You can override its mass (100),
	health (80), and dmg (150).
	*/

	static EntTouchAdapter barrel_touch = new EntTouchAdapter() {

		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			float ratio;
			float[] v={0,0,0};

			if ((null == other.groundentity) || (other.groundentity == self))
				return;

			ratio = (float) other.mass / (float) self.mass;
			VectorSubtract(self.s.origin, other.s.origin, v);
			M.M_walkmove(self, vectoyaw(v), 20 * ratio * FRAMETIME);
		}
	};

	static EntThinkAdapter barrel_explode = new EntThinkAdapter() {
		public boolean think(edict_t self) {

			float[] org={0,0,0};
			float spd;
			float[] save={0,0,0};

			T_RadiusDamage(self, self.activator, self.dmg, null, self.dmg + 40, MOD_BARREL);

			VectorCopy(self.s.origin, save);
			VectorMA(self.absmin, 0.5f, self.size, self.s.origin);

			// a few big chunks
			spd = 1.5f * (float) self.dmg / 200.0f;
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris1/tris.md2", spd, org);
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris1/tris.md2", spd, org);

			// bottom corners
			spd = 1.75f * (float) self.dmg / 200.0f;
			VectorCopy(self.absmin, org);
			ThrowDebris(self, "models/objects/debris3/tris.md2", spd, org);
			VectorCopy(self.absmin, org);
			org[0] += self.size[0];
			ThrowDebris(self, "models/objects/debris3/tris.md2", spd, org);
			VectorCopy(self.absmin, org);
			org[1] += self.size[1];
			ThrowDebris(self, "models/objects/debris3/tris.md2", spd, org);
			VectorCopy(self.absmin, org);
			org[0] += self.size[0];
			org[1] += self.size[1];
			ThrowDebris(self, "models/objects/debris3/tris.md2", spd, org);

			// a bunch of little chunks
			spd = 2 * self.dmg / 200;
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + crandom() * self.size[0];
			org[1] = self.s.origin[1] + crandom() * self.size[1];
			org[2] = self.s.origin[2] + crandom() * self.size[2];
			ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);

			VectorCopy(save, self.s.origin);
			if (self.groundentity != null)
				BecomeExplosion2(self);
			else
				BecomeExplosion1(self);
				
			return true;
		}
	};

	static EntDieAdapter barrel_delay = new EntDieAdapter() {
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {

			self.takedamage = DAMAGE_NO;
			self.nextthink = level.time + 2 * FRAMETIME;
			self.think = barrel_explode;
			self.activator = attacker;
		}
	};

	public static void SP_misc_explobox(edict_t self) {
		if (deathmatch.value != 0) { // auto-remove for deathmatch
			G_FreeEdict(self);
			return;
		}

		gi.modelindex("models/objects/debris1/tris.md2");
		gi.modelindex("models/objects/debris2/tris.md2");
		gi.modelindex("models/objects/debris3/tris.md2");

		self.solid = SOLID_BBOX;
		self.movetype = MOVETYPE_STEP;

		self.model = "models/objects/barrels/tris.md2";
		self.s.modelindex = gi.modelindex(self.model);
		VectorSet(self.mins, -16, -16, 0);
		VectorSet(self.maxs, 16, 16, 40);

		if (self.mass == 0)
			self.mass = 400;
		if (0 == self.health)
			self.health = 10;
		if (0 == self.dmg)
			self.dmg = 150;

		self.die = barrel_delay;
		self.takedamage = DAMAGE_YES;
		self.monsterinfo.aiflags = AI_NOSTEP;

		self.touch = barrel_touch;

		self.think = M.M_droptofloor;
		self.nextthink = level.time + 2 * FRAMETIME;

		gi.linkentity(self);
	}

	//
	// miscellaneous specialty items
	//

	/*QUAKED misc_blackhole (1 .5 0) (-8 -8 -8) (8 8 8)
	*/

	static EntUseAdapter misc_blackhole_use = new EntUseAdapter() {
		public void use(edict_t ent, edict_t other, edict_t activator) {
			/*
			gi.WriteByte (svc_temp_entity);
			gi.WriteByte (TE_BOSSTPORT);
			gi.WritePosition (ent.s.origin);
			gi.multicast (ent.s.origin, MULTICAST_PVS);
			*/
			G_FreeEdict(ent);
		}
	};

	static EntThinkAdapter misc_blackhole_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {

			if (++self.s.frame < 19)
				self.nextthink = level.time + FRAMETIME;
			else {
				self.s.frame = 0;
				self.nextthink = level.time + FRAMETIME;
			}
			return true;
		}
	};

	public static void SP_misc_blackhole(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_NOT;
		VectorSet(ent.mins, -64, -64, 0);
		VectorSet(ent.maxs, 64, 64, 8);
		ent.s.modelindex = gi.modelindex("models/objects/black/tris.md2");
		ent.s.renderfx = RF_TRANSLUCENT;
		ent.use = misc_blackhole_use;
		ent.think = misc_blackhole_think;
		ent.nextthink = level.time + 2 * FRAMETIME;
		gi.linkentity(ent);
	}

	/*QUAKED misc_eastertank (1 .5 0) (-32 -32 -16) (32 32 32)
	*/

	static EntThinkAdapter misc_eastertank_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (++self.s.frame < 293)
				self.nextthink = level.time + FRAMETIME;
			else {
				self.s.frame = 254;
				self.nextthink = level.time + FRAMETIME;
			}
			return true;
		}
	};

	public static void SP_misc_eastertank(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -32, -32, -16);
		VectorSet(ent.maxs, 32, 32, 32);
		ent.s.modelindex = gi.modelindex("models/monsters/tank/tris.md2");
		ent.s.frame = 254;
		ent.think = misc_eastertank_think;
		ent.nextthink = level.time + 2 * FRAMETIME;
		gi.linkentity(ent);
	}

	/*QUAKED misc_easterchick (1 .5 0) (-32 -32 0) (32 32 32)
	*/

	static EntThinkAdapter misc_easterchick_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (++self.s.frame < 247)
				self.nextthink = level.time + FRAMETIME;
			else {
				self.s.frame = 208;
				self.nextthink = level.time + FRAMETIME;
			}
			return true;
		}
	};

	public static void SP_misc_easterchick(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -32, -32, 0);
		VectorSet(ent.maxs, 32, 32, 32);
		ent.s.modelindex = gi.modelindex("models/monsters/bitch/tris.md2");
		ent.s.frame = 208;
		ent.think = misc_easterchick_think;
		ent.nextthink = level.time + 2 * FRAMETIME;
		gi.linkentity(ent);
	}

	/*QUAKED misc_easterchick2 (1 .5 0) (-32 -32 0) (32 32 32)
	*/
	static EntThinkAdapter misc_easterchick2_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (++self.s.frame < 287)
				self.nextthink = level.time + FRAMETIME;
			else {
				self.s.frame = 248;
				self.nextthink = level.time + FRAMETIME;
			}
			return true;
		}
	};

	public static void SP_misc_easterchick2(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -32, -32, 0);
		VectorSet(ent.maxs, 32, 32, 32);
		ent.s.modelindex = gi.modelindex("models/monsters/bitch/tris.md2");
		ent.s.frame = 248;
		ent.think = misc_easterchick2_think;
		ent.nextthink = level.time + 2 * FRAMETIME;
		gi.linkentity(ent);
	}

	/*QUAKED monster_commander_body (1 .5 0) (-32 -32 0) (32 32 48)
	Not really a monster, this is the Tank Commander's decapitated body.
	There should be a item_commander_head that has this as it's target.
	*/

	static EntThinkAdapter commander_body_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (++self.s.frame < 24)
				self.nextthink = level.time + FRAMETIME;
			else
				self.nextthink = 0;

			if (self.s.frame == 22)
				gi.sound(self, CHAN_BODY, gi.soundindex("tank/thud.wav"), 1, ATTN_NORM, 0);
			return true;
		}
	};

	static EntUseAdapter commander_body_use = new EntUseAdapter() {

		public void use(edict_t self, edict_t other, edict_t activator) {
			self.think = commander_body_think;
			self.nextthink = level.time + FRAMETIME;
			gi.sound(self, CHAN_BODY, gi.soundindex("tank/pain.wav"), 1, ATTN_NORM, 0);
		}
	};

	static EntThinkAdapter commander_body_drop = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.movetype = MOVETYPE_TOSS;
			self.s.origin[2] += 2;
			return true;
		}
	};

	public static void SP_monster_commander_body(edict_t self) {
		self.movetype = MOVETYPE_NONE;
		self.solid = SOLID_BBOX;
		self.model = "models/monsters/commandr/tris.md2";
		self.s.modelindex = gi.modelindex(self.model);
		VectorSet(self.mins, -32, -32, 0);
		VectorSet(self.maxs, 32, 32, 48);
		self.use = commander_body_use;
		self.takedamage = DAMAGE_YES;
		self.flags = FL_GODMODE;
		self.s.renderfx |= RF_FRAMELERP;
		gi.linkentity(self);

		gi.soundindex("tank/thud.wav");
		gi.soundindex("tank/pain.wav");

		self.think = commander_body_drop;
		self.nextthink = level.time + 5 * FRAMETIME;
	}

	/*QUAKED misc_banner (1 .5 0) (-4 -4 -4) (4 4 4)
	The origin is the bottom of the banner.
	The banner is 128 tall.
	*/
	static EntThinkAdapter misc_banner_think = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			ent.s.frame = (ent.s.frame + 1) % 16;
			ent.nextthink = level.time + FRAMETIME;
			return true;
		}
	};

	public static void SP_misc_banner(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_NOT;
		ent.s.modelindex = gi.modelindex("models/objects/banner/tris.md2");
		ent.s.frame = rand() % 16;
		gi.linkentity(ent);

		ent.think = misc_banner_think;
		ent.nextthink = level.time + FRAMETIME;
	}

	/*QUAKED misc_deadsoldier (1 .5 0) (-16 -16 0) (16 16 16) ON_BACK ON_STOMACH BACK_DECAP FETAL_POS SIT_DECAP IMPALED
	This is the dead player model. Comes in 6 exciting different poses!
	*/
	static EntDieAdapter misc_deadsoldier_die = new EntDieAdapter() {

		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {
			int n;

			if (self.health > -80)
				return;

			gi.sound(self, CHAN_BODY, gi.soundindex("misc/udeath.wav"), 1, ATTN_NORM, 0);
			for (n = 0; n < 4; n++)
				ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage, GIB_ORGANIC);
			ThrowHead(self, "models/objects/gibs/head2/tris.md2", damage, GIB_ORGANIC);
		}
	};

	public static void SP_misc_deadsoldier(edict_t ent) {
		if (deathmatch.value != 0) { // auto-remove for deathmatch
			G_FreeEdict(ent);
			return;
		}

		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		ent.s.modelindex = gi.modelindex("models/deadbods/dude/tris.md2");

		// Defaults to frame 0
		if ((ent.spawnflags & 2) != 0)
			ent.s.frame = 1;
		else if ((ent.spawnflags & 4) != 0)
			ent.s.frame = 2;
		else if ((ent.spawnflags & 8) != 0)
			ent.s.frame = 3;
		else if ((ent.spawnflags & 16) != 0)
			ent.s.frame = 4;
		else if ((ent.spawnflags & 32) != 0)
			ent.s.frame = 5;
		else
			ent.s.frame = 0;

		VectorSet(ent.mins, -16, -16, 0);
		VectorSet(ent.maxs, 16, 16, 16);
		ent.deadflag = DEAD_DEAD;
		ent.takedamage = DAMAGE_YES;
		ent.svflags |= SVF_MONSTER | SVF_DEADMONSTER;
		ent.die = misc_deadsoldier_die;
		ent.monsterinfo.aiflags |= AI_GOOD_GUY;

		gi.linkentity(ent);
	}

	/*QUAKED misc_viper (1 .5 0) (-16 -16 0) (16 16 32)
	This is the Viper for the flyby bombing.
	It is trigger_spawned, so you must have something use it for it to show up.
	There must be a path for it to follow once it is activated.
	
	"speed"		How fast the Viper should fly
	*/

	static EntUseAdapter misc_viper_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			self.svflags &= ~SVF_NOCLIENT;
			self.use = GameFunc.train_use;
			GameFunc.train_use.use(self, other, activator);
		}
	};

	public static void SP_misc_viper(edict_t ent) {
		if (null == ent.target) {
			gi.dprintf("misc_viper without a target at " + vtos(ent.absmin) + "\n");
			G_FreeEdict(ent);
			return;
		}

		if (0 == ent.speed)
			ent.speed = 300;

		ent.movetype = MOVETYPE_PUSH;
		ent.solid = SOLID_NOT;
		ent.s.modelindex = gi.modelindex("models/ships/viper/tris.md2");
		VectorSet(ent.mins, -16, -16, 0);
		VectorSet(ent.maxs, 16, 16, 32);

		ent.think = GameFunc.func_train_find;
		ent.nextthink = level.time + FRAMETIME;
		ent.use = misc_viper_use;
		ent.svflags |= SVF_NOCLIENT;
		ent.moveinfo.accel = ent.moveinfo.decel = ent.moveinfo.speed = ent.speed;

		gi.linkentity(ent);
	}

	/*QUAKED misc_bigviper (1 .5 0) (-176 -120 -24) (176 120 72) 
	This is a large stationary viper as seen in Paul's intro
	*/
	public static void SP_misc_bigviper(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -176, -120, -24);
		VectorSet(ent.maxs, 176, 120, 72);
		ent.s.modelindex = gi.modelindex("models/ships/bigviper/tris.md2");
		gi.linkentity(ent);
	}

	/*QUAKED misc_viper_bomb (1 0 0) (-8 -8 -8) (8 8 8)
	"dmg"	how much boom should the bomb make?
	*/
	static EntTouchAdapter misc_viper_bomb_touch = new EntTouchAdapter() {

		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			G_UseTargets(self, self.activator);

			self.s.origin[2] = self.absmin[2] + 1;
			T_RadiusDamage(self, self, self.dmg, null, self.dmg + 40, MOD_BOMB);
			BecomeExplosion2(self);
		}
	};

	static EntThinkAdapter misc_viper_bomb_prethink = new EntThinkAdapter() {
		public boolean think(edict_t self) {

			float[] v={0,0,0};
			float diff;

			self.groundentity = null;

			diff = self.timestamp - level.time;
			if (diff < -1.0)
				diff = -1.0f;

			VectorScale(self.moveinfo.dir, 1.0f + diff, v);
			v[2] = diff;

			diff = self.s.angles[2];
			vectoangles(v, self.s.angles);
			self.s.angles[2] = diff + 10;

			return true;
		}
	};

	static EntUseAdapter misc_viper_bomb_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			edict_t viper=null;

			self.solid = SOLID_BBOX;
			self.svflags &= ~SVF_NOCLIENT;
			self.s.effects |= EF_ROCKET;
			self.use = null;
			self.movetype = MOVETYPE_TOSS;
			self.prethink = misc_viper_bomb_prethink;
			self.touch = misc_viper_bomb_touch;
			self.activator = activator;

			EdictIterator es = null;

			es = G_Find(es, findByClass, "misc_viper");
			if (es != null)
				viper = es.o;

			VectorScale(viper.moveinfo.dir, viper.moveinfo.speed, self.velocity);

			self.timestamp = level.time;
			VectorCopy(viper.moveinfo.dir, self.moveinfo.dir);
		}
	};

	public static void SP_misc_viper_bomb(edict_t self) {
		self.movetype = MOVETYPE_NONE;
		self.solid = SOLID_NOT;
		VectorSet(self.mins, -8, -8, -8);
		VectorSet(self.maxs, 8, 8, 8);

		self.s.modelindex = gi.modelindex("models/objects/bomb/tris.md2");

		if (self.dmg == 0)
			self.dmg = 1000;

		self.use = misc_viper_bomb_use;
		self.svflags |= SVF_NOCLIENT;

		gi.linkentity(self);
	}

	/*QUAKED misc_strogg_ship (1 .5 0) (-16 -16 0) (16 16 32)
	This is a Storgg ship for the flybys.
	It is trigger_spawned, so you must have something use it for it to show up.
	There must be a path for it to follow once it is activated.
	
	"speed"		How fast it should fly
	*/

	static EntUseAdapter misc_strogg_ship_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			self.svflags &= ~SVF_NOCLIENT;
			self.use = GameFunc.train_use;
			GameFunc.train_use.use(self, other, activator);
		}
	};

	public static void SP_misc_strogg_ship(edict_t ent) {
		if (null == ent.target) {
			gi.dprintf(ent.classname + " without a target at " + vtos(ent.absmin) + "\n");
			G_FreeEdict(ent);
			return;
		}

		if (0 == ent.speed)
			ent.speed = 300;

		ent.movetype = MOVETYPE_PUSH;
		ent.solid = SOLID_NOT;
		ent.s.modelindex = gi.modelindex("models/ships/strogg1/tris.md2");
		VectorSet(ent.mins, -16, -16, 0);
		VectorSet(ent.maxs, 16, 16, 32);

		ent.think = GameFunc.func_train_find;
		ent.nextthink = level.time + FRAMETIME;
		ent.use = misc_strogg_ship_use;
		ent.svflags |= SVF_NOCLIENT;
		ent.moveinfo.accel = ent.moveinfo.decel = ent.moveinfo.speed = ent.speed;

		gi.linkentity(ent);
	}

	/*QUAKED misc_satellite_dish (1 .5 0) (-64 -64 0) (64 64 128)
	*/
	static EntThinkAdapter misc_satellite_dish_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.s.frame++;
			if (self.s.frame < 38)
				self.nextthink = level.time + FRAMETIME;
			return true;
		}
	};

	static EntUseAdapter misc_satellite_dish_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			self.s.frame = 0;
			self.think = misc_satellite_dish_think;
			self.nextthink = level.time + FRAMETIME;
		}
	};

	public static void SP_misc_satellite_dish(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -64, -64, 0);
		VectorSet(ent.maxs, 64, 64, 128);
		ent.s.modelindex = gi.modelindex("models/objects/satellite/tris.md2");
		ent.use = misc_satellite_dish_use;
		gi.linkentity(ent);
	}

	/*QUAKED light_mine1 (0 1 0) (-2 -2 -12) (2 2 12)
	*/
	public static void SP_light_mine1(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		ent.s.modelindex = gi.modelindex("models/objects/minelite/light1/tris.md2");
		gi.linkentity(ent);
	}

	/*QUAKED light_mine2 (0 1 0) (-2 -2 -12) (2 2 12)
	*/
	public static void SP_light_mine2(edict_t ent) {
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		ent.s.modelindex = gi.modelindex("models/objects/minelite/light2/tris.md2");
		gi.linkentity(ent);
	}

	/*QUAKED misc_gib_arm (1 0 0) (-8 -8 -8) (8 8 8)
	Intended for use with the target_spawner
	*/
	public static void SP_misc_gib_arm(edict_t ent) {
		gi.setmodel(ent, "models/objects/gibs/arm/tris.md2");
		ent.solid = SOLID_NOT;
		ent.s.effects |= EF_GIB;
		ent.takedamage = DAMAGE_YES;
		ent.die = gib_die;
		ent.movetype = MOVETYPE_TOSS;
		ent.svflags |= SVF_MONSTER;
		ent.deadflag = DEAD_DEAD;
		ent.avelocity[0] = random() * 200;
		ent.avelocity[1] = random() * 200;
		ent.avelocity[2] = random() * 200;
		ent.think = G_FreeEdictA;
		ent.nextthink = level.time + 30;
		gi.linkentity(ent);
	}

	/*QUAKED misc_gib_leg (1 0 0) (-8 -8 -8) (8 8 8)
	Intended for use with the target_spawner
	*/
	public static void SP_misc_gib_leg(edict_t ent) {
		gi.setmodel(ent, "models/objects/gibs/leg/tris.md2");
		ent.solid = SOLID_NOT;
		ent.s.effects |= EF_GIB;
		ent.takedamage = DAMAGE_YES;
		ent.die = gib_die;
		ent.movetype = MOVETYPE_TOSS;
		ent.svflags |= SVF_MONSTER;
		ent.deadflag = DEAD_DEAD;
		ent.avelocity[0] = random() * 200;
		ent.avelocity[1] = random() * 200;
		ent.avelocity[2] = random() * 200;
		ent.think = G_FreeEdictA;
		ent.nextthink = level.time + 30;
		gi.linkentity(ent);
	}

	/*QUAKED misc_gib_head (1 0 0) (-8 -8 -8) (8 8 8)
	Intended for use with the target_spawner
	*/
	public static void SP_misc_gib_head(edict_t ent) {
		gi.setmodel(ent, "models/objects/gibs/head/tris.md2");
		ent.solid = SOLID_NOT;
		ent.s.effects |= EF_GIB;
		ent.takedamage = DAMAGE_YES;
		ent.die = gib_die;
		ent.movetype = MOVETYPE_TOSS;
		ent.svflags |= SVF_MONSTER;
		ent.deadflag = DEAD_DEAD;
		ent.avelocity[0] = random() * 200;
		ent.avelocity[1] = random() * 200;
		ent.avelocity[2] = random() * 200;
		ent.think = G_FreeEdictA;
		ent.nextthink = level.time + 30;
		gi.linkentity(ent);
	}

	//=====================================================

	/*QUAKED target_character (0 0 1) ?
	used with target_string (must be on same "team")
	"count" is position in the string (starts at 1)
	*/

	public static void SP_target_character(edict_t self) {
		self.movetype = MOVETYPE_PUSH;
		gi.setmodel(self, self.model);
		self.solid = SOLID_BSP;
		self.s.frame = 12;
		gi.linkentity(self);
		return;
	}

	/*QUAKED target_string (0 0 1) (-8 -8 -8) (8 8 8)
	*/

	static EntUseAdapter target_string_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			edict_t e;
			int n, l;
			char c;

			l = self.message.length();
			for (e = self.teammaster; e != null; e = e.teamchain) {
				if (e.count == 0)
					continue;
				n = e.count - 1;
				if (n > l) {
					e.s.frame = 12;
					continue;
				}

				c = self.message.charAt(n);
				if (c >= '0' && c <= '9')
					e.s.frame = c - '0';
				else if (c == '-')
					e.s.frame = 10;
				else if (c == ':')
					e.s.frame = 11;
				else
					e.s.frame = 12;
			}
		}
	};

	public static void SP_target_string(edict_t self) {
		if (self.message == null)
			self.message = "";
		self.use = target_string_use;
	}

	/*QUAKED func_clock (0 0 1) (-8 -8 -8) (8 8 8) TIMER_UP TIMER_DOWN START_OFF MULTI_USE
	target a target_string with this
	
	The default is to be a time of day clock
	
	TIMER_UP and TIMER_DOWN run for "count" seconds and the fire "pathtarget"
	If START_OFF, this entity must be used before it starts
	
	"style"		0 "xx"
				1 "xx:xx"
				2 "xx:xx:xx"
	*/

	public static final int CLOCK_MESSAGE_SIZE = 16;

	// don't let field width of any clock messages change, or it
	// could cause an overwrite after a game load

	public static void func_clock_reset(edict_t self) {
		self.activator = null;
		if ((self.spawnflags & 1) != 0) {
			self.health = 0;
			self.wait = self.count;
		}
		else if ((self.spawnflags & 2) != 0) {
			self.health = self.count;
			self.wait = 0;
		}
	}

	public static void func_clock_format_countdown(edict_t self) {
		if (self.style == 0) {
			self.message = "" + self.health;
			//Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i", self.health);
			return;
		}

		if (self.style == 1) {
			self.message = "" + self.health / 60 + ":" + self.health % 60;
			//Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i:%2i", self.health / 60, self.health % 60);
			/*
			if (self.message.charAt(3) == ' ')
				self.message.charAt(3) = '0';
				*/
			return;
		}

		if (self.style == 2) {
			self.message = "" + self.health / 3600 + ":" + (self.health - (self.health / 3600) * 3600) / 60 + ":" + self.health % 60;
			/*
			Com_sprintf(
				self.message,
				CLOCK_MESSAGE_SIZE,
				"%2i:%2i:%2i",
				self.health / 3600,
				(self.health - (self.health / 3600) * 3600) / 60,
				self.health % 60);
			if (self.message[3] == ' ')
				self.message[3] = '0';
			if (self.message[6] == ' ')
				self.message[6] = '0';
			*/
			return;
		}
	}

	public static EntThinkAdapter func_clock_think = new EntThinkAdapter() {

		public boolean  think(edict_t self) {
			if (null == self.enemy) {

				EdictIterator es = null;

				es = G_Find(es, findByTarget, self.target);
				if (es != null)
					self.enemy = es.o;
				if (self.enemy == null)
					return true;
			}

			if ((self.spawnflags & 1) != 0) {
				func_clock_format_countdown(self);
				self.health++;
			}
			else if ((self.spawnflags & 2) != 0) {
				func_clock_format_countdown(self);
				self.health--;
			}
			else {
				Date d = new Date();
				self.message = "" + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds();

				/*
				struct tm * ltime;
				time_t gmtime;
				
				time(& gmtime);
				ltime = localtime(& gmtime);
				Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i:%2i:%2i", ltime.tm_hour, ltime.tm_min, ltime.tm_sec);
				if (self.message[3] == ' ')
					self.message[3] = '0';
				if (self.message[6] == ' ')
					self.message[6] = '0';
				*/
			}

			self.enemy.message = self.message;
			self.enemy.use.use(self.enemy, self, self);

			if (((self.spawnflags & 1) != 0 && (self.health > self.wait))
				|| ((self.spawnflags & 2) != 0 && (self.health < self.wait))) {
				if (self.pathtarget != null) {
					String savetarget;
					String savemessage;

					savetarget = self.target;
					savemessage = self.message;
					self.target = self.pathtarget;
					self.message = null;
					G_UseTargets(self, self.activator);
					self.target = savetarget;
					self.message = savemessage;
				}

				if (0 == (self.spawnflags & 8))
					return true;

				func_clock_reset(self);

				if ((self.spawnflags & 4) != 0)
					return true;
			}

			self.nextthink = level.time + 1;
			return true;

		}
	};

	static EntUseAdapter func_clock_use = new EntUseAdapter() {

		public void use(edict_t self, edict_t other, edict_t activator) {
			if (0 == (self.spawnflags & 8))
				self.use = null;
			if (self.activator!=null)
				return;
			self.activator = activator;
			self.think.think(self);
		}
	};

	public static void SP_func_clock(edict_t self) {
		if (self.target == null) {
			gi.dprintf(self.classname + " with no target at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		if ((self.spawnflags & 2) != 0 && (0 == self.count)) {
			gi.dprintf(self.classname + " with no count at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		if ((self.spawnflags & 1) != 0 && (0 == self.count))
			self.count = 60 * 60;

		func_clock_reset(self);

		//self.message = gi.TagMalloc(CLOCK_MESSAGE_SIZE, TAG_LEVEL);
		self.message = new String();

		self.think = func_clock_think;

		if ((self.spawnflags & 4) != 0)
			self.use = func_clock_use;
		else
			self.nextthink = level.time + 1;
	}

	//=================================================================================

	static EntTouchAdapter teleporter_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			edict_t dest;
			int i;

			if (other.client ==null)
				return;
				
			EdictIterator es =null; 	
			dest = G_Find(null, findByTarget, self.target).o;
			
			if (dest==null) {
				gi.dprintf("Couldn't find destination\n");
				return;
			}

			// unlink to make sure it can't possibly interfere with KillBox
			gi.unlinkentity(other);

			VectorCopy(dest.s.origin, other.s.origin);
			VectorCopy(dest.s.origin, other.s.old_origin);
			other.s.origin[2] += 10;

			// clear the velocity and hold them in place briefly
			VectorClear(other.velocity);
			other.client.ps.pmove.pm_time = 160 >> 3; // hold time
			other.client.ps.pmove.pm_flags |= PMF_TIME_TELEPORT;

			// draw the teleport splash at source and on the player
			self.owner.s.event = EV_PLAYER_TELEPORT;
			other.s.event = EV_PLAYER_TELEPORT;

			// set angles
			for (i = 0; i < 3; i++) {
				other.client.ps.pmove.delta_angles[i] = (short) ANGLE2SHORT(dest.s.angles[i] - other.client.resp.cmd_angles[i]);
			}

			VectorClear(other.s.angles);
			VectorClear(other.client.ps.viewangles);
			VectorClear(other.client.v_angle);

			// kill anything at the destination
			KillBox(other);

			gi.linkentity(other);
		}
	};

	/*QUAKED misc_teleporter (1 0 0) (-32 -32 -24) (32 32 -16)
	Stepping onto this disc will teleport players to the targeted misc_teleporter_dest object.
	*/
	public static void SP_misc_teleporter(edict_t ent) {
		edict_t trig;

		if (ent.target==null) {
			gi.dprintf("teleporter without a target.\n");
			G_FreeEdict(ent);
			return;
		}

		gi.setmodel(ent, "models/objects/dmspot/tris.md2");
		ent.s.skinnum = 1;
		ent.s.effects = EF_TELEPORTER;
		ent.s.sound = gi.soundindex("world/amb10.wav");
		ent.solid = SOLID_BBOX;

		VectorSet(ent.mins, -32, -32, -24);
		VectorSet(ent.maxs, 32, 32, -16);
		gi.linkentity(ent);

		trig = G_Spawn();
		trig.touch = teleporter_touch;
		trig.solid = SOLID_TRIGGER;
		trig.target = ent.target;
		trig.owner = ent;
		VectorCopy(ent.s.origin, trig.s.origin);
		VectorSet(trig.mins, -8, -8, 8);
		VectorSet(trig.maxs, 8, 8, 24);
		gi.linkentity(trig);

	}

	/*QUAKED misc_teleporter_dest (1 0 0) (-32 -32 -24) (32 32 -16)
	Point teleporters at these.
	*/

	public static EntThinkAdapter SP_misc_teleporter_dest = new EntThinkAdapter() {
		public boolean think(edict_t ent) {

			gi.setmodel(ent, "models/objects/dmspot/tris.md2");
			ent.s.skinnum = 0;
			ent.solid = SOLID_BBOX;
			//	ent.s.effects |= EF_FLIES;
			VectorSet(ent.mins, -32, -32, -24);
			VectorSet(ent.maxs, 32, 32, -16);
			gi.linkentity(ent);
			return true;
		}
	};

}
