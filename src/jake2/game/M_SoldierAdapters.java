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
// $Id: M_SoldierAdapters.java,v 1.1 2004-07-08 15:58:44 hzi Exp $

package jake2.game;

import jake2.Defines;
import jake2.qcommon.Com;
import jake2.util.*;

public class M_SoldierAdapters
{

	static EntThinkAdapter soldier_cock = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.s.frame == M_Soldier.FRAME_stand322)
				GameBase.gi.sound(self, Defines.CHAN_WEAPON, M_Soldier.sound_cock, 1, Defines.ATTN_IDLE, 0);
			else
				GameBase.gi.sound(self, Defines.CHAN_WEAPON, M_Soldier.sound_cock, 1, Defines.ATTN_NORM, 0);
			return true;
		}
	};
	// ATTACK3 (duck and shoot)

	static EntThinkAdapter soldier_duck_down = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if ((self.monsterinfo.aiflags & Defines.AI_DUCKED) != 0)
				return true;
			self.monsterinfo.aiflags |= Defines.AI_DUCKED;
			self.maxs[2] -= 32;
			self.takedamage = Defines.DAMAGE_YES;
			self.monsterinfo.pausetime = GameBase.level.time + 1;
			GameBase.gi.linkentity(self);
			return true;
		}
	};
	static EntThinkAdapter soldier_duck_up = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			self.monsterinfo.aiflags &= ~Defines.AI_DUCKED;
			self.maxs[2] += 32;
			self.takedamage = Defines.DAMAGE_AIM;
			GameBase.gi.linkentity(self);
			return true;
		}
	};
	static EntThinkAdapter soldier_attack = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.s.skinnum < 4)
			{
				if (Lib.random() < 0.5)
					self.monsterinfo.currentmove = M_Soldier.soldier_move_attack1;
				else
					self.monsterinfo.currentmove = M_Soldier.soldier_move_attack2;
			}
			else
			{
				self.monsterinfo.currentmove = M_Soldier.soldier_move_attack4;
			}
			return true;
		}
	};
	//
	// DUCK
	//

	static EntThinkAdapter soldier_duck_hold = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (GameBase.level.time >= self.monsterinfo.pausetime)
				self.monsterinfo.aiflags &= ~Defines.AI_HOLD_FRAME;
			else
				self.monsterinfo.aiflags |= Defines.AI_HOLD_FRAME;
			return true;
		}
	};
	static EntDodgeAdapter soldier_dodge = new EntDodgeAdapter()
	{
		public void dodge(edict_t self, edict_t attacker, float eta)
		{
			float r;

			r = Lib.random();
			if (r > 0.25)
				return;

			if (self.enemy == null)
				self.enemy = attacker;

			if (GameBase.skill.value == 0)
			{
				self.monsterinfo.currentmove = M_Soldier.soldier_move_duck;
				return;
			}

			self.monsterinfo.pausetime = GameBase.level.time + eta + 0.3f;
			r = Lib.random();

			if (GameBase.skill.value == 1)
			{
				if (r > 0.33)
					self.monsterinfo.currentmove = M_Soldier.soldier_move_duck;
				else
					self.monsterinfo.currentmove = M_Soldier.soldier_move_attack3;
				return;
			}

			if (GameBase.skill.value >= 2)
			{
				if (r > 0.66)
					self.monsterinfo.currentmove = M_Soldier.soldier_move_duck;
				else
					self.monsterinfo.currentmove = M_Soldier.soldier_move_attack3;
				return;
			}

			self.monsterinfo.currentmove = M_Soldier.soldier_move_attack3;
		}
	};
	static EntThinkAdapter soldier_dead = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{

			Math3D.VectorSet(self.mins, -16, -16, -24);
			Math3D.VectorSet(self.maxs, 16, 16, -8);
			self.movetype = Defines.MOVETYPE_TOSS;
			self.svflags |= Defines.SVF_DEADMONSTER;
			self.nextthink = 0;
			GameBase.gi.linkentity(self);
			return true;
		}
	};
	static EntDieAdapter soldier_die = new EntDieAdapter()
	{
		public void die(edict_t self, edict_t inflictor, edict_t attacker, int damage, float[] point)
		{
			int n;

			// check for gib
			if (self.health <= self.gib_health)
			{
				GameBase.gi.sound(self, Defines.CHAN_VOICE, GameBase.gi.soundindex("misc/udeath.wav"), 1, Defines.ATTN_NORM, 0);
				for (n = 0; n < 3; n++)
					GameAI.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage, Defines.GIB_ORGANIC);
				GameAI.ThrowGib(self, "models/objects/gibs/chest/tris.md2", damage, Defines.GIB_ORGANIC);
				GameAI.ThrowHead(self, "models/objects/gibs/head2/tris.md2", damage, Defines.GIB_ORGANIC);
				self.deadflag = Defines.DEAD_DEAD;
				return;
			}

			if (self.deadflag == Defines.DEAD_DEAD)
				return;

			// regular death
			self.deadflag = Defines.DEAD_DEAD;
			self.takedamage = Defines.DAMAGE_YES;
			self.s.skinnum |= 1;

			if (self.s.skinnum == 1)
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_death_light, 1, Defines.ATTN_NORM, 0);
			else if (self.s.skinnum == 3)
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_death, 1, Defines.ATTN_NORM, 0);
			else // (self.s.skinnum == 5)
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_death_ss, 1, Defines.ATTN_NORM, 0);

			if (Math.abs((self.s.origin[2] + self.viewheight) - point[2]) <= 4)
			{
				// head shot
				self.monsterinfo.currentmove = M_Soldier.soldier_move_death3;
				return;
			}

			n = Lib.rand() % 5;
			if (n == 0)
				self.monsterinfo.currentmove = M_Soldier.soldier_move_death1;
			else if (n == 1)
				self.monsterinfo.currentmove = M_Soldier.soldier_move_death2;
			else if (n == 2)
				self.monsterinfo.currentmove = M_Soldier.soldier_move_death4;
			else if (n == 3)
				self.monsterinfo.currentmove = M_Soldier.soldier_move_death5;
			else
				self.monsterinfo.currentmove = M_Soldier.soldier_move_death6;
		}
	};

	static EntThinkAdapter soldier_attack1_refire1 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.s.skinnum > 1)
				return true;

			if (self.enemy.health <= 0)
				return true;

			if (((GameBase.skill.value == 3) && (Lib.random() < 0.5)) || (GameUtil.range(self, self.enemy) == Defines.RANGE_MELEE))
				self.monsterinfo.nextframe = M_Soldier.FRAME_attak102;
			else
				self.monsterinfo.nextframe = M_Soldier.FRAME_attak110;
			return true;
		}
	};

	static EntThinkAdapter soldier_attack1_refire2 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.s.skinnum < 2)
				return true;

			if (self.enemy.health <= 0)
				return true;

			if (((GameBase.skill.value == 3) && (Lib.random() < 0.5)) || (GameUtil.range(self, self.enemy) == Defines.RANGE_MELEE))
				self.monsterinfo.nextframe = M_Soldier.FRAME_attak102;
			return true;
		}
	};

	static EntThinkAdapter soldier_attack2_refire1 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.s.skinnum > 1)
				return true;

			if (self.enemy.health <= 0)
				return true;

			if (((GameBase.skill.value == 3) && (Lib.random() < 0.5)) || (GameUtil.range(self, self.enemy) == Defines.RANGE_MELEE))
				self.monsterinfo.nextframe = M_Soldier.FRAME_attak204;
			else
				self.monsterinfo.nextframe = M_Soldier.FRAME_attak216;
			return true;
		}
	};

	static EntThinkAdapter soldier_attack2_refire2 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.s.skinnum < 2)
				return true;

			if (self.enemy.health <= 0)
				return true;

			if (((GameBase.skill.value == 3) && (Lib.random() < 0.5)) || (GameUtil.range(self, self.enemy) == Defines.RANGE_MELEE))
				self.monsterinfo.nextframe = M_Soldier.FRAME_attak204;
			return true;
		}
	};

	static EntThinkAdapter soldier_attack3_refire = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if ((GameBase.level.time + 0.4) < self.monsterinfo.pausetime)
				self.monsterinfo.nextframe = M_Soldier.FRAME_attak303;
			return true;
		}
	};

	static EntThinkAdapter soldier_attack6_refire = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (self.enemy.health <= 0)
				return true;

			if (GameUtil.range(self, self.enemy) < Defines.RANGE_MID)
				return true;

			if (GameBase.skill.value == 3)
				self.monsterinfo.nextframe = M_Soldier.FRAME_runs03;
			return true;
		}
	};

	// ATTACK6 (run & shoot)
	static EntThinkAdapter soldier_fire8 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			M_Soldier.soldier_fire(self, 7);
			return true;
		}
	};

	// ATTACK1 (blaster/shotgun)

	static EntThinkAdapter soldier_fire1 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			M_Soldier.soldier_fire(self, 0);
			return true;
		}
	};

	// ATTACK2 (blaster/shotgun)

	static EntThinkAdapter soldier_fire2 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			M_Soldier.soldier_fire(self, 1);
			return true;
		}
	};

	static EntThinkAdapter soldier_fire3 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			M_SoldierAdapters.soldier_duck_down.think(self);
			M_Soldier.soldier_fire(self, 2);
			return true;
		}
	};

	// ATTACK4 (machinegun)

	static EntThinkAdapter soldier_fire4 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			M_Soldier.soldier_fire(self, 3);
			//
			//	if (self.enemy.health <= 0)
			//		return;
			//
			//	if ( ((skill.value == 3) && (random() < 0.5)) || (range(self, self.enemy) == RANGE_MELEE) )
			//		self.monsterinfo.nextframe = FRAME_attak402;
			return true;
		}
	};

	//
	// DEATH
	//

	static EntThinkAdapter soldier_fire6 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			M_Soldier.soldier_fire(self, 5);
			return true;
		}
	};

	static EntThinkAdapter soldier_fire7 = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			M_Soldier.soldier_fire(self, 6);
			return true;
		}
	};

	static EntThinkAdapter soldier_idle = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (Lib.random() > 0.8)
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_idle, 1, Defines.ATTN_IDLE, 0);
			return true;
		}
	};

	static EntThinkAdapter soldier_stand = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if ((self.monsterinfo.currentmove == M_Soldier.soldier_move_stand3) || (Lib.random() < 0.8))
				self.monsterinfo.currentmove = M_Soldier.soldier_move_stand1;
			else
				self.monsterinfo.currentmove = M_Soldier.soldier_move_stand3;
			return true;
		}
	};

	//
	// WALK
	//
	static EntThinkAdapter soldier_walk1_random = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (Lib.random() > 0.1)
				self.monsterinfo.nextframe = M_Soldier.FRAME_walk101;
			return true;
		}
	};

	static EntThinkAdapter soldier_walk = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (Lib.random() < 0.5)
				self.monsterinfo.currentmove = M_Soldier.soldier_move_walk1;
			else
				self.monsterinfo.currentmove = M_Soldier.soldier_move_walk2;
			return true;
		}
	};

	static EntThinkAdapter soldier_run = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if ((self.monsterinfo.aiflags & Defines.AI_STAND_GROUND) != 0)
			{
				self.monsterinfo.currentmove = M_Soldier.soldier_move_stand1;
				return true;
			}

			if (self.monsterinfo.currentmove == M_Soldier.soldier_move_walk1
				|| self.monsterinfo.currentmove == M_Soldier.soldier_move_walk2
				|| self.monsterinfo.currentmove == M_Soldier.soldier_move_start_run)
			{
				self.monsterinfo.currentmove = M_Soldier.soldier_move_run;
				int a = 2;
			}
			else
			{
				self.monsterinfo.currentmove = M_Soldier.soldier_move_start_run;
			}
			return true;
		}
	};

	static EntPainAdapter soldier_pain = new EntPainAdapter()
	{
		public void pain(edict_t self, edict_t other, float kick, int damage)
		{
			float r;
			int n;

			if (self.health < (self.max_health / 2))
				self.s.skinnum |= 1;

			if (GameBase.level.time < self.pain_debounce_time)
			{
				if ((self.velocity[2] > 100)
					&& ((self.monsterinfo.currentmove == M_Soldier.soldier_move_pain1)
						|| (self.monsterinfo.currentmove == M_Soldier.soldier_move_pain2)
						|| (self.monsterinfo.currentmove == M_Soldier.soldier_move_pain3)))
					self.monsterinfo.currentmove = M_Soldier.soldier_move_pain4;
				return;
			}

			self.pain_debounce_time = GameBase.level.time + 3;

			n = self.s.skinnum | 1;
			if (n == 1)
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_pain_light, 1, Defines.ATTN_NORM, 0);
			else if (n == 3)
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_pain, 1, Defines.ATTN_NORM, 0);
			else
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_pain_ss, 1, Defines.ATTN_NORM, 0);

			if (self.velocity[2] > 100)
			{
				self.monsterinfo.currentmove = M_Soldier.soldier_move_pain4;
				return;
			}

			if (GameBase.skill.value == 3)
				return; // no pain anims in nightmare

			r = Lib.random();

			if (r < 0.33)
				self.monsterinfo.currentmove = M_Soldier.soldier_move_pain1;
			else if (r < 0.66)
				self.monsterinfo.currentmove = M_Soldier.soldier_move_pain2;
			else
				self.monsterinfo.currentmove = M_Soldier.soldier_move_pain3;
		}
	};

	//
	// SIGHT
	//

	static EntInteractAdapter soldier_sight = new EntInteractAdapter()
	{
		public boolean interact(edict_t self, edict_t other)
		{
			if (Lib.random() < 0.5)
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_sight1, 1, Defines.ATTN_NORM, 0);
			else
				GameBase.gi.sound(self, Defines.CHAN_VOICE, M_Soldier.sound_sight2, 1, Defines.ATTN_NORM, 0);

			if ((GameBase.skill.value > 0) && (GameUtil.range(self, self.enemy) >= Defines.RANGE_MID))
			{
				if (Lib.random() > 0.5)
					self.monsterinfo.currentmove = M_Soldier.soldier_move_attack6;
			}
			return true;
		}
	};

	//
	// SPAWN
	//

	static EntThinkAdapter SP_monster_soldier_x = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{

			self.s.modelindex = GameBase.gi.modelindex("models/monsters/soldier/tris.md2");
			self.monsterinfo.scale = M_Soldier.MODEL_SCALE;
			Math3D.VectorSet(self.mins, -16, -16, -24);
			Math3D.VectorSet(self.maxs, 16, 16, 32);
			self.movetype = Defines.MOVETYPE_STEP;
			self.solid = Defines.SOLID_BBOX;

			M_Soldier.sound_idle = GameBase.gi.soundindex("soldier/solidle1.wav");
			M_Soldier.sound_sight1 = GameBase.gi.soundindex("soldier/solsght1.wav");
			M_Soldier.sound_sight2 = GameBase.gi.soundindex("soldier/solsrch1.wav");
			M_Soldier.sound_cock = GameBase.gi.soundindex("infantry/infatck3.wav");

			self.mass = 100;

			self.pain = soldier_pain;
			self.die = M_SoldierAdapters.soldier_die;

			self.monsterinfo.stand = soldier_stand;
			self.monsterinfo.walk = soldier_walk;
			self.monsterinfo.run = soldier_run;
			self.monsterinfo.dodge = M_SoldierAdapters.soldier_dodge;
			self.monsterinfo.attack = M_SoldierAdapters.soldier_attack;
			self.monsterinfo.melee = null;
			self.monsterinfo.sight = soldier_sight;

			GameBase.gi.linkentity(self);

			self.monsterinfo.stand.think(self);

			GameAIAdapters.walkmonster_start.think(self);
			return true;
		}
	};

	/*QUAKED monster_soldier_light (1 .5 0) (-16 -16 -24) (16 16 32) Ambush Trigger_Spawn Sight
	*/
	static EntThinkAdapter SP_monster_soldier_light = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (GameBase.deathmatch.value != 0)
			{
				GameUtil.G_FreeEdict(self);
				return true;
			}

			SP_monster_soldier_x.think(self);

			M_Soldier.sound_pain_light = GameBase.gi.soundindex("soldier/solpain2.wav");
			M_Soldier.sound_death_light = GameBase.gi.soundindex("soldier/soldeth2.wav");
			GameBase.gi.modelindex("models/objects/laser/tris.md2");
			GameBase.gi.soundindex("misc/lasfly.wav");
			GameBase.gi.soundindex("soldier/solatck2.wav");

			self.s.skinnum = 0;
			self.health = 20;
			self.gib_health = -30;
			return true;
		}
	};

	/*QUAKED monster_soldier (1 .5 0) (-16 -16 -24) (16 16 32) Ambush Trigger_Spawn Sight
	*/

	static EntThinkAdapter SP_monster_soldier = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (GameBase.deathmatch.value != 0)
			{
				GameUtil.G_FreeEdict(self);
				return true;
			}

			SP_monster_soldier_x.think(self);

			M_Soldier.sound_pain = GameBase.gi.soundindex("soldier/solpain1.wav");
			M_Soldier.sound_death = GameBase.gi.soundindex("soldier/soldeth1.wav");
			GameBase.gi.soundindex("soldier/solatck1.wav");

			self.s.skinnum = 2;
			self.health = 30;
			self.gib_health = -30;
			return true;
		}
	};

	/*QUAKED monster_soldier_ss (1 .5 0) (-16 -16 -24) (16 16 32) Ambush Trigger_Spawn Sight
	*/
	static EntThinkAdapter SP_monster_soldier_ss = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			if (GameBase.deathmatch.value != 0)
			{
				GameUtil.G_FreeEdict(self);
				return true;
			}

			SP_monster_soldier_x.think(self);

			M_Soldier.sound_pain_ss = GameBase.gi.soundindex("soldier/solpain3.wav");
			M_Soldier.sound_death_ss = GameBase.gi.soundindex("soldier/soldeth3.wav");
			GameBase.gi.soundindex("soldier/solatck3.wav");

			self.s.skinnum = 4;
			self.health = 40;
			self.gib_health = -30;
			return true;
		}
	};
}
