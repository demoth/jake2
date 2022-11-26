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
import jake2.game.items.GameItems;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.cplane_t;
import jake2.qcommon.csurface_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import static jake2.game.TriggersKt.multiTrigger;

class GameTrigger {

    private static void InitTrigger(SubgameEntity self, GameExportsImpl gameExports) {
        if (!Math3D.VectorEquals(self.s.angles, Globals.vec3_origin))
            GameBase.G_SetMovedir(self.s.angles, self.movedir);

        self.solid = Defines.SOLID_TRIGGER;
        self.movetype = GameDefines.MOVETYPE_NONE;
        gameExports.gameImports.setmodel(self, self.model);
        self.svflags = Defines.SVF_NOCLIENT;
    }

    static void SP_trigger_relay(SubgameEntity self) {
        self.use = trigger_relay_use;
    }

    static void SP_trigger_key(SubgameEntity self, GameExportsImpl gameExports) {
        if (self.st.item == null) {
            gameExports.gameImports.dprintf("no key item for trigger_key at "
                    + Lib.vtos(self.s.origin) + "\n");
            return;
        }
        self.item = GameItems.FindItemByClassname(self.st.item, gameExports);

        if (null == self.item) {
            gameExports.gameImports.dprintf("item " + self.st.item
                    + " not found for trigger_key at "
                    + Lib.vtos(self.s.origin) + "\n");
            return;
        }

        if (self.target == null) {
            gameExports.gameImports.dprintf(self.classname + " at "
                    + Lib.vtos(self.s.origin) + " has no target\n");
            return;
        }

        gameExports.gameImports.soundindex("misc/keytry.wav");
        gameExports.gameImports.soundindex("misc/keyuse.wav");

        self.use = trigger_key_use;
    }

    static void SP_trigger_counter(SubgameEntity self) {
        self.wait = -1;
        if (0 == self.count)
            self.count = 2;

        self.use = trigger_counter_use;
    }

    /*
     * QUAKED trigger_push (.5 .5 .5) ? PUSH_ONCE Pushes the player "speed"
     * defaults to 1000
     */
    static void SP_trigger_push(SubgameEntity self, GameExportsImpl gameExports) {
        InitTrigger(self, gameExports);
        gameExports.windsound_index = gameExports.gameImports.soundindex("misc/windfly.wav");
        self.touch = trigger_push_touch;
        if (0 == self.speed)
            self.speed = 1000;
        gameExports.gameImports.linkentity(self);
    }

    static void SP_trigger_hurt(SubgameEntity self, GameExportsImpl gameExports) {
        InitTrigger(self, gameExports);

        self.noise_index = gameExports.gameImports.soundindex("world/electro.wav");
        self.touch = hurt_touch;

        if (0 == self.dmg)
            self.dmg = 5;

        if ((self.spawnflags & 1) != 0)
            self.solid = Defines.SOLID_NOT;
        else
            self.solid = Defines.SOLID_TRIGGER;

        if ((self.spawnflags & 2) != 0)
            self.use = hurt_use;

        gameExports.gameImports.linkentity(self);
    }

    static void SP_trigger_gravity(SubgameEntity self, GameExportsImpl gameExports) {
        if (self.st.gravity == null) {
            gameExports.gameImports.dprintf("trigger_gravity without gravity set at "
                    + Lib.vtos(self.s.origin) + "\n");
            gameExports.freeEntity(self);
            return;
        }

        InitTrigger(self, gameExports);
        self.gravity = Lib.atoi(self.st.gravity);
        self.touch = trigger_gravity_touch;
    }

    static void SP_trigger_monsterjump(SubgameEntity self, GameExportsImpl gameExports) {
        if (0 == self.speed)
            self.speed = 200;
        if (0 == self.st.height)
            self.st.height = 200;
        if (self.s.angles[Defines.YAW] == 0)
            self.s.angles[Defines.YAW] = 360;
        InitTrigger(self, gameExports);
        self.touch = trigger_monsterjump_touch;
        self.movedir[2] = self.st.height;
    }

    /**
     * QUAKED trigger_relay (.5 .5 .5) (-8 -8 -8) (8 8 8) This fixed size
     * trigger cannot be touched, it can only be fired by other events.
     */
    private static EntUseAdapter trigger_relay_use = new EntUseAdapter() {
    	public String getID(){ return "trigger_relay_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            GameUtil.G_UseTargets(self, activator, gameExports);
        }
    };

    /*
     * ==============================================================================
     * 
     * trigger_key
     * 
     * ==============================================================================
     */

    /**
     * QUAKED trigger_key (.5 .5 .5) (-8 -8 -8) (8 8 8) A relay trigger that
     * only fires it's targets if player has the proper key. Use "item" to
     * specify the required key, for example "key_data_cd"
     */

    private static EntUseAdapter trigger_key_use = new EntUseAdapter() {
    	public String getID(){ return "trigger_key_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            int index;

            if (self.item == null)
                return;
            gclient_t activatorClient = activator.getClient();
            if (activatorClient == null)
                return;

            index = self.item.index;
            if (activatorClient.pers.inventory[index] == 0) {
                if (gameExports.level.time < self.touch_debounce_time)
                    return;
                self.touch_debounce_time = gameExports.level.time + 5.0f;
                gameExports.gameImports.centerprintf(activator, "You need the "
                        + self.item.pickup_name);
                gameExports.gameImports.sound(activator, Defines.CHAN_AUTO,
                        gameExports.gameImports.soundindex("misc/keytry.wav"), 1,
                                Defines.ATTN_NORM, 0);
                return;
            }

            gameExports.gameImports.sound(activator, Defines.CHAN_AUTO, gameExports.gameImports
                    .soundindex("misc/keyuse.wav"), 1, Defines.ATTN_NORM, 0);
            if (gameExports.gameCvars.coop.value != 0) {
                int player;
                SubgameEntity ent;

                if ("key_power_cube".equals(self.item.classname)) {
                    int cube;

                    for (cube = 0; cube < 8; cube++)
                        if ((activatorClient.pers.power_cubes & (1 << cube)) != 0)
                            break;
                    for (player = 1; player <= gameExports.game.maxclients; player++) {
                        ent = gameExports.g_edicts[player];
                        if (!ent.inuse)
                            continue;
                        gclient_t client = ent.getClient();
                        if (client == null)
                            continue;
                        if ((client.pers.power_cubes & (1 << cube)) != 0) {
                            client.pers.inventory[index]--;
                            client.pers.power_cubes &= ~(1 << cube);
                        }
                    }
                } else {
                    for (player = 1; player <= gameExports.game.maxclients; player++) {
                        ent = gameExports.g_edicts[player];
                        if (!ent.inuse)
                            continue;
                        gclient_t client = ent.getClient();
                        if (client == null)
                            continue;
                        client.pers.inventory[index] = 0;
                    }
                }
            } else {
                activatorClient.pers.inventory[index]--;
            }

            GameUtil.G_UseTargets(self, activator, gameExports);

            self.use = null;
        }
    };

    /**
     * QUAKED trigger_counter (.5 .5 .5) ? nomessage Acts as an intermediary for
     * an action that takes multiple inputs.
     * 
     * If nomessage is not set, t will print "1 more.. " etc when triggered and
     * "sequence complete" when finished.
     * 
     * After the counter has been triggered "count" times (default 2), it will
     * fire all of it's targets and remove itself.
     */
    private static EntUseAdapter trigger_counter_use = new EntUseAdapter() {
    	public String getID(){ return "trigger_counter_use"; }

        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (self.count == 0)
                return;

            self.count--;

            if (self.count != 0) {
                if (0 == (self.spawnflags & 1)) {
                    gameExports.gameImports.centerprintf(activator, self.count
                            + " more to go...");
                    gameExports.gameImports.sound(activator, Defines.CHAN_AUTO, gameExports.gameImports
                            .soundindex("misc/talk1.wav"), 1,
                            Defines.ATTN_NORM, 0);
                }
                return;
            }

            if (0 == (self.spawnflags & 1)) {
                gameExports.gameImports.centerprintf(activator, "Sequence completed!");
                gameExports.gameImports.sound(activator, Defines.CHAN_AUTO, gameExports.gameImports
                        .soundindex("misc/talk1.wav"), 1, Defines.ATTN_NORM, 0);
            }
            self.activator = activator;
            multiTrigger(self, gameExports);
        }
    };

    /*
     * ==============================================================================
     * 
     * trigger_push
     * 
     * ==============================================================================
     */

    private static final int PUSH_ONCE = 1;

    private static EntTouchAdapter trigger_push_touch = new EntTouchAdapter() {
    	public String getID(){ return "trigger_push_touch"; }
        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            if ("grenade".equals(other.classname)) {
                Math3D.VectorScale(self.movedir, self.speed * 10,
                        other.velocity);
            } else if (other.health > 0) {
                Math3D.VectorScale(self.movedir, self.speed * 10,
                        other.velocity);

                gclient_t otherClient = other.getClient();
                if (otherClient != null) {
                    // don't take falling damage immediately from this
                    Math3D.VectorCopy(other.velocity, otherClient.oldvelocity);
                    if (other.fly_sound_debounce_time < gameExports.level.time) {
                        other.fly_sound_debounce_time = gameExports.level.time + 1.5f;
                        gameExports.gameImports.sound(other, Defines.CHAN_AUTO, gameExports.windsound_index,
                                1, Defines.ATTN_NORM, 0);
                    }
                }
            }
            if ((self.spawnflags & PUSH_ONCE) != 0)
                gameExports.freeEntity(self);
        }
    };


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
     * trigger_gravity
     * 
     * ==============================================================================
     */

    /**
     * QUAKED trigger_gravity (.5 .5 .5) ? Changes the touching entites gravity
     * to the value of "gravity". 1.0 is standard gravity for the level.
     */

    private static EntTouchAdapter trigger_gravity_touch = new EntTouchAdapter() {
    	public String getID(){ return "trigger_gravity_touch"; }

        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            other.gravity = self.gravity;
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
