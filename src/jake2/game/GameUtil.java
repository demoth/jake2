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
// $Id: GameUtil.java,v 1.23 2004-06-03 21:32:51 rst Exp $

package jake2.game;

import java.sql.Savepoint;

import jake2.Defines;
import jake2.client.M;
import jake2.qcommon.Com;
import jake2.util.*;

public class GameUtil extends GameBase
{

	public static void checkClassname(edict_t ent)
	{

		if (ent.classname == null)
		{
			Com.Printf("edict with classname = null: " + ent.index);
		}
	}

	/**
	the global "activator" should be set to the entity that initiated the firing.
	
	If self.delay is set, a DelayedUse entity will be created that will actually
	do the SUB_UseTargets after that many seconds have passed.
	
	Centerprints any self.message to the activator.
	
	Search for (string)targetname in all entities that
	match (string)self.target and call their .use function
	*/

	public static void G_UseTargets(edict_t ent, edict_t activator)
	{
		edict_t t;

		checkClassname(ent);

		//
		//	   check for a delay
		//
		if (ent.delay != 0)
		{
			// create a temp object to fire at a later time
			t = G_Spawn();
			t.classname = "DelayedUse";
			t.nextthink = level.time + ent.delay;
			t.think = GameUtilAdapters.Think_Delay;
			t.activator = activator;
			if (activator == null)
				gi.dprintf("Think_Delay with no activator\n");
			t.message = ent.message;
			t.target = ent.target;
			t.killtarget = ent.killtarget;
			return;
		}

		//
		//	   print the message
		//
		if ((ent.message != null) && (activator.svflags & SVF_MONSTER) == 0)
		{
			gi.centerprintf(activator, "" + ent.message);
			if (ent.noise_index != 0)
				gi.sound(activator, CHAN_AUTO, ent.noise_index, 1, ATTN_NORM, 0);
			else
				gi.sound(activator, CHAN_AUTO, gi.soundindex("misc/talk1.wav"), 1, ATTN_NORM, 0);
		}

		//
		// kill killtargets
		//

		EdictIterator edit = null;

		if (ent.killtarget != null)
		{
			while ((edit = G_Find(edit, findByTarget, ent.killtarget)) != null)
			{
				t = edit.o;
				G_FreeEdict(t);
				if (!ent.inuse)
				{
					gi.dprintf("entity was removed while using killtargets\n");
					return;
				}
			}
		}

		// fire targets 

		if (ent.target != null)
		{
			edit = null;
			while ((edit = G_Find(edit, findByTarget, ent.target)) != null)
			{
				t = edit.o;
				// doors fire area portals in a specific way
				if (Lib.Q_stricmp("func_areaportal", t.classname) == 0
					&& (Lib.Q_stricmp("func_door", ent.classname) == 0 || Lib.Q_stricmp("func_door_rotating", ent.classname) == 0))
					continue;

				if (t == ent)
				{
					gi.dprintf("WARNING: Entity used itself.\n");
				}
				else
				{
					if (t.use != null)
						t.use.use(t, ent, activator);
				}
				if (!ent.inuse)
				{
					gi.dprintf("entity was removed while using targets\n");
					return;
				}
			}
		}
	}

	public static void G_InitEdict(edict_t e, int i)
	{
		e.inuse = true;
		e.classname = "noclass";
		e.gravity = 1.0f;
		//e.s.number= e - g_edicts;
		e.s = new entity_state_t(e);
		e.s.number = i;
		e.index = i;
	}

	/** 
	 * Either finds a free edict, or allocates a new one.
	 * Try to avoid reusing an entity that was recently freed, because it
	 * can cause the client to think the entity morphed into something else
	 * instead of being removed and recreated, which can cause interpolated
	 * angles and bad trails.
	*/
	public static edict_t G_Spawn()
	{
		int i;
		edict_t e = null;

		for (i = (int) maxclients.value + 1; i < globals.num_edicts; i++)
		{
			e = g_edicts[i];
			// the first couple seconds of server time can involve a lot of
			// freeing and allocating, so relax the replacement policy
			if (!e.inuse && (e.freetime < 2 || level.time - e.freetime > 0.5))
			{
				e = g_edicts[i] = new edict_t(i);
				G_InitEdict(e, i);
				return e;
			}
		}

		if (i == game.maxentities)
			gi.error("ED_Alloc: no free edicts");

		e = g_edicts[i] = new edict_t(i);
		globals.num_edicts++;
		G_InitEdict(e, i);
		return e;
	}

	/**
	 * Marks the edict as free
	*/
	public static void G_FreeEdict(edict_t ed)
	{
		gi.unlinkentity(ed); // unlink from world

		//if ((ed - g_edicts) <= (maxclients.value + BODY_QUEUE_SIZE))
		if (ed.index <= (maxclients.value + BODY_QUEUE_SIZE))
		{
			//			gi.dprintf("tried to free special edict\n");
			return;
		}

		//memset(ed, 0, sizeof(* ed));
		g_edicts[ed.index] = new edict_t(ed.index);
		//ed.clear();
		ed.classname = "freed";
		ed.freetime = level.time;
		ed.inuse = false;
	}

	/**
	 * Call after linking a new trigger in during gameplay 
	 * to force all entities it covers to immediately touch it.
	*/

	public static void G_ClearEdict(edict_t ent)
	{
		int i= ent.index;
		g_edicts[i] = new edict_t(i);
	}
	
	
	public static void G_TouchSolids(edict_t ent)
	{
		int i, num;
		edict_t touch[] = new edict_t[MAX_EDICTS], hit;

		num = gi.BoxEdicts(ent.absmin, ent.absmax, touch, MAX_EDICTS, AREA_SOLID);

		// be careful, it is possible to have an entity in this
		// list removed before we get to it (killtriggered)
		for (i = 0; i < num; i++)
		{
			hit = touch[i];
			if (!hit.inuse)
				continue;
			if (ent.touch != null)
			{
				ent.touch.touch(hit, ent, GameBase.dummyplane, null);
			}
			if (!ent.inuse)
				break;
		}
	}

	/**
	 * Kills all entities that would touch the proposed new positioning
	 * of ent.  Ent should be unlinked before calling this!
	 */

	public static boolean KillBox(edict_t ent)
	{
		trace_t tr;

		while (true)
		{
			tr = gi.trace(ent.s.origin, ent.mins, ent.maxs, ent.s.origin, null, MASK_PLAYERSOLID);
			if (tr.ent == null || tr.ent == g_edicts[0])
				break;

			// nail it
			T_Damage(tr.ent, ent, ent, vec3_origin, ent.s.origin, vec3_origin, 100000, 0, DAMAGE_NO_PROTECTION, MOD_TELEFRAG);

			// if we didn't kill it, fail
			if (tr.ent.solid != 0)
				return false;
		}

		return true; // all clear
	}

	public static boolean OnSameTeam(edict_t ent1, edict_t ent2)
	{
		if (0 == ((int) (dmflags.value) & (DF_MODELTEAMS | DF_SKINTEAMS)))
			return false;

		if (ClientTeam(ent1).equals(ClientTeam(ent2)))
			return true;
		return false;
	}

	static String ClientTeam(edict_t ent)
	{
		String value;

		if (ent.client == null)
			return "";

		value = Info.Info_ValueForKey(ent.client.pers.userinfo, "skin");

		int p = value.indexOf("/");

		if (p == -1)
			return value;

		if (((int) (dmflags.value) & DF_MODELTEAMS) != 0)
		{
			return value.substring(0, p);
		}

		return value.substring(p + 1, value.length());
	}

	static void SetRespawn(edict_t ent, float delay)
	{
		ent.flags |= FL_RESPAWN;
		ent.svflags |= SVF_NOCLIENT;
		ent.solid = SOLID_NOT;
		ent.nextthink = level.time + delay;
		ent.think = GameUtilAdapters.DoRespawn;
		gi.linkentity(ent);
	}

	static int ITEM_INDEX(gitem_t item)
	{
		return item.index;
	}

	static edict_t Drop_Item(edict_t ent, gitem_t item)
	{
		edict_t dropped;
		float[] forward = { 0, 0, 0 };
		float[] right = { 0, 0, 0 };
		float[] offset = { 0, 0, 0 };

		dropped = G_Spawn();

		dropped.classname = item.classname;
		dropped.item = item;
		dropped.spawnflags = DROPPED_ITEM;
		dropped.s.effects = item.world_model_flags;
		dropped.s.renderfx = RF_GLOW;
		Math3D.VectorSet(dropped.mins, -15, -15, -15);
		Math3D.VectorSet(dropped.maxs, 15, 15, 15);
		gi.setmodel(dropped, dropped.item.world_model);
		dropped.solid = SOLID_TRIGGER;
		dropped.movetype = MOVETYPE_TOSS;

		dropped.touch = GameUtilAdapters.drop_temp_touch;

		dropped.owner = ent;

		if (ent.client != null)
		{
			trace_t trace;

			Math3D.AngleVectors(ent.client.v_angle, forward, right, null);
			Math3D.VectorSet(offset, 24, 0, -16);
			Math3D.G_ProjectSource(ent.s.origin, offset, forward, right, dropped.s.origin);
			trace = gi.trace(ent.s.origin, dropped.mins, dropped.maxs, dropped.s.origin, ent, CONTENTS_SOLID);
			Math3D.VectorCopy(trace.endpos, dropped.s.origin);
		}
		else
		{
			Math3D.AngleVectors(ent.s.angles, forward, right, null);
			Math3D.VectorCopy(ent.s.origin, dropped.s.origin);
		}

		Math3D.VectorScale(forward, 100, dropped.velocity);
		dropped.velocity[2] = 300;

		dropped.think = GameUtilAdapters.drop_make_touchable;
		dropped.nextthink = level.time + 1;

		gi.linkentity(dropped);

		return dropped;
	}

	static void ValidateSelectedItem(edict_t ent)
	{
		gclient_t cl;

		cl = ent.client;

		if (cl.pers.inventory[cl.pers.selected_item] != 0)
			return; // valid

		GameAI.SelectNextItem(ent, -1);
	}

	static void Use_Item(edict_t ent, edict_t other, edict_t activator)
	{
		ent.svflags &= ~SVF_NOCLIENT;
		ent.use = null;

		if ((ent.spawnflags & ITEM_NO_TOUCH) != 0)
		{
			ent.solid = SOLID_BBOX;
			ent.touch = null;
		}
		else
		{
			ent.solid = SOLID_TRIGGER;
			ent.touch = GameUtilAdapters.Touch_Item;
		}

		gi.linkentity(ent);
	}

	/*
	============
	CanDamage
	
	Returns true if the inflictor can directly damage the target.  Used for
	explosions and melee attacks.
	============
	*/
	static boolean CanDamage(edict_t targ, edict_t inflictor)
	{
		float[] dest = { 0, 0, 0 };
		trace_t trace;

		// bmodels need special checking because their origin is 0,0,0
		if (targ.movetype == MOVETYPE_PUSH)
		{
			Math3D.VectorAdd(targ.absmin, targ.absmax, dest);
			Math3D.VectorScale(dest, 0.5f, dest);
			trace = gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
			if (trace.fraction == 1.0f)
				return true;
			if (trace.ent == targ)
				return true;
			return false;
		}

		trace = gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, targ.s.origin, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		Math3D.VectorCopy(targ.s.origin, dest);
		dest[0] += 15.0;
		dest[1] += 15.0;
		trace = gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		Math3D.VectorCopy(targ.s.origin, dest);
		dest[0] += 15.0;
		dest[1] -= 15.0;
		trace = gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		Math3D.VectorCopy(targ.s.origin, dest);
		dest[0] -= 15.0;
		dest[1] += 15.0;
		trace = gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		Math3D.VectorCopy(targ.s.origin, dest);
		dest[0] -= 15.0;
		dest[1] -= 15.0;
		trace = gi.trace(inflictor.s.origin, vec3_origin, vec3_origin, dest, inflictor, MASK_SOLID);
		if (trace.fraction == 1.0)
			return true;

		return false;
	}

	public static void T_Damage(
		edict_t targ,
		edict_t inflictor,
		edict_t attacker,
		float[] dir,
		float[] point,
		float[] normal,
		int damage,
		int knockback,
		int dflags,
		int mod)
	{
		gclient_t client;
		int take;
		int save;
		int asave;
		int psave;
		int te_sparks;

		if (targ.takedamage == 0)
			return;

		// friendly fire avoidance
		// if enabled you can't hurt teammates (but you can hurt yourself)
		// knockback still occurs
		if ((targ != attacker)
			&& ((deathmatch.value != 0 && 0 != ((int) (dmflags.value) & (DF_MODELTEAMS | DF_SKINTEAMS))) || coop.value != 0))
		{
			if (OnSameTeam(targ, attacker))
			{
				if (((int) (dmflags.value) & DF_NO_FRIENDLY_FIRE) != 0)
					damage = 0;
				else
					mod |= MOD_FRIENDLY_FIRE;
			}
		}
		meansOfDeath = mod;

		// easy mode takes half damage
		if (skill.value == 0 && deathmatch.value == 0 && targ.client != null)
		{
			damage *= 0.5;
			if (damage == 0)
				damage = 1;
		}

		client = targ.client;

		if ((dflags & DAMAGE_BULLET) != 0)
			te_sparks = TE_BULLET_SPARKS;
		else
			te_sparks = TE_SPARKS;

		Math3D.VectorNormalize(dir);

		//	   bonus damage for suprising a monster
		if (0 == (dflags & DAMAGE_RADIUS)
			&& (targ.svflags & SVF_MONSTER) != 0
			&& (attacker.client != null)
			&& (targ.enemy == null)
			&& (targ.health > 0))
			damage *= 2;

		if ((targ.flags & FL_NO_KNOCKBACK) != 0)
			knockback = 0;

		//	   figure momentum add
		if (0 == (dflags & DAMAGE_NO_KNOCKBACK))
		{
			if ((knockback != 0)
				&& (targ.movetype != MOVETYPE_NONE)
				&& (targ.movetype != MOVETYPE_BOUNCE)
				&& (targ.movetype != MOVETYPE_PUSH)
				&& (targ.movetype != MOVETYPE_STOP))
			{
				float[] kvel = { 0, 0, 0 };
				float mass;

				if (targ.mass < 50)
					mass = 50;
				else
					mass = targ.mass;

				if (targ.client != null && attacker == targ)
					Math3D.VectorScale(dir, 1600.0f * (float) knockback / mass, kvel);
				// the rocket jump hack...
				else
					Math3D.VectorScale(dir, 500.0f * (float) knockback / mass, kvel);

				Math3D.VectorAdd(targ.velocity, kvel, targ.velocity);
			}
		}

		take = damage;
		save = 0;

		// check for godmode
		if ((targ.flags & FL_GODMODE) != 0 && 0 == (dflags & DAMAGE_NO_PROTECTION))
		{
			take = 0;
			save = damage;
			SpawnDamage(te_sparks, point, normal, save);
		}

		// check for invincibility
		if ((client != null && client.invincible_framenum > level.framenum) && 0 == (dflags & DAMAGE_NO_PROTECTION))
		{
			if (targ.pain_debounce_time < level.time)
			{
				gi.sound(targ, CHAN_ITEM, gi.soundindex("items/protect4.wav"), 1, ATTN_NORM, 0);
				targ.pain_debounce_time = level.time + 2;
			}
			take = 0;
			save = damage;
		}

		psave = CheckPowerArmor(targ, point, normal, take, dflags);
		take -= psave;

		asave = CheckArmor(targ, point, normal, take, te_sparks, dflags);
		take -= asave;

		// treat cheat/powerup savings the same as armor
		asave += save;

		// team damage avoidance
		if (0 == (dflags & DAMAGE_NO_PROTECTION) && CheckTeamDamage(targ, attacker))
			return;

		// do the damage
		if (take != 0)
		{
			if (0 != (targ.svflags & SVF_MONSTER) || (client != null))
				SpawnDamage(TE_BLOOD, point, normal, take);
			else
				SpawnDamage(te_sparks, point, normal, take);

			targ.health = targ.health - take;

			if (targ.health <= 0)
			{
				if ((targ.svflags & SVF_MONSTER) != 0 || (client != null))
					targ.flags |= FL_NO_KNOCKBACK;
				Killed(targ, inflictor, attacker, take, point);
				return;
			}
		}

		if ((targ.svflags & SVF_MONSTER) != 0)
		{
			M.M_ReactToDamage(targ, attacker);
			if (0 == (targ.monsterinfo.aiflags & AI_DUCKED) && (take != 0))
			{
				targ.pain.pain(targ, attacker, knockback, take);
				// nightmare mode monsters don't go into pain frames often
				if (skill.value == 3)
					targ.pain_debounce_time = level.time + 5;
			}
		}
		else if (client != null)
		{
			if (((targ.flags & FL_GODMODE) == 0) && (take != 0))
				targ.pain.pain(targ, attacker, knockback, take);
		}
		else if (take != 0)
		{
			if (targ.pain != null)
				targ.pain.pain(targ, attacker, knockback, take);
		}

		// add to the damage inflicted on a player this frame
		// the total will be turned into screen blends and view angle kicks
		// at the end of the frame
		if (client != null)
		{
			client.damage_parmor += psave;
			client.damage_armor += asave;
			client.damage_blood += take;
			client.damage_knockback += knockback;
			Math3D.VectorCopy(point, client.damage_from);
		}
	}

	/*
	============
	Killed
	============
	*/
	public static void Killed(edict_t targ, edict_t inflictor, edict_t attacker, int damage, float[] point)
	{
		if (targ.health < -999)
			targ.health = -999;

		//Com.Println("Killed:" + targ.classname);
		targ.enemy = attacker;

		if ((targ.svflags & SVF_MONSTER) != 0 && (targ.deadflag != DEAD_DEAD))
		{
			//			targ.svflags |= SVF_DEADMONSTER;	// now treat as a different content type
			if (0 == (targ.monsterinfo.aiflags & AI_GOOD_GUY))
			{
				level.killed_monsters++;
				if (coop.value != 0 && attacker.client != null)
					attacker.client.resp.score++;
				// medics won't heal monsters that they kill themselves
				if (attacker.classname.equals("monster_medic"))
					targ.owner = attacker;
			}
		}

		if (targ.movetype == MOVETYPE_PUSH || targ.movetype == MOVETYPE_STOP || targ.movetype == MOVETYPE_NONE)
		{ // doors, triggers, etc
			targ.die.die(targ, inflictor, attacker, damage, point);
			return;
		}

		if ((targ.svflags & SVF_MONSTER) != 0 && (targ.deadflag != DEAD_DEAD))
		{
			targ.touch = null;
			Monster.monster_death_use(targ);
		}

		targ.die.die(targ, inflictor, attacker, damage, point);
	}

	/*
	================
	SpawnDamage
	================
	*/
	static void SpawnDamage(int type, float[] origin, float[] normal, int damage)
	{
		if (damage > 255)
			damage = 255;
		gi.WriteByte(svc_temp_entity);
		gi.WriteByte(type);
		//		gi.WriteByte (damage);
		gi.WritePosition(origin);
		gi.WriteDir(normal);
		gi.multicast(origin, MULTICAST_PVS);
	}

	static int PowerArmorType(edict_t ent)
	{
		if (ent.client == null)
			return POWER_ARMOR_NONE;

		if (0 == (ent.flags & FL_POWER_ARMOR))
			return POWER_ARMOR_NONE;

		if (ent.client.pers.inventory[GameUtilAdapters.power_shield_index] > 0)
			return POWER_ARMOR_SHIELD;

		if (ent.client.pers.inventory[GameUtilAdapters.power_screen_index] > 0)
			return POWER_ARMOR_SCREEN;

		return POWER_ARMOR_NONE;
	}

	static int CheckPowerArmor(edict_t ent, float[] point, float[] normal, int damage, int dflags)
	{
		gclient_t client;
		int save;
		int power_armor_type;
		int index = 0;
		int damagePerCell;
		int pa_te_type;
		int power = 0;
		int power_used;

		if (damage != 0)
			return 0;

		client = ent.client;

		if ((dflags & DAMAGE_NO_ARMOR) != 0)
			return 0;

		if (client != null)
		{
			power_armor_type = PowerArmorType(ent);
			if (power_armor_type != POWER_ARMOR_NONE)
			{
				index = ITEM_INDEX(FindItem("Cells"));
				power = client.pers.inventory[index];
			}
		}
		else if ((ent.svflags & SVF_MONSTER) != 0)
		{
			power_armor_type = ent.monsterinfo.power_armor_type;
			power = ent.monsterinfo.power_armor_power;
		}
		else
			return 0;

		if (power_armor_type == POWER_ARMOR_NONE)
			return 0;
		if (power == 0)
			return 0;

		if (power_armor_type == POWER_ARMOR_SCREEN)
		{
			float[] vec = { 0, 0, 0 };
			float dot;
			float[] forward = { 0, 0, 0 };

			// only works if damage point is in front
			Math3D.AngleVectors(ent.s.angles, forward, null, null);
			Math3D.VectorSubtract(point, ent.s.origin, vec);
			Math3D.VectorNormalize(vec);
			dot = Math3D.DotProduct(vec, forward);
			if (dot <= 0.3)
				return 0;

			damagePerCell = 1;
			pa_te_type = TE_SCREEN_SPARKS;
			damage = damage / 3;
		}
		else
		{
			damagePerCell = 2;
			pa_te_type = TE_SHIELD_SPARKS;
			damage = (2 * damage) / 3;
		}

		save = power * damagePerCell;

		if (save == 0)
			return 0;
		if (save > damage)
			save = damage;

		SpawnDamage(pa_te_type, point, normal, save);
		ent.powerarmor_time = level.time + 0.2f;

		power_used = save / damagePerCell;

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
	static void ai_walk(edict_t self, float dist)
	{
		M.M_MoveToGoal(self, dist);

		// check for noticing a player
		if (FindTarget(self))
			return;

		if ((self.monsterinfo.search != null) && (level.time > self.monsterinfo.idle_time))
		{
			if (self.monsterinfo.idle_time != 0)
			{
				self.monsterinfo.search.think(self);
				self.monsterinfo.idle_time = level.time + 15 + Lib.random() * 15;
			}
			else
			{
				self.monsterinfo.idle_time = level.time + Lib.random() * 15;
			}
		}
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
	static int range(edict_t self, edict_t other)
	{
		float[] v = { 0, 0, 0 };
		float len;

		Math3D.VectorSubtract(self.s.origin, other.s.origin, v);
		len = Math3D.VectorLength(v);
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
	static gitem_t FindItemByClassname(String classname)
	{

		for (int i = 1; i < game.num_items; i++)
		{
			gitem_t it = GameAI.itemlist[i];

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
	//geht.
	static gitem_t FindItem(String pickup_name)
	{
		//Com.Printf("FindItem:" + pickup_name + "\n");
		for (int i = 1; i < game.num_items; i++)
		{
			gitem_t it = GameAI.itemlist[i];

			if (it.pickup_name == null)
				continue;
			if (it.pickup_name.equalsIgnoreCase(pickup_name))
				return it;
		}
		Com.p("Item not found:" + pickup_name);
		return null;
	}

	static int ArmorIndex(edict_t ent)
	{
		if (ent.client == null)
			return 0;

		if (ent.client.pers.inventory[GameUtilAdapters.jacket_armor_index] > 0)
			return GameUtilAdapters.jacket_armor_index;

		if (ent.client.pers.inventory[GameUtilAdapters.combat_armor_index] > 0)
			return GameUtilAdapters.combat_armor_index;

		if (ent.client.pers.inventory[GameUtilAdapters.body_armor_index] > 0)
			return GameUtilAdapters.body_armor_index;

		return 0;
	}

	static int CheckArmor(edict_t ent, float[] point, float[] normal, int damage, int te_sparks, int dflags)
	{
		gclient_t client;
		int save;
		int index;
		gitem_t armor;

		if (damage == 0)
			return 0;

		client = ent.client;

		if (client != null)
			return 0;

		if ((dflags & DAMAGE_NO_ARMOR) != 0)
			return 0;

		index = ArmorIndex(ent);

		if (index == 0)
			return 0;

		armor = GameAI.GetItemByIndex(index);
		gitem_armor_t garmor = (gitem_armor_t) armor.info;

		if (0 != (dflags & DAMAGE_ENERGY))
			save = (int) Math.ceil(garmor.energy_protection * damage);
		else
			save = (int) Math.ceil(garmor.normal_protection * damage);

		if (save >= client.pers.inventory[index])
			save = client.pers.inventory[index];

		if (save == 0)
			return 0;

		client.pers.inventory[index] -= save;
		SpawnDamage(te_sparks, point, normal, save);

		return save;
	}

	static void AttackFinished(edict_t self, float time)
	{
		self.monsterinfo.attack_finished = level.time + time;
	}

	/*
	=============
	infront
	
	returns true if the entity is in front (in sight) of self
	=============
	*/
	static boolean infront(edict_t self, edict_t other)
	{
		float[] vec = { 0, 0, 0 };
		float dot;
		float[] forward = { 0, 0, 0 };

		Math3D.AngleVectors(self.s.angles, forward, null, null);
		Math3D.VectorSubtract(other.s.origin, self.s.origin, vec);
		Math3D.VectorNormalize(vec);
		dot = Math3D.DotProduct(vec, forward);

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
	public static boolean visible(edict_t self, edict_t other)
	{
		float[] spot1 = { 0, 0, 0 };
		float[] spot2 = { 0, 0, 0 };
		trace_t trace;

		Math3D.VectorCopy(self.s.origin, spot1);
		spot1[2] += self.viewheight;
		Math3D.VectorCopy(other.s.origin, spot2);
		spot2[2] += other.viewheight;
		trace = gi.trace(spot1, vec3_origin, vec3_origin, spot2, self, MASK_OPAQUE);

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
	static void AI_SetSightClient()
	{
		edict_t ent;
		int start, check;

		if (level.sight_client == null)
			start = 1;
		else
			start = level.sight_client.index;

		check = start;
		while (true)
		{
			check++;
			if (check > game.maxclients)
				check = 1;
			ent = g_edicts[check];

			if (ent.inuse && ent.health > 0 && (ent.flags & FL_NOTARGET) == 0)
			{
				level.sight_client = ent;
				return; // got one
			}
			if (check == start)
			{
				level.sight_client = null;
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
	static void ai_move(edict_t self, float dist)
	{
		M.M_walkmove(self, self.s.angles[YAW], dist);
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
	static boolean FindTarget(edict_t self)
	{
		edict_t client;
		boolean heardit;
		int r;

		if ((self.monsterinfo.aiflags & AI_GOOD_GUY) != 0)
		{
			if (self.goalentity != null && self.goalentity.inuse && self.goalentity.classname != null)
			{
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

		heardit = false;
		if ((level.sight_entity_framenum >= (level.framenum - 1)) && 0 == (self.spawnflags & 1))
		{
			client = level.sight_entity;
			if (client.enemy == self.enemy)
			{
				return false;
			}
		}
		else if (level.sound_entity_framenum >= (level.framenum - 1))
		{
			client = level.sound_entity;
			heardit = true;
		}
		else if (null != (self.enemy) && (level.sound2_entity_framenum >= (level.framenum - 1)) && 0 != (self.spawnflags & 1))
		{
			client = level.sound2_entity;
			heardit = true;
		}
		else
		{
			client = level.sight_client;
			if (client == null)
				return false; // no clients to get mad at
		}

		// if the entity went away, forget it
		if (!client.inuse)
			return false;

		if (client == self.enemy)
			return true; // JDC false;

		if (client.client != null)
		{
			if ((client.flags & FL_NOTARGET) != 0)
				return false;
		}
		else if ((client.svflags & SVF_MONSTER) != 0)
		{
			if (client.enemy == null)
				return false;
			if ((client.enemy.flags & FL_NOTARGET) != 0)
				return false;
		}
		else if (heardit)
		{
			if ((client.owner.flags & FL_NOTARGET) != 0)
				return false;
		}
		else
			return false;

		if (!heardit)
		{
			r = range(self, client);

			if (r == RANGE_FAR)
				return false;

			//	   this is where we would check invisibility

			// is client in an spot too dark to be seen?
			if (client.light_level <= 5)
				return false;

			if (!visible(self, client))
			{
				return false;
			}

			if (r == RANGE_NEAR)
			{
				if (client.show_hostile < level.time && !infront(self, client))
				{
					return false;
				}
			}
			else if (r == RANGE_MID)
			{
				if (!infront(self, client))
				{
					return false;
				}
			}

			self.enemy = client;

			if (!self.enemy.classname.equals("player_noise"))
			{
				self.monsterinfo.aiflags &= ~AI_SOUND_TARGET;

				if (self.enemy.client == null)
				{
					self.enemy = self.enemy.enemy;
					if (self.enemy.client == null)
					{
						self.enemy = null;
						return false;
					}
				}
			}
		}
		else
		{
			// heard it
			float[] temp = { 0, 0, 0 };

			if ((self.spawnflags & 1) != 0)
			{
				if (!visible(self, client))
					return false;
			}
			else
			{
				if (!gi.inPHS(self.s.origin, client.s.origin))
					return false;
			}

			Math3D.VectorSubtract(client.s.origin, self.s.origin, temp);

			if (Math3D.VectorLength(temp) > 1000) // too far to hear
			{
				return false;
			}

			// check area portals - if they are different and not connected then we can't hear it
			if (client.areanum != self.areanum)
				if (!gi.AreasConnected(self.areanum, client.areanum))
					return false;

			self.ideal_yaw = Math3D.vectoyaw(temp);
			M.M_ChangeYaw(self);

			// hunt the sound for a bit; hopefully find the real player
			self.monsterinfo.aiflags |= AI_SOUND_TARGET;
			self.enemy = client;
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
	//ok
	static void HuntTarget(edict_t self)
	{
		float[] vec = { 0, 0, 0 };

		self.goalentity = self.enemy;
		if ((self.monsterinfo.aiflags & AI_STAND_GROUND) != 0)
			self.monsterinfo.stand.think(self);
		else
			self.monsterinfo.run.think(self);
		Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, vec);
		self.ideal_yaw = Math3D.vectoyaw(vec);
		// wait a while before first attack
		if (0 == (self.monsterinfo.aiflags & AI_STAND_GROUND))
			AttackFinished(self, 1);
	}

	public static void FoundTarget(edict_t self)
	{
		// let other monsters see this monster for a while
		if (self.enemy.client != null)
		{
			level.sight_entity = self;
			level.sight_entity_framenum = level.framenum;
			level.sight_entity.light_level = 128;
		}

		self.show_hostile = (int) level.time + 1; // wake up other monsters

		Math3D.VectorCopy(self.enemy.s.origin, self.monsterinfo.last_sighting);
		self.monsterinfo.trail_time = level.time;

		if (self.combattarget == null)
		{
			HuntTarget(self);
			return;
		}

		self.goalentity = self.movetarget = G_PickTarget(self.combattarget);
		if (self.movetarget == null)
		{
			self.goalentity = self.movetarget = self.enemy;
			HuntTarget(self);
			gi.dprintf("" + self.classname + "at " + Lib.vtos(self.s.origin) + ", combattarget " + self.combattarget + " not found\n");
			return;
		}

		// clear out our combattarget, these are a one shot deal
		self.combattarget = null;
		self.monsterinfo.aiflags |= AI_COMBAT_POINT;

		// clear the targetname, that point is ours!
		self.movetarget.targetname = null;
		self.monsterinfo.pausetime = 0;

		// run for it
		self.monsterinfo.run.think(self);
	}

	static boolean CheckTeamDamage(edict_t targ, edict_t attacker)
	{
		//FIXME make the next line real and uncomment this block
		// if ((ability to damage a teammate == OFF) && (targ's team == attacker's team))
		return false;
	}

	/*
	============
	T_RadiusDamage
	============
	*/
	static void T_RadiusDamage(edict_t inflictor, edict_t attacker, float damage, edict_t ignore, float radius, int mod)
	{
		float points;
		EdictIterator edictit = null;

		float[] v = { 0, 0, 0 };
		float[] dir = { 0, 0, 0 };
		;

		while ((edictit = findradius(edictit, inflictor.s.origin, radius)) != null)
		{
			edict_t ent = edictit.o;
			if (ent == ignore)
				continue;
			if (ent.takedamage == 0)
				continue;

			Math3D.VectorAdd(ent.mins, ent.maxs, v);
			Math3D.VectorMA(ent.s.origin, 0.5f, v, v);
			Math3D.VectorSubtract(inflictor.s.origin, v, v);
			points = damage - 0.5f * Math3D.VectorLength(v);
			if (ent == attacker)
				points = points * 0.5f;
			if (points > 0)
			{
				if (CanDamage(ent, inflictor))
				{
					Math3D.VectorSubtract(ent.s.origin, inflictor.s.origin, dir);
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
