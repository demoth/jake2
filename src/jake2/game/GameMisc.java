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

// Created on 27.12.2003 by RST.
// $Id: GameMisc.java,v 1.4 2004-02-29 00:51:05 rst Exp $

package jake2.game;

import jake2.client.M;

public class GameMisc extends GameTrigger
{
	public static void SP_path_corner(edict_t self)
	{
		if (self.targetname == null)
		{
			gi.dprintf("path_corner with no targetname at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		self.solid = SOLID_TRIGGER;
		self.touch = GameMiscAdapters.path_corner_touch;
		VectorSet(self.mins, -8, -8, -8);
		VectorSet(self.maxs, 8, 8, 8);
		self.svflags |= SVF_NOCLIENT;
		gi.linkentity(self);
	}

	public static void SP_point_combat(edict_t self)
	{
		if (deathmatch.value != 0)
		{
			G_FreeEdict(self);
			return;
		}
		self.solid = SOLID_TRIGGER;
		self.touch = GameMiscAdapters.point_combat_touch;
		VectorSet(self.mins, -8, -8, -16);
		VectorSet(self.maxs, 8, 8, 16);
		self.svflags = SVF_NOCLIENT;
		gi.linkentity(self);
	};

	public static void SP_viewthing(edict_t ent)
	{
		gi.dprintf("viewthing spawned\n");

		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		ent.s.renderfx = RF_FRAMELERP;
		VectorSet(ent.mins, -16, -16, -24);
		VectorSet(ent.maxs, 16, 16, 32);
		ent.s.modelindex = gi.modelindex("models/objects/banner/tris.md2");
		gi.linkentity(ent);
		ent.nextthink = level.time + 0.5f;
		ent.think = GameMiscAdapters.TH_viewthing;
		return;
	}

	/*QUAKED info_null (0 0.5 0) (-4 -4 -4) (4 4 4)
	Used as a positional target for spotlights, etc.
	*/
	public static void SP_info_null(edict_t self)
	{
		G_FreeEdict(self);
	};

	/*QUAKED info_notnull (0 0.5 0) (-4 -4 -4) (4 4 4)
	Used as a positional target for lightning.
	*/
	public static void SP_info_notnull(edict_t self)
	{
		VectorCopy(self.s.origin, self.absmin);
		VectorCopy(self.s.origin, self.absmax);
	};

	public static void SP_light(edict_t self)
	{
		// no targeted lights in deathmatch, because they cause global messages
		if (null == self.targetname || deathmatch.value != 0)
		{
			G_FreeEdict(self);
			return;
		}

		if (self.style >= 32)
		{
			self.use = GameMiscAdapters.light_use;
			if ((self.spawnflags & GameMiscAdapters.START_OFF) != 0)
				gi.configstring(CS_LIGHTS + self.style, "a");
			else
				gi.configstring(CS_LIGHTS + self.style, "m");
		}
	}

	public static void SP_func_wall(edict_t self)
	{
		self.movetype = MOVETYPE_PUSH;
		gi.setmodel(self, self.model);

		if ((self.spawnflags & 8) != 0)
			self.s.effects |= EF_ANIM_ALL;
		if ((self.spawnflags & 16) != 0)
			self.s.effects |= EF_ANIM_ALLFAST;

		// just a wall
		if ((self.spawnflags & 7) == 0)
		{
			self.solid = SOLID_BSP;
			gi.linkentity(self);
			return;
		}

		// it must be TRIGGER_SPAWN
		if (0 == (self.spawnflags & 1))
		{
			//		gi.dprintf("func_wall missing TRIGGER_SPAWN\n");
			self.spawnflags |= 1;
		}

		// yell if the spawnflags are odd
		if ((self.spawnflags & 4) != 0)
		{
			if (0 == (self.spawnflags & 2))
			{
				gi.dprintf("func_wall START_ON without TOGGLE\n");
				self.spawnflags |= 2;
			}
		}

		self.use = GameMiscAdapters.func_wall_use;
		if ((self.spawnflags & 4) != 0)
		{
			self.solid = SOLID_BSP;
		}
		else
		{
			self.solid = SOLID_NOT;
			self.svflags |= SVF_NOCLIENT;
		}
		gi.linkentity(self);
	}

	public static void SP_func_object(edict_t self)
	{
		gi.setmodel(self, self.model);

		self.mins[0] += 1;
		self.mins[1] += 1;
		self.mins[2] += 1;
		self.maxs[0] -= 1;
		self.maxs[1] -= 1;
		self.maxs[2] -= 1;

		if (self.dmg == 0)
			self.dmg = 100;

		if (self.spawnflags == 0)
		{
			self.solid = SOLID_BSP;
			self.movetype = MOVETYPE_PUSH;
			self.think = GameMiscAdapters.func_object_release;
			self.nextthink = level.time + 2 * FRAMETIME;
		}
		else
		{
			self.solid = SOLID_NOT;
			self.movetype = MOVETYPE_PUSH;
			self.use = GameMiscAdapters.func_object_use;
			self.svflags |= SVF_NOCLIENT;
		}

		if ((self.spawnflags & 2) != 0)
			self.s.effects |= EF_ANIM_ALL;
		if ((self.spawnflags & 4) != 0)
			self.s.effects |= EF_ANIM_ALLFAST;

		self.clipmask = MASK_MONSTERSOLID;

		gi.linkentity(self);
	}

	public static void SP_func_explosive(edict_t self)
	{
		if (deathmatch.value != 0)
		{ // auto-remove for deathmatch
			G_FreeEdict(self);
			return;
		}

		self.movetype = MOVETYPE_PUSH;

		gi.modelindex("models/objects/debris1/tris.md2");
		gi.modelindex("models/objects/debris2/tris.md2");

		gi.setmodel(self, self.model);

		if ((self.spawnflags & 1) != 0)
		{
			self.svflags |= SVF_NOCLIENT;
			self.solid = SOLID_NOT;
			self.use = GameMiscAdapters.func_explosive_spawn;
		}
		else
		{
			self.solid = SOLID_BSP;
			if (self.targetname != null)
				self.use = GameMiscAdapters.func_explosive_use;
		}

		if ((self.spawnflags & 2) != 0)
			self.s.effects |= EF_ANIM_ALL;
		if ((self.spawnflags & 4) != 0)
			self.s.effects |= EF_ANIM_ALLFAST;

		if (self.use != GameMiscAdapters.func_explosive_use)
		{
			if (self.health == 0)
				self.health = 100;
			self.die = GameMiscAdapters.func_explosive_explode;
			self.takedamage = DAMAGE_YES;
		}

		gi.linkentity(self);
	}

	public static void SP_misc_explobox(edict_t self)
	{
		if (deathmatch.value != 0)
		{ // auto-remove for deathmatch
			G_FreeEdict(self);
			return;
		}

		gi.modelindex("models/objects/debris1/tris.md2");
		gi.modelindex("models/objects/debris2/tris.md2");
		gi.modelindex("models/objects/debris3/tris.md2");

		self.solid = SOLID_BBOX;
		self.movetype = MOVETYPE_STEP;

		self.model = "models/objects/barrels/tris.md2";
		self.s.modelindex = gi.modelindex(self.model);
		VectorSet(self.mins, -16, -16, 0);
		VectorSet(self.maxs, 16, 16, 40);

		if (self.mass == 0)
			self.mass = 400;
		if (0 == self.health)
			self.health = 10;
		if (0 == self.dmg)
			self.dmg = 150;

		self.die = GameMiscAdapters.barrel_delay;
		self.takedamage = DAMAGE_YES;
		self.monsterinfo.aiflags = AI_NOSTEP;

		self.touch = GameMiscAdapters.barrel_touch;

		self.think = M.M_droptofloor;
		self.nextthink = level.time + 2 * FRAMETIME;

		gi.linkentity(self);
	}

	public static void SP_misc_blackhole(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_NOT;
		VectorSet(ent.mins, -64, -64, 0);
		VectorSet(ent.maxs, 64, 64, 8);
		ent.s.modelindex = gi.modelindex("models/objects/black/tris.md2");
		ent.s.renderfx = RF_TRANSLUCENT;
		ent.use = GameMiscAdapters.misc_blackhole_use;
		ent.think = GameMiscAdapters.misc_blackhole_think;
		ent.nextthink = level.time + 2 * FRAMETIME;
		gi.linkentity(ent);
	}

	public static void SP_misc_eastertank(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -32, -32, -16);
		VectorSet(ent.maxs, 32, 32, 32);
		ent.s.modelindex = gi.modelindex("models/monsters/tank/tris.md2");
		ent.s.frame = 254;
		ent.think = GameMiscAdapters.misc_eastertank_think;
		ent.nextthink = level.time + 2 * FRAMETIME;
		gi.linkentity(ent);
	}

	public static void SP_misc_easterchick(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -32, -32, 0);
		VectorSet(ent.maxs, 32, 32, 32);
		ent.s.modelindex = gi.modelindex("models/monsters/bitch/tris.md2");
		ent.s.frame = 208;
		ent.think = GameMiscAdapters.misc_easterchick_think;
		ent.nextthink = level.time + 2 * FRAMETIME;
		gi.linkentity(ent);
	}

	public static void SP_misc_easterchick2(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -32, -32, 0);
		VectorSet(ent.maxs, 32, 32, 32);
		ent.s.modelindex = gi.modelindex("models/monsters/bitch/tris.md2");
		ent.s.frame = 248;
		ent.think = GameMiscAdapters.misc_easterchick2_think;
		ent.nextthink = level.time + 2 * FRAMETIME;
		gi.linkentity(ent);
	}

	public static void SP_monster_commander_body(edict_t self)
	{
		self.movetype = MOVETYPE_NONE;
		self.solid = SOLID_BBOX;
		self.model = "models/monsters/commandr/tris.md2";
		self.s.modelindex = gi.modelindex(self.model);
		VectorSet(self.mins, -32, -32, 0);
		VectorSet(self.maxs, 32, 32, 48);
		self.use = GameMiscAdapters.commander_body_use;
		self.takedamage = DAMAGE_YES;
		self.flags = FL_GODMODE;
		self.s.renderfx |= RF_FRAMELERP;
		gi.linkentity(self);

		gi.soundindex("tank/thud.wav");
		gi.soundindex("tank/pain.wav");

		self.think = GameMiscAdapters.commander_body_drop;
		self.nextthink = level.time + 5 * FRAMETIME;
	}

	public static void SP_misc_banner(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_NOT;
		ent.s.modelindex = gi.modelindex("models/objects/banner/tris.md2");
		ent.s.frame = rand() % 16;
		gi.linkentity(ent);

		ent.think = GameMiscAdapters.misc_banner_think;
		ent.nextthink = level.time + FRAMETIME;
	}

	public static void SP_misc_deadsoldier(edict_t ent)
	{
		if (deathmatch.value != 0)
		{ // auto-remove for deathmatch
			G_FreeEdict(ent);
			return;
		}

		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		ent.s.modelindex = gi.modelindex("models/deadbods/dude/tris.md2");

		// Defaults to frame 0
		if ((ent.spawnflags & 2) != 0)
			ent.s.frame = 1;
		else if ((ent.spawnflags & 4) != 0)
			ent.s.frame = 2;
		else if ((ent.spawnflags & 8) != 0)
			ent.s.frame = 3;
		else if ((ent.spawnflags & 16) != 0)
			ent.s.frame = 4;
		else if ((ent.spawnflags & 32) != 0)
			ent.s.frame = 5;
		else
			ent.s.frame = 0;

		VectorSet(ent.mins, -16, -16, 0);
		VectorSet(ent.maxs, 16, 16, 16);
		ent.deadflag = DEAD_DEAD;
		ent.takedamage = DAMAGE_YES;
		ent.svflags |= SVF_MONSTER | SVF_DEADMONSTER;
		ent.die = GameMiscAdapters.misc_deadsoldier_die;
		ent.monsterinfo.aiflags |= AI_GOOD_GUY;

		gi.linkentity(ent);
	}

	public static void SP_misc_viper(edict_t ent)
	{
		if (null == ent.target)
		{
			gi.dprintf("misc_viper without a target at " + vtos(ent.absmin) + "\n");
			G_FreeEdict(ent);
			return;
		}

		if (0 == ent.speed)
			ent.speed = 300;

		ent.movetype = MOVETYPE_PUSH;
		ent.solid = SOLID_NOT;
		ent.s.modelindex = gi.modelindex("models/ships/viper/tris.md2");
		VectorSet(ent.mins, -16, -16, 0);
		VectorSet(ent.maxs, 16, 16, 32);

		ent.think = GameFuncAdapters.func_train_find;
		ent.nextthink = level.time + FRAMETIME;
		ent.use = GameMiscAdapters.misc_viper_use;
		ent.svflags |= SVF_NOCLIENT;
		ent.moveinfo.accel = ent.moveinfo.decel = ent.moveinfo.speed = ent.speed;

		gi.linkentity(ent);
	}

	/*QUAKED misc_bigviper (1 .5 0) (-176 -120 -24) (176 120 72) 
	This is a large stationary viper as seen in Paul's intro
	*/
	public static void SP_misc_bigviper(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -176, -120, -24);
		VectorSet(ent.maxs, 176, 120, 72);
		ent.s.modelindex = gi.modelindex("models/ships/bigviper/tris.md2");
		gi.linkentity(ent);
	}

	public static void SP_misc_viper_bomb(edict_t self)
	{
		self.movetype = MOVETYPE_NONE;
		self.solid = SOLID_NOT;
		VectorSet(self.mins, -8, -8, -8);
		VectorSet(self.maxs, 8, 8, 8);

		self.s.modelindex = gi.modelindex("models/objects/bomb/tris.md2");

		if (self.dmg == 0)
			self.dmg = 1000;

		self.use = GameMiscAdapters.misc_viper_bomb_use;
		self.svflags |= SVF_NOCLIENT;

		gi.linkentity(self);
	}

	public static void SP_misc_strogg_ship(edict_t ent)
	{
		if (null == ent.target)
		{
			gi.dprintf(ent.classname + " without a target at " + vtos(ent.absmin) + "\n");
			G_FreeEdict(ent);
			return;
		}

		if (0 == ent.speed)
			ent.speed = 300;

		ent.movetype = MOVETYPE_PUSH;
		ent.solid = SOLID_NOT;
		ent.s.modelindex = gi.modelindex("models/ships/strogg1/tris.md2");
		VectorSet(ent.mins, -16, -16, 0);
		VectorSet(ent.maxs, 16, 16, 32);

		ent.think = GameFuncAdapters.func_train_find;
		ent.nextthink = level.time + FRAMETIME;
		ent.use = GameMiscAdapters.misc_strogg_ship_use;
		ent.svflags |= SVF_NOCLIENT;
		ent.moveinfo.accel = ent.moveinfo.decel = ent.moveinfo.speed = ent.speed;

		gi.linkentity(ent);
	}

	public static void SP_misc_satellite_dish(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		VectorSet(ent.mins, -64, -64, 0);
		VectorSet(ent.maxs, 64, 64, 128);
		ent.s.modelindex = gi.modelindex("models/objects/satellite/tris.md2");
		ent.use = GameMiscAdapters.misc_satellite_dish_use;
		gi.linkentity(ent);
	}

	/*QUAKED light_mine1 (0 1 0) (-2 -2 -12) (2 2 12)
	*/
	public static void SP_light_mine1(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		ent.s.modelindex = gi.modelindex("models/objects/minelite/light1/tris.md2");
		gi.linkentity(ent);
	}

	/*QUAKED light_mine2 (0 1 0) (-2 -2 -12) (2 2 12)
	*/
	public static void SP_light_mine2(edict_t ent)
	{
		ent.movetype = MOVETYPE_NONE;
		ent.solid = SOLID_BBOX;
		ent.s.modelindex = gi.modelindex("models/objects/minelite/light2/tris.md2");
		gi.linkentity(ent);
	}

	/*QUAKED misc_gib_arm (1 0 0) (-8 -8 -8) (8 8 8)
	Intended for use with the target_spawner
	*/
	public static void SP_misc_gib_arm(edict_t ent)
	{
		gi.setmodel(ent, "models/objects/gibs/arm/tris.md2");
		ent.solid = SOLID_NOT;
		ent.s.effects |= EF_GIB;
		ent.takedamage = DAMAGE_YES;
		ent.die = GameAIAdapters.gib_die;
		ent.movetype = MOVETYPE_TOSS;
		ent.svflags |= SVF_MONSTER;
		ent.deadflag = DEAD_DEAD;
		ent.avelocity[0] = random() * 200;
		ent.avelocity[1] = random() * 200;
		ent.avelocity[2] = random() * 200;
		ent.think = GameUtilAdapters.G_FreeEdictA;
		ent.nextthink = level.time + 30;
		gi.linkentity(ent);
	}

	/*QUAKED misc_gib_leg (1 0 0) (-8 -8 -8) (8 8 8)
	Intended for use with the target_spawner
	*/
	public static void SP_misc_gib_leg(edict_t ent)
	{
		gi.setmodel(ent, "models/objects/gibs/leg/tris.md2");
		ent.solid = SOLID_NOT;
		ent.s.effects |= EF_GIB;
		ent.takedamage = DAMAGE_YES;
		ent.die = GameAIAdapters.gib_die;
		ent.movetype = MOVETYPE_TOSS;
		ent.svflags |= SVF_MONSTER;
		ent.deadflag = DEAD_DEAD;
		ent.avelocity[0] = random() * 200;
		ent.avelocity[1] = random() * 200;
		ent.avelocity[2] = random() * 200;
		ent.think = GameUtilAdapters.G_FreeEdictA;
		ent.nextthink = level.time + 30;
		gi.linkentity(ent);
	}

	/*QUAKED misc_gib_head (1 0 0) (-8 -8 -8) (8 8 8)
	Intended for use with the target_spawner
	*/
	public static void SP_misc_gib_head(edict_t ent)
	{
		gi.setmodel(ent, "models/objects/gibs/head/tris.md2");
		ent.solid = SOLID_NOT;
		ent.s.effects |= EF_GIB;
		ent.takedamage = DAMAGE_YES;
		ent.die = GameAIAdapters.gib_die;
		ent.movetype = MOVETYPE_TOSS;
		ent.svflags |= SVF_MONSTER;
		ent.deadflag = DEAD_DEAD;
		ent.avelocity[0] = random() * 200;
		ent.avelocity[1] = random() * 200;
		ent.avelocity[2] = random() * 200;
		ent.think = GameUtilAdapters.G_FreeEdictA;
		ent.nextthink = level.time + 30;
		gi.linkentity(ent);
	}

	//=====================================================

	/*QUAKED target_character (0 0 1) ?
	used with target_string (must be on same "team")
	"count" is position in the string (starts at 1)
	*/

	public static void SP_target_character(edict_t self)
	{
		self.movetype = MOVETYPE_PUSH;
		gi.setmodel(self, self.model);
		self.solid = SOLID_BSP;
		self.s.frame = 12;
		gi.linkentity(self);
		return;
	}

	public static void SP_target_string(edict_t self)
	{
		if (self.message == null)
			self.message = "";
		self.use = GameMiscAdapters.target_string_use;
	}

	// don't let field width of any clock messages change, or it
	// could cause an overwrite after a game load

	public static void func_clock_reset(edict_t self)
	{
		self.activator = null;
		if ((self.spawnflags & 1) != 0)
		{
			self.health = 0;
			self.wait = self.count;
		}
		else if ((self.spawnflags & 2) != 0)
		{
			self.health = self.count;
			self.wait = 0;
		}
	}

	public static void func_clock_format_countdown(edict_t self)
	{
		if (self.style == 0)
		{
			self.message = "" + self.health;
			//Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i", self.health);
			return;
		}

		if (self.style == 1)
		{
			self.message = "" + self.health / 60 + ":" + self.health % 60;
			//Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i:%2i", self.health / 60, self.health % 60);
			/*
			if (self.message.charAt(3) == ' ')
				self.message.charAt(3) = '0';
				*/
			return;
		}

		if (self.style == 2)
		{
			self.message = "" + self.health / 3600 + ":" + (self.health - (self.health / 3600) * 3600) / 60 + ":" + self.health % 60;
			/*
			Com_sprintf(
				self.message,
				CLOCK_MESSAGE_SIZE,
				"%2i:%2i:%2i",
				self.health / 3600,
				(self.health - (self.health / 3600) * 3600) / 60,
				self.health % 60);
			if (self.message[3] == ' ')
				self.message[3] = '0';
			if (self.message[6] == ' ')
				self.message[6] = '0';
			*/
			return;
		}
	}

	public static void SP_func_clock(edict_t self)
	{
		if (self.target == null)
		{
			gi.dprintf(self.classname + " with no target at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		if ((self.spawnflags & 2) != 0 && (0 == self.count))
		{
			gi.dprintf(self.classname + " with no count at " + vtos(self.s.origin) + "\n");
			G_FreeEdict(self);
			return;
		}

		if ((self.spawnflags & 1) != 0 && (0 == self.count))
			self.count = 60 * 60;

		func_clock_reset(self);

		//self.message = gi.TagMalloc(CLOCK_MESSAGE_SIZE, TAG_LEVEL);
		self.message = new String();

		self.think = GameMiscAdapters.func_clock_think;

		if ((self.spawnflags & 4) != 0)
			self.use = GameMiscAdapters.func_clock_use;
		else
			self.nextthink = level.time + 1;
	}

	/*QUAKED misc_teleporter (1 0 0) (-32 -32 -24) (32 32 -16)
	Stepping onto this disc will teleport players to the targeted misc_teleporter_dest object.
	*/
	public static void SP_misc_teleporter(edict_t ent)
	{
		edict_t trig;

		if (ent.target == null)
		{
			gi.dprintf("teleporter without a target.\n");
			G_FreeEdict(ent);
			return;
		}

		gi.setmodel(ent, "models/objects/dmspot/tris.md2");
		ent.s.skinnum = 1;
		ent.s.effects = EF_TELEPORTER;
		ent.s.sound = gi.soundindex("world/amb10.wav");
		ent.solid = SOLID_BBOX;

		VectorSet(ent.mins, -32, -32, -24);
		VectorSet(ent.maxs, 32, 32, -16);
		gi.linkentity(ent);

		trig = G_Spawn();
		trig.touch = GameMiscAdapters.teleporter_touch;
		trig.solid = SOLID_TRIGGER;
		trig.target = ent.target;
		trig.owner = ent;
		VectorCopy(ent.s.origin, trig.s.origin);
		VectorSet(trig.mins, -8, -8, 8);
		VectorSet(trig.maxs, 8, 8, 24);
		gi.linkentity(trig);
	}
}
