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
// $Id: GameAIAdapters.java,v 1.1 2004-02-26 22:36:31 rst Exp $

package jake2.game;

import jake2.Defines;
import jake2.client.M;
import jake2.util.*;

import java.util.*;

public class GameAIAdapters {


	/** Common Boss explode animation.*/
	
	public static EntThinkAdapter BossExplode = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			float[] org = { 0, 0, 0 };
	
			int n;
	
			self.think = BossExplode;
			Math3D.VectorCopy(self.s.origin, org);
			org[2] += 24 + (Lib.rand() & 15);
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
					self.s.sound = 0;
					for (n = 0; n < 4; n++)
						GameAI.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", 500, Defines.GIB_ORGANIC);
					for (n = 0; n < 8; n++)
						GameAI.ThrowGib(self, "models/objects/gibs/sm_metal/tris.md2", 500, Defines.GIB_METALLIC);
					GameAI.ThrowGib(self, "models/objects/gibs/chest/tris.md2", 500, Defines.GIB_ORGANIC);
					GameAI.ThrowHead(self, "models/objects/gibs/gear/tris.md2", 500, Defines.GIB_METALLIC);
					self.deadflag = Defines.DEAD_DEAD;
					return true;
			}
	
			GameBase.gi.WriteByte(Defines.svc_temp_entity);
			GameBase.gi.WriteByte(Defines.TE_EXPLOSION1);
			GameBase.gi.WritePosition(org);
			GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PVS);
	
			self.nextthink = GameBase.level.time + 0.1f;
			return true;
		}
	};
	public static EntThinkAdapter walkmonster_start_go = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			if (0 == (self.spawnflags & 2) && GameBase.level.time < 1) {
				M.M_droptofloor.think(self);
	
				if (self.groundentity != null)
					if (!M.M_walkmove(self, 0, 0))
						GameBase.gi.dprintf(self.classname + " in solid at " + Lib.vtos(self.s.origin) + "\n");
			}
	
			if (0 == self.yaw_speed)
				self.yaw_speed = 20;
			self.viewheight = 25;
	
			Monster.monster_start_go(self);
	
			if ((self.spawnflags & 2) != 0)
				MonsterAdapters.monster_triggered_start.think(self);
			return true;
		}
	};
	public static EntThinkAdapter walkmonster_start = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			self.think = walkmonster_start_go;
			Monster.monster_start(self);
			return true;
		}
	};
	public static EntThinkAdapter flymonster_start_go = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (!M.M_walkmove(self, 0, 0))
				GameBase.gi.dprintf(self.classname + " in solid at " + Lib.vtos(self.s.origin) + "\n");
	
			if (0 == self.yaw_speed)
				self.yaw_speed = 10;
			self.viewheight = 25;
	
			Monster.monster_start_go(self);
	
			if ((self.spawnflags & 2) != 0)
				MonsterAdapters.monster_triggered_start.think(self);
			return true;
		}
	};
	public static EntThinkAdapter flymonster_start = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.flags |= Defines.FL_FLY;
			self.think = flymonster_start_go;
			Monster.monster_start(self);
			return true;
		}
	};
	public static EntThinkAdapter swimmonster_start_go = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (0 == self.yaw_speed)
				self.yaw_speed = 10;
			self.viewheight = 10;
	
			Monster.monster_start_go(self);
	
			if ((self.spawnflags & 2) != 0)
				MonsterAdapters.monster_triggered_start.think(self);
			return true;
		}
	};
	public static EntThinkAdapter swimmonster_start = new EntThinkAdapter() {
		public boolean think(edict_t self) {
	
			{
				self.flags |= Defines.FL_SWIM;
				self.think = swimmonster_start_go;
				Monster.monster_start(self);
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
	public static AIAdapter ai_turn = new AIAdapter() {
		public void ai(edict_t self, float dist) {
			if (dist != 0)
				M.M_walkmove(self, self.s.angles[Defines.YAW], dist);
	
			if (GameUtil.FindTarget(self))
				return;
	
			M.M_ChangeYaw(self);
		}
	};
	/*
	=============
	ai_move
	
	Move the specified distance at current facing.
	This replaces the QC functions: ai_forward, ai_back, ai_pain, and ai_painforward
	==============
	*/
	public static AIAdapter ai_move = new AIAdapter() {
		public void ai(edict_t self, float dist) {
			M.M_walkmove(self, self.s.angles[Defines.YAW], dist);
		}
	};
	/*
	=============
	ai_walk
	
	The monster is walking it's beat
	=============
	*/
	public static AIAdapter ai_walk = new AIAdapter() {
		public void ai(edict_t self, float dist) {
			M.M_MoveToGoal(self, dist);
	
			// check for noticing a player
			if (GameUtil.FindTarget(self))
				return;
	
			if ((self.monsterinfo.search != null) && (GameBase.level.time > self.monsterinfo.idle_time)) {
				if (self.monsterinfo.idle_time != 0) {
					self.monsterinfo.search.think(self);
					self.monsterinfo.idle_time = GameBase.level.time + 15 + Lib.random() * 15;
				}
				else {
					self.monsterinfo.idle_time = GameBase.level.time + Lib.random() * 15;
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
	
	public static AIAdapter ai_stand = new AIAdapter() {
		public void ai(edict_t self, float dist) {
			float[] v = { 0, 0, 0 };
	
			if (dist != 0)
				M.M_walkmove(self, self.s.angles[Defines.YAW], dist);
	
			if ((self.monsterinfo.aiflags & Defines.AI_STAND_GROUND) != 0) {
				if (self.enemy != null) {
					Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, v);
					self.ideal_yaw = Math3D.vectoyaw(v);
					if (self.s.angles[Defines.YAW] != self.ideal_yaw && 0 != (self.monsterinfo.aiflags & Defines.AI_TEMP_STAND_GROUND)) {
						self.monsterinfo.aiflags &= ~(Defines.AI_STAND_GROUND | Defines.AI_TEMP_STAND_GROUND);
						self.monsterinfo.run.think(self);
					}
					M.M_ChangeYaw(self);
					GameAI.ai_checkattack(self, 0);
				}
				else
					GameUtil.FindTarget(self);
				return;
			}
	
			if (GameUtil.FindTarget(self))
				return;
	
			if (GameBase.level.time > self.monsterinfo.pausetime) {
				self.monsterinfo.walk.think(self);
				return;
			}
	
			if (0 == (self.spawnflags & 1) && (self.monsterinfo.idle != null) && (GameBase.level.time > self.monsterinfo.idle_time)) {
				if (self.monsterinfo.idle_time != 0) {
					self.monsterinfo.idle.think(self);
					self.monsterinfo.idle_time = GameBase.level.time + 15 + Lib.random() * 15;
				}
				else {
					self.monsterinfo.idle_time = GameBase.level.time + Lib.random() * 15;
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
	public static AIAdapter ai_charge = new AIAdapter() {
	
		public void ai(edict_t self, float dist) {
			float[] v = { 0, 0, 0 };
	
			Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, v);
			self.ideal_yaw = Math3D.vectoyaw(v);
			M.M_ChangeYaw(self);
	
			if (dist != 0)
				M.M_walkmove(self, self.s.angles[Defines.YAW], dist);
		}
	};
	/*
	=============
	ai_run
	
	The monster has an enemy it is trying to kill
	=============
	*/
	public static AIAdapter ai_run = new AIAdapter() {
		public void ai_run(edict_t self, float dist) {
			float[] v = { 0, 0, 0 };
	
			edict_t tempgoal;
			edict_t save;
			boolean new1;
			edict_t marker;
			float d1, d2;
			trace_t tr;
			float[] v_forward = { 0, 0, 0 }, v_right = { 0, 0, 0 };
			float left, center, right;
			float[] left_target = { 0, 0, 0 }, right_target = { 0, 0, 0 };
	
			// if we're going to a combat point, just proceed
			if ((self.monsterinfo.aiflags & Defines.AI_COMBAT_POINT) != 0) {
				M.M_MoveToGoal(self, dist);
				return;
			}
	
			if ((self.monsterinfo.aiflags & Defines.AI_SOUND_TARGET) != 0) {
				Math3D.VectorSubtract(self.s.origin, self.enemy.s.origin, v);
				if (Math3D.VectorLength(v) < 64) {
					self.monsterinfo.aiflags |= (Defines.AI_STAND_GROUND | Defines.AI_TEMP_STAND_GROUND);
					self.monsterinfo.stand.think(self);
					return;
				}
	
				M.M_MoveToGoal(self, dist);
	
				if (!GameUtil.FindTarget(self))
					return;
			}
	
			if (GameAI.ai_checkattack(self, dist))
				return;
	
			if (self.monsterinfo.attack_state == Defines.AS_SLIDING) {
				GameAI.ai_run_slide(self, dist);
				return;
			}
	
			if (GameUtilAdapters.enemy_vis) {
				//			if (self.aiflags & AI_LOST_SIGHT)
				//				dprint("regained sight\n");
				M.M_MoveToGoal(self, dist);
				self.monsterinfo.aiflags &= ~Defines.AI_LOST_SIGHT;
				Math3D.VectorCopy(self.enemy.s.origin, self.monsterinfo.last_sighting);
				self.monsterinfo.trail_time = GameBase.level.time;
				return;
			}
	
			// coop will change to another enemy if visible
			if (GameBase.coop.value != 0) {
				// FIXME: insane guys get mad with this, which causes crashes!
				if (GameUtil.FindTarget(self))
					return;
			}
	
			if ((self.monsterinfo.search_time != 0) && (GameBase.level.time > (self.monsterinfo.search_time + 20))) {
				M.M_MoveToGoal(self, dist);
				self.monsterinfo.search_time = 0;
				//			dprint("search timeout\n");
				return;
			}
	
			save = self.goalentity;
			tempgoal = GameUtil.G_Spawn();
			self.goalentity = tempgoal;
	
			new1 = false;
	
			if (0 == (self.monsterinfo.aiflags & Defines.AI_LOST_SIGHT)) {
				// just lost sight of the player, decide where to go first
				//			dprint("lost sight of player, last seen at "); dprint(vtos(self.last_sighting)); dprint("\n");
				self.monsterinfo.aiflags |= (Defines.AI_LOST_SIGHT | Defines.AI_PURSUIT_LAST_SEEN);
				self.monsterinfo.aiflags &= ~(Defines.AI_PURSUE_NEXT | Defines.AI_PURSUE_TEMP);
				new1 = true;
			}
	
			if ((self.monsterinfo.aiflags & Defines.AI_PURSUE_NEXT) != 0) {
				self.monsterinfo.aiflags &= ~Defines.AI_PURSUE_NEXT;
				//			dprint("reached current goal: "); dprint(vtos(self.origin)); dprint(" "); dprint(vtos(self.last_sighting)); dprint(" "); dprint(ftos(vlen(self.origin - self.last_sighting))); dprint("\n");
	
				// give ourself more time since we got this far
				self.monsterinfo.search_time = GameBase.level.time + 5;
	
				if ((self.monsterinfo.aiflags & Defines.AI_PURSUE_TEMP) != 0) {
					//				dprint("was temp goal; retrying original\n");
					self.monsterinfo.aiflags &= ~Defines.AI_PURSUE_TEMP;
					marker = null;
					Math3D.VectorCopy(self.monsterinfo.saved_goal, self.monsterinfo.last_sighting);
					new1 = true;
				}
				else if ((self.monsterinfo.aiflags & Defines.AI_PURSUIT_LAST_SEEN) != 0) {
					self.monsterinfo.aiflags &= ~Defines.AI_PURSUIT_LAST_SEEN;
					marker = PlayerTrail.PickFirst(self);
				}
				else {
					marker = PlayerTrail.PickNext(self);
				}
	
				if (marker != null) {
					Math3D.VectorCopy(marker.s.origin, self.monsterinfo.last_sighting);
					self.monsterinfo.trail_time = marker.timestamp;
					self.s.angles[Defines.YAW] = self.ideal_yaw = marker.s.angles[Defines.YAW];
					//				dprint("heading is "); dprint(ftos(self.ideal_yaw)); dprint("\n");
	
					//				debug_drawline(self.origin, self.last_sighting, 52);
					new1 = true;
				}
			}
	
			Math3D.VectorSubtract(self.s.origin, self.monsterinfo.last_sighting, v);
			d1 = Math3D.VectorLength(v);
			if (d1 <= dist) {
				self.monsterinfo.aiflags |= Defines.AI_PURSUE_NEXT;
				dist = d1;
			}
	
			Math3D.VectorCopy(self.monsterinfo.last_sighting, self.goalentity.s.origin);
	
			if (new1) {
				//			gi.dprintf("checking for course correction\n");
	
				tr = GameBase.gi.trace(self.s.origin, self.mins, self.maxs, self.monsterinfo.last_sighting, self, Defines.MASK_PLAYERSOLID);
				if (tr.fraction < 1) {
					Math3D.VectorSubtract(self.goalentity.s.origin, self.s.origin, v);
					d1 = Math3D.VectorLength(v);
					center = tr.fraction;
					d2 = d1 * ((center + 1) / 2);
					self.s.angles[Defines.YAW] = self.ideal_yaw = Math3D.vectoyaw(v);
					Math3D.AngleVectors(self.s.angles, v_forward, v_right, null);
	
					Math3D.VectorSet(v, d2, -16, 0);
					Math3D.G_ProjectSource(self.s.origin, v, v_forward, v_right, left_target);
					tr = GameBase.gi.trace(self.s.origin, self.mins, self.maxs, left_target, self, Defines.MASK_PLAYERSOLID);
					left = tr.fraction;
	
					Math3D.VectorSet(v, d2, 16, 0);
					Math3D.G_ProjectSource(self.s.origin, v, v_forward, v_right, right_target);
					tr = GameBase.gi.trace(self.s.origin, self.mins, self.maxs, right_target, self, Defines.MASK_PLAYERSOLID);
					right = tr.fraction;
	
					center = (d1 * center) / d2;
					if (left >= center && left > right) {
						if (left < 1) {
							Math3D.VectorSet(v, d2 * left * 0.5f, -16f, 0f);
							Math3D.G_ProjectSource(self.s.origin, v, v_forward, v_right, left_target);
							//						gi.dprintf("incomplete path, go part way and adjust again\n");
						}
						Math3D.VectorCopy(self.monsterinfo.last_sighting, self.monsterinfo.saved_goal);
						self.monsterinfo.aiflags |= Defines.AI_PURSUE_TEMP;
						Math3D.VectorCopy(left_target, self.goalentity.s.origin);
						Math3D.VectorCopy(left_target, self.monsterinfo.last_sighting);
						Math3D.VectorSubtract(self.goalentity.s.origin, self.s.origin, v);
						self.s.angles[Defines.YAW] = self.ideal_yaw = Math3D.vectoyaw(v);
						//					gi.dprintf("adjusted left\n");
						//					debug_drawline(self.origin, self.last_sighting, 152);
					}
					else if (right >= center && right > left) {
						if (right < 1) {
							Math3D.VectorSet(v, d2 * right * 0.5f, 16f, 0f);
							Math3D.G_ProjectSource(self.s.origin, v, v_forward, v_right, right_target);
							//						gi.dprintf("incomplete path, go part way and adjust again\n");
						}
						Math3D.VectorCopy(self.monsterinfo.last_sighting, self.monsterinfo.saved_goal);
						self.monsterinfo.aiflags |= Defines.AI_PURSUE_TEMP;
						Math3D.VectorCopy(right_target, self.goalentity.s.origin);
						Math3D.VectorCopy(right_target, self.monsterinfo.last_sighting);
						Math3D.VectorSubtract(self.goalentity.s.origin, self.s.origin, v);
						self.s.angles[Defines.YAW] = self.ideal_yaw = Math3D.vectoyaw(v);
						//					gi.dprintf("adjusted right\n");
						//					debug_drawline(self.origin, self.last_sighting, 152);
					}
				}
				//			else gi.dprintf("course was fine\n");
			}
	
			M.M_MoveToGoal(self, dist);
	
			GameUtil.G_FreeEdict(tempgoal);
	
			if (self != null)
				self.goalentity = save;
		}
	};
	public static EntInteractAdapter Pickup_Ammo = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			int oldcount;
			int count;
			boolean weapon;
	
			weapon = (ent.item.flags & Defines.IT_WEAPON) != 0;
			if ((weapon) && ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO) != 0)
				count = 1000;
			else if (ent.count != 0)
				count = ent.count;
			else
				count = ent.item.quantity;
	
			oldcount = other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)];
	
			if (!GameAI.Add_Ammo(other, ent.item, count))
				return false;
	
			if (weapon && 0 == oldcount) {
				if (other.client.pers.weapon != ent.item
					&& (0 == GameBase.deathmatch.value || other.client.pers.weapon == GameUtil.FindItem("blaster")))
					other.client.newweapon = ent.item;
			}
	
			if (0 == (ent.spawnflags & (Defines.DROPPED_ITEM | Defines.DROPPED_PLAYER_ITEM)) && (GameBase.deathmatch.value != 0))
				GameUtil.SetRespawn(ent, 30);
			return true;
		}
	};
	public static EntInteractAdapter Pickup_Armor = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			int old_armor_index;
			gitem_armor_t oldinfo;
			gitem_armor_t newinfo;
			int newcount;
			float salvage;
			int salvagecount;
	
			// get info on new armor
			newinfo = (gitem_armor_t) ent.item.info;
	
			old_armor_index = GameUtil.ArmorIndex(other);
	
			// handle armor shards specially
			if (ent.item.tag == Defines.ARMOR_SHARD) {
				if (0 == old_armor_index)
					other.client.pers.inventory[GameUtilAdapters.jacket_armor_index] = 2;
				else
					other.client.pers.inventory[old_armor_index] += 2;
			}
	
			// if player has no armor, just use it
			else if (0 == old_armor_index) {
				other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)] = newinfo.base_count;
			}
	
			// use the better armor
			else {
				// get info on old armor
				if (old_armor_index == GameUtilAdapters.jacket_armor_index)
					oldinfo = jacketarmor_info;
	
				else if (old_armor_index == GameUtilAdapters.combat_armor_index)
					oldinfo = combatarmor_info;
	
				else // (old_armor_index == body_armor_index)
					oldinfo = bodyarmor_info;
	
				if (newinfo.normal_protection > oldinfo.normal_protection) {
					// calc new armor values
					salvage = oldinfo.normal_protection / newinfo.normal_protection;
					salvagecount = (int) salvage * other.client.pers.inventory[old_armor_index];
					newcount = newinfo.base_count + salvagecount;
					if (newcount > newinfo.max_count)
						newcount = newinfo.max_count;
	
					// zero count of old armor so it goes away
					other.client.pers.inventory[old_armor_index] = 0;
	
					// change armor to new item with computed value
					other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)] = newcount;
				}
				else {
					// calc new armor values
					salvage = newinfo.normal_protection / oldinfo.normal_protection;
					salvagecount = (int) salvage * newinfo.base_count;
					newcount = other.client.pers.inventory[old_armor_index] + salvagecount;
					if (newcount > oldinfo.max_count)
						newcount = oldinfo.max_count;
	
					// if we're already maxed out then we don't need the new armor
					if (other.client.pers.inventory[old_armor_index] >= newcount)
						return false;
	
					// update current armor value
					other.client.pers.inventory[old_armor_index] = newcount;
				}
			}
	
			if (0 == (ent.spawnflags & Defines.DROPPED_ITEM) && (GameBase.deathmatch.value == 0))
				GameUtil.SetRespawn(ent, 20);
	
			return true;
		}
	};
	public static EntInteractAdapter Pickup_PowerArmor = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
	
			int quantity;
	
			quantity = other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)];
	
			other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;
	
			if (GameBase.deathmatch.value != 0) {
				if (0 == (ent.spawnflags & Defines.DROPPED_ITEM))
					GameUtil.SetRespawn(ent, ent.item.quantity);
				// auto-use for DM only if we didn't already have one
				if (0 == quantity)
					ent.item.use.use(other, ent.item);
			}
			return true;
		}
	};
	//	======================================================================
	
	public static EntInteractAdapter Pickup_Powerup = new EntInteractAdapter() {
	
		public boolean interact(edict_t ent, edict_t other) {
			int quantity;
	
			quantity = other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)];
			if ((GameBase.skill.value == 1 && quantity >= 2) || (GameBase.skill.value >= 2 && quantity >= 1))
				return false;
	
			if ((GameBase.coop.value != 0) && (ent.item.flags & Defines.IT_STAY_COOP) != 0 && (quantity > 0))
				return false;
	
			other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;
	
			if (GameBase.deathmatch.value != 0) {
				if (0 == (ent.spawnflags & Defines.DROPPED_ITEM))
					GameUtil.SetRespawn(ent, ent.item.quantity);
				if (((int) GameBase.dmflags.value & Defines.DF_INSTANT_ITEMS) != 0
					|| ((ent.item.use == GameUtilAdapters.Use_Quad) && 0 != (ent.spawnflags & Defines.DROPPED_PLAYER_ITEM))) {
					if ((ent.item.use == GameUtilAdapters.Use_Quad) && 0 != (ent.spawnflags & Defines.DROPPED_PLAYER_ITEM))
						GameUtilAdapters.quad_drop_timeout_hack = (int) ((ent.nextthink - GameBase.level.time) / Defines.FRAMETIME);
	
					ent.item.use.use(other, ent.item);
				}
			}
	
			return true;
		}
	};
	public static EntInteractAdapter Pickup_Adrenaline = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			if (GameBase.deathmatch.value == 0)
				other.max_health += 1;
	
			if (other.health < other.max_health)
				other.health = other.max_health;
	
			if (0 == (ent.spawnflags & Defines.DROPPED_ITEM) && (GameBase.deathmatch.value != 0))
				GameUtil.SetRespawn(ent, ent.item.quantity);
	
			return true;
	
		}
	};
	public static EntInteractAdapter Pickup_AncientHead = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			other.max_health += 2;
	
			if (0 == (ent.spawnflags & Defines.DROPPED_ITEM) && (GameBase.deathmatch.value != 0))
				GameUtil.SetRespawn(ent, ent.item.quantity);
	
			return true;
		}
	};
	public static EntInteractAdapter Pickup_Bandolier = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			gitem_t item;
			int index;
	
			if (other.client.pers.max_bullets < 250)
				other.client.pers.max_bullets = 250;
			if (other.client.pers.max_shells < 150)
				other.client.pers.max_shells = 150;
			if (other.client.pers.max_cells < 250)
				other.client.pers.max_cells = 250;
			if (other.client.pers.max_slugs < 75)
				other.client.pers.max_slugs = 75;
	
			item = GameUtil.FindItem("Bullets");
			if (item != null) {
				index = GameUtil.ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_bullets)
					other.client.pers.inventory[index] = other.client.pers.max_bullets;
			}
	
			item = GameUtil.FindItem("Shells");
			if (item != null) {
				index = GameUtil.ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_shells)
					other.client.pers.inventory[index] = other.client.pers.max_shells;
			}
	
			if (0 == (ent.spawnflags & Defines.DROPPED_ITEM) && (GameBase.deathmatch.value != 0))
				GameUtil.SetRespawn(ent, ent.item.quantity);
	
			return true;
	
		}
	};
	public static EntUseAdapter Use_Item = new EntUseAdapter() {
		public void use(edict_t ent, edict_t other, edict_t activator) {
			ent.svflags &= ~Defines.SVF_NOCLIENT;
			ent.use = null;
	
			if ((ent.spawnflags & Defines.ITEM_NO_TOUCH) != 0) {
				ent.solid = Defines.SOLID_BBOX;
				ent.touch = null;
			}
			else {
				ent.solid = Defines.SOLID_TRIGGER;
				ent.touch = GameUtilAdapters.Touch_Item;
			}
	
			GameBase.gi.linkentity(ent);
		}
	};
	/*
	================
	droptofloor
	================
	*/
	
	public static EntThinkAdapter droptofloor = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			trace_t tr;
			float[] dest = { 0, 0, 0 };
	
			float v[];
	
			v = Lib.tv(-15, -15, -15);
			Math3D.VectorCopy(v, ent.mins);
			v = Lib.tv(15, 15, 15);
			Math3D.VectorCopy(v, ent.maxs);
	
			if (ent.model != null)
				GameBase.gi.setmodel(ent, ent.model);
			else
				GameBase.gi.setmodel(ent, ent.item.world_model);
			ent.solid = Defines.SOLID_TRIGGER;
			ent.movetype = Defines.MOVETYPE_TOSS;
			ent.touch = GameUtilAdapters.Touch_Item;
	
			v = Lib.tv(0, 0, -128);
			Math3D.VectorAdd(ent.s.origin, v, dest);
	
			tr = GameBase.gi.trace(ent.s.origin, ent.mins, ent.maxs, dest, ent, Defines.MASK_SOLID);
			if (tr.startsolid) {
				GameBase.gi.dprintf("droptofloor: " + ent.classname + " startsolid at " + Lib.vtos(ent.s.origin) + "\n");
				GameUtil.G_FreeEdict(ent);
				return true;
			}
	
			Math3D.VectorCopy(tr.endpos, ent.s.origin);
	
			if (ent.team != null) {
				ent.flags &= ~Defines.FL_TEAMSLAVE;
				ent.chain = ent.teamchain;
				ent.teamchain = null;
	
				ent.svflags |= Defines.SVF_NOCLIENT;
				ent.solid = Defines.SOLID_NOT;
				if (ent == ent.teammaster) {
					ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
					ent.think = GameUtilAdapters.DoRespawn;
				}
			}
	
			if ((ent.spawnflags & Defines.ITEM_NO_TOUCH) != 0) {
				ent.solid = Defines.SOLID_BBOX;
				ent.touch = null;
				ent.s.effects &= ~Defines.EF_ROTATE;
				ent.s.renderfx &= ~Defines.RF_GLOW;
			}
	
			if ((ent.spawnflags & Defines.ITEM_TRIGGER_SPAWN) != 0) {
				ent.svflags |= Defines.SVF_NOCLIENT;
				ent.solid = Defines.SOLID_NOT;
				ent.use = Use_Item;
			}
	
			GameBase.gi.linkentity(ent);
			return true;
		}
	};
	public static EntThinkAdapter gib_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.s.frame++;
			self.nextthink = GameBase.level.time + Defines.FRAMETIME;
	
			if (self.s.frame == 10) {
				self.think = GameUtilAdapters.G_FreeEdictA;
				self.nextthink = GameBase.level.time + 8 + Lib.random() * 10;
			}
			return true;
		}
	};
	public static EntTouchAdapter gib_touch = new EntTouchAdapter() {
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf) {
			float[] normal_angles = { 0, 0, 0 }, right = { 0, 0, 0 };
	
			if (null == self.groundentity)
				return;
	
			self.touch = null;
	
			if (plane != null) {
				GameBase.gi.sound(self, Defines.CHAN_VOICE, GameBase.gi.soundindex("misc/fhit3.wav"), 1, Defines.ATTN_NORM, 0);
	
				Math3D.vectoangles(plane.normal, normal_angles);
				Math3D.AngleVectors(normal_angles, null, right, null);
				Math3D.vectoangles(right, self.s.angles);
	
				if (self.s.modelindex == GameBase.sm_meat_index) {
					self.s.frame++;
					self.think = gib_think;
					self.nextthink = GameBase.level.time + Defines.FRAMETIME;
				}
			}
		}
	};
	public static EntDieAdapter gib_die = new EntDieAdapter() {
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {
			GameUtil.G_FreeEdict(self);
		}
	};
	/*
	=================
	debris
	=================
	*/
	public static EntDieAdapter debris_die = new EntDieAdapter() {
	
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {
			GameUtil.G_FreeEdict(self);
		}
	};
	public static int player_die_i = 0;
	/*
	==================
	player_die
	==================
	*/
	static EntDieAdapter player_die = new EntDieAdapter() {
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point) {
			int n;
	
			Math3D.VectorClear(self.avelocity);
	
			self.takedamage = Defines.DAMAGE_YES;
			self.movetype = Defines.MOVETYPE_TOSS;
	
			self.s.modelindex2 = 0; // remove linked weapon model
	
			self.s.angles[0] = 0;
			self.s.angles[2] = 0;
	
			self.s.sound = 0;
			self.client.weapon_sound = 0;
	
			self.maxs[2] = -8;
	
			//		self.solid = SOLID_NOT;
			self.svflags |= Defines.SVF_DEADMONSTER;
	
			if (self.deadflag == 0) {
				self.client.respawn_time = GameBase.level.time + 1.0f;
				GameAI.LookAtKiller(self, inflictor, attacker);
				self.client.ps.pmove.pm_type = Defines.PM_DEAD;
				GameAI.ClientObituary(self, inflictor, attacker);
				GameAI.TossClientWeapon(self);
				if (GameBase.deathmatch.value != 0)
					Cmd.Help_f(self); // show scores
	
				// clear inventory
				// this is kind of ugly, but it's how we want to handle keys in coop
				for (n = 0; n < GameBase.game.num_items; n++) {
					if (GameBase.coop.value != 0 && (GameAI.itemlist[n].flags & Defines.IT_KEY) != 0)
						self.client.resp.coop_respawn.inventory[n] = self.client.pers.inventory[n];
					self.client.pers.inventory[n] = 0;
				}
			}
	
			// remove powerups
			self.client.quad_framenum = 0;
			self.client.invincible_framenum = 0;
			self.client.breather_framenum = 0;
			self.client.enviro_framenum = 0;
			self.flags &= ~Defines.FL_POWER_ARMOR;
	
			if (self.health < -40) { // gib
				GameBase.gi.sound(self, Defines.CHAN_BODY, GameBase.gi.soundindex("misc/udeath.wav"), 1, Defines.ATTN_NORM, 0);
				for (n = 0; n < 4; n++)
					GameAI.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage, Defines.GIB_ORGANIC);
				GameAI.ThrowClientHead(self, damage);
	
				self.takedamage = Defines.DAMAGE_NO;
			}
			else { // normal death
				if (self.deadflag == 0) {
	
					player_die_i = (player_die_i + 1) % 3;
					// start a death animation
					self.client.anim_priority = Defines.ANIM_DEATH;
					if ((self.client.ps.pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
						self.s.frame = M_Player.FRAME_crdeath1 - 1;
						self.client.anim_end = M_Player.FRAME_crdeath5;
					}
					else
						switch (player_die_i) {
							case 0 :
								self.s.frame = M_Player.FRAME_death101 - 1;
								self.client.anim_end = M_Player.FRAME_death106;
								break;
							case 1 :
								self.s.frame = M_Player.FRAME_death201 - 1;
								self.client.anim_end = M_Player.FRAME_death206;
								break;
							case 2 :
								self.s.frame = M_Player.FRAME_death301 - 1;
								self.client.anim_end = M_Player.FRAME_death308;
								break;
						}
	
					GameBase.gi.sound(self, Defines.CHAN_VOICE, GameBase.gi.soundindex("*death" + ((Lib.rand() % 4) + 1) + ".wav"), 1, Defines.ATTN_NORM, 0);
				}
			}
	
			self.deadflag = Defines.DEAD_DEAD;
	
			GameBase.gi.linkentity(self);
		}
	};
	public static Comparator PlayerSort = new Comparator() {
		public int compare(Object o1, Object o2) {
			int anum = ((Integer) o1).intValue();
			int bnum = ((Integer) o2).intValue();
	
			int anum1 = GameBase.game.clients[anum].ps.stats[Defines.STAT_FRAGS];
			int bnum1 = GameBase.game.clients[bnum].ps.stats[Defines.STAT_FRAGS];
	
			if (anum1 < bnum1)
				return -1;
			if (anum1 > bnum1)
				return 1;
			return 0;
		}
	};
	public static ItemUseAdapter Use_PowerArmor = new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
			int index;
	
			if ((ent.flags & Defines.FL_POWER_ARMOR) != 0) {
				ent.flags &= ~Defines.FL_POWER_ARMOR;
				GameBase.gi.sound(ent, Defines.CHAN_AUTO, GameBase.gi.soundindex("misc/power2.wav"), 1, Defines.ATTN_NORM, 0);
			}
			else {
				index = GameUtil.ITEM_INDEX(GameUtil.FindItem("cells"));
				if (0 == ent.client.pers.inventory[index]) {
					GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "No cells for power armor.\n");
					return;
				}
				ent.flags |= Defines.FL_POWER_ARMOR;
				GameBase.gi.sound(ent, Defines.CHAN_AUTO, GameBase.gi.soundindex("misc/power1.wav"), 1, Defines.ATTN_NORM, 0);
			}
		}
	};
	public static ItemDropAdapter Drop_Ammo = new ItemDropAdapter() {
		public void drop(edict_t ent, gitem_t item) {
			edict_t dropped;
			int index;
	
			index = GameUtil.ITEM_INDEX(item);
			dropped = GameUtil.Drop_Item(ent, item);
			if (ent.client.pers.inventory[index] >= item.quantity)
				dropped.count = item.quantity;
			else
				dropped.count = ent.client.pers.inventory[index];
	
			if (ent.client.pers.weapon != null
				&& ent.client.pers.weapon.tag == Defines.AMMO_GRENADES
				&& item.tag == Defines.AMMO_GRENADES
				&& ent.client.pers.inventory[index] - dropped.count <= 0) {
				GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "Can't drop current weapon\n");
				GameUtil.G_FreeEdict(dropped);
				return;
			}
	
			ent.client.pers.inventory[index] -= dropped.count;
			GameAI.ValidateSelectedItem(ent);
		}
	};
	public static ItemDropAdapter Drop_General = new ItemDropAdapter() {
		public void drop(edict_t ent, gitem_t item) {
			GameUtil.Drop_Item(ent, item);
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
			GameAI.ValidateSelectedItem(ent);
		}
	};
	public static ItemDropAdapter Drop_PowerArmor = new ItemDropAdapter() {
		public void drop(edict_t ent, gitem_t item) {
			if (0 != (ent.flags & Defines.FL_POWER_ARMOR) && (ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)] == 1))
				Use_PowerArmor.use(ent, item);
			Drop_General.drop(ent, item);
		}
	};
	public static gitem_armor_t jacketarmor_info = new gitem_armor_t(25, 50, .30f, .00f, Defines.ARMOR_JACKET);
	public static gitem_armor_t combatarmor_info = new gitem_armor_t(50, 100, .60f, .30f, Defines.ARMOR_COMBAT);
	public static gitem_armor_t bodyarmor_info = new gitem_armor_t(100, 200, .80f, .60f, Defines.ARMOR_BODY);
}
