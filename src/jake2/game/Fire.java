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

// Created on 04.12.2003 by RST.
// $Id: Fire.java,v 1.5 2004-02-04 18:10:55 rst Exp $

package jake2.game;


 import jake2.*;
 import jake2.client.*;
 import jake2.qcommon.*;
 import jake2.render.*;
 import jake2.server.*;
import jake2.util.*;

public class Fire {

	/*
	=================
	fire_hit
	
	Used for all impact (hit/punch/slash) attacks
	=================
	*/
	public static  boolean fire_hit(edict_t self, float[] aim, int damage, int kick) {
		trace_t tr;
		float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 }, up= { 0, 0, 0 };
		float[] v= { 0, 0, 0 };
		float[] point= { 0, 0, 0 };
		float range;
		float[] dir= { 0, 0, 0 };
	
		//see if enemy is in range
		Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, dir);
		range= Math3D.VectorLength(dir);
		if (range > aim[0])
			return false;
	
		if (aim[1] > self.mins[0] && aim[1] < self.maxs[0]) {
			// the hit is straight on so back the range up to the edge of their bbox
			range -= self.enemy.maxs[0];
		} else {
			// this is a side hit so adjust the "right" value out to the edge of their bbox
			if (aim[1] < 0)
				aim[1]= self.enemy.mins[0];
			else
				aim[1]= self.enemy.maxs[0];
		}
	
		Math3D.VectorMA(self.s.origin, range, dir, point);
	
		tr= GameBase.gi.trace(self.s.origin, null, null, point, self, Defines.MASK_SHOT);
		if (tr.fraction < 1) {
			if (0 == tr.ent.takedamage)
				return false;
			// if it will hit any client/monster then hit the one we wanted to hit
			if ((tr.ent.svflags & Defines.SVF_MONSTER) != 0 || (tr.ent.client != null))
				tr.ent= self.enemy;
		}
	
		Math3D.AngleVectors(self.s.angles, forward, right, up);
		Math3D.VectorMA(self.s.origin, range, forward, point);
		Math3D.VectorMA(point, aim[1], right, point);
		Math3D.VectorMA(point, aim[2], up, point);
		Math3D.VectorSubtract(point, self.enemy.s.origin, dir);
	
		// do the damage
		GameUtil.T_Damage(
			tr.ent,
			self,
			self,
			dir,
			point,
			GameBase.vec3_origin,
			damage,
			kick / 2,
			Defines.DAMAGE_NO_KNOCKBACK,
			Defines.MOD_HIT);
	
		if (0 == (tr.ent.svflags & Defines.SVF_MONSTER) && (null == tr.ent.client))
			return false;
	
		// do our special form of knockback here
		Math3D.VectorMA(self.enemy.absmin, 0.5f, self.enemy.size, v);
		Math3D.VectorSubtract(v, point, v);
		Math3D.VectorNormalize(v);
		Math3D.VectorMA(self.enemy.velocity, kick, v, self.enemy.velocity);
		if (self.enemy.velocity[2] > 0)
			self.enemy.groundentity= null;
		return true;
	}
	/*
	=================
	fire_lead
	
	This is an internal support routine used for bullet/pellet based weapons.
	=================
	*/
	public static  void fire_lead(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int kick,
		int te_impact,
		int hspread,
		int vspread,
		int mod) {
		trace_t tr;
		float[] dir= { 0, 0, 0 };
		float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 }, up= { 0, 0, 0 };
		float[] end= { 0, 0, 0 };
		float r;
		float u;
		float[] water_start= { 0, 0, 0 };
		boolean water= false;
		int content_mask= Defines.MASK_SHOT | Defines.MASK_WATER;
	
		tr= GameBase.gi.trace(self.s.origin, null, null, start, self, Defines.MASK_SHOT);
		if (!(tr.fraction < 1.0)) {
			Math3D.vectoangles(aimdir, dir);
			Math3D.AngleVectors(dir, forward, right, up);
	
			r= Lib.crandom() * hspread;
			u= Lib.crandom() * vspread;
			Math3D.VectorMA(start, 8192, forward, end);
			Math3D.VectorMA(end, r, right, end);
			Math3D.VectorMA(end, u, up, end);
	
			if ((GameBase.gi.pointcontents.pointcontents(start) & Defines.MASK_WATER) != 0) {
				water= true;
				Math3D.VectorCopy(start, water_start);
				content_mask &= ~Defines.MASK_WATER;
			}
	
			tr= GameBase.gi.trace(start, null, null, end, self, content_mask);
	
			// see if we hit water
			if ((tr.contents & Defines.MASK_WATER) != 0) {
				int color;
	
				water= true;
				Math3D.VectorCopy(tr.endpos, water_start);
	
				if (0 == Math3D.VectorCompare(start, tr.endpos)) {
					if ((tr.contents & Defines.CONTENTS_WATER) != 0) {
						if (Lib.strcmp(tr.surface.name, "*brwater") == 0)
							color= Defines.SPLASH_BROWN_WATER;
						else
							color= Defines.SPLASH_BLUE_WATER;
					} else if ((tr.contents & Defines.CONTENTS_SLIME) != 0)
						color= Defines.SPLASH_SLIME;
					else if ((tr.contents & Defines.CONTENTS_LAVA) != 0)
						color= Defines.SPLASH_LAVA;
					else
						color= Defines.SPLASH_UNKNOWN;
	
					if (color != Defines.SPLASH_UNKNOWN) {
						GameBase.gi.WriteByte(Defines.svc_temp_entity);
						GameBase.gi.WriteByte(Defines.TE_SPLASH);
						GameBase.gi.WriteByte(8);
						GameBase.gi.WritePosition(tr.endpos);
						GameBase.gi.WriteDir(tr.plane.normal);
						GameBase.gi.WriteByte(color);
						GameBase.gi.multicast(tr.endpos, Defines.MULTICAST_PVS);
					}
	
					// change bullet's course when it enters water
					Math3D.VectorSubtract(end, start, dir);
					Math3D.vectoangles(dir, dir);
					Math3D.AngleVectors(dir, forward, right, up);
					r= Lib.crandom() * hspread * 2;
					u= Lib.crandom() * vspread * 2;
					Math3D.VectorMA(water_start, 8192, forward, end);
					Math3D.VectorMA(end, r, right, end);
					Math3D.VectorMA(end, u, up, end);
				}
	
				// re-trace ignoring water this time
				tr= GameBase.gi.trace(water_start, null, null, end, self, Defines.MASK_SHOT);
			}
		}
	
		// send gun puff / flash
		if (!((tr.surface != null) && 0 != (tr.surface.flags & Defines.SURF_SKY))) {
			if (tr.fraction < 1.0) {
				if (tr.ent.takedamage != 0) {
					GameUtil.T_Damage(
						tr.ent,
						self,
						self,
						aimdir,
						tr.endpos,
						tr.plane.normal,
						damage,
						kick,
						Defines.DAMAGE_BULLET,
						mod);
				} else {
					if (Lib.strncmp(tr.surface.name, "sky", 3) != 0) {
						GameBase.gi.WriteByte(Defines.svc_temp_entity);
						GameBase.gi.WriteByte(te_impact);
						GameBase.gi.WritePosition(tr.endpos);
						GameBase.gi.WriteDir(tr.plane.normal);
						GameBase.gi.multicast(tr.endpos, Defines.MULTICAST_PVS);
	
						if (self.client != null)
							GameWeapon.PlayerNoise(self, tr.endpos, Defines.PNOISE_IMPACT);
					}
				}
			}
		}
	
		// if went through water, determine where the end and make a bubble trail
		if (water) {
			float[] pos= { 0, 0, 0 };
	
			Math3D.VectorSubtract(tr.endpos, water_start, dir);
			Math3D.VectorNormalize(dir);
			Math3D.VectorMA(tr.endpos, -2, dir, pos);
			if ((Game.gi.pointcontents.pointcontents(pos) & Defines.MASK_WATER) != 0)
				Math3D.VectorCopy(pos, tr.endpos);
			else
				tr= GameBase.gi.trace(pos, null, null, water_start, tr.ent, Defines.MASK_WATER);
	
			Math3D.VectorAdd(water_start, tr.endpos, pos);
			Math3D.VectorScale(pos, 0.5f, pos);
	
			GameBase.gi.WriteByte(Defines.svc_temp_entity);
			GameBase.gi.WriteByte(Defines.TE_BUBBLETRAIL);
			GameBase.gi.WritePosition(water_start);
			GameBase.gi.WritePosition(tr.endpos);
			GameBase.gi.multicast(pos, Defines.MULTICAST_PVS);
		}
	}
	/*
	=================
	fire_bullet
	
	Fires a single round.  Used for machinegun and chaingun.  Would be fine for
	pistols, rifles, etc....
	=================
	*/
	public static  void fire_bullet(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int kick,
		int hspread,
		int vspread,
		int mod) {
		fire_lead(self, start, aimdir, damage, kick, Defines.TE_GUNSHOT, hspread, vspread, mod);
	}
	/*
	=================
	fire_shotgun
	
	Shoots shotgun pellets.  Used by shotgun and super shotgun.
	=================
	*/
	public static  void fire_shotgun(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int kick,
		int hspread,
		int vspread,
		int count,
		int mod) {
		int i;
	
		for (i= 0; i < count; i++)
			fire_lead(self, start, aimdir, damage, kick, Defines.TE_SHOTGUN, hspread, vspread, mod);
	}
	public static  void fire_blaster(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int speed,
		int effect,
		boolean hyper) {
		edict_t bolt;
		trace_t tr;
	
		Math3D.VectorNormalize(dir);
	
		bolt= GameUtil.G_Spawn();
		bolt.svflags= Defines.SVF_DEADMONSTER;
		// yes, I know it looks weird that projectiles are deadmonsters
		// what this means is that when prediction is used against the object
		// (blaster/hyperblaster shots), the player won't be solid clipped against
		// the object.  Right now trying to run into a firing hyperblaster
		// is very jerky since you are predicted 'against' the shots.
		Math3D.VectorCopy(start, bolt.s.origin);
		Math3D.VectorCopy(start, bolt.s.old_origin);
		Math3D.vectoangles(dir, bolt.s.angles);
		Math3D.VectorScale(dir, speed, bolt.velocity);
		bolt.movetype= Defines.MOVETYPE_FLYMISSILE;
		bolt.clipmask= Defines.MASK_SHOT;
		bolt.solid= Defines.SOLID_BBOX;
		bolt.s.effects |= effect;
		Math3D.VectorClear(bolt.mins);
		Math3D.VectorClear(bolt.maxs);
		bolt.s.modelindex= GameBase.gi.modelindex("models/objects/laser/tris.md2");
		bolt.s.sound= GameBase.gi.soundindex("misc/lasfly.wav");
		bolt.owner= self;
		bolt.touch= GameWeapon.blaster_touch;
		bolt.nextthink= GameBase.level.time + 2;
		bolt.think= GameUtil.G_FreeEdictA;
		bolt.dmg= damage;
		bolt.classname= "bolt";
		if (hyper)
			bolt.spawnflags= 1;
		GameBase.gi.linkentity(bolt);
	
		if (self.client != null)
			GameWeapon.check_dodge(self, bolt.s.origin, dir, speed);
	
		tr= GameBase.gi.trace(self.s.origin, null, null, bolt.s.origin, bolt, Defines.MASK_SHOT);
		if (tr.fraction < 1.0) {
			Math3D.VectorMA(bolt.s.origin, -10, dir, bolt.s.origin);
			bolt.touch.touch(bolt, tr.ent, null, null);
		}
	}
	/*
	=================
	fire_grenade
	=================
	*/
	
	public static  void fire_grenade(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int speed,
		float timer,
		float damage_radius) {
		edict_t grenade;
		float[] dir= { 0, 0, 0 };
		float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 }, up= { 0, 0, 0 };
	
		Math3D.vectoangles(aimdir, dir);
		Math3D.AngleVectors(dir, forward, right, up);
	
		grenade= GameUtil.G_Spawn();
		Math3D.VectorCopy(start, grenade.s.origin);
		Math3D.VectorScale(aimdir, speed, grenade.velocity);
		Math3D.VectorMA(grenade.velocity, 200f + Lib.crandom() * 10.0f, up, grenade.velocity);
		Math3D.VectorMA(grenade.velocity, Lib.crandom() * 10.0f, right, grenade.velocity);
		Math3D.VectorSet(grenade.avelocity, 300, 300, 300);
		grenade.movetype= Defines.MOVETYPE_BOUNCE;
		grenade.clipmask= Defines.MASK_SHOT;
		grenade.solid= Defines.SOLID_BBOX;
		grenade.s.effects |= Defines.EF_GRENADE;
		Math3D.VectorClear(grenade.mins);
		Math3D.VectorClear(grenade.maxs);
		grenade.s.modelindex= GameBase.gi.modelindex("models/objects/grenade/tris.md2");
		grenade.owner= self;
		grenade.touch= GameWeapon.Grenade_Touch;
		grenade.nextthink= GameBase.level.time + timer;
		grenade.think= GameWeapon.Grenade_Explode;
		grenade.dmg= damage;
		grenade.dmg_radius= damage_radius;
		grenade.classname= "grenade";
	
		GameBase.gi.linkentity(grenade);
	}
	public static  void fire_grenade2(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int speed,
		float timer,
		float damage_radius,
		boolean held) {
		edict_t grenade;
		float[] dir= { 0, 0, 0 };
		float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 }, up= { 0, 0, 0 };
	
		Math3D.vectoangles(aimdir, dir);
		Math3D.AngleVectors(dir, forward, right, up);
	
		grenade= GameUtil.G_Spawn();
		Math3D.VectorCopy(start, grenade.s.origin);
		Math3D.VectorScale(aimdir, speed, grenade.velocity);
		Math3D.VectorMA(grenade.velocity, 200f + Lib.crandom() * 10.0f, up, grenade.velocity);
		Math3D.VectorMA(grenade.velocity, Lib.crandom() * 10.0f, right, grenade.velocity);
		Math3D.VectorSet(grenade.avelocity, 300f, 300f, 300f);
		grenade.movetype= Defines.MOVETYPE_BOUNCE;
		grenade.clipmask= Defines.MASK_SHOT;
		grenade.solid= Defines.SOLID_BBOX;
		grenade.s.effects |= Defines.EF_GRENADE;
		Math3D.VectorClear(grenade.mins);
		Math3D.VectorClear(grenade.maxs);
		grenade.s.modelindex= GameBase.gi.modelindex("models/objects/grenade2/tris.md2");
		grenade.owner= self;
		grenade.touch= GameWeapon.Grenade_Touch;
		grenade.nextthink= GameBase.level.time + timer;
		grenade.think= GameWeapon.Grenade_Explode;
		grenade.dmg= damage;
		grenade.dmg_radius= damage_radius;
		grenade.classname= "hgrenade";
		if (held)
			grenade.spawnflags= 3;
		else
			grenade.spawnflags= 1;
		grenade.s.sound= GameBase.gi.soundindex("weapons/hgrenc1b.wav");
	
		if (timer <= 0.0)
			GameWeapon.Grenade_Explode.think(grenade);
		else {
			GameBase.gi.sound(self, Defines.CHAN_WEAPON, GameBase.gi.soundindex("weapons/hgrent1a.wav"), 1, Defines.ATTN_NORM, 0);
			GameBase.gi.linkentity(grenade);
		}
	}
	public static  void fire_rocket(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int speed,
		float damage_radius,
		int radius_damage) {
		edict_t rocket;
	
		rocket= GameUtil.G_Spawn();
		Math3D.VectorCopy(start, rocket.s.origin);
		Math3D.VectorCopy(dir, rocket.movedir);
		Math3D.vectoangles(dir, rocket.s.angles);
		Math3D.VectorScale(dir, speed, rocket.velocity);
		rocket.movetype= Defines.MOVETYPE_FLYMISSILE;
		rocket.clipmask= Defines.MASK_SHOT;
		rocket.solid= Defines.SOLID_BBOX;
		rocket.s.effects |= Defines.EF_ROCKET;
		Math3D.VectorClear(rocket.mins);
		Math3D.VectorClear(rocket.maxs);
		rocket.s.modelindex= GameBase.gi.modelindex("models/objects/rocket/tris.md2");
		rocket.owner= self;
		rocket.touch= GameWeapon.rocket_touch;
		rocket.nextthink= GameBase.level.time + 8000 / speed;
		rocket.think= GameUtil.G_FreeEdictA;
		rocket.dmg= damage;
		rocket.radius_dmg= radius_damage;
		rocket.dmg_radius= damage_radius;
		rocket.s.sound= GameBase.gi.soundindex("weapons/rockfly.wav");
		rocket.classname= "rocket";
	
		if (self.client != null)
			GameWeapon.check_dodge(self, rocket.s.origin, dir, speed);
	
		GameBase.gi.linkentity(rocket);
	}
	/*
	=================
	fire_rail
	=================
	*/
	public static  void fire_rail(edict_t self, float[] start, float[] aimdir, int damage, int kick) {
		float[] from= { 0, 0, 0 };
		float[] end= { 0, 0, 0 };
		trace_t tr= null;
		edict_t ignore;
		int mask;
		boolean water;
	
		Math3D.VectorMA(start, 8192f, aimdir, end);
		Math3D.VectorCopy(start, from);
		ignore= self;
		water= false;
		mask= Defines.MASK_SHOT | Defines.CONTENTS_SLIME | Defines.CONTENTS_LAVA;
		while (ignore != null) {
			tr= GameBase.gi.trace(from, null, null, end, ignore, mask);
	
			if ((tr.contents & (Defines.CONTENTS_SLIME | Defines.CONTENTS_LAVA)) != 0) {
				mask &= ~(Defines.CONTENTS_SLIME | Defines.CONTENTS_LAVA);
				water= true;
			} else {
				//ZOID--added so rail goes through SOLID_BBOX entities (gibs, etc)
				if ((tr.ent.svflags & Defines.SVF_MONSTER) != 0
					|| (tr.ent.client != null)
					|| (tr.ent.solid == Defines.SOLID_BBOX))
					ignore= tr.ent;
				else
					ignore= null;
	
				if ((tr.ent != self) && (tr.ent.takedamage != 0))
					GameUtil.T_Damage(
						tr.ent,
						self,
						self,
						aimdir,
						tr.endpos,
						tr.plane.normal,
						damage,
						kick,
						0,
						Defines.MOD_RAILGUN);
			}
	
			Math3D.VectorCopy(tr.endpos, from);
		}
	
		// send gun puff / flash
		GameBase.gi.WriteByte(Defines.svc_temp_entity);
		GameBase.gi.WriteByte(Defines.TE_RAILTRAIL);
		GameBase.gi.WritePosition(start);
		GameBase.gi.WritePosition(tr.endpos);
		GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PHS);
		//		gi.multicast (start, MULTICAST_PHS);
		if (water) {
			GameBase.gi.WriteByte(Defines.svc_temp_entity);
			GameBase.gi.WriteByte(Defines.TE_RAILTRAIL);
			GameBase.gi.WritePosition(start);
			GameBase.gi.WritePosition(tr.endpos);
			GameBase.gi.multicast(tr.endpos, Defines.MULTICAST_PHS);
		}
	
		if (self.client != null)
			GameWeapon.PlayerNoise(self, tr.endpos, Defines.PNOISE_IMPACT);
	}
	public static  void fire_bfg(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int speed,
		float damage_radius) {
		edict_t bfg;
	
		bfg= GameUtil.G_Spawn();
		Math3D.VectorCopy(start, bfg.s.origin);
		Math3D.VectorCopy(dir, bfg.movedir);
		Math3D.vectoangles(dir, bfg.s.angles);
		Math3D.VectorScale(dir, speed, bfg.velocity);
		bfg.movetype= Defines.MOVETYPE_FLYMISSILE;
		bfg.clipmask= Defines.MASK_SHOT;
		bfg.solid= Defines.SOLID_BBOX;
		bfg.s.effects |= Defines.EF_BFG | Defines.EF_ANIM_ALLFAST;
		Math3D.VectorClear(bfg.mins);
		Math3D.VectorClear(bfg.maxs);
		bfg.s.modelindex= GameBase.gi.modelindex("sprites/s_bfg1.sp2");
		bfg.owner= self;
		bfg.touch= GameWeapon.bfg_touch;
		bfg.nextthink= GameBase.level.time + 8000 / speed;
		bfg.think= GameUtil.G_FreeEdictA;
		bfg.radius_dmg= damage;
		bfg.dmg_radius= damage_radius;
		bfg.classname= "bfg blast";
		bfg.s.sound= GameBase.gi.soundindex("weapons/bfg__l1a.wav");
	
		bfg.think= GameWeapon.bfg_think;
		bfg.nextthink= GameBase.level.time + Defines.FRAMETIME;
		bfg.teammaster= bfg;
		bfg.teamchain= null;
	
		if (self.client != null)
			GameWeapon.check_dodge(self, bfg.s.origin, dir, speed);
	
		GameBase.gi.linkentity(bfg);
	}
}
