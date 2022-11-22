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
import jake2.game.func.TrainKt;
import jake2.qcommon.Defines;

class GameFunc {


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
}

