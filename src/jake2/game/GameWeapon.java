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

// Created on 12.11.2003 by RST.
// $Id: GameWeapon.java,v 1.3 2003-12-04 20:35:26 rst Exp $

package jake2.game;

import jake2.Defines;

public class GameWeapon extends GameAI {

	/*
	===============
	PlayerNoise
	
	Each player can have two noise objects associated with it:
	a personal noise (jumping, pain, weapon firing), and a weapon
	target noise (bullet wall impacts)
	
	Monsters that don't directly see the player can move
	to a noise in hopes of seeing the player from there.
	===============
	*/
	static void PlayerNoise(edict_t who, float[] where, int type) {
		edict_t noise;

		if (type == PNOISE_WEAPON) {
			if (who.client.silencer_shots == 0) {
				who.client.silencer_shots--;
				return;
			}
		}

		if (deathmatch.value != 0)
			return;

		if ((who.flags & FL_NOTARGET) != 0)
			return;

		if (who.mynoise == null) {
			noise= G_Spawn();
			noise.classname= "player_noise";
			VectorSet(noise.mins, -8, -8, -8);
			VectorSet(noise.maxs, 8, 8, 8);
			noise.owner= who;
			noise.svflags= SVF_NOCLIENT;
			who.mynoise= noise;

			noise= G_Spawn();
			noise.classname= "player_noise";
			VectorSet(noise.mins, -8, -8, -8);
			VectorSet(noise.maxs, 8, 8, 8);
			noise.owner= who;
			noise.svflags= SVF_NOCLIENT;
			who.mynoise2= noise;
		}

		if (type == PNOISE_SELF || type == PNOISE_WEAPON) {
			noise= who.mynoise;
			level.sound_entity= noise;
			level.sound_entity_framenum= level.framenum;
		} else // type == PNOISE_IMPACT
			{
			noise= who.mynoise2;
			level.sound2_entity= noise;
			level.sound2_entity_framenum= level.framenum;
		}

		VectorCopy(where, noise.s.origin);
		VectorSubtract(where, noise.maxs, noise.absmin);
		VectorAdd(where, noise.maxs, noise.absmax);
		noise.teleport_time= level.time;
		gi.linkentity(noise);
	}

	/*
	=================
	check_dodge
	
	This is a support routine used when a client is firing
	a non-instant attack weapon.  It checks to see if a
	monster's dodge function should be called.
	=================
	*/
	static void check_dodge(edict_t self, float[] start, float[] dir, int speed) {
		float[] end= { 0, 0, 0 };
		float[] v= { 0, 0, 0 };
		trace_t tr;
		float eta;

		// easy mode only ducks one quarter the time
		if (skill.value == 0) {
			if (random() > 0.25)
				return;
		}
		VectorMA(start, 8192, dir, end);
		tr= gi.trace(start, null, null, end, self, MASK_SHOT);
		if ((tr.ent != null)
			&& (tr.ent.svflags & SVF_MONSTER) != 0
			&& (tr.ent.health > 0)
			&& (null != tr.ent.monsterinfo.dodge)
			&& infront(tr.ent, self)) {
			VectorSubtract(tr.endpos, start, v);
			eta= (VectorLength(v) - tr.ent.maxs[0]) / speed;
			tr.ent.monsterinfo.dodge.dodge(tr.ent, self, eta);
		}
	}

	/*
	=================
	fire_blaster
	
	Fires a single blaster bolt.  Used by the blaster and hyper blaster.
	=================
	*/
	static EntTouchAdapter blaster_touch= new EntTouchAdapter() {

		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			int mod;

			if (other == self.owner)
				return;

			if (surf != null && (surf.flags & SURF_SKY) != 0) {
				G_FreeEdict(self);
				return;
			}

			if (self.owner.client != null)
				PlayerNoise(self.owner, self.s.origin, PNOISE_IMPACT);

			if (other.takedamage != 0) {
				if ((self.spawnflags & 1) != 0)
					mod= MOD_HYPERBLASTER;
				else
					mod= MOD_BLASTER;
				T_Damage(
					other,
					self,
					self.owner,
					self.velocity,
					self.s.origin,
					plane.normal,
					self.dmg,
					1,
					DAMAGE_ENERGY,
					mod);
			} else {
				gi.WriteByte(svc_temp_entity);
				gi.WriteByte(TE_BLASTER);
				gi.WritePosition(self.s.origin);
				if (plane == null)
					gi.WriteDir(vec3_origin);
				else
					gi.WriteDir(plane.normal);
				gi.multicast(self.s.origin, MULTICAST_PVS);
			}

			G_FreeEdict(self);
		}
	};

	static EntThinkAdapter Grenade_Explode= new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			float[] origin= { 0, 0, 0 };
			int mod;

			if (ent.owner.client != null)
				PlayerNoise(ent.owner, ent.s.origin, PNOISE_IMPACT);

			//FIXME: if we are onground then raise our Z just a bit since we are a point?
			if (ent.enemy != null) {
				float points= 0;
				float[] v= { 0, 0, 0 };
				float[] dir= { 0, 0, 0 };

				VectorAdd(ent.enemy.mins, ent.enemy.maxs, v);
				VectorMA(ent.enemy.s.origin, 0.5f, v, v);
				VectorSubtract(ent.s.origin, v, v);
				points= ent.dmg - 0.5f * VectorLength(v);
				VectorSubtract(ent.enemy.s.origin, ent.s.origin, dir);
				if ((ent.spawnflags & 1) != 0)
					mod= MOD_HANDGRENADE;
				else
					mod= MOD_GRENADE;
				T_Damage(
					ent.enemy,
					ent,
					ent.owner,
					dir,
					ent.s.origin,
					vec3_origin,
					(int) points,
					(int) points,
					DAMAGE_RADIUS,
					mod);
			}

			if ((ent.spawnflags & 2) != 0)
				mod= MOD_HELD_GRENADE;
			else if ((ent.spawnflags & 1) != 0)
				mod= MOD_HG_SPLASH;
			else
				mod= MOD_G_SPLASH;
			T_RadiusDamage(ent, ent.owner, ent.dmg, ent.enemy, ent.dmg_radius, mod);

			VectorMA(ent.s.origin, -0.02f, ent.velocity, origin);
			gi.WriteByte(svc_temp_entity);
			if (ent.waterlevel != 0) {
				if (ent.groundentity != null)
					gi.WriteByte(TE_GRENADE_EXPLOSION_WATER);
				else
					gi.WriteByte(TE_ROCKET_EXPLOSION_WATER);
			} else {
				if (ent.groundentity != null)
					gi.WriteByte(TE_GRENADE_EXPLOSION);
				else
					gi.WriteByte(TE_ROCKET_EXPLOSION);
			}
			gi.WritePosition(origin);
			gi.multicast(ent.s.origin, MULTICAST_PHS);

			G_FreeEdict(ent);
			return true;
		}
	};

	static EntTouchAdapter Grenade_Touch= new EntTouchAdapter() {
		public void touch(edict_t ent, edict_t other, cplane_t plane, csurface_t surf) {
			if (other == ent.owner)
				return;

			if (surf != null && 0 != (surf.flags & SURF_SKY)) {
				G_FreeEdict(ent);
				return;
			}

			if (other.takedamage == 0) {
				if ((ent.spawnflags & 1) != 0) {
					if (random() > 0.5f)
						gi.sound(
							ent,
							CHAN_VOICE,
							gi.soundindex("weapons/hgrenb1a.wav"),
							1,
							ATTN_NORM,
							0);
					else
						gi.sound(
							ent,
							CHAN_VOICE,
							gi.soundindex("weapons/hgrenb2a.wav"),
							1,
							ATTN_NORM,
							0);
				} else {
					gi.sound(
						ent,
						CHAN_VOICE,
						gi.soundindex("weapons/grenlb1b.wav"),
						1,
						ATTN_NORM,
						0);
				}
				return;
			}

			ent.enemy= other;
			Grenade_Explode.think(ent);
		}
	};

	/*
	=================
	fire_rocket
	=================
	*/
	static EntTouchAdapter rocket_touch= new EntTouchAdapter() {
		public void touch(edict_t ent, edict_t other, cplane_t plane, csurface_t surf) {
			float[] origin= { 0, 0, 0 };
			int n;

			if (other == ent.owner)
				return;

			if (surf != null && (surf.flags & SURF_SKY) != 0) {
				G_FreeEdict(ent);
				return;
			}

			if (ent.owner.client != null)
				PlayerNoise(ent.owner, ent.s.origin, PNOISE_IMPACT);

			// calculate position for the explosion entity
			VectorMA(ent.s.origin, -0.02f, ent.velocity, origin);

			if (other.takedamage != 0) {
				T_Damage(
					other,
					ent,
					ent.owner,
					ent.velocity,
					ent.s.origin,
					plane.normal,
					ent.dmg,
					0,
					0,
					MOD_ROCKET);
			} else {
				// don't throw any debris in net games
				if (deathmatch.value == 0 && 0 == coop.value) {
					if ((surf != null)
						&& 0
							== (surf.flags
								& (SURF_WARP | SURF_TRANS33 | SURF_TRANS66 | SURF_FLOWING))) {
						n= rand() % 5;
						while (n-- > 0)
							ThrowDebris(ent, "models/objects/debris2/tris.md2", 2, ent.s.origin);
					}
				}
			}

			T_RadiusDamage(ent, ent.owner, ent.radius_dmg, other, ent.dmg_radius, MOD_R_SPLASH);

			gi.WriteByte(svc_temp_entity);
			if (ent.waterlevel != 0)
				gi.WriteByte(TE_ROCKET_EXPLOSION_WATER);
			else
				gi.WriteByte(TE_ROCKET_EXPLOSION);
			gi.WritePosition(origin);
			gi.multicast(ent.s.origin, MULTICAST_PHS);

			G_FreeEdict(ent);
		}
	};

	/*
	=================
	fire_bfg
	=================
	*/
	static EntThinkAdapter bfg_explode= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			edict_t ent;
			float points;
			float[] v= { 0, 0, 0 };
			float dist;

			EdictIterator edit= null;

			if (self.s.frame == 0) {
				// the BFG effect
				ent= null;
				while ((edit= findradius(edit, self.s.origin, self.dmg_radius)) != null) {
					ent= edit.o;
					if (ent.takedamage == 0)
						continue;
					if (ent == self.owner)
						continue;
					if (!CanDamage(ent, self))
						continue;
					if (!CanDamage(ent, self.owner))
						continue;

					VectorAdd(ent.mins, ent.maxs, v);
					VectorMA(ent.s.origin, 0.5f, v, v);
					VectorSubtract(self.s.origin, v, v);
					dist= VectorLength(v);
					points= (float) (self.radius_dmg * (1.0 - Math.sqrt(dist / self.dmg_radius)));
					if (ent == self.owner)
						points= points * 0.5f;

					gi.WriteByte(svc_temp_entity);
					gi.WriteByte(TE_BFG_EXPLOSION);
					gi.WritePosition(ent.s.origin);
					gi.multicast(ent.s.origin, MULTICAST_PHS);
					T_Damage(
						ent,
						self,
						self.owner,
						self.velocity,
						ent.s.origin,
						vec3_origin,
						(int) points,
						0,
						DAMAGE_ENERGY,
						MOD_BFG_EFFECT);
				}
			}

			self.nextthink= level.time + FRAMETIME;
			self.s.frame++;
			if (self.s.frame == 5)
				self.think= G_FreeEdictA;
			return true;

		}
	};

	static EntTouchAdapter bfg_touch= new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			if (other == self.owner)
				return;

			if (surf != null && (surf.flags & SURF_SKY) != 0) {
				G_FreeEdict(self);
				return;
			}

			if (self.owner.client != null)
				PlayerNoise(self.owner, self.s.origin, PNOISE_IMPACT);

			// core explosion - prevents firing it into the wall/floor
			if (other.takedamage != 0)
				T_Damage(
					other,
					self,
					self.owner,
					self.velocity,
					self.s.origin,
					plane.normal,
					200,
					0,
					0,
					MOD_BFG_BLAST);
			T_RadiusDamage(self, self.owner, 200, other, 100, MOD_BFG_BLAST);

			gi.sound(self, CHAN_VOICE, gi.soundindex("weapons/bfg__x1b.wav"), 1, ATTN_NORM, 0);
			self.solid= SOLID_NOT;
			self.touch= null;
			VectorMA(self.s.origin, -1 * FRAMETIME, self.velocity, self.s.origin);
			VectorClear(self.velocity);
			self.s.modelindex= gi.modelindex("sprites/s_bfg3.sp2");
			self.s.frame= 0;
			self.s.sound= 0;
			self.s.effects &= ~EF_ANIM_ALLFAST;
			self.think= bfg_explode;
			self.nextthink= level.time + FRAMETIME;
			self.enemy= other;

			gi.WriteByte(svc_temp_entity);
			gi.WriteByte(TE_BFG_BIGEXPLOSION);
			gi.WritePosition(self.s.origin);
			gi.multicast(self.s.origin, MULTICAST_PVS);
		}
	};

	static EntThinkAdapter bfg_think= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			edict_t ent;
			edict_t ignore;
			float[] point= { 0, 0, 0 };
			float[] dir= { 0, 0, 0 };
			float[] start= { 0, 0, 0 };
			float[] end= { 0, 0, 0 };
			int dmg;
			trace_t tr;

			if (deathmatch.value != 0)
				dmg= 5;
			else
				dmg= 10;

			EdictIterator edit= null;
			while ((edit= findradius(edit, self.s.origin, 256)) != null) {
				ent= edit.o;

				if (ent == self)
					continue;

				if (ent == self.owner)
					continue;

				if (ent.takedamage == 0)
					continue;

				if (0 == (ent.svflags & SVF_MONSTER)
					&& (null == ent.client)
					&& (strcmp(ent.classname, "misc_explobox") != 0))
					continue;

				VectorMA(ent.absmin, 0.5f, ent.size, point);

				VectorSubtract(point, self.s.origin, dir);
				VectorNormalize(dir);

				ignore= self;
				VectorCopy(self.s.origin, start);
				VectorMA(start, 2048, dir, end);
				while (true) {
					tr=
						gi.trace(
							start,
							null,
							null,
							end,
							ignore,
							CONTENTS_SOLID | CONTENTS_MONSTER | CONTENTS_DEADMONSTER);

					if (null == tr.ent)
						break;

					// hurt it if we can
					if ((tr.ent.takedamage != 0)
						&& 0 == (tr.ent.flags & FL_IMMUNE_LASER)
						&& (tr.ent != self.owner))
						T_Damage(
							tr.ent,
							self,
							self.owner,
							dir,
							tr.endpos,
							vec3_origin,
							dmg,
							1,
							DAMAGE_ENERGY,
							MOD_BFG_LASER);

					// if we hit something that's not a monster or player we're done
					if (0 == (tr.ent.svflags & SVF_MONSTER) && (null == tr.ent.client)) {
						gi.WriteByte(svc_temp_entity);
						gi.WriteByte(TE_LASER_SPARKS);
						gi.WriteByte(4);
						gi.WritePosition(tr.endpos);
						gi.WriteDir(tr.plane.normal);
						gi.WriteByte(self.s.skinnum);
						gi.multicast(tr.endpos, MULTICAST_PVS);
						break;
					}

					ignore= tr.ent;
					VectorCopy(tr.endpos, start);
				}

				gi.WriteByte(svc_temp_entity);
				gi.WriteByte(TE_BFG_LASER);
				gi.WritePosition(self.s.origin);
				gi.WritePosition(tr.endpos);
				gi.multicast(self.s.origin, MULTICAST_PHS);
			}

			self.nextthink= level.time + FRAMETIME;
			return true;
		}
	};

}
