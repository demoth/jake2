/*
 * CL_pred.java
 * Copyright (C) 2004
 * 
 * $Id: CL_pred.java,v 1.6 2004-10-04 12:50:37 hzi Exp $
 */
/*
 Copyright (C) 1997-2001 Id Software, Inc.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 */
package jake2.client;

import jake2.Defines;
import jake2.Globals;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.util.Math3D;

/**
 * CL_pred
 */
public class CL_pred {

    /*
     * =================== CL_CheckPredictionError ===================
     */
    static void CheckPredictionError() {
        int frame;
        int[] delta = new int[3];
        int i;
        int len;

        if (Globals.cl_predict.value == 0.0f
                || (Globals.cl.frame.playerstate.pmove.pm_flags & pmove_t.PMF_NO_PREDICTION) != 0)
            return;

        // calculate the last usercmd_t we sent that the server has processed
        frame = Globals.cls.netchan.incoming_acknowledged;
        frame &= (Defines.CMD_BACKUP - 1);

        // compare what the server returned with what we had predicted it to be
        Math3D.VectorSubtract(Globals.cl.frame.playerstate.pmove.origin,
                Globals.cl.predicted_origins[frame], delta);

        // save the prediction error for interpolation
        len = Math.abs(delta[0]) + Math.abs(delta[1]) + Math.abs(delta[2]);
        if (len > 640) // 80 world units
        { // a teleport or something
            Math3D.VectorClear(Globals.cl.prediction_error);
        } else {
            if (Globals.cl_showmiss.value != 0.0f
                    && (delta[0] != 0 || delta[1] != 0 || delta[2] != 0))
                Com.Printf("prediction miss on " + Globals.cl.frame.serverframe
                        + ": " + (delta[0] + delta[1] + delta[2]) + "\n");

            Math3D.VectorCopy(Globals.cl.frame.playerstate.pmove.origin,
                    Globals.cl.predicted_origins[frame]);

            // save for error itnerpolation
            for (i = 0; i < 3; i++)
                Globals.cl.prediction_error[i] = delta[i] * 0.125f;
        }
    }

    /*
     * ==================== CL_ClipMoveToEntities
     * 
     * ====================
     */
    static void ClipMoveToEntities(float[] start, float[] mins, float[] maxs,
            float[] end, trace_t tr) {
        int i, x, zd, zu;
        trace_t trace;
        int headnode;
        float[] angles;
        entity_state_t ent;
        int num;
        cmodel_t cmodel;
        float[] bmins = new float[3];
        float[] bmaxs = new float[3];

        for (i = 0; i < Globals.cl.frame.num_entities; i++) {
            num = (Globals.cl.frame.parse_entities + i)
                    & (Defines.MAX_PARSE_ENTITIES - 1);
            ent = Globals.cl_parse_entities[num];

            if (ent.solid == 0)
                continue;

            if (ent.number == Globals.cl.playernum + 1)
                continue;

            if (ent.solid == 31) { // special value for bmodel
                cmodel = Globals.cl.model_clip[ent.modelindex];
                if (cmodel == null)
                    continue;
                headnode = cmodel.headnode;
                angles = ent.angles;
            } else { // encoded bbox
                x = 8 * (ent.solid & 31);
                zd = 8 * ((ent.solid >>> 5) & 31);
                zu = 8 * ((ent.solid >>> 10) & 63) - 32;

                bmins[0] = bmins[1] = -x;
                bmaxs[0] = bmaxs[1] = x;
                bmins[2] = -zd;
                bmaxs[2] = zu;

                headnode = CM.HeadnodeForBox(bmins, bmaxs);
                angles = Globals.vec3_origin; // boxes don't rotate
            }

            if (tr.allsolid)
                return;

            trace = CM.TransformedBoxTrace(start, end, mins, maxs, headnode,
                    Defines.MASK_PLAYERSOLID, ent.origin, angles);

            if (trace.allsolid || trace.startsolid
                    || trace.fraction < tr.fraction) {
                trace.ent = ent.surrounding_ent;
                if (tr.startsolid) {
                    tr.set(trace); // rst: solved the Z U P P E L - P R O B L E
                                   // M
                    tr.startsolid = true;
                } else
                    tr.set(trace); // rst: solved the Z U P P E L - P R O B L E
                                   // M
            } else if (trace.startsolid)
                tr.startsolid = true;
        }
    }

    /*
     * ================ CL_PMTrace ================
     */

    public static edict_t DUMMY_ENT = new edict_t(-1);

    static trace_t PMTrace(float[] start, float[] mins, float[] maxs,
            float[] end) {
        trace_t t;

        // check against world
        t = CM.BoxTrace(start, end, mins, maxs, 0, Defines.MASK_PLAYERSOLID);

        if (t.fraction < 1.0f) {
            t.ent = DUMMY_ENT;
        }

        // check all other solid models
        ClipMoveToEntities(start, mins, maxs, end, t);

        return t;
    }

    /*
     * ================= PMpointcontents
     * 
     * Returns the content identificator of the point. =================
     */
    static int PMpointcontents(float[] point) {
        int i;
        entity_state_t ent;
        int num;
        cmodel_t cmodel;
        int contents;

        contents = CM.PointContents(point, 0);

        for (i = 0; i < Globals.cl.frame.num_entities; i++) {
            num = (Globals.cl.frame.parse_entities + i)
                    & (Defines.MAX_PARSE_ENTITIES - 1);
            ent = Globals.cl_parse_entities[num];

            if (ent.solid != 31) // special value for bmodel
                continue;

            cmodel = Globals.cl.model_clip[ent.modelindex];
            if (cmodel == null)
                continue;

            contents |= CM.TransformedPointContents(point, cmodel.headnode,
                    ent.origin, ent.angles);
        }
        return contents;
    }

    /*
     * ================= CL_PredictMovement
     * 
     * Sets cl.predicted_origin and cl.predicted_angles =================
     */
    static void PredictMovement() {

        if (Globals.cls.state != Defines.ca_active)
            return;

        if (Globals.cl_paused.value != 0.0f)
            return;

        if (Globals.cl_predict.value == 0.0f
                || (Globals.cl.frame.playerstate.pmove.pm_flags & pmove_t.PMF_NO_PREDICTION) != 0) {
            // just set angles
            for (int i = 0; i < 3; i++) {
                Globals.cl.predicted_angles[i] = Globals.cl.viewangles[i]
                        + Math3D
                                .SHORT2ANGLE(Globals.cl.frame.playerstate.pmove.delta_angles[i]);
            }
            return;
        }

        int ack = Globals.cls.netchan.incoming_acknowledged;
        int current = Globals.cls.netchan.outgoing_sequence;

        // if we are too far out of date, just freeze
        if (current - ack >= Defines.CMD_BACKUP) {
            if (Globals.cl_showmiss.value != 0.0f)
                Com.Printf("exceeded CMD_BACKUP\n");
            return;
        }

        // copy current state to pmove
        //memset (pm, 0, sizeof(pm));
        pmove_t pm = new pmove_t();

        pm.trace = new pmove_t.TraceAdapter() {
            public trace_t trace(float[] start, float[] mins, float[] maxs,
                    float[] end) {
                return PMTrace(start, mins, maxs, end);
            }
        };
        pm.pointcontents = new pmove_t.PointContentsAdapter() {
            public int pointcontents(float[] point) {
                return PMpointcontents(point);
            }
        };

        try {
            PMove.pm_airaccelerate = Float
                    .parseFloat(Globals.cl.configstrings[Defines.CS_AIRACCEL]);
        } catch (Exception e) {
            PMove.pm_airaccelerate = 0;
        }

        // bugfix (rst) yeah !!!!!!!! found the solution to the B E W E G U N G
        // S P R O B L E M.
        pm.s.set(Globals.cl.frame.playerstate.pmove);

        // SCR_DebugGraph (current - ack - 1, 0);
        int frame = 0;

        // run frames
        usercmd_t cmd;
        while (++ack < current) {
            frame = ack & (Defines.CMD_BACKUP - 1);
            cmd = Globals.cl.cmds[frame];

            pm.cmd.set(cmd);

            PMove.Pmove(pm);

            // save for debug checking
            Math3D.VectorCopy(pm.s.origin, Globals.cl.predicted_origins[frame]);
        }

        int oldframe = (ack - 2) & (Defines.CMD_BACKUP - 1);
        int oldz = Globals.cl.predicted_origins[oldframe][2];
        int step = pm.s.origin[2] - oldz;
        if (step > 63 && step < 160
                && (pm.s.pm_flags & pmove_t.PMF_ON_GROUND) != 0) {
            Globals.cl.predicted_step = step * 0.125f;
            Globals.cl.predicted_step_time = (int) (Globals.cls.realtime - Globals.cls.frametime * 500);
        }

        // copy results out for rendering
        Globals.cl.predicted_origin[0] = pm.s.origin[0] * 0.125f;
        Globals.cl.predicted_origin[1] = pm.s.origin[1] * 0.125f;
        Globals.cl.predicted_origin[2] = pm.s.origin[2] * 0.125f;

        Math3D.VectorCopy(pm.viewangles, Globals.cl.predicted_angles);
    }
}