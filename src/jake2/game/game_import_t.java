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
// $Id: game_import_t.java,v 1.4 2004-09-22 19:22:00 salomo Exp $
package jake2.game;

import jake2.Defines;
import jake2.client.SCR;
import jake2.qcommon.CM;
import jake2.qcommon.Cbuf;
import jake2.qcommon.Com;
import jake2.qcommon.Cvar;
import jake2.qcommon.PMove;
import jake2.server.SV_GAME;
import jake2.server.SV_INIT;
import jake2.server.SV_SEND;
import jake2.server.SV_WORLD;

//
//	collection of functions provided by the main engine
//
public class game_import_t {
    // special messages
    public void bprintf(int printlevel, String s) {
        SV_SEND.SV_BroadcastPrintf(printlevel, s);
    }

    public void dprintf(String s) {
        SV_GAME.PF_dprintf(s);
    }

    public void cprintf(edict_t ent, int printlevel, String s) {
        SV_GAME.PF_cprintf(ent, printlevel, s);
    }

    public void centerprintf(edict_t ent, String s) {
        SV_GAME.PF_centerprintf(ent, s);
    }

    public void sound(edict_t ent, int channel, int soundindex, float volume,
            float attenuation, float timeofs) {
        SV_GAME.PF_StartSound(ent, channel, soundindex, volume, attenuation,
                timeofs);
    }

    public void positioned_sound(float[] origin, edict_t ent, int channel,
            int soundinedex, float volume, float attenuation, float timeofs) {

        SV_SEND.SV_StartSound(origin, ent, channel, soundinedex, volume,
                attenuation, timeofs);
    }

    // config strings hold all the index strings, the lightstyles,
    // and misc data like the sky definition and cdtrack.
    // All of the current configstrings are sent to clients when
    // they connect, and changes are sent to all connected clients.
    public void configstring(int num, String string) {
        //Com.Error(Defines.ERR_FATAL,"method is not implemented!");
        SV_GAME.PF_Configstring(num, string);
    }

    public void error(String err) {
        Com.Error(Defines.ERR_FATAL, err);
    }

    public void error(int level, String err) {
        SV_GAME.PF_error(level, err);
    }

    // the *index functions create configstrings and some internal server state
    public int modelindex(String name) {
        return SV_INIT.SV_ModelIndex(name);
    }

    public int soundindex(String name) {
        return SV_INIT.SV_SoundIndex(name);
    }

    public int imageindex(String name) {
        return SV_INIT.SV_ImageIndex(name);
    }

    public void setmodel(edict_t ent, String name) {
        SV_GAME.PF_setmodel(ent, name);
    }

    // collision detection
    public trace_t trace(float[] start, float[] mins, float[] maxs,
            float[] end, edict_t passent, int contentmask) {
        return SV_WORLD.SV_Trace(start, mins, maxs, end, passent, contentmask);
    }

    public pmove_t.PointContentsAdapter pointcontents;

    public boolean inPVS(float[] p1, float[] p2) {
        return SV_GAME.PF_inPVS(p1, p2);
    }

    public boolean inPHS(float[] p1, float[] p2) {
        return SV_GAME.PF_inPHS(p1, p2);
    }

    public void SetAreaPortalState(int portalnum, boolean open) {
        CM.CM_SetAreaPortalState(portalnum, open);
    }

    public boolean AreasConnected(int area1, int area2) {
        return CM.CM_AreasConnected(area1, area2);
    }

    // an entity will never be sent to a client or used for collision
    // if it is not passed to linkentity. If the size, position, or
    // solidity changes, it must be relinked.
    public void linkentity(edict_t ent) {
        SV_WORLD.SV_LinkEdict(ent);
    }

    public void unlinkentity(edict_t ent) {
        SV_WORLD.SV_UnlinkEdict(ent);
    }

    // call before removing an interactive edict
    public int BoxEdicts(float[] mins, float[] maxs, edict_t list[],
            int maxcount, int areatype) {
        return SV_WORLD.SV_AreaEdicts(mins, maxs, list, maxcount, areatype);
    }

    public void Pmove(pmove_t pmove) {
        PMove.Pmove(pmove);
    }

    // player movement code common with client prediction
    // network messaging
    public void multicast(float[] origin, int to) {
        SV_SEND.SV_Multicast(origin, to);
    }

    public void unicast(edict_t ent, boolean reliable) {
        SV_GAME.PF_Unicast(ent, reliable);
    }

    public void WriteChar(int c) {
        SV_GAME.PF_WriteChar(c);
    }

    public void WriteByte(int c) {
        SV_GAME.PF_WriteByte(c);
    }

    public void WriteShort(int c) {
        SV_GAME.PF_WriteShort(c);
    }

    public void WriteLong(int c) {
        SV_GAME.PF_WriteLong(c);
    }

    public void WriteFloat(float f) {
        SV_GAME.PF_WriteFloat(f);
    }

    public void WriteString(String s) {
        SV_GAME.PF_WriteString(s);
    }

    public void WritePosition(float[] pos) {
        SV_GAME.PF_WritePos(pos);
    }

    // some fractional bits
    public void WriteDir(float[] pos) {
        SV_GAME.PF_WriteDir(pos);
    }

    // single byte encoded, very coarse
    public void WriteAngle(float f) {
        Com.Error(Defines.ERR_FATAL, "method is not implemented!");
    }

    // managed memory allocation
    public void TagMalloc(int size, int tag) {
        Com.Error(Defines.ERR_FATAL, "method is not implemented!");
    }

    public void TagFree(Object block) {
        Com.Error(Defines.ERR_FATAL, "method is not implemented!");
    }

    public void FreeTags(int tag) {
        Com.Error(Defines.ERR_FATAL, "method is not implemented!");
    }

    // console variable interaction
    public cvar_t cvar(String var_name, String value, int flags) {
        return Cvar.Get(var_name, value, flags);
    }

    public cvar_t cvar_set(String var_name, String value) {
        return Cvar.Set(var_name, value);
        //return null;
    }

    public cvar_t cvar_forceset(String var_name, String value) {
        return Cvar.ForceSet(var_name, value);
    }

    // ClientCommand and ServerCommand parameter access
    public int argc() {
        return Cmd.Argc();
    }

    public String argv(int n) {
        return Cmd.Argv(n);
    }

    // concatenation of all argv >= 1

    public String args() {
        return Cmd.Args();
    }

    // add commands to the server console as if they were typed in
    // for map changing, etc
    public void AddCommandString(String text) {
        Cbuf.AddText(text);
    }

    public void DebugGraph(float value, int color) {
        SCR.DebugGraph(value, color);
    }
}