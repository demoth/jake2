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

// Created on 25.01.2004 by RST.
// $Id: PMove.java,v 1.5 2004-09-22 19:22:09 salomo Exp $
package jake2.qcommon;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.Math3D;

public class PMove {

    // all of the locals will be zeroed before each
    // pmove, just to make damn sure we don't have
    // any differences when running on client or server

    public static class pml_t {
        public float[] origin = { 0, 0, 0 }; // full float precision

        public float[] velocity = { 0, 0, 0 }; // full float precision

        public float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 }, up = { 0, 0,
                0 };

        public float frametime;

        public csurface_t groundsurface;

        public cplane_t groundplane = new cplane_t();

        public int groundcontents;

        public float[] previous_origin = { 0, 0, 0 };

        public boolean ladder;
    }

    public static pmove_t pm;

    public static PMove.pml_t pml = new PMove.pml_t();

    // movement parameters
    public static float pm_stopspeed = 100;

    public static float pm_maxspeed = 300;

    public static float pm_duckspeed = 100;

    public static float pm_accelerate = 10;

    public static float pm_airaccelerate = 0;

    public static float pm_wateraccelerate = 10;

    public static float pm_friction = 6;

    public static float pm_waterfriction = 1;

    public static float pm_waterspeed = 400;

    /*
     * ================ PM_SnapPosition
     * 
     * On exit, the origin will have a value that is pre-quantized to the 0.125
     * precision of the network channel and in a valid position.
     * ================
     */
    // try all single bits first
    public static int jitterbits[] = { 0, 4, 1, 2, 3, 5, 6, 7 };

    /*
     * ================ PM_InitialSnapPosition
     * 
     * ================
     */
    public static int offset[] = { 0, -1, 1 };

    /*
     * 
     * walking up a step should kill some velocity
     *  
     */

    /*
     * ================== PM_ClipVelocity
     * 
     * Slide off of the impacting object returns the blocked flags (1 = floor, 2 =
     * step / wall) ==================
     */

    public static void PM_ClipVelocity(float[] in, float[] normal, float[] out,
            float overbounce) {
        float backoff;
        float change;
        int i;

        backoff = Math3D.DotProduct(in, normal) * overbounce;

        for (i = 0; i < 3; i++) {
            change = normal[i] * backoff;
            out[i] = in[i] - change;
            if (out[i] > -Defines.MOVE_STOP_EPSILON
                    && out[i] < Defines.MOVE_STOP_EPSILON)
                out[i] = 0;
        }
    }

    public static void PM_StepSlideMove_() {
        int bumpcount, numbumps;
        float[] dir = { 0, 0, 0 };
        float d;
        int numplanes;
        float[] planes[] = new float[GameBase.MAX_CLIP_PLANES][3];
        float[] primal_velocity = { 0, 0, 0 };
        int i, j;
        trace_t trace;
        float[] end = { 0, 0, 0 };
        float time_left;

        numbumps = 4;

        Math3D.VectorCopy(PMove.pml.velocity, primal_velocity);
        numplanes = 0;

        time_left = PMove.pml.frametime;

        for (bumpcount = 0; bumpcount < numbumps; bumpcount++) {
            for (i = 0; i < 3; i++)
                end[i] = PMove.pml.origin[i] + time_left
                        * PMove.pml.velocity[i];

            trace = PMove.pm.trace.trace(PMove.pml.origin, PMove.pm.mins,
                    PMove.pm.maxs, end);

            if (trace.allsolid) { // entity is trapped in another solid
                PMove.pml.velocity[2] = 0; // don't build up falling damage
                return;
            }

            if (trace.fraction > 0) { // actually covered some distance
                Math3D.VectorCopy(trace.endpos, PMove.pml.origin);
                numplanes = 0;
            }

            if (trace.fraction == 1)
                break; // moved the entire distance

            // save entity for contact
            if (PMove.pm.numtouch < Defines.MAXTOUCH && trace.ent != null) {
                //rst: just for debugging touches.
                //if (trace.ent.index != -1 && trace.ent.index != 0)
                //Com.p("touch: " + trace.ent.classname + " (" +
                // trace.ent.index + ")" );

                PMove.pm.touchents[PMove.pm.numtouch] = trace.ent;
                PMove.pm.numtouch++;
            }

            time_left -= time_left * trace.fraction;

            // slide along this plane
            if (numplanes >= GameBase.MAX_CLIP_PLANES) { // this shouldn't
                                                         // really happen
                Math3D.VectorCopy(Globals.vec3_origin, PMove.pml.velocity);
                break;
            }

            Math3D.VectorCopy(trace.plane.normal, planes[numplanes]);
            numplanes++;

            //
            // modify original_velocity so it parallels all of the clip planes
            //

            for (i = 0; i < numplanes; i++) {
                PMove.PM_ClipVelocity(PMove.pml.velocity, planes[i],
                        PMove.pml.velocity, 1.01f);
                for (j = 0; j < numplanes; j++)
                    if (j != i) {
                        if (Math3D.DotProduct(PMove.pml.velocity, planes[j]) < 0)
                            break; // not ok
                    }
                if (j == numplanes)
                    break;
            }

            if (i != numplanes) { // go along this plane
            } else { // go along the crease
                if (numplanes != 2) {
                    //				Con_Printf ("clip velocity, numplanes ==
                    // %i\n",numplanes);
                    Math3D.VectorCopy(Globals.vec3_origin, PMove.pml.velocity);
                    break;
                }
                Math3D.CrossProduct(planes[0], planes[1], dir);
                d = Math3D.DotProduct(dir, PMove.pml.velocity);
                Math3D.VectorScale(dir, d, PMove.pml.velocity);
            }

            //
            // if velocity is against the original velocity, stop dead
            // to avoid tiny occilations in sloping corners
            //
            if (Math3D.DotProduct(PMove.pml.velocity, primal_velocity) <= 0) {
                Math3D.VectorCopy(Globals.vec3_origin, PMove.pml.velocity);
                break;
            }
        }

        if (PMove.pm.s.pm_time != 0) {
            Math3D.VectorCopy(primal_velocity, PMove.pml.velocity);
        }
    }

    /*
     * ================== PM_StepSlideMove
     * 
     * ==================
     */
    public static void PM_StepSlideMove() {
        float[] start_o = { 0, 0, 0 }, start_v = { 0, 0, 0 };
        float[] down_o = { 0, 0, 0 }, down_v = { 0, 0, 0 };
        trace_t trace;
        float down_dist, up_dist;
        //	float [] delta;
        float[] up = { 0, 0, 0 }, down = { 0, 0, 0 };

        Math3D.VectorCopy(PMove.pml.origin, start_o);
        Math3D.VectorCopy(PMove.pml.velocity, start_v);

        PM_StepSlideMove_();

        Math3D.VectorCopy(PMove.pml.origin, down_o);
        Math3D.VectorCopy(PMove.pml.velocity, down_v);

        Math3D.VectorCopy(start_o, up);
        up[2] += Defines.STEPSIZE;

        trace = PMove.pm.trace.trace(up, PMove.pm.mins, PMove.pm.maxs, up);
        if (trace.allsolid)
            return; // can't step up

        // try sliding above
        Math3D.VectorCopy(up, PMove.pml.origin);
        Math3D.VectorCopy(start_v, PMove.pml.velocity);

        PM_StepSlideMove_();

        // push down the final amount
        Math3D.VectorCopy(PMove.pml.origin, down);
        down[2] -= Defines.STEPSIZE;
        trace = PMove.pm.trace.trace(PMove.pml.origin, PMove.pm.mins,
                PMove.pm.maxs, down);
        if (!trace.allsolid) {
            Math3D.VectorCopy(trace.endpos, PMove.pml.origin);
        }

        Math3D.VectorCopy(PMove.pml.origin, up);

        // decide which one went farther
        down_dist = (down_o[0] - start_o[0]) * (down_o[0] - start_o[0])
                + (down_o[1] - start_o[1]) * (down_o[1] - start_o[1]);
        up_dist = (up[0] - start_o[0]) * (up[0] - start_o[0])
                + (up[1] - start_o[1]) * (up[1] - start_o[1]);

        if (down_dist > up_dist
                || trace.plane.normal[2] < Defines.MIN_STEP_NORMAL) {
            Math3D.VectorCopy(down_o, PMove.pml.origin);
            Math3D.VectorCopy(down_v, PMove.pml.velocity);
            return;
        }
        //!! Special case
        // if we were walking along a plane, then we need to copy the Z over
        PMove.pml.velocity[2] = down_v[2];
    }

    /*
     * ================== PM_Friction
     * 
     * Handles both ground friction and water friction ==================
     */
    public static void PM_Friction() {
        float vel[];
        float speed, newspeed, control;
        float friction;
        float drop;

        vel = PMove.pml.velocity;

        speed = (float) (Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2]
                * vel[2]));
        if (speed < 1) {
            vel[0] = 0;
            vel[1] = 0;
            return;
        }

        drop = 0;

        // apply ground friction
        if ((PMove.pm.groundentity != null && PMove.pml.groundsurface != null && 0 == (PMove.pml.groundsurface.flags & Defines.SURF_SLICK))
                || (PMove.pml.ladder)) {
            friction = PMove.pm_friction;
            control = speed < PMove.pm_stopspeed ? PMove.pm_stopspeed : speed;
            drop += control * friction * PMove.pml.frametime;
        }

        // apply water friction
        if (PMove.pm.waterlevel != 0 && !PMove.pml.ladder)
            drop += speed * PMove.pm_waterfriction * PMove.pm.waterlevel
                    * PMove.pml.frametime;

        // scale the velocity
        newspeed = speed - drop;
        if (newspeed < 0) {
            newspeed = 0;
        }
        newspeed /= speed;

        vel[0] = vel[0] * newspeed;
        vel[1] = vel[1] * newspeed;
        vel[2] = vel[2] * newspeed;
    }

    /*
     * ============== PM_Accelerate
     * 
     * Handles user intended acceleration ==============
     */
    public static void PM_Accelerate(float[] wishdir, float wishspeed,
            float accel) {
        int i;
        float addspeed, accelspeed, currentspeed;

        currentspeed = Math3D.DotProduct(PMove.pml.velocity, wishdir);
        addspeed = wishspeed - currentspeed;
        if (addspeed <= 0)
            return;
        accelspeed = accel * PMove.pml.frametime * wishspeed;
        if (accelspeed > addspeed)
            accelspeed = addspeed;

        for (i = 0; i < 3; i++)
            PMove.pml.velocity[i] += accelspeed * wishdir[i];
    }

    public static void PM_AirAccelerate(float[] wishdir, float wishspeed,
            float accel) {
        int i;
        float addspeed, accelspeed, currentspeed, wishspd = wishspeed;

        if (wishspd > 30)
            wishspd = 30;
        currentspeed = Math3D.DotProduct(PMove.pml.velocity, wishdir);
        addspeed = wishspd - currentspeed;
        if (addspeed <= 0)
            return;
        accelspeed = accel * wishspeed * PMove.pml.frametime;
        if (accelspeed > addspeed)
            accelspeed = addspeed;

        for (i = 0; i < 3; i++)
            PMove.pml.velocity[i] += accelspeed * wishdir[i];
    }

    /*
     * ============= PM_AddCurrents =============
     */
    public static void PM_AddCurrents(float[] wishvel) {
        float[] v = { 0, 0, 0 };
        float s;

        //
        // account for ladders
        //

        if (PMove.pml.ladder && Math.abs(PMove.pml.velocity[2]) <= 200) {
            if ((PMove.pm.viewangles[Defines.PITCH] <= -15)
                    && (PMove.pm.cmd.forwardmove > 0))
                wishvel[2] = 200;
            else if ((PMove.pm.viewangles[Defines.PITCH] >= 15)
                    && (PMove.pm.cmd.forwardmove > 0))
                wishvel[2] = -200;
            else if (PMove.pm.cmd.upmove > 0)
                wishvel[2] = 200;
            else if (PMove.pm.cmd.upmove < 0)
                wishvel[2] = -200;
            else
                wishvel[2] = 0;

            // limit horizontal speed when on a ladder
            if (wishvel[0] < -25)
                wishvel[0] = -25;
            else if (wishvel[0] > 25)
                wishvel[0] = 25;

            if (wishvel[1] < -25)
                wishvel[1] = -25;
            else if (wishvel[1] > 25)
                wishvel[1] = 25;
        }

        //
        // add water currents
        //

        if ((PMove.pm.watertype & Defines.MASK_CURRENT) != 0) {
            Math3D.VectorClear(v);

            if ((PMove.pm.watertype & Defines.CONTENTS_CURRENT_0) != 0)
                v[0] += 1;
            if ((PMove.pm.watertype & Defines.CONTENTS_CURRENT_90) != 0)
                v[1] += 1;
            if ((PMove.pm.watertype & Defines.CONTENTS_CURRENT_180) != 0)
                v[0] -= 1;
            if ((PMove.pm.watertype & Defines.CONTENTS_CURRENT_270) != 0)
                v[1] -= 1;
            if ((PMove.pm.watertype & Defines.CONTENTS_CURRENT_UP) != 0)
                v[2] += 1;
            if ((PMove.pm.watertype & Defines.CONTENTS_CURRENT_DOWN) != 0)
                v[2] -= 1;

            s = PMove.pm_waterspeed;
            if ((PMove.pm.waterlevel == 1) && (PMove.pm.groundentity != null))
                s /= 2;

            Math3D.VectorMA(wishvel, s, v, wishvel);
        }

        //
        // add conveyor belt velocities
        //

        if (PMove.pm.groundentity != null) {
            Math3D.VectorClear(v);

            if ((PMove.pml.groundcontents & Defines.CONTENTS_CURRENT_0) != 0)
                v[0] += 1;
            if ((PMove.pml.groundcontents & Defines.CONTENTS_CURRENT_90) != 0)
                v[1] += 1;
            if ((PMove.pml.groundcontents & Defines.CONTENTS_CURRENT_180) != 0)
                v[0] -= 1;
            if ((PMove.pml.groundcontents & Defines.CONTENTS_CURRENT_270) != 0)
                v[1] -= 1;
            if ((PMove.pml.groundcontents & Defines.CONTENTS_CURRENT_UP) != 0)
                v[2] += 1;
            if ((PMove.pml.groundcontents & Defines.CONTENTS_CURRENT_DOWN) != 0)
                v[2] -= 1;

            Math3D.VectorMA(wishvel, 100 /* pm.groundentity.speed */
            , v, wishvel);
        }
    }

    /*
     * =================== PM_WaterMove
     * 
     * ===================
     */
    public static void PM_WaterMove() {
        int i;
        float[] wishvel = { 0, 0, 0 };
        float wishspeed;
        float[] wishdir = { 0, 0, 0 };

        //
        // user intentions
        //
        for (i = 0; i < 3; i++)
            wishvel[i] = PMove.pml.forward[i] * PMove.pm.cmd.forwardmove
                    + PMove.pml.right[i] * PMove.pm.cmd.sidemove;

        if (0 == PMove.pm.cmd.forwardmove && 0 == PMove.pm.cmd.sidemove
                && 0 == PMove.pm.cmd.upmove)
            wishvel[2] -= 60; // drift towards bottom
        else
            wishvel[2] += PMove.pm.cmd.upmove;

        PM_AddCurrents(wishvel);

        Math3D.VectorCopy(wishvel, wishdir);
        wishspeed = Math3D.VectorNormalize(wishdir);

        if (wishspeed > PMove.pm_maxspeed) {
            Math3D.VectorScale(wishvel, PMove.pm_maxspeed / wishspeed, wishvel);
            wishspeed = PMove.pm_maxspeed;
        }
        wishspeed *= 0.5;

        PM_Accelerate(wishdir, wishspeed, PMove.pm_wateraccelerate);

        PM_StepSlideMove();
    }

    /*
     * =================== PM_AirMove
     * 
     * ===================
     */
    public static void PM_AirMove() {
        int i;
        float[] wishvel = { 0, 0, 0 };
        float fmove, smove;
        float[] wishdir = { 0, 0, 0 };
        float wishspeed;
        float maxspeed;

        fmove = PMove.pm.cmd.forwardmove;
        smove = PMove.pm.cmd.sidemove;

        for (i = 0; i < 2; i++)
            wishvel[i] = PMove.pml.forward[i] * fmove + PMove.pml.right[i]
                    * smove;
        wishvel[2] = 0;

        PM_AddCurrents(wishvel);

        Math3D.VectorCopy(wishvel, wishdir);
        wishspeed = Math3D.VectorNormalize(wishdir);

        //
        // clamp to server defined max speed
        //
        maxspeed = (PMove.pm.s.pm_flags & pmove_t.PMF_DUCKED) != 0 ? PMove.pm_duckspeed
                : PMove.pm_maxspeed;

        if (wishspeed > maxspeed) {
            Math3D.VectorScale(wishvel, maxspeed / wishspeed, wishvel);
            wishspeed = maxspeed;
        }

        if (PMove.pml.ladder) {
            PM_Accelerate(wishdir, wishspeed, PMove.pm_accelerate);
            if (0 == wishvel[2]) {
                if (PMove.pml.velocity[2] > 0) {
                    PMove.pml.velocity[2] -= PMove.pm.s.gravity
                            * PMove.pml.frametime;
                    if (PMove.pml.velocity[2] < 0)
                        PMove.pml.velocity[2] = 0;
                } else {
                    PMove.pml.velocity[2] += PMove.pm.s.gravity
                            * PMove.pml.frametime;
                    if (PMove.pml.velocity[2] > 0)
                        PMove.pml.velocity[2] = 0;
                }
            }
            PM_StepSlideMove();
        } else if (PMove.pm.groundentity != null) { // walking on ground
            PMove.pml.velocity[2] = 0; //!!! this is before the accel
            PM_Accelerate(wishdir, wishspeed, PMove.pm_accelerate);

            // PGM -- fix for negative trigger_gravity fields
            //		pml.velocity[2] = 0;
            if (PMove.pm.s.gravity > 0)
                PMove.pml.velocity[2] = 0;
            else
                PMove.pml.velocity[2] -= PMove.pm.s.gravity
                        * PMove.pml.frametime;
            // PGM

            if (0 == PMove.pml.velocity[0] && 0 == PMove.pml.velocity[1])
                return;
            PM_StepSlideMove();
        } else { // not on ground, so little effect on velocity
            if (PMove.pm_airaccelerate != 0)
                PM_AirAccelerate(wishdir, wishspeed, PMove.pm_accelerate);
            else
                PM_Accelerate(wishdir, wishspeed, 1);
            // add gravity
            PMove.pml.velocity[2] -= PMove.pm.s.gravity * PMove.pml.frametime;
            PM_StepSlideMove();
        }
    }

    /*
     * ============= PM_CatagorizePosition =============
     */
    public static void PM_CatagorizePosition() {
        float[] point = { 0, 0, 0 };
        int cont;
        trace_t trace;
        int sample1;
        int sample2;

        // if the player hull point one unit down is solid, the player
        // is on ground

        // see if standing on something solid
        point[0] = PMove.pml.origin[0];
        point[1] = PMove.pml.origin[1];
        point[2] = PMove.pml.origin[2] - 0.25f;
        if (PMove.pml.velocity[2] > 180) //!!ZOID changed from 100 to 180 (ramp
                                         // accel)
        {
            PMove.pm.s.pm_flags &= ~pmove_t.PMF_ON_GROUND;
            PMove.pm.groundentity = null;
        } else {
            trace = PMove.pm.trace.trace(PMove.pml.origin, PMove.pm.mins,
                    PMove.pm.maxs, point);
            PMove.pml.groundplane = trace.plane;
            PMove.pml.groundsurface = trace.surface;
            PMove.pml.groundcontents = trace.contents;

            if (null == trace.ent
                    || (trace.plane.normal[2] < 0.7 && !trace.startsolid)) {
                PMove.pm.groundentity = null;
                PMove.pm.s.pm_flags &= ~pmove_t.PMF_ON_GROUND;
            } else {
                PMove.pm.groundentity = trace.ent;
                // hitting solid ground will end a waterjump
                if ((PMove.pm.s.pm_flags & pmove_t.PMF_TIME_WATERJUMP) != 0) {
                    PMove.pm.s.pm_flags &= ~(pmove_t.PMF_TIME_WATERJUMP
                            | pmove_t.PMF_TIME_LAND | pmove_t.PMF_TIME_TELEPORT);
                    PMove.pm.s.pm_time = 0;
                }

                if (0 == (PMove.pm.s.pm_flags & pmove_t.PMF_ON_GROUND)) { // just
                                                                          // hit
                                                                          // the
                                                                          // ground
                    PMove.pm.s.pm_flags |= pmove_t.PMF_ON_GROUND;
                    // don't do landing time if we were just going down a slope
                    if (PMove.pml.velocity[2] < -200) {
                        PMove.pm.s.pm_flags |= pmove_t.PMF_TIME_LAND;
                        // don't allow another jump for a little while
                        if (PMove.pml.velocity[2] < -400)
                            PMove.pm.s.pm_time = 25;
                        else
                            PMove.pm.s.pm_time = 18;
                    }
                }
            }

            if (PMove.pm.numtouch < Defines.MAXTOUCH && trace.ent != null) {
                PMove.pm.touchents[PMove.pm.numtouch] = trace.ent;
                PMove.pm.numtouch++;
            }
        }

        //
        // get waterlevel, accounting for ducking
        //
        PMove.pm.waterlevel = 0;
        PMove.pm.watertype = 0;

        sample2 = (int) (PMove.pm.viewheight - PMove.pm.mins[2]);
        sample1 = sample2 / 2;

        point[2] = PMove.pml.origin[2] + PMove.pm.mins[2] + 1;
        cont = PMove.pm.pointcontents.pointcontents(point);

        if ((cont & Defines.MASK_WATER) != 0) {
            PMove.pm.watertype = cont;
            PMove.pm.waterlevel = 1;
            point[2] = PMove.pml.origin[2] + PMove.pm.mins[2] + sample1;
            cont = PMove.pm.pointcontents.pointcontents(point);
            if ((cont & Defines.MASK_WATER) != 0) {
                PMove.pm.waterlevel = 2;
                point[2] = PMove.pml.origin[2] + PMove.pm.mins[2] + sample2;
                cont = PMove.pm.pointcontents.pointcontents(point);
                if ((cont & Defines.MASK_WATER) != 0)
                    PMove.pm.waterlevel = 3;
            }
        }

    }

    /*
     * ============= PM_CheckJump =============
     */
    public static void PM_CheckJump() {
        if ((PMove.pm.s.pm_flags & pmove_t.PMF_TIME_LAND) != 0) {
            // hasn't been long enough since landing to jump again
            return;
        }

        if (PMove.pm.cmd.upmove < 10) { // not holding jump
            PMove.pm.s.pm_flags &= ~pmove_t.PMF_JUMP_HELD;
            return;
        }

        // must wait for jump to be released
        if ((PMove.pm.s.pm_flags & pmove_t.PMF_JUMP_HELD) != 0)
            return;

        if (PMove.pm.s.pm_type == Defines.PM_DEAD)
            return;

        if (PMove.pm.waterlevel >= 2) { // swimming, not jumping
            PMove.pm.groundentity = null;

            if (PMove.pml.velocity[2] <= -300)
                return;

            if (PMove.pm.watertype == Defines.CONTENTS_WATER)
                PMove.pml.velocity[2] = 100;
            else if (PMove.pm.watertype == Defines.CONTENTS_SLIME)
                PMove.pml.velocity[2] = 80;
            else
                PMove.pml.velocity[2] = 50;
            return;
        }

        if (PMove.pm.groundentity == null)
            return; // in air, so no effect

        PMove.pm.s.pm_flags |= pmove_t.PMF_JUMP_HELD;

        PMove.pm.groundentity = null;
        PMove.pml.velocity[2] += 270;
        if (PMove.pml.velocity[2] < 270)
            PMove.pml.velocity[2] = 270;
    }

    /*
     * ============= PM_CheckSpecialMovement =============
     */
    public static void PM_CheckSpecialMovement() {
        float[] spot = { 0, 0, 0 };
        int cont;
        float[] flatforward = { 0, 0, 0 };
        trace_t trace;

        if (PMove.pm.s.pm_time != 0)
            return;

        PMove.pml.ladder = false;

        // check for ladder
        flatforward[0] = PMove.pml.forward[0];
        flatforward[1] = PMove.pml.forward[1];
        flatforward[2] = 0;
        Math3D.VectorNormalize(flatforward);

        Math3D.VectorMA(PMove.pml.origin, 1, flatforward, spot);
        trace = PMove.pm.trace.trace(PMove.pml.origin, PMove.pm.mins,
                PMove.pm.maxs, spot);
        if ((trace.fraction < 1)
                && (trace.contents & Defines.CONTENTS_LADDER) != 0)
            PMove.pml.ladder = true;

        // check for water jump
        if (PMove.pm.waterlevel != 2)
            return;

        Math3D.VectorMA(PMove.pml.origin, 30, flatforward, spot);
        spot[2] += 4;
        cont = PMove.pm.pointcontents.pointcontents(spot);
        if (0 == (cont & Defines.CONTENTS_SOLID))
            return;

        spot[2] += 16;
        cont = PMove.pm.pointcontents.pointcontents(spot);
        if (cont != 0)
            return;
        // jump out of water
        Math3D.VectorScale(flatforward, 50, PMove.pml.velocity);
        PMove.pml.velocity[2] = 350;

        PMove.pm.s.pm_flags |= pmove_t.PMF_TIME_WATERJUMP;
        PMove.pm.s.pm_time = -1; // was 255
    }

    /*
     * =============== PM_FlyMove ===============
     */
    public static void PM_FlyMove(boolean doclip) {
        float speed, drop, friction, control, newspeed;
        float currentspeed, addspeed, accelspeed;
        int i;
        float[] wishvel = { 0, 0, 0 };
        float fmove, smove;
        float[] wishdir = { 0, 0, 0 };
        float wishspeed;
        float[] end = { 0, 0, 0 };
        trace_t trace;

        PMove.pm.viewheight = 22;

        // friction

        speed = Math3D.VectorLength(PMove.pml.velocity);
        if (speed < 1) {
            Math3D.VectorCopy(Globals.vec3_origin, PMove.pml.velocity);
        } else {
            drop = 0;

            friction = PMove.pm_friction * 1.5f; // extra friction
            control = speed < PMove.pm_stopspeed ? PMove.pm_stopspeed : speed;
            drop += control * friction * PMove.pml.frametime;

            // scale the velocity
            newspeed = speed - drop;
            if (newspeed < 0)
                newspeed = 0;
            newspeed /= speed;

            Math3D
                    .VectorScale(PMove.pml.velocity, newspeed,
                            PMove.pml.velocity);
        }

        // accelerate
        fmove = PMove.pm.cmd.forwardmove;
        smove = PMove.pm.cmd.sidemove;

        Math3D.VectorNormalize(PMove.pml.forward);
        Math3D.VectorNormalize(PMove.pml.right);

        for (i = 0; i < 3; i++)
            wishvel[i] = PMove.pml.forward[i] * fmove + PMove.pml.right[i]
                    * smove;
        wishvel[2] += PMove.pm.cmd.upmove;

        Math3D.VectorCopy(wishvel, wishdir);
        wishspeed = Math3D.VectorNormalize(wishdir);

        //
        // clamp to server defined max speed
        //
        if (wishspeed > PMove.pm_maxspeed) {
            Math3D.VectorScale(wishvel, PMove.pm_maxspeed / wishspeed, wishvel);
            wishspeed = PMove.pm_maxspeed;
        }

        currentspeed = Math3D.DotProduct(PMove.pml.velocity, wishdir);
        addspeed = wishspeed - currentspeed;
        if (addspeed <= 0)
            return;
        accelspeed = PMove.pm_accelerate * PMove.pml.frametime * wishspeed;
        if (accelspeed > addspeed)
            accelspeed = addspeed;

        for (i = 0; i < 3; i++)
            PMove.pml.velocity[i] += accelspeed * wishdir[i];

        if (doclip) {
            for (i = 0; i < 3; i++)
                end[i] = PMove.pml.origin[i] + PMove.pml.frametime
                        * PMove.pml.velocity[i];

            trace = PMove.pm.trace.trace(PMove.pml.origin, PMove.pm.mins,
                    PMove.pm.maxs, end);

            Math3D.VectorCopy(trace.endpos, PMove.pml.origin);
        } else {
            // move
            Math3D.VectorMA(PMove.pml.origin, PMove.pml.frametime,
                    PMove.pml.velocity, PMove.pml.origin);
        }
    }

    /*
     * ============== PM_CheckDuck
     * 
     * Sets mins, maxs, and pm.viewheight ==============
     */
    public static void PM_CheckDuck() {
        trace_t trace;

        PMove.pm.mins[0] = -16;
        PMove.pm.mins[1] = -16;

        PMove.pm.maxs[0] = 16;
        PMove.pm.maxs[1] = 16;

        if (PMove.pm.s.pm_type == Defines.PM_GIB) {
            PMove.pm.mins[2] = 0;
            PMove.pm.maxs[2] = 16;
            PMove.pm.viewheight = 8;
            return;
        }

        PMove.pm.mins[2] = -24;

        if (PMove.pm.s.pm_type == Defines.PM_DEAD) {
            PMove.pm.s.pm_flags |= pmove_t.PMF_DUCKED;
        } else if (PMove.pm.cmd.upmove < 0
                && (PMove.pm.s.pm_flags & pmove_t.PMF_ON_GROUND) != 0) { // duck
            PMove.pm.s.pm_flags |= pmove_t.PMF_DUCKED;
        } else { // stand up if possible
            if ((PMove.pm.s.pm_flags & pmove_t.PMF_DUCKED) != 0) {
                // try to stand up
                PMove.pm.maxs[2] = 32;
                trace = PMove.pm.trace.trace(PMove.pml.origin, PMove.pm.mins,
                        PMove.pm.maxs, PMove.pml.origin);
                if (!trace.allsolid)
                    PMove.pm.s.pm_flags &= ~pmove_t.PMF_DUCKED;
            }
        }

        if ((PMove.pm.s.pm_flags & pmove_t.PMF_DUCKED) != 0) {
            PMove.pm.maxs[2] = 4;
            PMove.pm.viewheight = -2;
        } else {
            PMove.pm.maxs[2] = 32;
            PMove.pm.viewheight = 22;
        }
    }

    /*
     * ============== PM_DeadMove ==============
     */
    public static void PM_DeadMove() {
        float forward;

        if (null == PMove.pm.groundentity)
            return;

        // extra friction

        forward = Math3D.VectorLength(PMove.pml.velocity);
        forward -= 20;
        if (forward <= 0) {
            Math3D.VectorClear(PMove.pml.velocity);
        } else {
            Math3D.VectorNormalize(PMove.pml.velocity);
            Math3D.VectorScale(PMove.pml.velocity, forward, PMove.pml.velocity);
        }
    }

    public static boolean PM_GoodPosition() {
        trace_t trace;
        float[] origin = { 0, 0, 0 }, end = { 0, 0, 0 };
        int i;

        if (PMove.pm.s.pm_type == Defines.PM_SPECTATOR)
            return true;

        for (i = 0; i < 3; i++)
            origin[i] = end[i] = PMove.pm.s.origin[i] * 0.125f;
        trace = PMove.pm.trace.trace(origin, PMove.pm.mins, PMove.pm.maxs, end);

        return !trace.allsolid;
    }

    public static void PM_SnapPosition() {
        int sign[] = { 0, 0, 0 };
        int i, j, bits;
        short base[] = { 0, 0, 0 };

        // snap velocity to eigths
        for (i = 0; i < 3; i++)
            PMove.pm.s.velocity[i] = (short) (PMove.pml.velocity[i] * 8);

        for (i = 0; i < 3; i++) {
            if (PMove.pml.origin[i] >= 0)
                sign[i] = 1;
            else
                sign[i] = -1;
            PMove.pm.s.origin[i] = (short) (PMove.pml.origin[i] * 8);
            if (PMove.pm.s.origin[i] * 0.125 == PMove.pml.origin[i])
                sign[i] = 0;
        }
        Math3D.VectorCopy(PMove.pm.s.origin, base);

        // try all combinations
        for (j = 0; j < 8; j++) {
            bits = jitterbits[j];
            Math3D.VectorCopy(base, PMove.pm.s.origin);
            for (i = 0; i < 3; i++)
                if ((bits & (1 << i)) != 0)
                    PMove.pm.s.origin[i] += sign[i];

            if (PMove.PM_GoodPosition())
                return;
        }

        // go back to the last position
        Math3D.VectorCopy(PMove.pml.previous_origin, PMove.pm.s.origin);
        //	Com_DPrintf ("using previous_origin\n");
    }

    public static void PM_InitialSnapPosition() {
        int x, y, z;
        short base[] = { 0, 0, 0 };

        Math3D.VectorCopy(PMove.pm.s.origin, base);

        for (z = 0; z < 3; z++) {
            PMove.pm.s.origin[2] = (short) (base[2] + offset[z]);
            for (y = 0; y < 3; y++) {
                PMove.pm.s.origin[1] = (short) (base[1] + offset[y]);
                for (x = 0; x < 3; x++) {
                    PMove.pm.s.origin[0] = (short) (base[0] + offset[x]);
                    if (PMove.PM_GoodPosition()) {
                        PMove.pml.origin[0] = PMove.pm.s.origin[0] * 0.125f;
                        PMove.pml.origin[1] = PMove.pm.s.origin[1] * 0.125f;
                        PMove.pml.origin[2] = PMove.pm.s.origin[2] * 0.125f;
                        Math3D.VectorCopy(PMove.pm.s.origin,
                                PMove.pml.previous_origin);
                        return;
                    }
                }
            }
        }

        Com.DPrintf("Bad InitialSnapPosition\n");
    }

    /*
     * ================ PM_ClampAngles
     * 
     * ================
     */
    public static void PM_ClampAngles() {
        short temp;
        int i;

        if ((PMove.pm.s.pm_flags & pmove_t.PMF_TIME_TELEPORT) != 0) {
            PMove.pm.viewangles[Defines.YAW] = Math3D
                    .SHORT2ANGLE(PMove.pm.cmd.angles[Defines.YAW]
                            + PMove.pm.s.delta_angles[Defines.YAW]);
            PMove.pm.viewangles[Defines.PITCH] = 0;
            PMove.pm.viewangles[Defines.ROLL] = 0;
        } else {
            // circularly clamp the angles with deltas
            for (i = 0; i < 3; i++) {
                temp = (short) (PMove.pm.cmd.angles[i] + PMove.pm.s.delta_angles[i]);
                PMove.pm.viewangles[i] = Math3D.SHORT2ANGLE(temp);
            }

            // don't let the player look up or down more than 90 degrees
            if (PMove.pm.viewangles[Defines.PITCH] > 89
                    && PMove.pm.viewangles[Defines.PITCH] < 180)
                PMove.pm.viewangles[Defines.PITCH] = 89;
            else if (PMove.pm.viewangles[Defines.PITCH] < 271
                    && PMove.pm.viewangles[Defines.PITCH] >= 180)
                PMove.pm.viewangles[Defines.PITCH] = 271;
        }
        Math3D.AngleVectors(PMove.pm.viewangles, PMove.pml.forward,
                PMove.pml.right, PMove.pml.up);
    }

    /*
     * ================ Pmove
     * 
     * Can be called by either the server or the client ================
     */
    public static void Pmove(pmove_t pmove) {
        PMove.pm = pmove;

        // clear results
        PMove.pm.numtouch = 0;
        Math3D.VectorClear(PMove.pm.viewangles);
        PMove.pm.viewheight = 0;
        PMove.pm.groundentity = null;
        PMove.pm.watertype = 0;
        PMove.pm.waterlevel = 0;

        // clear all pmove local vars
        PMove.pml = new PMove.pml_t();

        // convert origin and velocity to float values
        PMove.pml.origin[0] = PMove.pm.s.origin[0] * 0.125f;
        PMove.pml.origin[1] = PMove.pm.s.origin[1] * 0.125f;
        PMove.pml.origin[2] = PMove.pm.s.origin[2] * 0.125f;

        PMove.pml.velocity[0] = PMove.pm.s.velocity[0] * 0.125f;
        PMove.pml.velocity[1] = PMove.pm.s.velocity[1] * 0.125f;
        PMove.pml.velocity[2] = PMove.pm.s.velocity[2] * 0.125f;

        // save old org in case we get stuck
        Math3D.VectorCopy(PMove.pm.s.origin, PMove.pml.previous_origin);

        PMove.pml.frametime = (PMove.pm.cmd.msec & 0xFF) * 0.001f;

        PM_ClampAngles();

        if (PMove.pm.s.pm_type == Defines.PM_SPECTATOR) {
            PMove.PM_FlyMove(false);
            PM_SnapPosition();
            return;
        }

        if (PMove.pm.s.pm_type >= Defines.PM_DEAD) {
            PMove.pm.cmd.forwardmove = 0;
            PMove.pm.cmd.sidemove = 0;
            PMove.pm.cmd.upmove = 0;
        }

        if (PMove.pm.s.pm_type == Defines.PM_FREEZE)
            return; // no movement at all

        // set mins, maxs, and viewheight
        PMove.PM_CheckDuck();

        if (PMove.pm.snapinitial)
            PM_InitialSnapPosition();

        // set groundentity, watertype, and waterlevel
        PMove.PM_CatagorizePosition();

        if (PMove.pm.s.pm_type == Defines.PM_DEAD)
            PMove.PM_DeadMove();

        PMove.PM_CheckSpecialMovement();

        // drop timing counter
        if (PMove.pm.s.pm_time != 0) {
            int msec;

            // TOD o bugfix cwei
            msec = PMove.pm.cmd.msec >>> 3;
            if (msec == 0)
                msec = 1;
            if (msec >= (PMove.pm.s.pm_time & 0xFF)) {
                PMove.pm.s.pm_flags &= ~(pmove_t.PMF_TIME_WATERJUMP
                        | pmove_t.PMF_TIME_LAND | pmove_t.PMF_TIME_TELEPORT);
                PMove.pm.s.pm_time = 0;
            } else
                PMove.pm.s.pm_time = (byte) ((PMove.pm.s.pm_time & 0xFF) - msec);
        }

        if ((PMove.pm.s.pm_flags & pmove_t.PMF_TIME_TELEPORT) != 0) { // teleport
                                                                      // pause
                                                                      // stays
                                                                      // exactly
                                                                      // in
                                                                      // place
        } else if ((PMove.pm.s.pm_flags & pmove_t.PMF_TIME_WATERJUMP) != 0) { // waterjump
                                                                              // has
                                                                              // no
                                                                              // control,
                                                                              // but
                                                                              // falls
            PMove.pml.velocity[2] -= PMove.pm.s.gravity * PMove.pml.frametime;
            if (PMove.pml.velocity[2] < 0) { // cancel as soon as we are falling
                                             // down again
                PMove.pm.s.pm_flags &= ~(pmove_t.PMF_TIME_WATERJUMP
                        | pmove_t.PMF_TIME_LAND | pmove_t.PMF_TIME_TELEPORT);
                PMove.pm.s.pm_time = 0;
            }

            PMove.PM_StepSlideMove();
        } else {
            PMove.PM_CheckJump();

            PMove.PM_Friction();

            if (PMove.pm.waterlevel >= 2)
                PMove.PM_WaterMove();
            else {
                float[] angles = { 0, 0, 0 };

                Math3D.VectorCopy(PMove.pm.viewangles, angles);
                if (angles[Defines.PITCH] > 180)
                    angles[Defines.PITCH] = angles[Defines.PITCH] - 360;
                angles[Defines.PITCH] /= 3;

                Math3D.AngleVectors(angles, PMove.pml.forward, PMove.pml.right,
                        PMove.pml.up);

                PMove.PM_AirMove();
            }
        }

        // set groundentity, watertype, and waterlevel for final spot
        PMove.PM_CatagorizePosition();

        PM_SnapPosition();
    }
}