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

// Created on 16.11.2005 by RST.
// $Id: GameChase.java,v 1.2 2006-01-21 21:53:32 salomo Exp $

package jake2.game;


import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.Math3D;


public class GameChase {

    public static void UpdateChaseCam(edict_t ent) {
        float[] o = { 0, 0, 0 }, ownerv = { 0, 0, 0 }, goal = { 0, 0, 0 };
        edict_t targ;
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
        trace_t trace;
        int i;
        float[] oldgoal = { 0, 0, 0 };
        float[] angles = { 0, 0, 0 };
    
        // is our chase target gone?
        if (!ent.client.chase_target.inuse
                || ent.client.chase_target.client.resp.spectator) {
            edict_t old = ent.client.chase_target;
            ChaseNext(ent);
            if (ent.client.chase_target == old) {
                ent.client.chase_target = null;
                ent.client.ps.pmove.pm_flags &= ~pmove_t.PMF_NO_PREDICTION;
                return;
            }
        }
    
        targ = ent.client.chase_target;
    
        Math3D.VectorCopy(targ.s.origin, ownerv);
        Math3D.VectorCopy(ent.s.origin, oldgoal);
    
        ownerv[2] += targ.viewheight;
    
        Math3D.VectorCopy(targ.client.v_angle, angles);
        if (angles[Defines.PITCH] > 56)
            angles[Defines.PITCH] = 56;
        Math3D.AngleVectors(angles, forward, right, null);
        Math3D.VectorNormalize(forward);
        Math3D.VectorMA(ownerv, -30, forward, o);
    
        if (o[2] < targ.s.origin[2] + 20)
            o[2] = targ.s.origin[2] + 20;
    
        // jump animation lifts
        if (targ.groundentity == null)
            o[2] += 16;
    
        trace = GameBase.gi.trace(ownerv, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);
    
        Math3D.VectorCopy(trace.endpos, goal);
    
        Math3D.VectorMA(goal, 2, forward, goal);
    
        // pad for floors and ceilings
        Math3D.VectorCopy(goal, o);
        o[2] += 6;
        trace = GameBase.gi.trace(goal, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);
        if (trace.fraction < 1) {
            Math3D.VectorCopy(trace.endpos, goal);
            goal[2] -= 6;
        }
    
        Math3D.VectorCopy(goal, o);
        o[2] -= 6;
        trace = GameBase.gi.trace(goal, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);
        if (trace.fraction < 1) {
            Math3D.VectorCopy(trace.endpos, goal);
            goal[2] += 6;
        }
    
        if (targ.deadflag != 0)
            ent.client.ps.pmove.pm_type = Defines.PM_DEAD;
        else
            ent.client.ps.pmove.pm_type = Defines.PM_FREEZE;
    
        Math3D.VectorCopy(goal, ent.s.origin);
        for (i = 0; i < 3; i++)
            ent.client.ps.pmove.delta_angles[i] = (short) Math3D
                    .ANGLE2SHORT(targ.client.v_angle[i]
                            - ent.client.resp.cmd_angles[i]);
    
        if (targ.deadflag != 0) {
            ent.client.ps.viewangles[Defines.ROLL] = 40;
            ent.client.ps.viewangles[Defines.PITCH] = -15;
            ent.client.ps.viewangles[Defines.YAW] = targ.client.killer_yaw;
        } else {
            Math3D.VectorCopy(targ.client.v_angle, ent.client.ps.viewangles);
            Math3D.VectorCopy(targ.client.v_angle, ent.client.v_angle);
        }
    
        ent.viewheight = 0;
        ent.client.ps.pmove.pm_flags |= pmove_t.PMF_NO_PREDICTION;
        SV_WORLD.SV_LinkEdict(ent);
    }

    public static void ChaseNext(edict_t ent) {
        int i;
        edict_t e;
    
        if (null == ent.client.chase_target)
            return;
    
        i = ent.client.chase_target.index;
        do {
            i++;
            if (i > GameBase.maxclients.value)
                i = 1;
            e = GameBase.g_edicts[i];
    
            if (!e.inuse)
                continue;
            if (!e.client.resp.spectator)
                break;
        } while (e != ent.client.chase_target);
    
        ent.client.chase_target = e;
        ent.client.update_chase = true;
    }

    public static void ChasePrev(edict_t ent) {
        int i;
        edict_t e;
    
        if (ent.client.chase_target == null)
            return;
    
        i = ent.client.chase_target.index;
        do {
            i--;
            if (i < 1)
                i = (int) GameBase.maxclients.value;
            e = GameBase.g_edicts[i];
            if (!e.inuse)
                continue;
            if (!e.client.resp.spectator)
                break;
        } while (e != ent.client.chase_target);
    
        ent.client.chase_target = e;
        ent.client.update_chase = true;
    }

    public static void GetChaseTarget(edict_t ent) {
        int i;
        edict_t other;
    
        for (i = 1; i <= GameBase.maxclients.value; i++) {
            other = GameBase.g_edicts[i];
            if (other.inuse && !other.client.resp.spectator) {
                ent.client.chase_target = other;
                ent.client.update_chase = true;
                UpdateChaseCam(ent);
                return;
            }
        }
        GameBase.gi.centerprintf(ent, "No other players to chase.");
    }
}
