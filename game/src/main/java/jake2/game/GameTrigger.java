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
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.cplane_t;
import jake2.qcommon.csurface_t;

import static jake2.game.TriggersKt.initTrigger;

class GameTrigger {

    static void SP_trigger_hurt(SubgameEntity self, GameExportsImpl game) {
        initTrigger(self, game);

        self.noise_index = game.gameImports.soundindex("world/electro.wav");
        self.touch = hurt_touch;

        if (0 == self.dmg)
            self.dmg = 5;

        if ((self.spawnflags & 1) != 0)
            self.solid = Defines.SOLID_NOT;
        else
            self.solid = Defines.SOLID_TRIGGER;

        if ((self.spawnflags & 2) != 0)
            self.use = hurt_use;

        game.gameImports.linkentity(self);
    }

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

    /**
     * QUAKED trigger_hurt (.5 .5 .5) ? START_OFF TOGGLE SILENT NO_PROTECTION
     * SLOW Any entity that touches this will be hurt.
     * 
     * It does dmg points of damage each server frame
     * 
     * SILENT supresses playing the sound SLOW changes the damage rate to once
     * per second NO_PROTECTION *nothing* stops the damage
     * 
     * "dmg" default 5 (whole numbers only)
     *  
     */
    private static EntUseAdapter hurt_use = new EntUseAdapter() {
    	public String getID(){ return "hurt_use"; }

        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (self.solid == Defines.SOLID_NOT)
                self.solid = Defines.SOLID_TRIGGER;
            else
                self.solid = Defines.SOLID_NOT;
            gameExports.gameImports.linkentity(self);

            if (0 == (self.spawnflags & 2))
                self.use = null;
        }
    };

    private static EntTouchAdapter hurt_touch = new EntTouchAdapter() {
    	public String getID(){ return "hurt_touch"; }
        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            int dflags;

            if (other.takedamage == 0)
                return;

            if (self.timestamp > gameExports.level.time)
                return;

            if ((self.spawnflags & 16) != 0)
                self.timestamp = gameExports.level.time + 1;
            else
                self.timestamp = gameExports.level.time + Defines.FRAMETIME;

            if (0 == (self.spawnflags & 4)) {
                if ((gameExports.level.framenum % 10) == 0)
                    gameExports.gameImports.sound(other, Defines.CHAN_AUTO,
                            self.noise_index, 1, Defines.ATTN_NORM, 0);
            }

            if ((self.spawnflags & 8) != 0)
                dflags = DamageFlags.DAMAGE_NO_PROTECTION;
            else
                dflags = 0;
            GameCombat.T_Damage(other, self, self, Globals.vec3_origin,
                    other.s.origin, Globals.vec3_origin, self.dmg, self.dmg,
                    dflags, GameDefines.MOD_TRIGGER_HURT, gameExports);
        }
    };

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
