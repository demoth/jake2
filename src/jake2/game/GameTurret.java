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
// $Id: GameTurret.java,v 1.2 2004-07-08 15:58:44 hzi Exp $

package jake2.game;

import jake2.*;
import jake2.client.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.Lib;
import jake2.util.Math3D;

public class GameTurret extends GameMisc {

	public static void AnglesNormalize(float[] vec) {
		while (vec[0] > 360)
			vec[0] -= 360;
		while (vec[0] < 0)
			vec[0] += 360;
		while (vec[1] > 360)
			vec[1] -= 360;
		while (vec[1] < 0)
			vec[1] += 360;
	}

	public static float SnapToEights(float x) {
		x *= 8.0;
		if (x > 0.0)
			x += 0.5;
		else
			x -= 0.5;
		return 0.125f * (int) x;
	}

	/*QUAKED turret_breach (0 0 0) ?
	This portion of the turret can change both pitch and yaw.
	The model  should be made with a flat pitch.
	It (and the associated base) need to be oriented towards 0.
	Use "angle" to set the starting angle.
	
	"speed"		default 50
	"dmg"		default 10
	"angle"		point this forward
	"target"	point this at an info_notnull at the muzzle tip
	"minpitch"	min acceptable pitch angle : default -30
	"maxpitch"	max acceptable pitch angle : default 30
	"minyaw"	min acceptable yaw angle   : default 0
	"maxyaw"	max acceptable yaw angle   : default 360
	*/

	public static void turret_breach_fire(edict_t self) {
		float[] f = { 0, 0, 0 }, r = { 0, 0, 0 }, u = { 0, 0, 0 };
		float[] start = { 0, 0, 0 };
		int damage;
		int speed;

		AngleVectors(self.s.angles, f, r, u);
		VectorMA(self.s.origin, self.move_origin[0], f, start);
		VectorMA(start, self.move_origin[1], r, start);
		VectorMA(start, self.move_origin[2], u, start);

		damage = (int) (100 + random() * 50);
		speed = (int) (550 + 50 * skill.value);
		Fire.fire_rocket(self.teammaster.owner, start, f, damage, speed, 150, damage);
		gi.positioned_sound(start, self, CHAN_WEAPON, gi.soundindex("weapons/rocklf1a.wav"), 1, ATTN_NORM, 0);
	}

	public static void SP_turret_breach(edict_t self) {
		self.solid = SOLID_BSP;
		self.movetype = MOVETYPE_PUSH;
		gi.setmodel(self, self.model);

		if (self.speed == 0)
			self.speed = 50;
		if (self.dmg == 0)
			self.dmg = 10;

		if (st.minpitch == 0)
			st.minpitch = -30;
		if (st.maxpitch == 0)
			st.maxpitch = 30;
		if (st.maxyaw == 0)
			st.maxyaw = 360;

		self.pos1[PITCH] = -1 * st.minpitch;
		self.pos1[YAW] = st.minyaw;
		self.pos2[PITCH] = -1 * st.maxpitch;
		self.pos2[YAW] = st.maxyaw;

		self.ideal_yaw = self.s.angles[YAW];
		self.move_angles[YAW] = self.ideal_yaw;

		self.blocked = GameTurretAdapters.turret_blocked;

		self.think = GameTurretAdapters.turret_breach_finish_init;
		self.nextthink = level.time + FRAMETIME;
		gi.linkentity(self);
	}

	/*QUAKED turret_base (0 0 0) ?
	This portion of the turret changes yaw only.
	MUST be teamed with a turret_breach.
	*/

	public static void SP_turret_base(edict_t self) {
		self.solid = SOLID_BSP;
		self.movetype = MOVETYPE_PUSH;
		gi.setmodel(self, self.model);
		self.blocked = GameTurretAdapters.turret_blocked;
		gi.linkentity(self);
	}

	public static void SP_turret_driver(edict_t self) {
		if (deathmatch.value != 0) {
			G_FreeEdict(self);
			return;
		}

		self.movetype = MOVETYPE_PUSH;
		self.solid = SOLID_BBOX;
		self.s.modelindex = gi.modelindex("models/monsters/infantry/tris.md2");
		VectorSet(self.mins, -16, -16, -24);
		VectorSet(self.maxs, 16, 16, 32);

		self.health = 100;
		self.gib_health = 0;
		self.mass = 200;
		self.viewheight = 24;

		self.die = GameTurretAdapters.turret_driver_die;
		self.monsterinfo.stand = M_Infantry.infantry_stand;

		self.flags |= FL_NO_KNOCKBACK;

		level.total_monsters++;

		self.svflags |= SVF_MONSTER;
		self.s.renderfx |= RF_FRAMELERP;
		self.takedamage = DAMAGE_AIM;
		self.use = GameUtilAdapters.monster_use;
		self.clipmask = MASK_MONSTERSOLID;
		VectorCopy(self.s.origin, self.s.old_origin);
		self.monsterinfo.aiflags |= AI_STAND_GROUND | AI_DUCKED;

		if (st.item != null) {
			self.item = FindItemByClassname(st.item);
			if (self.item == null)
				gi.dprintf(self.classname + " at " + vtos(self.s.origin) + " has bad item: " + st.item + "\n");
		}

		self.think = GameTurretAdapters.turret_driver_link;
		self.nextthink = level.time + FRAMETIME;

		gi.linkentity(self);
	}
}
