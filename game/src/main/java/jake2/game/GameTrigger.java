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

// Created on 27.12.2003 by RST.

package jake2.game;

import jake2.game.adapters.EntTouchAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.cplane_t;
import jake2.qcommon.csurface_t;

import static jake2.game.TriggersKt.initTrigger;

class GameTrigger {


    static void SP_trigger_monsterjump(SubgameEntity self, GameExportsImpl gameExports) {
        if (0 == self.speed)
            self.speed = 200;
        if (0 == self.st.height)
            self.st.height = 200;
        if (self.s.angles[Defines.YAW] == 0)
            self.s.angles[Defines.YAW] = 360;
        initTrigger(self, gameExports);
        self.touch = trigger_monsterjump_touch;
        self.movedir[2] = self.st.height;
    }


    /*
     * ==============================================================================
     * 
     * trigger_monsterjump
     * 
     * ==============================================================================
     */

    /**
     * QUAKED trigger_monsterjump (.5 .5 .5) ? Walking monsters that touch this
     * will jump in the direction of the trigger's angle "speed" default to 200,
     * the speed thrown forward "height" default to 200, the speed thrown
     * upwards
     */

    private static EntTouchAdapter trigger_monsterjump_touch = new EntTouchAdapter() {
    	public String getID(){ return "trigger_monsterjump_touch"; }
        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            if ((other.flags & (GameDefines.FL_FLY | GameDefines.FL_SWIM)) != 0)
                return;
            if ((other.svflags & Defines.SVF_DEADMONSTER) != 0)
                return;
            if (0 == (other.svflags & Defines.SVF_MONSTER))
                return;

            // set XY even if not on ground, so the jump will clear lips
            other.velocity[0] = self.movedir[0] * self.speed;
            other.velocity[1] = self.movedir[1] * self.speed;

            if (other.groundentity != null)
                return;

            other.groundentity = null;
            other.velocity[2] = self.movedir[2];
        }
    };
}
