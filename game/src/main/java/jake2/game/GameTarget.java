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
// $Id: GameTarget.java,v 1.8 2006-01-21 21:53:31 salomo Exp $
package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.edict_t;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.SplashTEMessage;
import jake2.qcommon.trace_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

class GameTarget {

    static void target_laser_on(SubgameEntity self, GameExportsImpl gameExports) {
        if (null == self.activator)
            self.activator = self;
        self.spawnflags |= 0x80000001;
        self.svflags &= ~Defines.SVF_NOCLIENT;
        target_laser_think.think(self, gameExports);
    }

    static void target_laser_off(SubgameEntity self) {
        self.spawnflags &= ~1;
        self.svflags |= Defines.SVF_NOCLIENT;
        self.think.nextTime = 0;
    }


    static EntThinkAdapter target_laser_think = new EntThinkAdapter() {
    	public String getID() { return "target_laser_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl game) {

            float[] start = { 0, 0, 0 };
            float[] end = { 0, 0, 0 };
            trace_t tr;
            float[] point = { 0, 0, 0 };
            float[] last_movedir = { 0, 0, 0 };
            int count;

            if ((self.spawnflags & 0x80000000) != 0)
                count = 8;
            else
                count = 4;

            if (self.enemy != null) {
                Math3D.VectorCopy(self.movedir, last_movedir);
                Math3D.VectorMA(self.enemy.absmin, 0.5f, self.enemy.size, point);
                Math3D.VectorSubtract(point, self.s.origin, self.movedir);
                Math3D.VectorNormalize(self.movedir);
                if (!Math3D.VectorEquals(self.movedir, last_movedir))
                    self.spawnflags |= 0x80000000;
            }

            edict_t ignore = self;
            Math3D.VectorCopy(self.s.origin, start);
            Math3D.VectorMA(start, 2048, self.movedir, end);
            while (true) {
                tr = game.gameImports.trace(start, null, null, end, ignore,
                        Defines.CONTENTS_SOLID | Defines.CONTENTS_MONSTER
                                | Defines.CONTENTS_DEADMONSTER);

                SubgameEntity target = (SubgameEntity) tr.ent;
                if (target == null)
                    break;

                // hurt it if we can
                if ((target.takedamage != 0)
                        && 0 == (target.flags & GameDefines.FL_IMMUNE_LASER))
                    GameCombat.T_Damage((SubgameEntity) target, self, self.activator,
                            self.movedir, tr.endpos, Globals.vec3_origin,
                            self.dmg, 1, DamageFlags.DAMAGE_ENERGY,
                            GameDefines.MOD_TARGET_LASER, game);

                // if we hit something that's not a monster or player or is
                // immune to lasers, we're done
                if (0 == (target.svflags & Defines.SVF_MONSTER)
                        && (null == target.getClient())) {
                    if ((self.spawnflags & 0x80000000) != 0) {
                        self.spawnflags &= ~0x80000000;
                        game.gameImports.multicastMessage(tr.endpos, new SplashTEMessage(Defines.TE_LASER_SPARKS, count, tr.endpos, tr.plane.normal, self.s.skinnum), MulticastTypes.MULTICAST_PVS);
                    }
                    break;
                }

                ignore = target;
                Math3D.VectorCopy(tr.endpos, start);
            }

            Math3D.VectorCopy(tr.endpos, self.s.old_origin);

            self.think.nextTime = game.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    static EntUseAdapter target_laser_use = new EntUseAdapter() {
    	public String getID() { return "target_laser_use"; }

        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            self.activator = activator;
            if ((self.spawnflags & 1) != 0)
                target_laser_off(self);
            else
                target_laser_on(self, gameExports);
        }
    };

    private static EntThinkAdapter target_laser_start = new EntThinkAdapter() {
    	public String getID() { return "target_laser_start"; }
        public boolean think(SubgameEntity self, GameExportsImpl game) {

            self.movetype = GameDefines.MOVETYPE_NONE;
            self.solid = Defines.SOLID_NOT;
            self.s.renderfx |= Defines.RF_BEAM | Defines.RF_TRANSLUCENT;
            self.s.modelindex = 1; // must be non-zero

            // set the beam diameter
            if ((self.spawnflags & 64) != 0)
                self.s.frame = 16;
            else
                self.s.frame = 4;

            // set the color
            if ((self.spawnflags & 2) != 0)
                self.s.skinnum = 0xf2f2f0f0;
            else if ((self.spawnflags & 4) != 0)
                self.s.skinnum = 0xd0d1d2d3;
            else if ((self.spawnflags & 8) != 0)
                self.s.skinnum = 0xf3f3f1f1;
            else if ((self.spawnflags & 16) != 0)
                self.s.skinnum = 0xdcdddedf;
            else if ((self.spawnflags & 32) != 0)
                self.s.skinnum = 0xe0e1e2e3;

            if (self.enemy == null) {
                if (self.target != null) {
                    EdictIterator edit = GameBase.G_Find(null, GameBase.findByTargetName, self.target, game);
                    if (null == edit)
                        game.gameImports.dprintf(self.classname + " at " + Lib.vtos(self.s.origin) + ": " + self.target + " is a bad target\n");
                    self.enemy = edit.o;
                } else {
                    GameBase.G_SetMovedir(self.s.angles, self.movedir);
                }
            }
            self.use = target_laser_use;
            self.think.action = target_laser_think;

            if (0 == self.dmg)
                self.dmg = 1;

            Math3D.VectorSet(self.mins, -8, -8, -8);
            Math3D.VectorSet(self.maxs, 8, 8, 8);
            game.gameImports.linkentity(self);

            if ((self.spawnflags & 1) != 0)
                target_laser_on(self, game);
            else
                target_laser_off(self);
            return true;
        }
    };
}
