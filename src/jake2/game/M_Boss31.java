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

// Created on 13.11.2003 by RST.

package jake2.game;

public class M_Boss31 extends GameWeapon {

	public final static int FRAME_attak101= 0;
	public final static int FRAME_attak102= 1;
	public final static int FRAME_attak103= 2;
	public final static int FRAME_attak104= 3;
	public final static int FRAME_attak105= 4;
	public final static int FRAME_attak106= 5;
	public final static int FRAME_attak107= 6;
	public final static int FRAME_attak108= 7;
	public final static int FRAME_attak109= 8;
	public final static int FRAME_attak110= 9;
	public final static int FRAME_attak111= 10;
	public final static int FRAME_attak112= 11;
	public final static int FRAME_attak113= 12;
	public final static int FRAME_attak114= 13;
	public final static int FRAME_attak115= 14;
	public final static int FRAME_attak116= 15;
	public final static int FRAME_attak117= 16;
	public final static int FRAME_attak118= 17;
	public final static int FRAME_attak201= 18;
	public final static int FRAME_attak202= 19;
	public final static int FRAME_attak203= 20;
	public final static int FRAME_attak204= 21;
	public final static int FRAME_attak205= 22;
	public final static int FRAME_attak206= 23;
	public final static int FRAME_attak207= 24;
	public final static int FRAME_attak208= 25;
	public final static int FRAME_attak209= 26;
	public final static int FRAME_attak210= 27;
	public final static int FRAME_attak211= 28;
	public final static int FRAME_attak212= 29;
	public final static int FRAME_attak213= 30;
	public final static int FRAME_death01= 31;
	public final static int FRAME_death02= 32;
	public final static int FRAME_death03= 33;
	public final static int FRAME_death04= 34;
	public final static int FRAME_death05= 35;
	public final static int FRAME_death06= 36;
	public final static int FRAME_death07= 37;
	public final static int FRAME_death08= 38;
	public final static int FRAME_death09= 39;
	public final static int FRAME_death10= 40;
	public final static int FRAME_death11= 41;
	public final static int FRAME_death12= 42;
	public final static int FRAME_death13= 43;
	public final static int FRAME_death14= 44;
	public final static int FRAME_death15= 45;
	public final static int FRAME_death16= 46;
	public final static int FRAME_death17= 47;
	public final static int FRAME_death18= 48;
	public final static int FRAME_death19= 49;
	public final static int FRAME_death20= 50;
	public final static int FRAME_death21= 51;
	public final static int FRAME_death22= 52;
	public final static int FRAME_death23= 53;
	public final static int FRAME_death24= 54;
	public final static int FRAME_death25= 55;
	public final static int FRAME_death26= 56;
	public final static int FRAME_death27= 57;
	public final static int FRAME_death28= 58;
	public final static int FRAME_death29= 59;
	public final static int FRAME_death30= 60;
	public final static int FRAME_death31= 61;
	public final static int FRAME_death32= 62;
	public final static int FRAME_death33= 63;
	public final static int FRAME_death34= 64;
	public final static int FRAME_death35= 65;
	public final static int FRAME_death36= 66;
	public final static int FRAME_death37= 67;
	public final static int FRAME_death38= 68;
	public final static int FRAME_death39= 69;
	public final static int FRAME_death40= 70;
	public final static int FRAME_death41= 71;
	public final static int FRAME_death42= 72;
	public final static int FRAME_death43= 73;
	public final static int FRAME_death44= 74;
	public final static int FRAME_death45= 75;
	public final static int FRAME_death46= 76;
	public final static int FRAME_death47= 77;
	public final static int FRAME_death48= 78;
	public final static int FRAME_death49= 79;
	public final static int FRAME_death50= 80;
	public final static int FRAME_pain101= 81;
	public final static int FRAME_pain102= 82;
	public final static int FRAME_pain103= 83;
	public final static int FRAME_pain201= 84;
	public final static int FRAME_pain202= 85;
	public final static int FRAME_pain203= 86;
	public final static int FRAME_pain301= 87;
	public final static int FRAME_pain302= 88;
	public final static int FRAME_pain303= 89;
	public final static int FRAME_pain304= 90;
	public final static int FRAME_pain305= 91;
	public final static int FRAME_pain306= 92;
	public final static int FRAME_pain307= 93;
	public final static int FRAME_pain308= 94;
	public final static int FRAME_pain309= 95;
	public final static int FRAME_pain310= 96;
	public final static int FRAME_pain311= 97;
	public final static int FRAME_pain312= 98;
	public final static int FRAME_pain313= 99;
	public final static int FRAME_pain314= 100;
	public final static int FRAME_pain315= 101;
	public final static int FRAME_pain316= 102;
	public final static int FRAME_pain317= 103;
	public final static int FRAME_pain318= 104;
	public final static int FRAME_pain319= 105;
	public final static int FRAME_pain320= 106;
	public final static int FRAME_pain321= 107;
	public final static int FRAME_pain322= 108;
	public final static int FRAME_pain323= 109;
	public final static int FRAME_pain324= 110;
	public final static int FRAME_pain325= 111;
	public final static int FRAME_stand01= 112;
	public final static int FRAME_stand02= 113;
	public final static int FRAME_stand03= 114;
	public final static int FRAME_stand04= 115;
	public final static int FRAME_stand05= 116;
	public final static int FRAME_stand06= 117;
	public final static int FRAME_stand07= 118;
	public final static int FRAME_stand08= 119;
	public final static int FRAME_stand09= 120;
	public final static int FRAME_stand10= 121;
	public final static int FRAME_stand11= 122;
	public final static int FRAME_stand12= 123;
	public final static int FRAME_stand13= 124;
	public final static int FRAME_stand14= 125;
	public final static int FRAME_stand15= 126;
	public final static int FRAME_stand16= 127;
	public final static int FRAME_stand17= 128;
	public final static int FRAME_stand18= 129;
	public final static int FRAME_stand19= 130;
	public final static int FRAME_stand20= 131;
	public final static int FRAME_stand21= 132;
	public final static int FRAME_stand22= 133;
	public final static int FRAME_stand23= 134;
	public final static int FRAME_stand24= 135;
	public final static int FRAME_stand25= 136;
	public final static int FRAME_stand26= 137;
	public final static int FRAME_stand27= 138;
	public final static int FRAME_stand28= 139;
	public final static int FRAME_stand29= 140;
	public final static int FRAME_stand30= 141;
	public final static int FRAME_stand31= 142;
	public final static int FRAME_stand32= 143;
	public final static int FRAME_stand33= 144;
	public final static int FRAME_stand34= 145;
	public final static int FRAME_stand35= 146;
	public final static int FRAME_stand36= 147;
	public final static int FRAME_stand37= 148;
	public final static int FRAME_stand38= 149;
	public final static int FRAME_stand39= 150;
	public final static int FRAME_stand40= 151;
	public final static int FRAME_stand41= 152;
	public final static int FRAME_stand42= 153;
	public final static int FRAME_stand43= 154;
	public final static int FRAME_stand44= 155;
	public final static int FRAME_stand45= 156;
	public final static int FRAME_stand46= 157;
	public final static int FRAME_stand47= 158;
	public final static int FRAME_stand48= 159;
	public final static int FRAME_stand49= 160;
	public final static int FRAME_stand50= 161;
	public final static int FRAME_stand51= 162;
	public final static int FRAME_walk01= 163;
	public final static int FRAME_walk02= 164;
	public final static int FRAME_walk03= 165;
	public final static int FRAME_walk04= 166;
	public final static int FRAME_walk05= 167;
	public final static int FRAME_walk06= 168;
	public final static int FRAME_walk07= 169;
	public final static int FRAME_walk08= 170;
	public final static int FRAME_walk09= 171;
	public final static int FRAME_walk10= 172;
	public final static int FRAME_walk11= 173;
	public final static int FRAME_walk12= 174;
	public final static int FRAME_walk13= 175;
	public final static int FRAME_walk14= 176;
	public final static int FRAME_walk15= 177;
	public final static int FRAME_walk16= 178;
	public final static int FRAME_walk17= 179;
	public final static int FRAME_walk18= 180;
	public final static int FRAME_walk19= 181;
	public final static int FRAME_walk20= 182;
	public final static int FRAME_walk21= 183;
	public final static int FRAME_walk22= 184;
	public final static int FRAME_walk23= 185;
	public final static int FRAME_walk24= 186;
	public final static int FRAME_walk25= 187;

	public final static float MODEL_SCALE= 1.000000f;

	/*
	==============================================================================
	
	jorg
	
	==============================================================================
	*/

	static int sound_pain1;
	static int sound_pain2;
	static int sound_pain3;
	static int sound_idle;
	static int sound_death;
	static int sound_search1;
	static int sound_search2;
	static int sound_search3;
	static int sound_attack1;
	static int sound_attack2;
	static int sound_firegun;
	static int sound_step_left;
	static int sound_step_right;
	static int sound_death_hit;

	/*
	static EntThinkAdapter xxx = new EntThinkAdapter()
	{
		public boolean think(edict_t self)
		{
			return true;
		}
	};
	*/

	static EntThinkAdapter jorg_search= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			float r;

			r= random();

			if (r <= 0.3)
				gi.sound(self, CHAN_VOICE, sound_search1, 1, ATTN_NORM, 0);
			else if (r <= 0.6)
				gi.sound(self, CHAN_VOICE, sound_search2, 1, ATTN_NORM, 0);
			else
				gi.sound(self, CHAN_VOICE, sound_search3, 1, ATTN_NORM, 0);
			return true;
		}
	};

	static EntThinkAdapter jorg_idle= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			gi.sound(self, CHAN_VOICE, sound_idle, 1, ATTN_NORM, 0);
			return true;
		}
	};

	static EntThinkAdapter jorg_death_hit= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			gi.sound(self, CHAN_BODY, sound_death_hit, 1, ATTN_NORM, 0);
			return true;
		}
	};

	static EntThinkAdapter jorg_step_left= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			gi.sound(self, CHAN_BODY, sound_step_left, 1, ATTN_NORM, 0);
			return true;
		}
	};

	static EntThinkAdapter jorg_step_right= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			gi.sound(self, CHAN_BODY, sound_step_right, 1, ATTN_NORM, 0);
			return true;
		}
	};

	static EntThinkAdapter jorg_stand= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.monsterinfo.currentmove= jorg_move_stand;
			return true;
		}
	};

	static EntThinkAdapter jorg_reattack1= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if (visible(self, self.enemy))
				if (random() < 0.9)
					self.monsterinfo.currentmove= jorg_move_attack1;
				else {
					self.s.sound= 0;
					self.monsterinfo.currentmove= jorg_move_end_attack1;
				}
			else {
				self.s.sound= 0;
				self.monsterinfo.currentmove= jorg_move_end_attack1;
			}
			return true;
		}
	};

	static EntThinkAdapter jorg_attack1= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.monsterinfo.currentmove= jorg_move_attack1;
			return true;
		}
	};

	static EntPainAdapter jorg_pain= new EntPainAdapter() {
		public void pain(edict_t self, edict_t other, float kick, int damage) {
			if (self.health < (self.max_health / 2))
				self.s.skinnum= 1;

			self.s.sound= 0;

			if (level.time < self.pain_debounce_time)
				return;

			// Lessen the chance of him going into his pain frames if he takes little damage
			if (damage <= 40)
				if (random() <= 0.6)
					return;

			/* 
			If he's entering his attack1 or using attack1, lessen the chance of him
			going into pain
			*/

			if ((self.s.frame >= FRAME_attak101) && (self.s.frame <= FRAME_attak108))
				if (random() <= 0.005)
					return;

			if ((self.s.frame >= FRAME_attak109) && (self.s.frame <= FRAME_attak114))
				if (random() <= 0.00005)
					return;

			if ((self.s.frame >= FRAME_attak201) && (self.s.frame <= FRAME_attak208))
				if (random() <= 0.005)
					return;

			self.pain_debounce_time= level.time + 3;
			if (skill.value == 3)
				return; // no pain anims in nightmare

			if (damage <= 50) {
				gi.sound(self, CHAN_VOICE, sound_pain1, 1, ATTN_NORM, 0);
				self.monsterinfo.currentmove= jorg_move_pain1;
			} else if (damage <= 100) {
				gi.sound(self, CHAN_VOICE, sound_pain2, 1, ATTN_NORM, 0);
				self.monsterinfo.currentmove= jorg_move_pain2;
			} else {
				if (random() <= 0.3) {
					gi.sound(self, CHAN_VOICE, sound_pain3, 1, ATTN_NORM, 0);
					self.monsterinfo.currentmove= jorg_move_pain3;
				}
			}

		}
	};

	static EntThinkAdapter jorgBFG= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 };

			float[] start= { 0, 0, 0 };
			float[] dir= { 0, 0, 0 };
			float[] vec= { 0, 0, 0 };

			AngleVectors(self.s.angles, forward, right, null);
			G_ProjectSource(
				self.s.origin,
				monster_flash_offset[MZ2_JORG_BFG_1],
				forward,
				right,
				start);

			VectorCopy(self.enemy.s.origin, vec);
			vec[2] += self.enemy.viewheight;
			VectorSubtract(vec, start, dir);
			VectorNormalize(dir);
			gi.sound(self, CHAN_VOICE, sound_attack2, 1, ATTN_NORM, 0);
			/*void monster_fire_bfg (edict_t self, 
									 float []  start, 
									 float []  aimdir, 
									 int damage, 
									 int speed, 
									 int kick, 
									 float damage_radius, 
									 int flashtype)*/
			monster_fire_bfg(self, start, dir, 50, 300, 100, 200, MZ2_JORG_BFG_1);
			return true;
		}
	};

	static EntThinkAdapter jorg_firebullet_right= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 }, target= { 0, 0, 0 };
			float[] start= { 0, 0, 0 };

			AngleVectors(self.s.angles, forward, right, null);
			G_ProjectSource(
				self.s.origin,
				monster_flash_offset[MZ2_JORG_MACHINEGUN_R1],
				forward,
				right,
				start);

			VectorMA(self.enemy.s.origin, -0.2f, self.enemy.velocity, target);
			target[2] += self.enemy.viewheight;
			VectorSubtract(target, start, forward);
			VectorNormalize(forward);

			monster_fire_bullet(
				self,
				start,
				forward,
				6,
				4,
				DEFAULT_BULLET_HSPREAD,
				DEFAULT_BULLET_VSPREAD,
				MZ2_JORG_MACHINEGUN_R1);
			return true;
		}
	};

	static EntThinkAdapter xxx= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			return true;
		}
	};

	static EntThinkAdapter jorg_firebullet_left= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			float[] forward= { 0, 0, 0 }, right= { 0, 0, 0 }, target= { 0, 0, 0 };
			float[] start= { 0, 0, 0 };

			AngleVectors(self.s.angles, forward, right, null);
			G_ProjectSource(
				self.s.origin,
				monster_flash_offset[MZ2_JORG_MACHINEGUN_L1],
				forward,
				right,
				start);

			VectorMA(self.enemy.s.origin, -0.2f, self.enemy.velocity, target);
			target[2] += self.enemy.viewheight;
			VectorSubtract(target, start, forward);
			VectorNormalize(forward);

			monster_fire_bullet(
				self,
				start,
				forward,
				6,
				4,
				DEFAULT_BULLET_HSPREAD,
				DEFAULT_BULLET_VSPREAD,
				MZ2_JORG_MACHINEGUN_L1);
			return true;
		}
	};

	static EntThinkAdapter jorg_firebullet= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			jorg_firebullet_left.think(self);
			jorg_firebullet_right.think(self);
			return true;
		}
	};

	static EntThinkAdapter jorg_attack= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			float[] vec= { 0, 0, 0 };
			float range= 0;

			VectorSubtract(self.enemy.s.origin, self.s.origin, vec);
			range= VectorLength(vec);

			if (random() <= 0.75) {
				gi.sound(self, CHAN_VOICE, sound_attack1, 1, ATTN_NORM, 0);
				self.s.sound= gi.soundindex("boss3/w_loop.wav");
				self.monsterinfo.currentmove= jorg_move_start_attack1;
			} else {
				gi.sound(self, CHAN_VOICE, sound_attack2, 1, ATTN_NORM, 0);
				self.monsterinfo.currentmove= jorg_move_attack2;
			}
			return true;
		}
	};

	/** Was disabled. RST. */
	static EntThinkAdapter jorg_dead= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			/*
							edict_t  tempent;
					
								//VectorSet (self.mins, -16, -16, -24);
								//VectorSet (self.maxs, 16, 16, -8);
							   // Jorg is on modelindex2. Do not clear him.
							VectorSet(
								self.mins,
								-60,
								-60,
								0);
							VectorSet(self.maxs, 60, 60, 72);
							self.movetype= MOVETYPE_TOSS;
							self.nextthink= 0;
							gi.linkentity(self);
					
							tempent= G_Spawn();
							VectorCopy(self.s.origin, tempent.s.origin);
							VectorCopy(self.s.angles, tempent.s.angles);
							tempent.killtarget= self.killtarget;
							tempent.target= self.target;
							tempent.activator= self.enemy;
							self.killtarget= 0;
							self.target= 0;
							SP_monster_makron(tempent);
							
							*/
			return true;
		}
	};

	static EntDieAdapter jorg_die= new EntDieAdapter() {
		public void die(
			edict_t self,
			edict_t inflictor,
			edict_t attacker,
			int damage,
			float[] point) {
			gi.sound(self, CHAN_VOICE, sound_death, 1, ATTN_NORM, 0);
			self.deadflag= DEAD_DEAD;
			self.takedamage= DAMAGE_NO;
			self.s.sound= 0;
			self.count= 0;
			self.monsterinfo.currentmove= jorg_move_death;
			return;
		}
	};

	static EntThinkAdapter Jorg_CheckAttack= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			float[] spot1= { 0, 0, 0 }, spot2= { 0, 0, 0 };
			float[] temp= { 0, 0, 0 };
			float chance;
			trace_t tr;
			boolean enemy_infront;
			int enemy_range;
			float enemy_yaw;

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
						CONTENTS_SOLID | CONTENTS_MONSTER | CONTENTS_SLIME | CONTENTS_LAVA);

				// do we have a clear shot?
				if (tr.ent != self.enemy)
					return false;
			}

			enemy_infront= infront(self, self.enemy);
			enemy_range= range(self, self.enemy);
			VectorSubtract(self.enemy.s.origin, self.s.origin, temp);
			enemy_yaw= vectoyaw(temp);

			self.ideal_yaw= enemy_yaw;

			// melee attack
			if (enemy_range == RANGE_MELEE) {
				if (self.monsterinfo.melee != null)
					self.monsterinfo.attack_state= AS_MELEE;
				else
					self.monsterinfo.attack_state= AS_MISSILE;
				return true;
			}

			//	   missile attack ?
			if (self.monsterinfo.attack == null)
				return false;

			if (level.time < self.monsterinfo.attack_finished)
				return false;

			if (enemy_range == RANGE_FAR)
				return false;

			if ((self.monsterinfo.aiflags & AI_STAND_GROUND) != 0) {
				chance= 0.4f;
			} else if (enemy_range == RANGE_MELEE) {
				chance= 0.8f;
			} else if (enemy_range == RANGE_NEAR) {
				chance= 0.4f;
			} else if (enemy_range == RANGE_MID) {
				chance= 0.2f;
			} else {
				return false;
			}

			if (random() < chance) {
				self.monsterinfo.attack_state= AS_MISSILE;
				self.monsterinfo.attack_finished= level.time + 2 * random();
				return true;
			}

			if ((self.flags & FL_FLY) != 0) {
				if (random() < 0.3)
					self.monsterinfo.attack_state= AS_SLIDING;
				else
					self.monsterinfo.attack_state= AS_STRAIGHT;
			}

			return false;
		}
	};

	//
	//	   stand
	//

	static mframe_t jorg_frames_stand[]=
		new mframe_t[] {
			new mframe_t(ai_stand, 0, jorg_idle),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
		// 10
		new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
		// 20
		new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
		// 30
		new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 19, null),
			new mframe_t(ai_stand, 11, jorg_step_left),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 6, null),
			new mframe_t(ai_stand, 9, jorg_step_right),
			new mframe_t(ai_stand, 0, null),
		// 40
		new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, -2, null),
			new mframe_t(ai_stand, -17, jorg_step_left),
			new mframe_t(ai_stand, 0, null),
			new mframe_t(ai_stand, -12, null),
		// 50
		new mframe_t(ai_stand, -14, jorg_step_right) // 51
	};
	static mmove_t jorg_move_stand=
		new mmove_t(FRAME_stand01, FRAME_stand51, jorg_frames_stand, null);

	static mframe_t jorg_frames_run[]=
		new mframe_t[] {
			new mframe_t(ai_run, 17, jorg_step_left),
			new mframe_t(ai_run, 0, null),
			new mframe_t(ai_run, 0, null),
			new mframe_t(ai_run, 0, null),
			new mframe_t(ai_run, 12, null),
			new mframe_t(ai_run, 8, null),
			new mframe_t(ai_run, 10, null),
			new mframe_t(ai_run, 33, jorg_step_right),
			new mframe_t(ai_run, 0, null),
			new mframe_t(ai_run, 0, null),
			new mframe_t(ai_run, 0, null),
			new mframe_t(ai_run, 9, null),
			new mframe_t(ai_run, 9, null),
			new mframe_t(ai_run, 9, null)};
	static mmove_t jorg_move_run= new mmove_t(FRAME_walk06, FRAME_walk19, jorg_frames_run, null);

	//
	//	   walk
	//

	static mframe_t jorg_frames_start_walk[]=
		new mframe_t[] {
			new mframe_t(ai_walk, 5, null),
			new mframe_t(ai_walk, 6, null),
			new mframe_t(ai_walk, 7, null),
			new mframe_t(ai_walk, 9, null),
			new mframe_t(ai_walk, 15, null)};
	static mmove_t jorg_move_start_walk=
		new mmove_t(FRAME_walk01, FRAME_walk05, jorg_frames_start_walk, null);

	static mframe_t jorg_frames_walk[]=
		new mframe_t[] {
			new mframe_t(ai_walk, 17, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 12, null),
			new mframe_t(ai_walk, 8, null),
			new mframe_t(ai_walk, 10, null),
			new mframe_t(ai_walk, 33, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 9, null),
			new mframe_t(ai_walk, 9, null),
			new mframe_t(ai_walk, 9, null)};
	static mmove_t jorg_move_walk= new mmove_t(FRAME_walk06, FRAME_walk19, jorg_frames_walk, null);

	static mframe_t jorg_frames_end_walk[]=
		new mframe_t[] {
			new mframe_t(ai_walk, 11, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 0, null),
			new mframe_t(ai_walk, 8, null),
			new mframe_t(ai_walk, -8, null)};
	static mmove_t jorg_move_end_walk=
		new mmove_t(FRAME_walk20, FRAME_walk25, jorg_frames_end_walk, null);

	static EntThinkAdapter jorg_walk= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			self.monsterinfo.currentmove= jorg_move_walk;
			return true;
		}
	};

	static EntThinkAdapter jorg_run= new EntThinkAdapter() {
		public boolean think(edict_t self) {
			if ((self.monsterinfo.aiflags & AI_STAND_GROUND) != 0)
				self.monsterinfo.currentmove= jorg_move_stand;
			else
				self.monsterinfo.currentmove= jorg_move_run;
			return true;
		}
	};

	static mframe_t jorg_frames_pain3[]=
		new mframe_t[] {
			new mframe_t(ai_move, -28, null),
			new mframe_t(ai_move, -6, null),
			new mframe_t(ai_move, -3, jorg_step_left),
			new mframe_t(ai_move, -9, null),
			new mframe_t(ai_move, 0, jorg_step_right),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, -7, null),
			new mframe_t(ai_move, 1, null),
			new mframe_t(ai_move, -11, null),
			new mframe_t(ai_move, -4, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 10, null),
			new mframe_t(ai_move, 11, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 10, null),
			new mframe_t(ai_move, 3, null),
			new mframe_t(ai_move, 10, null),
			new mframe_t(ai_move, 7, jorg_step_left),
			new mframe_t(ai_move, 17, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, jorg_step_right)};

	static mmove_t jorg_move_pain3=
		new mmove_t(FRAME_pain301, FRAME_pain325, jorg_frames_pain3, jorg_run);

	static mframe_t jorg_frames_pain2[]=
		new mframe_t[] {
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null)};

	static mmove_t jorg_move_pain2=
		new mmove_t(FRAME_pain201, FRAME_pain203, jorg_frames_pain2, jorg_run);

	static mframe_t jorg_frames_pain1[]=
		new mframe_t[] {
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null)};
	static mmove_t jorg_move_pain1=
		new mmove_t(FRAME_pain101, FRAME_pain103, jorg_frames_pain1, jorg_run);

	static mframe_t jorg_frames_death1[]=
		new mframe_t[] {
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
		// 10
		new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
		// 20
		new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
		// 30
		new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
		// 40
		new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, M_Boss32.MakronToss),
			new mframe_t(ai_move, 0, BossExplode) // 50
	};

	static mmove_t jorg_move_death=
		new mmove_t(FRAME_death01, FRAME_death50, jorg_frames_death1, jorg_dead);

	static mframe_t jorg_frames_attack2[]=
		new mframe_t[] {
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, jorgBFG),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null)};

	static mmove_t jorg_move_attack2=
		new mmove_t(FRAME_attak201, FRAME_attak213, jorg_frames_attack2, jorg_run);

	static mframe_t jorg_frames_start_attack1[]=
		new mframe_t[] {
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null),
			new mframe_t(ai_charge, 0, null)};

	static mmove_t jorg_move_start_attack1=
		new mmove_t(FRAME_attak101, FRAME_attak108, jorg_frames_start_attack1, jorg_attack1);

	static mframe_t jorg_frames_attack1[]=
		new mframe_t[] {
			new mframe_t(ai_charge, 0, jorg_firebullet),
			new mframe_t(ai_charge, 0, jorg_firebullet),
			new mframe_t(ai_charge, 0, jorg_firebullet),
			new mframe_t(ai_charge, 0, jorg_firebullet),
			new mframe_t(ai_charge, 0, jorg_firebullet),
			new mframe_t(ai_charge, 0, jorg_firebullet)};

	static mmove_t jorg_move_attack1=
		new mmove_t(FRAME_attak109, FRAME_attak114, jorg_frames_attack1, jorg_reattack1);

	static mframe_t jorg_frames_end_attack1[]=
		new mframe_t[] {
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null),
			new mframe_t(ai_move, 0, null)};

	static mmove_t jorg_move_end_attack1=
		new mmove_t(FRAME_attak115, FRAME_attak118, jorg_frames_end_attack1, jorg_run);

	/*QUAKED monster_jorg (1 .5 0) (-80 -80 0) (90 90 140) Ambush Trigger_Spawn Sight
	*/
	static void SP_monster_jorg(edict_t self) {
		if (deathmatch.value != 0) {
			G_FreeEdict(self);
			return;
		}

		sound_pain1= gi.soundindex("boss3/bs3pain1.wav");
		sound_pain2= gi.soundindex("boss3/bs3pain2.wav");
		sound_pain3= gi.soundindex("boss3/bs3pain3.wav");
		sound_death= gi.soundindex("boss3/bs3deth1.wav");
		sound_attack1= gi.soundindex("boss3/bs3atck1.wav");
		sound_attack2= gi.soundindex("boss3/bs3atck2.wav");
		sound_search1= gi.soundindex("boss3/bs3srch1.wav");
		sound_search2= gi.soundindex("boss3/bs3srch2.wav");
		sound_search3= gi.soundindex("boss3/bs3srch3.wav");
		sound_idle= gi.soundindex("boss3/bs3idle1.wav");
		sound_step_left= gi.soundindex("boss3/step1.wav");
		sound_step_right= gi.soundindex("boss3/step2.wav");
		sound_firegun= gi.soundindex("boss3/xfire.wav");
		sound_death_hit= gi.soundindex("boss3/d_hit.wav");

		M_Boss32.MakronPrecache();

		self.movetype= MOVETYPE_STEP;
		self.solid= SOLID_BBOX;
		self.s.modelindex= gi.modelindex("models/monsters/boss3/rider/tris.md2");
		self.s.modelindex2= gi.modelindex("models/monsters/boss3/jorg/tris.md2");
		VectorSet(self.mins, -80, -80, 0);
		VectorSet(self.maxs, 80, 80, 140);

		self.health= 3000;
		self.gib_health= -2000;
		self.mass= 1000;

		self.pain= jorg_pain;
		self.die= jorg_die;
		self.monsterinfo.stand= jorg_stand;
		self.monsterinfo.walk= jorg_walk;
		self.monsterinfo.run= jorg_run;
		self.monsterinfo.dodge= null;
		self.monsterinfo.attack= jorg_attack;
		self.monsterinfo.search= jorg_search;
		self.monsterinfo.melee= null;
		self.monsterinfo.sight= null;
		self.monsterinfo.checkattack= Jorg_CheckAttack;
		gi.linkentity(self);

		self.monsterinfo.currentmove= jorg_move_stand;
		self.monsterinfo.scale= MODEL_SCALE;

		walkmonster_start.think(self);
	}

}
