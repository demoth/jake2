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
// $Id: GameMiscAdapters.java,v 1.3 2004-03-03 22:32:31 rst Exp $

package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.client.M;
import jake2.util.Lib;
import jake2.util.Math3D;

import java.util.Date;

public class GameMiscAdapters
{

	/*QUAKED func_group (0 0 0) ?
	Used to group brushes together just for editor convenience.
	*/

	//=====================================================

	public static EntUseAdapter Use_Areaportal = new EntUseAdapter()
	{
		public void use(edict_t ent, edict_t other, edict_t activator)
		{
			ent.count ^= 1; // toggle state
			//	gi.dprintf ("portalstate: %i = %i\n", ent.style, ent.count);
			GameBase.gi.SetAreaPortalState(ent.style, ent.count != 0);
		}
	};
	/*QUAKED func_areaportal (0 0 0) ?
	
	This is a non-visible object that divides the world into
	areas that are seperated when this portal is not activated.
	Usually enclosed in the middle of a door.
	*/

	static EntThinkAdapter SP_func_areaportal = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			ent.use = Use_Areaportal;
			ent.count = 0; // always start closed;
			return true;
		}
	};
	/*QUAKED path_corner (.5 .3 0) (-8 -8 -8) (8 8 8) TELEPORT
	Target: next path corner
	Pathtarget: gets used when an entity that has
		this path_corner targeted touches it
	*/
	public static EntTouchAdapter path_corner_touch = new EntTouchAdapter()
	{
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			float[] v = { 0, 0, 0 };
			edict_t next;

			if (other.movetarget != self)
				return;

			if (other.enemy != null)
				return;

			if (self.pathtarget != null)
			{
				String savetarget;

				savetarget = self.target;
				self.target = self.pathtarget;
				GameUtil.G_UseTargets(self, other);
				self.target = savetarget;
			}

			if (self.target != null)
				next = GameBase.G_PickTarget(self.target);
			else
				next = null;

			if ((next != null) && (next.spawnflags & 1) != 0)
			{
				Math3D.VectorCopy(next.s.origin, v);
				v[2] += next.mins[2];
				v[2] -= other.mins[2];
				Math3D.VectorCopy(v, other.s.origin);
				next = GameBase.G_PickTarget(next.target);
				other.s.event = Defines.EV_OTHER_TELEPORT;
			}

			other.goalentity = other.movetarget = next;

			if (self.wait != 0)
			{
				other.monsterinfo.pausetime = GameBase.level.time + self.wait;
				other.monsterinfo.stand.think(other);
				return;
			}

			if (other.movetarget == null)
			{
				other.monsterinfo.pausetime = GameBase.level.time + 100000000;
				other.monsterinfo.stand.think(other);
			}
			else
			{
				Math3D.VectorSubtract(other.goalentity.s.origin, other.s.origin, v);
				other.ideal_yaw = Math3D.vectoyaw(v);
			}
		}
	};
	/*QUAKED point_combat (0.5 0.3 0) (-8 -8 -8) (8 8 8) Hold
	Makes this the target of a monster and it will head here
	when first activated before going after the activator.  If
	hold is selected, it will stay here.
	*/
	public static EntTouchAdapter point_combat_touch = new EntTouchAdapter()
	{
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			edict_t activator;

			if (other.movetarget != self)
				return;

			if (self.target != null)
			{
				other.target = self.target;
				other.goalentity = other.movetarget = GameBase.G_PickTarget(other.target);
				if (null == other.goalentity)
				{
					GameBase.gi.dprintf(
						self.classname + " at " + Lib.vtos(self.s.origin) + " target " + self.target + " does not exist\n");
					other.movetarget = self;
				}
				self.target = null;
			}
			else if ((self.spawnflags & 1) != 0 && 0 == (other.flags & (Defines.FL_SWIM | Defines.FL_FLY)))
			{
				other.monsterinfo.pausetime = GameBase.level.time + 100000000;
				other.monsterinfo.aiflags |= Defines.AI_STAND_GROUND;
				other.monsterinfo.stand.think(other);
			}

			if (other.movetarget == self)
			{
				other.target = null;
				other.movetarget = null;
				other.goalentity = other.enemy;
				other.monsterinfo.aiflags &= ~Defines.AI_COMBAT_POINT;
			}

			if (self.pathtarget != null)
			{
				String savetarget;

				savetarget = self.target;
				self.target = self.pathtarget;
				if (other.enemy != null && other.enemy.client != null)
					activator = other.enemy;
				else if (other.oldenemy != null && other.oldenemy.client != null)
					activator = other.oldenemy;
				else if (other.activator != null && other.activator.client != null)
					activator = other.activator;
				else
					activator = other;
				GameUtil.G_UseTargets(self, activator);
				self.target = savetarget;
			}
		}
	};
	/*QUAKED viewthing (0 .5 .8) (-8 -8 -8) (8 8 8)
	Just for the debugging level.  Don't use
	*/
	public static EntThinkAdapter TH_viewthing = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			ent.s.frame = (ent.s.frame + 1) % 7;
			ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
			return true;
		}
	};
	/*QUAKED light (0 1 0) (-8 -8 -8) (8 8 8) START_OFF
	Non-displayed light.
	Default light value is 300.
	Default style is 0.
	If targeted, will toggle between on and off.
	Default _cone value is 10 (used to set size of light for spotlights)
	*/

	public static final int START_OFF = 1;
	public static EntUseAdapter light_use = new EntUseAdapter()
	{

		public void use(edict_t self, edict_t other, edict_t activator)
		{
			if ((self.spawnflags & START_OFF) != 0)
			{
				GameBase.gi.configstring(Defines.CS_LIGHTS + self.style, "m");
				self.spawnflags &= ~START_OFF;
			}
			else
			{
				GameBase.gi.configstring(Defines.CS_LIGHTS + self.style, "a");
				self.spawnflags |= START_OFF;
			}
		}
	};
	/*QUAKED func_wall (0 .5 .8) ? TRIGGER_SPAWN TOGGLE START_ON ANIMATED ANIMATED_FAST
	This is just a solid wall if not inhibited
	
	TRIGGER_SPAWN	the wall will not be present until triggered
					it will then blink in to existance; it will
					kill anything that was in it's way
	
	TOGGLE			only valid for TRIGGER_SPAWN walls
					this allows the wall to be turned on and off
	
	START_ON		only valid for TRIGGER_SPAWN walls
					the wall will initially be present
	*/

	static EntUseAdapter func_wall_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			if (self.solid == Defines.SOLID_NOT)
			{
				self.solid = Defines.SOLID_BSP;
				self.svflags &= ~Defines.SVF_NOCLIENT;
				GameUtil.KillBox(self);
			}
			else
			{
				self.solid = Defines.SOLID_NOT;
				self.svflags |= Defines.SVF_NOCLIENT;
			}
			GameBase.gi.linkentity(self);

			if (0 == (self.spawnflags & 2))
				self.use = null;
		}
	};
	/*QUAKED func_object (0 .5 .8) ? TRIGGER_SPAWN ANIMATED ANIMATED_FAST
	This is solid bmodel that will fall if it's support it removed.
	*/
	static EntTouchAdapter func_object_touch = new EntTouchAdapter()
	{
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
				// only squash thing we fall on top of
	if (plane == null)
				return;
			if (plane.normal[2] < 1.0)
				return;
			if (other.takedamage == Defines.DAMAGE_NO)
				return;
			GameUtil.T_Damage(
				other,
				self,
				self,
				Globals.vec3_origin,
				self.s.origin,
				Globals.vec3_origin,
				self.dmg,
				1,
				0,
				Defines.MOD_CRUSH);
		}
	};
	static EntThinkAdapter func_object_release = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.movetype = Defines.MOVETYPE_TOSS;
			self.touch = func_object_touch;
			return true;
		}
	};
	static EntUseAdapter func_object_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.solid = Defines.SOLID_BSP;
			self.svflags &= ~Defines.SVF_NOCLIENT;
			self.use = null;
			GameUtil.KillBox(self);
			func_object_release.think(self);
		}
	};
	/*QUAKED func_explosive (0 .5 .8) ? Trigger_Spawn ANIMATED ANIMATED_FAST
	Any brush that you want to explode or break apart.  If you want an
	ex0plosion, set dmg and it will do a radius explosion of that amount
	at the center of the bursh.
	
	If targeted it will not be shootable.
	
	health defaults to 100.
	
	mass defaults to 75.  This determines how much debris is emitted when
	it explodes.  You get one large chunk per 100 of mass (up to 8) and
	one small chunk per 25 of mass (up to 16).  So 800 gives the most.
	*/
	public static EntDieAdapter func_explosive_explode = new EntDieAdapter()
	{

		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point)
		{
			float[] origin = { 0, 0, 0 };
			float[] chunkorigin = { 0, 0, 0 };
			float[] size = { 0, 0, 0 };
			int count;
			int mass;

			// bmodel origins are (0 0 0), we need to adjust that here
			Math3D.VectorScale(self.size, 0.5f, size);
			Math3D.VectorAdd(self.absmin, size, origin);
			Math3D.VectorCopy(origin, self.s.origin);

			self.takedamage = Defines.DAMAGE_NO;

			if (self.dmg != 0)
				GameUtil.T_RadiusDamage(self, attacker, self.dmg, null, self.dmg + 40, Defines.MOD_EXPLOSIVE);

			Math3D.VectorSubtract(self.s.origin, inflictor.s.origin, self.velocity);
			Math3D.VectorNormalize(self.velocity);
			Math3D.VectorScale(self.velocity, 150, self.velocity);

			// start chunks towards the center
			Math3D.VectorScale(size, 0.5f, size);

			mass = self.mass;
			if (0 == mass)
				mass = 75;

			// big chunks
			if (mass >= 100)
			{
				count = mass / 100;
				if (count > 8)
					count = 8;
				while (count-- != 0)
				{
					chunkorigin[0] = origin[0] + Lib.crandom() * size[0];
					chunkorigin[1] = origin[1] + Lib.crandom() * size[1];
					chunkorigin[2] = origin[2] + Lib.crandom() * size[2];
					GameAI.ThrowDebris(self, "models/objects/debris1/tris.md2", 1, chunkorigin);
				}
			}

			// small chunks
			count = mass / 25;
			if (count > 16)
				count = 16;
			while (count-- != 0)
			{
				chunkorigin[0] = origin[0] + Lib.crandom() * size[0];
				chunkorigin[1] = origin[1] + Lib.crandom() * size[1];
				chunkorigin[2] = origin[2] + Lib.crandom() * size[2];
				GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", 2, chunkorigin);
			}

			GameUtil.G_UseTargets(self, attacker);

			if (self.dmg != 0)
				GameAI.BecomeExplosion1(self);
			else
				GameUtil.G_FreeEdict(self);
		}
	};
	public static EntUseAdapter func_explosive_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			func_explosive_explode.die(self, self, other, self.health, Globals.vec3_origin);
		}
	};
	public static EntUseAdapter func_explosive_spawn = new EntUseAdapter()
	{

		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.solid = Defines.SOLID_BSP;
			self.svflags &= ~Defines.SVF_NOCLIENT;
			self.use = null;
			GameUtil.KillBox(self);
			GameBase.gi.linkentity(self);
		}
	};
	/*QUAKED misc_explobox (0 .5 .8) (-16 -16 0) (16 16 40)
	Large exploding box.  You can override its mass (100),
	health (80), and dmg (150).
	*/

	public static EntTouchAdapter barrel_touch = new EntTouchAdapter()
	{

		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			float ratio;
			float[] v = { 0, 0, 0 };

			if ((null == other.groundentity) || (other.groundentity == self))
				return;

			ratio = (float) other.mass / (float) self.mass;
			Math3D.VectorSubtract(self.s.origin, other.s.origin, v);
			M.M_walkmove(self, Math3D.vectoyaw(v), 20 * ratio * Defines.FRAMETIME);
		}
	};
	public static EntThinkAdapter barrel_explode = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{

			float[] org = { 0, 0, 0 };
			float spd;
			float[] save = { 0, 0, 0 };

			GameUtil.T_RadiusDamage(self, self.activator, self.dmg, null, self.dmg + 40, Defines.MOD_BARREL);

			Math3D.VectorCopy(self.s.origin, save);
			Math3D.VectorMA(self.absmin, 0.5f, self.size, self.s.origin);

			// a few big chunks
			spd = 1.5f * (float) self.dmg / 200.0f;
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris1/tris.md2", spd, org);
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris1/tris.md2", spd, org);

			// bottom corners
			spd = 1.75f * (float) self.dmg / 200.0f;
			Math3D.VectorCopy(self.absmin, org);
			GameAI.ThrowDebris(self, "models/objects/debris3/tris.md2", spd, org);
			Math3D.VectorCopy(self.absmin, org);
			org[0] += self.size[0];
			GameAI.ThrowDebris(self, "models/objects/debris3/tris.md2", spd, org);
			Math3D.VectorCopy(self.absmin, org);
			org[1] += self.size[1];
			GameAI.ThrowDebris(self, "models/objects/debris3/tris.md2", spd, org);
			Math3D.VectorCopy(self.absmin, org);
			org[0] += self.size[0];
			org[1] += self.size[1];
			GameAI.ThrowDebris(self, "models/objects/debris3/tris.md2", spd, org);

			// a bunch of little chunks
			spd = 2 * self.dmg / 200;
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);
			org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
			org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
			org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
			GameAI.ThrowDebris(self, "models/objects/debris2/tris.md2", spd, org);

			Math3D.VectorCopy(save, self.s.origin);
			if (self.groundentity != null)
				GameAI.BecomeExplosion2(self);
			else
				GameAI.BecomeExplosion1(self);

			return true;
		}
	};
	public static EntDieAdapter barrel_delay = new EntDieAdapter()
	{
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point)
		{

			self.takedamage = Defines.DAMAGE_NO;
			self.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;
			self.think = barrel_explode;
			self.activator = attacker;
		}
	};
	//
	// miscellaneous specialty items
	//

	/*QUAKED misc_blackhole (1 .5 0) (-8 -8 -8) (8 8 8)
	*/

	static EntUseAdapter misc_blackhole_use = new EntUseAdapter()
	{
		public void use(edict_t ent, edict_t other, edict_t activator)
		{
			/*
			gi.WriteByte (svc_temp_entity);
			gi.WriteByte (TE_BOSSTPORT);
			gi.WritePosition (ent.s.origin);
			gi.multicast (ent.s.origin, MULTICAST_PVS);
			*/
			GameUtil.G_FreeEdict(ent);
		}
	};
	static EntThinkAdapter misc_blackhole_think = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{

			if (++self.s.frame < 19)
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			else
			{
				self.s.frame = 0;
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			}
			return true;
		}
	};
	/*QUAKED misc_eastertank (1 .5 0) (-32 -32 -16) (32 32 32)
	*/

	static EntThinkAdapter misc_eastertank_think = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (++self.s.frame < 293)
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			else
			{
				self.s.frame = 254;
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			}
			return true;
		}
	};
	/*QUAKED misc_easterchick (1 .5 0) (-32 -32 0) (32 32 32)
	*/

	static EntThinkAdapter misc_easterchick_think = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (++self.s.frame < 247)
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			else
			{
				self.s.frame = 208;
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			}
			return true;
		}
	};
	/*QUAKED misc_easterchick2 (1 .5 0) (-32 -32 0) (32 32 32)
	*/
	static EntThinkAdapter misc_easterchick2_think = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (++self.s.frame < 287)
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			else
			{
				self.s.frame = 248;
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			}
			return true;
		}
	};
	/*QUAKED monster_commander_body (1 .5 0) (-32 -32 0) (32 32 48)
	Not really a monster, this is the Tank Commander's decapitated body.
	There should be a item_commander_head that has this as it's target.
	*/

	public static EntThinkAdapter commander_body_think = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (++self.s.frame < 24)
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			else
				self.nextthink = 0;

			if (self.s.frame == 22)
				GameBase.gi.sound(self, Defines.CHAN_BODY, GameBase.gi.soundindex("tank/thud.wav"), 1, Defines.ATTN_NORM, 0);
			return true;
		}
	};
	public static EntUseAdapter commander_body_use = new EntUseAdapter()
	{

		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.think = commander_body_think;
			self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			GameBase.gi.sound(self, Defines.CHAN_BODY, GameBase.gi.soundindex("tank/pain.wav"), 1, Defines.ATTN_NORM, 0);
		}
	};
	public static EntThinkAdapter commander_body_drop = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.movetype = Defines.MOVETYPE_TOSS;
			self.s.origin[2] += 2;
			return true;
		}
	};
	/*QUAKED misc_banner (1 .5 0) (-4 -4 -4) (4 4 4)
	The origin is the bottom of the banner.
	The banner is 128 tall.
	*/
	static EntThinkAdapter misc_banner_think = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			ent.s.frame = (ent.s.frame + 1) % 16;
			ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
			return true;
		}
	};
	/*QUAKED misc_deadsoldier (1 .5 0) (-16 -16 0) (16 16 16) ON_BACK ON_STOMACH BACK_DECAP FETAL_POS SIT_DECAP IMPALED
	This is the dead player model. Comes in 6 exciting different poses!
	*/
	static EntDieAdapter misc_deadsoldier_die = new EntDieAdapter()
	{

		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point)
		{
			int n;

			if (self.health > -80)
				return;

			GameBase.gi.sound(self, Defines.CHAN_BODY, GameBase.gi.soundindex("misc/udeath.wav"), 1, Defines.ATTN_NORM, 0);
			for (n = 0; n < 4; n++)
				GameAI.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage, Defines.GIB_ORGANIC);
			GameAI.ThrowHead(self, "models/objects/gibs/head2/tris.md2", damage, Defines.GIB_ORGANIC);
		}
	};
	/*QUAKED misc_viper (1 .5 0) (-16 -16 0) (16 16 32)
	This is the Viper for the flyby bombing.
	It is trigger_spawned, so you must have something use it for it to show up.
	There must be a path for it to follow once it is activated.
	
	"speed"		How fast the Viper should fly
	*/

	static EntUseAdapter misc_viper_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.svflags &= ~Defines.SVF_NOCLIENT;
			self.use = GameFuncAdapters.train_use;
			GameFuncAdapters.train_use.use(self, other, activator);
		}
	};
	/*QUAKED misc_viper_bomb (1 0 0) (-8 -8 -8) (8 8 8)
	"dmg"	how much boom should the bomb make?
	*/
	static EntTouchAdapter misc_viper_bomb_touch = new EntTouchAdapter()
	{

		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			GameUtil.G_UseTargets(self, self.activator);

			self.s.origin[2] = self.absmin[2] + 1;
			GameUtil.T_RadiusDamage(self, self, self.dmg, null, self.dmg + 40, Defines.MOD_BOMB);
			GameAI.BecomeExplosion2(self);
		}
	};
	static EntThinkAdapter misc_viper_bomb_prethink = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{

			float[] v = { 0, 0, 0 };
			float diff;

			self.groundentity = null;

			diff = self.timestamp - GameBase.level.time;
			if (diff < -1.0)
				diff = -1.0f;

			Math3D.VectorScale(self.moveinfo.dir, 1.0f + diff, v);
			v[2] = diff;

			diff = self.s.angles[2];
			Math3D.vectoangles(v, self.s.angles);
			self.s.angles[2] = diff + 10;

			return true;
		}
	};
	static EntUseAdapter misc_viper_bomb_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			edict_t viper = null;

			self.solid = Defines.SOLID_BBOX;
			self.svflags &= ~Defines.SVF_NOCLIENT;
			self.s.effects |= Defines.EF_ROCKET;
			self.use = null;
			self.movetype = Defines.MOVETYPE_TOSS;
			self.prethink = misc_viper_bomb_prethink;
			self.touch = misc_viper_bomb_touch;
			self.activator = activator;

			EdictIterator es = null;

			es = GameBase.G_Find(es, GameBase.findByClass, "misc_viper");
			if (es != null)
				viper = es.o;

			Math3D.VectorScale(viper.moveinfo.dir, viper.moveinfo.speed, self.velocity);

			self.timestamp = GameBase.level.time;
			Math3D.VectorCopy(viper.moveinfo.dir, self.moveinfo.dir);
		}
	};
	/*QUAKED misc_strogg_ship (1 .5 0) (-16 -16 0) (16 16 32)
	This is a Storgg ship for the flybys.
	It is trigger_spawned, so you must have something use it for it to show up.
	There must be a path for it to follow once it is activated.
	
	"speed"		How fast it should fly
	*/

	static EntUseAdapter misc_strogg_ship_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.svflags &= ~Defines.SVF_NOCLIENT;
			self.use = GameFuncAdapters.train_use;
			GameFuncAdapters.train_use.use(self, other, activator);
		}
	};
	/*QUAKED misc_satellite_dish (1 .5 0) (-64 -64 0) (64 64 128)
	*/
	static EntThinkAdapter misc_satellite_dish_think = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.s.frame++;
			if (self.s.frame < 38)
				self.nextthink = GameBase.level.time + Defines.FRAMETIME;
			return true;
		}
	};
	static EntUseAdapter misc_satellite_dish_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			self.s.frame = 0;
			self.think = misc_satellite_dish_think;
			self.nextthink = GameBase.level.time + Defines.FRAMETIME;
		}
	};
	/*QUAKED target_string (0 0 1) (-8 -8 -8) (8 8 8)
	*/

	static EntUseAdapter target_string_use = new EntUseAdapter()
	{
		public void use(edict_t self, edict_t other, edict_t activator)
		{
			edict_t e;
			int n, l;
			char c;

			l = self.message.length();
			for (e = self.teammaster; e != null; e = e.teamchain)
			{
				if (e.count == 0)
					continue;
				n = e.count - 1;
				if (n > l)
				{
					e.s.frame = 12;
					continue;
				}

				c = self.message.charAt(n);
				if (c >= '0' && c <= '9')
					e.s.frame = c - '0';
				else if (c == '-')
					e.s.frame = 10;
				else if (c == ':')
					e.s.frame = 11;
				else
					e.s.frame = 12;
			}
		}
	};
	/*QUAKED func_clock (0 0 1) (-8 -8 -8) (8 8 8) TIMER_UP TIMER_DOWN START_OFF MULTI_USE
	target a target_string with this
	
	The default is to be a time of day clock
	
	TIMER_UP and TIMER_DOWN run for "count" seconds and the fire "pathtarget"
	If START_OFF, this entity must be used before it starts
	
	"style"		0 "xx"
				1 "xx:xx"
				2 "xx:xx:xx"
	*/

	public static final int CLOCK_MESSAGE_SIZE = 16;
	public static EntThinkAdapter func_clock_think = new EntThinkAdapter()
	{

		public boolean think(edict_t self)
		{
			if (null == self.enemy)
			{

				EdictIterator es = null;

				es = GameBase.G_Find(es, GameBase.findByTarget, self.target);
				if (es != null)
					self.enemy = es.o;
				if (self.enemy == null)
					return true;
			}

			if ((self.spawnflags & 1) != 0)
			{
				GameMisc.func_clock_format_countdown(self);
				self.health++;
			}
			else if ((self.spawnflags & 2) != 0)
			{
				GameMisc.func_clock_format_countdown(self);
				self.health--;
			}
			else
			{
				Date d = new Date();
				self.message = "" + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds();

				/*
				struct tm * ltime;
				time_t gmtime;
				
				time(& gmtime);
				ltime = localtime(& gmtime);
				Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i:%2i:%2i", ltime.tm_hour, ltime.tm_min, ltime.tm_sec);
				if (self.message[3] == ' ')
					self.message[3] = '0';
				if (self.message[6] == ' ')
					self.message[6] = '0';
				*/
			}

			self.enemy.message = self.message;
			self.enemy.use.use(self.enemy, self, self);

			if (((self.spawnflags & 1) != 0 && (self.health > self.wait))
				|| ((self.spawnflags & 2) != 0 && (self.health < self.wait)))
			{
				if (self.pathtarget != null)
				{
					String savetarget;
					String savemessage;

					savetarget = self.target;
					savemessage = self.message;
					self.target = self.pathtarget;
					self.message = null;
					GameUtil.G_UseTargets(self, self.activator);
					self.target = savetarget;
					self.message = savemessage;
				}

				if (0 == (self.spawnflags & 8))
					return true;

				GameMisc.func_clock_reset(self);

				if ((self.spawnflags & 4) != 0)
					return true;
			}

			self.nextthink = GameBase.level.time + 1;
			return true;

		}
	};
	public static EntUseAdapter func_clock_use = new EntUseAdapter()
	{

		public void use(edict_t self, edict_t other, edict_t activator)
		{
			if (0 == (self.spawnflags & 8))
				self.use = null;
			if (self.activator != null)
				return;
			self.activator = activator;
			self.think.think(self);
		}
	};
	//=================================================================================

	static EntTouchAdapter teleporter_touch = new EntTouchAdapter()
	{
		public void touch(edict_t self, edict_t other, cplane_t plane, csurface_t surf)
		{
			edict_t dest;
			int i;

			if (other.client == null)
				return;

			EdictIterator es = null;
			dest = GameBase.G_Find(null, GameBase.findByTarget, self.target).o;

			if (dest == null)
			{
				GameBase.gi.dprintf("Couldn't find destination\n");
				return;
			}

			// unlink to make sure it can't possibly interfere with KillBox
			GameBase.gi.unlinkentity(other);

			Math3D.VectorCopy(dest.s.origin, other.s.origin);
			Math3D.VectorCopy(dest.s.origin, other.s.old_origin);
			other.s.origin[2] += 10;

			// clear the velocity and hold them in place briefly
			Math3D.VectorClear(other.velocity);
			other.client.ps.pmove.pm_time = 160 >> 3; // hold time
			other.client.ps.pmove.pm_flags |= Defines.PMF_TIME_TELEPORT;

			// draw the teleport splash at source and on the player
			self.owner.s.event = Defines.EV_PLAYER_TELEPORT;
			other.s.event = Defines.EV_PLAYER_TELEPORT;

			// set angles
			for (i = 0; i < 3; i++)
			{
				other.client.ps.pmove.delta_angles[i] = (short) Math3D.ANGLE2SHORT(dest.s.angles[i] - other.client.resp.cmd_angles[i]);
			}

			Math3D.VectorClear(other.s.angles);
			Math3D.VectorClear(other.client.ps.viewangles);
			Math3D.VectorClear(other.client.v_angle);

			// kill anything at the destination
			GameUtil.KillBox(other);

			GameBase.gi.linkentity(other);
		}
	};
	/*QUAKED misc_teleporter_dest (1 0 0) (-32 -32 -24) (32 32 -16)
	Point teleporters at these.
	*/

	public static EntThinkAdapter SP_misc_teleporter_dest = new EntThinkAdapter()
	{
		public boolean think(edict_t ent)
		{
			GameBase.gi.setmodel(ent, "models/objects/dmspot/tris.md2");
			ent.s.skinnum = 0;
			ent.solid = Defines.SOLID_BBOX;
			//	ent.s.effects |= EF_FLIES;
			Math3D.VectorSet(ent.mins, -32, -32, -24);
			Math3D.VectorSet(ent.maxs, 32, 32, -16);
			GameBase.gi.linkentity(ent);
			return true;
		}
	};
}
