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

import jake2.game.adapters.EntBlockedAdapter;
import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.EntTouchAdapter;
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.cplane_t;
import jake2.qcommon.csurface_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import static jake2.game.DoorsKt.DOOR_START_OPEN;

class GameFunc {

    public static void Move_Calc(SubgameEntity ent, float[] dest, EntThinkAdapter func, GameExportsImpl gameExports) {
        Math3D.VectorClear(ent.velocity);
        Math3D.VectorSubtract(dest, ent.s.origin, ent.moveinfo.dir);

        ent.moveinfo.remaining_distance = Math3D.VectorNormalize(ent.moveinfo.dir);
        ent.moveinfo.endfunc = func;

        if (ent.moveinfo.speed == ent.moveinfo.accel && ent.moveinfo.speed == ent.moveinfo.decel) {
            final boolean teamSlave = (ent.flags & GameDefines.FL_TEAMSLAVE) != 0;
            if (gameExports.level.current_entity == (teamSlave ? ent.teammaster: ent)) {
                Move_Begin.think(ent, gameExports);
            } else {
                ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
                ent.think.action = Move_Begin;
            }
        } else {
            // accelerative
            ent.moveinfo.current_speed = 0;
            ent.think.action = Think_AccelMove;
            ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
        }
    }

    public static void AngleMove_Calc(SubgameEntity ent, EntThinkAdapter func, GameExportsImpl gameExports) {
        Math3D.VectorClear(ent.avelocity);
        ent.moveinfo.endfunc = func;
        if (gameExports.level.current_entity == ((ent.flags & GameDefines.FL_TEAMSLAVE) != 0 ? ent.teammaster
                : ent)) {
            AngleMove_Begin.think(ent, gameExports);
        } else {
            ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            ent.think.action = AngleMove_Begin;
        }
    }

    /**
     * Think_AccelMove
     * 
     * The team has completed a frame of movement, so change the speed for the
     * next frame.
     */
    private static float AccelerationDistance(float target, float rate) {
        return target * ((target / rate) + 1) / 2;
    };

    private static void plat_CalcAcceleratedMove(moveinfo_t moveinfo) {
        float accel_dist;
        float decel_dist;

        moveinfo.move_speed = moveinfo.speed;

        if (moveinfo.remaining_distance < moveinfo.accel) {
            moveinfo.current_speed = moveinfo.remaining_distance;
            return;
        }

        accel_dist = AccelerationDistance(moveinfo.speed, moveinfo.accel);
        decel_dist = AccelerationDistance(moveinfo.speed, moveinfo.decel);

        if ((moveinfo.remaining_distance - accel_dist - decel_dist) < 0) {
            float f;

            f = (moveinfo.accel + moveinfo.decel)
                    / (moveinfo.accel * moveinfo.decel);
            moveinfo.move_speed = (float) ((-2 + Math.sqrt(4 - 4 * f
                    * (-2 * moveinfo.remaining_distance))) / (2 * f));
            decel_dist = AccelerationDistance(moveinfo.move_speed,
                    moveinfo.decel);
        }

        moveinfo.decel_distance = decel_dist;
    };

    private static void plat_Accelerate(moveinfo_t moveinfo) {
        // are we decelerating?
        if (moveinfo.remaining_distance <= moveinfo.decel_distance) {
            if (moveinfo.remaining_distance < moveinfo.decel_distance) {
                if (moveinfo.next_speed != 0) {
                    moveinfo.current_speed = moveinfo.next_speed;
                    moveinfo.next_speed = 0;
                    return;
                }
                if (moveinfo.current_speed > moveinfo.decel)
                    moveinfo.current_speed -= moveinfo.decel;
            }
            return;
        }

        // are we at full speed and need to start decelerating during this move?
        if (moveinfo.current_speed == moveinfo.move_speed)
            if ((moveinfo.remaining_distance - moveinfo.current_speed) < moveinfo.decel_distance) {
                float p1_distance;
                float p2_distance;
                float distance;

                p1_distance = moveinfo.remaining_distance
                        - moveinfo.decel_distance;
                p2_distance = moveinfo.move_speed
                        * (1.0f - (p1_distance / moveinfo.move_speed));
                distance = p1_distance + p2_distance;
                moveinfo.current_speed = moveinfo.move_speed;
                moveinfo.next_speed = moveinfo.move_speed - moveinfo.decel
                        * (p2_distance / distance);
                return;
            }

        // are we accelerating?
        if (moveinfo.current_speed < moveinfo.speed) {
            float old_speed;
            float p1_distance;
            float p1_speed;
            float p2_distance;
            float distance;

            old_speed = moveinfo.current_speed;

            // figure simple acceleration up to move_speed
            moveinfo.current_speed += moveinfo.accel;
            if (moveinfo.current_speed > moveinfo.speed)
                moveinfo.current_speed = moveinfo.speed;

            // are we accelerating throughout this entire move?
            if ((moveinfo.remaining_distance - moveinfo.current_speed) >= moveinfo.decel_distance)
                return;

            // during this move we will accelrate from current_speed to
            // move_speed
            // and cross over the decel_distance; figure the average speed for
            // the
            // entire move
            p1_distance = moveinfo.remaining_distance - moveinfo.decel_distance;
            p1_speed = (old_speed + moveinfo.move_speed) / 2.0f;
            p2_distance = moveinfo.move_speed
                    * (1.0f - (p1_distance / p1_speed));
            distance = p1_distance + p2_distance;
            moveinfo.current_speed = (p1_speed * (p1_distance / distance))
                    + (moveinfo.move_speed * (p2_distance / distance));
            moveinfo.next_speed = moveinfo.move_speed - moveinfo.decel
                    * (p2_distance / distance);
            return;
        }

        // we are at constant velocity (move_speed)
        return;
    };

    public static void plat_go_up(SubgameEntity ent, GameExportsImpl gameExports) {
        if (0 == (ent.flags & GameDefines.FL_TEAMSLAVE)) {
            if (ent.moveinfo.sound_start != 0)
                gameExports.gameImports.sound(ent, Defines.CHAN_NO_PHS_ADD
                        + Defines.CHAN_VOICE, ent.moveinfo.sound_start, 1,
                        Defines.ATTN_STATIC, 0);
            ent.s.sound = ent.moveinfo.sound_middle;
        }
        ent.moveinfo.state = STATE_UP;
        Move_Calc(ent, ent.moveinfo.start_origin, plat_hit_top, gameExports);
    }

    
    
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

        self.use = DoorsKt.getDoorOpenUse();

        if (self.wait == -1)
            self.spawnflags |= DOOR_TOGGLE;

        self.classname = "func_door";

        gameExports.gameImports.linkentity(self);
    }

    private static void train_resume(SubgameEntity self, GameExportsImpl gameExports) {
        float[] dest = { 0, 0, 0 };

        SubgameEntity ent = self.target_ent;

        Math3D.VectorSubtract(ent.s.origin, self.mins, dest);
        self.moveinfo.state = STATE_TOP;
        Math3D.VectorCopy(self.s.origin, self.moveinfo.start_origin);
        Math3D.VectorCopy(dest, self.moveinfo.end_origin);
        Move_Calc(self, dest, train_wait, gameExports);
        self.spawnflags |= TRAIN_START_ON;

    }

    static void SP_func_train(SubgameEntity self, GameExportsImpl gameExports) {
        self.movetype = GameDefines.MOVETYPE_PUSH;

        Math3D.VectorClear(self.s.angles);
        self.blocked = train_blocked;
        if ((self.spawnflags & TRAIN_BLOCK_STOPS) != 0)
            self.dmg = 0;
        else {
            if (0 == self.dmg)
                self.dmg = 100;
        }
        self.solid = Defines.SOLID_BSP;
        gameExports.gameImports.setmodel(self, self.model);

        if (self.st.noise != null)
            self.moveinfo.sound_middle = gameExports.gameImports
                    .soundindex(self.st.noise);

        if (0 == self.speed)
            self.speed = 100;

        self.moveinfo.speed = self.speed;
        self.moveinfo.accel = self.moveinfo.decel = self.moveinfo.speed;

        self.use = train_use;

        gameExports.gameImports.linkentity(self);

        if (self.target != null) {
            // start trains on the second frame, to make sure their targets have
            // had
            // a chance to spawn
            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            self.think.action = func_train_find;
        } else {
            gameExports.gameImports.dprintf("func_train without a target at "
                    + Lib.vtos(self.absmin) + "\n");
        }
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

    /**
     * PLATS
     * 
     * movement options:
     * 
     * linear smooth start, hard stop smooth start, smooth stop
     * 
     * start end acceleration speed deceleration begin sound end sound target
     * fired when reaching end wait at end
     * 
     * object characteristics that use move segments
     * --------------------------------------------- movetype_push, or
     * movetype_stop action when touched action when blocked action when used
     * disabled? auto trigger spawning
     * 
     */

    public final static int PLAT_LOW_TRIGGER = 1;

    public final static int STATE_TOP = 0;

    public final static int STATE_BOTTOM = 1;

    public final static int STATE_UP = 2;

    public final static int STATE_DOWN = 3;

    public final static int DOOR_TOGGLE = 32;


    private static EntThinkAdapter Move_Done = new EntThinkAdapter() {
        public String getID() { return "move_done";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            Math3D.VectorClear(ent.velocity);
            ent.moveinfo.endfunc.think(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter Move_Final = new EntThinkAdapter() {
        public String getID() { return "move_final";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            if (ent.moveinfo.remaining_distance == 0) {
                Move_Done.think(ent, gameExports);
                return true;
            }

            Math3D.VectorScale(ent.moveinfo.dir,
                    ent.moveinfo.remaining_distance / Defines.FRAMETIME,
                    ent.velocity);

            ent.think.action = Move_Done;
            ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    private static EntThinkAdapter Move_Begin = new EntThinkAdapter() {
        public String getID() { return "move_begin";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            float frames;

            if ((ent.moveinfo.speed * Defines.FRAMETIME) >= ent.moveinfo.remaining_distance) {
                Move_Final.think(ent, gameExports);
                return true;
            }
            Math3D.VectorScale(ent.moveinfo.dir, ent.moveinfo.speed,
                    ent.velocity);
            frames = (float) Math
                    .floor((ent.moveinfo.remaining_distance / ent.moveinfo.speed)
                            / Defines.FRAMETIME);
            ent.moveinfo.remaining_distance -= frames * ent.moveinfo.speed
                    * Defines.FRAMETIME;
            ent.think.nextTime = gameExports.level.time + (frames * Defines.FRAMETIME);
            ent.think.action = Move_Final;
            return true;
        }
    };

    //
    //	   Support routines for angular movement (changes in angle using avelocity)
    //

    private static EntThinkAdapter AngleMove_Done = new EntThinkAdapter() {
        public String getID() { return "agnle_move_done";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            Math3D.VectorClear(ent.avelocity);
            ent.moveinfo.endfunc.think(ent, gameExports);
            return true;
        }
    };

    private static EntThinkAdapter AngleMove_Final = new EntThinkAdapter() {
        public String getID() { return "angle_move_final";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            float[] move = { 0, 0, 0 };

            if (ent.moveinfo.state == STATE_UP)
                Math3D.VectorSubtract(ent.moveinfo.end_angles, ent.s.angles,
                        move);
            else
                Math3D.VectorSubtract(ent.moveinfo.start_angles, ent.s.angles,
                        move);

            if (Math3D.VectorEquals(move, Globals.vec3_origin)) {
                AngleMove_Done.think(ent, gameExports);
                return true;
            }

            Math3D.VectorScale(move, 1.0f / Defines.FRAMETIME, ent.avelocity);

            ent.think.action = AngleMove_Done;
            ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    private static EntThinkAdapter AngleMove_Begin = new EntThinkAdapter() {
        public String getID() { return "angle_move_begin";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            float[] destdelta = { 0, 0, 0 };
            float len;
            float traveltime;
            float frames;

            // set destdelta to the vector needed to move
            if (ent.moveinfo.state == STATE_UP)
                Math3D.VectorSubtract(ent.moveinfo.end_angles, ent.s.angles,
                        destdelta);
            else
                Math3D.VectorSubtract(ent.moveinfo.start_angles, ent.s.angles,
                        destdelta);

            // calculate length of vector
            len = Math3D.VectorLength(destdelta);

            // divide by speed to get time to reach dest
            traveltime = len / ent.moveinfo.speed;

            if (traveltime < Defines.FRAMETIME) {
                AngleMove_Final.think(ent, gameExports);
                return true;
            }

            frames = (float) (Math.floor(traveltime / Defines.FRAMETIME));

            // scale the destdelta vector by the time spent traveling to get
            // velocity
            Math3D.VectorScale(destdelta, 1.0f / traveltime, ent.avelocity);

            // set nextthink to trigger a think when dest is reached
            ent.think.nextTime = gameExports.level.time + frames * Defines.FRAMETIME;
            ent.think.action = AngleMove_Final;
            return true;
        }
    };

    private static EntThinkAdapter Think_AccelMove = new EntThinkAdapter() {
        public String getID() { return "thinc_accelmove";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            ent.moveinfo.remaining_distance -= ent.moveinfo.current_speed;

            if (ent.moveinfo.current_speed == 0) // starting or blocked
                plat_CalcAcceleratedMove(ent.moveinfo);

            plat_Accelerate(ent.moveinfo);

            // will the entire move complete on next frame?
            if (ent.moveinfo.remaining_distance <= ent.moveinfo.current_speed) {
                Move_Final.think(ent, gameExports);
                return true;
            }

            Math3D.VectorScale(ent.moveinfo.dir,
                    ent.moveinfo.current_speed * 10, ent.velocity);
            ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            ent.think.action = Think_AccelMove;
            return true;
        }
    };

    private static EntThinkAdapter plat_hit_top = new EntThinkAdapter() {
        public String getID() { return "plat_hit_top";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            if (0 == (ent.flags & GameDefines.FL_TEAMSLAVE)) {
                if (ent.moveinfo.sound_end != 0)
                    gameExports.gameImports.sound(ent, Defines.CHAN_NO_PHS_ADD
                            + Defines.CHAN_VOICE, ent.moveinfo.sound_end, 1,
                            Defines.ATTN_STATIC, 0);
                ent.s.sound = 0;
            }
            ent.moveinfo.state = STATE_TOP;

            ent.think.action = plat_go_down;
            ent.think.nextTime = gameExports.level.time + 3;
            return true;
        }
    };

    private static EntThinkAdapter plat_hit_bottom = new EntThinkAdapter() {
        public String getID() { return "plat_hit_bottom";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            if (0 == (ent.flags & GameDefines.FL_TEAMSLAVE)) {
                if (ent.moveinfo.sound_end != 0)
                    gameExports.gameImports.sound(ent, Defines.CHAN_NO_PHS_ADD
                            + Defines.CHAN_VOICE, ent.moveinfo.sound_end, 1,
                            Defines.ATTN_STATIC, 0);
                ent.s.sound = 0;
            }
            ent.moveinfo.state = STATE_BOTTOM;
            return true;
        }
    };

    public static EntThinkAdapter plat_go_down = new EntThinkAdapter() {
        public String getID() { return "plat_go_down";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            if (0 == (ent.flags & GameDefines.FL_TEAMSLAVE)) {
                if (ent.moveinfo.sound_start != 0)
                    gameExports.gameImports.sound(ent, Defines.CHAN_NO_PHS_ADD
                            + Defines.CHAN_VOICE, ent.moveinfo.sound_start, 1,
                            Defines.ATTN_STATIC, 0);
                ent.s.sound = ent.moveinfo.sound_middle;
            }
            ent.moveinfo.state = STATE_DOWN;
            Move_Calc(ent, ent.moveinfo.end_origin, plat_hit_bottom, gameExports);
            return true;
        }
    };

    public static EntTouchAdapter Touch_Plat_Center = new EntTouchAdapter() {
        public String getID() { return "touch_plat_center";}
        public void touch(SubgameEntity ent, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            if (other.getClient() == null)
                return;

            if (other.health <= 0)
                return;

            ent = ent.enemy; // now point at the plat, not the trigger
            if (ent.moveinfo.state == STATE_BOTTOM)
                plat_go_up(ent, gameExports);
            else if (ent.moveinfo.state == STATE_TOP) {
                ent.think.nextTime = gameExports.level.time + 1; // the player is still
                                                         // on the plat, so
                                                         // delay going down
            }
        }
    };

    /**
     * QUAKED func_rotating (0 .5 .8) ? START_ON REVERSE X_AXIS Y_AXIS
     * TOUCH_PAIN STOP ANIMATED ANIMATED_FAST You need to have an origin brush
     * as part of this entity. The center of that brush will be the point around
     * which it is rotated. It will rotate around the Z axis by default. You can
     * check either the X_AXIS or Y_AXIS box to change that.
     * 
     * "speed" determines how fast it moves; default value is 100. "dmg" damage
     * to inflict when blocked (2 default)
     * 
     * REVERSE will cause the it to rotate in the opposite direction. STOP mean
     * it will stop moving instead of pushing entities
     */

    private static EntBlockedAdapter rotating_blocked = new EntBlockedAdapter() {
        public String getID() { return "rotating_blocked";}
        public void blocked(SubgameEntity self, SubgameEntity obstacle, GameExportsImpl gameExports) {
            GameCombat.T_Damage(obstacle, self, self, Globals.vec3_origin,
                    obstacle.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
                    GameDefines.MOD_CRUSH, gameExports);
        }
    };

    private static EntTouchAdapter rotating_touch = new EntTouchAdapter() {
        public String getID() { return "rotating_touch";}
        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            if (self.avelocity[0] != 0 || self.avelocity[1] != 0
                    || self.avelocity[2] != 0)
                GameCombat.T_Damage(other, self, self, Globals.vec3_origin,
                        other.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
                        GameDefines.MOD_CRUSH, gameExports);
        }
    };

    private static EntUseAdapter rotating_use = new EntUseAdapter() {
        public String getID() { return "rotating_use";}
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (!Math3D.VectorEquals(self.avelocity, Globals.vec3_origin)) {
                self.s.sound = 0;
                Math3D.VectorClear(self.avelocity);
                self.touch = null;
            } else {
                self.s.sound = self.moveinfo.sound_middle;
                Math3D.VectorScale(self.movedir, self.speed, self.avelocity);
                if ((self.spawnflags & 16) != 0)
                    self.touch = rotating_touch;
            }
        }
    };

    static EntThinkAdapter SP_func_rotating = new EntThinkAdapter() {
        public String getID() { return "sp_func_rotating";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            ent.solid = Defines.SOLID_BSP;
            if ((ent.spawnflags & 32) != 0)
                ent.movetype = GameDefines.MOVETYPE_STOP;
            else
                ent.movetype = GameDefines.MOVETYPE_PUSH;

            // set the axis of rotation
            Math3D.VectorClear(ent.movedir);
            if ((ent.spawnflags & 4) != 0)
                ent.movedir[2] = 1.0f;
            else if ((ent.spawnflags & 8) != 0)
                ent.movedir[0] = 1.0f;
            else
                // Z_AXIS
                ent.movedir[1] = 1.0f;

            // check for reverse rotation
            if ((ent.spawnflags & 2) != 0)
                Math3D.VectorNegate(ent.movedir, ent.movedir);

            if (0 == ent.speed)
                ent.speed = 100;
            if (0 == ent.dmg)
                ent.dmg = 2;

            //		ent.moveinfo.sound_middle = "doors/hydro1.wav";

            ent.use = rotating_use;
            if (ent.dmg != 0)
                ent.blocked = rotating_blocked;

            if ((ent.spawnflags & 1) != 0)
                ent.use.use(ent, null, null, gameExports);

            if ((ent.spawnflags & 64) != 0)
                ent.s.effects |= Defines.EF_ANIM_ALL;
            if ((ent.spawnflags & 128) != 0)
                ent.s.effects |= Defines.EF_ANIM_ALLFAST;

            gameExports.gameImports.setmodel(ent, ent.model);
            gameExports.gameImports.linkentity(ent);
            return true;
        }
    };

    public static EntThinkAdapter Think_CalcMoveSpeed = new EntThinkAdapter() {
        public String getID() { return "think_calc_movespeed";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            SubgameEntity ent;
            float min;
            float time;
            float newspeed;
            float ratio;
            float dist;

            if ((self.flags & GameDefines.FL_TEAMSLAVE) != 0)
                return true; // only the team master does this

            // find the smallest distance any member of the team will be moving
            min = Math.abs(self.moveinfo.distance);
            for (ent = self.teamchain; ent != null; ent = ent.teamchain) {
                dist = Math.abs(ent.moveinfo.distance);
                if (dist < min)
                    min = dist;
            }

            time = min / self.moveinfo.speed;

            // adjust speeds so they will all complete at the same time
            for (ent = self; ent != null; ent = ent.teamchain) {
                newspeed = Math.abs(ent.moveinfo.distance) / time;
                ratio = newspeed / ent.moveinfo.speed;
                if (ent.moveinfo.accel == ent.moveinfo.speed)
                    ent.moveinfo.accel = newspeed;
                else
                    ent.moveinfo.accel *= ratio;
                if (ent.moveinfo.decel == ent.moveinfo.speed)
                    ent.moveinfo.decel = newspeed;
                else
                    ent.moveinfo.decel *= ratio;
                ent.moveinfo.speed = newspeed;
            }
            return true;
        }
    };


    private final static int TRAIN_START_ON = 1;

    private final static int TRAIN_TOGGLE = 2;

    private final static int TRAIN_BLOCK_STOPS = 4;

    /*
     * QUAKED func_train (0 .5 .8) ? START_ON TOGGLE BLOCK_STOPS Trains are
     * moving platforms that players can ride. The targets origin specifies the
     * min point of the train at each corner. The train spawns at the first
     * target it is pointing at. If the train is the target of a button or
     * trigger, it will not begin moving until activated. speed default 100 dmg
     * default 2 noise looping sound to play when the train is in motion
     *  
     */

    private static EntBlockedAdapter train_blocked = new EntBlockedAdapter() {
        public String getID() { return "train_blocked";}
        public void blocked(SubgameEntity self, SubgameEntity obstacle, GameExportsImpl gameExports) {
            if (0 == (obstacle.svflags & Defines.SVF_MONSTER)
                    && (null == obstacle.getClient())) {
                // give it a chance to go away on it's own terms (like gibs)
                GameCombat.T_Damage(obstacle, self, self, Globals.vec3_origin,
                        obstacle.s.origin, Globals.vec3_origin, 100000, 1, 0,
                        GameDefines.MOD_CRUSH, gameExports);
                // if it's still there, nuke it
                if (obstacle != null)
                    GameMisc.BecomeExplosion1(obstacle, gameExports);
                return;
            }

            if (gameExports.level.time < self.touch_debounce_time)
                return;

            if (self.dmg == 0)
                return;
            self.touch_debounce_time = gameExports.level.time + 0.5f;
            GameCombat.T_Damage(obstacle, self, self, Globals.vec3_origin,
                    obstacle.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
                    GameDefines.MOD_CRUSH, gameExports);
        }
    };

    private static EntThinkAdapter train_wait = new EntThinkAdapter() {
        public String getID() { return "train_wait";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            if (self.target_ent.pathtarget != null) {
                String savetarget;
                SubgameEntity ent;

                ent = self.target_ent;
                savetarget = ent.target;
                ent.target = ent.pathtarget;
                GameUtil.G_UseTargets(ent, self.activator, gameExports);
                ent.target = savetarget;

                // make sure we didn't get killed by a killtarget
                if (!self.inuse)
                    return true;
            }

            if (self.moveinfo.wait != 0) {
                if (self.moveinfo.wait > 0) {
                    self.think.nextTime = gameExports.level.time + self.moveinfo.wait;
                    self.think.action = train_next;
                } else if (0 != (self.spawnflags & TRAIN_TOGGLE)) // && wait < 0
                {
                    train_next.think(self, gameExports);
                    self.spawnflags &= ~TRAIN_START_ON;
                    Math3D.VectorClear(self.velocity);
                    self.think.nextTime = 0;
                }

                if (0 == (self.flags & GameDefines.FL_TEAMSLAVE)) {
                    if (self.moveinfo.sound_end != 0)
                        gameExports.gameImports.sound(self, Defines.CHAN_NO_PHS_ADD + Defines.CHAN_VOICE, self.moveinfo.sound_end, 1, Defines.ATTN_STATIC, 0);
                    self.s.sound = 0;
                }
            } else {
                train_next.think(self, gameExports);
            }
            return true;
        }
    };

    private static EntThinkAdapter train_next = new EntThinkAdapter() {
        public String getID() { return "train_next";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            SubgameEntity ent = null;
            float[] dest = { 0, 0, 0 };
            boolean first;

            first = true;

            boolean dogoto = true;
            while (dogoto) {
                if (null == self.target) {
                    //			gi.dprintf ("train_next: no next target\n");
                    return true;
                }

                ent = GameBase.G_PickTarget(self.target, gameExports);
                if (null == ent) {
                    gameExports.gameImports.dprintf("train_next: bad target " + self.target
                            + "\n");
                    return true;
                }

                self.target = ent.target;
                dogoto = false;
                // check for a teleport path_corner
                if ((ent.spawnflags & 1) != 0) {
                    if (!first) {
                        gameExports.gameImports
                                .dprintf("connected teleport path_corners, see "
                                        + ent.classname
                                        + " at "
                                        + Lib.vtos(ent.s.origin) + "\n");
                        return true;
                    }
                    first = false;
                    Math3D.VectorSubtract(ent.s.origin, self.mins,
                            self.s.origin);
                    Math3D.VectorCopy(self.s.origin, self.s.old_origin);
                    self.s.event = Defines.EV_OTHER_TELEPORT;
                    gameExports.gameImports.linkentity(self);
                    dogoto = true;
                }
            }
            self.moveinfo.wait = ent.wait;
            self.target_ent = ent;

            if (0 == (self.flags & GameDefines.FL_TEAMSLAVE)) {
                if (self.moveinfo.sound_start != 0)
                    gameExports.gameImports.sound(self, Defines.CHAN_NO_PHS_ADD
                            + Defines.CHAN_VOICE, self.moveinfo.sound_start, 1,
                            Defines.ATTN_STATIC, 0);
                self.s.sound = self.moveinfo.sound_middle;
            }

            Math3D.VectorSubtract(ent.s.origin, self.mins, dest);
            self.moveinfo.state = STATE_TOP;
            Math3D.VectorCopy(self.s.origin, self.moveinfo.start_origin);
            Math3D.VectorCopy(dest, self.moveinfo.end_origin);
            Move_Calc(self, dest, train_wait, gameExports);
            self.spawnflags |= TRAIN_START_ON;
            return true;
        }
    };

    static EntThinkAdapter func_train_find = new EntThinkAdapter() {
        public String getID() { return "func_train_find";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            SubgameEntity ent;

            if (null == self.target) {
                gameExports.gameImports.dprintf("train_find: no target\n");
                return true;
            }
            ent = GameBase.G_PickTarget(self.target, gameExports);
            if (null == ent) {
                gameExports.gameImports.dprintf("train_find: target " + self.target
                        + " not found\n");
                return true;
            }
            self.target = ent.target;

            Math3D.VectorSubtract(ent.s.origin, self.mins, self.s.origin);
            gameExports.gameImports.linkentity(self);

            // if not triggered, start immediately
            if (null == self.targetname)
                self.spawnflags |= TRAIN_START_ON;

            if ((self.spawnflags & TRAIN_START_ON) != 0) {
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
                self.think.action = train_next;
                self.activator = self;
            }
            return true;
        }
    };

    static EntUseAdapter train_use = new EntUseAdapter() {
        public String getID() { return "train_use";}
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            self.activator = activator;

            if ((self.spawnflags & TRAIN_START_ON) != 0) {
                if (0 == (self.spawnflags & TRAIN_TOGGLE))
                    return;
                self.spawnflags &= ~TRAIN_START_ON;
                Math3D.VectorClear(self.velocity);
                self.think.nextTime = 0;
            } else {
                if (self.target_ent != null)
                    train_resume(self, gameExports);
                else
                    train_next.think(self, gameExports);
            }
        }
    };

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
            train_resume(self.movetarget, gameExports);
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
