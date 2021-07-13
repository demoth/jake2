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

import jake2.qcommon.*;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.network.messages.server.ConfigStringMessage;
import jake2.qcommon.util.Math3D;

import java.io.File;

public class SV_GAME {
    final GameImportsImpl gameImports;

    public SV_GAME(GameImportsImpl gameImports) {
        this.gameImports = gameImports;
    }

    /**
     * PF_Unicast
     * 
     * Sends the contents of the mutlicast buffer to a single client.
     */
    public void PF_Unicast(int p, boolean reliable, NetworkMessage msg) {

        if (p < 1 || p > gameImports.serverMain.getClients().size())
            return;

        client_t client = gameImports.serverMain.getClients().get(p - 1);

        if (reliable) {
            msg.writeTo(gameImports.sv.multicast);
            client.netchan.reliable.writeBytes(gameImports.sv.multicast.data, gameImports.sv.multicast.cursize);
            gameImports.sv.multicast.clear();

        } else
            client.unreliable.add(msg);

    }

    /**
     * Centerprintf for critical messages.
     */
    public void PF_cprintfhigh(edict_t ent, String fmt) {
    	PF_cprintf(ent, Defines.PRINT_HIGH, fmt);
    }
    
    /**
     * PF_cprintf
     * 
     * Print to a single client.
     */
    public void PF_cprintf(edict_t ent, int level, String fmt) {

        int n = 0;

        if (ent != null) {
            n = ent.index;
            if (n < 1 || n > gameImports.serverMain.getClients().size())
                Com.Error(Defines.ERR_DROP, "cprintf to a non-client");
        }

        if (ent != null)
            SV_SEND.SV_ClientPrintf(gameImports.serverMain.getClients().get(n - 1), level, fmt);
        else
            Com.Printf(fmt);
    }

    /**
     *  PF_error
     * 
     *  Abort the server with a game error.
     */
    public void PF_error(String fmt) {
        Com.Error(Defines.ERR_DROP, "Game Error: " + fmt);
    }

    /**
     * PF_setmodel
     * 
     * Also sets mins and maxs for inline bmodels.
     */
    public void PF_setmodel(edict_t ent, String name) {

        if (name == null) {
            Com.DPrintf( "Error: SV_GAME.PF_setmodel: name is null");
            return;
        }

        ent.s.modelindex = SV_FindIndex(name, Defines.CS_MODELS, Defines.MAX_MODELS, true);

        // if it is an inline model, get the size information for it
        if (name.startsWith("*")) {
            cmodel_t mod = gameImports.cm.InlineModel(name);
            Math3D.VectorCopy(mod.mins, ent.mins);
            Math3D.VectorCopy(mod.maxs, ent.maxs);
            SV_WORLD.SV_LinkEdict(ent, gameImports);
        }
    }

    /**
     *  Change i-th configstring to 'val' and multicast it to everyone reliably
     */
    void PF_Configstring(int index, String val) {
        if (index < 0 || index >= Defines.MAX_CONFIGSTRINGS)
            Com.Error(Defines.ERR_DROP, "configstring: bad index " + index
                    + "\n");

        if (val == null)
            val = "";

        // change the string in sv
        gameImports.sv.configstrings[index] = val;

        if (gameImports.sv.state != ServerStates.SS_LOADING) { // send the update to
                                                      // everyone
            gameImports.sv.multicast.clear();
            gameImports.multicastMessage(Globals.vec3_origin, new ConfigStringMessage(index, val), MulticastTypes.MULTICAST_ALL_R);
        }
    }

    /**
     * PF_inPVS
     * 
     * Also checks portalareas so that doors block sight.
     */
    public boolean PF_inPVS(float[] p1, float[] p2) {
        int leafnum;
        int cluster;
        int area1, area2;
        byte mask[];

        leafnum = gameImports.cm.CM_PointLeafnum(p1);
        cluster = gameImports.cm.CM_LeafCluster(leafnum);
        area1 = gameImports.cm.CM_LeafArea(leafnum);
        mask = gameImports.cm.CM_ClusterPVS(cluster);

        leafnum = gameImports.cm.CM_PointLeafnum(p2);
        cluster = gameImports.cm.CM_LeafCluster(leafnum);
        area2 = gameImports.cm.CM_LeafArea(leafnum);

        // quake2 bugfix
        if (cluster == -1)
            return false;
        if (mask != null && (0 == (mask[cluster >>> 3] & (1 << (cluster & 7)))))
            return false;

        if (!gameImports.cm.CM_AreasConnected(area1, area2))
            return false; // a door blocks sight

        return true;
    }

    /**
     * PF_inPHS.
     * 
     * Also checks portalareas so that doors block sound.
     */
    public boolean PF_inPHS(float[] p1, float[] p2) {
        int leafnum;
        int cluster;
        int area1, area2;
        byte mask[];

        leafnum = gameImports.cm.CM_PointLeafnum(p1);
        cluster = gameImports.cm.CM_LeafCluster(leafnum);
        area1 = gameImports.cm.CM_LeafArea(leafnum);
        mask = gameImports.cm.CM_ClusterPHS(cluster);

        leafnum = gameImports.cm.CM_PointLeafnum(p2);
        cluster = gameImports.cm.CM_LeafCluster(leafnum);
        area2 = gameImports.cm.CM_LeafArea(leafnum);

        // quake2 bugfix
        if (cluster == -1)
            return false;
        if (mask != null && (0 == (mask[cluster >> 3] & (1 << (cluster & 7)))))
            return false; // more than one bounce away
        if (!gameImports.cm.CM_AreasConnected(area1, area2))
            return false; // a door blocks hearing

        return true;
    }

    public void PF_StartSound(edict_t entity, int channel,
            int sound_num, float volume, float attenuation, float timeofs) {

        if (null == entity)
            return;
        SV_SEND.SV_StartSound(null, entity, channel, sound_num, volume,
                attenuation, timeofs, gameImports);

    }

    /**
     * Find an index of a configstring with `val` value, or create a new one.
     */
    int SV_FindIndex(String val, int start, int max, boolean create) {
        int i;

        if (val == null || val.length() == 0)
            return 0;

        for (i = 1; i < max && gameImports.sv.configstrings[start + i] != null; i++)
            if (val.equals(gameImports.sv.configstrings[start + i]))
                return i;

        if (!create)
            return 0;

        if (i == max)
            Com.Error(Defines.ERR_DROP, "*Index: overflow");

        PF_Configstring(start + i, val);

        return i;
    }

    /**
     * SV_CreateBaseline
     *
     * Entity baselines are used to compress the update messages to the clients --
     * only the fields that differ from the baseline will be transmitted.
     */
    void SV_CreateBaseline() {
        for (int entnum = 1; entnum < gameImports.gameExports.getNumEdicts(); entnum++) {
            edict_t svent = gameImports.gameExports.getEdict(entnum);

            if (!svent.inuse)
                continue;
            if (0 == svent.s.modelindex && 0 == svent.s.sound
                    && 0 == svent.s.effects)
                continue;

            svent.s.number = entnum;

            // take current state as baseline
            Math3D.VectorCopy(svent.s.origin, svent.s.old_origin);
            gameImports.sv.baselines[entnum].set(svent.s);
        }
    }

    /**
     * SV_CheckForSavegame.
     * @param sv
     */
    void SV_CheckForSavegame(server_t sv) {

        if (Cvar.getInstance().Get("sv_noreload", "0", 0).value != 0)
            return;

        if (Cvar.getInstance().VariableValue("deathmatch") != 0)
            return;

        String name = FS.getWriteDir() + "/save/current/" + sv.name + ".sav";

        if (!new File(name).exists())
            return;

        SV_WORLD.SV_ClearWorld(gameImports);

        // get configstrings and areaportals
        // then read game enitites
        SV_CCMDS.SV_ReadLevelFile(sv.name, gameImports);

        if (!sv.loadgame) {
            // coming back to a level after being in a different
            // level, so run it for ten seconds

            // rlava2 was sending too many lightstyles, and overflowing the
            // reliable data. temporarily changing the server state to loading
            // prevents these from being passed down.
            ServerStates previousState; // PGM

            previousState = sv.state; // PGM
            sv.state = ServerStates.SS_LOADING; // PGM
            for (int i = 0; i < 100; i++)
                gameImports.gameExports.G_RunFrame();

            sv.state = previousState; // PGM
        }
    }


}