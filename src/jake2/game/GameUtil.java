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

// Created on 01.11.2003 by RST.
// $Id: GameUtil.java,v 1.2 2003-11-29 13:28:28 rst Exp $

package jake2.game;

public class GameUtil extends GameBase {

	static EntThinkAdapter Think_Delay= new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			G_UseTargets(ent, ent.activator);
			G_FreeEdict(ent);
			return true;
		}
	};

	/**
	the global "activator" should be set to the entity that initiated the firing.
	
	If self.delay is set, a DelayedUse entity will be created that will actually
	do the SUB_UseTargets after that many seconds have passed.
	
	Centerprints any self.message to the activator.
	
	Search for (string)targetname in all entities that
	match (string)self.target and call their .use function
	*/

	static void G_UseTargets(edict_t ent, edict_t activator) {
		edict_t t;

		//
		//	   check for a delay
		//
		if (ent.delay != 0) {
			// create a temp object to fire at a later time
			t= G_Spawn();
			t.classname= "DelayedUse";
			t.nextthink= level.time + ent.delay;
			t.think= Think_Delay;
			t.activator= activator;
			if (activator == null)
				gi.dprintf("Think_Delay with no activator\n");
			t.message= ent.message;
			t.target= ent.target;
			t.killtarget= ent.killtarget;
			return;
		}

		//
		//	   print the message
		//
		if ((ent.message != null) && (activator.svflags & SVF_MONSTER) == 0) {
			gi.centerprintf(activator, "" + ent.message);
			if (ent.noise_index != 0)
				gi.sound(activator, CHAN_AUTO, ent.noise_index, 1, ATTN_NORM, 0);
			else
				gi.sound(activator, CHAN_AUTO, gi.soundindex("misc/talk1.wav"), 1, ATTN_NORM, 0);
		}

		//
		// kill killtargets
		//

		EdictIterator edit= null;

		if (ent.killtarget != null) {
			while ((edit= G_Find(edit, findByTarget, ent.killtarget)) != null) {
				t= edit.o;
				G_FreeEdict(t);
				if (!ent.inuse) {
					gi.dprintf("entity was removed while using killtargets\n");
					return;
				}
			}
		}

		// fire targets 

		if (ent.target != null) {
			edit= null;
			while ((edit= G_Find(edit, findByTarget, ent.target)) != null) {
				t= edit.o;
				// doors fire area portals in a specific way
				if (Q_stricmp(t.classname, "func_areaportal") == 0
					&& (Q_stricmp(ent.classname, "func_door") == 0
						|| Q_stricmp(ent.classname, "func_door_rotating") == 0))
					continue;

				if (t == ent) {
					gi.dprintf("WARNING: Entity used itself.\n");
				} else {
					if (t.use != null)
						t.use.use(t, ent, activator);
				}
				if (!ent.inuse) {
					gi.dprintf("entity was removed while using targets\n");
					return;
				}
			}
		}
	}



	static void G_InitEdict(edict_t e, int i) {
		e.inuse= true;
		e.classname= "noclass";
		e.gravity= 1.0f;
		//e.s.number= e - g_edicts;
		e.s.number= i;
	}

	/** 
	 * Either finds a free edict, or allocates a new one.
	 * Try to avoid reusing an entity that was recently freed, because it
	 * can cause the client to think the entity morphed into something else
	 * instead of being removed and recreated, which can cause interpolated
	 * angles and bad trails.
	*/
	static edict_t G_Spawn() {
		int i;
		edict_t e= null;

		for (i= (int) maxclients.value + 1; i < globals.num_edicts; i++) {
			e= g_edicts[i];
			// the first couple seconds of server time can involve a lot of
			// freeing and allocating, so relax the replacement policy
			if (!e.inuse && (e.freetime < 2 || level.time - e.freetime > 0.5)) {
				G_InitEdict(e, i);
				return e;
			}
		}

		if (i == game.maxentities)
			gi.error("ED_Alloc: no free edicts");

		globals.num_edicts++;
		G_InitEdict(e, i);
		return e;
	}

	static EntThinkAdapter G_FreeEdictA= new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			G_FreeEdict(ent);
			return false;
		}
	};

	/**
	 * Marks the edict as free
	*/
	static void G_FreeEdict(edict_t ed) {
		gi.unlinkentity(ed); // unlink from world

		//if ((ed - g_edicts) <= (maxclients.value + BODY_QUEUE_SIZE))
		if (ed.s.number <= (maxclients.value + BODY_QUEUE_SIZE)) {
			//			gi.dprintf("tried to free special edict\n");
			return;
		}

		//memset(ed, 0, sizeof(* ed));
		ed.clear();
		ed.classname= "freed";
		ed.freetime= level.time;
		ed.inuse= false;
	}

	/**
	 * Call after linking a new trigger in during gameplay 
	 * to force all entities it covers to immediately touch it.
	*/

	static void G_TouchSolids(edict_t ent) {
		int i, num;
		edict_t touch[]= new edict_t[MAX_EDICTS], hit;

		num= gi.BoxEdicts(ent.absmin, ent.absmax, touch, MAX_EDICTS, AREA_SOLID);

		// be careful, it is possible to have an entity in this
		// list removed before we get to it (killtriggered)
		for (i= 0; i < num; i++) {
			hit= touch[i];
			if (!hit.inuse)
				continue;
			if (ent.touch != null)
				ent.touch.touch(hit, ent, null, null);
			if (!ent.inuse)
				break;
		}
	}

	/**
	 * Kills all entities that would touch the proposed new positioning
	 * of ent.  Ent should be unlinked before calling this!
	 */

	static boolean KillBox(edict_t ent) {
		trace_t tr;

		while (true) {
			tr= gi.trace(ent.s.origin, ent.mins, ent.maxs, ent.s.origin, null, MASK_PLAYERSOLID);
			if (tr.ent == null)
				break;

			// nail it
			T_Damage(
				tr.ent,
				ent,
				ent,
				vec3_origin,
				ent.s.origin,
				vec3_origin,
				100000,
				0,
				DAMAGE_NO_PROTECTION,
				MOD_TELEFRAG);

			// if we didn't kill it, fail
			if (tr.ent.solid != 0)
				return false;
		}

		return true; // all clear
	}

	static boolean OnSameTeam(edict_t ent1, edict_t ent2) {
		if (0 == ((int) (dmflags.value) & (DF_MODELTEAMS | DF_SKINTEAMS)))
			return false;

		if (ClientTeam(ent1).equals(ClientTeam(ent2)))
			return true;
		return false;
	}

	/** TODO: test, i replaced the string operations. */
	static String ClientTeam(edict_t ent) {
		String value;

		if (ent.client == null)
			return "";

		value= Info_ValueForKey(ent.client.pers.userinfo, "skin");

		int p= value.indexOf("/");

		if (p == -1)
			return value;

		if (((int) (dmflags.value) & DF_MODELTEAMS) != 0) {
			return value.substring(0, p);
		}

		return value.substring(p + 1, value.length());
	}

	/** TODO: port it. */
	static String Info_ValueForKey(String s, String key) {
		return "";
	}

	static EntThinkAdapter MegaHealth_think= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (self.owner.health > self.owner.max_health) {
				self.nextthink= level.time + 1;
				self.owner.health -= 1;
				return false;
			}

			if (!((self.spawnflags & DROPPED_ITEM) != 0) && (deathmatch.value != 0))
				SetRespawn(self, 20);
			else
				G_FreeEdict(self);

			return false;
		}
	};

	static EntThinkAdapter DoRespawn= new EntThinkAdapter() {
		public boolean think(edict_t ent) {
			if (ent.team != null) {
				edict_t master;
				int count;
				int choice= 0;

				master= ent.teammaster;

				// tiefe zählen
				// count the depth
				for (count= 0, ent= master; ent != null; ent= ent.chain, count++)
					choice= rand() % count;

				for (count= 0, ent= master; count < choice; ent= ent.chain, count++);
			}

			ent.svflags &= ~SVF_NOCLIENT;
			ent.solid= SOLID_TRIGGER;
			gi.linkentity(ent);

			// send an effect
			ent.s.event= EV_ITEM_RESPAWN;

			return false;
		}
	};

	static void SetRespawn(edict_t ent, float delay) {
		ent.flags |= FL_RESPAWN;
		ent.svflags |= SVF_NOCLIENT;
		ent.solid= SOLID_NOT;
		ent.nextthink= level.time + delay;
		ent.think= DoRespawn;
		gi.linkentity(ent);
	}

	static EntInteractAdapter Pickup_Pack= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {

			gitem_t item;
			int index;

			if (other.client.pers.max_bullets < 300)
				other.client.pers.max_bullets= 300;
			if (other.client.pers.max_shells < 200)
				other.client.pers.max_shells= 200;
			if (other.client.pers.max_rockets < 100)
				other.client.pers.max_rockets= 100;
			if (other.client.pers.max_grenades < 100)
				other.client.pers.max_grenades= 100;
			if (other.client.pers.max_cells < 300)
				other.client.pers.max_cells= 300;
			if (other.client.pers.max_slugs < 100)
				other.client.pers.max_slugs= 100;

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

			item= FindItem("Cells");
			if (item != null) {
				index= ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_cells)
					other.client.pers.inventory[index]= other.client.pers.max_cells;
			}

			item= FindItem("Grenades");
			if (item != null) {
				index= ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_grenades)
					other.client.pers.inventory[index]= other.client.pers.max_grenades;
			}

			item= FindItem("Rockets");
			if (item != null) {
				index= ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_rockets)
					other.client.pers.inventory[index]= other.client.pers.max_rockets;
			}

			item= FindItem("Slugs");
			if (item != null) {
				index= ITEM_INDEX(item);
				other.client.pers.inventory[index] += item.quantity;
				if (other.client.pers.inventory[index] > other.client.pers.max_slugs)
					other.client.pers.inventory[index]= other.client.pers.max_slugs;
			}

			if (0 == (ent.spawnflags & DROPPED_ITEM) && (deathmatch.value != 0))
				SetRespawn(ent, ent.item.quantity);

			return true;
		}
	};

	final static EntInteractAdapter Pickup_Health= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {

			if (0 == (ent.style & HEALTH_IGNORE_MAX))
				if (other.health >= other.max_health)
					return false;

			other.health += ent.count;

			if (0 == (ent.style & HEALTH_IGNORE_MAX)) {
				if (other.health > other.max_health)
					other.health= other.max_health;
			}

			if (0 != (ent.style & HEALTH_TIMED)) {
				ent.think= MegaHealth_think;
				ent.nextthink= level.time + 5f;
				ent.owner= other;
				ent.flags |= FL_RESPAWN;
				ent.svflags |= SVF_NOCLIENT;
				ent.solid= SOLID_NOT;
			} else {
				if (!((ent.spawnflags & DROPPED_ITEM) != 0) && (deathmatch.value != 0))
					SetRespawn(ent, 30);
			}

			return true;
		}

	};

	// TODO: port it.
	static int ITEM_INDEX(gitem_t item) {
		return 0;
	}

	/*
		===============
		Touch_Item
		===============
	*/

	static EntTouchAdapter Touch_Item= new EntTouchAdapter() {
		public void touch(edict_t ent, edict_t other, cplane_t plane, csurface_t surf) {
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
				other.client.ps.stats[STAT_PICKUP_STRING]=
					(short) (CS_ITEMS + ITEM_INDEX(ent.item));
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
					gi.sound(
						other,
						CHAN_ITEM,
						gi.soundindex(ent.item.pickup_sound),
						1,
						ATTN_NORM,
						0);
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
	};

	static EntTouchAdapter drop_temp_touch= new EntTouchAdapter() {
		public void touch(edict_t ent, edict_t other, cplane_t plane, csurface_t surf) {
			if (other == ent.owner)
				return;

			Touch_Item.touch(ent, other, plane, surf);
		}
	};

	static EntThinkAdapter drop_make_touchable= new EntThinkAdapter() {
		boolean think(edict_t ent) {
			ent.touch= Touch_Item;
			if (deathmatch.value != 0) {
				ent.nextthink= level.time + 29;
				ent.think= G_FreeEdictA;
			}
			return false;
		}
	};

	static edict_t Drop_Item(edict_t ent, gitem_t item) {
		edict_t dropped;
		float[] forward= { 0, 0, 0 };
		float[] right= { 0, 0, 0 };
		float[] offset= { 0, 0, 0 };

		dropped= G_Spawn();

		dropped.classname= item.classname;
		dropped.item= item;
		dropped.spawnflags= DROPPED_ITEM;
		dropped.s.effects= item.world_model_flags;
		dropped.s.renderfx= RF_GLOW;
		VectorSet(dropped.mins, -15, -15, -15);
		VectorSet(dropped.maxs, 15, 15, 15);
		gi.setmodel(dropped, dropped.item.world_model);
		dropped.solid= SOLID_TRIGGER;
		dropped.movetype= MOVETYPE_TOSS;

		dropped.touch= drop_temp_touch;

		dropped.owner= ent;

		if (ent.client != null) {
			trace_t trace;

			AngleVectors(ent.client.v_angle, forward, right, null);
			VectorSet(offset, 24, 0, -16);
			G_ProjectSource(ent.s.origin, offset, forward, right, dropped.s.origin);
			trace=
				gi.trace(
					ent.s.origin,
					dropped.mins,
					dropped.maxs,
					dropped.s.origin,
					ent,
					CONTENTS_SOLID);
			VectorCopy(trace.endpos, dropped.s.origin);
		} else {
			AngleVectors(ent.s.angles, forward, right, null);
			VectorCopy(ent.s.origin, dropped.s.origin);
		}

		VectorScale(forward, 100, dropped.velocity);
		dropped.velocity[2]= 300;

		dropped.think= drop_make_touchable;
		dropped.nextthink= level.time + 1;

		gi.linkentity(dropped);

		return dropped;
	}

	static void ValidateSelectedItem(edict_t ent) {
		gclient_t cl;

		cl= ent.client;

		if (cl.pers.inventory[cl.pers.selected_item] != 0)
			return; // valid

		GameAI.SelectNextItem(ent, -1);
	}

	static int quad_drop_timeout_hack= 0;

	static ItemUseAdapter Use_Quad= new ItemUseAdapter() {

		public void use(edict_t ent, gitem_t item) {
			int timeout;

			ent.client.pers.inventory[ITEM_INDEX(item)]--;
			ValidateSelectedItem(ent);

			if (quad_drop_timeout_hack != 0) {
				timeout= quad_drop_timeout_hack;
				quad_drop_timeout_hack= 0;
			} else {
				timeout= 300;
			}

			if (ent.client.quad_framenum > level.framenum)
				ent.client.quad_framenum += timeout;
			else
				ent.client.quad_framenum= level.framenum + timeout;

			gi.sound(ent, CHAN_ITEM, gi.soundindex("items/damage.wav"), 1, ATTN_NORM, 0);
		}
	};

	static ItemUseAdapter Use_Invulnerability= new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
			ent.client.pers.inventory[ITEM_INDEX(item)]--;
			ValidateSelectedItem(ent);

			if (ent.client.invincible_framenum > level.framenum)
				ent.client.invincible_framenum += 300;
			else
				ent.client.invincible_framenum= level.framenum + 300;

			gi.sound(ent, CHAN_ITEM, gi.soundindex("items/protect.wav"), 1, ATTN_NORM, 0);
		}
	};

	static void Use_Item(edict_t ent, edict_t other, edict_t activator) {
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

	//	======================================================================

	static ItemUseAdapter Use_Breather= new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
			ent.client.pers.inventory[ITEM_INDEX(item)]--;
			ValidateSelectedItem(ent);

			if (ent.client.breather_framenum > level.framenum)
				ent.client.breather_framenum += 300;
			else
				ent.client.breather_framenum= level.framenum + 300;

			//	  gi.sound(ent, CHAN_ITEM, gi.soundindex("items/damage.wav"), 1, ATTN_NORM, 0);
		}
	};

	//	======================================================================

	static ItemUseAdapter Use_Envirosuit= new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {
			ent.client.pers.inventory[ITEM_INDEX(item)]--;
			ValidateSelectedItem(ent);

			if (ent.client.enviro_framenum > level.framenum)
				ent.client.enviro_framenum += 300;
			else
				ent.client.enviro_framenum= level.framenum + 300;

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

	static ItemUseAdapter Use_Silencer= new ItemUseAdapter() {
		public void use(edict_t ent, gitem_t item) {

			ent.client.pers.inventory[ITEM_INDEX(item)]--;
			ValidateSelectedItem(ent);
			ent.client.silencer_shots += 30;

			//	  gi.sound(ent, CHAN_ITEM, gi.soundindex("items/damage.wav"), 1, ATTN_NORM, 0);
		}
	};

	//	======================================================================

	static EntInteractAdapter Pickup_Key= new EntInteractAdapter() {
		public boolean interact(edict_t ent, edict_t other) {
			if (coop.value != 0) {
				if (strcmp(ent.classname, "key_power_cube") == 0) {
					if ((other.client.pers.power_cubes & ((ent.spawnflags & 0x0000ff00) >> 8))
						!= 0)
						return false;
					other.client.pers.inventory[ITEM_INDEX(ent.item)]++;
					other.client.pers.power_cubes |= ((ent.spawnflags & 0x0000ff00) >> 8);
				} else {
					if (other.client.pers.inventory[ITEM_INDEX(ent.item)] != 0)
						return false;
					other.client.pers.inventory[ITEM_INDEX(ent.item)]= 1;
				}
				return true;
			}
			other.client.pers.inventory[ITEM_INDEX(ent.item)]++;
			return true;
		}
	};

	/*
	============
	CanDamage
	
	Returns true if the inflictor can directly damage the target.  Used for
	explosions and melee attacks.
	============
	*/
	static boolean CanDamage(edict_t targ, edict_t inflictor) {
		float[] dest= { 0, 0, 0 };
		trace_t trace;

		// bmodels need special checking because their origin is 0,0,0
		if (targ.movetype == MOVETYPE_PUSH) {
			VectorAdd(targ.absmin, targ.absmax, dest);
			VectorScale(dest, 0.5f, dest);
			trace=
				gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
			if (trace.fraction == 1.0f)
				return true;
			if (trace.ent == targ)
				return true;
			return false;
		}

		trace=
			gi.trace(
				inflictor.s.origin,
				vec3_origin,
				vec3_origin,
				targ.s.origin,
				inflictor,
				MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		VectorCopy(targ.s.origin, dest);
		dest[0] += 15.0;
		dest[1] += 15.0;
		trace= gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		VectorCopy(targ.s.origin, dest);
		dest[0] += 15.0;
		dest[1] -= 15.0;
		trace= gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		VectorCopy(targ.s.origin, dest);
		dest[0] -= 15.0;
		dest[1] += 15.0;
		trace= gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		VectorCopy(targ.s.origin, dest);
		dest[0] -= 15.0;
		dest[1] -= 15.0;
		trace= gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		return false;
	}

	static void T_Damage(
		edict_t targ,
		edict_t inflictor,
		edict_t attacker,
		float[] dir,
		float[] point,
		float[] normal,
		int damage,
		int knockback,
		int dflags,
		int mod) {
		gclient_t client;
		int take;
		int save;
		int asave;
		int psave;
		int te_sparks;

		if (targ.takedamage != 0)
			return;

		// friendly fire avoidance
		// if enabled you can't hurt teammates (but you can hurt yourself)
		// knockback still occurs
		if ((targ != attacker)
			&& ((deathmatch.value != 0
				&& 0 != ((int) (dmflags.value) & (DF_MODELTEAMS | DF_SKINTEAMS)))
				|| coop.value != 0)) {
			if (OnSameTeam(targ, attacker)) {
				if (((int) (dmflags.value) & DF_NO_FRIENDLY_FIRE) != 0)
					damage= 0;
				else
					mod |= MOD_FRIENDLY_FIRE;
			}
		}
		meansOfDeath= mod;

		// easy mode takes half damage
		if (skill.value == 0 && deathmatch.value == 0 && targ.client != null) {
			damage *= 0.5;
			if (damage == 0)
				damage= 1;
		}

		client= targ.client;

		if ((dflags & DAMAGE_BULLET) != 0)
			te_sparks= TE_BULLET_SPARKS;
		else
			te_sparks= TE_SPARKS;

		VectorNormalize(dir);

		//	   bonus damage for suprising a monster
		if (0 == (dflags & DAMAGE_RADIUS)
			&& (targ.svflags & SVF_MONSTER) != 0
			&& (attacker.client != null)
			&& (targ.enemy == null)
			&& (targ.health > 0))
			damage *= 2;

		if ((targ.flags & FL_NO_KNOCKBACK) != 0)
			knockback= 0;

		//	   figure momentum add
		if (0 == (dflags & DAMAGE_NO_KNOCKBACK)) {
			if ((knockback != 0)
				&& (targ.movetype != MOVETYPE_NONE)
				&& (targ.movetype != MOVETYPE_BOUNCE)
				&& (targ.movetype != MOVETYPE_PUSH)
				&& (targ.movetype != MOVETYPE_STOP)) {
				float[] kvel= { 0, 0, 0 };
				float mass;

				if (targ.mass < 50)
					mass= 50;
				else
					mass= targ.mass;

				if (targ.client != null && attacker == targ)
					VectorScale(dir, 1600.0f * (float) knockback / mass, kvel);
				// the rocket jump hack...
				else
					VectorScale(dir, 500.0f * (float) knockback / mass, kvel);

				VectorAdd(targ.velocity, kvel, targ.velocity);
			}
		}

		take= damage;
		save= 0;

		// check for godmode
		if ((targ.flags & FL_GODMODE) != 0 && 0 == (dflags & DAMAGE_NO_PROTECTION)) {
			take= 0;
			save= damage;
			SpawnDamage(te_sparks, point, normal, save);
		}

		// check for invincibility
		if ((client != null && client.invincible_framenum > level.framenum)
			&& 0 == (dflags & DAMAGE_NO_PROTECTION)) {
			if (targ.pain_debounce_time < level.time) {
				gi.sound(targ, CHAN_ITEM, gi.soundindex("items/protect4.wav"), 1, ATTN_NORM, 0);
				targ.pain_debounce_time= level.time + 2;
			}
			take= 0;
			save= damage;
		}

		psave= CheckPowerArmor(targ, point, normal, take, dflags);
		take -= psave;

		asave= CheckArmor(targ, point, normal, take, te_sparks, dflags);
		take -= asave;

		// treat cheat/powerup savings the same as armor
		asave += save;

		// team damage avoidance
		if (0 == (dflags & DAMAGE_NO_PROTECTION) && CheckTeamDamage(targ, attacker))
			return;

		// do the damage
		if (take != 0) {
			if (0 != (targ.svflags & SVF_MONSTER) || (client != null))
				SpawnDamage(TE_BLOOD, point, normal, take);
			else
				SpawnDamage(te_sparks, point, normal, take);

			targ.health= targ.health - take;

			if (targ.health <= 0) {
				if ((targ.svflags & SVF_MONSTER) != 0 || (client != null))
					targ.flags |= FL_NO_KNOCKBACK;
				Killed(targ, inflictor, attacker, take, point);
				return;
			}
		}

		if ((targ.svflags & SVF_MONSTER) != 0) {
			M_ReactToDamage(targ, attacker);
			if (0 != (targ.monsterinfo.aiflags & AI_DUCKED) && (take != 0)) {
				targ.pain.pain(targ, attacker, knockback, take);
				// nightmare mode monsters don't go into pain frames often
				if (skill.value == 3)
					targ.pain_debounce_time= level.time + 5;
			}
		} else if (client != null) {
			if (((targ.flags & FL_GODMODE) == 0) && (take != 0))
				targ.pain.pain(targ, attacker, knockback, take);
		} else if (take != 0) {
			if (targ.pain != null)
				targ.pain.pain(targ, attacker, knockback, take);
		}

		// add to the damage inflicted on a player this frame
		// the total will be turned into screen blends and view angle kicks
		// at the end of the frame
		if (client != null) {
			client.damage_parmor += psave;
			client.damage_armor += asave;
			client.damage_blood += take;
			client.damage_knockback += knockback;
			VectorCopy(point, client.damage_from);
		}
	}

	/*
	============
	Killed
	============
	*/
	static void Killed(
		edict_t targ,
		edict_t inflictor,
		edict_t attacker,
		int damage,
		float[] point) {
		if (targ.health < -999)
			targ.health= -999;

		targ.enemy= attacker;

		if ((targ.svflags & SVF_MONSTER) != 0 && (targ.deadflag != DEAD_DEAD)) {
			//			targ.svflags |= SVF_DEADMONSTER;	// now treat as a different content type
			if (0 == (targ.monsterinfo.aiflags & AI_GOOD_GUY)) {
				level.killed_monsters++;
				if (!(coop.value != 0 && attacker.client != null))
					attacker.client.resp.score++;
				// medics won't heal monsters that they kill themselves
				if (attacker.classname.equals("monster_medic"))
					targ.owner= attacker;
			}
		}

		if (targ.movetype == MOVETYPE_PUSH
			|| targ.movetype == MOVETYPE_STOP
			|| targ.movetype == MOVETYPE_NONE) { // doors, triggers, etc
			targ.die.die(targ, inflictor, attacker, damage, point);
			return;
		}

		if ((targ.svflags & SVF_MONSTER) != 0 && (targ.deadflag != DEAD_DEAD)) {
			targ.touch= null;
			monster_death_use(targ);
		}

		targ.die.die(targ, inflictor, attacker, damage, point);
	}

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

	/*
	================
	SpawnDamage
	================
	*/
	static void SpawnDamage(int type, float[] origin, float[] normal, int damage) {
		if (damage > 255)
			damage= 255;
		gi.WriteByte(svc_temp_entity);
		gi.WriteByte(type);
		//		gi.WriteByte (damage);
		gi.WritePosition(origin);
		gi.WriteDir(normal);
		gi.multicast(origin, MULTICAST_PVS);
	}

	static int PowerArmorType(edict_t ent) {
		if (ent.client == null)
			return POWER_ARMOR_NONE;

		if (0 == (ent.flags & FL_POWER_ARMOR))
			return POWER_ARMOR_NONE;

		if (ent.client.pers.inventory[power_shield_index] > 0)
			return POWER_ARMOR_SHIELD;

		if (ent.client.pers.inventory[power_screen_index] > 0)
			return POWER_ARMOR_SCREEN;

		return POWER_ARMOR_NONE;
	}

	static int jacket_armor_index;
	static int combat_armor_index;
	static int body_armor_index;
	static int power_screen_index;
	static int power_shield_index;

	static int CheckPowerArmor(
		edict_t ent,
		float[] point,
		float[] normal,
		int damage,
		int dflags) {
		gclient_t client;
		int save;
		int power_armor_type;
		int index= 0;
		int damagePerCell;
		int pa_te_type;
		int power= 0;
		int power_used;

		if (damage != 0)
			return 0;

		client= ent.client;

		if ((dflags & DAMAGE_NO_ARMOR) != 0)
			return 0;

		if (client != null) {
			power_armor_type= PowerArmorType(ent);
			if (power_armor_type != POWER_ARMOR_NONE) {
				index= ITEM_INDEX(FindItem("Cells"));
				power= client.pers.inventory[index];
			}
		} else if ((ent.svflags & SVF_MONSTER) != 0) {
			power_armor_type= ent.monsterinfo.power_armor_type;
			power= ent.monsterinfo.power_armor_power;
		} else
			return 0;

		if (power_armor_type == POWER_ARMOR_NONE)
			return 0;
		if (power == 0)
			return 0;

		if (power_armor_type == POWER_ARMOR_SCREEN) {
			float[] vec= { 0, 0, 0 };
			float dot;
			float[] forward= { 0, 0, 0 };

			// only works if damage point is in front
			AngleVectors(ent.s.angles, forward, null, null);
			VectorSubtract(point, ent.s.origin, vec);
			VectorNormalize(vec);
			dot= DotProduct(vec, forward);
			if (dot <= 0.3)
				return 0;

			damagePerCell= 1;
			pa_te_type= TE_SCREEN_SPARKS;
			damage= damage / 3;
		} else {
			damagePerCell= 2;
			pa_te_type= TE_SHIELD_SPARKS;
			damage= (2 * damage) / 3;
		}

		save= power * damagePerCell;

		if (save == 0)
			return 0;
		if (save > damage)
			save= damage;

		SpawnDamage(pa_te_type, point, normal, save);
		ent.powerarmor_time= level.time + 0.2f;

		power_used= save / damagePerCell;

		if (client != null)
			client.pers.inventory[index] -= power_used;
		else
			ent.monsterinfo.power_armor_power -= power_used;
		return save;
	}

	/**
	 * The monster is walking it's beat.
	 * 
	 */
	static void ai_walk(edict_t self, float dist) {
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

	static EntThinkAdapter M_CheckAttack= new EntThinkAdapter() {

		boolean think(edict_t self) {
			float[] spot1= { 0, 0, 0 };

			float[] spot2= { 0, 0, 0 };
			float chance;
			trace_t tr;

			if (self.enemy.health > 0) {
				// see if any entities are in the way of the shot
				VectorCopy(self.s.origin, spot1);
				spot1[2] += self.viewheight;
				VectorCopy(self.enemy.s.origin, spot2);
				spot2[2] += self.enemy.viewheight;

				tr=
					gi.trace(
						spot1,
						null,
						null,
						spot2,
						self,
						CONTENTS_SOLID
							| CONTENTS_MONSTER
							| CONTENTS_SLIME
							| CONTENTS_LAVA
							| CONTENTS_WINDOW);

				// do we have a clear shot?
				if (tr.ent != self.enemy)
					return false;
			}

			// melee attack
			if (enemy_range == RANGE_MELEE) {
				// don't always melee in easy mode
				if (skill.value == 0 && (rand() & 3) != 0)
					return false;
				if (self.monsterinfo.melee != null)
					self.monsterinfo.attack_state= AS_MELEE;
				else
					self.monsterinfo.attack_state= AS_MISSILE;
				return true;
			}

			//					 missile attack
			if (self.monsterinfo.attack == null)
				return false;

			if (level.time < self.monsterinfo.attack_finished)
				return false;

			if (enemy_range == RANGE_FAR)
				return false;

			if ((self.monsterinfo.aiflags & AI_STAND_GROUND) != 0) {
				chance= 0.4f;
			} else if (enemy_range == RANGE_MELEE) {
				chance= 0.2f;
			} else if (enemy_range == RANGE_NEAR) {
				chance= 0.1f;
			} else if (enemy_range == RANGE_MID) {
				chance= 0.02f;
			} else {
				return false;
			}

			if (skill.value == 0)
				chance *= 0.5;
			else if (skill.value >= 2)
				chance *= 2;

			if (random() < chance) {
				self.monsterinfo.attack_state= AS_MISSILE;
				self.monsterinfo.attack_finished= level.time + 2 * random();
				return true;
			}

			if ((self.flags & FL_FLY) != 0) {
				if (random() < 0.3f)
					self.monsterinfo.attack_state= AS_SLIDING;
				else
					self.monsterinfo.attack_state= AS_STRAIGHT;
			}

			return false;

		}
	};

	static EntUseAdapter monster_use= new EntUseAdapter() {
		public void use(edict_t self, edict_t other, edict_t activator) {
			if (self.enemy != null)
				return;
			if (self.health <= 0)
				return;
			if ((activator.flags & FL_NOTARGET) != 0)
				return;
			if ((null == activator.client) && 0 == (activator.monsterinfo.aiflags & AI_GOOD_GUY))
				return;

			// delay reaction so if the monster is teleported, its sound is still heard
			self.enemy= activator;
			FoundTarget(self);
		}
	};

	static boolean monster_start(edict_t self) {
		if (deathmatch.value != 0) {
			G_FreeEdict(self);
			return false;
		}

		if ((self.spawnflags & 4) != 0 && 0 == (self.monsterinfo.aiflags & AI_GOOD_GUY)) {
			self.spawnflags &= ~4;
			self.spawnflags |= 1;
			//		  gi.dprintf("fixed spawnflags on %s at %s\n", self.classname, vtos(self.s.origin));
		}

		if (0 == (self.monsterinfo.aiflags & AI_GOOD_GUY))
			level.total_monsters++;

		self.nextthink= level.time + FRAMETIME;
		self.svflags |= SVF_MONSTER;
		self.s.renderfx |= RF_FRAMELERP;
		self.takedamage= DAMAGE_AIM;
		self.air_finished= level.time + 12;

		// monster_use()
		self.use= monster_use;

		self.max_health= self.health;
		self.clipmask= MASK_MONSTERSOLID;

		self.s.skinnum= 0;
		self.deadflag= DEAD_NO;
		self.svflags &= ~SVF_DEADMONSTER;

		if (self.monsterinfo.checkattack == null)
			//	M_CheckAttack;
			self.monsterinfo.checkattack= M_CheckAttack;

		VectorCopy(self.s.origin, self.s.old_origin);

		if (st.item != null) {
			self.item= FindItemByClassname(st.item);
			if (self.item == null)
				gi.dprintf(
					""
						+ self.classname
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

	/*
	=============
	range
	
	returns the range catagorization of an entity reletive to self
	0	melee range, will become hostile even if back is turned
	1	visibility and infront, or visibility and show hostile
	2	infront and show hostile
	3	only triggered by damage
	=============
	*/
	static int range(edict_t self, edict_t other) {
		float[] v= { 0, 0, 0 };
		float len;

		VectorSubtract(self.s.origin, other.s.origin, v);
		len= VectorLength(v);
		if (len < MELEE_DISTANCE)
			return RANGE_MELEE;
		if (len < 500)
			return RANGE_NEAR;
		if (len < 1000)
			return RANGE_MID;
		return RANGE_FAR;
	}

	/*
	===============
	FindItemByClassname
	
	===============
	*/
	static gitem_t FindItemByClassname(String classname) {

		for (int i= 0; i < game.num_items; i++) {
			gitem_t it= GameAI.itemlist[i];

			if (it.classname == null)
				continue;
			if (it.classname.equalsIgnoreCase(classname))
				return it;
		}

		return null;
	}

	/*
	===============
	FindItem
	
	===============
	*/
	static gitem_t FindItem(String pickup_name) {
		for (int i= 0; i < game.num_items; i++) {
			gitem_t it= GameAI.itemlist[i];

			if (it.pickup_name == null)
				continue;
			if (it.pickup_name.equalsIgnoreCase(pickup_name))
				return it;
		}
		return null;
	}

	static int ArmorIndex(edict_t ent) {
		if (ent.client == null)
			return 0;

		if (ent.client.pers.inventory[jacket_armor_index] > 0)
			return jacket_armor_index;

		if (ent.client.pers.inventory[combat_armor_index] > 0)
			return combat_armor_index;

		if (ent.client.pers.inventory[body_armor_index] > 0)
			return body_armor_index;

		return 0;
	}

	static int CheckArmor(
		edict_t ent,
		float[] point,
		float[] normal,
		int damage,
		int te_sparks,
		int dflags) {
		gclient_t client;
		int save;
		int index;
		gitem_t armor;

		if (damage == 0)
			return 0;

		client= ent.client;

		if (client != null)
			return 0;

		if ((dflags & DAMAGE_NO_ARMOR) != 0)
			return 0;

		index= ArmorIndex(ent);

		if (index != 0)
			return 0;

		armor= GameAI.GetItemByIndex(index);
		gitem_armor_t garmor= (gitem_armor_t) armor.info;

		if (0 != (dflags & DAMAGE_ENERGY))
			save= (int) Math.ceil(garmor.energy_protection * damage);
		else
			save= (int) Math.ceil(garmor.normal_protection * damage);

		if (save >= client.pers.inventory[index])
			save= client.pers.inventory[index];

		if (save == 0)
			return 0;

		client.pers.inventory[index] -= save;
		SpawnDamage(te_sparks, point, normal, save);

		return save;
	}

	static boolean enemy_vis;
	static boolean enemy_infront;
	static int enemy_range;
	static float enemy_yaw;

	static void AttackFinished(edict_t self, float time) {
		self.monsterinfo.attack_finished= level.time + time;
	}

	/*
	=============
	infront
	
	returns true if the entity is in front (in sight) of self
	=============
	*/
	static boolean infront(edict_t self, edict_t other) {
		float[] vec= { 0, 0, 0 };
		float dot;
		float[] forward= { 0, 0, 0 };

		AngleVectors(self.s.angles, forward, null, null);
		VectorSubtract(other.s.origin, self.s.origin, vec);
		VectorNormalize(vec);
		dot= DotProduct(vec, forward);

		if (dot > 0.3)
			return true;
		return false;
	}

	/*
	=============
	visible
	
	returns 1 if the entity is visible to self, even if not infront ()
	=============
	*/
	static boolean visible(edict_t self, edict_t other) {
		float[] spot1= { 0, 0, 0 };
		float[] spot2= { 0, 0, 0 };
		trace_t trace;

		VectorCopy(self.s.origin, spot1);
		spot1[2] += self.viewheight;
		VectorCopy(other.s.origin, spot2);
		spot2[2] += other.viewheight;
		trace= gi.trace(spot1, vec3_origin, vec3_origin, spot2, self, MASK_OPAQUE);

		if (trace.fraction == 1.0)
			return true;
		return false;
	}

	/*
	=================
	AI_SetSightClient
	
	Called once each frame to set level.sight_client to the
	player to be checked for in findtarget.
	
	If all clients are either dead or in notarget, sight_client
	will be null.
	
	In coop games, sight_client will cycle between the clients.
	=================
	*/
	static void AI_SetSightClient() {
		edict_t ent;
		int start, check;

		if (level.sight_client == null)
			start= 1;
		else
			start= level.sight_client.s.number;

		check= start;
		while (true) {
			check++;
			if (check > game.maxclients)
				check= 1;
			ent= g_edicts[check];

			if (ent.inuse && ent.health > 0 && (ent.flags & FL_NOTARGET) == 0) {
				level.sight_client= ent;
				return; // got one
			}
			if (check == start) {
				level.sight_client= null;
				return; // nobody to see
			}
		}
	}

	/*
	=============
	ai_move
	
	Move the specified distance at current facing.
	This replaces the QC functions: ai_forward, ai_back, ai_pain, and ai_painforward
	==============
	*/
	static void ai_move(edict_t self, float dist) {
		M_walkmove(self, self.s.angles[YAW], dist);
	}

	/*
	===========
	FindTarget
	
	Self is currently not attacking anything, so try to find a target
	
	Returns TRUE if an enemy was sighted
	
	When a player fires a missile, the point of impact becomes a fakeplayer so
	that monsters that see the impact will respond as if they had seen the
	player.
	
	To avoid spending too much time, only a single client (or fakeclient) is
	checked each frame.  This means multi player games will have slightly
	slower noticing monsters.
	============
	*/
	static boolean FindTarget(edict_t self) {
		edict_t client;
		boolean heardit;
		int r;

		if ((self.monsterinfo.aiflags & AI_GOOD_GUY) != 0) {
			if (self.goalentity != null
				&& self.goalentity.inuse
				&& self.goalentity.classname != null) {
				if (self.goalentity.classname.equals("target_actor"))
					return false;
			}

			//FIXME look for monsters?
			return false;
		}

		// if we're going to a combat point, just proceed
		if ((self.monsterinfo.aiflags & AI_COMBAT_POINT) != 0)
			return false;

		//	   if the first spawnflag bit is set, the monster will only wake up on
		//	   really seeing the player, not another monster getting angry or hearing
		//	   something

		//	   revised behavior so they will wake up if they "see" a player make a noise
		//	   but not weapon impact/explosion noises

		heardit= false;
		if ((level.sight_entity_framenum >= (level.framenum - 1)) && 0 == (self.spawnflags & 1)) {
			client= level.sight_entity;
			if (client.enemy == self.enemy) {
				return false;
			}
		} else if (level.sound_entity_framenum >= (level.framenum - 1)) {
			client= level.sound_entity;
			heardit= true;
		} else if (
			null != (self.enemy)
				&& (level.sound2_entity_framenum >= (level.framenum - 1))
				&& 0 != (self.spawnflags & 1)) {
			client= level.sound2_entity;
			heardit= true;
		} else {
			client= level.sight_client;
			if (client == null)
				return false; // no clients to get mad at
		}

		// if the entity went away, forget it
		if (!client.inuse)
			return false;

		if (client == self.enemy)
			return true; // JDC false;

		if (client.client != null) {
			if ((client.flags & FL_NOTARGET) != 0)
				return false;
		} else if ((client.svflags & SVF_MONSTER) != 0) {
			if (client.enemy == null)
				return false;
			if ((client.enemy.flags & FL_NOTARGET) != 0)
				return false;
		} else if (heardit) {
			if ((client.owner.flags & FL_NOTARGET) != 0)
				return false;
		} else
			return false;

		if (!heardit) {
			r= range(self, client);

			if (r == RANGE_FAR)
				return false;

			//	   this is where we would check invisibility

			// is client in an spot too dark to be seen?
			if (client.light_level <= 5)
				return false;

			if (!visible(self, client)) {
				return false;
			}

			if (r == RANGE_NEAR) {
				if (client.show_hostile < level.time && !infront(self, client)) {
					return false;
				}
			} else if (r == RANGE_MID) {
				if (!infront(self, client)) {
					return false;
				}
			}

			self.enemy= client;

			if (!self.enemy.classname.equals("player_noise")) {
				self.monsterinfo.aiflags &= ~AI_SOUND_TARGET;

				if (self.enemy.client == null) {
					self.enemy= self.enemy.enemy;
					if (self.enemy.client == null) {
						self.enemy= null;
						return false;
					}
				}
			}
		} else // heardit
			{
			float[] temp= { 0, 0, 0 };

			if ((self.spawnflags & 1) != 0) {
				if (!visible(self, client))
					return false;
			} else {
				if (!gi.inPHS(self.s.origin, client.s.origin))
					return false;
			}

			VectorSubtract(client.s.origin, self.s.origin, temp);

			if (VectorLength(temp) > 1000) // too far to hear
				{
				return false;
			}

			// check area portals - if they are different and not connected then we can't hear it
			if (client.areanum != self.areanum)
				if (!gi.AreasConnected(self.areanum, client.areanum))
					return false;

			self.ideal_yaw= vectoyaw(temp);
			M_ChangeYaw(self);

			// hunt the sound for a bit; hopefully find the real player
			self.monsterinfo.aiflags |= AI_SOUND_TARGET;
			self.enemy= client;
		}

		//
		//	   got one
		//
		FoundTarget(self);

		if (0 == (self.monsterinfo.aiflags & AI_SOUND_TARGET) && (self.monsterinfo.sight != null))
			self.monsterinfo.sight.interact(self, self.enemy);

		return true;
	}

	//	============================================================================

	static void HuntTarget(edict_t self) {
		float[] vec= { 0, 0, 0 };

		self.goalentity= self.enemy;
		if ((self.monsterinfo.aiflags & AI_STAND_GROUND) != 0)
			self.monsterinfo.stand.think(self);
		else
			self.monsterinfo.run.think(self);
		VectorSubtract(self.enemy.s.origin, self.s.origin, vec);
		self.ideal_yaw= vectoyaw(vec);
		// wait a while before first attack
		if (0 == (self.monsterinfo.aiflags & AI_STAND_GROUND))
			AttackFinished(self, 1);
	}

	static void FoundTarget(edict_t self) {
		// let other monsters see this monster for a while
		if (self.enemy.client != null) {
			level.sight_entity= self;
			level.sight_entity_framenum= level.framenum;
			level.sight_entity.light_level= 128;
		}

		self.show_hostile= (int) level.time + 1; // wake up other monsters

		VectorCopy(self.enemy.s.origin, self.monsterinfo.last_sighting);
		self.monsterinfo.trail_time= level.time;

		if (self.combattarget == null) {
			HuntTarget(self);
			return;
		}

		self.goalentity= self.movetarget= G_PickTarget(self.combattarget);
		if (self.movetarget == null) {
			self.goalentity= self.movetarget= self.enemy;
			HuntTarget(self);
			gi.dprintf(
				""
					+ self.classname
					+ "at "
					+ vtos(self.s.origin)
					+ ", combattarget "
					+ self.combattarget
					+ " not found\n");
			return;
		}

		// clear out our combattarget, these are a one shot deal
		self.combattarget= null;
		self.monsterinfo.aiflags |= AI_COMBAT_POINT;

		// clear the targetname, that point is ours!
		self.movetarget.targetname= null;
		self.monsterinfo.pausetime= 0;

		// run for it
		self.monsterinfo.run.think(self);
	}



	static void M_ReactToDamage(edict_t targ, edict_t attacker) {
		if ((null != attacker.client) && 0 != (attacker.svflags & SVF_MONSTER))
			return;

		if (attacker == targ || attacker == targ.enemy)
			return;

		// if we are a good guy monster and our attacker is a player
		// or another good guy, do not get mad at them
		if (0 != (targ.monsterinfo.aiflags & AI_GOOD_GUY)) {
			if (attacker.client != null || (attacker.monsterinfo.aiflags & AI_GOOD_GUY) != 0)
				return;
		}

		// we now know that we are not both good guys

		// if attacker is a client, get mad at them because he's good and we're not
		if (attacker.client != null) {
			targ.monsterinfo.aiflags &= ~AI_SOUND_TARGET;

			// this can only happen in coop (both new and old enemies are clients)
			// only switch if can't see the current enemy
			if (targ.enemy != null && targ.enemy.client != null) {
				if (visible(targ, targ.enemy)) {
					targ.oldenemy= attacker;
					return;
				}
				targ.oldenemy= targ.enemy;
			}
			targ.enemy= attacker;
			if (0 != (targ.monsterinfo.aiflags & AI_DUCKED))
				FoundTarget(targ);
			return;
		}

		// it's the same base (walk/swim/fly) type and a different classname and it's not a tank
		// (they spray too much), get mad at them
		if (((targ.flags & (FL_FLY | FL_SWIM)) == (attacker.flags & (FL_FLY | FL_SWIM)))
			&& (strcmp(targ.classname, attacker.classname) != 0)
			&& (strcmp(attacker.classname, "monster_tank") != 0)
			&& (strcmp(attacker.classname, "monster_supertank") != 0)
			&& (strcmp(attacker.classname, "monster_makron") != 0)
			&& (strcmp(attacker.classname, "monster_jorg") != 0)) {
			if (targ.enemy != null && targ.enemy.client != null)
				targ.oldenemy= targ.enemy;
			targ.enemy= attacker;
			if (0 == (targ.monsterinfo.aiflags & AI_DUCKED))
				FoundTarget(targ);
		}
		// if they *meant* to shoot us, then shoot back
		else if (attacker.enemy == targ) {
			if (targ.enemy != null && targ.enemy.client != null)
				targ.oldenemy= targ.enemy;
			targ.enemy= attacker;
			if (0 == (targ.monsterinfo.aiflags & AI_DUCKED))
				FoundTarget(targ);
		}
		// otherwise get mad at whoever they are mad at (help our buddy) unless it is us!
		else if (attacker.enemy != null && attacker.enemy != targ) {
			if (targ.enemy != null && targ.enemy.client != null)
				targ.oldenemy= targ.enemy;
			targ.enemy= attacker.enemy;
			if (0 == (targ.monsterinfo.aiflags & AI_DUCKED))
				FoundTarget(targ);
		}
	}

	static boolean CheckTeamDamage(edict_t targ, edict_t attacker) {
		//FIXME make the next line real and uncomment this block
		// if ((ability to damage a teammate == OFF) && (targ's team == attacker's team))
		return false;
	}

	/*
	============
	T_RadiusDamage
	============
	*/
	static void T_RadiusDamage(
		edict_t inflictor,
		edict_t attacker,
		float damage,
		edict_t ignore,
		float radius,
		int mod) {
		float points;
		EdictIterator edictit= null;

		float[] v= { 0, 0, 0 };
		float[] dir= { 0, 0, 0 };
		;

		while ((edictit= findradius(edictit, inflictor.s.origin, radius)) != null) {
			edict_t ent= edictit.o;
			if (ent == ignore)
				continue;
			if (ent.takedamage == 0)
				continue;

			VectorAdd(ent.mins, ent.maxs, v);
			VectorMA(ent.s.origin, 0.5f, v, v);
			VectorSubtract(inflictor.s.origin, v, v);
			points= damage - 0.5f * VectorLength(v);
			if (ent == attacker)
				points= points * 0.5f;
			if (points > 0) {
				if (CanDamage(ent, inflictor)) {
					VectorSubtract(ent.s.origin, inflictor.s.origin, dir);
					T_Damage(
						ent,
						inflictor,
						attacker,
						dir,
						inflictor.s.origin,
						vec3_origin,
						(int) points,
						(int) points,
						DAMAGE_RADIUS,
						mod);
				}
			}
		}

	}
}
