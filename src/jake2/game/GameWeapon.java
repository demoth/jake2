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
// $Id: GameWeapon.java,v 1.2 2003-11-29 13:28:29 rst Exp $

package jake2.game;

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
	fire_hit
	
	Used for all impact (hit/punch/slash) attacks
	=================
	*/
	static boolean fire_hit(edict_t self, float[] aim, int damage, int kick) {
		trace_t tr;
		float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 }, up= { 0, 0, 0 };
		float[] v= { 0, 0, 0 };
		float[] point= { 0, 0, 0 };
		float range;
		float[] dir= { 0, 0, 0 };

		//see if enemy is in range
		VectorSubtract(self.enemy.s.origin, self.s.origin, dir);
		range= VectorLength(dir);
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

		VectorMA(self.s.origin, range, dir, point);

		tr= gi.trace(self.s.origin, null, null, point, self, MASK_SHOT);
		if (tr.fraction < 1) {
			if (0 == tr.ent.takedamage)
				return false;
			// if it will hit any client/monster then hit the one we wanted to hit
			if ((tr.ent.svflags & SVF_MONSTER) != 0 || (tr.ent.client != null))
				tr.ent= self.enemy;
		}

		AngleVectors(self.s.angles, forward, right, up);
		VectorMA(self.s.origin, range, forward, point);
		VectorMA(point, aim[1], right, point);
		VectorMA(point, aim[2], up, point);
		VectorSubtract(point, self.enemy.s.origin, dir);

		// do the damage
		T_Damage(
			tr.ent,
			self,
			self,
			dir,
			point,
			vec3_origin,
			damage,
			kick / 2,
			DAMAGE_NO_KNOCKBACK,
			MOD_HIT);

		if (0 == (tr.ent.svflags & SVF_MONSTER) && (null == tr.ent.client))
			return false;

		// do our special form of knockback here
		VectorMA(self.enemy.absmin, 0.5f, self.enemy.size, v);
		VectorSubtract(v, point, v);
		VectorNormalize(v);
		VectorMA(self.enemy.velocity, kick, v, self.enemy.velocity);
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
	static void fire_lead(
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
		int content_mask= MASK_SHOT | MASK_WATER;

		tr= gi.trace(self.s.origin, null, null, start, self, MASK_SHOT);
		if (!(tr.fraction < 1.0)) {
			vectoangles(aimdir, dir);
			AngleVectors(dir, forward, right, up);

			r= crandom() * hspread;
			u= crandom() * vspread;
			VectorMA(start, 8192, forward, end);
			VectorMA(end, r, right, end);
			VectorMA(end, u, up, end);

			if ((gi.pointcontents(start) & MASK_WATER) != 0) {
				water= true;
				VectorCopy(start, water_start);
				content_mask &= ~MASK_WATER;
			}

			tr= gi.trace(start, null, null, end, self, content_mask);

			// see if we hit water
			if ((tr.contents & MASK_WATER) != 0) {
				int color;

				water= true;
				VectorCopy(tr.endpos, water_start);

				if (0 == VectorCompare(start, tr.endpos)) {
					if ((tr.contents & CONTENTS_WATER) != 0) {
						if (strcmp(tr.surface.name, "*brwater") == 0)
							color= SPLASH_BROWN_WATER;
						else
							color= SPLASH_BLUE_WATER;
					} else if ((tr.contents & CONTENTS_SLIME) != 0)
						color= SPLASH_SLIME;
					else if ((tr.contents & CONTENTS_LAVA) != 0)
						color= SPLASH_LAVA;
					else
						color= SPLASH_UNKNOWN;

					if (color != SPLASH_UNKNOWN) {
						gi.WriteByte(svc_temp_entity);
						gi.WriteByte(TE_SPLASH);
						gi.WriteByte(8);
						gi.WritePosition(tr.endpos);
						gi.WriteDir(tr.plane.normal);
						gi.WriteByte(color);
						gi.multicast(tr.endpos, MULTICAST_PVS);
					}

					// change bullet's course when it enters water
					VectorSubtract(end, start, dir);
					vectoangles(dir, dir);
					AngleVectors(dir, forward, right, up);
					r= crandom() * hspread * 2;
					u= crandom() * vspread * 2;
					VectorMA(water_start, 8192, forward, end);
					VectorMA(end, r, right, end);
					VectorMA(end, u, up, end);
				}

				// re-trace ignoring water this time
				tr= gi.trace(water_start, null, null, end, self, MASK_SHOT);
			}
		}

		// send gun puff / flash
		if (!((tr.surface != null) && 0 != (tr.surface.flags & SURF_SKY))) {
			if (tr.fraction < 1.0) {
				if (tr.ent.takedamage != 0) {
					T_Damage(
						tr.ent,
						self,
						self,
						aimdir,
						tr.endpos,
						tr.plane.normal,
						damage,
						kick,
						DAMAGE_BULLET,
						mod);
				} else {
					if (strncmp(tr.surface.name, "sky", 3) != 0) {
						gi.WriteByte(svc_temp_entity);
						gi.WriteByte(te_impact);
						gi.WritePosition(tr.endpos);
						gi.WriteDir(tr.plane.normal);
						gi.multicast(tr.endpos, MULTICAST_PVS);

						if (self.client != null)
							PlayerNoise(self, tr.endpos, PNOISE_IMPACT);
					}
				}
			}
		}

		// if went through water, determine where the end and make a bubble trail
		if (water) {
			float[] pos= { 0, 0, 0 };

			VectorSubtract(tr.endpos, water_start, dir);
			VectorNormalize(dir);
			VectorMA(tr.endpos, -2, dir, pos);
			if ((gi.pointcontents(pos) & MASK_WATER) != 0)
				VectorCopy(pos, tr.endpos);
			else
				tr= gi.trace(pos, null, null, water_start, tr.ent, MASK_WATER);

			VectorAdd(water_start, tr.endpos, pos);
			VectorScale(pos, 0.5f, pos);

			gi.WriteByte(svc_temp_entity);
			gi.WriteByte(TE_BUBBLETRAIL);
			gi.WritePosition(water_start);
			gi.WritePosition(tr.endpos);
			gi.multicast(pos, MULTICAST_PVS);
		}
	}

	/*
	=================
	fire_bullet
	
	Fires a single round.  Used for machinegun and chaingun.  Would be fine for
	pistols, rifles, etc....
	=================
	*/
	static void fire_bullet(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int kick,
		int hspread,
		int vspread,
		int mod) {
		fire_lead(self, start, aimdir, damage, kick, TE_GUNSHOT, hspread, vspread, mod);
	}

	/*
	=================
	fire_shotgun
	
	Shoots shotgun pellets.  Used by shotgun and super shotgun.
	=================
	*/
	static void fire_shotgun(
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
			fire_lead(self, start, aimdir, damage, kick, TE_SHOTGUN, hspread, vspread, mod);
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

	static void fire_blaster(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int speed,
		int effect,
		boolean hyper) {
		edict_t bolt;
		trace_t tr;

		VectorNormalize(dir);

		bolt= G_Spawn();
		bolt.svflags= SVF_DEADMONSTER;
		// yes, I know it looks weird that projectiles are deadmonsters
		// what this means is that when prediction is used against the object
		// (blaster/hyperblaster shots), the player won't be solid clipped against
		// the object.  Right now trying to run into a firing hyperblaster
		// is very jerky since you are predicted 'against' the shots.
		VectorCopy(start, bolt.s.origin);
		VectorCopy(start, bolt.s.old_origin);
		vectoangles(dir, bolt.s.angles);
		VectorScale(dir, speed, bolt.velocity);
		bolt.movetype= MOVETYPE_FLYMISSILE;
		bolt.clipmask= MASK_SHOT;
		bolt.solid= SOLID_BBOX;
		bolt.s.effects |= effect;
		VectorClear(bolt.mins);
		VectorClear(bolt.maxs);
		bolt.s.modelindex= gi.modelindex("models/objects/laser/tris.md2");
		bolt.s.sound= gi.soundindex("misc/lasfly.wav");
		bolt.owner= self;
		bolt.touch= blaster_touch;
		bolt.nextthink= level.time + 2;
		bolt.think= G_FreeEdictA;
		bolt.dmg= damage;
		bolt.classname= "bolt";
		if (hyper)
			bolt.spawnflags= 1;
		gi.linkentity(bolt);

		if (self.client != null)
			check_dodge(self, bolt.s.origin, dir, speed);

		tr= gi.trace(self.s.origin, null, null, bolt.s.origin, bolt, MASK_SHOT);
		if (tr.fraction < 1.0) {
			VectorMA(bolt.s.origin, -10, dir, bolt.s.origin);
			bolt.touch.touch(bolt, tr.ent, null, null);
		}
	}

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
	fire_grenade
	=================
	*/

	static void fire_grenade(
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

		vectoangles(aimdir, dir);
		AngleVectors(dir, forward, right, up);

		grenade= G_Spawn();
		VectorCopy(start, grenade.s.origin);
		VectorScale(aimdir, speed, grenade.velocity);
		VectorMA(grenade.velocity, 200f + crandom() * 10.0f, up, grenade.velocity);
		VectorMA(grenade.velocity, crandom() * 10.0f, right, grenade.velocity);
		VectorSet(grenade.avelocity, 300, 300, 300);
		grenade.movetype= MOVETYPE_BOUNCE;
		grenade.clipmask= MASK_SHOT;
		grenade.solid= SOLID_BBOX;
		grenade.s.effects |= EF_GRENADE;
		VectorClear(grenade.mins);
		VectorClear(grenade.maxs);
		grenade.s.modelindex= gi.modelindex("models/objects/grenade/tris.md2");
		grenade.owner= self;
		grenade.touch= Grenade_Touch;
		grenade.nextthink= level.time + timer;
		grenade.think= Grenade_Explode;
		grenade.dmg= damage;
		grenade.dmg_radius= damage_radius;
		grenade.classname= "grenade";

		gi.linkentity(grenade);
	}

	static void fire_grenade2(
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

		vectoangles(aimdir, dir);
		AngleVectors(dir, forward, right, up);

		grenade= G_Spawn();
		VectorCopy(start, grenade.s.origin);
		VectorScale(aimdir, speed, grenade.velocity);
		VectorMA(grenade.velocity, 200f + crandom() * 10.0f, up, grenade.velocity);
		VectorMA(grenade.velocity, crandom() * 10.0f, right, grenade.velocity);
		VectorSet(grenade.avelocity, 300f, 300f, 300f);
		grenade.movetype= MOVETYPE_BOUNCE;
		grenade.clipmask= MASK_SHOT;
		grenade.solid= SOLID_BBOX;
		grenade.s.effects |= EF_GRENADE;
		VectorClear(grenade.mins);
		VectorClear(grenade.maxs);
		grenade.s.modelindex= gi.modelindex("models/objects/grenade2/tris.md2");
		grenade.owner= self;
		grenade.touch= Grenade_Touch;
		grenade.nextthink= level.time + timer;
		grenade.think= Grenade_Explode;
		grenade.dmg= damage;
		grenade.dmg_radius= damage_radius;
		grenade.classname= "hgrenade";
		if (held)
			grenade.spawnflags= 3;
		else
			grenade.spawnflags= 1;
		grenade.s.sound= gi.soundindex("weapons/hgrenc1b.wav");

		if (timer <= 0.0)
			Grenade_Explode.think(grenade);
		else {
			gi.sound(self, CHAN_WEAPON, gi.soundindex("weapons/hgrent1a.wav"), 1, ATTN_NORM, 0);
			gi.linkentity(grenade);
		}
	}

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

	static void fire_rocket(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int speed,
		float damage_radius,
		int radius_damage) {
		edict_t rocket;

		rocket= G_Spawn();
		VectorCopy(start, rocket.s.origin);
		VectorCopy(dir, rocket.movedir);
		vectoangles(dir, rocket.s.angles);
		VectorScale(dir, speed, rocket.velocity);
		rocket.movetype= MOVETYPE_FLYMISSILE;
		rocket.clipmask= MASK_SHOT;
		rocket.solid= SOLID_BBOX;
		rocket.s.effects |= EF_ROCKET;
		VectorClear(rocket.mins);
		VectorClear(rocket.maxs);
		rocket.s.modelindex= gi.modelindex("models/objects/rocket/tris.md2");
		rocket.owner= self;
		rocket.touch= rocket_touch;
		rocket.nextthink= level.time + 8000 / speed;
		rocket.think= G_FreeEdictA;
		rocket.dmg= damage;
		rocket.radius_dmg= radius_damage;
		rocket.dmg_radius= damage_radius;
		rocket.s.sound= gi.soundindex("weapons/rockfly.wav");
		rocket.classname= "rocket";

		if (self.client != null)
			check_dodge(self, rocket.s.origin, dir, speed);

		gi.linkentity(rocket);
	}

	/*
	=================
	fire_rail
	=================
	*/
	static void fire_rail(edict_t self, float[] start, float[] aimdir, int damage, int kick) {
		float[] from= { 0, 0, 0 };
		float[] end= { 0, 0, 0 };
		trace_t tr= null;
		edict_t ignore;
		int mask;
		boolean water;

		VectorMA(start, 8192f, aimdir, end);
		VectorCopy(start, from);
		ignore= self;
		water= false;
		mask= MASK_SHOT | CONTENTS_SLIME | CONTENTS_LAVA;
		while (ignore != null) {
			tr= gi.trace(from, null, null, end, ignore, mask);

			if ((tr.contents & (CONTENTS_SLIME | CONTENTS_LAVA)) != 0) {
				mask &= ~(CONTENTS_SLIME | CONTENTS_LAVA);
				water= true;
			} else {
				//ZOID--added so rail goes through SOLID_BBOX entities (gibs, etc)
				if ((tr.ent.svflags & SVF_MONSTER) != 0
					|| (tr.ent.client != null)
					|| (tr.ent.solid == SOLID_BBOX))
					ignore= tr.ent;
				else
					ignore= null;

				if ((tr.ent != self) && (tr.ent.takedamage != 0))
					T_Damage(
						tr.ent,
						self,
						self,
						aimdir,
						tr.endpos,
						tr.plane.normal,
						damage,
						kick,
						0,
						MOD_RAILGUN);
			}

			VectorCopy(tr.endpos, from);
		}

		// send gun puff / flash
		gi.WriteByte(svc_temp_entity);
		gi.WriteByte(TE_RAILTRAIL);
		gi.WritePosition(start);
		gi.WritePosition(tr.endpos);
		gi.multicast(self.s.origin, MULTICAST_PHS);
		//		gi.multicast (start, MULTICAST_PHS);
		if (water) {
			gi.WriteByte(svc_temp_entity);
			gi.WriteByte(TE_RAILTRAIL);
			gi.WritePosition(start);
			gi.WritePosition(tr.endpos);
			gi.multicast(tr.endpos, MULTICAST_PHS);
		}

		if (self.client != null)
			PlayerNoise(self, tr.endpos, PNOISE_IMPACT);
	}

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

	static void fire_bfg(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int speed,
		float damage_radius) {
		edict_t bfg;

		bfg= G_Spawn();
		VectorCopy(start, bfg.s.origin);
		VectorCopy(dir, bfg.movedir);
		vectoangles(dir, bfg.s.angles);
		VectorScale(dir, speed, bfg.velocity);
		bfg.movetype= MOVETYPE_FLYMISSILE;
		bfg.clipmask= MASK_SHOT;
		bfg.solid= SOLID_BBOX;
		bfg.s.effects |= EF_BFG | EF_ANIM_ALLFAST;
		VectorClear(bfg.mins);
		VectorClear(bfg.maxs);
		bfg.s.modelindex= gi.modelindex("sprites/s_bfg1.sp2");
		bfg.owner= self;
		bfg.touch= bfg_touch;
		bfg.nextthink= level.time + 8000 / speed;
		bfg.think= G_FreeEdictA;
		bfg.radius_dmg= damage;
		bfg.dmg_radius= damage_radius;
		bfg.classname= "bfg blast";
		bfg.s.sound= gi.soundindex("weapons/bfg__l1a.wav");

		bfg.think= bfg_think;
		bfg.nextthink= level.time + FRAMETIME;
		bfg.teammaster= bfg;
		bfg.teamchain= null;

		if (self.client != null)
			check_dodge(self, bfg.s.origin, dir, speed);

		gi.linkentity(bfg);
	}
}
