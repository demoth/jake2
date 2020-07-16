/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 13.11.2003 by RST.

// $Id: M_Brain.java,v 1.4 2005-11-20 22:18:33 salomo Exp $

package jake2.game.monsters;

import jake2.game.*;
import jake2.qcommon.Defines;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class M_Brain {

    public final static int FRAME_walk101 = 0;

    public final static int FRAME_walk102 = 1;

    public final static int FRAME_walk103 = 2;

    public final static int FRAME_walk104 = 3;

    public final static int FRAME_walk105 = 4;

    public final static int FRAME_walk106 = 5;

    public final static int FRAME_walk107 = 6;

    public final static int FRAME_walk108 = 7;

    public final static int FRAME_walk109 = 8;

    public final static int FRAME_walk110 = 9;

    public final static int FRAME_walk111 = 10;

    public final static int FRAME_walk112 = 11;

    public final static int FRAME_walk113 = 12;

    public final static int FRAME_walk201 = 13;

    public final static int FRAME_walk202 = 14;

    public final static int FRAME_walk203 = 15;

    public final static int FRAME_walk204 = 16;

    public final static int FRAME_walk205 = 17;

    public final static int FRAME_walk206 = 18;

    public final static int FRAME_walk207 = 19;

    public final static int FRAME_walk208 = 20;

    public final static int FRAME_walk209 = 21;

    public final static int FRAME_walk210 = 22;

    public final static int FRAME_walk211 = 23;

    public final static int FRAME_walk212 = 24;

    public final static int FRAME_walk213 = 25;

    public final static int FRAME_walk214 = 26;

    public final static int FRAME_walk215 = 27;

    public final static int FRAME_walk216 = 28;

    public final static int FRAME_walk217 = 29;

    public final static int FRAME_walk218 = 30;

    public final static int FRAME_walk219 = 31;

    public final static int FRAME_walk220 = 32;

    public final static int FRAME_walk221 = 33;

    public final static int FRAME_walk222 = 34;

    public final static int FRAME_walk223 = 35;

    public final static int FRAME_walk224 = 36;

    public final static int FRAME_walk225 = 37;

    public final static int FRAME_walk226 = 38;

    public final static int FRAME_walk227 = 39;

    public final static int FRAME_walk228 = 40;

    public final static int FRAME_walk229 = 41;

    public final static int FRAME_walk230 = 42;

    public final static int FRAME_walk231 = 43;

    public final static int FRAME_walk232 = 44;

    public final static int FRAME_walk233 = 45;

    public final static int FRAME_walk234 = 46;

    public final static int FRAME_walk235 = 47;

    public final static int FRAME_walk236 = 48;

    public final static int FRAME_walk237 = 49;

    public final static int FRAME_walk238 = 50;

    public final static int FRAME_walk239 = 51;

    public final static int FRAME_walk240 = 52;

    public final static int FRAME_attak101 = 53;

    public final static int FRAME_attak102 = 54;

    public final static int FRAME_attak103 = 55;

    public final static int FRAME_attak104 = 56;

    public final static int FRAME_attak105 = 57;

    public final static int FRAME_attak106 = 58;

    public final static int FRAME_attak107 = 59;

    public final static int FRAME_attak108 = 60;

    public final static int FRAME_attak109 = 61;

    public final static int FRAME_attak110 = 62;

    public final static int FRAME_attak111 = 63;

    public final static int FRAME_attak112 = 64;

    public final static int FRAME_attak113 = 65;

    public final static int FRAME_attak114 = 66;

    public final static int FRAME_attak115 = 67;

    public final static int FRAME_attak116 = 68;

    public final static int FRAME_attak117 = 69;

    public final static int FRAME_attak118 = 70;

    public final static int FRAME_attak201 = 71;

    public final static int FRAME_attak202 = 72;

    public final static int FRAME_attak203 = 73;

    public final static int FRAME_attak204 = 74;

    public final static int FRAME_attak205 = 75;

    public final static int FRAME_attak206 = 76;

    public final static int FRAME_attak207 = 77;

    public final static int FRAME_attak208 = 78;

    public final static int FRAME_attak209 = 79;

    public final static int FRAME_attak210 = 80;

    public final static int FRAME_attak211 = 81;

    public final static int FRAME_attak212 = 82;

    public final static int FRAME_attak213 = 83;

    public final static int FRAME_attak214 = 84;

    public final static int FRAME_attak215 = 85;

    public final static int FRAME_attak216 = 86;

    public final static int FRAME_attak217 = 87;

    public final static int FRAME_pain101 = 88;

    public final static int FRAME_pain102 = 89;

    public final static int FRAME_pain103 = 90;

    public final static int FRAME_pain104 = 91;

    public final static int FRAME_pain105 = 92;

    public final static int FRAME_pain106 = 93;

    public final static int FRAME_pain107 = 94;

    public final static int FRAME_pain108 = 95;

    public final static int FRAME_pain109 = 96;

    public final static int FRAME_pain110 = 97;

    public final static int FRAME_pain111 = 98;

    public final static int FRAME_pain112 = 99;

    public final static int FRAME_pain113 = 100;

    public final static int FRAME_pain114 = 101;

    public final static int FRAME_pain115 = 102;

    public final static int FRAME_pain116 = 103;

    public final static int FRAME_pain117 = 104;

    public final static int FRAME_pain118 = 105;

    public final static int FRAME_pain119 = 106;

    public final static int FRAME_pain120 = 107;

    public final static int FRAME_pain121 = 108;

    public final static int FRAME_pain201 = 109;

    public final static int FRAME_pain202 = 110;

    public final static int FRAME_pain203 = 111;

    public final static int FRAME_pain204 = 112;

    public final static int FRAME_pain205 = 113;

    public final static int FRAME_pain206 = 114;

    public final static int FRAME_pain207 = 115;

    public final static int FRAME_pain208 = 116;

    public final static int FRAME_pain301 = 117;

    public final static int FRAME_pain302 = 118;

    public final static int FRAME_pain303 = 119;

    public final static int FRAME_pain304 = 120;

    public final static int FRAME_pain305 = 121;

    public final static int FRAME_pain306 = 122;

    public final static int FRAME_death101 = 123;

    public final static int FRAME_death102 = 124;

    public final static int FRAME_death103 = 125;

    public final static int FRAME_death104 = 126;

    public final static int FRAME_death105 = 127;

    public final static int FRAME_death106 = 128;

    public final static int FRAME_death107 = 129;

    public final static int FRAME_death108 = 130;

    public final static int FRAME_death109 = 131;

    public final static int FRAME_death110 = 132;

    public final static int FRAME_death111 = 133;

    public final static int FRAME_death112 = 134;

    public final static int FRAME_death113 = 135;

    public final static int FRAME_death114 = 136;

    public final static int FRAME_death115 = 137;

    public final static int FRAME_death116 = 138;

    public final static int FRAME_death117 = 139;

    public final static int FRAME_death118 = 140;

    public final static int FRAME_death201 = 141;

    public final static int FRAME_death202 = 142;

    public final static int FRAME_death203 = 143;

    public final static int FRAME_death204 = 144;

    public final static int FRAME_death205 = 145;

    public final static int FRAME_duck01 = 146;

    public final static int FRAME_duck02 = 147;

    public final static int FRAME_duck03 = 148;

    public final static int FRAME_duck04 = 149;

    public final static int FRAME_duck05 = 150;

    public final static int FRAME_duck06 = 151;

    public final static int FRAME_duck07 = 152;

    public final static int FRAME_duck08 = 153;

    public final static int FRAME_defens01 = 154;

    public final static int FRAME_defens02 = 155;

    public final static int FRAME_defens03 = 156;

    public final static int FRAME_defens04 = 157;

    public final static int FRAME_defens05 = 158;

    public final static int FRAME_defens06 = 159;

    public final static int FRAME_defens07 = 160;

    public final static int FRAME_defens08 = 161;

    public final static int FRAME_stand01 = 162;

    public final static int FRAME_stand02 = 163;

    public final static int FRAME_stand03 = 164;

    public final static int FRAME_stand04 = 165;

    public final static int FRAME_stand05 = 166;

    public final static int FRAME_stand06 = 167;

    public final static int FRAME_stand07 = 168;

    public final static int FRAME_stand08 = 169;

    public final static int FRAME_stand09 = 170;

    public final static int FRAME_stand10 = 171;

    public final static int FRAME_stand11 = 172;

    public final static int FRAME_stand12 = 173;

    public final static int FRAME_stand13 = 174;

    public final static int FRAME_stand14 = 175;

    public final static int FRAME_stand15 = 176;

    public final static int FRAME_stand16 = 177;

    public final static int FRAME_stand17 = 178;

    public final static int FRAME_stand18 = 179;

    public final static int FRAME_stand19 = 180;

    public final static int FRAME_stand20 = 181;

    public final static int FRAME_stand21 = 182;

    public final static int FRAME_stand22 = 183;

    public final static int FRAME_stand23 = 184;

    public final static int FRAME_stand24 = 185;

    public final static int FRAME_stand25 = 186;

    public final static int FRAME_stand26 = 187;

    public final static int FRAME_stand27 = 188;

    public final static int FRAME_stand28 = 189;

    public final static int FRAME_stand29 = 190;

    public final static int FRAME_stand30 = 191;

    public final static int FRAME_stand31 = 192;

    public final static int FRAME_stand32 = 193;

    public final static int FRAME_stand33 = 194;

    public final static int FRAME_stand34 = 195;

    public final static int FRAME_stand35 = 196;

    public final static int FRAME_stand36 = 197;

    public final static int FRAME_stand37 = 198;

    public final static int FRAME_stand38 = 199;

    public final static int FRAME_stand39 = 200;

    public final static int FRAME_stand40 = 201;

    public final static int FRAME_stand41 = 202;

    public final static int FRAME_stand42 = 203;

    public final static int FRAME_stand43 = 204;

    public final static int FRAME_stand44 = 205;

    public final static int FRAME_stand45 = 206;

    public final static int FRAME_stand46 = 207;

    public final static int FRAME_stand47 = 208;

    public final static int FRAME_stand48 = 209;

    public final static int FRAME_stand49 = 210;

    public final static int FRAME_stand50 = 211;

    public final static int FRAME_stand51 = 212;

    public final static int FRAME_stand52 = 213;

    public final static int FRAME_stand53 = 214;

    public final static int FRAME_stand54 = 215;

    public final static int FRAME_stand55 = 216;

    public final static int FRAME_stand56 = 217;

    public final static int FRAME_stand57 = 218;

    public final static int FRAME_stand58 = 219;

    public final static int FRAME_stand59 = 220;

    public final static int FRAME_stand60 = 221;

    public final static float MODEL_SCALE = 1.000000f;

    static int sound_chest_open;

    static int sound_tentacles_extend;

    static int sound_tentacles_retract;

    static int sound_death;

    static int sound_idle1;

    static int sound_idle2;

    static int sound_idle3;

    static int sound_pain1;

    static int sound_pain2;

    static int sound_sight;

    static int sound_search;

    static int sound_melee1;

    static int sound_melee2;

    static int sound_melee3;

    static EntInteractAdapter brain_sight = new EntInteractAdapter() {
    	public String getID() { return "brain_sight"; }
        public boolean interact(SubgameEntity self, SubgameEntity other) {
            GameBase.gameExports.gameImports.sound(self, Defines.CHAN_VOICE, sound_sight, 1,
                    Defines.ATTN_NORM, 0);
            return true;
        }
    };

    static EntThinkAdapter brain_search = new EntThinkAdapter() {
    	public String getID() { return "brain_search"; }
        public boolean think(SubgameEntity self) {
            GameBase.gameExports.gameImports.sound(self, Defines.CHAN_VOICE, sound_search, 1,
                    Defines.ATTN_NORM, 0);
            return true;
        }
    };

    //
    //	   STAND
    //

    static mframe_t brain_frames_stand[] = new mframe_t[] {
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null) };

    static mmove_t brain_move_stand = new mmove_t(FRAME_stand01, FRAME_stand30,
            brain_frames_stand, null);

    static EntThinkAdapter brain_stand = new EntThinkAdapter() {
    	public String getID() { return "brain_stand"; }
        public boolean think(SubgameEntity self) {
            self.monsterinfo.currentmove = brain_move_stand;
            return true;
        }
    };

    //
    //	   IDLE
    //

    static mframe_t brain_frames_idle[] = new mframe_t[] {
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null),
            new mframe_t(GameAI.ai_stand, 0, null) };

    static mmove_t brain_move_idle = new mmove_t(FRAME_stand31, FRAME_stand60,
            brain_frames_idle, brain_stand);

    static EntThinkAdapter brain_idle = new EntThinkAdapter() {
    	public String getID() { return "brain_idle"; }
        public boolean think(SubgameEntity self) {
            GameBase.gameExports.gameImports.sound(self, Defines.CHAN_AUTO, sound_idle3, 1,
                    Defines.ATTN_IDLE, 0);
            self.monsterinfo.currentmove = brain_move_idle;
            return true;
        }
    };

    //
    //	   WALK
    //
    static mframe_t brain_frames_walk1[] = new mframe_t[] {
            new mframe_t(GameAI.ai_walk, 7, null),
            new mframe_t(GameAI.ai_walk, 2, null),
            new mframe_t(GameAI.ai_walk, 3, null),
            new mframe_t(GameAI.ai_walk, 3, null),
            new mframe_t(GameAI.ai_walk, 1, null),
            new mframe_t(GameAI.ai_walk, 0, null),
            new mframe_t(GameAI.ai_walk, 0, null),
            new mframe_t(GameAI.ai_walk, 9, null),
            new mframe_t(GameAI.ai_walk, -4, null),
            new mframe_t(GameAI.ai_walk, -1, null),
            new mframe_t(GameAI.ai_walk, 2, null) };

    static mmove_t brain_move_walk1 = new mmove_t(FRAME_walk101, FRAME_walk111,
            brain_frames_walk1, null);

    //	   walk2 is FUBAR, do not use
    /*
     * # if 0 void brain_walk2_cycle(SubgameEntity self) { if (random() > 0.1)
     * self.monsterinfo.nextframe= FRAME_walk220; }
     * 
     * static mframe_t brain_frames_walk2[]= new mframe_t[] { new
     * mframe_t(ai_walk, 3, null), new mframe_t(ai_walk, -2, null), new
     * mframe_t(ai_walk, -4, null), new mframe_t(ai_walk, -3, null), new
     * mframe_t(ai_walk, 0, null), new mframe_t(ai_walk, 1, null), new
     * mframe_t(ai_walk, 12, null), new mframe_t(ai_walk, 0, null), new
     * mframe_t(ai_walk, -3, null), new mframe_t(ai_walk, 0, null), new
     * mframe_t(ai_walk, -2, null), new mframe_t(ai_walk, 0, null), new
     * mframe_t(ai_walk, 0, null), new mframe_t(ai_walk, 1, null), new
     * mframe_t(ai_walk, 0, null), new mframe_t(ai_walk, 0, null), new
     * mframe_t(ai_walk, 0, null), new mframe_t(ai_walk, 0, null), new
     * mframe_t(ai_walk, 0, null), new mframe_t(ai_walk, 10, null, // Cycle
     * Start)
     * 
     * new mframe_t(ai_walk, -1, null), new mframe_t(ai_walk, 7, null), new
     * mframe_t(ai_walk, 0, null), new mframe_t(ai_walk, 3, null), new
     * mframe_t(ai_walk, -3, null), new mframe_t(ai_walk, 2, null), new
     * mframe_t(ai_walk, 4, null), new mframe_t(ai_walk, -3, null), new
     * mframe_t(ai_walk, 2, null), new mframe_t(ai_walk, 0, null), new
     * mframe_t(ai_walk, 4, brain_walk2_cycle), new mframe_t(ai_walk, -1, null),
     * new mframe_t(ai_walk, -1, null), new mframe_t(ai_walk, -8, null,) new
     * mframe_t(ai_walk, 0, null), new mframe_t(ai_walk, 1, null), new
     * mframe_t(ai_walk, 5, null), new mframe_t(ai_walk, 2, null), new
     * mframe_t(ai_walk, -1, null), new mframe_t(ai_walk, -5, null)}; static
     * mmove_t brain_move_walk2= new mmove_t(FRAME_walk201, FRAME_walk240,
     * brain_frames_walk2, null);
     *  # endif
     */
    static EntThinkAdapter brain_walk = new EntThinkAdapter() {
    	public String getID() { return "brain_walk"; }
        public boolean think(SubgameEntity self) {
            //			if (random() <= 0.5)
            self.monsterinfo.currentmove = brain_move_walk1;
            //		else
            //			self.monsterinfo.currentmove = &brain_move_walk2;
            return true;
        }
    };

    //
    //	   DUCK
    //

    static EntThinkAdapter brain_duck_down = new EntThinkAdapter() {
    	public String getID() { return "brain_duck_down"; }
        public boolean think(SubgameEntity self) {

            if ((self.monsterinfo.aiflags & GameDefines.AI_DUCKED) != 0)
                return true;
            self.monsterinfo.aiflags |= GameDefines.AI_DUCKED;
            self.maxs[2] -= 32;
            self.takedamage = Defines.DAMAGE_YES;
            GameBase.gameExports.gameImports.linkentity(self);
            return true;
        }
    };

    static EntThinkAdapter brain_duck_hold = new EntThinkAdapter() {
    	public String getID() { return "brain_duck_hold"; }
        public boolean think(SubgameEntity self) {
            if (GameBase.level.time >= self.monsterinfo.pausetime)
                self.monsterinfo.aiflags &= ~GameDefines.AI_HOLD_FRAME;
            else
                self.monsterinfo.aiflags |= GameDefines.AI_HOLD_FRAME;
            return true;
        }
    };

    static EntThinkAdapter brain_duck_up = new EntThinkAdapter() {
    	public String getID() { return "brain_duck_up"; }
        public boolean think(SubgameEntity self) {
            self.monsterinfo.aiflags &= ~GameDefines.AI_DUCKED;
            self.maxs[2] += 32;
            self.takedamage = Defines.DAMAGE_AIM;
            GameBase.gameExports.gameImports.linkentity(self);
            return true;
        }
    };

    static EntDodgeAdapter brain_dodge = new EntDodgeAdapter() {
    	public String getID() { return "brain_dodge"; }
        public void dodge(SubgameEntity self, SubgameEntity attacker, float eta) {
            if (Lib.random() > 0.25)
                return;

            if (self.enemy == null)
                self.enemy = attacker;

            self.monsterinfo.pausetime = GameBase.level.time + eta + 0.5f;
            self.monsterinfo.currentmove = brain_move_duck;
            return;
        }
    };

    static mframe_t brain_frames_death2[] = new mframe_t[] {
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 9, null),
            new mframe_t(GameAI.ai_move, 0, null) };

    static EntThinkAdapter brain_dead = new EntThinkAdapter() {
    	public String getID() { return "brain_dead"; }
        public boolean think(SubgameEntity self) {
            Math3D.VectorSet(self.mins, -16, -16, -24);
            Math3D.VectorSet(self.maxs, 16, 16, -8);
            self.movetype = GameDefines.MOVETYPE_TOSS;
            self.svflags |= Defines.SVF_DEADMONSTER;
            self.nextthink = 0;
            GameBase.gameExports.gameImports.linkentity(self);
            return true;
        }
    };

    static mmove_t brain_move_death2 = new mmove_t(FRAME_death201,
            FRAME_death205, brain_frames_death2, brain_dead);

    static mframe_t brain_frames_death1[] = new mframe_t[] {
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, -2, null),
            new mframe_t(GameAI.ai_move, 9, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null) };

    static mmove_t brain_move_death1 = new mmove_t(FRAME_death101,
            FRAME_death118, brain_frames_death1, brain_dead);

    //
    //	   MELEE
    //

    static EntThinkAdapter brain_swing_right = new EntThinkAdapter() {
    	public String getID() { return "brain_swing_right"; }
        public boolean think(SubgameEntity self) {
            GameBase.gameExports.gameImports.sound(self, Defines.CHAN_BODY, sound_melee1, 1,
                    Defines.ATTN_NORM, 0);
            return true;
        }
    };

    static EntThinkAdapter brain_hit_right = new EntThinkAdapter() {
    	public String getID() { return "brain_hit_right"; }
        public boolean think(SubgameEntity self) {
            float[] aim = { 0, 0, 0 };

            Math3D.VectorSet(aim, GameDefines.MELEE_DISTANCE, self.maxs[0], 8);
            if (GameWeapon.fire_hit(self, aim, (15 + (Lib.rand() % 5)), 40))
                GameBase.gameExports.gameImports.sound(self, Defines.CHAN_WEAPON, sound_melee3, 1,
                        Defines.ATTN_NORM, 0);
            return true;
        }
    };

    static EntThinkAdapter brain_swing_left = new EntThinkAdapter() {
    	public String getID() { return "brain_swing_left"; }
        public boolean think(SubgameEntity self) {
            GameBase.gameExports.gameImports.sound(self, Defines.CHAN_BODY, sound_melee2, 1,
                    Defines.ATTN_NORM, 0);

            return true;
        }
    };

    static EntThinkAdapter brain_hit_left = new EntThinkAdapter() {
    	public String getID() { return "brain_hit_left"; }
        public boolean think(SubgameEntity self) {
            float[] aim = { 0, 0, 0 };

            Math3D.VectorSet(aim, GameDefines.MELEE_DISTANCE, self.mins[0], 8);
            if (GameWeapon.fire_hit(self, aim, (15 + (Lib.rand() % 5)), 40))
                GameBase.gameExports.gameImports.sound(self, Defines.CHAN_WEAPON, sound_melee3, 1,
                        Defines.ATTN_NORM, 0);

            return true;
        }
    };

    static EntThinkAdapter brain_chest_open = new EntThinkAdapter() {
    	public String getID() { return "brain_chest_open"; }
        public boolean think(SubgameEntity self) {
            self.spawnflags &= ~65536;
            self.monsterinfo.power_armor_type = GameDefines.POWER_ARMOR_NONE;
            GameBase.gameExports.gameImports.sound(self, Defines.CHAN_BODY, sound_chest_open, 1,
                    Defines.ATTN_NORM, 0);
            return true;
        }
    };

    static EntThinkAdapter brain_tentacle_attack = new EntThinkAdapter() {
    	public String getID() { return "brain_tentacle_attack"; }
        public boolean think(SubgameEntity self) {

            float[] aim = { 0, 0, 0 };

            Math3D.VectorSet(aim, GameDefines.MELEE_DISTANCE, 0, 8);
            if (GameWeapon.fire_hit(self, aim, (10 + (Lib.rand() % 5)), -600)
                    && GameBase.gameExports.cvarCache.skill.value > 0)
                self.spawnflags |= 65536;
            GameBase.gameExports.gameImports.sound(self, Defines.CHAN_WEAPON,
                    sound_tentacles_retract, 1, Defines.ATTN_NORM, 0);
            return true;
        }
    };

    static mframe_t brain_frames_attack1[] = new mframe_t[] {
            new mframe_t(GameAI.ai_charge, 8, null),
            new mframe_t(GameAI.ai_charge, 3, null),
            new mframe_t(GameAI.ai_charge, 5, null),
            new mframe_t(GameAI.ai_charge, 0, null),
            new mframe_t(GameAI.ai_charge, -3, brain_swing_right),
            new mframe_t(GameAI.ai_charge, 0, null),
            new mframe_t(GameAI.ai_charge, -5, null),
            new mframe_t(GameAI.ai_charge, -7, brain_hit_right),
            new mframe_t(GameAI.ai_charge, 0, null),
            new mframe_t(GameAI.ai_charge, 6, brain_swing_left),
            new mframe_t(GameAI.ai_charge, 1, null),
            new mframe_t(GameAI.ai_charge, 2, brain_hit_left),
            new mframe_t(GameAI.ai_charge, -3, null),
            new mframe_t(GameAI.ai_charge, 6, null),
            new mframe_t(GameAI.ai_charge, -1, null),
            new mframe_t(GameAI.ai_charge, -3, null),
            new mframe_t(GameAI.ai_charge, 2, null),
            new mframe_t(GameAI.ai_charge, -11, null) };

    static EntThinkAdapter brain_chest_closed = new EntThinkAdapter() {
    	public String getID() { return "brain_chest_closed"; }
        public boolean think(SubgameEntity self) {

            self.monsterinfo.power_armor_type = GameDefines.POWER_ARMOR_SCREEN;
            if ((self.spawnflags & 65536) != 0) {
                self.spawnflags &= ~65536;
                self.monsterinfo.currentmove = brain_move_attack1;
            }
            return true;
        }
    };

    static mframe_t brain_frames_attack2[] = new mframe_t[] {
            new mframe_t(GameAI.ai_charge, 5, null),
            new mframe_t(GameAI.ai_charge, -4, null),
            new mframe_t(GameAI.ai_charge, -4, null),
            new mframe_t(GameAI.ai_charge, -3, null),
            new mframe_t(GameAI.ai_charge, 0, brain_chest_open),
            new mframe_t(GameAI.ai_charge, 0, null),
            new mframe_t(GameAI.ai_charge, 13, brain_tentacle_attack),
            new mframe_t(GameAI.ai_charge, 0, null),
            new mframe_t(GameAI.ai_charge, 2, null),
            new mframe_t(GameAI.ai_charge, 0, null),
            new mframe_t(GameAI.ai_charge, -9, brain_chest_closed),
            new mframe_t(GameAI.ai_charge, 0, null),
            new mframe_t(GameAI.ai_charge, 4, null),
            new mframe_t(GameAI.ai_charge, 3, null),
            new mframe_t(GameAI.ai_charge, 2, null),
            new mframe_t(GameAI.ai_charge, -3, null),
            new mframe_t(GameAI.ai_charge, -6, null) };

    static EntThinkAdapter brain_melee = new EntThinkAdapter() {
    	public String getID() { return "brain_melee"; }
        public boolean think(SubgameEntity self) {
            if (Lib.random() <= 0.5)
                self.monsterinfo.currentmove = brain_move_attack1;
            else
                self.monsterinfo.currentmove = brain_move_attack2;

            return true;
        }
    };

    //
    //	   RUN
    //

    static mframe_t brain_frames_run[] = new mframe_t[] {
            new mframe_t(GameAI.ai_run, 9, null),
            new mframe_t(GameAI.ai_run, 2, null),
            new mframe_t(GameAI.ai_run, 3, null),
            new mframe_t(GameAI.ai_run, 3, null),
            new mframe_t(GameAI.ai_run, 1, null),
            new mframe_t(GameAI.ai_run, 0, null),
            new mframe_t(GameAI.ai_run, 0, null),
            new mframe_t(GameAI.ai_run, 10, null),
            new mframe_t(GameAI.ai_run, -4, null),
            new mframe_t(GameAI.ai_run, -1, null),
            new mframe_t(GameAI.ai_run, 2, null) };

    static mmove_t brain_move_run = new mmove_t(FRAME_walk101, FRAME_walk111,
            brain_frames_run, null);

    static EntThinkAdapter brain_run = new EntThinkAdapter() {
    	public String getID() { return "brain_run"; }
        public boolean think(SubgameEntity self) {
            self.monsterinfo.power_armor_type = GameDefines.POWER_ARMOR_SCREEN;
            if ((self.monsterinfo.aiflags & GameDefines.AI_STAND_GROUND) != 0)
                self.monsterinfo.currentmove = brain_move_stand;
            else
                self.monsterinfo.currentmove = brain_move_run;
            return true;
        }
    };

    static mframe_t brain_frames_defense[] = new mframe_t[] {
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null) };

    static mmove_t brain_move_defense = new mmove_t(FRAME_defens01,
            FRAME_defens08, brain_frames_defense, null);

    static mframe_t brain_frames_pain3[] = new mframe_t[] {
            new mframe_t(GameAI.ai_move, -2, null),
            new mframe_t(GameAI.ai_move, 2, null),
            new mframe_t(GameAI.ai_move, 1, null),
            new mframe_t(GameAI.ai_move, 3, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, -4, null) };

    static mmove_t brain_move_pain3 = new mmove_t(FRAME_pain301, FRAME_pain306,
            brain_frames_pain3, brain_run);

    static mframe_t brain_frames_pain2[] = new mframe_t[] {
            new mframe_t(GameAI.ai_move, -2, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 3, null),
            new mframe_t(GameAI.ai_move, 1, null),
            new mframe_t(GameAI.ai_move, -2, null) };

    static mmove_t brain_move_pain2 = new mmove_t(FRAME_pain201, FRAME_pain208,
            brain_frames_pain2, brain_run);

    static mframe_t brain_frames_pain1[] = new mframe_t[] {
            new mframe_t(GameAI.ai_move, -6, null),
            new mframe_t(GameAI.ai_move, -2, null),
            new mframe_t(GameAI.ai_move, -6, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 2, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 2, null),
            new mframe_t(GameAI.ai_move, 1, null),
            new mframe_t(GameAI.ai_move, 7, null),
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, 3, null),
            new mframe_t(GameAI.ai_move, -1, null) };

    static mmove_t brain_move_pain1 = new mmove_t(FRAME_pain101, FRAME_pain121,
            brain_frames_pain1, brain_run);

    static mframe_t brain_frames_duck[] = new mframe_t[] {
            new mframe_t(GameAI.ai_move, 0, null),
            new mframe_t(GameAI.ai_move, -2, brain_duck_down),
            new mframe_t(GameAI.ai_move, 17, brain_duck_hold),
            new mframe_t(GameAI.ai_move, -3, null),
            new mframe_t(GameAI.ai_move, -1, brain_duck_up),
            new mframe_t(GameAI.ai_move, -5, null),
            new mframe_t(GameAI.ai_move, -6, null),
            new mframe_t(GameAI.ai_move, -6, null) };

    static mmove_t brain_move_duck = new mmove_t(FRAME_duck01, FRAME_duck08,
            brain_frames_duck, brain_run);

    static EntPainAdapter brain_pain = new EntPainAdapter() {
    	public String getID() { return "brain_pain"; }
        public void pain(SubgameEntity self, SubgameEntity other, float kick, int damage) {
            float r;

            if (self.health < (self.max_health / 2))
                self.s.skinnum = 1;

            if (GameBase.level.time < self.pain_debounce_time)
                return;

            self.pain_debounce_time = GameBase.level.time + 3;
            if (GameBase.gameExports.cvarCache.skill.value == 3)
                return; // no pain anims in nightmare

            r = Lib.random();
            if (r < 0.33) {
                GameBase.gameExports.gameImports.sound(self, Defines.CHAN_VOICE, sound_pain1, 1,
                        Defines.ATTN_NORM, 0);
                self.monsterinfo.currentmove = brain_move_pain1;
            } else if (r < 0.66) {
                GameBase.gameExports.gameImports.sound(self, Defines.CHAN_VOICE, sound_pain2, 1,
                        Defines.ATTN_NORM, 0);
                self.monsterinfo.currentmove = brain_move_pain2;
            } else {
                GameBase.gameExports.gameImports.sound(self, Defines.CHAN_VOICE, sound_pain1, 1,
                        Defines.ATTN_NORM, 0);
                self.monsterinfo.currentmove = brain_move_pain3;
            }
        }

    };

    static EntDieAdapter brain_die = new EntDieAdapter() {
    	public String getID() { return "brain_die"; }
        public void die(SubgameEntity self, SubgameEntity inflictor, SubgameEntity attacker,
                int damage, float[] point) {
            int n;

            self.s.effects = 0;
            self.monsterinfo.power_armor_type = GameDefines.POWER_ARMOR_NONE;

            //	   check for gib
            if (self.health <= self.gib_health) {
                GameBase.gameExports.gameImports
                        .sound(self, Defines.CHAN_VOICE, GameBase.gameExports.gameImports
                                .soundindex("misc/udeath.wav"), 1,
                                Defines.ATTN_NORM, 0);
                for (n = 0; n < 2; n++)
                    GameMisc.ThrowGib(self, "models/objects/gibs/bone/tris.md2",
                            damage, GameDefines.GIB_ORGANIC);
                for (n = 0; n < 4; n++)
                    GameMisc.ThrowGib(self,
                            "models/objects/gibs/sm_meat/tris.md2", damage,
                            GameDefines.GIB_ORGANIC);
                GameMisc.ThrowHead(self, "models/objects/gibs/head2/tris.md2",
                        damage, GameDefines.GIB_ORGANIC);
                self.deadflag = GameDefines.DEAD_DEAD;
                return;
            }

            if (self.deadflag == GameDefines.DEAD_DEAD)
                return;

            //	   regular death
            GameBase.gameExports.gameImports.sound(self, Defines.CHAN_VOICE, sound_death, 1,
                    Defines.ATTN_NORM, 0);
            self.deadflag = GameDefines.DEAD_DEAD;
            self.takedamage = Defines.DAMAGE_YES;
            if (Lib.random() <= 0.5)
                self.monsterinfo.currentmove = brain_move_death1;
            else
                self.monsterinfo.currentmove = brain_move_death2;
        }
    };

    static mmove_t brain_move_attack1 = new mmove_t(FRAME_attak101,
            FRAME_attak118, brain_frames_attack1, brain_run);

    static mmove_t brain_move_attack2 = new mmove_t(FRAME_attak201,
            FRAME_attak217, brain_frames_attack2, brain_run);

    /*
     * QUAKED monster_brain (1 .5 0) (-16 -16 -24) (16 16 32) Ambush
     * Trigger_Spawn Sight
     */
    public static void SP_monster_brain(SubgameEntity self) {
        if (GameBase.gameExports.cvarCache.deathmatch.value != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }

        sound_chest_open = GameBase.gameExports.gameImports.soundindex("brain/brnatck1.wav");
        sound_tentacles_extend = GameBase.gameExports.gameImports.soundindex("brain/brnatck2.wav");
        sound_tentacles_retract = GameBase.gameExports.gameImports.soundindex("brain/brnatck3.wav");
        sound_death = GameBase.gameExports.gameImports.soundindex("brain/brndeth1.wav");
        sound_idle1 = GameBase.gameExports.gameImports.soundindex("brain/brnidle1.wav");
        sound_idle2 = GameBase.gameExports.gameImports.soundindex("brain/brnidle2.wav");
        sound_idle3 = GameBase.gameExports.gameImports.soundindex("brain/brnlens1.wav");
        sound_pain1 = GameBase.gameExports.gameImports.soundindex("brain/brnpain1.wav");
        sound_pain2 = GameBase.gameExports.gameImports.soundindex("brain/brnpain2.wav");
        sound_sight = GameBase.gameExports.gameImports.soundindex("brain/brnsght1.wav");
        sound_search = GameBase.gameExports.gameImports.soundindex("brain/brnsrch1.wav");
        sound_melee1 = GameBase.gameExports.gameImports.soundindex("brain/melee1.wav");
        sound_melee2 = GameBase.gameExports.gameImports.soundindex("brain/melee2.wav");
        sound_melee3 = GameBase.gameExports.gameImports.soundindex("brain/melee3.wav");

        self.movetype = GameDefines.MOVETYPE_STEP;
        self.solid = Defines.SOLID_BBOX;
        self.s.modelindex = GameBase.gameExports.gameImports
                .modelindex("models/monsters/brain/tris.md2");
        Math3D.VectorSet(self.mins, -16, -16, -24);
        Math3D.VectorSet(self.maxs, 16, 16, 32);

        self.health = 300;
        self.gib_health = -150;
        self.mass = 400;

        self.pain = brain_pain;
        self.die = brain_die;

        self.monsterinfo.stand = brain_stand;
        self.monsterinfo.walk = brain_walk;
        self.monsterinfo.run = brain_run;
        self.monsterinfo.dodge = brain_dodge;
        //		self.monsterinfo.attack = brain_attack;
        self.monsterinfo.melee = brain_melee;
        self.monsterinfo.sight = brain_sight;
        self.monsterinfo.search = brain_search;
        self.monsterinfo.idle = brain_idle;

        self.monsterinfo.power_armor_type = GameDefines.POWER_ARMOR_SCREEN;
        self.monsterinfo.power_armor_power = 100;

        GameBase.gameExports.gameImports.linkentity(self);

        self.monsterinfo.currentmove = brain_move_stand;
        self.monsterinfo.scale = MODEL_SCALE;

        GameAI.walkmonster_start.think(self);
    }
}