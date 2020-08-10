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

// Created on 28.12.2003 by RST.

package jake2.game;

import jake2.game.monsters.M_Infantry;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class GameTurret {

    public static void AnglesNormalize(float[] vec) {
        while (vec[0] > 360)
            vec[0] -= 360;
        while (vec[0] < 0)
            vec[0] += 360;
        while (vec[1] > 360)
            vec[1] -= 360;
        while (vec[1] < 0)
            vec[1] += 360;
    }

    public static float SnapToEights(float x) {
        x *= 8.0;
        if (x > 0.0)
            x += 0.5;
        else
            x -= 0.5;
        return 0.125f * (int) x;
    }

    /**
     * QUAKED turret_breach (0 0 0) ? This portion of the turret can change both
     * pitch and yaw. The model should be made with a flat pitch. It (and the
     * associated base) need to be oriented towards 0. Use "angle" to set the
     * starting angle.
     * 
     * "speed" default 50 "dmg" default 10 "angle" point this forward "target"
     * point this at an info_notnull at the muzzle tip "minpitch" min acceptable
     * pitch angle : default -30 "maxpitch" max acceptable pitch angle : default
     * 30 "minyaw" min acceptable yaw angle : default 0 "maxyaw" max acceptable
     * yaw angle : default 360
     */

    public static void turret_breach_fire(SubgameEntity self, GameExportsImpl gameExports) {
        float[] f = { 0, 0, 0 }, r = { 0, 0, 0 }, u = { 0, 0, 0 };
        float[] start = { 0, 0, 0 };
        int damage;
        int speed;

        Math3D.AngleVectors(self.s.angles, f, r, u);
        Math3D.VectorMA(self.s.origin, self.move_origin[0], f, start);
        Math3D.VectorMA(start, self.move_origin[1], r, start);
        Math3D.VectorMA(start, self.move_origin[2], u, start);

        damage = (int) (100 + Lib.random() * 50);
        speed = (int) (550 + 50 * gameExports.gameCvars.skill.value);
        GameWeapon.fire_rocket(self.teammaster.getOwner(), start, f, damage, speed, 150,
                damage, gameExports);
        gameExports.gameImports.positioned_sound(start, self, Defines.CHAN_WEAPON,
                gameExports.gameImports.soundindex("weapons/rocklf1a.wav"), 1,
                Defines.ATTN_NORM, 0);
    }

    public static void SP_turret_breach(SubgameEntity self, GameExportsImpl gameExports) {
        self.solid = Defines.SOLID_BSP;
        self.movetype = GameDefines.MOVETYPE_PUSH;
        gameExports.gameImports.setmodel(self, self.model);

        if (self.speed == 0)
            self.speed = 50;
        if (self.dmg == 0)
            self.dmg = 10;

        if (gameExports.st.minpitch == 0)
            gameExports.st.minpitch = -30;
        if (gameExports.st.maxpitch == 0)
            gameExports.st.maxpitch = 30;
        if (gameExports.st.maxyaw == 0)
            gameExports.st.maxyaw = 360;

        self.pos1[Defines.PITCH] = -1 * gameExports.st.minpitch;
        self.pos1[Defines.YAW] = gameExports.st.minyaw;
        self.pos2[Defines.PITCH] = -1 * gameExports.st.maxpitch;
        self.pos2[Defines.YAW] = gameExports.st.maxyaw;

        self.ideal_yaw = self.s.angles[Defines.YAW];
        self.move_angles[Defines.YAW] = self.ideal_yaw;

        self.blocked = turret_blocked;

        self.think = turret_breach_finish_init;
        self.nextthink = gameExports.level.time + Defines.FRAMETIME;
        gameExports.gameImports.linkentity(self);
    }

    /**
     * QUAKED turret_base (0 0 0) ? This portion of the turret changes yaw only.
     * MUST be teamed with a turret_breach.
     */

    public static void SP_turret_base(SubgameEntity self, GameExportsImpl gameExports) {
        self.solid = Defines.SOLID_BSP;
        self.movetype = GameDefines.MOVETYPE_PUSH;
        gameExports.gameImports.setmodel(self, self.model);
        self.blocked = turret_blocked;
        gameExports.gameImports.linkentity(self);
    }

    public static void SP_turret_driver(SubgameEntity self, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.deathmatch.value != 0) {
            GameUtil.G_FreeEdict(self, gameExports);
            return;
        }

        self.movetype = GameDefines.MOVETYPE_PUSH;
        self.solid = Defines.SOLID_BBOX;
        self.s.modelindex = gameExports.gameImports
                .modelindex("models/monsters/infantry/tris.md2");
        Math3D.VectorSet(self.mins, -16, -16, -24);
        Math3D.VectorSet(self.maxs, 16, 16, 32);

        self.health = 100;
        self.gib_health = 0;
        self.mass = 200;
        self.viewheight = 24;

        self.die = turret_driver_die;
        self.monsterinfo.stand = M_Infantry.infantry_stand;

        self.flags |= GameDefines.FL_NO_KNOCKBACK;

        gameExports.level.total_monsters++;

        self.svflags |= Defines.SVF_MONSTER;
        self.s.renderfx |= Defines.RF_FRAMELERP;
        self.takedamage = Defines.DAMAGE_AIM;
        self.use = GameUtil.monster_use;
        self.clipmask = Defines.MASK_MONSTERSOLID;
        Math3D.VectorCopy(self.s.origin, self.s.old_origin);
        self.monsterinfo.aiflags |= GameDefines.AI_STAND_GROUND | GameDefines.AI_DUCKED;

        if (gameExports.st.item != null) {
            self.item = GameItems.FindItemByClassname(gameExports.st.item, gameExports);
            if (self.item == null)
                gameExports.gameImports.dprintf(self.classname + " at "
                        + Lib.vtos(self.s.origin) + " has bad item: "
                        + gameExports.st.item + "\n");
        }

        self.think = turret_driver_link;
        self.nextthink = gameExports.level.time + Defines.FRAMETIME;

        gameExports.gameImports.linkentity(self);
    }

    static EntBlockedAdapter turret_blocked = new EntBlockedAdapter() {
    	public String getID() { return "turret_blocked"; }
        public void blocked(SubgameEntity self, SubgameEntity obstacle, GameExportsImpl gameExports) {

            if (obstacle.takedamage != 0) {
                SubgameEntity attacker;
                if (self.teammaster.getOwner() != null)
                    attacker = self.teammaster.getOwner();
                else
                    attacker = self.teammaster;
                GameCombat.T_Damage(obstacle, self, attacker, Globals.vec3_origin,
                        obstacle.s.origin, Globals.vec3_origin,
                        self.teammaster.dmg, 10, 0, GameDefines.MOD_CRUSH, gameExports);
            }
        }
    };

    static EntThinkAdapter turret_breach_think = new EntThinkAdapter() {
    	public String getID() { return "turret_breach_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            float[] current_angles = { 0, 0, 0 };
            float[] delta = { 0, 0, 0 };

            Math3D.VectorCopy(self.s.angles, current_angles);
            AnglesNormalize(current_angles);

            AnglesNormalize(self.move_angles);
            if (self.move_angles[Defines.PITCH] > 180)
                self.move_angles[Defines.PITCH] -= 360;

            // clamp angles to mins & maxs
            if (self.move_angles[Defines.PITCH] > self.pos1[Defines.PITCH])
                self.move_angles[Defines.PITCH] = self.pos1[Defines.PITCH];
            else if (self.move_angles[Defines.PITCH] < self.pos2[Defines.PITCH])
                self.move_angles[Defines.PITCH] = self.pos2[Defines.PITCH];

            if ((self.move_angles[Defines.YAW] < self.pos1[Defines.YAW])
                    || (self.move_angles[Defines.YAW] > self.pos2[Defines.YAW])) {
                float dmin, dmax;

                dmin = Math.abs(self.pos1[Defines.YAW]
                        - self.move_angles[Defines.YAW]);
                if (dmin < -180)
                    dmin += 360;
                else if (dmin > 180)
                    dmin -= 360;
                dmax = Math.abs(self.pos2[Defines.YAW]
                        - self.move_angles[Defines.YAW]);
                if (dmax < -180)
                    dmax += 360;
                else if (dmax > 180)
                    dmax -= 360;
                if (Math.abs(dmin) < Math.abs(dmax))
                    self.move_angles[Defines.YAW] = self.pos1[Defines.YAW];
                else
                    self.move_angles[Defines.YAW] = self.pos2[Defines.YAW];
            }

            Math3D.VectorSubtract(self.move_angles, current_angles, delta);
            if (delta[0] < -180)
                delta[0] += 360;
            else if (delta[0] > 180)
                delta[0] -= 360;
            if (delta[1] < -180)
                delta[1] += 360;
            else if (delta[1] > 180)
                delta[1] -= 360;
            delta[2] = 0;

            if (delta[0] > self.speed * Defines.FRAMETIME)
                delta[0] = self.speed * Defines.FRAMETIME;
            if (delta[0] < -1 * self.speed * Defines.FRAMETIME)
                delta[0] = -1 * self.speed * Defines.FRAMETIME;
            if (delta[1] > self.speed * Defines.FRAMETIME)
                delta[1] = self.speed * Defines.FRAMETIME;
            if (delta[1] < -1 * self.speed * Defines.FRAMETIME)
                delta[1] = -1 * self.speed * Defines.FRAMETIME;

            Math3D.VectorScale(delta, 1.0f / Defines.FRAMETIME, self.avelocity);

            self.nextthink = gameExports.level.time + Defines.FRAMETIME;

            for (SubgameEntity ent = self.teammaster; ent != null; ent = ent.teamchain)
                ent.avelocity[1] = self.avelocity[1];

            // if we have adriver, adjust his velocities
            if (self.getOwner() != null) {
                float angle;
                float target_z;
                float diff;
                float[] target = { 0, 0, 0 };
                float[] dir = { 0, 0, 0 };

                // angular is easy, just copy ours
                self.getOwner().avelocity[0] = self.avelocity[0];
                self.getOwner().avelocity[1] = self.avelocity[1];

                // x & y
                angle = self.s.angles[1] + self.getOwner().move_origin[1];
                angle *= (Math.PI * 2 / 360);
                target[0] = GameTurret.SnapToEights((float) (self.s.origin[0] + 
                			Math.cos(angle) * self.getOwner().move_origin[0]));
                target[1] = GameTurret.SnapToEights((float) (self.s.origin[1] + 
                			Math.sin(angle) * self.getOwner().move_origin[0]));
                target[2] = self.getOwner().s.origin[2];

                Math3D.VectorSubtract(target, self.getOwner().s.origin, dir);
                self.getOwner().velocity[0] = dir[0] * 1.0f / Defines.FRAMETIME;
                self.getOwner().velocity[1] = dir[1] * 1.0f / Defines.FRAMETIME;

                // z
                angle = self.s.angles[Defines.PITCH] * (float) (Math.PI * 2f / 360f);
                target_z = GameTurret.SnapToEights((float) (self.s.origin[2]
                                + self.getOwner().move_origin[0] * Math.tan(angle) + self.getOwner().move_origin[2]));

                diff = target_z - self.getOwner().s.origin[2];
                self.getOwner().velocity[2] = diff * 1.0f / Defines.FRAMETIME;

                if ((self.spawnflags & 65536) != 0) {
                    turret_breach_fire(self, gameExports);
                    self.spawnflags &= ~65536;
                }
            }
            return true;
        }
    };

    static EntThinkAdapter turret_breach_finish_init = new EntThinkAdapter() {
    	public String getID() { return "turret_breach_finish_init"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            // get and save info for muzzle location
            if (self.target == null) {
                gameExports.gameImports.dprintf(self.classname + " at "
                        + Lib.vtos(self.s.origin) + " needs a target\n");
            } else {
                self.target_ent = GameBase.G_PickTarget(self.target, gameExports);
                Math3D.VectorSubtract(self.target_ent.s.origin, self.s.origin,
                        self.move_origin);
                GameUtil.G_FreeEdict(self.target_ent, gameExports);
            }

            self.teammaster.dmg = self.dmg;
            self.think = turret_breach_think;
            self.think.think(self, gameExports);
            return true;
        }
    };

    /*
     * QUAKED turret_driver (1 .5 0) (-16 -16 -24) (16 16 32) Must NOT be on the
     * team with the rest of the turret parts. Instead it must target the
     * turret_breach.
     */
    static EntDieAdapter turret_driver_die = new EntDieAdapter() {
    	public String getID() { return "turret_driver_die"; }
        public void die(SubgameEntity self, SubgameEntity inflictor, SubgameEntity attacker,
                        int damage, float[] point, GameExportsImpl gameExports) {

            // level the gun
            self.target_ent.move_angles[0] = 0;

            // remove the driver from the end of them team chain
            SubgameEntity ent;
            for (ent = self.target_ent.teammaster; ent.teamchain != self; ent = ent.teamchain)
                ;
            ent.teamchain = null;
            self.teammaster = null;
            self.flags &= ~GameDefines.FL_TEAMSLAVE;

            self.target_ent.setOwner(null);
            self.target_ent.teammaster.setOwner(null);

            M_Infantry.infantry_die.die(self, inflictor, attacker, damage, null, gameExports);
        }
    };

    static EntThinkAdapter turret_driver_think = new EntThinkAdapter() {
    	public String getID() { return "turret_driver_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            float[] target = { 0, 0, 0 };
            float[] dir = { 0, 0, 0 };
            float reaction_time;

            self.nextthink = gameExports.level.time + Defines.FRAMETIME;

            if (self.enemy != null
                    && (!self.enemy.inuse || self.enemy.health <= 0))
                self.enemy = null;

            if (null == self.enemy) {
                if (!GameUtil.FindTarget(self, gameExports))
                    return true;
                self.monsterinfo.trail_time = gameExports.level.time;
                self.monsterinfo.aiflags &= ~GameDefines.AI_LOST_SIGHT;
            } else {
                if (GameUtil.visible(self, self.enemy, gameExports)) {
                    if ((self.monsterinfo.aiflags & GameDefines.AI_LOST_SIGHT) != 0) {
                        self.monsterinfo.trail_time = gameExports.level.time;
                        self.monsterinfo.aiflags &= ~GameDefines.AI_LOST_SIGHT;
                    }
                } else {
                    self.monsterinfo.aiflags |= GameDefines.AI_LOST_SIGHT;
                    return true;
                }
            }

            // let the turret know where we want it to aim
            Math3D.VectorCopy(self.enemy.s.origin, target);
            target[2] += self.enemy.viewheight;
            Math3D.VectorSubtract(target, self.target_ent.s.origin, dir);
            Math3D.vectoangles(dir, self.target_ent.move_angles);

            // decide if we should shoot
            if (gameExports.level.time < self.monsterinfo.attack_finished)
                return true;

            reaction_time = (3 - gameExports.gameCvars.skill.value) * 1.0f;
            if ((gameExports.level.time - self.monsterinfo.trail_time) < reaction_time)
                return true;

            self.monsterinfo.attack_finished = gameExports.level.time
                    + reaction_time + 1.0f;
            //FIXME how do we really want to pass this along?
            self.target_ent.spawnflags |= 65536;
            return true;
        }
    };

    public static EntThinkAdapter turret_driver_link = new EntThinkAdapter() {
    	public String getID() { return "turret_driver_link"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            float[] vec = { 0, 0, 0 };

            self.think = turret_driver_think;
            self.nextthink = gameExports.level.time + Defines.FRAMETIME;

            self.target_ent = GameBase.G_PickTarget(self.target, gameExports);
            self.target_ent.setOwner(self);
            self.target_ent.teammaster.setOwner(self);
            Math3D.VectorCopy(self.target_ent.s.angles, self.s.angles);

            vec[0] = self.target_ent.s.origin[0] - self.s.origin[0];
            vec[1] = self.target_ent.s.origin[1] - self.s.origin[1];
            vec[2] = 0;
            self.move_origin[0] = Math3D.VectorLength(vec);

            Math3D.VectorSubtract(self.s.origin, self.target_ent.s.origin, vec);
            Math3D.vectoangles(vec, vec);
            AnglesNormalize(vec);
            
            self.move_origin[1] = vec[1];
            self.move_origin[2] = self.s.origin[2] - self.target_ent.s.origin[2];

            // add the driver to the end of them team chain
            SubgameEntity ent;
            for (ent = self.target_ent.teammaster; ent.teamchain != null; ent = ent.teamchain)
                ;
            ent.teamchain = self;
            self.teammaster = self.target_ent.teammaster;
            self.flags |= GameDefines.FL_TEAMSLAVE;
            return true;
        }
    };
}