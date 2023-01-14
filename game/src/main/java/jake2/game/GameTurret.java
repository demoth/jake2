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

import jake2.game.adapters.EntDieAdapter;
import jake2.game.adapters.EntThinkAdapter;
import jake2.game.items.GameItems;
import jake2.game.monsters.M_Infantry;
import jake2.qcommon.Defines;
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




    public static void SP_turret_driver(SubgameEntity self, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.deathmatch.value != 0) {
            gameExports.freeEntity(self);
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

        if (self.st.item != null) {
            self.item = GameItems.FindItemByClassname(self.st.item, gameExports);
            if (self.item == null)
                gameExports.gameImports.dprintf(self.classname + " at "
                        + Lib.vtos(self.s.origin) + " has bad item: "
                        + self.st.item + "\n");
        }

        self.think.action = turret_driver_link;
        self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;

        gameExports.gameImports.linkentity(self);
    }

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

            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;

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

            self.think.action = turret_driver_think;
            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;

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
