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
// $Id: GameTurretAdapters.java,v 1.1 2004-07-08 15:58:44 hzi Exp $

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.*;

public class GameTurretAdapters {

	static EntBlockedAdapter turret_blocked = new EntBlockedAdapter() {
	
		public void blocked(edict_t self, edict_t other) {
			edict_t attacker;
	
			if (other.takedamage != 0) {
				if (self.teammaster.owner != null)
					attacker = self.teammaster.owner;
				else
					attacker = self.teammaster;
				GameUtil.T_Damage(other, self, attacker, Globals.vec3_origin, other.s.origin, Globals.vec3_origin, self.teammaster.dmg, 10, 0, Defines.MOD_CRUSH);
			}
		}
	};
	static EntThinkAdapter turret_breach_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			edict_t ent;
			float[] current_angles = { 0, 0, 0 };
			float[] delta = { 0, 0, 0 };
	
			Math3D.VectorCopy(self.s.angles, current_angles);
			GameTurret.AnglesNormalize(current_angles);
	
			GameTurret.AnglesNormalize(self.move_angles);
			if (self.move_angles[Defines.PITCH] > 180)
				self.move_angles[Defines.PITCH] -= 360;
	
			// clamp angles to mins & maxs
			if (self.move_angles[Defines.PITCH] > self.pos1[Defines.PITCH])
				self.move_angles[Defines.PITCH] = self.pos1[Defines.PITCH];
			else if (self.move_angles[Defines.PITCH] < self.pos2[Defines.PITCH])
				self.move_angles[Defines.PITCH] = self.pos2[Defines.PITCH];
	
			if ((self.move_angles[Defines.YAW] < self.pos1[Defines.YAW]) || (self.move_angles[Defines.YAW] > self.pos2[Defines.YAW])) {
				float dmin, dmax;
	
				dmin = Math.abs(self.pos1[Defines.YAW] - self.move_angles[Defines.YAW]);
				if (dmin < -180)
					dmin += 360;
				else if (dmin > 180)
					dmin -= 360;
				dmax = Math.abs(self.pos2[Defines.YAW] - self.move_angles[Defines.YAW]);
				if (dmax < -180)
					dmax += 360;
				else if (dmax > 180)
					dmax -= 360;
				if (Math.abs(dmin) < Math.abs(dmax))
					self.move_angles[Defines.YAW] = self.pos1[Defines.YAW];
				else
					self.move_angles[Defines.YAW] = self.pos2[Defines.YAW];
			}
	
			Math3D.VectorSubtract(self.move_angles, current_angles, delta);
			if (delta[0] < -180)
				delta[0] += 360;
			else if (delta[0] > 180)
				delta[0] -= 360;
			if (delta[1] < -180)
				delta[1] += 360;
			else if (delta[1] > 180)
				delta[1] -= 360;
			delta[2] = 0;
	
			if (delta[0] > self.speed * Defines.FRAMETIME)
				delta[0] = self.speed * Defines.FRAMETIME;
			if (delta[0] < -1 * self.speed * Defines.FRAMETIME)
				delta[0] = -1 * self.speed * Defines.FRAMETIME;
			if (delta[1] > self.speed * Defines.FRAMETIME)
				delta[1] = self.speed * Defines.FRAMETIME;
			if (delta[1] < -1 * self.speed * Defines.FRAMETIME)
				delta[1] = -1 * self.speed * Defines.FRAMETIME;
	
			Math3D.VectorScale(delta, 1.0f / Defines.FRAMETIME, self.avelocity);
	
			self.nextthink = GameBase.level.time + Defines.FRAMETIME;
	
			for (ent = self.teammaster; ent != null; ent = ent.teamchain)
				ent.avelocity[1] = self.avelocity[1];
	
			// if we have adriver, adjust his velocities
			if (self.owner != null) {
				float angle;
				float target_z;
				float diff;
				float[] target = { 0, 0, 0 };
				float[] dir = { 0, 0, 0 };
	
				// angular is easy, just copy ours
				self.owner.avelocity[0] = self.avelocity[0];
				self.owner.avelocity[1] = self.avelocity[1];
	
				// x & y
				angle = self.s.angles[1] + self.owner.move_origin[1];
				angle *= (Math.PI * 2 / 360);
				target[0] = GameTurret.SnapToEights((float) (self.s.origin[0] + Math.cos(angle) * self.owner.move_origin[0]));
				target[1] = GameTurret.SnapToEights((float) (self.s.origin[1] + Math.sin(angle) * self.owner.move_origin[0]));
				target[2] = self.owner.s.origin[2];
	
				Math3D.VectorSubtract(target, self.owner.s.origin, dir);
				self.owner.velocity[0] = dir[0] * 1.0f / Defines.FRAMETIME;
				self.owner.velocity[1] = dir[1] * 1.0f / Defines.FRAMETIME;
	
				// z
				angle = self.s.angles[Defines.PITCH] * (float) (Math.PI * 2f / 360f);
				target_z =
					GameTurret.SnapToEights((float) (self.s.origin[2] + self.owner.move_origin[0] * Math.tan(angle) + self.owner.move_origin[2]));
	
				diff = target_z - self.owner.s.origin[2];
				self.owner.velocity[2] = diff * 1.0f / Defines.FRAMETIME;
	
				if ((self.spawnflags & 65536) != 0) {
					GameTurret.turret_breach_fire(self);
					self.spawnflags &= ~65536;
				}
			}
			return true;
		}
	};
	static EntThinkAdapter turret_breach_finish_init = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
				// get and save info for muzzle location
	if (self.target == null) {
				GameBase.gi.dprintf(self.classname + " at " + Lib.vtos(self.s.origin) + " needs a target\n");
			}
			else {
				self.target_ent = GameBase.G_PickTarget(self.target);
				Math3D.VectorSubtract(self.target_ent.s.origin, self.s.origin, self.move_origin);
				GameUtil.G_FreeEdict(self.target_ent);
			}
	
			self.teammaster.dmg = self.dmg;
			self.think = turret_breach_think;
			self.think.think(self);
			return true;
		}
	};
	/*QUAKED turret_driver (1 .5 0) (-16 -16 -24) (16 16 32)
	Must NOT be on the team with the rest of the turret parts.
	Instead it must target the turret_breach.
	*/
	static EntDieAdapter turret_driver_die = new EntDieAdapter() {
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {
	
			edict_t ent;
	
			// level the gun
			self.target_ent.move_angles[0] = 0;
	
			// remove the driver from the end of them team chain
			for (ent = self.target_ent.teammaster; ent.teamchain != self; ent = ent.teamchain);
			ent.teamchain = null;
			self.teammaster = null;
			self.flags &= ~Defines.FL_TEAMSLAVE;
	
			self.target_ent.owner = null;
			self.target_ent.teammaster.owner = null;
	
			//TODO: null appended as last missing argument, was unclean. rst
			M_Infantry.infantry_die.die(self, inflictor, attacker, damage, null);
		}
	};
	static EntThinkAdapter turret_driver_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			float[] target = { 0, 0, 0 };
			float[] dir = { 0, 0, 0 };
			float reaction_time;
	
			self.nextthink = GameBase.level.time + Defines.FRAMETIME;
	
			if (self.enemy != null && (!self.enemy.inuse || self.enemy.health <= 0))
				self.enemy = null;
	
			if (null == self.enemy) {
				if (!GameUtil.FindTarget(self))
					return true;
				self.monsterinfo.trail_time = GameBase.level.time;
				self.monsterinfo.aiflags &= ~Defines.AI_LOST_SIGHT;
			}
			else {
				if (GameUtil.visible(self, self.enemy)) {
					if ((self.monsterinfo.aiflags & Defines.AI_LOST_SIGHT) != 0) {
						self.monsterinfo.trail_time = GameBase.level.time;
						self.monsterinfo.aiflags &= ~Defines.AI_LOST_SIGHT;
					}
				}
				else {
					self.monsterinfo.aiflags |= Defines.AI_LOST_SIGHT;
					return true;
				}
			}
	
			// let the turret know where we want it to aim
			Math3D.VectorCopy(self.enemy.s.origin, target);
			target[2] += self.enemy.viewheight;
			Math3D.VectorSubtract(target, self.target_ent.s.origin, dir);
			Math3D.vectoangles(dir, self.target_ent.move_angles);
	
			// decide if we should shoot
			if (GameBase.level.time < self.monsterinfo.attack_finished)
				return true;
	
			reaction_time = (3 - GameBase.skill.value) * 1.0f;
			if ((GameBase.level.time - self.monsterinfo.trail_time) < reaction_time)
				return true;
	
			self.monsterinfo.attack_finished = GameBase.level.time + reaction_time + 1.0f;
			//FIXME how do we really want to pass this along?
			self.target_ent.spawnflags |= 65536;
			return true;
		}
	};
	public static EntThinkAdapter turret_driver_link = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			float[] vec = { 0, 0, 0 };
			edict_t ent;
	
			self.think = turret_driver_think;
			self.nextthink = GameBase.level.time + Defines.FRAMETIME;
	
			self.target_ent = GameBase.G_PickTarget(self.target);
			self.target_ent.owner = self;
			self.target_ent.teammaster.owner = self;
			Math3D.VectorCopy(self.target_ent.s.angles, self.s.angles);
	
			vec[0] = self.target_ent.s.origin[0] - self.s.origin[0];
			vec[1] = self.target_ent.s.origin[1] - self.s.origin[1];
			vec[2] = 0;
			self.move_origin[0] = Math3D.VectorLength(vec);
	
			Math3D.VectorSubtract(self.s.origin, self.target_ent.s.origin, vec);
			Math3D.vectoangles(vec, vec);
			GameTurret.AnglesNormalize(vec);
			self.move_origin[1] = vec[1];
	
			self.move_origin[2] = self.s.origin[2] - self.target_ent.s.origin[2];
	
			// add the driver to the end of them team chain
			for (ent = self.target_ent.teammaster; ent.teamchain != null; ent = ent.teamchain);
			ent.teamchain = self;
			self.teammaster = self.target_ent.teammaster;
			self.flags |= Defines.FL_TEAMSLAVE;
			return true;
		}
	};
}
