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
// $Id: pmove_t.java,v 1.3 2005-01-21 00:54:44 cawe Exp $
package jake2.game;

import jake2.Defines;
import jake2.util.Math3D;

import java.util.Arrays;

public class pmove_t {

    public static class TraceAdapter {
        // callbacks to test the world
        public trace_t trace(float[] start, float[] mins, float[] maxs,
                float[] end) {
            return null;
        }
    }

    public static class PointContentsAdapter {
        // callbacks to test the world
        public int pointcontents(float[] point) {
            return 0;
        }
    }

    // state (in / out)
    public pmove_state_t s = new pmove_state_t();

    // command (in)
    public usercmd_t cmd = new usercmd_t();

    public boolean snapinitial; // if s has been changed outside pmove

    // results (out)
    public int numtouch;

    public edict_t touchents[] = new edict_t[Defines.MAXTOUCH];

    public float[] viewangles = { 0, 0, 0 }; // clamped

    public float viewheight;

    public float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 }; // bounding box size

    public edict_t groundentity;

    public int watertype;

    public int waterlevel;

    public TraceAdapter trace;

    public PointContentsAdapter pointcontents;

    // pmove->pm_flags
    public final static int PMF_DUCKED = 1;

    public final static int PMF_JUMP_HELD = 2;

    public final static int PMF_ON_GROUND = 4;

    public final static int PMF_TIME_WATERJUMP = 8; // pm_time is waterjump

    public final static int PMF_TIME_LAND = 16; // pm_time is time before rejump

    public final static int PMF_TIME_TELEPORT = 32; // pm_time is non-moving
                                                    // time

    public final static int PMF_NO_PREDICTION = 64; // temporarily disables
                                                    // prediction (used for
                                                    // grappling hook)

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
        cmd.reset();
        s.reset();
    }
}