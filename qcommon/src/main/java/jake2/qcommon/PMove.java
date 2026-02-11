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
        private final float[][] currentPlanes = new float[pmove_t.MAX_CLIP_PLANES][3];

        @Override
        public synchronized void move(pmove_t pmove) {
            currentPm = pmove;
            // Compatibility bridge for tests and any external diagnostics that read PMove.pm/pml.
            PMove.pm = currentPm;
            PMove.pml = currentPml;
            currentPm.runLegacyPmove(currentPml, currentPlanes);
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
     *   PM_WaterMove (migrated), PM_AirMove (migrated), PM_FlyMove (migrated), PM_StepSlideMove_ (migrated), PM_StepSlideMove (migrated).
     * - processor shell behavior:
     *   Pmove.
     */
    /**
     * Can be called by either the server or the client.
     */
    public static void Pmove(pmove_t pmove) {
        processor.move(pmove);
    }
}
