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

// Created on 12.11.2003 by RST.
// $Id: GameWeapon.java,v 1.3 2004-09-22 19:22:02 salomo Exp $
package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.util.*;

public class GameWeapon {

    /*
     * =============== PlayerNoise
     * 
     * Each player can have two noise objects associated with it: a personal
     * noise (jumping, pain, weapon firing), and a weapon target noise (bullet
     * wall impacts)
     * 
     * Monsters that don't directly see the player can move to a noise in hopes
     * of seeing the player from there. ===============
     */
    static void PlayerNoise(edict_t who, float[] where, int type) {
        edict_t noise;

        if (type == Defines.PNOISE_WEAPON) {
            if (who.client.silencer_shots == 0) {
                who.client.silencer_shots--;
                return;
            }
        }

        if (GameBase.deathmatch.value != 0)
            return;

        if ((who.flags & Defines.FL_NOTARGET) != 0)
            return;

        if (who.mynoise == null) {
            noise = GameUtil.G_Spawn();
            noise.classname = "player_noise";
            Math3D.VectorSet(noise.mins, -8, -8, -8);
            Math3D.VectorSet(noise.maxs, 8, 8, 8);
            noise.owner = who;
            noise.svflags = Defines.SVF_NOCLIENT;
            who.mynoise = noise;

            noise = GameUtil.G_Spawn();
            noise.classname = "player_noise";
            Math3D.VectorSet(noise.mins, -8, -8, -8);
            Math3D.VectorSet(noise.maxs, 8, 8, 8);
            noise.owner = who;
            noise.svflags = Defines.SVF_NOCLIENT;
            who.mynoise2 = noise;
        }

        if (type == Defines.PNOISE_SELF || type == Defines.PNOISE_WEAPON) {
            noise = who.mynoise;
            GameBase.level.sound_entity = noise;
            GameBase.level.sound_entity_framenum = GameBase.level.framenum;
        } else // type == PNOISE_IMPACT
        {
            noise = who.mynoise2;
            GameBase.level.sound2_entity = noise;
            GameBase.level.sound2_entity_framenum = GameBase.level.framenum;
        }

        Math3D.VectorCopy(where, noise.s.origin);
        Math3D.VectorSubtract(where, noise.maxs, noise.absmin);
        Math3D.VectorAdd(where, noise.maxs, noise.absmax);
        noise.teleport_time = GameBase.level.time;
        GameBase.gi.linkentity(noise);
    }

    /*
     * ================= check_dodge
     * 
     * This is a support routine used when a client is firing a non-instant
     * attack weapon. It checks to see if a monster's dodge function should be
     * called. =================
     */
    static void check_dodge(edict_t self, float[] start, float[] dir, int speed) {
        float[] end = { 0, 0, 0 };
        float[] v = { 0, 0, 0 };
        trace_t tr;
        float eta;

        // easy mode only ducks one quarter the time
        if (GameBase.skill.value == 0) {
            if (Lib.random() > 0.25)
                return;
        }
        Math3D.VectorMA(start, 8192, dir, end);
        tr = GameBase.gi.trace(start, null, null, end, self, Defines.MASK_SHOT);
        if ((tr.ent != null) && (tr.ent.svflags & Defines.SVF_MONSTER) != 0
                && (tr.ent.health > 0) && (null != tr.ent.monsterinfo.dodge)
                && GameUtil.infront(tr.ent, self)) {
            Math3D.VectorSubtract(tr.endpos, start, v);
            eta = (Math3D.VectorLength(v) - tr.ent.maxs[0]) / speed;
            tr.ent.monsterinfo.dodge.dodge(tr.ent, self, eta);
        }
    }

    /*
     * ================= fire_blaster
     * 
     * Fires a single blaster bolt. Used by the blaster and hyper blaster.
     * =================
     */
    static EntTouchAdapter blaster_touch = new EntTouchAdapter() {

        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            int mod;

            if (other == self.owner)
                return;

            if (surf != null && (surf.flags & Defines.SURF_SKY) != 0) {
                GameUtil.G_FreeEdict(self);
                return;
            }

            if (self.owner.client != null)
                GameWeapon.PlayerNoise(self.owner, self.s.origin,
                        Defines.PNOISE_IMPACT);

            if (other.takedamage != 0) {
                if ((self.spawnflags & 1) != 0)
                    mod = Defines.MOD_HYPERBLASTER;
                else
                    mod = Defines.MOD_BLASTER;

                // bugfix null plane rst
                float[] normal;
                if (plane == null)
                    normal = new float[3];
                else
                    normal = plane.normal;

                GameUtil.T_Damage(other, self, self.owner, self.velocity,
                        self.s.origin, normal, self.dmg, 1,
                        Defines.DAMAGE_ENERGY, mod);

            } else {
                GameBase.gi.WriteByte(Defines.svc_temp_entity);
                GameBase.gi.WriteByte(Defines.TE_BLASTER);
                GameBase.gi.WritePosition(self.s.origin);
                if (plane == null)
                    GameBase.gi.WriteDir(Globals.vec3_origin);
                else
                    GameBase.gi.WriteDir(plane.normal);
                GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PVS);
            }

            GameUtil.G_FreeEdict(self);
        }
    };

    static EntThinkAdapter Grenade_Explode = new EntThinkAdapter() {
        public boolean think(edict_t ent) {
            float[] origin = { 0, 0, 0 };
            int mod;

            if (ent.owner.client != null)
                GameWeapon.PlayerNoise(ent.owner, ent.s.origin,
                        Defines.PNOISE_IMPACT);

            //FIXME: if we are onground then raise our Z just a bit since we
            // are a point?
            if (ent.enemy != null) {
                float points = 0;
                float[] v = { 0, 0, 0 };
                float[] dir = { 0, 0, 0 };

                Math3D.VectorAdd(ent.enemy.mins, ent.enemy.maxs, v);
                Math3D.VectorMA(ent.enemy.s.origin, 0.5f, v, v);
                Math3D.VectorSubtract(ent.s.origin, v, v);
                points = ent.dmg - 0.5f * Math3D.VectorLength(v);
                Math3D.VectorSubtract(ent.enemy.s.origin, ent.s.origin, dir);
                if ((ent.spawnflags & 1) != 0)
                    mod = Defines.MOD_HANDGRENADE;
                else
                    mod = Defines.MOD_GRENADE;
                GameUtil.T_Damage(ent.enemy, ent, ent.owner, dir, ent.s.origin,
                        Globals.vec3_origin, (int) points, (int) points,
                        Defines.DAMAGE_RADIUS, mod);
            }

            if ((ent.spawnflags & 2) != 0)
                mod = Defines.MOD_HELD_GRENADE;
            else if ((ent.spawnflags & 1) != 0)
                mod = Defines.MOD_HG_SPLASH;
            else
                mod = Defines.MOD_G_SPLASH;
            GameUtil.T_RadiusDamage(ent, ent.owner, ent.dmg, ent.enemy,
                    ent.dmg_radius, mod);

            Math3D.VectorMA(ent.s.origin, -0.02f, ent.velocity, origin);
            GameBase.gi.WriteByte(Defines.svc_temp_entity);
            if (ent.waterlevel != 0) {
                if (ent.groundentity != null)
                    GameBase.gi.WriteByte(Defines.TE_GRENADE_EXPLOSION_WATER);
                else
                    GameBase.gi.WriteByte(Defines.TE_ROCKET_EXPLOSION_WATER);
            } else {
                if (ent.groundentity != null)
                    GameBase.gi.WriteByte(Defines.TE_GRENADE_EXPLOSION);
                else
                    GameBase.gi.WriteByte(Defines.TE_ROCKET_EXPLOSION);
            }
            GameBase.gi.WritePosition(origin);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PHS);

            GameUtil.G_FreeEdict(ent);
            return true;
        }
    };

    static EntTouchAdapter Grenade_Touch = new EntTouchAdapter() {
        public void touch(edict_t ent, edict_t other, cplane_t plane,
                csurface_t surf) {
            if (other == ent.owner)
                return;

            if (surf != null && 0 != (surf.flags & Defines.SURF_SKY)) {
                GameUtil.G_FreeEdict(ent);
                return;
            }

            if (other.takedamage == 0) {
                if ((ent.spawnflags & 1) != 0) {
                    if (Lib.random() > 0.5f)
                        GameBase.gi.sound(ent, Defines.CHAN_VOICE, GameBase.gi
                                .soundindex("weapons/hgrenb1a.wav"), 1,
                                Defines.ATTN_NORM, 0);
                    else
                        GameBase.gi.sound(ent, Defines.CHAN_VOICE, GameBase.gi
                                .soundindex("weapons/hgrenb2a.wav"), 1,
                                Defines.ATTN_NORM, 0);
                } else {
                    GameBase.gi.sound(ent, Defines.CHAN_VOICE, GameBase.gi
                            .soundindex("weapons/grenlb1b.wav"), 1,
                            Defines.ATTN_NORM, 0);
                }
                return;
            }

            ent.enemy = other;
            Grenade_Explode.think(ent);
        }
    };

    /*
     * ================= fire_rocket =================
     */
    static EntTouchAdapter rocket_touch = new EntTouchAdapter() {
        public void touch(edict_t ent, edict_t other, cplane_t plane,
                csurface_t surf) {
            float[] origin = { 0, 0, 0 };
            int n;

            if (other == ent.owner)
                return;

            if (surf != null && (surf.flags & Defines.SURF_SKY) != 0) {
                GameUtil.G_FreeEdict(ent);
                return;
            }

            if (ent.owner.client != null)
                GameWeapon.PlayerNoise(ent.owner, ent.s.origin,
                        Defines.PNOISE_IMPACT);

            // calculate position for the explosion entity
            Math3D.VectorMA(ent.s.origin, -0.02f, ent.velocity, origin);

            if (other.takedamage != 0) {
                GameUtil.T_Damage(other, ent, ent.owner, ent.velocity,
                        ent.s.origin, plane.normal, ent.dmg, 0, 0,
                        Defines.MOD_ROCKET);
            } else {
                // don't throw any debris in net games
                if (GameBase.deathmatch.value == 0 && 0 == GameBase.coop.value) {
                    if ((surf != null)
                            && 0 == (surf.flags & (Defines.SURF_WARP
                                    | Defines.SURF_TRANS33
                                    | Defines.SURF_TRANS66 | Defines.SURF_FLOWING))) {
                        n = Lib.rand() % 5;
                        while (n-- > 0)
                            GameAI.ThrowDebris(ent,
                                    "models/objects/debris2/tris.md2", 2,
                                    ent.s.origin);
                    }
                }
            }

            GameUtil.T_RadiusDamage(ent, ent.owner, ent.radius_dmg, other,
                    ent.dmg_radius, Defines.MOD_R_SPLASH);

            GameBase.gi.WriteByte(Defines.svc_temp_entity);
            if (ent.waterlevel != 0)
                GameBase.gi.WriteByte(Defines.TE_ROCKET_EXPLOSION_WATER);
            else
                GameBase.gi.WriteByte(Defines.TE_ROCKET_EXPLOSION);
            GameBase.gi.WritePosition(origin);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PHS);

            GameUtil.G_FreeEdict(ent);
        }
    };

    /*
     * ================= fire_bfg =================
     */
    static EntThinkAdapter bfg_explode = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            edict_t ent;
            float points;
            float[] v = { 0, 0, 0 };
            float dist;

            EdictIterator edit = null;

            if (self.s.frame == 0) {
                // the BFG effect
                ent = null;
                while ((edit = GameBase.findradius(edit, self.s.origin,
                        self.dmg_radius)) != null) {
                    ent = edit.o;
                    if (ent.takedamage == 0)
                        continue;
                    if (ent == self.owner)
                        continue;
                    if (!GameUtil.CanDamage(ent, self))
                        continue;
                    if (!GameUtil.CanDamage(ent, self.owner))
                        continue;

                    Math3D.VectorAdd(ent.mins, ent.maxs, v);
                    Math3D.VectorMA(ent.s.origin, 0.5f, v, v);
                    Math3D.VectorSubtract(self.s.origin, v, v);
                    dist = Math3D.VectorLength(v);
                    points = (float) (self.radius_dmg * (1.0 - Math.sqrt(dist
                            / self.dmg_radius)));
                    if (ent == self.owner)
                        points = points * 0.5f;

                    GameBase.gi.WriteByte(Defines.svc_temp_entity);
                    GameBase.gi.WriteByte(Defines.TE_BFG_EXPLOSION);
                    GameBase.gi.WritePosition(ent.s.origin);
                    GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PHS);
                    GameUtil.T_Damage(ent, self, self.owner, self.velocity,
                            ent.s.origin, Globals.vec3_origin, (int) points, 0,
                            Defines.DAMAGE_ENERGY, Defines.MOD_BFG_EFFECT);
                }
            }

            self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            self.s.frame++;
            if (self.s.frame == 5)
                self.think = GameUtil.G_FreeEdictA;
            return true;

        }
    };

    static EntTouchAdapter bfg_touch = new EntTouchAdapter() {
        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            if (other == self.owner)
                return;

            if (surf != null && (surf.flags & Defines.SURF_SKY) != 0) {
                GameUtil.G_FreeEdict(self);
                return;
            }

            if (self.owner.client != null)
                GameWeapon.PlayerNoise(self.owner, self.s.origin,
                        Defines.PNOISE_IMPACT);

            // core explosion - prevents firing it into the wall/floor
            if (other.takedamage != 0)
                GameUtil.T_Damage(other, self, self.owner, self.velocity,
                        self.s.origin, plane.normal, 200, 0, 0,
                        Defines.MOD_BFG_BLAST);
            GameUtil.T_RadiusDamage(self, self.owner, 200, other, 100,
                    Defines.MOD_BFG_BLAST);

            GameBase.gi.sound(self, Defines.CHAN_VOICE, GameBase.gi
                    .soundindex("weapons/bfg__x1b.wav"), 1, Defines.ATTN_NORM,
                    0);
            self.solid = Defines.SOLID_NOT;
            self.touch = null;
            Math3D.VectorMA(self.s.origin, -1 * Defines.FRAMETIME,
                    self.velocity, self.s.origin);
            Math3D.VectorClear(self.velocity);
            self.s.modelindex = GameBase.gi.modelindex("sprites/s_bfg3.sp2");
            self.s.frame = 0;
            self.s.sound = 0;
            self.s.effects &= ~Defines.EF_ANIM_ALLFAST;
            self.think = bfg_explode;
            self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            self.enemy = other;

            GameBase.gi.WriteByte(Defines.svc_temp_entity);
            GameBase.gi.WriteByte(Defines.TE_BFG_BIGEXPLOSION);
            GameBase.gi.WritePosition(self.s.origin);
            GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PVS);
        }
    };

    static EntThinkAdapter bfg_think = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            edict_t ent;
            edict_t ignore;
            float[] point = { 0, 0, 0 };
            float[] dir = { 0, 0, 0 };
            float[] start = { 0, 0, 0 };
            float[] end = { 0, 0, 0 };
            int dmg;
            trace_t tr;

            if (GameBase.deathmatch.value != 0)
                dmg = 5;
            else
                dmg = 10;

            EdictIterator edit = null;
            while ((edit = GameBase.findradius(edit, self.s.origin, 256)) != null) {
                ent = edit.o;

                if (ent == self)
                    continue;

                if (ent == self.owner)
                    continue;

                if (ent.takedamage == 0)
                    continue;

                if (0 == (ent.svflags & Defines.SVF_MONSTER)
                        && (null == ent.client)
                        && (Lib.strcmp(ent.classname, "misc_explobox") != 0))
                    continue;

                Math3D.VectorMA(ent.absmin, 0.5f, ent.size, point);

                Math3D.VectorSubtract(point, self.s.origin, dir);
                Math3D.VectorNormalize(dir);

                ignore = self;
                Math3D.VectorCopy(self.s.origin, start);
                Math3D.VectorMA(start, 2048, dir, end);
                while (true) {
                    tr = GameBase.gi.trace(start, null, null, end, ignore,
                            Defines.CONTENTS_SOLID | Defines.CONTENTS_MONSTER
                                    | Defines.CONTENTS_DEADMONSTER);

                    if (null == tr.ent)
                        break;

                    // hurt it if we can
                    if ((tr.ent.takedamage != 0)
                            && 0 == (tr.ent.flags & Defines.FL_IMMUNE_LASER)
                            && (tr.ent != self.owner))
                        GameUtil.T_Damage(tr.ent, self, self.owner, dir,
                                tr.endpos, Globals.vec3_origin, dmg, 1,
                                Defines.DAMAGE_ENERGY, Defines.MOD_BFG_LASER);

                    // if we hit something that's not a monster or player we're
                    // done
                    if (0 == (tr.ent.svflags & Defines.SVF_MONSTER)
                            && (null == tr.ent.client)) {
                        GameBase.gi.WriteByte(Defines.svc_temp_entity);
                        GameBase.gi.WriteByte(Defines.TE_LASER_SPARKS);
                        GameBase.gi.WriteByte(4);
                        GameBase.gi.WritePosition(tr.endpos);
                        GameBase.gi.WriteDir(tr.plane.normal);
                        GameBase.gi.WriteByte(self.s.skinnum);
                        GameBase.gi.multicast(tr.endpos, Defines.MULTICAST_PVS);
                        break;
                    }

                    ignore = tr.ent;
                    Math3D.VectorCopy(tr.endpos, start);
                }

                GameBase.gi.WriteByte(Defines.svc_temp_entity);
                GameBase.gi.WriteByte(Defines.TE_BFG_LASER);
                GameBase.gi.WritePosition(self.s.origin);
                GameBase.gi.WritePosition(tr.endpos);
                GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PHS);
            }

            self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            return true;
        }
    };
}