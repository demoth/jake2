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

    public edict_t touchents[] = new edict_t[Defines.MAXTOUCH];

    public float[] viewangles = { 0, 0, 0 }; // clamped

    public float viewheight;

    public float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 }; // bounding box size

    public edict_t groundentity;

    public int watertype;

    public int waterlevel;

    public TraceAdapter trace;

    public PointContentsAdapter pointcontents;

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