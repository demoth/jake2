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

// Created on 14.01.2004 by RST.
// $Id: SV_GAME.java,v 1.10 2006-01-21 21:53:32 salomo Exp $
package jake2.server;

import jake2.game.GameExportsImpl;
import jake2.qcommon.*;
import jake2.qcommon.util.Math3D;

public class SV_GAME {

    // todo implement singleton
    public static GameExports gameExports;

    /**
     * PF_Unicast
     * 
     * Sends the contents of the mutlicast buffer to a single client.
     */
    public static void PF_Unicast(edict_t ent, boolean reliable) {
        int p;
        client_t client;

        if (ent == null)
            return;

        p = ent.index;
        if (p < 1 || p > SV_MAIN.maxclients.value)
            return;

        client = SV_INIT.svs.clients[p - 1];

        if (reliable)
            SZ.Write(client.netchan.message, SV_INIT.sv.multicast.data,
                    SV_INIT.sv.multicast.cursize);
        else
            SZ.Write(client.datagram, SV_INIT.sv.multicast.data,
                    SV_INIT.sv.multicast.cursize);

        SV_INIT.sv.multicast.clear();
    }

    /**
     * Centerprintf for critical messages.
     */
    public static void PF_cprintfhigh(edict_t ent, String fmt) {
    	PF_cprintf(ent, Defines.PRINT_HIGH, fmt);
    }
    
    /**
     * PF_cprintf
     * 
     * Print to a single client.
     */
    public static void PF_cprintf(edict_t ent, int level, String fmt) {

        int n = 0;

        if (ent != null) {
            n = ent.index;
            if (n < 1 || n > SV_MAIN.maxclients.value)
                Com.Error(Defines.ERR_DROP, "cprintf to a non-client");
        }

        if (ent != null)
            SV_SEND.SV_ClientPrintf(SV_INIT.svs.clients[n - 1], level, fmt);
        else
            Com.Printf(fmt);
    }

    /**
     * PF_centerprintf
     * 
     * centerprint to a single client.
     */
    public static void PF_centerprintf(edict_t ent, String fmt) {
        int n;

        n = ent.index;
        if (n < 1 || n > SV_MAIN.maxclients.value)
            return; // Com_Error (ERR_DROP, "centerprintf to a non-client");

        MSG.WriteByte(SV_INIT.sv.multicast, Defines.svc_centerprint);
        MSG.WriteString(SV_INIT.sv.multicast, fmt);
        PF_Unicast(ent, true);
    }

    /**
     *  PF_error
     * 
     *  Abort the server with a game error. 
     */
    public static void PF_error(String fmt) {
        Com.Error(Defines.ERR_DROP, "Game Error: " + fmt);
    }

    /**
     * PF_setmodel
     * 
     * Also sets mins and maxs for inline bmodels.
     */
    public static void PF_setmodel(edict_t ent, String name) {
        int i;
        cmodel_t mod;

        if (name == null)
            Com.Error(Defines.ERR_DROP, "PF_setmodel: NULL");

        i = SV_INIT.SV_ModelIndex(name);

        ent.s.modelindex = i;

        // if it is an inline model, get the size information for it
        if (name.startsWith("*")) {
            mod = CM.InlineModel(name);
            Math3D.VectorCopy(mod.mins, ent.mins);
            Math3D.VectorCopy(mod.maxs, ent.maxs);
            SV_WORLD.SV_LinkEdict(ent);
        }
    }

    /**
     *  PF_Configstring
     */
    public static void PF_Configstring(int index, String val) {
        if (index < 0 || index >= Defines.MAX_CONFIGSTRINGS)
            Com.Error(Defines.ERR_DROP, "configstring: bad index " + index
                    + "\n");

        if (val == null)
            val = "";

        // change the string in sv
        SV_INIT.sv.configstrings[index] = val;

        if (SV_INIT.sv.state != ServerStates.SS_LOADING) { // send the update to
                                                      // everyone
            SV_INIT.sv.multicast.clear();
            MSG.WriteChar(SV_INIT.sv.multicast, Defines.svc_configstring);
            MSG.WriteShort(SV_INIT.sv.multicast, index);
            MSG.WriteString(SV_INIT.sv.multicast, val);

            SV_SEND.SV_Multicast(Globals.vec3_origin, Defines.MULTICAST_ALL_R);
        }
    }

    public static void PF_WriteChar(int c) {
        MSG.WriteChar(SV_INIT.sv.multicast, c);
    }

    public static void PF_WriteByte(int c) {
        MSG.WriteByte(SV_INIT.sv.multicast, c);
    }

    public static void PF_WriteShort(int c) {
        MSG.WriteShort(SV_INIT.sv.multicast, c);
    }

    public static void PF_WriteLong(int c) {
        MSG.WriteLong(SV_INIT.sv.multicast, c);
    }

    public static void PF_WriteFloat(float f) {
        MSG.WriteFloat(SV_INIT.sv.multicast, f);
    }

    public static void PF_WriteString(String s) {
        MSG.WriteString(SV_INIT.sv.multicast, s);
    }

    public static void PF_WritePos(float[] pos) {
        MSG.WritePos(SV_INIT.sv.multicast, pos);
    }

    public static void PF_WriteDir(float[] dir) {
        MSG.WriteDir(SV_INIT.sv.multicast, dir);
    }

    public static void PF_WriteAngle(float f) {
        MSG.WriteAngle(SV_INIT.sv.multicast, f);
    }

    /**
     * PF_inPVS
     * 
     * Also checks portalareas so that doors block sight.
     */
    public static boolean PF_inPVS(float[] p1, float[] p2) {
        int leafnum;
        int cluster;
        int area1, area2;
        byte mask[];

        leafnum = CM.CM_PointLeafnum(p1);
        cluster = CM.CM_LeafCluster(leafnum);
        area1 = CM.CM_LeafArea(leafnum);
        mask = CM.CM_ClusterPVS(cluster);

        leafnum = CM.CM_PointLeafnum(p2);
        cluster = CM.CM_LeafCluster(leafnum);
        area2 = CM.CM_LeafArea(leafnum);

        // quake2 bugfix
        if (cluster == -1)
            return false;
        if (mask != null && (0 == (mask[cluster >>> 3] & (1 << (cluster & 7)))))
            return false;

        if (!CM.CM_AreasConnected(area1, area2))
            return false; // a door blocks sight

        return true;
    }

    /**
     * PF_inPHS.
     * 
     * Also checks portalareas so that doors block sound.
     */
    public static boolean PF_inPHS(float[] p1, float[] p2) {
        int leafnum;
        int cluster;
        int area1, area2;
        byte mask[];

        leafnum = CM.CM_PointLeafnum(p1);
        cluster = CM.CM_LeafCluster(leafnum);
        area1 = CM.CM_LeafArea(leafnum);
        mask = CM.CM_ClusterPHS(cluster);

        leafnum = CM.CM_PointLeafnum(p2);
        cluster = CM.CM_LeafCluster(leafnum);
        area2 = CM.CM_LeafArea(leafnum);

        // quake2 bugfix
        if (cluster == -1)
            return false;
        if (mask != null && (0 == (mask[cluster >> 3] & (1 << (cluster & 7)))))
            return false; // more than one bounce away
        if (!CM.CM_AreasConnected(area1, area2))
            return false; // a door blocks hearing

        return true;
    }

    public static void PF_StartSound(edict_t entity, int channel,
            int sound_num, float volume, float attenuation, float timeofs) {

        if (null == entity)
            return;
        SV_SEND.SV_StartSound(null, entity, channel, sound_num, volume,
                attenuation, timeofs);

    }


    /**
     * SV_InitGameProgs
     * 
     * Init the game subsystem for a new map. 
     */

    public static void SV_InitGameProgs() {

        gameExports = new GameExportsImpl(new GameImportsImpl());
    }
}