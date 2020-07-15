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
// $Id: game_import_t.java,v 1.7 2006-01-21 21:53:31 salomo Exp $
package jake2.server;

import jake2.qcommon.*;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.util.Lib;

//
//	collection of functions provided by the main engine
//
// todo make singleton (same as game exports)
public class GameImportsImpl implements GameImports {

    // persistent server state
    public server_static_t svs;

    // local (instance) server state
    public server_t sv;

    public GameImportsImpl() {
        // Initialize server static state
        svs = new server_static_t();
        svs.initialized = true;
        svs.spawncount = Lib.rand();

        // Clear all clients
        svs.clients = new client_t[(int) SV_MAIN.maxclients.value]; //todo use cvar
        for (int n = 0; n < svs.clients.length; n++) {
            svs.clients[n] = new client_t();
            svs.clients[n].serverindex = n;
        }

        svs.num_client_entities = ((int) SV_MAIN.maxclients.value)
                * Defines.UPDATE_BACKUP * 64; //ok.

        // Clear all client entity states
        svs.client_entities = new entity_state_t[svs.num_client_entities];
        for (int n = 0; n < svs.client_entities.length; n++) {
            svs.client_entities[n] = new entity_state_t(null);
        }

        // heartbeats will always be sent to the id master
        svs.last_heartbeat = -99999; // send immediately

        // create local server state
        sv = new server_t();

    }

    void resetClients(GameExports gameExports) {
        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            svs.clients[i].edict = gameExports.getEdict(i + 1);
            svs.clients[i].lastcmd = new usercmd_t();
        }
    }

    // special messages
    @Override
    public void bprintf(int printlevel, String s) {
        SV_SEND.SV_BroadcastPrintf(printlevel, s);
    }

    @Override
    public void dprintf(String s) {
        Com.Printf(s);
    }

    @Override
    public void cprintf(edict_t ent, int printlevel, String s) {
        SV_GAME.PF_cprintf(ent, printlevel, s);
    }

    @Override
    public void centerprintf(edict_t ent, String s) {
        SV_GAME.PF_centerprintf(ent, s);
    }

    @Override
    public void sound(edict_t ent, int channel, int soundindex, float volume,
                      float attenuation, float timeofs) {
        SV_GAME.PF_StartSound(ent, channel, soundindex, volume, attenuation,
                timeofs);
    }

    @Override
    public void positioned_sound(float[] origin, edict_t ent, int channel,
                                 int soundinedex, float volume, float attenuation, float timeofs) {

        SV_SEND.SV_StartSound(origin, ent, channel, soundinedex, volume,
                attenuation, timeofs);
    }

    /*
     config strings hold all the index strings, the lightstyles,
     and misc data like the sky definition and cdtrack.
     All of the current configstrings are sent to clients when
     they connect, and changes are sent to all connected clients.
    */
    @Override
    public void configstring(int num, String string) {
        SV_GAME.PF_Configstring(num, string);
    }

    @Override
    public void error(String err) {
        Com.Error(Defines.ERR_FATAL, err);
    }

    @Override
    public void error(int level, String err) {
        Com.Error(level, err);
    }

    /* the *index functions create configstrings and some internal server state */
    @Override
    public int modelindex(String name) {
        return SV_INIT.SV_ModelIndex(name);
    }

    @Override
    public int soundindex(String name) {
        return SV_INIT.SV_SoundIndex(name);
    }

    @Override
    public int imageindex(String name) {
        return SV_INIT.SV_ImageIndex(name);
    }

    @Override
    public void setmodel(edict_t ent, String name) {
        SV_GAME.PF_setmodel(ent, name);
    }

    /* collision detection */
    @Override
    public trace_t trace(float[] start, float[] mins, float[] maxs,
                         float[] end, edict_t passent, int contentmask) {
        return SV_WORLD.SV_Trace(start, mins, maxs, end, passent, contentmask);
    }

    @Override
    public boolean inPHS(float[] p1, float[] p2) {
        return SV_GAME.PF_inPHS(p1, p2);
    }

    @Override
    public void SetAreaPortalState(int portalnum, boolean open) {
        CM.CM_SetAreaPortalState(portalnum, open);
    }

    @Override
    public boolean AreasConnected(int area1, int area2) {
        return CM.CM_AreasConnected(area1, area2);
    }

    /*
     an entity will never be sent to a client or used for collision
     if it is not passed to linkentity. If the size, position, or
     solidity changes, it must be relinked.
    */
    @Override
    public void linkentity(edict_t ent) {
        SV_WORLD.SV_LinkEdict(ent);
    }

    @Override
    public void unlinkentity(edict_t ent) {
        SV_WORLD.SV_UnlinkEdict(ent);
    }

    /* call before removing an interactive edict */
    @Override
    public int BoxEdicts(float[] mins, float[] maxs, edict_t list[],
                         int maxcount, int areatype) {
        return SV_WORLD.SV_AreaEdicts(mins, maxs, list, maxcount, areatype);
    }

    @Override
    public void Pmove(pmove_t pmove) {
        PMove.Pmove(pmove);
    }

    /*
     player movement code common with client prediction
     network messaging
    */
    @Override
    public void multicast(float[] origin, MulticastTypes to) {
        SV_SEND.SV_Multicast(origin, to);
    }

    @Override
    public void unicast(edict_t ent, boolean reliable) {
        SV_GAME.PF_Unicast(ent, reliable);
    }


    @Override
    public void WriteByte(int c) {
        SV_GAME.PF_WriteByte(c);
    }

    @Override
    public void WriteShort(int c) {
        SV_GAME.PF_WriteShort(c);
    }

    @Override
    public void WriteString(String s) {
        SV_GAME.PF_WriteString(s);
    }

    @Override
    public void WritePosition(float[] pos) {
        SV_GAME.PF_WritePos(pos);
    }

    /* some fractional bits */
    @Override
    public void WriteDir(float[] pos) {
        SV_GAME.PF_WriteDir(pos);
    }

    /* console variable interaction */
    @Override
    public cvar_t cvar(String var_name, String value, int flags) {
        return Cvar.Get(var_name, value, flags);
    }

    /* console variable interaction */
    @Override
    public cvar_t cvar_set(String var_name, String value) {
        return Cvar.Set(var_name, value);
    }

    /* console variable interaction */
    @Override
    public cvar_t cvar_forceset(String var_name, String value) {
        return Cvar.ForceSet(var_name, value);
    }


    /*
     add commands to the server console as if they were typed in
     for map changing, etc
    */
    @Override
    public void AddCommandString(String text) {
        Cbuf.AddText(text);
    }

    @Override
    public int getPointContents(float[] p) {
        return SV_WORLD.SV_PointContents(p);
    }

}