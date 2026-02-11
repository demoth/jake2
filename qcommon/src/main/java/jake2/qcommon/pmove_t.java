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

// Created on 31.10.2003 by RST.
// $Id: pmove_t.java,v 1.4 2005-01-21 01:08:48 cawe Exp $
package jake2.qcommon;

import jake2.qcommon.util.Math3D;

import java.util.Arrays;

public class pmove_t {

    public interface PointContentsAdapter {
        // callbacks to test the world
        int pointcontents(float[] point);
    }

    public static class TraceAdapter {
        // callbacks to test the world
        public trace_t trace(float[] start, float[] mins, float[] maxs,
                             float[] end) {
            return null;
        }
    }

    // state (in / out)
    public pmove_state_t s = new pmove_state_t();

    // command (in)
    public usercmd_t cmd = new usercmd_t();

    public boolean snapinitial; // if s has been changed outside pmove

    // results (out)
    public int numtouch;

    public ServerEntity touchents[] = new ServerEntity[Defines.MAXTOUCH];

    public float[] viewangles = { 0, 0, 0 }; // clamped

    public float viewheight;

    public float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 }; // bounding box size

    public ServerEntity groundentity;

    public int watertype;

    public int waterlevel;

    public TraceAdapter trace;

    public PointContentsAdapter pointcontents;

    /**
     * Resolves final pmove view angles from user command angles and server delta angles.
     * Extracted from PMove.PM_ClampAngles as part of static-to-instance migration.
     */
    public void clampAngles(float[] forward, float[] right, float[] up) {
        short temp;

        if ((s.pm_flags & Defines.PMF_TIME_TELEPORT) != 0) {
            viewangles[Defines.YAW] = Math3D.SHORT2ANGLE(cmd.angles[Defines.YAW] + s.delta_angles[Defines.YAW]);
            viewangles[Defines.PITCH] = 0;
            viewangles[Defines.ROLL] = 0;
        } else {
            for (int i = 0; i < 3; i++) {
                temp = (short) (cmd.angles[i] + s.delta_angles[i]);
                viewangles[i] = Math3D.SHORT2ANGLE(temp);
            }

            if (viewangles[Defines.PITCH] > 89 && viewangles[Defines.PITCH] < 180) {
                viewangles[Defines.PITCH] = 89;
            } else if (viewangles[Defines.PITCH] < 271 && viewangles[Defines.PITCH] >= 180) {
                viewangles[Defines.PITCH] = 271;
            }
        }

        Math3D.AngleVectors(viewangles, forward, right, up);
    }

    /**
     * Updates collision bounds and view height based on movement type / duck state.
     * Extracted from PMove.PM_CheckDuck as part of static-to-instance migration.
     */
    public void checkDuck(float[] origin) {
        trace_t trace;

        mins[0] = -16;
        mins[1] = -16;
        maxs[0] = 16;
        maxs[1] = 16;

        if (s.pm_type == Defines.PM_GIB) {
            mins[2] = 0;
            maxs[2] = 16;
            viewheight = 8;
            return;
        }

        mins[2] = -24;

        if (s.pm_type == Defines.PM_DEAD) {
            s.pm_flags |= Defines.PMF_DUCKED;
        } else if (cmd.upmove < 0 && (s.pm_flags & Defines.PMF_ON_GROUND) != 0) {
            s.pm_flags |= Defines.PMF_DUCKED;
        } else {
            if ((s.pm_flags & Defines.PMF_DUCKED) != 0) {
                maxs[2] = 32;
                trace = this.trace.trace(origin, mins, maxs, origin);
                if (!trace.allsolid) {
                    s.pm_flags &= ~Defines.PMF_DUCKED;
                }
            }
        }

        if ((s.pm_flags & Defines.PMF_DUCKED) != 0) {
            maxs[2] = 4;
            viewheight = -2;
        } else {
            maxs[2] = 32;
            viewheight = 22;
        }
    }

    /**
     * Applies extra friction to dead bodies while grounded.
     * Extracted from PMove.PM_DeadMove as part of static-to-instance migration.
     */
    public void deadMove(float[] velocity) {
        float forward;

        if (groundentity == null) {
            return;
        }

        forward = Math3D.VectorLength(velocity);
        forward -= 20;
        if (forward <= 0) {
            Math3D.VectorClear(velocity);
        } else {
            Math3D.VectorNormalize(velocity);
            Math3D.VectorScale(velocity, forward, velocity);
        }
    }

    public void clear() {
        groundentity = null;
        waterlevel = watertype = 0;
        trace = null;
        pointcontents = null;
        Math3D.VectorClear(mins);
        Math3D.VectorClear(maxs);
        viewheight = 0;
        Math3D.VectorClear(viewangles);
        Arrays.fill(touchents, null);
        numtouch = 0;
        snapinitial = false;
        cmd.clear();
        s.clear();
    }
}
