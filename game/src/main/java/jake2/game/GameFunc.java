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

// Created on 18.11.2003 by RST.
// $Id: GameFunc.java,v 1.9 2006-01-21 21:53:32 salomo Exp $
package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import static jake2.game.DoorKt.DOOR_START_OPEN;

class GameFunc {

    /**
     * QUAKED func_water (0 .5 .8) ? START_OPEN func_water is a moveable water
     * brush. It must be targeted to operate. Use a non-water texture at your
     * own risk.
     * 
     * START_OPEN causes the water to move to its destination when spawned and
     * operate in reverse.
     * 
     * "angle" determines the opening direction (up or down only) "speed"
     * movement speed (25 default) "wait" wait before returning (-1 default, -1 =
     * TOGGLE) "lip" lip remaining at end of move (0 default) "sounds" (yes,
     * these need to be changed) 0) no sound 1) water 2) lava
     */
    static void SP_func_water(SubgameEntity self, GameExportsImpl gameExports) {
        float[] abs_movedir = { 0, 0, 0 };

        GameBase.G_SetMovedir(self.s.angles, self.movedir);
        self.movetype = GameDefines.MOVETYPE_PUSH;
        self.solid = Defines.SOLID_BSP;
        gameExports.gameImports.setmodel(self, self.model);

        switch (self.sounds) {
        default:
            break;

        case 1: // water
            self.moveinfo.sound_start = gameExports.gameImports
                    .soundindex("world/mov_watr.wav");
            self.moveinfo.sound_end = gameExports.gameImports
                    .soundindex("world/stp_watr.wav");
            break;

        case 2: // lava
            self.moveinfo.sound_start = gameExports.gameImports
                    .soundindex("world/mov_watr.wav");
            self.moveinfo.sound_end = gameExports.gameImports
                    .soundindex("world/stp_watr.wav");
            break;
        }

        // calculate second position
        Math3D.VectorCopy(self.s.origin, self.pos1);
        abs_movedir[0] = Math.abs(self.movedir[0]);
        abs_movedir[1] = Math.abs(self.movedir[1]);
        abs_movedir[2] = Math.abs(self.movedir[2]);
        self.moveinfo.distance = abs_movedir[0] * self.size[0] + abs_movedir[1]
                * self.size[1] + abs_movedir[2] * self.size[2]
                - self.st.lip;
        Math3D.VectorMA(self.pos1, self.moveinfo.distance, self.movedir,
                self.pos2);

        // if it starts open, switch the positions
        if ((self.spawnflags & DOOR_START_OPEN) != 0) {
            Math3D.VectorCopy(self.pos2, self.s.origin);
            Math3D.VectorCopy(self.pos1, self.pos2);
            Math3D.VectorCopy(self.s.origin, self.pos1);
        }

        Math3D.VectorCopy(self.pos1, self.moveinfo.start_origin);
        Math3D.VectorCopy(self.s.angles, self.moveinfo.start_angles);
        Math3D.VectorCopy(self.pos2, self.moveinfo.end_origin);
        Math3D.VectorCopy(self.s.angles, self.moveinfo.end_angles);

        self.moveinfo.state = STATE_BOTTOM;

        if (0 == self.speed)
            self.speed = 25;
        self.moveinfo.accel = self.moveinfo.decel = self.moveinfo.speed = self.speed;

        if (0 == self.wait)
            self.wait = -1;
        self.moveinfo.wait = self.wait;

        self.use = DoorKt.getDoorOpenUse();

        if (self.wait == -1)
            self.spawnflags |= DOOR_TOGGLE;

        self.classname = "func_door";

        gameExports.gameImports.linkentity(self);
    }


    static void SP_func_timer(SubgameEntity self, GameExportsImpl gameExports) {
        if (0 == self.wait)
            self.wait = 1.0f;

        self.use = func_timer_use;
        self.think.action = func_timer_think;

        if (self.random >= self.wait) {
            self.random = self.wait - Defines.FRAMETIME;
            gameExports.gameImports.dprintf("func_timer at " + Lib.vtos(self.s.origin)
                    + " has random >= wait\n");
        }

        if ((self.spawnflags & 1) != 0) {
            self.think.nextTime = gameExports.level.time + 1.0f + self.st.pausetime
                    + self.delay + self.wait + Lib.crandom() * self.random;
            self.activator = self;
        }

        self.svflags = Defines.SVF_NOCLIENT;
    }

    public final static int STATE_TOP = 0;

    public final static int STATE_BOTTOM = 1;

    public final static int STATE_UP = 2;

    public final static int STATE_DOWN = 3;

    public final static int DOOR_TOGGLE = 32;

    /*
     * QUAKED trigger_elevator (0.3 0.1 0.6) (-8 -8 -8) (8 8 8)
     */
    private static EntUseAdapter trigger_elevator_use = new EntUseAdapter() {
        public String getID() { return "trigger_elevator_use";}

        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {

            if (self.movetarget.think.nextTime != 0) {
                //			gi.dprintf("elevator busy\n");
                return;
            }

            if (null == other.pathtarget) {
                gameExports.gameImports.dprintf("elevator used with no pathtarget\n");
                return;
            }

            SubgameEntity target = GameBase.G_PickTarget(other.pathtarget, gameExports);
            if (null == target) {
                gameExports.gameImports.dprintf("elevator used with bad pathtarget: "
                        + other.pathtarget + "\n");
                return;
            }

            self.movetarget.target_ent = target;
            TrainKt.trainResume(self.movetarget, gameExports);
        }
    };

    private static EntThinkAdapter trigger_elevator_init = new EntThinkAdapter() {
        public String getID() { return "trigger_elevator_init";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            if (null == self.target) {
                gameExports.gameImports.dprintf("trigger_elevator has no target\n");
                return true;
            }
            self.movetarget = GameBase.G_PickTarget(self.target, gameExports);
            if (null == self.movetarget) {
                gameExports.gameImports.dprintf("trigger_elevator unable to find target "
                        + self.target + "\n");
                return true;
            }
            if (!"func_train".equals(self.movetarget.classname)) {
                gameExports.gameImports.dprintf("trigger_elevator target " + self.target
                        + " is not a train\n");
                return true;
            }

            self.use = trigger_elevator_use;
            self.svflags = Defines.SVF_NOCLIENT;
            return true;
        }
    };

    static EntThinkAdapter SP_trigger_elevator = new EntThinkAdapter() {
        public String getID() { return "sp_trigger_elevator";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            self.think.action = trigger_elevator_init;
            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    /*
     * QUAKED func_timer (0.3 0.1 0.6) (-8 -8 -8) (8 8 8) START_ON "wait" base
     * time between triggering all targets, default is 1 "random" wait variance,
     * default is 0
     * 
     * so, the basic time between firing is a random time between (wait -
     * random) and (wait + random)
     * 
     * "delay" delay before first firing when turned on, default is 0
     * 
     * "pausetime" additional delay used only the very first time and only if
     * spawned with START_ON
     * 
     * These can used but not touched.
     */

    private static EntThinkAdapter func_timer_think = new EntThinkAdapter() {
        public String getID() { return "func_timer_think";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            GameUtil.G_UseTargets(self, self.activator, gameExports);
            self.think.nextTime = gameExports.level.time + self.wait + Lib.crandom()
                    * self.random;
            return true;
        }
    };

    private static EntUseAdapter func_timer_use = new EntUseAdapter() {
        public String getID() { return "func_timer_use";}
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            self.activator = activator;

            // if on, turn it off
            if (self.think.nextTime != 0) {
                self.think.nextTime = 0;
                return;
            }

            // turn it on
            if (self.delay != 0)
                self.think.nextTime = gameExports.level.time + self.delay;
            else
                func_timer_think.think(self, gameExports);
        }
    };

    /*
     * QUAKED func_conveyor (0 .5 .8) ? START_ON TOGGLE Conveyors are stationary
     * brushes that move what's on them. The brush should be have a surface with
     * at least one current content enabled. speed default 100
     */

    private static EntUseAdapter func_conveyor_use = new EntUseAdapter() {
        public String getID() { return "func_conveyor_use";}
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if ((self.spawnflags & 1) != 0) {
                self.speed = 0;
                self.spawnflags &= ~1;
            } else {
                self.speed = self.count;
                self.spawnflags |= 1;
            }

            if (0 == (self.spawnflags & 2))
                self.count = 0;
        }
    };

    static EntThinkAdapter SP_func_conveyor = new EntThinkAdapter() {
        public String getID() { return "sp_func_conveyor";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            if (0 == self.speed)
                self.speed = 100;

            if (0 == (self.spawnflags & 1)) {
                self.count = (int) self.speed;
                self.speed = 0;
            }

            self.use = func_conveyor_use;

            gameExports.gameImports.setmodel(self, self.model);
            self.solid = Defines.SOLID_BSP;
            gameExports.gameImports.linkentity(self);
            return true;
        }
    };

    /**
     * QUAKED func_killbox (1 0 0) ? Kills everything inside when fired,
     * irrespective of protection.
     */
    private static EntUseAdapter use_killbox = new EntUseAdapter() {
        public String getID() { return "use_killbox";}
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            GameUtil.KillBox(self, gameExports);
        }
    };

    static EntThinkAdapter SP_func_killbox = new EntThinkAdapter() {
        public String getID() { return "sp_func_killbox";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            gameExports.gameImports.setmodel(ent, ent.model);
            ent.use = use_killbox;
            ent.svflags = Defines.SVF_NOCLIENT;
            return true;
        }
    };

}
