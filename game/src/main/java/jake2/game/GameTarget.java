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


    static void SP_target_changelevel(SubgameEntity ent, GameExportsImpl gameExports) {
        if (ent.map == null) {
            gameExports.gameImports.dprintf("target_changelevel with no map at "
                    + Lib.vtos(ent.s.origin) + "\n");
            gameExports.freeEntity(ent);
            return;
        }

        // ugly hack because *SOMEBODY* screwed up their map
        if ((Lib.Q_stricmp(gameExports.level.mapname, "fact1") == 0)
                && (Lib.Q_stricmp(ent.map, "fact3") == 0))
            ent.map = "fact3$secret1";

        ent.use = use_target_changelevel;
        ent.svflags = Defines.SVF_NOCLIENT;
    }

    static void SP_target_crosslevel_trigger(SubgameEntity self) {
        self.svflags = Defines.SVF_NOCLIENT;
        self.use = trigger_crosslevel_trigger_use;
    }

    static void SP_target_crosslevel_target(SubgameEntity self, GameExportsImpl gameExports) {
        if (0 == self.delay)
            self.delay = 1;
        self.svflags = Defines.SVF_NOCLIENT;

        self.think.action = target_crosslevel_target_think;
        self.think.nextTime = gameExports.level.time + self.delay;
    }

    private static void target_laser_on(SubgameEntity self, GameExportsImpl gameExports) {
        if (null == self.activator)
            self.activator = self;
        self.spawnflags |= 0x80000001;
        self.svflags &= ~Defines.SVF_NOCLIENT;
        target_laser_think.think(self, gameExports);
    }

    private static void target_laser_off(SubgameEntity self) {
        self.spawnflags &= ~1;
        self.svflags |= Defines.SVF_NOCLIENT;
        self.think.nextTime = 0;
    }

    static void SP_target_laser(SubgameEntity self, GameExportsImpl gameExports) {
        // let everything else get spawned before we start firing
        self.think.action = target_laser_start;
        self.think.nextTime = gameExports.level.time + 1;
    }

    /**
     * QUAKED target_changelevel (1 0 0) (-8 -8 -8) (8 8 8) Changes level to
     * "map" when fired
     */
    private static EntUseAdapter use_target_changelevel = new EntUseAdapter() {
    	public String getID() { return "use_target_changelevel"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (gameExports.level.intermissiontime != 0)
                return; // already activated

            if (0 == gameExports.gameCvars.deathmatch.value && 0 == gameExports.gameCvars.coop.value) {
                if (gameExports.g_edicts[1].health <= 0)
                    return;
            }

            // if noexit, do a ton of damage to other
            if (gameExports.gameCvars.deathmatch.value != 0
                    && 0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_ALLOW_EXIT)
                    && other != gameExports.g_edicts[0] /* world */
            ) {
                GameCombat.T_Damage(other, self, self, Globals.vec3_origin,
                        other.s.origin, Globals.vec3_origin,
                        10 * other.max_health, 1000, 0, GameDefines.MOD_EXIT, gameExports);
                return;
            }

            // if multiplayer, let everyone know who hit the exit
            if (gameExports.gameCvars.deathmatch.value != 0) {
                if (activator != null) {
                    gclient_t activatorClient = activator.getClient();
                    if (activatorClient != null) gameExports.gameImports.bprintf(Defines.PRINT_HIGH,
                            activatorClient.pers.netname
                                    + " exited the level.\n");
                }
            }

            // if going to a new unit, clear cross triggers
            if (self.map.indexOf('*') > -1)
                gameExports.game.serverflags &= ~(Defines.SFL_CROSS_TRIGGER_MASK);

            PlayerHud.BeginIntermission(self, gameExports);
        }
    };

    /**
     * QUAKED target_crosslevel_trigger (.5 .5 .5) (-8 -8 -8) (8 8 8) trigger1
     * trigger2 trigger3 trigger4 trigger5 trigger6 trigger7 trigger8 Once this
     * trigger is touched/used, any trigger_crosslevel_target with the same
     * trigger number is automatically used when a level is started within the
     * same unit. It is OK to check multiple triggers. Message, delay, target,
     * and killtarget also work.
     */
    private static EntUseAdapter trigger_crosslevel_trigger_use = new EntUseAdapter() {
    	public String getID() { return "trigger_crosslevel_trigger_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            gameExports.game.serverflags |= self.spawnflags;
            gameExports.freeEntity(self);
        }
    };

    /**
     * QUAKED target_crosslevel_target (.5 .5 .5) (-8 -8 -8) (8 8 8) trigger1
     * trigger2 trigger3 trigger4 trigger5 trigger6 trigger7 trigger8 Triggered
     * by a trigger_crosslevel elsewhere within a unit. If multiple triggers are
     * checked, all must be true. Delay, target and killtarget also work.
     * 
     * "delay" delay before using targets if the trigger has been activated
     * (default 1)
     */
    private static EntThinkAdapter target_crosslevel_target_think = new EntThinkAdapter() {
    	public String getID() { return "target_crosslevel_target_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            if (self.spawnflags == (gameExports.game.serverflags
                    & Defines.SFL_CROSS_TRIGGER_MASK & self.spawnflags)) {
                GameUtil.G_UseTargets(self, self, gameExports);
                gameExports.freeEntity(self);
            }
            return true;
        }
    };

    /**
     * QUAKED target_laser (0 .5 .8) (-8 -8 -8) (8 8 8) START_ON RED GREEN BLUE
     * YELLOW ORANGE FAT When triggered, fires a laser. You can either set a
     * target or a direction.
     */
    private static EntThinkAdapter target_laser_think = new EntThinkAdapter() {
    	public String getID() { return "target_laser_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            edict_t ignore;
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

            ignore = self;
            Math3D.VectorCopy(self.s.origin, start);
            Math3D.VectorMA(start, 2048, self.movedir, end);
            while (true) {
                tr = gameExports.gameImports.trace(start, null, null, end, ignore,
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
                            GameDefines.MOD_TARGET_LASER, gameExports);

                // if we hit something that's not a monster or player or is
                // immune to lasers, we're done
                if (0 == (target.svflags & Defines.SVF_MONSTER)
                        && (null == target.getClient())) {
                    if ((self.spawnflags & 0x80000000) != 0) {
                        self.spawnflags &= ~0x80000000;
                        gameExports.gameImports.multicastMessage(tr.endpos, new SplashTEMessage(Defines.TE_LASER_SPARKS, count, tr.endpos, tr.plane.normal, self.s.skinnum), MulticastTypes.MULTICAST_PVS);
                    }
                    break;
                }

                ignore = target;
                Math3D.VectorCopy(tr.endpos, start);
            }

            Math3D.VectorCopy(tr.endpos, self.s.old_origin);

            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    private static EntUseAdapter target_laser_use = new EntUseAdapter() {
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
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

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

            if (null == self.enemy) {
                if (self.target != null) {
                    EdictIterator edit = GameBase.G_Find(null, GameBase.findByTargetName,
                            self.target, gameExports);
                    if (edit == null)
                        gameExports.gameImports.dprintf(self.classname + " at "
                                + Lib.vtos(self.s.origin) + ": " + self.target
                                + " is a bad target\n");
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
            gameExports.gameImports.linkentity(self);

            if ((self.spawnflags & 1) != 0)
                target_laser_on(self, gameExports);
            else
                target_laser_off(self);
            return true;
        }
    };
}
