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

// Created on 02.11.2003 by RST.

package jake2.game;

import java.util.*;

import jake2.game.*;

public class GAME_AI extends GAME_UTIL {

	/*
	===============
	GetItemByIndex
	===============
	*/
	public static gitem_t GetItemByIndex(int index) {

		gitem_t xxx= new gitem_t(123);

		if (index == 0 || index >= game.num_items)
			return null;

		return itemlist[index];
	}

	static float[][] monster_flash_offset= null;

	/** Common Boss explode animation.*/

	static EntThinkAdapter BossExplode= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			float[] org= { 0, 0, 0 };

			int n;

			self.think= BossExplode;
			VectorCopy(self.s.origin, org);
			org[2] += 24 + (rand() & 15);
			switch (self.count++) {
				case 0 :
					org[0] -= 24;
					org[1] -= 24;
					break;
				case 1 :
					org[0] += 24;
					org[1] += 24;
					break;
				case 2 :
					org[0] += 24;
					org[1] -= 24;
					break;
				case 3 :
					org[0] -= 24;
					org[1] += 24;
					break;
				case 4 :
					org[0] -= 48;
					org[1] -= 48;
					break;
				case 5 :
					org[0] += 48;
					org[1] += 48;
					break;
				case 6 :
					org[0] -= 48;
					org[1] += 48;
					break;
				case 7 :
					org[0] += 48;
					org[1] -= 48;
					break;
				case 8 :
					self.s.sound= 0;
					for (n= 0; n < 4; n++)
						ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", 500, GIB_ORGANIC);
					for (n= 0; n < 8; n++)
						ThrowGib(self, "models/objects/gibs/sm_metal/tris.md2", 500, GIB_METALLIC);
					ThrowGib(self, "models/objects/gibs/chest/tris.md2", 500, GIB_ORGANIC);
					ThrowHead(self, "models/objects/gibs/gear/tris.md2", 500, GIB_METALLIC);
					self.deadflag= DEAD_DEAD;
					return true;
			}

			gi.WriteByte(svc_temp_entity);
			gi.WriteByte(TE_EXPLOSION1);
			gi.WritePosition(org);
			gi.multicast(self.s.origin, MULTICAST_PVS);

			self.nextthink= level.time + 0.1f;
			return true;
		}
	};

	// FIXME mosnters should call these with a totally accurate direction
	//	and we can mess it up based on skill.  Spread should be for normal
	//	and we can tighten or loosen based on skill.  We could muck with
	//	the damages too, but I'm not sure that's such a good idea.
	static void monster_fire_bullet(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int kick,
		int hspread,
		int vspread,
		int flashtype) {
		GAME_WEAPON.fire_bullet(self, start, dir, damage, kick, hspread, vspread, MOD_UNKNOWN);

		gi.WriteByte(svc_muzzleflash2);
		gi.WriteShort(self.s.number);
		gi.WriteByte(flashtype);
		gi.multicast(start, MULTICAST_PVS);
	}

	/** The Moster fires the shotgun. */
	static void monster_fire_shotgun(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int kick,
		int hspread,
		int vspread,
		int count,
		int flashtype) {
		GAME_WEAPON.fire_shotgun(
			self,
			start,
			aimdir,
			damage,
			kick,
			hspread,
			vspread,
			count,
			MOD_UNKNOWN);

		gi.WriteByte(svc_muzzleflash2);
		gi.WriteShort(self.s.number);
		gi.WriteByte(flashtype);
		gi.multicast(start, MULTICAST_PVS);
	}

	/** The Moster fires the blaster. */
	static void monster_fire_blaster(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int speed,
		int flashtype,
		int effect) {
		GAME_WEAPON.fire_blaster(self, start, dir, damage, speed, effect, false);

		gi.WriteByte(svc_muzzleflash2);
		gi.WriteShort(self.s.number);
		gi.WriteByte(flashtype);
		gi.multicast(start, MULTICAST_PVS);
	}

	/** The Moster fires the grenade. */
	static void monster_fire_grenade(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int speed,
		int flashtype) {
		GAME_WEAPON.fire_grenade(self, start, aimdir, damage, speed, 2.5f, damage + 40);

		gi.WriteByte(svc_muzzleflash2);
		gi.WriteShort(self.s.number);
		gi.WriteByte(flashtype);
		gi.multicast(start, MULTICAST_PVS);
	}

	/** The Moster fires the rocket. */
	static void monster_fire_rocket(
		edict_t self,
		float[] start,
		float[] dir,
		int damage,
		int speed,
		int flashtype) {
		GAME_WEAPON.fire_rocket(self, start, dir, damage, speed, damage + 20, damage);

		gi.WriteByte(svc_muzzleflash2);
		gi.WriteShort(self.s.number);
		gi.WriteByte(flashtype);
		gi.multicast(start, MULTICAST_PVS);
	}

	/** The Moster fires the railgun. */
	static void monster_fire_railgun(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int kick,
		int flashtype) {
		GAME_WEAPON.fire_rail(self, start, aimdir, damage, kick);

		gi.WriteByte(svc_muzzleflash2);
		gi.WriteShort(self.s.number);
		gi.WriteByte(flashtype);
		gi.multicast(start, MULTICAST_PVS);
	}

	/** The Moster fires the bfg. */
	static void monster_fire_bfg(
		edict_t self,
		float[] start,
		float[] aimdir,
		int damage,
		int speed,
		int kick,
		float damage_radius,
		int flashtype) {
		GAME_WEAPON.fire_bfg(self, start, aimdir, damage, speed, damage_radius);

		gi.WriteByte(svc_muzzleflash2);
		gi.WriteShort(self.s.number);
		gi.WriteByte(flashtype);
		gi.multicast(start, MULTICAST_PVS);
	}

	/** Stops the Flies. */
	static EntThinkAdapter M_FliesOff= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.s.effects &= ~EF_FLIES;
			self.s.sound= 0;
			return true;
		}
	};

	/** Starts the Flies as setting the animation flag in the entity. */
	static EntThinkAdapter M_FliesOn= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (self.waterlevel != 0)
				return true;

			self.s.effects |= EF_FLIES;
			self.s.sound= gi.soundindex("infantry/inflies1.wav");
			self.think= M_FliesOff;
			self.nextthink= level.time + 60;
			return true;
		}
	};

	/** Adds some flies after a random time */
	static EntThinkAdapter M_FlyCheck= new EntThinkAdapter() {
		public boolean think(edict_t self) {

			if (self.waterlevel != 0)
				return true;

			if (random() > 0.5)
				return true;

			self.think= M_FliesOn;
			self.nextthink= level.time + 5 + 10 * random();
			return true;
		}
	};

	static void AttackFinished(edict_t self, float time) {
		self.monsterinfo.attack_finished= level.time + time;
	}

	static void M_CatagorizePosition(edict_t ent) {
		float[] point= { 0, 0, 0 };
		int cont;

		//
		//	get waterlevel
		//
		point[0]= ent.s.origin[0];
		point[1]= ent.s.origin[1];
		point[2]= ent.s.origin[2] + ent.mins[2] + 1;
		cont= gi.pointcontents(point);

		if (0 == (cont & MASK_WATER)) {
			ent.waterlevel= 0;
			ent.watertype= 0;
			return;
		}

		ent.watertype= cont;
		ent.waterlevel= 1;
		point[2] += 26;
		cont= gi.pointcontents(point);
		if (0 == (cont & MASK_WATER))
			return;

		ent.waterlevel= 2;
		point[2] += 22;
		cont= gi.pointcontents(point);
		if (0 != (cont & MASK_WATER))
			ent.waterlevel= 3;
	}

	static void M_WorldEffects(edict_t ent) {
		int dmg;

		if (ent.health > 0) {
			if (0 == (ent.flags & FL_SWIM)) {
				if (ent.waterlevel < 3) {
					ent.air_finished= level.time + 12;
				} else if (ent.air_finished < level.time) {
					// drown!
					if (ent.pain_debounce_time < level.time) {
						dmg= (int) (2f + 2f * Math.floor(level.time - ent.air_finished));
						if (dmg > 15)
							dmg= 15;
						T_Damage(
							ent,
							g_edicts[0],
							g_edicts[0],
							vec3_origin,
							ent.s.origin,
							vec3_origin,
							dmg,
							0,
							DAMAGE_NO_ARMOR,
							MOD_WATER);
						ent.pain_debounce_time= level.time + 1;
					}
				}
			} else {
				if (ent.waterlevel > 0) {
					ent.air_finished= level.time + 9;
				} else if (ent.air_finished < level.time) {
					// suffocate!
					if (ent.pain_debounce_time < level.time) {
						dmg= (int) (2 + 2 * Math.floor(level.time - ent.air_finished));
						if (dmg > 15)
							dmg= 15;
						T_Damage(
							ent,
							g_edicts[0],
							g_edicts[0],
							vec3_origin,
							ent.s.origin,
							vec3_origin,
							dmg,
							0,
							DAMAGE_NO_ARMOR,
							MOD_WATER);
						ent.pain_debounce_time= level.time + 1;
					}
				}
			}
		}

		if (ent.waterlevel == 0) {
			if ((ent.flags & FL_INWATER) != 0) {
				gi.sound(ent, CHAN_BODY, gi.soundindex("player/watr_out.wav"), 1, ATTN_NORM, 0);
				ent.flags &= ~FL_INWATER;
			}
			return;
		}

		if ((ent.watertype & CONTENTS_LAVA) != 0 && 0 == (ent.flags & FL_IMMUNE_LAVA)) {
			if (ent.damage_debounce_time < level.time) {
				ent.damage_debounce_time= level.time + 0.2f;
				T_Damage(
					ent,
					g_edicts[0],
					g_edicts[0],
					vec3_origin,
					ent.s.origin,
					vec3_origin,
					10 * ent.waterlevel,
					0,
					0,
					MOD_LAVA);
			}
		}
		if ((ent.watertype & CONTENTS_SLIME) != 0 && 0 == (ent.flags & FL_IMMUNE_SLIME)) {
			if (ent.damage_debounce_time < level.time) {
				ent.damage_debounce_time= level.time + 1;
				T_Damage(
					ent,
					g_edicts[0],
					g_edicts[0],
					vec3_origin,
					ent.s.origin,
					vec3_origin,
					4 * ent.waterlevel,
					0,
					0,
					MOD_SLIME);
			}
		}

		if (0 == (ent.flags & FL_INWATER)) {
			if (0 == (ent.svflags & SVF_DEADMONSTER)) {
				if ((ent.watertype & CONTENTS_LAVA) != 0)
					if (random() <= 0.5)
						gi.sound(
							ent,
							CHAN_BODY,
							gi.soundindex("player/lava1.wav"),
							1,
							ATTN_NORM,
							0);
					else
						gi.sound(
							ent,
							CHAN_BODY,
							gi.soundindex("player/lava2.wav"),
							1,
							ATTN_NORM,
							0);
				else if ((ent.watertype & CONTENTS_SLIME) != 0)
					gi.sound(ent, CHAN_BODY, gi.soundindex("player/watr_in.wav"), 1, ATTN_NORM, 0);
				else if ((ent.watertype & CONTENTS_WATER) != 0)
					gi.sound(ent, CHAN_BODY, gi.soundindex("player/watr_in.wav"), 1, ATTN_NORM, 0);
			}

			ent.flags |= FL_INWATER;
			ent.damage_debounce_time= 0;
		}
	}

	static void M_droptofloor(edict_t ent) {
		float[] end= { 0, 0, 0 };
		trace_t trace;

		ent.s.origin[2] += 1;
		VectorCopy(ent.s.origin, end);
		end[2] -= 256;

		trace= gi.trace(ent.s.origin, ent.mins, ent.maxs, end, ent, MASK_MONSTERSOLID);

		if (trace.fraction == 1 || trace.allsolid)
			return;

		VectorCopy(trace.endpos, ent.s.origin);

		gi.linkentity(ent);
		M_CheckGround(ent);
		M_CatagorizePosition(ent);
	}

	static void M_SetEffects(edict_t ent) {
		ent.s.effects &= ~(EF_COLOR_SHELL | EF_POWERSCREEN);
		ent.s.renderfx &= ~(RF_SHELL_RED | RF_SHELL_GREEN | RF_SHELL_BLUE);

		if ((ent.monsterinfo.aiflags & AI_RESURRECTING) != 0) {
			ent.s.effects |= EF_COLOR_SHELL;
			ent.s.renderfx |= RF_SHELL_RED;
		}

		if (ent.health <= 0)
			return;

		if (ent.powerarmor_time > level.time) {
			if (ent.monsterinfo.power_armor_type == POWER_ARMOR_SCREEN) {
				ent.s.effects |= EF_POWERSCREEN;
			} else if (ent.monsterinfo.power_armor_type == POWER_ARMOR_SHIELD) {
				ent.s.effects |= EF_COLOR_SHELL;
				ent.s.renderfx |= RF_SHELL_GREEN;
			}
		}
	}

	static void M_MoveFrame(edict_t self) {
		mmove_t move;
		int index;

		move= self.monsterinfo.currentmove;
		self.nextthink= level.time + FRAMETIME;

		if ((self.monsterinfo.nextframe != 0)
			&& (self.monsterinfo.nextframe >= move.firstframe)
			&& (self.monsterinfo.nextframe <= move.lastframe)) {
			self.s.frame= self.monsterinfo.nextframe;
			self.monsterinfo.nextframe= 0;
		} else {
			if (self.s.frame == move.lastframe) {
				if (move.endfunc != null) {
					move.endfunc.think(self);

					// regrab move, endfunc is very likely to change it
					move= self.monsterinfo.currentmove;

					// check for death
					if ((self.svflags & SVF_DEADMONSTER) != 0)
						return;
				}
			}

			if (self.s.frame < move.firstframe || self.s.frame > move.lastframe) {
				self.monsterinfo.aiflags &= ~AI_HOLD_FRAME;
				self.s.frame= move.firstframe;
			} else {
				if (0 == (self.monsterinfo.aiflags & AI_HOLD_FRAME)) {
					self.s.frame++;
					if (self.s.frame > move.lastframe)
						self.s.frame= move.firstframe;
				}
			}
		}

		index= self.s.frame - move.firstframe;
		if (move.frame[index].ai != null)
			if (0 == (self.monsterinfo.aiflags & AI_HOLD_FRAME))
				move.frame[index].ai.ai(self, move.frame[index].dist * self.monsterinfo.scale);
			else
				move.frame[index].ai.ai(self, 0);

		if (move.frame[index].think != null)
			move.frame[index].think.think(self);
	}

	static EntThinkAdapter monster_think= new EntThinkAdapter() {
		public boolean think(edict_t self) {

			M_MoveFrame(self);
			if (self.linkcount != self.monsterinfo.linkcount) {
				self.monsterinfo.linkcount= self.linkcount;
				M_CheckGround(self);
			}
			M_CatagorizePosition(self);
			M_WorldEffects(self);
			M_SetEffects(self);
			return true;
		}
	};

	static EntThinkAdapter monster_triggered_spawn= new EntThinkAdapter() {
		public boolean think(edict_t self) {

			self.s.origin[2] += 1;
			KillBox(self);

			self.solid= SOLID_BBOX;
			self.movetype= MOVETYPE_STEP;
			self.svflags &= ~SVF_NOCLIENT;
			self.air_finished= level.time + 12;
			gi.linkentity(self);

			monster_start_go(self);

			if (self.enemy != null
				&& 0 == (self.spawnflags & 1)
				&& 0 == (self.enemy.flags & FL_NOTARGET)) {
				FoundTarget(self);
			} else {
				self.enemy= null;
			}
			return true;
		}
	};

	//	we have a one frame delay here so we don't telefrag the guy who activated us
	static EntUseAdapter monster_triggered_spawn_use= new EntUseAdapter() {

		public void use(edict_t self, edict_t other, edict_t activator) {
			self.think= monster_triggered_spawn;
			self.nextthink= level.time + FRAMETIME;
			if (activator.client != null)
				self.enemy= activator;
			self.use= monster_use;
		}
	};

	/*
	================
	monster_death_use
	
	When a monster dies, it fires all of its targets with the current
	enemy as activator.
	================
	*/
	static void monster_death_use(edict_t self) {
		self.flags &= ~(FL_FLY | FL_SWIM);
		self.monsterinfo.aiflags &= AI_GOOD_GUY;

		if (self.item != null) {
			Drop_Item(self, self.item);
			self.item= null;
		}

		if (self.deathtarget != null)
			self.target= self.deathtarget;

		if (self.target == null)
			return;

		G_UseTargets(self, self.enemy);
	}

	// ============================================================================
	static boolean monster_start(edict_t self) {
		if (deathmatch.value != 0) {
			G_FreeEdict(self);
			return false;
		}

		if ((self.spawnflags & 4) != 0 && 0 == (self.monsterinfo.aiflags & AI_GOOD_GUY)) {
			self.spawnflags &= ~4;
			self.spawnflags |= 1;
			//		 gi.dprintf("fixed spawnflags on %s at %s\n", self.classname, vtos(self.s.origin));
		}

		if (0 == (self.monsterinfo.aiflags & AI_GOOD_GUY))
			level.total_monsters++;

		self.nextthink= level.time + FRAMETIME;
		self.svflags |= SVF_MONSTER;
		self.s.renderfx |= RF_FRAMELERP;
		self.takedamage= DAMAGE_AIM;
		self.air_finished= level.time + 12;
		self.use= monster_use;
		self.max_health= self.health;
		self.clipmask= MASK_MONSTERSOLID;

		self.s.skinnum= 0;
		self.deadflag= DEAD_NO;
		self.svflags &= ~SVF_DEADMONSTER;

		if (null == self.monsterinfo.checkattack)
			self.monsterinfo.checkattack= M_CheckAttack;
		VectorCopy(self.s.origin, self.s.old_origin);

		if (st.item != null) {
			self.item= FindItemByClassname(st.item);
			if (self.item != null)
				gi.dprintf(
					self.classname
						+ " at "
						+ vtos(self.s.origin)
						+ " has bad item: "
						+ st.item
						+ "\n");
		}

		// randomize what frame they start on
		if (self.monsterinfo.currentmove != null)
			self.s.frame=
				self.monsterinfo.currentmove.firstframe
					+ (rand()
						% (self.monsterinfo.currentmove.lastframe
							- self.monsterinfo.currentmove.firstframe
							+ 1));

		return true;
	}

	static void monster_start_go(edict_t self) {
		float[] v= { 0, 0, 0 };

		if (self.health <= 0)
			return;

		// check for target to combat_point and change to combattarget
		if (self.target != null) {
			boolean notcombat;
			boolean fixup;
			edict_t target= null;
			notcombat= false;
			fixup= false;

			EdictIterator edit= null;

			while ((edit= G_Find(edit, findByTarget, self.target)) != null) {
				target= edit.o;
				if (strcmp(target.classname, "point_combat") == 0) {
					self.combattarget= self.target;
					fixup= true;
				} else {
					notcombat= true;
				}
			}
			if (notcombat && self.combattarget != null)
				gi.dprintf(
					self.classname
						+ " at "
						+ vtos(self.s.origin)
						+ " has target with mixed types\n");
			if (fixup)
				self.target= null;
		}

		// validate combattarget
		if (self.combattarget != null) {
			edict_t target= null;

			EdictIterator edit= null;
			while ((edit= G_Find(edit, findByTarget, self.combattarget)) != null) {
				target= edit.o;

				if (strcmp(target.classname, "point_combat") != 0) {
					gi.dprintf(
						self.classname
							+ " at "
							+ vtos(self.s.origin)
							+ " has bad combattarget "
							+ self.combattarget
							+ " : "
							+ target.classname
							+ " at "
							+ vtos(target.s.origin));
				}
			}
		}

		if (self.target != null) {
			self.goalentity= self.movetarget= G_PickTarget(self.target);
			if (null == self.movetarget) {
				gi.dprintf(
					self.classname
						+ " can't find target "
						+ self.target
						+ " at "
						+ vtos(self.s.origin)
						+ "\n");
				self.target= null;
				self.monsterinfo.pausetime= 100000000;
				self.monsterinfo.stand.think(self);
			} else if (strcmp(self.movetarget.classname, "path_corner") == 0) {
				VectorSubtract(self.goalentity.s.origin, self.s.origin, v);
				self.ideal_yaw= self.s.angles[YAW]= vectoyaw(v);
				self.monsterinfo.walk.think(self);
				self.target= null;
			} else {
				self.goalentity= self.movetarget= null;
				self.monsterinfo.pausetime= 100000000;
				self.monsterinfo.stand.think(self);
			}
		} else {
			self.monsterinfo.pausetime= 100000000;
			self.monsterinfo.stand.think(self);
		}

		self.think= monster_think;
		self.nextthink= level.time + FRAMETIME;
	}

	static EntThinkAdapter monster_triggered_start= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.solid= SOLID_NOT;
			self.movetype= MOVETYPE_NONE;
			self.svflags |= SVF_NOCLIENT;
			self.nextthink= 0;
			self.use= monster_triggered_spawn_use;
			return true;
		}
	};

	static EntThinkAdapter walkmonster_start_go= new EntThinkAdapter() {
		public boolean think(edict_t self) {

			if (0 == (self.spawnflags & 2) && level.time < 1) {
				M_droptofloor(self);

				if (self.groundentity != null)
					if (!M_walkmove(self, 0, 0))
						gi.dprintf(self.classname + " in solid at " + vtos(self.s.origin) + "\n");
			}

			if (0 == self.yaw_speed)
				self.yaw_speed= 20;
			self.viewheight= 25;

			monster_start_go(self);

			if ((self.spawnflags & 2) != 0)
				monster_triggered_start.think(self);
			return true;
		}
	};

	static EntThinkAdapter walkmonster_start= new EntThinkAdapter() {
		public boolean think(edict_t self) {

			self.think= walkmonster_start_go;
			monster_start(self);
			return true;
		}
	};

	static EntThinkAdapter flymonster_start_go= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (!M_walkmove(self, 0, 0))
				gi.dprintf(self.classname + " in solid at " + vtos(self.s.origin) + "\n");

			if (0 == self.yaw_speed)
				self.yaw_speed= 10;
			self.viewheight= 25;

			monster_start_go(self);

			if ((self.spawnflags & 2) != 0)
				monster_triggered_start.think(self);
			return true;
		}
	};

	static EntThinkAdapter flymonster_start= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.flags |= FL_FLY;
			self.think= flymonster_start_go;
			monster_start(self);
			return true;
		}
	};

	static EntThinkAdapter swimmonster_start_go= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (0 == self.yaw_speed)
				self.yaw_speed= 10;
			self.viewheight= 10;

			monster_start_go(self);

			if ((self.spawnflags & 2) != 0)
				monster_triggered_start.think(self);
			return true;
		}
	};

	static EntThinkAdapter swimmonster_start= new EntThinkAdapter() {
		public boolean think(edict_t self) {

			{
				self.flags |= FL_SWIM;
				self.think= swimmonster_start_go;
				monster_start(self);
				return true;
			}
		}
	};

	/*
	=============
	ai_turn
	
	don't move, but turn towards ideal_yaw
	Distance is for slight position adjustments needed by the animations
	=============
	*/
	static AIAdapter ai_turn= new AIAdapter() {
		public void ai(edict_t self, float dist) {
			if (dist != 0)
				M_walkmove(self, self.s.angles[YAW], dist);

			if (FindTarget(self))
				return;

			M_ChangeYaw(self);
		}
	};

	/*
	=============
	ai_move
	
	Move the specified distance at current facing.
	This replaces the QC functions: ai_forward, ai_back, ai_pain, and ai_painforward
	==============
	*/
	static AIAdapter ai_move= new AIAdapter() {
		public void ai(edict_t self, float dist) {
			M_walkmove(self, self.s.angles[YAW], dist);
		}
	};

	/*
	=============
	ai_walk
	
	The monster is walking it's beat
	=============
	*/
	static AIAdapter ai_walk= new AIAdapter() {
		public void ai(edict_t self, float dist) {
			M_MoveToGoal(self, dist);

			// check for noticing a player
			if (FindTarget(self))
				return;

			if ((self.monsterinfo.search != null) && (level.time > self.monsterinfo.idle_time)) {
				if (self.monsterinfo.idle_time != 0) {
					self.monsterinfo.search.think(self);
					self.monsterinfo.idle_time= level.time + 15 + random() * 15;
				} else {
					self.monsterinfo.idle_time= level.time + random() * 15;
				}
			}
		}
	};

	/*
	=============
	ai_stand
	
	Used for standing around and looking for players
	Distance is for slight position adjustments needed by the animations
	==============
	*/

	static AIAdapter ai_stand= new AIAdapter() {
		public void ai(edict_t self, float dist) {
			float[] v= { 0, 0, 0 };

			if (dist != 0)
				M_walkmove(self, self.s.angles[YAW], dist);

			if ((self.monsterinfo.aiflags & AI_STAND_GROUND) != 0) {
				if (self.enemy != null) {
					VectorSubtract(self.enemy.s.origin, self.s.origin, v);
					self.ideal_yaw= vectoyaw(v);
					if (self.s.angles[YAW] != self.ideal_yaw
						&& 0 != (self.monsterinfo.aiflags & AI_TEMP_STAND_GROUND)) {
						self.monsterinfo.aiflags &= ~(AI_STAND_GROUND | AI_TEMP_STAND_GROUND);
						self.monsterinfo.run.think(self);
					}
					M_ChangeYaw(self);
					ai_checkattack(self, 0);
				} else
					FindTarget(self);
				return;
			}

			if (FindTarget(self))
				return;

			if (level.time > self.monsterinfo.pausetime) {
				self.monsterinfo.walk.think(self);
				return;
			}

			if (0 == (self.spawnflags & 1)
				&& (self.monsterinfo.idle != null)
				&& (level.time > self.monsterinfo.idle_time)) {
				if (self.monsterinfo.idle_time != 0) {
					self.monsterinfo.idle.think(self);
					self.monsterinfo.idle_time= level.time + 15 + random() * 15;
				} else {
					self.monsterinfo.idle_time= level.time + random() * 15;
				}
			}
		}
	};

	/*
	=============
	ai_charge
	
	Turns towards target and advances
	Use this call with a distnace of 0 to replace ai_face
	==============
	*/
	static AIAdapter ai_charge= new AIAdapter() {

		public void ai(edict_t self, float dist) {
			float[] v= { 0, 0, 0 };

			VectorSubtract(self.enemy.s.origin, self.s.origin, v);
			self.ideal_yaw= vectoyaw(v);
			M_ChangeYaw(self);

			if (dist != 0)
				M_walkmove(self, self.s.angles[YAW], dist);
		}
	};

	/*
	=============
	ai_turn
	
	don't move, but turn towards ideal_yaw
	Distance is for slight position adjustments needed by the animations
	=============
	*/
	static void ai_turn(edict_t self, float dist) {
		if (dist != 0)
			M_walkmove(self, self.s.angles[YAW], dist);

		if (FindTarget(self))
			return;

		M_ChangeYaw(self);
	}

	/*
	
	.enemy
	Will be world if not currently angry at anyone.
	
	.movetarget
	The next path spot to walk toward.  If .enemy, ignore .movetarget.
	When an enemy is killed, the monster will try to return to it's path.
	
	.hunt_time
	Set to time + something when the player is in sight, but movement straight for
	him is blocked.  This causes the monster to use wall following code for
	movement direction instead of sighting on the player.
	
	.ideal_yaw
	A yaw angle of the intended direction, which will be turned towards at up
	to 45 deg / state.  If the enemy is in view and hunt_time is not active,
	this will be the exact line towards the enemy.
	
	.pausetime
	A monster will leave it's stand state and head towards it's .movetarget when
	time > .pausetime.
	
	walkmove(angle, speed) primitive is all or nothing
	*/

	/*
	============
	FacingIdeal
	
	============
	*/

	static boolean FacingIdeal(edict_t self) {
		float delta;

		delta= anglemod(self.s.angles[YAW] - self.ideal_yaw);
		if (delta > 45 && delta < 315)
			return false;
		return true;
	}

	/*
	=============
	ai_run_melee
	
	Turn and close until within an angle to launch a melee attack
	=============
	*/
	static void ai_run_melee(edict_t self) {
		self.ideal_yaw= enemy_yaw;
		M_ChangeYaw(self);

		if (FacingIdeal(self)) {
			self.monsterinfo.melee.think(self);
			self.monsterinfo.attack_state= AS_STRAIGHT;
		}
	}

	/*
	=============
	ai_run_missile
	
	Turn in place until within an angle to launch a missile attack
	=============
	*/
	static void ai_run_missile(edict_t self) {
		self.ideal_yaw= enemy_yaw;
		M_ChangeYaw(self);

		if (FacingIdeal(self)) {
			self.monsterinfo.attack.think(self);
			self.monsterinfo.attack_state= AS_STRAIGHT;
		}
	};

	/*
	=============
	ai_run_slide
	
	Strafe sideways, but stay at aproximately the same range
	=============
	*/
	static void ai_run_slide(edict_t self, float distance) {
		float ofs;

		self.ideal_yaw= enemy_yaw;
		M_ChangeYaw(self);

		if (self.monsterinfo.lefty != 0)
			ofs= 90;
		else
			ofs= -90;

		if (M_walkmove(self, self.ideal_yaw + ofs, distance))
			return;

		self.monsterinfo.lefty= 1 - self.monsterinfo.lefty;
		M_walkmove(self, self.ideal_yaw - ofs, distance);
	}

	/*
	=============
	ai_checkattack
	
	Decides if we're going to attack or do something else
	used by ai_run and ai_stand
	=============
	*/
	static boolean ai_checkattack(edict_t self, float dist) {
		float temp[]= { 0, 0, 0 };

		boolean hesDeadJim;

		//	   this causes monsters to run blindly to the combat point w/o firing
		if (self.goalentity != null) {
			if ((self.monsterinfo.aiflags & AI_COMBAT_POINT) != 0)
				return false;

			if ((self.monsterinfo.aiflags & AI_SOUND_TARGET) != 0) {
				if ((level.time - self.enemy.teleport_time) > 5.0) {
					if (self.goalentity == self.enemy)
						if (self.movetarget != null)
							self.goalentity= self.movetarget;
						else
							self.goalentity= null;
					self.monsterinfo.aiflags &= ~AI_SOUND_TARGET;
					if ((self.monsterinfo.aiflags & AI_TEMP_STAND_GROUND) != 0)
						self.monsterinfo.aiflags &= ~(AI_STAND_GROUND | AI_TEMP_STAND_GROUND);
				} else {
					self.show_hostile= (int) level.time + 1;
					return false;
				}
			}
		}

		enemy_vis= false;

		//	   see if the enemy is dead
		hesDeadJim= false;
		if ((null == self.enemy) || (self.enemy.inuse)) {
			hesDeadJim= true;
		} else if ((self.monsterinfo.aiflags & AI_MEDIC) != 0) {
			if (self.enemy.health > 0) {
				hesDeadJim= true;
				self.monsterinfo.aiflags &= ~AI_MEDIC;
			}
		} else {
			if ((self.monsterinfo.aiflags & AI_BRUTAL) != 0) {
				if (self.enemy.health <= -80)
					hesDeadJim= true;
			} else {
				if (self.enemy.health <= 0)
					hesDeadJim= true;
			}
		}

		if (hesDeadJim) {
			self.enemy= null;
			// FIXME: look all around for other targets
			if (self.oldenemy != null && self.oldenemy.health > 0) {
				self.enemy= self.oldenemy;
				self.oldenemy= null;
				HuntTarget(self);
			} else {
				if (self.movetarget != null) {
					self.goalentity= self.movetarget;
					self.monsterinfo.walk.think(self);
				} else {
					// we need the pausetime otherwise the stand code
					// will just revert to walking with no target and
					// the monsters will wonder around aimlessly trying
					// to hunt the world entity
					self.monsterinfo.pausetime= level.time + 100000000;
					self.monsterinfo.stand.think(self);
				}
				return true;
			}
		}

		self.show_hostile= (int) level.time + 1; // wake up other monsters

		//	   check knowledge of enemy
		enemy_vis= visible(self, self.enemy);
		if (enemy_vis) {
			self.monsterinfo.search_time= level.time + 5;
			VectorCopy(self.enemy.s.origin, self.monsterinfo.last_sighting);
		}

		//	   look for other coop players here
		//		if (coop && self.monsterinfo.search_time < level.time)
		//		{
		//			if (FindTarget (self))
		//				return true;
		//		}

		enemy_infront= infront(self, self.enemy);
		enemy_range= range(self, self.enemy);
		VectorSubtract(self.enemy.s.origin, self.s.origin, temp);
		enemy_yaw= vectoyaw(temp);

		// JDC self.ideal_yaw = enemy_yaw;

		if (self.monsterinfo.attack_state == AS_MISSILE) {
			ai_run_missile(self);
			return true;
		}
		if (self.monsterinfo.attack_state == AS_MELEE) {
			ai_run_melee(self);
			return true;
		}

		// if enemy is not currently visible, we will never attack
		if (!enemy_vis)
			return false;

		return self.monsterinfo.checkattack.think(self);
	}


	/*
	=============
	ai_run
	
	The monster has an enemy it is trying to kill
	=============
	*/
	static AIAdapter ai_run= new AIAdapter() {
		public void ai_run(edict_t self, float dist) {
			float[] v= { 0, 0, 0 };

			edict_t tempgoal;
			edict_t save;
			boolean new1;
			edict_t marker;
			float d1, d2;
			trace_t tr;
			float[] v_forward= { 0, 0, 0 }, v_right= { 0, 0, 0 };
			float left, center, right;
			float[] left_target= { 0, 0, 0 }, right_target= { 0, 0, 0 };

			// if we're going to a combat point, just proceed
			if ((self.monsterinfo.aiflags & AI_COMBAT_POINT) != 0) {
				M_MoveToGoal(self, dist);
				return;
			}

			if ((self.monsterinfo.aiflags & AI_SOUND_TARGET) != 0) {
				VectorSubtract(self.s.origin, self.enemy.s.origin, v);
				if (VectorLength(v) < 64) {
					self.monsterinfo.aiflags |= (AI_STAND_GROUND | AI_TEMP_STAND_GROUND);
					self.monsterinfo.stand.think(self);
					return;
				}

				M_MoveToGoal(self, dist);

				if (!FindTarget(self))
					return;
			}

			if (ai_checkattack(self, dist))
				return;

			if (self.monsterinfo.attack_state == AS_SLIDING) {
				ai_run_slide(self, dist);
				return;
			}

			if (enemy_vis) {
				//			if (self.aiflags & AI_LOST_SIGHT)
				//				dprint("regained sight\n");
				M_MoveToGoal(self, dist);
				self.monsterinfo.aiflags &= ~AI_LOST_SIGHT;
				VectorCopy(self.enemy.s.origin, self.monsterinfo.last_sighting);
				self.monsterinfo.trail_time= level.time;
				return;
			}

			// coop will change to another enemy if visible
			if (coop.value != 0) { // FIXME: insane guys get mad with this, which causes crashes!
				if (FindTarget(self))
					return;
			}

			if ((self.monsterinfo.search_time != 0)
				&& (level.time > (self.monsterinfo.search_time + 20))) {
				M_MoveToGoal(self, dist);
				self.monsterinfo.search_time= 0;
				//			dprint("search timeout\n");
				return;
			}

			save= self.goalentity;
			tempgoal= G_Spawn();
			self.goalentity= tempgoal;

			new1= false;

			if (0 == (self.monsterinfo.aiflags & AI_LOST_SIGHT)) {
				// just lost sight of the player, decide where to go first
				//			dprint("lost sight of player, last seen at "); dprint(vtos(self.last_sighting)); dprint("\n");
				self.monsterinfo.aiflags |= (AI_LOST_SIGHT | AI_PURSUIT_LAST_SEEN);
				self.monsterinfo.aiflags &= ~(AI_PURSUE_NEXT | AI_PURSUE_TEMP);
				new1= true;
			}

			if ((self.monsterinfo.aiflags & AI_PURSUE_NEXT) != 0) {
				self.monsterinfo.aiflags &= ~AI_PURSUE_NEXT;
				//			dprint("reached current goal: "); dprint(vtos(self.origin)); dprint(" "); dprint(vtos(self.last_sighting)); dprint(" "); dprint(ftos(vlen(self.origin - self.last_sighting))); dprint("\n");

				// give ourself more time since we got this far
				self.monsterinfo.search_time= level.time + 5;

				if ((self.monsterinfo.aiflags & AI_PURSUE_TEMP) != 0) {
					//				dprint("was temp goal; retrying original\n");
					self.monsterinfo.aiflags &= ~AI_PURSUE_TEMP;
					marker= null;
					VectorCopy(self.monsterinfo.saved_goal, self.monsterinfo.last_sighting);
					new1= true;
				} else if ((self.monsterinfo.aiflags & AI_PURSUIT_LAST_SEEN) != 0) {
					self.monsterinfo.aiflags &= ~AI_PURSUIT_LAST_SEEN;
					marker= PlayerTrail.PickFirst(self);
				} else {
					marker= PlayerTrail.PickNext(self);
				}

				if (marker != null) {
					VectorCopy(marker.s.origin, self.monsterinfo.last_sighting);
					self.monsterinfo.trail_time= marker.timestamp;
					self.s.angles[YAW]= self.ideal_yaw= marker.s.angles[YAW];
					//				dprint("heading is "); dprint(ftos(self.ideal_yaw)); dprint("\n");

					//				debug_drawline(self.origin, self.last_sighting, 52);
					new1= true;
				}
			}

			VectorSubtract(self.s.origin, self.monsterinfo.last_sighting, v);
			d1= VectorLength(v);
			if (d1 <= dist) {
				self.monsterinfo.aiflags |= AI_PURSUE_NEXT;
				dist= d1;
			}

			VectorCopy(self.monsterinfo.last_sighting, self.goalentity.s.origin);

			if (new1) {
				//			gi.dprintf("checking for course correction\n");

				tr=
					gi.trace(
						self.s.origin,
						self.mins,
						self.maxs,
						self.monsterinfo.last_sighting,
						self,
						MASK_PLAYERSOLID);
				if (tr.fraction < 1) {
					VectorSubtract(self.goalentity.s.origin, self.s.origin, v);
					d1= VectorLength(v);
					center= tr.fraction;
					d2= d1 * ((center + 1) / 2);
					self.s.angles[YAW]= self.ideal_yaw= vectoyaw(v);
					AngleVectors(self.s.angles, v_forward, v_right, null);

					VectorSet(v, d2, -16, 0);
					G_ProjectSource(self.s.origin, v, v_forward, v_right, left_target);
					tr=
						gi.trace(
							self.s.origin,
							self.mins,
							self.maxs,
							left_target,
							self,
							MASK_PLAYERSOLID);
					left= tr.fraction;

					VectorSet(v, d2, 16, 0);
					G_ProjectSource(self.s.origin, v, v_forward, v_right, right_target);
					tr=
						gi.trace(
							self.s.origin,
							self.mins,
							self.maxs,
							right_target,
							self,
							MASK_PLAYERSOLID);
					right= tr.fraction;

					center= (d1 * center) / d2;
					if (left >= center && left > right) {
						if (left < 1) {
							VectorSet(v, d2 * left * 0.5f, -16f, 0f);
							G_ProjectSource(self.s.origin, v, v_forward, v_right, left_target);
							//						gi.dprintf("incomplete path, go part way and adjust again\n");
						}
						VectorCopy(self.monsterinfo.last_sighting, self.monsterinfo.saved_goal);
						self.monsterinfo.aiflags |= AI_PURSUE_TEMP;
						VectorCopy(left_target, self.goalentity.s.origin);
						VectorCopy(left_target, self.monsterinfo.last_sighting);
						VectorSubtract(self.goalentity.s.origin, self.s.origin, v);
						self.s.angles[YAW]= self.ideal_yaw= vectoyaw(v);
						//					gi.dprintf("adjusted left\n");
						//					debug_drawline(self.origin, self.last_sighting, 152);
					} else if (right >= center && right > left) {
						if (right < 1) {
							VectorSet(v, d2 * right * 0.5f, 16f, 0f);
							G_ProjectSource(self.s.origin, v, v_forward, v_right, right_target);
							//						gi.dprintf("incomplete path, go part way and adjust again\n");
						}
						VectorCopy(self.monsterinfo.last_sighting, self.monsterinfo.saved_goal);
						self.monsterinfo.aiflags |= AI_PURSUE_TEMP;
						VectorCopy(right_target, self.goalentity.s.origin);
						VectorCopy(right_target, self.monsterinfo.last_sighting);
						VectorSubtract(self.goalentity.s.origin, self.s.origin, v);
						self.s.angles[YAW]= self.ideal_yaw= vectoyaw(v);
						//					gi.dprintf("adjusted right\n");
						//					debug_drawline(self.origin, self.last_sighting, 152);
					}
				}
				//			else gi.dprintf("course was fine\n");
			}

			M_MoveToGoal(self, dist);

			G_FreeEdict(tempgoal);

			if (self != null)
				self.goalentity= save;
		}
	};

	static void UpdateChaseCam(edict_t ent) {
		float[] o= { 0, 0, 0 }, ownerv= { 0, 0, 0 }, goal= { 0, 0, 0 };
		edict_t targ;
		float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 };
		trace_t trace;
		int i;
		float[] oldgoal= { 0, 0, 0 };
		float[] angles= { 0, 0, 0 };

		// is our chase target gone?
		if (!ent.client.chase_target.inuse || ent.client.chase_target.client.resp.spectator) {
			edict_t old= ent.client.chase_target;
			ChaseNext(ent);
			if (ent.client.chase_target == old) {
				ent.client.chase_target= null;
				ent.client.ps.pmove.pm_flags &= ~PMF_NO_PREDICTION;
				return;
			}
		}

		targ= ent.client.chase_target;

		VectorCopy(targ.s.origin, ownerv);
		VectorCopy(ent.s.origin, oldgoal);

		ownerv[2] += targ.viewheight;

		VectorCopy(targ.client.v_angle, angles);
		if (angles[PITCH] > 56)
			angles[PITCH]= 56;
		AngleVectors(angles, forward, right, null);
		VectorNormalize(forward);
		VectorMA(ownerv, -30, forward, o);

		if (o[2] < targ.s.origin[2] + 20)
			o[2]= targ.s.origin[2] + 20;

		// jump animation lifts
		if (targ.groundentity == null)
			o[2] += 16;

		trace= gi.trace(ownerv, vec3_origin, vec3_origin, o, targ, MASK_SOLID);

		VectorCopy(trace.endpos, goal);

		VectorMA(goal, 2, forward, goal);

		// pad for floors and ceilings
		VectorCopy(goal, o);
		o[2] += 6;
		trace= gi.trace(goal, vec3_origin, vec3_origin, o, targ, MASK_SOLID);
		if (trace.fraction < 1) {
			VectorCopy(trace.endpos, goal);
			goal[2] -= 6;
		}

		VectorCopy(goal, o);
		o[2] -= 6;
		trace= gi.trace(goal, vec3_origin, vec3_origin, o, targ, MASK_SOLID);
		if (trace.fraction < 1) {
			VectorCopy(trace.endpos, goal);
			goal[2] += 6;
		}

		if (targ.deadflag != 0)
			ent.client.ps.pmove.pm_type= PM_DEAD;
		else
			ent.client.ps.pmove.pm_type= PM_FREEZE;

		VectorCopy(goal, ent.s.origin);
		for (i= 0; i < 3; i++)
			ent.client.ps.pmove.delta_angles[i]=
				(short) ANGLE2SHORT(targ.client.v_angle[i] - ent.client.resp.cmd_angles[i]);

		if (targ.deadflag != 0) {
			ent.client.ps.viewangles[ROLL]= 40;
			ent.client.ps.viewangles[PITCH]= -15;
			ent.client.ps.viewangles[YAW]= targ.client.killer_yaw;
		} else {
			VectorCopy(targ.client.v_angle, ent.client.ps.viewangles);
			VectorCopy(targ.client.v_angle, ent.client.v_angle);
		}

		ent.viewheight= 0;
		ent.client.ps.pmove.pm_flags |= PMF_NO_PREDICTION;
		gi.linkentity(ent);
	}

	static void ChaseNext(edict_t ent) {
		int i;
		edict_t e;

		if (null == ent.client.chase_target)
			return;

		i= ent.client.chase_target.s.number;
		do {
			i++;
			if (i > maxclients.value)
				i= 1;
			e= g_edicts[i];

			if (!e.inuse)
				continue;
			if (!e.client.resp.spectator)
				break;
		} while (e != ent.client.chase_target);

		ent.client.chase_target= e;
		ent.client.update_chase= true;
	}

	static void ChasePrev(edict_t ent) {
		int i;
		edict_t e;

		if (ent.client.chase_target == null)
			return;

		i= ent.client.chase_target.s.number;
		do {
			i--;
			if (i < 1)
				i= (int) maxclients.value;
			e= g_edicts[i];
			if (!e.inuse)
				continue;
			if (!e.client.resp.spectator)
				break;
		} while (e != ent.client.chase_target);

		ent.client.chase_target= e;
		ent.client.update_chase= true;
	}

	static void GetChaseTarget(edict_t ent) {
		int i;
		edict_t other;

		for (i= 1; i <= maxclients.value; i++) {
			other= g_edicts[i];
			if (other.inuse && !other.client.resp.spectator) {
				ent.client.chase_target= other;
				ent.client.update_chase= true;
				UpdateChaseCam(ent);
				return;
			}
		}
		gi.centerprintf(ent, "No other players to chase.");
	}

	static void SelectNextItem(edict_t ent, int itflags) {
		gclient_t cl;
		int i, index;
		gitem_t it;

		cl= ent.client;

		if (cl.chase_target != null) {
			ChaseNext(ent);
			return;
		}

		// scan  for the next valid one
		for (i= 1; i <= MAX_ITEMS; i++) {
			index= (cl.pers.selected_item + i) % MAX_ITEMS;
			if (0 == cl.pers.inventory[index])
				continue;
			it= itemlist[index];
			if (it.use == null)
				continue;
			if (0 == (it.flags & itflags))
				continue;

			cl.pers.selected_item= index;
			return;
		}

		cl.pers.selected_item= -1;
	}

	static void SelectPrevItem(edict_t ent, int itflags) {
		gclient_t cl;
		int i, index;
		gitem_t it;

		cl= ent.client;

		if (cl.chase_target != null) {
			ChasePrev(ent);
			return;
		}

		// scan  for the next valid one
		for (i= 1; i <= MAX_ITEMS; i++) {
			index= (cl.pers.selected_item + MAX_ITEMS - i) % MAX_ITEMS;
			if (0 == cl.pers.inventory[index])
				continue;
			it= itemlist[index];
			if (null == it.use)
				continue;
			if (0 == (it.flags & itflags))
				continue;

			cl.pers.selected_item= index;
			return;
		}

		cl.pers.selected_item= -1;
	}

	static void ValidateSelectedItem(edict_t ent) {
		gclient_t cl;

		cl= ent.client;

		if (cl.pers.inventory[cl.pers.selected_item] != 0)
			return; // valid

		SelectNextItem(ent, -1);
	}

	//	  =================================================================================

	static int atoi(String in) {
		try {
			return Integer.parseInt(in);
		} catch (Exception e) {
			return 0;
		}
	}

	//======================================================================

	static boolean Add_Ammo(edict_t ent, gitem_t item, int count) {
		int index;
		int max;

		if (null == ent.client)
			return false;

		if (item.tag == AMMO_BULLETS)
			max= ent.client.pers.max_bullets;
		else if (item.tag == AMMO_SHELLS)
			max= ent.client.pers.max_shells;
		else if (item.tag == AMMO_ROCKETS)
			max= ent.client.pers.max_rockets;
		else if (item.tag == AMMO_GRENADES)
			max= ent.client.pers.max_grenades;
		else if (item.tag == AMMO_CELLS)
			max= ent.client.pers.max_cells;
		else if (item.tag == AMMO_SLUGS)
			max= ent.client.pers.max_slugs;
		else
			return false;

		index= ITEM_INDEX(item);

		if (ent.client.pers.inventory[index] == max)
			return false;

		ent.client.pers.inventory[index] += count;

		if (ent.client.pers.inventory[index] > max)
			ent.client.pers.inventory[index]= max;

		return true;
	}

	/*
	===============
	PrecacheItem
	
	Precaches all data needed for a given item.
	This will be called for each item spawned in a level,
	and for each item in each client's inventory.
	===============
	*/
	static void PrecacheItem(gitem_t it) {
		String s;
		String data;
		int len;
		gitem_t ammo;

		if (it == null)
			return;

		if (it.pickup_sound != null)
			gi.soundindex(it.pickup_sound);

		if (it.world_model != null)
			gi.modelindex(it.world_model);

		if (it.view_model != null)
			gi.modelindex(it.view_model);

		if (it.icon != null)
			gi.imageindex(it.icon);

		// parse everything for its ammo
		if (it.ammo != null && it.ammo.length() != 0) {
			ammo= FindItem(it.ammo);
			if (ammo != it)
				PrecacheItem(ammo);
		}

		// parse the space seperated precache string for other items
		s= it.precaches;
		if (s == null || s.length() != 0)
			return;

		StringTokenizer tk= new StringTokenizer(s);

		// TODO: check it, it changed a lot.
		while (tk.hasMoreTokens()) {
			data= tk.nextToken();

			len= data.length();

			if (len >= MAX_QPATH || len < 5)
				gi.error("PrecacheItem: it.classname has bad precache string: " + s);

			// determine type based on extension
			if (data.endsWith("md2"))
				gi.modelindex(data);
			else if (data.endsWith("sp2"))
				gi.modelindex(data);
			else if (data.endsWith("wav"))
				gi.soundindex(data);
			if (data.endsWith("pcx"))
				gi.imageindex(data);
		}
	}

	static EntInteractAdapter Pickup_Ammo= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			int oldcount;
			int count;
			boolean weapon;

			weapon= (ent.item.flags & IT_WEAPON) != 0;
			if ((weapon) && ((int) dmflags.value & DF_INFINITE_AMMO) != 0)
				count= 1000;
			else if (ent.count != 0)
				count= ent.count;
			else
				count= ent.item.quantity;

			oldcount= other.client.pers.inventory[ITEM_INDEX(ent.item)];

			if (!Add_Ammo(other, ent.item, count))
				return false;

			if (weapon && 0 == oldcount) {
				if (other.client.pers.weapon != ent.item
					&& (0 == deathmatch.value || other.client.pers.weapon == FindItem("blaster")))
					other.client.newweapon= ent.item;
			}

			if (0 == (ent.spawnflags & (DROPPED_ITEM | DROPPED_PLAYER_ITEM))
				&& (deathmatch.value != 0))
				SetRespawn(ent, 30);
			return true;
		}
	};

	/*
	static gitem_armor_t jacketarmor_info = new gitem_armor_t(25, 50, .30f, .00f, ARMOR_JACKET);
	static gitem_armor_t combatarmor_info = new gitem_armor_t(50, 100, .60f, .30f, ARMOR_COMBAT);
	static gitem_armor_t bodyarmor_info = new gitem_armor_t(100, 200, .80f, .60f, ARMOR_BODY);
	*/
	static EntInteractAdapter Pickup_Armor= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			int old_armor_index;
			gitem_armor_t oldinfo;
			gitem_armor_t newinfo;
			int newcount;
			float salvage;
			int salvagecount;

			// get info on new armor
			newinfo= (gitem_armor_t) ent.item.info;

			old_armor_index= ArmorIndex(other);

			// handle armor shards specially
			if (ent.item.tag == ARMOR_SHARD) {
				if (0 == old_armor_index)
					other.client.pers.inventory[jacket_armor_index]= 2;
				else
					other.client.pers.inventory[old_armor_index] += 2;
			}

			// if player has no armor, just use it
			else if (0 == old_armor_index) {
				other.client.pers.inventory[ITEM_INDEX(ent.item)]= newinfo.base_count;
			}

			// use the better armor
			else {
				// get info on old armor
				if (old_armor_index == jacket_armor_index)
					oldinfo= jacketarmor_info;

				else if (old_armor_index == combat_armor_index)
					oldinfo= combatarmor_info;

				else // (old_armor_index == body_armor_index)
					oldinfo= bodyarmor_info;

				if (newinfo.normal_protection > oldinfo.normal_protection) {
					// calc new armor values
					salvage= oldinfo.normal_protection / newinfo.normal_protection;
					salvagecount= (int) salvage * other.client.pers.inventory[old_armor_index];
					newcount= newinfo.base_count + salvagecount;
					if (newcount > newinfo.max_count)
						newcount= newinfo.max_count;

					// zero count of old armor so it goes away
					other.client.pers.inventory[old_armor_index]= 0;

					// change armor to new item with computed value
					other.client.pers.inventory[ITEM_INDEX(ent.item)]= newcount;
				} else {
					// calc new armor values
					salvage= newinfo.normal_protection / oldinfo.normal_protection;
					salvagecount= (int) salvage * newinfo.base_count;
					newcount= other.client.pers.inventory[old_armor_index] + salvagecount;
					if (newcount > oldinfo.max_count)
						newcount= oldinfo.max_count;

					// if we're already maxed out then we don't need the new armor
					if (other.client.pers.inventory[old_armor_index] >= newcount)
						return false;

					// update current armor value
					other.client.pers.inventory[old_armor_index]= newcount;
				}
			}

			if (0 == (ent.spawnflags & DROPPED_ITEM) && (deathmatch.value == 0))
				SetRespawn(ent, 20);

			return true;
		}
	};

	static EntInteractAdapter Pickup_PowerArmor= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {

			int quantity;

			quantity= other.client.pers.inventory[ITEM_INDEX(ent.item)];

			other.client.pers.inventory[ITEM_INDEX(ent.item)]++;

			if (deathmatch.value != 0) {
				if (0 == (ent.spawnflags & DROPPED_ITEM))
					SetRespawn(ent, ent.item.quantity);
				// auto-use for DM only if we didn't already have one
				if (0 == quantity)
					ent.item.use.use(other, ent.item);
			}
			return true;
		}
	};

	//	======================================================================

	static EntInteractAdapter Pickup_Powerup= new EntInteractAdapter() {

		public boolean interact(edict_t ent, edict_t other) {
			int quantity;

			quantity= other.client.pers.inventory[ITEM_INDEX(ent.item)];
			if ((skill.value == 1 && quantity >= 2) || (skill.value >= 2 && quantity >= 1))
				return false;

			if ((coop.value != 0) && (ent.item.flags & IT_STAY_COOP) != 0 && (quantity > 0))
				return false;

			other.client.pers.inventory[ITEM_INDEX(ent.item)]++;

			if (deathmatch.value != 0) {
				if (0 == (ent.spawnflags & DROPPED_ITEM))
					SetRespawn(ent, ent.item.quantity);
				if (((int) dmflags.value & DF_INSTANT_ITEMS) != 0
					|| ((ent.item.use == Use_Quad) && 0 != (ent.spawnflags & DROPPED_PLAYER_ITEM))) {
					if ((ent.item.use == Use_Quad) && 0 != (ent.spawnflags & DROPPED_PLAYER_ITEM))
						quad_drop_timeout_hack= (int) ((ent.nextthink - level.time) / FRAMETIME);

					ent.item.use.use(other, ent.item);
				}
			}

			return true;
		}
	};

	static EntInteractAdapter Pickup_Adrenaline= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			if (deathmatch.value == 0)
				other.max_health += 1;

			if (other.health < other.max_health)
				other.health= other.max_health;

			if (0 == (ent.spawnflags & DROPPED_ITEM) && (deathmatch.value != 0))
				SetRespawn(ent, ent.item.quantity);

			return true;

		}
	};

	static EntInteractAdapter Pickup_AncientHead= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			other.max_health += 2;

			if (0 == (ent.spawnflags & DROPPED_ITEM) && (deathmatch.value != 0))
				SetRespawn(ent, ent.item.quantity);

			return true;
		}
	};

	static EntInteractAdapter Pickup_Bandolier= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			gitem_t item;
			int index;

			if (other.client.pers.max_bullets < 250)
				other.client.pers.max_bullets= 250;
			if (other.client.pers.max_shells < 150)
				other.client.pers.max_shells= 150;
			if (other.client.pers.max_cells < 250)
				other.client.pers.max_cells= 250;
			if (other.client.pers.max_slugs < 75)
				other.client.pers.max_slugs= 75;

			item= FindItem("Bullets");
			if (item != null) {
				index= ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_bullets)
					other.client.pers.inventory[index]= other.client.pers.max_bullets;
			}

			item= FindItem("Shells");
			if (item != null) {
				index= ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_shells)
					other.client.pers.inventory[index]= other.client.pers.max_shells;
			}

			if (0 == (ent.spawnflags & DROPPED_ITEM) && (deathmatch.value != 0))
				SetRespawn(ent, ent.item.quantity);

			return true;

		}
	};

	static EntUseAdapter Use_Item= new EntUseAdapter() {
		public void use(edict_t ent, edict_t other, edict_t activator) {
			ent.svflags &= ~SVF_NOCLIENT;
			ent.use= null;

			if ((ent.spawnflags & ITEM_NO_TOUCH) != 0) {
				ent.solid= SOLID_BBOX;
				ent.touch= null;
			} else {
				ent.solid= SOLID_TRIGGER;
				ent.touch= Touch_Item;
			}

			gi.linkentity(ent);
		}
	};

	/*
	================
	droptofloor
	================
	*/

	static EntThinkAdapter droptofloor= new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			trace_t tr;
			float[] dest= { 0, 0, 0 };

			float v[];

			v= tv(-15, -15, -15);
			VectorCopy(v, ent.mins);
			v= tv(15, 15, 15);
			VectorCopy(v, ent.maxs);

			if (ent.model != null)
				gi.setmodel(ent, ent.model);
			else
				gi.setmodel(ent, ent.item.world_model);
			ent.solid= SOLID_TRIGGER;
			ent.movetype= MOVETYPE_TOSS;
			ent.touch= Touch_Item;

			v= tv(0, 0, -128);
			VectorAdd(ent.s.origin, v, dest);

			tr= gi.trace(ent.s.origin, ent.mins, ent.maxs, dest, ent, MASK_SOLID);
			if (tr.startsolid) {
				gi.dprintf(
					"droptofloor: "
						+ ent.classname
						+ " startsolid at "
						+ vtos(ent.s.origin)
						+ "\n");
				G_FreeEdict(ent);
				return true;
			}

			VectorCopy(tr.endpos, ent.s.origin);

			if (ent.team != null) {
				ent.flags &= ~FL_TEAMSLAVE;
				ent.chain= ent.teamchain;
				ent.teamchain= null;

				ent.svflags |= SVF_NOCLIENT;
				ent.solid= SOLID_NOT;
				if (ent == ent.teammaster) {
					ent.nextthink= level.time + FRAMETIME;
					ent.think= DoRespawn;
				}
			}

			if ((ent.spawnflags & ITEM_NO_TOUCH) != 0) {
				ent.solid= SOLID_BBOX;
				ent.touch= null;
				ent.s.effects &= ~EF_ROTATE;
				ent.s.renderfx &= ~RF_GLOW;
			}

			if ((ent.spawnflags & ITEM_TRIGGER_SPAWN) != 0) {
				ent.svflags |= SVF_NOCLIENT;
				ent.solid= SOLID_NOT;
				ent.use= Use_Item;
			}

			gi.linkentity(ent);
			return true;
		}
	};

	/*
	============
	SpawnItem
	
	Sets the clipping size and plants the object on the floor.
	
	Items can't be immediately dropped to floor, because they might
	be on an entity that hasn't spawned yet.
	============
	*/
	static void SpawnItem(edict_t ent, gitem_t item) {
		PrecacheItem(item);

		if (ent.spawnflags != 0) {
			if (strcmp(ent.classname, "key_power_cube") != 0) {
				ent.spawnflags= 0;
				gi.dprintf(
					""
						+ ent.classname
						+ " at "
						+ vtos(ent.s.origin)
						+ " has invalid spawnflags set\n");
			}
		}

		// some items will be prevented in deathmatch
		if (deathmatch.value != 0) {
			if (((int) dmflags.value & DF_NO_ARMOR) != 0) {
				if (item.pickup == Pickup_Armor || item.pickup == Pickup_PowerArmor) {
					G_FreeEdict(ent);
					return;
				}
			}
			if (((int) dmflags.value & DF_NO_ITEMS) != 0) {
				if (item.pickup == Pickup_Powerup) {
					G_FreeEdict(ent);
					return;
				}
			}
			if (((int) dmflags.value & DF_NO_HEALTH) != 0) {
				if (item.pickup == Pickup_Health
					|| item.pickup == Pickup_Adrenaline
					|| item.pickup == Pickup_AncientHead) {
					G_FreeEdict(ent);
					return;
				}
			}
			if (((int) dmflags.value & DF_INFINITE_AMMO) != 0) {
				if ((item.flags == IT_AMMO) || (strcmp(ent.classname, "weapon_bfg") == 0)) {
					G_FreeEdict(ent);
					return;
				}
			}
		}

		if (coop.value != 0 && (strcmp(ent.classname, "key_power_cube") == 0)) {
			ent.spawnflags |= (1 << (8 + level.power_cubes));
			level.power_cubes++;
		}

		// don't let them drop items that stay in a coop game
		if ((coop.value != 0) && (item.flags & IT_STAY_COOP) != 0) {
			item.drop= null;
		}

		ent.item= item;
		ent.nextthink= level.time + 2 * FRAMETIME; // items start after other solids
		ent.think= droptofloor;
		ent.s.effects= item.world_model_flags;
		ent.s.renderfx= RF_GLOW;

		if (ent.model != null)
			gi.modelindex(ent.model);
	}

	/*
	===============
	Touch_Item
	===============
	*/
	static void Touch_Item(edict_t ent, edict_t other, cplane_t plane, csurface_t surf) {
		boolean taken;

		if (other.client == null)
			return;
		if (other.health < 1)
			return; // dead people can't pickup
		if (ent.item.pickup == null)
			return; // not a grabbable item?

		taken= ent.item.pickup.interact(ent, other);

		if (taken) {
			// flash the screen
			other.client.bonus_alpha= 0.25f;

			// show icon and name on status bar
			other.client.ps.stats[STAT_PICKUP_ICON]= (short) gi.imageindex(ent.item.icon);
			other.client.ps.stats[STAT_PICKUP_STRING]= (short) (CS_ITEMS + ITEM_INDEX(ent.item));
			other.client.pickup_msg_time= level.time + 3.0f;

			// change selected item
			if (ent.item.use != null)
				other.client.pers.selected_item=
					other.client.ps.stats[STAT_SELECTED_ITEM]= (short) ITEM_INDEX(ent.item);

			if (ent.item.pickup == Pickup_Health) {
				if (ent.count == 2)
					gi.sound(
						other,
						CHAN_ITEM,
						gi.soundindex("items/s_health.wav"),
						1,
						ATTN_NORM,
						0);
				else if (ent.count == 10)
					gi.sound(
						other,
						CHAN_ITEM,
						gi.soundindex("items/n_health.wav"),
						1,
						ATTN_NORM,
						0);
				else if (ent.count == 25)
					gi.sound(
						other,
						CHAN_ITEM,
						gi.soundindex("items/l_health.wav"),
						1,
						ATTN_NORM,
						0);
				else // (ent.count == 100)
					gi.sound(
						other,
						CHAN_ITEM,
						gi.soundindex("items/m_health.wav"),
						1,
						ATTN_NORM,
						0);
			} else if (ent.item.pickup_sound != null) {
				gi.sound(other, CHAN_ITEM, gi.soundindex(ent.item.pickup_sound), 1, ATTN_NORM, 0);
			}
		}

		if (0 == (ent.spawnflags & ITEM_TARGETS_USED)) {
			G_UseTargets(ent, other);
			ent.spawnflags |= ITEM_TARGETS_USED;
		}

		if (!taken)
			return;

		if (!((coop.value != 0) && (ent.item.flags & IT_STAY_COOP) != 0)
			|| 0 != (ent.spawnflags & (DROPPED_ITEM | DROPPED_PLAYER_ITEM))) {
			if ((ent.flags & FL_RESPAWN) != 0)
				ent.flags &= ~FL_RESPAWN;
			else
				G_FreeEdict(ent);
		}
	}

	/*
	==================
	Cmd_Give_f
	
	Give items to a client
	==================
	*/
	static void Cmd_Give_f(edict_t ent) {
		String name;
		gitem_t it;
		int index;
		int i;
		boolean give_all;
		edict_t it_ent;

		if (deathmatch.value == 0 && sv_cheats.value == 0) {
			gi.cprintf(
				ent,
				PRINT_HIGH,
				"You must run the server with '+set cheats 1' to enable this command.\n");
			return;
		}

		name= gi.args();

		if (0 == Q_stricmp(name, "all"))
			give_all= true;
		else
			give_all= false;

		if (give_all || 0 == Q_stricmp(gi.argv(1), "health")) {
			if (gi.argc() == 3)
				ent.health= atoi(gi.argv(2));
			else
				ent.health= ent.max_health;
			if (!give_all)
				return;
		}

		if (give_all || 0 == Q_stricmp(name, "weapons")) {
			for (i= 0; i < game.num_items; i++) {
				it= itemlist[i];
				if (null == it.pickup)
					continue;
				if (0 == (it.flags & IT_WEAPON))
					continue;
				ent.client.pers.inventory[i] += 1;
			}
			if (!give_all)
				return;
		}

		if (give_all || 0 == Q_stricmp(name, "ammo")) {
			for (i= 0; i < game.num_items; i++) {
				it= itemlist[i];
				if (null == it.pickup)
					continue;
				if (0 == (it.flags & IT_AMMO))
					continue;
				Add_Ammo(ent, it, 1000);
			}
			if (!give_all)
				return;
		}

		if (give_all || Q_stricmp(name, "armor") == 0) {
			gitem_armor_t info;

			it= FindItem("Jacket Armor");
			ent.client.pers.inventory[ITEM_INDEX(it)]= 0;

			it= FindItem("Combat Armor");
			ent.client.pers.inventory[ITEM_INDEX(it)]= 0;

			it= FindItem("Body Armor");
			info= (gitem_armor_t) it.info;
			ent.client.pers.inventory[ITEM_INDEX(it)]= info.max_count;

			if (!give_all)
				return;
		}

		if (give_all || Q_stricmp(name, "Power Shield") == 0) {
			it= FindItem("Power Shield");
			it_ent= G_Spawn();
			it_ent.classname= it.classname;
			SpawnItem(it_ent, it);
			Touch_Item(it_ent, ent, null, null);
			if (it_ent.inuse)
				G_FreeEdict(it_ent);

			if (!give_all)
				return;
		}

		if (give_all) {
			for (i= 0; i < game.num_items; i++) {
				it= itemlist[i];
				if (it.pickup != null)
					continue;
				if ((it.flags & (IT_ARMOR | IT_WEAPON | IT_AMMO)) != 0)
					continue;
				ent.client.pers.inventory[i]= 1;
			}
			return;
		}

		it= FindItem(name);
		if (it == null) {
			name= gi.argv(1);
			it= FindItem(name);
			if (it == null) {
				gi.cprintf(ent, PRINT_HIGH, "unknown item\n");
				return;
			}
		}

		if (it.pickup == null) {
			gi.cprintf(ent, PRINT_HIGH, "non-pickup item\n");
			return;
		}

		index= ITEM_INDEX(it);

		if ((it.flags & IT_AMMO) != 0) {
			if (gi.argc() == 3)
				ent.client.pers.inventory[index]= atoi(gi.argv(2));
			else
				ent.client.pers.inventory[index] += it.quantity;
		} else {
			it_ent= G_Spawn();
			it_ent.classname= it.classname;
			SpawnItem(it_ent, it);
			Touch_Item(it_ent, ent, null, null);
			if (it_ent.inuse)
				G_FreeEdict(it_ent);
		}
	}

	/*
	==================
	Cmd_God_f
	
	Sets client to godmode
	
	argv(0) god
	==================
	*/
	static void Cmd_God_f(edict_t ent) {
		String msg;

		if (deathmatch.value == 0 && sv_cheats.value == 0) {
			gi.cprintf(
				ent,
				PRINT_HIGH,
				"You must run the server with '+set cheats 1' to enable this command.\n");
			return;
		}

		ent.flags ^= FL_GODMODE;
		if (0 == (ent.flags & FL_GODMODE))
			msg= "godmode OFF\n";
		else
			msg= "godmode ON\n";

		gi.cprintf(ent, PRINT_HIGH, msg);
	}

	/*
	==================
	Cmd_Notarget_f
	
	Sets client to notarget
	
	argv(0) notarget
	==================
	*/
	static void Cmd_Notarget_f(edict_t ent) {
		String msg;

		if (deathmatch.value != 0 && sv_cheats.value == 0) {
			gi.cprintf(
				ent,
				PRINT_HIGH,
				"You must run the server with '+set cheats 1' to enable this command.\n");
			return;
		}

		ent.flags ^= FL_NOTARGET;
		if (0 == (ent.flags & FL_NOTARGET))
			msg= "notarget OFF\n";
		else
			msg= "notarget ON\n";

		gi.cprintf(ent, PRINT_HIGH, msg);
	}

	/*
	==================
	Cmd_Noclip_f
	
	argv(0) noclip
	==================
	*/
	static void Cmd_Noclip_f(edict_t ent) {
		String msg;

		if (deathmatch.value != 0 && sv_cheats.value == 0) {
			gi.cprintf(
				ent,
				PRINT_HIGH,
				"You must run the server with '+set cheats 1' to enable this command.\n");
			return;
		}

		if (ent.movetype == MOVETYPE_NOCLIP) {
			ent.movetype= MOVETYPE_WALK;
			msg= "noclip OFF\n";
		} else {
			ent.movetype= MOVETYPE_NOCLIP;
			msg= "noclip ON\n";
		}

		gi.cprintf(ent, PRINT_HIGH, msg);
	}

	/*
	==================
	Cmd_Use_f
	
	Use an inventory item
	==================
	*/
	static void Cmd_Use_f(edict_t ent) {
		int index;
		gitem_t it;
		String s;

		s= gi.args();
		it= FindItem(s);
		if (it != null) {
			gi.cprintf(ent, PRINT_HIGH, "unknown item: " + s + "\n");
			return;
		}
		if (it.use == null) {
			gi.cprintf(ent, PRINT_HIGH, "Item is not usable.\n");
			return;
		}
		index= ITEM_INDEX(it);
		if (0 == ent.client.pers.inventory[index]) {
			gi.cprintf(ent, PRINT_HIGH, "Out of item: " + s + "\n");
			return;
		}

		it.use.use(ent, it);
	}

	/*
	==================
	Cmd_Drop_f
	
	Drop an inventory item
	==================
	*/
	static void Cmd_Drop_f(edict_t ent) {
		int index;
		gitem_t it;
		String s;

		s= gi.args();
		it= FindItem(s);
		if (it == null) {
			gi.cprintf(ent, PRINT_HIGH, "unknown item: " + s + "\n");
			return;
		}
		if (it.drop == null) {
			gi.cprintf(ent, PRINT_HIGH, "Item is not dropable.\n");
			return;
		}
		index= ITEM_INDEX(it);
		if (0 == ent.client.pers.inventory[index]) {
			gi.cprintf(ent, PRINT_HIGH, "Out of item: " + s + "\n");
			return;
		}

		it.drop.drop(ent, it);
	}

	/*
	=================
	Cmd_Inven_f
	=================
	*/
	static void Cmd_Inven_f(edict_t ent) {
		int i;
		gclient_t cl;

		cl= ent.client;

		cl.showscores= false;
		cl.showhelp= false;

		if (cl.showinventory) {
			cl.showinventory= false;
			return;
		}

		cl.showinventory= true;

		gi.WriteByte(svc_inventory);
		for (i= 0; i < MAX_ITEMS; i++) {
			gi.WriteShort(cl.pers.inventory[i]);
		}
		gi.unicast(ent, true);
	}

	/*
	=================
	Cmd_InvUse_f
	=================
	*/
	static void Cmd_InvUse_f(edict_t ent) {
		gitem_t it;

		ValidateSelectedItem(ent);

		if (ent.client.pers.selected_item == -1) {
			gi.cprintf(ent, PRINT_HIGH, "No item to use.\n");
			return;
		}

		it= itemlist[ent.client.pers.selected_item];
		if (it.use == null) {
			gi.cprintf(ent, PRINT_HIGH, "Item is not usable.\n");
			return;
		}
		it.use.use(ent, it);
	}

	/*
	=================
	Cmd_WeapPrev_f
	=================
	*/
	static void Cmd_WeapPrev_f(edict_t ent) {
		gclient_t cl;
		int i, index;
		gitem_t it;
		int selected_weapon;

		cl= ent.client;

		if (cl.pers.weapon == null)
			return;

		selected_weapon= ITEM_INDEX(cl.pers.weapon);

		// scan  for the next valid one
		for (i= 1; i <= MAX_ITEMS; i++) {
			index= (selected_weapon + i) % MAX_ITEMS;
			if (0 == cl.pers.inventory[index])
				continue;

			it= itemlist[index];
			if (it.use == null)
				continue;

			if (0 == (it.flags & IT_WEAPON))
				continue;
			it.use.use(ent, it);
			if (cl.pers.weapon == it)
				return; // successful
		}
	}

	/*
	=================
	Cmd_WeapNext_f
	=================
	*/
	static void Cmd_WeapNext_f(edict_t ent) {
		gclient_t cl;
		int i, index;
		gitem_t it;
		int selected_weapon;

		cl= ent.client;

		if (null == cl.pers.weapon)
			return;

		selected_weapon= ITEM_INDEX(cl.pers.weapon);

		// scan  for the next valid one
		for (i= 1; i <= MAX_ITEMS; i++) {
			index= (selected_weapon + MAX_ITEMS - i) % MAX_ITEMS;
			if (0 == cl.pers.inventory[index])
				continue;
			it= itemlist[index];
			if (null == it.use)
				continue;
			if (0 == (it.flags & IT_WEAPON))
				continue;
			it.use.use(ent, it);
			if (cl.pers.weapon == it)
				return; // successful
		}
	}

	/*
	=================
	Cmd_WeapLast_f
	=================
	*/
	static void Cmd_WeapLast_f(edict_t ent) {
		gclient_t cl;
		int index;
		gitem_t it;

		cl= ent.client;

		if (null == cl.pers.weapon || null == cl.pers.lastweapon)
			return;

		index= ITEM_INDEX(cl.pers.lastweapon);
		if (0 == cl.pers.inventory[index])
			return;
		it= itemlist[index];
		if (null == it.use)
			return;
		if (0 == (it.flags & IT_WEAPON))
			return;
		it.use.use(ent, it);
	}

	/*
	=================
	Cmd_InvDrop_f
	=================
	*/
	static void Cmd_InvDrop_f(edict_t ent) {
		gitem_t it;

		ValidateSelectedItem(ent);

		if (ent.client.pers.selected_item == -1) {
			gi.cprintf(ent, PRINT_HIGH, "No item to drop.\n");
			return;
		}

		it= itemlist[ent.client.pers.selected_item];
		if (it.drop == null) {
			gi.cprintf(ent, PRINT_HIGH, "Item is not dropable.\n");
			return;
		}
		it.drop.drop(ent, it);
	}

	/*
	==================
	LookAtKiller
	==================
	*/
	static void LookAtKiller(edict_t self, edict_t inflictor, edict_t attacker) {
		float dir[]= { 0, 0, 0 };

		edict_t world= g_edicts[0];

		if (attacker != null && attacker != world && attacker != self) {
			VectorSubtract(attacker.s.origin, self.s.origin, dir);
		} else if (inflictor != null && inflictor != world && inflictor != self) {
			VectorSubtract(inflictor.s.origin, self.s.origin, dir);
		} else {
			self.client.killer_yaw= self.s.angles[YAW];
			return;
		}

		if (dir[0] != 0)
			self.client.killer_yaw= (float) (180 / Math.PI * Math.atan2(dir[1], dir[0]));
		else {
			self.client.killer_yaw= 0;
			if (dir[1] > 0)
				self.client.killer_yaw= 90;
			else if (dir[1] < 0)
				self.client.killer_yaw= -90;
		}
		if (self.client.killer_yaw < 0)
			self.client.killer_yaw += 360;

	}

	static void TossClientWeapon(edict_t self) {
		gitem_t item;
		edict_t drop;
		boolean quad;
		float spread;

		if (deathmatch.value == 0)
			return;

		item= self.client.pers.weapon;
		if (0 == self.client.pers.inventory[self.client.ammo_index])
			item= null;
		if (item != null && (strcmp(item.pickup_name, "Blaster") == 0))
			item= null;

		if (0 == ((int) (dmflags.value) & DF_QUAD_DROP))
			quad= false;
		else
			quad= (self.client.quad_framenum > (level.framenum + 10));

		if (item != null && quad)
			spread= 22.5f;
		else
			spread= 0.0f;

		if (item != null) {
			self.client.v_angle[YAW] -= spread;
			drop= Drop_Item(self, item);
			self.client.v_angle[YAW] += spread;
			drop.spawnflags= DROPPED_PLAYER_ITEM;
		}

		if (quad) {
			self.client.v_angle[YAW] += spread;
			drop= Drop_Item(self, FindItemByClassname("item_quad"));
			self.client.v_angle[YAW] -= spread;
			drop.spawnflags |= DROPPED_PLAYER_ITEM;

			drop.touch= Touch_Item;
			drop.nextthink= level.time + (self.client.quad_framenum - level.framenum) * FRAMETIME;
			drop.think= G_FreeEdictA;
		}
	}

	static EntThinkAdapter gib_think= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.s.frame++;
			self.nextthink= level.time + FRAMETIME;

			if (self.s.frame == 10) {
				self.think= G_FreeEdictA;
				self.nextthink= level.time + 8 + random() * 10;
			}
			return true;
		}
	};

	static EntTouchAdapter gib_touch= new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			float[] normal_angles= { 0, 0, 0 }, right= { 0, 0, 0 };

			if (null == self.groundentity)
				return;

			self.touch= null;

			if (plane != null) {
				gi.sound(self, CHAN_VOICE, gi.soundindex("misc/fhit3.wav"), 1, ATTN_NORM, 0);

				vectoangles(plane.normal, normal_angles);
				AngleVectors(normal_angles, null, right, null);
				vectoangles(right, self.s.angles);

				if (self.s.modelindex == sm_meat_index) {
					self.s.frame++;
					self.think= gib_think;
					self.nextthink= level.time + FRAMETIME;
				}
			}
		}
	};

	static EntDieAdapter gib_die= new EntDieAdapter() {
		public void die(
			edict_t self,
			edict_t inflictor,
			edict_t attacker,
			int damage,
			float[] point) {
			G_FreeEdict(self);
		}
	};

	static void ThrowGib(edict_t self, String gibname, int damage, int type) {
		edict_t gib;

		float[] vd= { 0, 0, 0 };
		float[] origin= { 0, 0, 0 };
		float[] size= { 0, 0, 0 };
		float vscale;

		gib= G_Spawn();

		VectorScale(self.size, 0.5f, size);
		VectorAdd(self.absmin, size, origin);
		gib.s.origin[0]= origin[0] + crandom() * size[0];
		gib.s.origin[1]= origin[1] + crandom() * size[1];
		gib.s.origin[2]= origin[2] + crandom() * size[2];

		gi.setmodel(gib, gibname);
		gib.solid= SOLID_NOT;
		gib.s.effects |= EF_GIB;
		gib.flags |= FL_NO_KNOCKBACK;
		gib.takedamage= DAMAGE_YES;
		gib.die= gib_die;

		if (type == GIB_ORGANIC) {
			gib.movetype= MOVETYPE_TOSS;
			gib.touch= gib_touch;
			vscale= 0.5f;
		} else {
			gib.movetype= MOVETYPE_BOUNCE;
			vscale= 1.0f;
		}

		VelocityForDamage(damage, vd);
		VectorMA(self.velocity, vscale, vd, gib.velocity);
		ClipGibVelocity(gib);
		gib.avelocity[0]= random() * 600;
		gib.avelocity[1]= random() * 600;
		gib.avelocity[2]= random() * 600;

		gib.think= G_FreeEdictA;
		gib.nextthink= level.time + 10 + random() * 10;

		gi.linkentity(gib);
	}

	static void ThrowHead(edict_t self, String gibname, int damage, int type) {
		float vd[]= { 0, 0, 0 };

		float vscale;

		self.s.skinnum= 0;
		self.s.frame= 0;
		VectorClear(self.mins);
		VectorClear(self.maxs);

		self.s.modelindex2= 0;
		gi.setmodel(self, gibname);
		self.solid= SOLID_NOT;
		self.s.effects |= EF_GIB;
		self.s.effects &= ~EF_FLIES;
		self.s.sound= 0;
		self.flags |= FL_NO_KNOCKBACK;
		self.svflags &= ~SVF_MONSTER;
		self.takedamage= DAMAGE_YES;
		self.die= gib_die;

		if (type == GIB_ORGANIC) {
			self.movetype= MOVETYPE_TOSS;
			self.touch= gib_touch;
			vscale= 0.5f;
		} else {
			self.movetype= MOVETYPE_BOUNCE;
			vscale= 1.0f;
		}

		VelocityForDamage(damage, vd);
		VectorMA(self.velocity, vscale, vd, self.velocity);
		ClipGibVelocity(self);

		self.avelocity[YAW]= crandom() * 600f;

		self.think= G_FreeEdictA;
		self.nextthink= level.time + 10 + random() * 10;

		gi.linkentity(self);
	}

	static void VelocityForDamage(int damage, float[] v) {
		v[0]= 100.0f * crandom();
		v[1]= 100.0f * crandom();
		v[2]= 200.0f + 100.0f * random();

		if (damage < 50)
			VectorScale(v, 0.7f, v);
		else
			VectorScale(v, 1.2f, v);
	}

	static void ClipGibVelocity(edict_t ent) {
		if (ent.velocity[0] < -300)
			ent.velocity[0]= -300;
		else if (ent.velocity[0] > 300)
			ent.velocity[0]= 300;
		if (ent.velocity[1] < -300)
			ent.velocity[1]= -300;
		else if (ent.velocity[1] > 300)
			ent.velocity[1]= 300;
		if (ent.velocity[2] < 200)
			ent.velocity[2]= 200; // always some upwards
		else if (ent.velocity[2] > 500)
			ent.velocity[2]= 500;
	}

	static void ThrowClientHead(edict_t self, int damage) {
		float vd[]= { 0, 0, 0 };
		String gibname;

		if ((rand() & 1) != 0) {
			gibname= "models/objects/gibs/head2/tris.md2";
			self.s.skinnum= 1; // second skin is player
		} else {
			gibname= "models/objects/gibs/skull/tris.md2";
			self.s.skinnum= 0;
		}

		self.s.origin[2] += 32;
		self.s.frame= 0;
		gi.setmodel(self, gibname);
		VectorSet(self.mins, -16, -16, 0);
		VectorSet(self.maxs, 16, 16, 16);

		self.takedamage= DAMAGE_NO;
		self.solid= SOLID_NOT;
		self.s.effects= EF_GIB;
		self.s.sound= 0;
		self.flags |= FL_NO_KNOCKBACK;

		self.movetype= MOVETYPE_BOUNCE;
		VelocityForDamage(damage, vd);
		VectorAdd(self.velocity, vd, self.velocity);

		if (self.client != null) // bodies in the queue don't have a client anymore
			{
			self.client.anim_priority= ANIM_DEATH;
			self.client.anim_end= self.s.frame;
		} else {
			self.think= null;
			self.nextthink= 0;
		}

		gi.linkentity(self);
	}

	/*
	=================
	debris
	=================
	*/
	static EntDieAdapter debris_die= new EntDieAdapter() {

		public void die(
			edict_t self,
			edict_t inflictor,
			edict_t attacker,
			int damage,
			float[] point) {
			G_FreeEdict(self);
		}
	};

	static void ThrowDebris(edict_t self, String modelname, float speed, float[] origin) {
		edict_t chunk;
		float[] v= { 0, 0, 0 };

		chunk= G_Spawn();
		VectorCopy(origin, chunk.s.origin);
		gi.setmodel(chunk, modelname);
		v[0]= 100 * crandom();
		v[1]= 100 * crandom();
		v[2]= 100 + 100 * crandom();
		VectorMA(self.velocity, speed, v, chunk.velocity);
		chunk.movetype= MOVETYPE_BOUNCE;
		chunk.solid= SOLID_NOT;
		chunk.avelocity[0]= random() * 600;
		chunk.avelocity[1]= random() * 600;
		chunk.avelocity[2]= random() * 600;
		chunk.think= G_FreeEdictA;
		chunk.nextthink= level.time + 5 + random() * 5;
		chunk.s.frame= 0;
		chunk.flags= 0;
		chunk.classname= "debris";
		chunk.takedamage= DAMAGE_YES;
		chunk.die= debris_die;
		gi.linkentity(chunk);
	}

	static void BecomeExplosion1(edict_t self) {
		gi.WriteByte(svc_temp_entity);
		gi.WriteByte(TE_EXPLOSION1);
		gi.WritePosition(self.s.origin);
		gi.multicast(self.s.origin, MULTICAST_PVS);

		G_FreeEdict(self);
	}

	static void BecomeExplosion2(edict_t self) {
		gi.WriteByte(svc_temp_entity);
		gi.WriteByte(TE_EXPLOSION2);
		gi.WritePosition(self.s.origin);
		gi.multicast(self.s.origin, MULTICAST_PVS);

		G_FreeEdict(self);
	}

	/** Returns true, if the players gender flag was set to female .*/
	static boolean IsFemale(edict_t ent) {
		char info;

		if (null == ent.client)
			return false;

		info= Info_ValueForKey(ent.client.pers.userinfo, "gender").charAt(0);
		if (info == 'f' || info == 'F')
			return true;
		return false;
	}

	/** Returns true, if the players gender flag was neither set to female nor to male.*/
	static boolean IsNeutral(edict_t ent) {
		char info;

		if (ent.client == null)
			return false;

		info= Info_ValueForKey(ent.client.pers.userinfo, "gender").charAt(0);

		if (info != 'f' && info != 'F' && info != 'm' && info != 'M')
			return true;
		return false;
	}

	/** Some reports about the cause of the players death. */
	static void ClientObituary(edict_t self, edict_t inflictor, edict_t attacker) {
		int mod;
		String message;
		String message2;
		boolean ff;

		if (coop.value != 0 && attacker.client != null)
			meansOfDeath |= MOD_FRIENDLY_FIRE;

		if (deathmatch.value != 0 || coop.value != 0) {
			ff= (meansOfDeath & MOD_FRIENDLY_FIRE) != 0;
			mod= meansOfDeath & ~MOD_FRIENDLY_FIRE;
			message= null;
			message2= "";

			switch (mod) {
				case MOD_SUICIDE :
					message= "suicides";
					break;
				case MOD_FALLING :
					message= "cratered";
					break;
				case MOD_CRUSH :
					message= "was squished";
					break;
				case MOD_WATER :
					message= "sank like a rock";
					break;
				case MOD_SLIME :
					message= "melted";
					break;
				case MOD_LAVA :
					message= "does a back flip into the lava";
					break;
				case MOD_EXPLOSIVE :
				case MOD_BARREL :
					message= "blew up";
					break;
				case MOD_EXIT :
					message= "found a way out";
					break;
				case MOD_TARGET_LASER :
					message= "saw the light";
					break;
				case MOD_TARGET_BLASTER :
					message= "got blasted";
					break;
				case MOD_BOMB :
				case MOD_SPLASH :
				case MOD_TRIGGER_HURT :
					message= "was in the wrong place";
					break;
			}
			if (attacker == self) {
				switch (mod) {
					case MOD_HELD_GRENADE :
						message= "tried to put the pin back in";
						break;
					case MOD_HG_SPLASH :
					case MOD_G_SPLASH :
						if (IsNeutral(self))
							message= "tripped on its own grenade";
						else if (IsFemale(self))
							message= "tripped on her own grenade";
						else
							message= "tripped on his own grenade";
						break;
					case MOD_R_SPLASH :
						if (IsNeutral(self))
							message= "blew itself up";
						else if (IsFemale(self))
							message= "blew herself up";
						else
							message= "blew himself up";
						break;
					case MOD_BFG_BLAST :
						message= "should have used a smaller gun";
						break;
					default :
						if (IsNeutral(self))
							message= "killed itself";
						else if (IsFemale(self))
							message= "killed herself";
						else
							message= "killed himself";
						break;
				}
			}
			if (message != null) {
				gi.bprintf(PRINT_MEDIUM, "" + self.client.pers.netname + " " + message + ".\n");
				if (deathmatch.value != 0)
					self.client.resp.score--;
				self.enemy= null;
				return;
			}

			self.enemy= attacker;

			if (attacker != null && attacker.client != null) {
				switch (mod) {
					case MOD_BLASTER :
						message= "was blasted by";
						break;
					case MOD_SHOTGUN :
						message= "was gunned down by";
						break;
					case MOD_SSHOTGUN :
						message= "was blown away by";
						message2= "'s super shotgun";
						break;
					case MOD_MACHINEGUN :
						message= "was machinegunned by";
						break;
					case MOD_CHAINGUN :
						message= "was cut in half by";
						message2= "'s chaingun";
						break;
					case MOD_GRENADE :
						message= "was popped by";
						message2= "'s grenade";
						break;
					case MOD_G_SPLASH :
						message= "was shredded by";
						message2= "'s shrapnel";
						break;
					case MOD_ROCKET :
						message= "ate";
						message2= "'s rocket";
						break;
					case MOD_R_SPLASH :
						message= "almost dodged";
						message2= "'s rocket";
						break;
					case MOD_HYPERBLASTER :
						message= "was melted by";
						message2= "'s hyperblaster";
						break;
					case MOD_RAILGUN :
						message= "was railed by";
						break;
					case MOD_BFG_LASER :
						message= "saw the pretty lights from";
						message2= "'s BFG";
						break;
					case MOD_BFG_BLAST :
						message= "was disintegrated by";
						message2= "'s BFG blast";
						break;
					case MOD_BFG_EFFECT :
						message= "couldn't hide from";
						message2= "'s BFG";
						break;
					case MOD_HANDGRENADE :
						message= "caught";
						message2= "'s handgrenade";
						break;
					case MOD_HG_SPLASH :
						message= "didn't see";
						message2= "'s handgrenade";
						break;
					case MOD_HELD_GRENADE :
						message= "feels";
						message2= "'s pain";
						break;
					case MOD_TELEFRAG :
						message= "tried to invade";
						message2= "'s personal space";
						break;
				}
				if (message != null) {
					gi.bprintf(
						PRINT_MEDIUM,
						self.client.pers.netname
							+ " "
							+ message
							+ " "
							+ attacker.client.pers.netname
							+ ""
							+ message2);
					if (deathmatch.value != 0) {
						if (ff)
							attacker.client.resp.score--;
						else
							attacker.client.resp.score++;
					}
					return;
				}
			}
		}

		gi.bprintf(PRINT_MEDIUM, self.client.pers.netname + " died.\n");
		if (deathmatch.value != 0)
			self.client.resp.score--;
	}

	/*
	==================
	DeathmatchScoreboardMessage
	
	==================
	*/
	static void DeathmatchScoreboardMessage(edict_t ent, edict_t killer) {
		String entry;
		String string;
		int stringlength;
		int i, j, k;
		int sorted[]= new int[MAX_CLIENTS];
		int sortedscores[]= new int[MAX_CLIENTS];
		int score, total;
		int picnum;
		int x, y;
		gclient_t cl;
		edict_t cl_ent;
		String tag;

		// sort the clients by score
		total= 0;
		for (i= 0; i < game.maxclients; i++) {
			cl_ent= g_edicts[1 + i];
			if (!cl_ent.inuse || game.clients[i].resp.spectator)
				continue;
			score= game.clients[i].resp.score;
			for (j= 0; j < total; j++) {
				if (score > sortedscores[j])
					break;
			}
			for (k= total; k > j; k--) {
				sorted[k]= sorted[k - 1];
				sortedscores[k]= sortedscores[k - 1];
			}
			sorted[j]= i;
			sortedscores[j]= score;
			total++;
		}

		// print level name and exit rules
		string= "";

		stringlength= strlen(string);

		// add the clients in sorted order
		if (total > 12)
			total= 12;

		for (i= 0; i < total; i++) {
			cl= game.clients[sorted[i]];
			cl_ent= g_edicts[1 + sorted[i]];

			picnum= gi.imageindex("i_fixme");
			x= (i >= 6) ? 160 : 0;
			y= 32 + 32 * (i % 6);

			// add a dogtag
			if (cl_ent == ent)
				tag= "tag1";
			else if (cl_ent == killer)
				tag= "tag2";
			else
				tag= null;
			if (tag != null) {
				entry= "xv " + (x + 32) + " yv " + y + " picn " + tag + " ";
				j= strlen(entry);
				if (stringlength + j > 1024)
					break;

				string= string + entry;

				//was: strcpy (string + stringlength, entry);
				stringlength += j;
			}

			// send the layout
			entry=
				"client "
					+ x
					+ " "
					+ y
					+ " "
					+ sorted[i]
					+ " "
					+ cl.resp.score
					+ " "
					+ cl.ping
					+ " "
					+ (level.framenum - cl.resp.enterframe) / 600f
					+ " ";

			j= strlen(entry);

			if (stringlength + j > 1024)
				break;

			string += entry;
			// was: strcpy(string + stringlength, entry);
			stringlength += j;
		}

		gi.WriteByte(svc_layout);
		gi.WriteString(string);
	}

	/*
	==================
	DeathmatchScoreboard
	
	Draw instead of help message.
	Note that it isn't that hard to overflow the 1400 byte message limit!
	==================
	*/
	static void DeathmatchScoreboard(edict_t ent) {
		DeathmatchScoreboardMessage(ent, ent.enemy);
		gi.unicast(ent, true);
	}

	/*
	==================
	Cmd_Score_f
	
	Display the scoreboard
	==================
	*/
	static void Cmd_Score_f(edict_t ent) {
		ent.client.showinventory= false;
		ent.client.showhelp= false;

		if (0 == deathmatch.value && 0 == coop.value)
			return;

		if (ent.client.showscores) {
			ent.client.showscores= false;
			return;
		}

		ent.client.showscores= true;
		DeathmatchScoreboard(ent);
	}

	/*
	==================
	HelpComputer
	
	Draw help computer.
	==================
	*/
	static void HelpComputer(edict_t ent) {
		String string;
		String sk;

		if (skill.value == 0)
			sk= "easy";
		else if (skill.value == 1)
			sk= "medium";
		else if (skill.value == 2)
			sk= "hard";
		else
			sk= "hard+";

		// send the layout
			string= "xv 32 yv 8 picn help " + // background
		"xv 202 yv 12 string2 \"" + sk + "\" " + // skill
		"xv 0 yv 24 cstring2 \"" + level.level_name + "\" " + // level name
		"xv 0 yv 54 cstring2 \"" + game.helpmessage1 + "\" " + // help 1
		"xv 0 yv 110 cstring2 \"" + game.helpmessage2 + "\" " + // help 2
	"xv 50 yv 164 string2 \" kills     goals    secrets\" "
		+ "xv 50 yv 172 string2 \""
		+ level.killed_monsters
		+ "/"
		+ level.total_monsters
		+ "       "
		+ level.found_goals
		+ "/"
		+ level.total_goals
		+ "       "
		+ level.found_secrets
		+ "/"
		+ level.total_secrets
		+ "\" ";

		gi.WriteByte(svc_layout);
		gi.WriteString(string);
		gi.unicast(ent, true);
	}

	/*
	==================
	Cmd_Help_f
	
	Display the current help message
	==================
	*/
	static void Cmd_Help_f(edict_t ent) {
		// this is for backwards compatability
		if (deathmatch.value != 0) {
			Cmd_Score_f(ent);
			return;
		}

		ent.client.showinventory= false;
		ent.client.showscores= false;

		if (ent.client.showhelp && (ent.client.pers.game_helpchanged == game.helpchanged)) {
			ent.client.showhelp= false;
			return;
		}

		ent.client.showhelp= true;
		ent.client.pers.helpchanged= 0;
		HelpComputer(ent);
	}

	static int player_die_i= 0;

	/*
	==================
	player_die
	==================
	*/
	static void player_die(
		edict_t self,
		edict_t inflictor,
		edict_t attacker,
		int damage,
		float[] point) {
		int n;

		VectorClear(self.avelocity);

		self.takedamage= DAMAGE_YES;
		self.movetype= MOVETYPE_TOSS;

		self.s.modelindex2= 0; // remove linked weapon model

		self.s.angles[0]= 0;
		self.s.angles[2]= 0;

		self.s.sound= 0;
		self.client.weapon_sound= 0;

		self.maxs[2]= -8;

		//		self.solid = SOLID_NOT;
		self.svflags |= SVF_DEADMONSTER;

		if (self.deadflag == 0) {
			self.client.respawn_time= level.time + 1.0f;
			LookAtKiller(self, inflictor, attacker);
			self.client.ps.pmove.pm_type= PM_DEAD;
			ClientObituary(self, inflictor, attacker);
			TossClientWeapon(self);
			if (deathmatch.value != 0)
				Cmd_Help_f(self); // show scores

			// clear inventory
			// this is kind of ugly, but it's how we want to handle keys in coop
			for (n= 0; n < game.num_items; n++) {
				if (coop.value != 0 && (itemlist[n].flags & IT_KEY) != 0)
					self.client.resp.coop_respawn.inventory[n]= self.client.pers.inventory[n];
				self.client.pers.inventory[n]= 0;
			}
		}

		// remove powerups
		self.client.quad_framenum= 0;
		self.client.invincible_framenum= 0;
		self.client.breather_framenum= 0;
		self.client.enviro_framenum= 0;
		self.flags &= ~FL_POWER_ARMOR;

		if (self.health < -40) { // gib
			gi.sound(self, CHAN_BODY, gi.soundindex("misc/udeath.wav"), 1, ATTN_NORM, 0);
			for (n= 0; n < 4; n++)
				ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage, GIB_ORGANIC);
			ThrowClientHead(self, damage);

			self.takedamage= DAMAGE_NO;
		} else { // normal death
			if (self.deadflag == 0) {

				player_die_i= (player_die_i + 1) % 3;
				// start a death animation
				self.client.anim_priority= ANIM_DEATH;
				if ((self.client.ps.pmove.pm_flags & PMF_DUCKED) != 0) {
					self.s.frame= M_Player.FRAME_crdeath1 - 1;
					self.client.anim_end= M_Player.FRAME_crdeath5;
				} else
					switch (player_die_i) {
						case 0 :
							self.s.frame= M_Player.FRAME_death101 - 1;
							self.client.anim_end= M_Player.FRAME_death106;
							break;
						case 1 :
							self.s.frame= M_Player.FRAME_death201 - 1;
							self.client.anim_end= M_Player.FRAME_death206;
							break;
						case 2 :
							self.s.frame= M_Player.FRAME_death301 - 1;
							self.client.anim_end= M_Player.FRAME_death308;
							break;
					}

				gi.sound(
					self,
					CHAN_VOICE,
					gi.soundindex("*death" + ((rand() % 4) + 1) + ".wav"),
					1,
					ATTN_NORM,
					0);
			}
		}

		self.deadflag= DEAD_DEAD;

		gi.linkentity(self);
	}

	//=======================================================================

	/*
	=================
	Cmd_Kill_f
	=================
	*/
	static void Cmd_Kill_f(edict_t ent) {
		if ((level.time - ent.client.respawn_time) < 5)
			return;
		ent.flags &= ~FL_GODMODE;
		ent.health= 0;
		meansOfDeath= MOD_SUICIDE;
		player_die(ent, ent, ent, 100000, vec3_origin);
	}

	/*
	=================
	Cmd_PutAway_f
	=================
	*/
	static void Cmd_PutAway_f(edict_t ent) {
		ent.client.showscores= false;
		ent.client.showhelp= false;
		ent.client.showinventory= false;

	}

	static Comparator PlayerSort= new Comparator() {
		public int compare(Object o1, Object o2) {
			int anum= ((Integer) o1).intValue();
			int bnum= ((Integer) o2).intValue();

			int anum1= game.clients[anum].ps.stats[STAT_FRAGS];
			int bnum1= game.clients[bnum].ps.stats[STAT_FRAGS];

			if (anum1 < bnum1)
				return -1;
			if (anum1 > bnum1)
				return 1;
			return 0;
		}
	};

	static int strlen(String in) {
		return in.length();
	}

	/*
	=================
	Cmd_Players_f
	=================
	*/
	static void Cmd_Players_f(edict_t ent) {
		int i;
		int count;
		String small;
		String large;

		Integer index[]= new Integer[256];

		count= 0;
		for (i= 0; i < maxclients.value; i++) {
			if (game.clients[i].pers.connected) {
				index[count]= new Integer(i);
				count++;
			}
		}

		// sort by frags
		//qsort(index, count, sizeof(index[0]), PlayerSort);
		//replaced by:
		Arrays.sort(index, 0, count - 1, PlayerSort);

		// print information
		large= "";

		for (i= 0; i < count; i++) {
			small=
				game.clients[index[i].intValue()].ps.stats[STAT_FRAGS]
					+ " "
					+ game.clients[index[i].intValue()].pers.netname
					+ "\n";

			if (strlen(small) + strlen(large) > 1024 - 100) {
				// can't print all of them in one packet
				large += "...\n";
				break;
			}
			large += small;
		}

		gi.cprintf(ent, PRINT_HIGH, "" + large + "\n" + count + " players\n");
	}

	/*
	=================
	Cmd_Wave_f
	=================
	*/
	static void Cmd_Wave_f(edict_t ent) {
		int i;

		i= atoi(gi.argv(1));

		// can't wave when ducked
		if ((ent.client.ps.pmove.pm_flags & PMF_DUCKED) != 0)
			return;

		if (ent.client.anim_priority > ANIM_WAVE)
			return;

		ent.client.anim_priority= ANIM_WAVE;

		switch (i) {
			case 0 :
				gi.cprintf(ent, PRINT_HIGH, "flipoff\n");
				ent.s.frame= M_Player.FRAME_flip01 - 1;
				ent.client.anim_end= M_Player.FRAME_flip12;
				break;
			case 1 :
				gi.cprintf(ent, PRINT_HIGH, "salute\n");
				ent.s.frame= M_Player.FRAME_salute01 - 1;
				ent.client.anim_end= M_Player.FRAME_salute11;
				break;
			case 2 :
				gi.cprintf(ent, PRINT_HIGH, "taunt\n");
				ent.s.frame= M_Player.FRAME_taunt01 - 1;
				ent.client.anim_end= M_Player.FRAME_taunt17;
				break;
			case 3 :
				gi.cprintf(ent, PRINT_HIGH, "wave\n");
				ent.s.frame= M_Player.FRAME_wave01 - 1;
				ent.client.anim_end= M_Player.FRAME_wave11;
				break;
			case 4 :
			default :
				gi.cprintf(ent, PRINT_HIGH, "point\n");
				ent.s.frame= M_Player.FRAME_point01 - 1;
				ent.client.anim_end= M_Player.FRAME_point12;
				break;
		}
	}

	static void strcat(String in, String i) {
		in += i;
	}

	/*
	==================
	Cmd_Say_f
	==================
	*/
	static void Cmd_Say_f(edict_t ent, boolean team, boolean arg0) {
		/*
		int i, j;
		edict_t other;
		char p;
		String text;
		gclient_t cl;
		
		if (gi.argc() < 2 && !arg0)
			return;
		
		if (0 == ((int) (dmflags.value) & (DF_MODELTEAMS | DF_SKINTEAMS)))
			team = false;
		
		if (team)
			text = "(" + ent.client.pers.netname + "): ";
		else
			text = "" + ent.client.pers.netname + ": ";
		
		if (arg0)
		{
			strcat(text, gi.argv(0));
			strcat(text, " ");
			strcat(text, gi.args());
		}
		else
		{
			p = gi.args();
			// *p ==
			if (p == '"')
			{
				p++;
				p[strlen(p) - 1] = 0;
			}
			strcat(text, p);
		}
		
		// don't let text be too long for malicious reasons
		if (strlen(text) > 150)
			text[150] = 0;
		
		strcat(text, "\n");
		
		if (flood_msgs.value != 0)
		{
			cl = ent.client;
		
			if (level.time < cl.flood_locktill)
			{
				gi.cprintf(
					ent,
					PRINT_HIGH,
					"You can't talk for " + (int) (cl.flood_locktill - level.time) + " more seconds\n");
				return;
			}
			i = (int) (cl.flood_whenhead - flood_msgs.value + 1);
			if (i < 0)
				i = (sizeof(cl.flood_when) / sizeof(cl.flood_when[0])) + i;
			if (cl.flood_when[i] != 0 && level.time - cl.flood_when[i] < flood_persecond.value)
			{
				cl.flood_locktill = level.time + flood_waitdelay.value;
				gi.cprintf(
					ent,
					PRINT_CHAT,
					"Flood protection:  You can't talk for " + (int) flood_waitdelay.value + " seconds.\n");
				return;
			}
			cl.flood_whenhead = (cl.flood_whenhead + 1) % (sizeof(cl.flood_when) / sizeof(cl.flood_when[0]));
			cl.flood_when[cl.flood_whenhead] = level.time;
		}
		
		if (dedicated.value!=0)
			gi.cprintf(null, PRINT_CHAT, "" + text + "" );
		
		for (j = 1; j <= game.maxclients; j++)
		{
			other = g_edicts[j];
			if (!other.inuse)
				continue;
			if (other.client==null)
				continue;
			if (team)
			{
				if (!OnSameTeam(ent, other))
					continue;
			}
			gi.cprintf(other, PRINT_CHAT, "" + text+"" );
		}
		*/
	}

	/**
	 * Returns the playerlist. 
	 * TODO: The list is badly formatted at the moment, RST. 
	 */
	static void Cmd_PlayerList_f(edict_t ent) {
		int i;
		String st;
		String text;
		edict_t e2;

		// connect time, ping, score, name
		text= "";

		for (i= 0; i < maxclients.value; i++) {
			e2= g_edicts[1 + i];
			if (!e2.inuse)
				continue;

			st=
				""
					+ (level.framenum - e2.client.resp.enterframe) / 600
					+ ":"
					+ ((level.framenum - e2.client.resp.enterframe) % 600) / 10
					+ " "
					+ e2.client.ping
					+ " "
					+ e2.client.resp.score
					+ " "
					+ e2.client.pers.netname
					+ " "
					+ (e2.client.resp.spectator ? " (spectator)" : "")
					+ "\n";

			if (strlen(text) + strlen(st) > 1024 - 50) {
				text += "And more...\n";
				gi.cprintf(ent, PRINT_HIGH, "" + text + "");
				return;
			}
			text += st;

		}
		gi.cprintf(ent, PRINT_HIGH, text);
	}

	/** 
	 * Processes the commands the player enters in the quake console. 
	 * 
		*/
	static void ClientCommand(edict_t ent) {
		String cmd;

		if (ent.client == null)
			return; // not fully in game yet

		cmd= gi.argv(0);

		if (Q_stricmp(cmd, "players") == 0) {
			Cmd_Players_f(ent);
			return;
		}
		if (Q_stricmp(cmd, "say") == 0) {
			Cmd_Say_f(ent, false, false);
			return;
		}
		if (Q_stricmp(cmd, "say_team") == 0) {
			Cmd_Say_f(ent, true, false);
			return;
		}
		if (Q_stricmp(cmd, "score") == 0) {
			Cmd_Score_f(ent);
			return;
		}
		if (Q_stricmp(cmd, "help") == 0) {
			Cmd_Help_f(ent);
			return;
		}

		if (level.intermissiontime != 0)
			return;

		if (Q_stricmp(cmd, "use") == 0)
			Cmd_Use_f(ent);
		else if (Q_stricmp(cmd, "drop") == 0)
			Cmd_Drop_f(ent);
		else if (Q_stricmp(cmd, "give") == 0)
			Cmd_Give_f(ent);
		else if (Q_stricmp(cmd, "god") == 0)
			Cmd_God_f(ent);
		else if (Q_stricmp(cmd, "notarget") == 0)
			Cmd_Notarget_f(ent);
		else if (Q_stricmp(cmd, "noclip") == 0)
			Cmd_Noclip_f(ent);
		else if (Q_stricmp(cmd, "inven") == 0)
			Cmd_Inven_f(ent);
		else if (Q_stricmp(cmd, "invnext") == 0)
			SelectNextItem(ent, -1);
		else if (Q_stricmp(cmd, "invprev") == 0)
			SelectPrevItem(ent, -1);
		else if (Q_stricmp(cmd, "invnextw") == 0)
			SelectNextItem(ent, IT_WEAPON);
		else if (Q_stricmp(cmd, "invprevw") == 0)
			SelectPrevItem(ent, IT_WEAPON);
		else if (Q_stricmp(cmd, "invnextp") == 0)
			SelectNextItem(ent, IT_POWERUP);
		else if (Q_stricmp(cmd, "invprevp") == 0)
			SelectPrevItem(ent, IT_POWERUP);
		else if (Q_stricmp(cmd, "invuse") == 0)
			Cmd_InvUse_f(ent);
		else if (Q_stricmp(cmd, "invdrop") == 0)
			Cmd_InvDrop_f(ent);
		else if (Q_stricmp(cmd, "weapprev") == 0)
			Cmd_WeapPrev_f(ent);
		else if (Q_stricmp(cmd, "weapnext") == 0)
			Cmd_WeapNext_f(ent);
		else if (Q_stricmp(cmd, "weaplast") == 0)
			Cmd_WeapLast_f(ent);
		else if (Q_stricmp(cmd, "kill") == 0)
			Cmd_Kill_f(ent);
		else if (Q_stricmp(cmd, "putaway") == 0)
			Cmd_PutAway_f(ent);
		else if (Q_stricmp(cmd, "wave") == 0)
			Cmd_Wave_f(ent);
		else if (Q_stricmp(cmd, "playerlist") == 0)
			Cmd_PlayerList_f(ent);
		else // anything that doesn't match a command will be a chat
			Cmd_Say_f(ent, false, true);
	}

	static ItemUseAdapter Use_PowerArmor= new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
			int index;

			if ((ent.flags & FL_POWER_ARMOR) != 0) {
				ent.flags &= ~FL_POWER_ARMOR;
				gi.sound(ent, CHAN_AUTO, gi.soundindex("misc/power2.wav"), 1, ATTN_NORM, 0);
			} else {
				index= ITEM_INDEX(FindItem("cells"));
				if (0 == ent.client.pers.inventory[index]) {
					gi.cprintf(ent, PRINT_HIGH, "No cells for power armor.\n");
					return;
				}
				ent.flags |= FL_POWER_ARMOR;
				gi.sound(ent, CHAN_AUTO, gi.soundindex("misc/power1.wav"), 1, ATTN_NORM, 0);
			}
		}
	};

	static boolean Pickup_PowerArmor(edict_t ent, edict_t other) {
		int quantity;

		quantity= other.client.pers.inventory[ITEM_INDEX(ent.item)];

		other.client.pers.inventory[ITEM_INDEX(ent.item)]++;

		if (deathmatch.value != 0) {
			if (0 == (ent.spawnflags & DROPPED_ITEM))
				SetRespawn(ent, ent.item.quantity);
			// auto-use for DM only if we didn't already have one
			if (0 == quantity)
				ent.item.use.use(other, ent.item);
		}

		return true;
	}

	static ItemDropAdapter Drop_Ammo= new ItemDropAdapter() {
		public void drop(edict_t ent, gitem_t item) {
			edict_t dropped;
			int index;

			index= ITEM_INDEX(item);
			dropped= Drop_Item(ent, item);
			if (ent.client.pers.inventory[index] >= item.quantity)
				dropped.count= item.quantity;
			else
				dropped.count= ent.client.pers.inventory[index];

			if (ent.client.pers.weapon != null
				&& ent.client.pers.weapon.tag == AMMO_GRENADES
				&& item.tag == AMMO_GRENADES
				&& ent.client.pers.inventory[index] - dropped.count <= 0) {
				gi.cprintf(ent, PRINT_HIGH, "Can't drop current weapon\n");
				G_FreeEdict(dropped);
				return;
			}

			ent.client.pers.inventory[index] -= dropped.count;
			ValidateSelectedItem(ent);
		}
	};

	static ItemDropAdapter Drop_General= new ItemDropAdapter() {
		public void drop(edict_t ent, gitem_t item) {
			Drop_Item(ent, item);
			ent.client.pers.inventory[ITEM_INDEX(item)]--;
			ValidateSelectedItem(ent);
		}
	};

	static ItemDropAdapter Drop_PowerArmor= new ItemDropAdapter() {
		public void drop(edict_t ent, gitem_t item) {
			if (0 != (ent.flags & FL_POWER_ARMOR)
				&& (ent.client.pers.inventory[ITEM_INDEX(item)] == 1))
				Use_PowerArmor.use(ent, item);
			Drop_General.drop(ent, item);
		}
	};

	static gitem_armor_t jacketarmor_info= new gitem_armor_t(25, 50, .30f, .00f, ARMOR_JACKET);
	static gitem_armor_t combatarmor_info= new gitem_armor_t(50, 100, .60f, .30f, ARMOR_COMBAT);
	static gitem_armor_t bodyarmor_info= new gitem_armor_t(100, 200, .80f, .60f, ARMOR_BODY);

	static gitem_t itemlist[]= {
		//leave index 0 alone
		null,

		//
		// ARMOR
		//
		new gitem_t(
		/*QUAKED item_armor_body (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/

		"item_armor_body",
			Pickup_Armor,
			null,
			null,
			null,
			"misc/ar1_pkup.wav",
			"models/items/armor/body/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_bodyarmor",
		/* pickup */
		"Body Armor",
		/* width */
		3, 0, null, IT_ARMOR, 0, bodyarmor_info, ARMOR_BODY,
		/* precache */
		""),

		/*QUAKED item_armor_combat (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_armor_combat",
			GAME_AI.Pickup_Armor,
			null,
			null,
			null,
			"misc/ar1_pkup.wav",
			"models/items/armor/combat/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_combatarmor",
		/* pickup */
		"Combat Armor",
		/* width */
		3, 0, null, IT_ARMOR, 0, GAME_AI.combatarmor_info, ARMOR_COMBAT,
		/* precache */
		""),

		/*QUAKED item_armor_jacket (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_armor_jacket",
			GAME_AI.Pickup_Armor,
			null,
			null,
			null,
			"misc/ar1_pkup.wav",
			"models/items/armor/jacket/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_jacketarmor",
		/* pickup */
		"Jacket Armor",
		/* width */
		3, 0, null, IT_ARMOR, 0, GAME_AI.jacketarmor_info, ARMOR_JACKET,
		/* precache */
		""),

		/*QUAKED item_armor_shard (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_armor_shard",
			GAME_AI.Pickup_Armor,
			null,
			null,
			null,
			"misc/ar2_pkup.wav",
			"models/items/armor/shard/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_jacketarmor",
		/* pickup */
		"Armor Shard",
		/* width */
		3, 0, null, IT_ARMOR, 0, null, ARMOR_SHARD,
		/* precache */
		""),

		/*QUAKED item_power_screen (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_power_screen",
			GAME_AI.Pickup_PowerArmor,
			GAME_AI.Use_PowerArmor,
			GAME_AI.Drop_PowerArmor,
			null,
			"misc/ar3_pkup.wav",
			"models/items/armor/screen/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_powerscreen",
		/* pickup */
		"Power Screen",
		/* width */
		0, 60, null, IT_ARMOR, 0, null, 0,
		/* precache */
		""),

		/*QUAKED item_power_shield (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_power_shield",
			Pickup_PowerArmor,
			Use_PowerArmor,
			Drop_PowerArmor,
			null,
			"misc/ar3_pkup.wav",
			"models/items/armor/shield/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_powershield",
		/* pickup */
		"Power Shield",
		/* width */
		0, 60, null, IT_ARMOR, 0, null, 0,
		/* precache */
		"misc/power2.wav misc/power1.wav"),

		//
		// WEAPONS 
		//

		/* weapon_blaster (.3 .3 1) (-16 -16 -16) (16 16 16)
		always owned, never in the world
		*/
		new gitem_t(
			"weapon_blaster",
			null,
			GAME_PWEAPON.Use_Weapon,
			null,
			GAME_PWEAPON.Weapon_Blaster,
			"misc/w_pkup.wav",
			null,
			0,
			"models/weapons/v_blast/tris.md2",
		/* icon */
		"w_blaster",
		/* pickup */
		"Blaster", 0, 0, null, IT_WEAPON | IT_STAY_COOP, WEAP_BLASTER, null, 0,
		/* precache */
		"weapons/blastf1a.wav misc/lasfly.wav"),

		/*QUAKED weapon_shotgun (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_shotgun",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_Shotgun,
			"misc/w_pkup.wav",
			"models/weapons/g_shotg/tris.md2",
			EF_ROTATE,
			"models/weapons/v_shotg/tris.md2",
		/* icon */
		"w_shotgun",
		/* pickup */
		"Shotgun", 0, 1, "Shells", IT_WEAPON | IT_STAY_COOP, WEAP_SHOTGUN, null, 0,
		/* precache */
		"weapons/shotgf1b.wav weapons/shotgr1b.wav"),

		/*QUAKED weapon_supershotgun (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_supershotgun",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_SuperShotgun,
			"misc/w_pkup.wav",
			"models/weapons/g_shotg2/tris.md2",
			EF_ROTATE,
			"models/weapons/v_shotg2/tris.md2",
		/* icon */
		"w_sshotgun",
		/* pickup */
		"Super Shotgun", 0, 2, "Shells", IT_WEAPON | IT_STAY_COOP, WEAP_SUPERSHOTGUN, null, 0,
		/* precache */
		"weapons/sshotf1b.wav"),

		/*QUAKED weapon_machinegun (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_machinegun",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_Machinegun,
			"misc/w_pkup.wav",
			"models/weapons/g_machn/tris.md2",
			EF_ROTATE,
			"models/weapons/v_machn/tris.md2",
		/* icon */
		"w_machinegun",
		/* pickup */
		"Machinegun", 0, 1, "Bullets", IT_WEAPON | IT_STAY_COOP, WEAP_MACHINEGUN, null, 0,
		/* precache */
		"weapons/machgf1b.wav weapons/machgf2b.wav weapons/machgf3b.wav weapons/machgf4b.wav weapons/machgf5b.wav"),

		/*QUAKED weapon_chaingun (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_chaingun",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_Chaingun,
			"misc/w_pkup.wav",
			"models/weapons/g_chain/tris.md2",
			EF_ROTATE,
			"models/weapons/v_chain/tris.md2",
		/* icon */
		"w_chaingun",
		/* pickup */
		"Chaingun", 0, 1, "Bullets", IT_WEAPON | IT_STAY_COOP, WEAP_CHAINGUN, null, 0,
		/* precache */
		"weapons/chngnu1a.wav weapons/chngnl1a.wav weapons/machgf3b.wav` weapons/chngnd1a.wav"),

		/*QUAKED ammo_grenades (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"ammo_grenades",
			Pickup_Ammo,
			GAME_PWEAPON.Use_Weapon,
			Drop_Ammo,
			GAME_PWEAPON.Weapon_Grenade,
			"misc/am_pkup.wav",
			"models/items/ammo/grenades/medium/tris.md2",
			0,
			"models/weapons/v_handgr/tris.md2",
		/* icon */
		"a_grenades",
		/* pickup */
		"Grenades",
		/* width */
		3, 5, "grenades", IT_AMMO | IT_WEAPON, WEAP_GRENADES, null, AMMO_GRENADES,
		/* precache */
		"weapons/hgrent1a.wav weapons/hgrena1b.wav weapons/hgrenc1b.wav weapons/hgrenb1a.wav weapons/hgrenb2a.wav "),

		/*QUAKED weapon_grenadelauncher (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_grenadelauncher",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_GrenadeLauncher,
			"misc/w_pkup.wav",
			"models/weapons/g_launch/tris.md2",
			EF_ROTATE,
			"models/weapons/v_launch/tris.md2",
		/* icon */
		"w_glauncher",
		/* pickup */
		"Grenade Launcher",
			0,
			1,
			"Grenades",
			IT_WEAPON | IT_STAY_COOP,
			WEAP_GRENADELAUNCHER,
			null,
			0,
		/* precache */
		"models/objects/grenade/tris.md2 weapons/grenlf1a.wav weapons/grenlr1b.wav weapons/grenlb1b.wav"),

		/*QUAKED weapon_rocketlauncher (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_rocketlauncher",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_RocketLauncher,
			"misc/w_pkup.wav",
			"models/weapons/g_rocket/tris.md2",
			EF_ROTATE,
			"models/weapons/v_rocket/tris.md2",
		/* icon */
		"w_rlauncher",
		/* pickup */
		"Rocket Launcher", 0, 1, "Rockets", IT_WEAPON | IT_STAY_COOP, WEAP_ROCKETLAUNCHER, null, 0,
		/* precache */
		"models/objects/rocket/tris.md2 weapons/rockfly.wav weapons/rocklf1a.wav weapons/rocklr1b.wav models/objects/debris2/tris.md2"),

		/*QUAKED weapon_hyperblaster (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_hyperblaster",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_HyperBlaster,
			"misc/w_pkup.wav",
			"models/weapons/g_hyperb/tris.md2",
			EF_ROTATE,
			"models/weapons/v_hyperb/tris.md2",
		/* icon */
		"w_hyperblaster",
		/* pickup */
		"HyperBlaster", 0, 1, "Cells", IT_WEAPON | IT_STAY_COOP, WEAP_HYPERBLASTER, null, 0,
		/* precache */
		"weapons/hyprbu1a.wav weapons/hyprbl1a.wav weapons/hyprbf1a.wav weapons/hyprbd1a.wav misc/lasfly.wav"),

		/*QUAKED weapon_railgun (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_railgun",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_Railgun,
			"misc/w_pkup.wav",
			"models/weapons/g_rail/tris.md2",
			EF_ROTATE,
			"models/weapons/v_rail/tris.md2",
		/* icon */
		"w_railgun",
		/* pickup */
		"Railgun", 0, 1, "Slugs", IT_WEAPON | IT_STAY_COOP, WEAP_RAILGUN, null, 0,
		/* precache */
		"weapons/rg_hum.wav"),

		/*QUAKED weapon_bfg (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"weapon_bfg",
			GAME_PWEAPON.Pickup_Weapon,
			GAME_PWEAPON.Use_Weapon,
			GAME_PWEAPON.Drop_Weapon,
			GAME_PWEAPON.Weapon_BFG,
			"misc/w_pkup.wav",
			"models/weapons/g_bfg/tris.md2",
			EF_ROTATE,
			"models/weapons/v_bfg/tris.md2",
		/* icon */
		"w_bfg",
		/* pickup */
		"BFG10K", 0, 50, "Cells", IT_WEAPON | IT_STAY_COOP, WEAP_BFG, null, 0,
		/* precache */
		"sprites/s_bfg1.sp2 sprites/s_bfg2.sp2 sprites/s_bfg3.sp2 weapons/bfg__f1y.wav weapons/bfg__l1a.wav weapons/bfg__x1b.wav weapons/bfg_hum.wav"),

		//
		// AMMO ITEMS
		//

		/*QUAKED ammo_shells (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"ammo_shells",
			GAME_PWEAPON.Pickup_Ammo,
			null,
			GAME_PWEAPON.Drop_Ammo,
			null,
			"misc/am_pkup.wav",
			"models/items/ammo/shells/medium/tris.md2",
			0,
			null,
		/* icon */
		"a_shells",
		/* pickup */
		"Shells",
		/* width */
		3, 10, null, IT_AMMO, 0, null, AMMO_SHELLS,
		/* precache */
		""),

		/*QUAKED ammo_bullets (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"ammo_bullets",
			GAME_PWEAPON.Pickup_Ammo,
			null,
			GAME_PWEAPON.Drop_Ammo,
			null,
			"misc/am_pkup.wav",
			"models/items/ammo/bullets/medium/tris.md2",
			0,
			null,
		/* icon */
		"a_bullets",
		/* pickup */
		"Bullets",
		/* width */
		3, 50, null, IT_AMMO, 0, null, AMMO_BULLETS,
		/* precache */
		""),

		/*QUAKED ammo_cells (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"ammo_cells",
			GAME_PWEAPON.Pickup_Ammo,
			null,
			GAME_PWEAPON.Drop_Ammo,
			null,
			"misc/am_pkup.wav",
			"models/items/ammo/cells/medium/tris.md2",
			0,
			null,
		/* icon */
		"a_cells",
		/* pickup */
		"Cells",
		/* width */
		3, 50, null, IT_AMMO, 0, null, AMMO_CELLS,
		/* precache */
		""),

		/*QUAKED ammo_rockets (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"ammo_rockets",
			GAME_PWEAPON.Pickup_Ammo,
			null,
			GAME_PWEAPON.Drop_Ammo,
			null,
			"misc/am_pkup.wav",
			"models/items/ammo/rockets/medium/tris.md2",
			0,
			null,
		/* icon */
		"a_rockets",
		/* pickup */
		"Rockets",
		/* width */
		3, 5, null, IT_AMMO, 0, null, AMMO_ROCKETS,
		/* precache */
		""),

		/*QUAKED ammo_slugs (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"ammo_slugs",
			GAME_PWEAPON.Pickup_Ammo,
			null,
			GAME_PWEAPON.Drop_Ammo,
			null,
			"misc/am_pkup.wav",
			"models/items/ammo/slugs/medium/tris.md2",
			0,
			null,
		/* icon */
		"a_slugs",
		/* pickup */
		"Slugs",
		/* width */
		3, 10, null, IT_AMMO, 0, null, AMMO_SLUGS,
		/* precache */
		""),

		//
		// POWERUP ITEMS
		//
		/*QUAKED item_quad (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_quad",
			Pickup_Powerup,
			Use_Quad,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/quaddama/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"p_quad",
		/* pickup */
		"Quad Damage",
		/* width */
		2, 60, null, IT_POWERUP, 0, null, 0,
		/* precache */
		"items/damage.wav items/damage2.wav items/damage3.wav"),

		/*QUAKED item_invulnerability (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_invulnerability",
			Pickup_Powerup,
			Use_Invulnerability,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/invulner/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"p_invulnerability",
		/* pickup */
		"Invulnerability",
		/* width */
		2, 300, null, IT_POWERUP, 0, null, 0,
		/* precache */
		"items/protect.wav items/protect2.wav items/protect4.wav"),

		/*QUAKED item_silencer (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_silencer",
			Pickup_Powerup,
			Use_Silencer,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/silencer/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"p_silencer",
		/* pickup */
		"Silencer",
		/* width */
		2, 60, null, IT_POWERUP, 0, null, 0,
		/* precache */
		""),

		/*QUAKED item_breather (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_breather",
			Pickup_Powerup,
			Use_Breather,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/breather/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"p_rebreather",
		/* pickup */
		"Rebreather",
		/* width */
		2, 60, null, IT_STAY_COOP | IT_POWERUP, 0, null, 0,
		/* precache */
		"items/airout.wav"),

		/*QUAKED item_enviro (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_enviro",
			Pickup_Powerup,
			Use_Envirosuit,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/enviro/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"p_envirosuit",
		/* pickup */
		"Environment Suit",
		/* width */
		2, 60, null, IT_STAY_COOP | IT_POWERUP, 0, null, 0,
		/* precache */
		"items/airout.wav"),

		/*QUAKED item_ancient_head (.3 .3 1) (-16 -16 -16) (16 16 16)
		Special item that gives +2 to maximum health
		*/
		new gitem_t(
			"item_ancient_head",
			Pickup_AncientHead,
			null,
			null,
			null,
			"items/pkup.wav",
			"models/items/c_head/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_fixme",
		/* pickup */
		"Ancient Head",
		/* width */
		2, 60, null, 0, 0, null, 0,
		/* precache */
		""),

		/*QUAKED item_adrenaline (.3 .3 1) (-16 -16 -16) (16 16 16)
		gives +1 to maximum health
		*/
		new gitem_t(
			"item_adrenaline",
			Pickup_Adrenaline,
			null,
			null,
			null,
			"items/pkup.wav",
			"models/items/adrenal/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"p_adrenaline",
		/* pickup */
		"Adrenaline",
		/* width */
		2, 60, null, 0, 0, null, 0,
		/* precache */
		""),

		/*QUAKED item_bandolier (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_bandolier",
			Pickup_Bandolier,
			null,
			null,
			null,
			"items/pkup.wav",
			"models/items/band/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"p_bandolier",
		/* pickup */
		"Bandolier",
		/* width */
		2, 60, null, 0, 0, null, 0,
		/* precache */
		""),

		/*QUAKED item_pack (.3 .3 1) (-16 -16 -16) (16 16 16)
		*/
		new gitem_t(
			"item_pack",
			GAME_PWEAPON.Pickup_Pack,
			null,
			null,
			null,
			"items/pkup.wav",
			"models/items/pack/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_pack",
		/* pickup */
		"Ammo Pack",
		/* width */
		2, 180, null, 0, 0, null, 0,
		/* precache */
		""),

		//
		// KEYS
		//
		/*QUAKED key_data_cd (0 .5 .8) (-16 -16 -16) (16 16 16)
		key for computer centers
		*/
		new gitem_t(
			"key_data_cd",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/keys/data_cd/tris.md2",
			EF_ROTATE,
			null,
			"k_datacd",
			"Data CD",
			2,
			0,
			null,
			IT_STAY_COOP | IT_KEY,
			0,
			null,
			0,
		/* precache */
		""),

		/*QUAKED key_power_cube (0 .5 .8) (-16 -16 -16) (16 16 16) TRIGGER_SPAWN NO_TOUCH
		warehouse circuits
		*/
		new gitem_t(
			"key_power_cube",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/keys/power/tris.md2",
			EF_ROTATE,
			null,
			"k_powercube",
			"Power Cube",
			2,
			0,
			null,
			IT_STAY_COOP | IT_KEY,
			0,
			null,
			0,
		/* precache */
		""),

		/*QUAKED key_pyramid (0 .5 .8) (-16 -16 -16) (16 16 16)
		key for the entrance of jail3
		*/
		new gitem_t(
			"key_pyramid",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/keys/pyramid/tris.md2",
			EF_ROTATE,
			null,
			"k_pyramid",
			"Pyramid Key",
			2,
			0,
			null,
			IT_STAY_COOP | IT_KEY,
			0,
			null,
			0,
		/* precache */
		""),

		/*QUAKED key_data_spinner (0 .5 .8) (-16 -16 -16) (16 16 16)
		key for the city computer
		*/
		new gitem_t(
			"key_data_spinner",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/keys/spinner/tris.md2",
			EF_ROTATE,
			null,
			"k_dataspin",
			"Data Spinner",
			2,
			0,
			null,
			IT_STAY_COOP | IT_KEY,
			0,
			null,
			0,
		/* precache */
		""),

		/*QUAKED key_pass (0 .5 .8) (-16 -16 -16) (16 16 16)
		security pass for the security level
		*/
		new gitem_t(
			"key_pass",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/keys/pass/tris.md2",
			EF_ROTATE,
			null,
			"k_security",
			"Security Pass",
			2,
			0,
			null,
			IT_STAY_COOP | IT_KEY,
			0,
			null,
			0,
		/* precache */
		""),

		/*QUAKED key_blue_key (0 .5 .8) (-16 -16 -16) (16 16 16)
		normal door key - blue
		*/
		new gitem_t(
			"key_blue_key",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/keys/key/tris.md2",
			EF_ROTATE,
			null,
			"k_bluekey",
			"Blue Key",
			2,
			0,
			null,
			IT_STAY_COOP | IT_KEY,
			0,
			null,
			0,
		/* precache */
		""),

		/*QUAKED key_red_key (0 .5 .8) (-16 -16 -16) (16 16 16)
		normal door key - red
		*/
		new gitem_t(
			"key_red_key",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/keys/red_key/tris.md2",
			EF_ROTATE,
			null,
			"k_redkey",
			"Red Key",
			2,
			0,
			null,
			IT_STAY_COOP | IT_KEY,
			0,
			null,
			0,
		/* precache */
		""),

		/*QUAKED key_commander_head (0 .5 .8) (-16 -16 -16) (16 16 16)
		tank commander's head
		*/
		new gitem_t(
			"key_commander_head",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/monsters/commandr/head/tris.md2",
			EF_GIB,
			null,
		/* icon */
		"k_comhead",
		/* pickup */
		"Commander's Head",
		/* width */
		2, 0, null, IT_STAY_COOP | IT_KEY, 0, null, 0,
		/* precache */
		""),

		/*QUAKED key_airstrike_target (0 .5 .8) (-16 -16 -16) (16 16 16)
		tank commander's head
		*/
		new gitem_t(
			"key_airstrike_target",
			Pickup_Key,
			null,
			Drop_General,
			null,
			"items/pkup.wav",
			"models/items/keys/target/tris.md2",
			EF_ROTATE,
			null,
		/* icon */
		"i_airstrike",
		/* pickup */
		"Airstrike Marker",
		/* width */
		2, 0, null, IT_STAY_COOP | IT_KEY, 0, null, 0,
		/* precache */
		""), new gitem_t(null, Pickup_Health, null, null, null, "items/pkup.wav", null, 0, null,
		/* icon */
		"i_health",
		/* pickup */
		"Health",
		/* width */
		3, 0, null, 0, 0, null, 0,
		/* precache */
		"items/s_health.wav items/n_health.wav items/l_health.wav items/m_health.wav"),

		// end of list marker
		null };

}
