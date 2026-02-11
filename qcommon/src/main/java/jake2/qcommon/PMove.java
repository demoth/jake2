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
// $Id: PMove.java,v 1.8 2006-01-21 21:53:32 salomo Exp $
package jake2.qcommon;

import jake2.qcommon.util.Math3D;

import java.util.Objects;

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

        public float[] previous_origin = { 0, 0, 0 };

    }

    public static pmove_t pm;

    public static pml_t pml = new pml_t();

    // movement parameters
    public static float pm_stopspeed = 100;

    public static float pm_maxspeed = 300;

    public static float pm_duckspeed = 100;

    public static float pm_accelerate = 10;

    /*
     was initialized in spawnServerInstance:
         if (Cvar.getInstance().VariableValue("deathmatch") != 0) {
            gameImports.sv.configstrings[CS_AIRACCEL] = "" + sv_airaccelerate.value;
            PMove.pm_airaccelerate = sv_airaccelerate.value;
        } else {
            gameImports.sv.configstrings[CS_AIRACCEL] = "0";
            PMove.pm_airaccelerate = 0;
        }

     */
    public static float pm_airaccelerate = 0;

    public static float pm_wateraccelerate = 10;

    public static float pm_friction = 6;

    public static float pm_waterfriction = 1;

    public static float pm_waterspeed = 400;

    // try all single bits first
    public static int jitterbits[] = { 0, 4, 1, 2, 3, 5, 6, 7 };

    public static int offset[] = { 0, -1, 1 };

    @FunctionalInterface
    public interface PmoveProcessor {
        void move(pmove_t pmove);
    }

    private static final class LegacyPmoveProcessor implements PmoveProcessor {
        private pmove_t currentPm;
        private final pml_t currentPml = new pml_t();
        private final float[][] currentPlanes = new float[MAX_CLIP_PLANES][3];

        @Override
        public synchronized void move(pmove_t pmove) {
            currentPm = pmove;
            // Compatibility bridge for tests and any external diagnostics that read PMove.pm/pml.
            PMove.pm = currentPm;
            PMove.pml = currentPml;
            runLegacyPmove(currentPm, currentPml, currentPlanes);
        }
    }

    /**
     * Creates a dedicated pmove processor instance with isolated per-run mutable state.
     * Useful for client/server separation while preserving legacy pmove behavior.
     */
    public static PmoveProcessor newLegacyProcessor() {
        return new LegacyPmoveProcessor();
    }

    private static final PmoveProcessor LEGACY_PROCESSOR = newLegacyProcessor();
    private static PmoveProcessor processor = LEGACY_PROCESSOR;

    // Test seam for the incremental OOP migration; production code keeps the legacy processor.
    static void setProcessor(PmoveProcessor nextProcessor) {
        processor = Objects.requireNonNull(nextProcessor, "nextProcessor");
    }

    static void resetProcessor() {
        processor = LEGACY_PROCESSOR;
    }

    /*
     * OOP migration ownership map:
     * - pmove_t instance behavior:
     *   PM_ClampAngles (migrated), PM_CheckDuck (migrated), PM_CatagorizePosition (migrated), PM_CheckJump (migrated),
     *   PM_CheckSpecialMovement (migrated), PM_DeadMove (migrated), PM_GoodPosition (migrated), PM_InitialSnapPosition (migrated),
     *   PM_SnapPosition (migrated), PM_AddCurrents (migrated), PM_Friction (migrated), PM_Accelerate (migrated), PM_AirAccelerate (migrated),
     *   PM_WaterMove (migrated), PM_AirMove, PM_FlyMove, PM_StepSlideMove_ (migrated), PM_StepSlideMove (migrated).
     * - processor shell behavior:
     *   Pmove, runLegacyPmove.
     */
    public final static int MAX_CLIP_PLANES = 5;

    /**
     * PM_AirMove.
     */
    private static void PM_AirMove(pmove_t pm, pml_t pml, float[][] planes) {
        float[] wishvel = { 0, 0, 0 };
        float fmove, smove;
        float[] wishdir = { 0, 0, 0 };
        float wishspeed;
        float maxspeed;

        fmove = pm.cmd.forwardmove;
        smove = pm.cmd.sidemove;

        wishvel[0] = pml.forward[0] * fmove + pml.right[0] * smove;
        wishvel[1] = pml.forward[1] * fmove + pml.right[1] * smove;
        
        wishvel[2] = 0;

        pm.addCurrents(pml.velocity, pm_waterspeed, wishvel);

        Math3D.VectorCopy(wishvel, wishdir);
        wishspeed = Math3D.VectorNormalize(wishdir);

        
        // clamp to server defined max speed
        maxspeed = (pm.s.pm_flags & Defines.PMF_DUCKED) != 0 ? pm_duckspeed
                : pm_maxspeed;

        if (wishspeed > maxspeed) {
            Math3D.VectorScale(wishvel, maxspeed / wishspeed, wishvel);
            wishspeed = maxspeed;
        }

        if (pm.ladder) {
            pm.accelerate(pml.velocity, pml.frametime, wishdir, wishspeed, pm_accelerate);
            if (0 == wishvel[2]) {
                if (pml.velocity[2] > 0) {
                    pml.velocity[2] -= pm.s.gravity * pml.frametime;
                    if (pml.velocity[2] < 0)
                        pml.velocity[2] = 0;
                } else {
                    pml.velocity[2] += pm.s.gravity * pml.frametime;
                    if (pml.velocity[2] > 0)
                        pml.velocity[2] = 0;
                }
            }
            pm.stepSlideMove(pml.origin, pml.velocity, pml.frametime, planes);
        } else if (pm.groundentity != null) { // walking on ground
            pml.velocity[2] = 0; //!!! this is before the accel
            pm.accelerate(pml.velocity, pml.frametime, wishdir, wishspeed, pm_accelerate);

            // PGM -- fix for negative trigger_gravity fields
            //		pml.velocity[2] = 0;
            if (pm.s.gravity > 0)
                pml.velocity[2] = 0;
            else
                pml.velocity[2] -= pm.s.gravity * pml.frametime;
            // PGM
            if (0 == pml.velocity[0] && 0 == pml.velocity[1])
                return;
            pm.stepSlideMove(pml.origin, pml.velocity, pml.frametime, planes);
        } else { // not on ground, so little effect on velocity
            if (pm_airaccelerate != 0)
                pm.airAccelerate(pml.velocity, pml.frametime, wishdir, wishspeed, pm_accelerate);
            else
                pm.accelerate(pml.velocity, pml.frametime, wishdir, wishspeed, 1);
            // add gravity
            pml.velocity[2] -= pm.s.gravity * pml.frametime;
            pm.stepSlideMove(pml.origin, pml.velocity, pml.frametime, planes);
        }
    }

    /**
     * PM_FlyMove.
     */
    private static void PM_FlyMove(pmove_t pm, pml_t pml, boolean doclip) {
        float speed, drop, friction, control, newspeed;
        float currentspeed, addspeed, accelspeed;
        int i;
        float[] wishvel = { 0, 0, 0 };
        float fmove, smove;
        float[] wishdir = { 0, 0, 0 };
        float wishspeed;
        float[] end = { 0, 0, 0 };
        trace_t trace;

        pm.viewheight = 22;

        // friction

        speed = Math3D.VectorLength(pml.velocity);
        if (speed < 1) {
            Math3D.VectorCopy(Globals.vec3_origin, pml.velocity);
        } else {
            drop = 0;

            friction = pm_friction * 1.5f; // extra friction
            control = speed < pm_stopspeed ? pm_stopspeed : speed;
            drop += control * friction * pml.frametime;

            // scale the velocity
            newspeed = speed - drop;
            if (newspeed < 0)
                newspeed = 0;
            newspeed /= speed;

            Math3D.VectorScale(pml.velocity, newspeed, pml.velocity);
        }

        // accelerate
        fmove = pm.cmd.forwardmove;
        smove = pm.cmd.sidemove;

        Math3D.VectorNormalize(pml.forward);
        Math3D.VectorNormalize(pml.right);

        for (i = 0; i < 3; i++)
            wishvel[i] = pml.forward[i] * fmove + pml.right[i]
                    * smove;
        wishvel[2] += pm.cmd.upmove;

        Math3D.VectorCopy(wishvel, wishdir);
        wishspeed = Math3D.VectorNormalize(wishdir);

        // clamp to server defined max speed
        if (wishspeed > pm_maxspeed) {
            Math3D.VectorScale(wishvel, pm_maxspeed / wishspeed, wishvel);
            wishspeed = pm_maxspeed;
        }

        currentspeed = Math3D.DotProduct(pml.velocity, wishdir);
        addspeed = wishspeed - currentspeed;
        if (addspeed <= 0)
            return;
        accelspeed = pm_accelerate * pml.frametime * wishspeed;
        if (accelspeed > addspeed)
            accelspeed = addspeed;

        for (i = 0; i < 3; i++)
            pml.velocity[i] += accelspeed * wishdir[i];

        if (doclip) {
            for (i = 0; i < 3; i++)
                end[i] = pml.origin[i] + pml.frametime * pml.velocity[i];

            trace = pm.trace.trace(pml.origin, pm.mins, pm.maxs, end);

            Math3D.VectorCopy(trace.endpos, pml.origin);
        } else {
            // move
            Math3D.VectorMA(pml.origin, pml.frametime, pml.velocity, pml.origin);
        }
    }

    /**
     * Can be called by either the server or the client.
     */
    public static void Pmove(pmove_t pmove) {
        processor.move(pmove);
    }

    // C reference: Pmove in qcommon/pmove.c. Kept as static legacy body while migration proceeds.
    private static void runLegacyPmove(pmove_t pm, pml_t pml, float[][] planes) {
        // clear results
        pm.numtouch = 0;
        Math3D.VectorClear(pm.viewangles);
        pm.viewheight = 0;
        pm.groundentity = null;
        pm.watertype = 0;
        pm.waterlevel = 0;

        pm.groundsurface = null;
        pm.groundcontents = 0;

        // convert origin and velocity to float values
        pml.origin[0] = pm.s.origin[0] * 0.125f;
        pml.origin[1] = pm.s.origin[1] * 0.125f;
        pml.origin[2] = pm.s.origin[2] * 0.125f;

        pml.velocity[0] = pm.s.velocity[0] * 0.125f;
        pml.velocity[1] = pm.s.velocity[1] * 0.125f;
        pml.velocity[2] = pm.s.velocity[2] * 0.125f;

        // save old org in case we get stuck
        Math3D.VectorCopy(pm.s.origin, pml.previous_origin);

        pml.frametime = (pm.cmd.msec & 0xFF) * 0.001f;

        pm.clampAngles(pml.forward, pml.right, pml.up);

        if (pm.s.pm_type == Defines.PM_SPECTATOR) {
            PM_FlyMove(pm, pml, false);
            pm.snapPosition(pml.origin, pml.velocity, pml.previous_origin, jitterbits);
            return;
        }

        if (pm.s.pm_type >= Defines.PM_DEAD) {
            pm.cmd.forwardmove = 0;
            pm.cmd.sidemove = 0;
            pm.cmd.upmove = 0;
        }

        if (pm.s.pm_type == Defines.PM_FREEZE)
            return; // no movement at all

        // set mins, maxs, and viewheight
        pm.checkDuck(pml.origin);

        if (pm.snapinitial)
            pm.initialSnapPosition(pml.origin, pml.previous_origin, offset);

        // set groundentity, watertype, and waterlevel
        pm.categorizePosition(pml.origin, pml.velocity);

        if (pm.s.pm_type == Defines.PM_DEAD)
            pm.deadMove(pml.velocity);

        pm.checkSpecialMovement(pml.origin, pml.forward, pml.velocity);

        // drop timing counter
        if (pm.s.pm_time != 0) {
            int msec;

            // TOD o bugfix cwei
            msec = pm.cmd.msec >>> 3;
            if (msec == 0)
                msec = 1;
            if (msec >= (pm.s.pm_time & 0xFF)) {
                pm.s.pm_flags &= ~(Defines.PMF_TIME_WATERJUMP
                        | Defines.PMF_TIME_LAND | Defines.PMF_TIME_TELEPORT);
                pm.s.pm_time = 0;
            } else
                pm.s.pm_time = (byte) ((pm.s.pm_time & 0xFF) - msec);
        }

        if ((pm.s.pm_flags & Defines.PMF_TIME_TELEPORT) != 0) {
        	// teleport pause stays exaclty in place
        } else if ((pm.s.pm_flags & Defines.PMF_TIME_WATERJUMP) != 0) {
        	// waterjump has no control, but falls 
            pml.velocity[2] -= pm.s.gravity * pml.frametime;
            if (pml.velocity[2] < 0) { 
            	// cancel as soon as we are falling down again
                pm.s.pm_flags &= ~(Defines.PMF_TIME_WATERJUMP
                        | Defines.PMF_TIME_LAND | Defines.PMF_TIME_TELEPORT);
                pm.s.pm_time = 0;
            }

            pm.stepSlideMove(pml.origin, pml.velocity, pml.frametime, planes);
        } else {
            pm.checkJump(pml.velocity);

            pm.friction(pml.velocity, pml.frametime, pm_stopspeed, pm_friction, pm_waterfriction);

            if (pm.waterlevel >= 2)
                pm.waterMove(
                        pml.forward,
                        pml.right,
                        pml.origin,
                        pml.velocity,
                        pml.frametime,
                        pm_maxspeed,
                        pm_wateraccelerate,
                        pm_waterspeed,
                        planes
                );
            else {
                float[] angles = { 0, 0, 0 };

                Math3D.VectorCopy(pm.viewangles, angles);
                
                if (angles[Defines.PITCH] > 180)
                    angles[Defines.PITCH] = angles[Defines.PITCH] - 360;
                
                angles[Defines.PITCH] /= 3;

                Math3D.AngleVectors(angles, pml.forward, pml.right, pml.up);

                PM_AirMove(pm, pml, planes);
            }
        }

        // set groundentity, watertype, and waterlevel for final spot
        pm.categorizePosition(pml.origin, pml.velocity);
        pm.snapPosition(pml.origin, pml.velocity, pml.previous_origin, jitterbits);
    }
}
