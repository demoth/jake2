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
// $Id: GameUtilAdapters.java,v 1.1 2004-02-26 22:36:31 rst Exp $

package jake2.game;

import jake2.*;
import jake2.util.*;

public class GameUtilAdapters {

	public static EntThinkAdapter Think_Delay = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			GameUtil.G_UseTargets(ent, ent.activator);
			GameUtil.G_FreeEdict(ent);
			return true;
		}
	};
	public static EntThinkAdapter G_FreeEdictA = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			GameUtil.G_FreeEdict(ent);
			return false;
		}
	};
	static EntThinkAdapter MegaHealth_think = new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (self.owner.health > self.owner.max_health) {
				self.nextthink = GameBase.level.time + 1;
				self.owner.health -= 1;
				return false;
			}
	
			if (!((self.spawnflags & Defines.DROPPED_ITEM) != 0) && (GameBase.deathmatch.value != 0))
				GameUtil.SetRespawn(self, 20);
			else
				GameUtil.G_FreeEdict(self);
	
			return false;
		}
	};
	static EntThinkAdapter DoRespawn = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			if (ent.team != null) {
				edict_t master;
				int count;
				int choice = 0;
	
				master = ent.teammaster;
	
				// tiefe zählen
				// count the depth
				for (count = 0, ent = master; ent != null; ent = ent.chain, count++);
				
				choice = Lib.rand() % count;
	
				for (count = 0, ent = master; count < choice; ent = ent.chain, count++);
			}
	
			ent.svflags &= ~Defines.SVF_NOCLIENT;
			ent.solid = Defines.SOLID_TRIGGER;
			GameBase.gi.linkentity(ent);
	
			// send an effect
			ent.s.event = Defines.EV_ITEM_RESPAWN;
	
			return false;
		}
	};
	static EntInteractAdapter Pickup_Pack = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
	
			gitem_t item;
			int index;
	
			if (other.client.pers.max_bullets < 300)
				other.client.pers.max_bullets = 300;
			if (other.client.pers.max_shells < 200)
				other.client.pers.max_shells = 200;
			if (other.client.pers.max_rockets < 100)
				other.client.pers.max_rockets = 100;
			if (other.client.pers.max_grenades < 100)
				other.client.pers.max_grenades = 100;
			if (other.client.pers.max_cells < 300)
				other.client.pers.max_cells = 300;
			if (other.client.pers.max_slugs < 100)
				other.client.pers.max_slugs = 100;
	
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
	
			item = GameUtil.FindItem("Cells");
			if (item != null) {
				index = GameUtil.ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_cells)
					other.client.pers.inventory[index] = other.client.pers.max_cells;
			}
	
			item = GameUtil.FindItem("Grenades");
			if (item != null) {
				index = GameUtil.ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_grenades)
					other.client.pers.inventory[index] = other.client.pers.max_grenades;
			}
	
			item = GameUtil.FindItem("Rockets");
			if (item != null) {
				index = GameUtil.ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_rockets)
					other.client.pers.inventory[index] = other.client.pers.max_rockets;
			}
	
			item = GameUtil.FindItem("Slugs");
			if (item != null) {
				index = GameUtil.ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_slugs)
					other.client.pers.inventory[index] = other.client.pers.max_slugs;
			}
	
			if (0 == (ent.spawnflags & Defines.DROPPED_ITEM) && (GameBase.deathmatch.value != 0))
				GameUtil.SetRespawn(ent, ent.item.quantity);
	
			return true;
		}
	};
	final static EntInteractAdapter Pickup_Health = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
	
			if (0 == (ent.style & Defines.HEALTH_IGNORE_MAX))
				if (other.health >= other.max_health)
					return false;
	
			other.health += ent.count;
	
			if (0 == (ent.style & Defines.HEALTH_IGNORE_MAX)) {
				if (other.health > other.max_health)
					other.health = other.max_health;
			}
	
			if (0 != (ent.style & Defines.HEALTH_TIMED)) {
				ent.think = MegaHealth_think;
				ent.nextthink = GameBase.level.time + 5f;
				ent.owner = other;
				ent.flags |= Defines.FL_RESPAWN;
				ent.svflags |= Defines.SVF_NOCLIENT;
				ent.solid = Defines.SOLID_NOT;
			}
			else {
				if (!((ent.spawnflags & Defines.DROPPED_ITEM) != 0) && (GameBase.deathmatch.value != 0))
					GameUtil.SetRespawn(ent, 30);
			}
	
			return true;
		}
	
	};
	/*
		===============
		Touch_Item
		===============
	*/
	
	static EntTouchAdapter Touch_Item = new EntTouchAdapter() {
		public void touch(edict_t ent, edict_t other, cplane_t plane, csurface_t surf) {
			boolean taken;
	
			if (other.client == null)
				return;
			if (other.health < 1)
				return; // dead people can't pickup
			if (ent.item.pickup == null)
				return; // not a grabbable item?
	
			taken = ent.item.pickup.interact(ent, other);
	
			if (taken) {
				// flash the screen
				other.client.bonus_alpha = 0.25f;
	
				// show icon and name on status bar
				other.client.ps.stats[Defines.STAT_PICKUP_ICON] = (short) GameBase.gi.imageindex(ent.item.icon);
				other.client.ps.stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + GameUtil.ITEM_INDEX(ent.item));
				other.client.pickup_msg_time = GameBase.level.time + 3.0f;
	
				// change selected item
				if (ent.item.use != null)
					other.client.pers.selected_item = other.client.ps.stats[Defines.STAT_SELECTED_ITEM] = (short) GameUtil.ITEM_INDEX(ent.item);
	
				if (ent.item.pickup == Pickup_Health) {
					if (ent.count == 2)
						GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi.soundindex("items/s_health.wav"), 1, Defines.ATTN_NORM, 0);
					else if (ent.count == 10)
						GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi.soundindex("items/n_health.wav"), 1, Defines.ATTN_NORM, 0);
					else if (ent.count == 25)
						GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi.soundindex("items/l_health.wav"), 1, Defines.ATTN_NORM, 0);
					else // (ent.count == 100)
						GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi.soundindex("items/m_health.wav"), 1, Defines.ATTN_NORM, 0);
				}
				else if (ent.item.pickup_sound != null) {
					GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi.soundindex(ent.item.pickup_sound), 1, Defines.ATTN_NORM, 0);
				}
			}
	
			if (0 == (ent.spawnflags & Defines.ITEM_TARGETS_USED)) {
				GameUtil.G_UseTargets(ent, other);
				ent.spawnflags |= Defines.ITEM_TARGETS_USED;
			}
	
			if (!taken)
				return;
	
			if (!((GameBase.coop.value != 0) && (ent.item.flags & Defines.IT_STAY_COOP) != 0)
				|| 0 != (ent.spawnflags & (Defines.DROPPED_ITEM | Defines.DROPPED_PLAYER_ITEM))) {
				if ((ent.flags & Defines.FL_RESPAWN) != 0)
					ent.flags &= ~Defines.FL_RESPAWN;
				else
					GameUtil.G_FreeEdict(ent);
			}
		}
	};
	static EntTouchAdapter drop_temp_touch = new EntTouchAdapter() {
		public void touch(edict_t ent, edict_t other, cplane_t plane, csurface_t surf) {
			if (other == ent.owner)
				return;
	
			Touch_Item.touch(ent, other, plane, surf);
		}
	};
	static EntThinkAdapter drop_make_touchable = new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			ent.touch = Touch_Item;
			if (GameBase.deathmatch.value != 0) {
				ent.nextthink = GameBase.level.time + 29;
				ent.think = G_FreeEdictA;
			}
			return false;
		}
	};
	static int quad_drop_timeout_hack = 0;
	static ItemUseAdapter Use_Quad = new ItemUseAdapter() {
	
		public void use(edict_t ent, gitem_t item) {
			int timeout;
	
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
			GameUtil.ValidateSelectedItem(ent);
	
			if (quad_drop_timeout_hack != 0) {
				timeout = quad_drop_timeout_hack;
				quad_drop_timeout_hack = 0;
			}
			else {
				timeout = 300;
			}
	
			if (ent.client.quad_framenum > GameBase.level.framenum)
				ent.client.quad_framenum += timeout;
			else
				ent.client.quad_framenum = GameBase.level.framenum + timeout;
	
			GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi.soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
		}
	};
	static ItemUseAdapter Use_Invulnerability = new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
			GameUtil.ValidateSelectedItem(ent);
	
			if (ent.client.invincible_framenum > GameBase.level.framenum)
				ent.client.invincible_framenum += 300;
			else
				ent.client.invincible_framenum = GameBase.level.framenum + 300;
	
			GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi.soundindex("items/protect.wav"), 1, Defines.ATTN_NORM, 0);
		}
	};
	//	======================================================================
	
	static ItemUseAdapter Use_Breather = new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
			GameUtil.ValidateSelectedItem(ent);
	
			if (ent.client.breather_framenum > GameBase.level.framenum)
				ent.client.breather_framenum += 300;
			else
				ent.client.breather_framenum = GameBase.level.framenum + 300;
	
			//	  gi.sound(ent, CHAN_ITEM, gi.soundindex("items/damage.wav"), 1, ATTN_NORM, 0);
		}
	};
	//	======================================================================
	
	static ItemUseAdapter Use_Envirosuit = new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
			GameUtil.ValidateSelectedItem(ent);
	
			if (ent.client.enviro_framenum > GameBase.level.framenum)
				ent.client.enviro_framenum += 300;
			else
				ent.client.enviro_framenum = GameBase.level.framenum + 300;
	
			//	  gi.sound(ent, CHAN_ITEM, gi.soundindex("items/damage.wav"), 1, ATTN_NORM, 0);
		}
	};
	//	======================================================================
	/*
	static ItemUseAdapter Use_Invulnerability = new ItemUseAdapter()
	{
		public void use(edict_t ent, gitem_t item)
		{
	
			ent.client.pers.inventory[ITEM_INDEX(item)]--;
			ValidateSelectedItem(ent);
	
			if (ent.client.invincible_framenum > level.framenum)
				ent.client.invincible_framenum += 300;
			else
				ent.client.invincible_framenum = level.framenum + 300;
	
			gi.sound(ent, CHAN_ITEM, gi.soundindex("items/protect.wav"), 1, ATTN_NORM, 0);
		}
	};
	*/
	
	//	======================================================================
	
	static ItemUseAdapter Use_Silencer = new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
	
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
			GameUtil.ValidateSelectedItem(ent);
			ent.client.silencer_shots += 30;
	
			//	  gi.sound(ent, CHAN_ITEM, gi.soundindex("items/damage.wav"), 1, ATTN_NORM, 0);
		}
	};
	//	======================================================================
	
	static EntInteractAdapter Pickup_Key = new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			if (GameBase.coop.value != 0) {
				if (Lib.strcmp(ent.classname, "key_power_cube") == 0) {
					if ((other.client.pers.power_cubes & ((ent.spawnflags & 0x0000ff00) >> 8)) != 0)
						return false;
					other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;
					other.client.pers.power_cubes |= ((ent.spawnflags & 0x0000ff00) >> 8);
				}
				else {
					if (other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)] != 0)
						return false;
					other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)] = 1;
				}
				return true;
			}
			other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;
			return true;
		}
	};
	static int jacket_armor_index;
	static int combat_armor_index;
	static int body_armor_index;
	static int power_screen_index;
	static int power_shield_index;
	/*
	=============
	range
	
	returns the range catagorization of an entity reletive to self.
	0	melee range, will become hostile even if back is turned
	1	visibility and infront, or visibility and show hostile
	2	infront and show hostile
	3	only triggered by damage
	
	*/
	//	static int range(edict_t self, edict_t other)
	//	{
	//		float[] v= { 0, 0, 0 };
	//		float len;
	//
	//		VectorSubtract(self.s.origin, other.s.origin, v);
	//		len= VectorLength(v);
	//		if (len < MELEE_DISTANCE)
	//			return RANGE_MELEE;
	//		if (len < 500)
	//			return RANGE_NEAR;
	//		if (len < 1000)
	//			return RANGE_MID;
	//		return RANGE_FAR;
	//	}
	
	//	============================================================================
	
	static EntThinkAdapter M_CheckAttack = new EntThinkAdapter() {
	
		public boolean think(edict_t self) {
			float[] spot1 = { 0, 0, 0 };
	
			float[] spot2 = { 0, 0, 0 };
			float chance;
			trace_t tr;
	
			if (self.enemy.health > 0) {
				// see if any entities are in the way of the shot
				Math3D.VectorCopy(self.s.origin, spot1);
				spot1[2] += self.viewheight;
				Math3D.VectorCopy(self.enemy.s.origin, spot2);
				spot2[2] += self.enemy.viewheight;
	
				tr =
					GameBase.gi.trace(
						spot1,
						null,
						null,
						spot2,
						self,
						Defines.CONTENTS_SOLID | Defines.CONTENTS_MONSTER | Defines.CONTENTS_SLIME | Defines.CONTENTS_LAVA | Defines.CONTENTS_WINDOW);
	
				// do we have a clear shot?
				if (tr.ent != self.enemy)
					return false;
			}
	
			// melee attack
			if (enemy_range == Defines.RANGE_MELEE) {
				// don't always melee in easy mode
				if (GameBase.skill.value == 0 && (Lib.rand() & 3) != 0)
					return false;
				if (self.monsterinfo.melee != null)
					self.monsterinfo.attack_state = Defines.AS_MELEE;
				else
					self.monsterinfo.attack_state = Defines.AS_MISSILE;
				return true;
			}
	
			//					 missile attack
			if (self.monsterinfo.attack == null)
				return false;
	
			if (GameBase.level.time < self.monsterinfo.attack_finished)
				return false;
	
			if (enemy_range == Defines.RANGE_FAR)
				return false;
	
			if ((self.monsterinfo.aiflags & Defines.AI_STAND_GROUND) != 0) {
				chance = 0.4f;
			}
			else if (enemy_range == Defines.RANGE_MELEE) {
				chance = 0.2f;
			}
			else if (enemy_range == Defines.RANGE_NEAR) {
				chance = 0.1f;
			}
			else if (enemy_range == Defines.RANGE_MID) {
				chance = 0.02f;
			}
			else {
				return false;
			}
	
			if (GameBase.skill.value == 0)
				chance *= 0.5;
			else if (GameBase.skill.value >= 2)
				chance *= 2;
	
			if (Lib.random() < chance) {
				self.monsterinfo.attack_state = Defines.AS_MISSILE;
				self.monsterinfo.attack_finished = GameBase.level.time + 2 * Lib.random();
				return true;
			}
	
			if ((self.flags & Defines.FL_FLY) != 0) {
				if (Lib.random() < 0.3f)
					self.monsterinfo.attack_state = Defines.AS_SLIDING;
				else
					self.monsterinfo.attack_state = Defines.AS_STRAIGHT;
			}
	
			return false;
	
		}
	};
	static EntUseAdapter monster_use = new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			if (self.enemy != null)
				return;
			if (self.health <= 0)
				return;
			if ((activator.flags & Defines.FL_NOTARGET) != 0)
				return;
			if ((null == activator.client) && 0 == (activator.monsterinfo.aiflags & Defines.AI_GOOD_GUY))
				return;
	
			// delay reaction so if the monster is teleported, its sound is still heard
			self.enemy = activator;
			GameUtil.FoundTarget(self);
		}
	};
	static boolean enemy_vis;
	static boolean enemy_infront;
	static int enemy_range;
	static float enemy_yaw;
}
