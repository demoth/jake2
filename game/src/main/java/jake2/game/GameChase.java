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

package jake2.game;


import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.edict_t;
import jake2.qcommon.trace_t;
import jake2.qcommon.util.Math3D;

public class GameChase {

    static void UpdateChaseCam(SubgameEntity ent, GameExportsImpl gameExports) {
        float[] o = { 0, 0, 0 }, ownerv = { 0, 0, 0 }, goal = { 0, 0, 0 };
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
        float[] oldgoal = { 0, 0, 0 };
        float[] angles = { 0, 0, 0 };
    
        // is our chase target gone?
        gclient_t client = ent.getClient();
        gclient_t chaseTargetClient = client.chase_target.getClient();
        if (!client.chase_target.inuse || chaseTargetClient.resp.spectator) {
            edict_t old = client.chase_target;
            ChaseNext(ent, gameExports);
            if (client.chase_target == old) {
                client.chase_target = null;
                client.getPlayerState().pmove.pm_flags &= ~Defines.PMF_NO_PREDICTION;
                return;
            }
        }

        SubgameEntity targ = client.chase_target;
    
        Math3D.VectorCopy(targ.s.origin, ownerv);
        Math3D.VectorCopy(ent.s.origin, oldgoal);
    
        ownerv[2] += targ.viewheight;

        gclient_t targetClient = targ.getClient();
        Math3D.VectorCopy(targetClient.v_angle, angles);
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

        trace_t trace = gameExports.gameImports.trace(ownerv, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);
    
        Math3D.VectorCopy(trace.endpos, goal);
    
        Math3D.VectorMA(goal, 2, forward, goal);
    
        // pad for floors and ceilings
        Math3D.VectorCopy(goal, o);
        o[2] += 6;
        trace = gameExports.gameImports.trace(goal, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);
        if (trace.fraction < 1) {
            Math3D.VectorCopy(trace.endpos, goal);
            goal[2] -= 6;
        }
    
        Math3D.VectorCopy(goal, o);
        o[2] -= 6;
        trace = gameExports.gameImports.trace(goal, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);
        if (trace.fraction < 1) {
            Math3D.VectorCopy(trace.endpos, goal);
            goal[2] += 6;
        }
    
        if (targ.deadflag != 0)
            client.getPlayerState().pmove.pm_type = Defines.PM_DEAD;
        else
            client.getPlayerState().pmove.pm_type = Defines.PM_FREEZE;
    
        Math3D.VectorCopy(goal, ent.s.origin);
        for (int i = 0; i < 3; i++)
            client.getPlayerState().pmove.delta_angles[i] = (short) Math3D
                    .ANGLE2SHORT(targetClient.v_angle[i]
                            - client.resp.cmd_angles[i]);
    
        if (targ.deadflag != 0) {
            client.getPlayerState().viewangles[Defines.ROLL] = 40;
            client.getPlayerState().viewangles[Defines.PITCH] = -15;
            client.getPlayerState().viewangles[Defines.YAW] = targetClient.killer_yaw;
        } else {
            Math3D.VectorCopy(targetClient.v_angle, client.getPlayerState().viewangles);
            Math3D.VectorCopy(targetClient.v_angle, client.v_angle);
        }
    
        ent.viewheight = 0;
        client.getPlayerState().pmove.pm_flags |= Defines.PMF_NO_PREDICTION;
        gameExports.gameImports.linkentity(ent);
    }

    public static void ChaseNext(SubgameEntity ent, GameExportsImpl gameExports) {

        gclient_t client = ent.getClient();
        if (null == client.chase_target)
            return;

        int i = client.chase_target.index;
        SubgameEntity e;
        do {
            i++;
            if (i > gameExports.game.maxclients)
                i = 1;
            e = gameExports.g_edicts[i];
    
            if (!e.inuse)
                continue;
            gclient_t entityClient = e.getClient();
            if (!entityClient.resp.spectator)
                break;
        } while (e != client.chase_target);
    
        client.chase_target = e;
        client.update_chase = true;
    }

    public static void ChasePrev(SubgameEntity ent, GameExportsImpl gameExports) {

        gclient_t client = ent.getClient();
        if (client.chase_target == null)
            return;

        int i = client.chase_target.index;
        SubgameEntity e;
        do {
            i--;
            if (i < 1)
                i = (int) gameExports.game.maxclients;
            e = gameExports.g_edicts[i];
            if (!e.inuse)
                continue;
            gclient_t entityClient = e.getClient();
            if (!entityClient.resp.spectator)
                break;
        } while (e != client.chase_target);
    
        client.chase_target = e;
        client.update_chase = true;
    }

    static void GetChaseTarget(SubgameEntity ent, GameExportsImpl gameExports) {

        for (int i = 1; i <= gameExports.game.maxclients; i++) {
            SubgameEntity other = gameExports.g_edicts[i];
            gclient_t otherClient = other.getClient();
            if (other.inuse && !otherClient.resp.spectator) {
                gclient_t client = ent.getClient();
                client.chase_target = other;
                client.update_chase = true;
                UpdateChaseCam(ent, gameExports);
                return;
            }
        }
        gameExports.gameImports.centerprintf(ent, "No other players to chase.");
    }
}
