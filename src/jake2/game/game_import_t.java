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

// Created on 31.10.2003 by RST.
// $Id: game_import_t.java,v 1.6 2004-01-08 22:38:16 rst Exp $

package jake2.game;

import jake2.Defines;
import jake2.qcommon.Com;
import jake2.qcommon.Cvar;
import jake2.server.SV;

//
//	collection of functions provided by the main engine
//
public class game_import_t {

	// R S T:    SEE   SV_InitGameProgs() ! 


	// special messages
	public void bprintf(int printlevel, String s) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void dprintf(String s) {
		Com.Printf(s);
	}
	public void cprintf(edict_t ent, int printlevel, String s) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void centerprintf(edict_t ent, String s) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void sound(edict_t ent, int channel, int soundindex, float volume, float attenuation, float timeofs) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void positioned_sound(
		float[] origin,
		edict_t[] ent,
		int channel,
		int soundinedex,
		float volume,
		float attenuation,
		float timeofs) {
			Com.Error(Defines.ERR_FATAL,"not implemented!");
	}

	// config strings hold all the index strings, the lightstyles,
	// and misc data like the sky definition and cdtrack.
	// All of the current configstrings are sent to clients when
	// they connect, and changes are sent to all connected clients.
	public void configstring(int num, String string) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void error(String err) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void error(int level, String err) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}

	// the *index functions create configstrings and some internal server state
	public int modelindex(String name) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return 0;
	}
	public int soundindex(String name) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return 0;
	}
	public int imageindex(String name) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return 0;
	}
	void setmodel(edict_t ent, String name) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}

	// collision detection
	public trace_t trace(float[] start, float[] mins, float[] maxs, float[] end, edict_t passent, int contentmask) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return null;
	}

	public pmove_t.PointContentsAdapter pointcontents;
	public boolean inPVS(float[] p1, float[] p2) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return false;
	}
	public boolean inPHS(float[] p1, float[] p2) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return false;
	}
	public void SetAreaPortalState(int portalnum, boolean open) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public boolean AreasConnected(int area1, int area2) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return false;
	}

	// an entity will never be sent to a client or used for collision
	// if it is not passed to linkentity.  If the size, position, or
	// solidity changes, it must be relinked.
	public void linkentity(edict_t ent) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void unlinkentity(edict_t ent) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	// call before removing an interactive edict
	public int BoxEdicts(float[] mins, float[] maxs, edict_t list[], int maxcount, int areatype) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return 0;
	}
	public void Pmove(pmove_t pmove) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	// player movement code common with client prediction

	// network messaging
	public void multicast(float[] origin, int to) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void unicast(edict_t ent, boolean reliable) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void WriteChar(int c) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void WriteByte(int c) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void WriteShort(int c) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void WriteLong(int c) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void WriteFloat(float f) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void WriteString(String s) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void WritePosition(float[] pos) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	} // some fractional bits
	public void WriteDir(float[] pos) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	} // single byte encoded, very coarse
	public void WriteAngle(float f) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}

	// managed memory allocation
	public void TagMalloc(int size, int tag) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void TagFree(Object block) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
	public void FreeTags(int tag) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}

	// console variable interaction
	
	public cvar_t cvar(String var_name, String value, int flags) {
		return Cvar.Get(var_name, value, flags);
		//return null;
	}
	public cvar_t cvar_set(String var_name, String value) {
		return Cvar.Set(var_name, value);
		//return null;
	}
	public cvar_t cvar_forceset(String var_name, String value) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return null;
	}

	// ClientCommand and ServerCommand parameter access
	public int argc() {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return 0;
	}
	public String argv(int n) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return null;
	}
	// concatenation of all argv >= 1

	public String args() {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
		return null;
	}

	// add commands to the server console as if they were typed in
	// for map changing, etc
	public void AddCommandString(String text) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}

	public void DebugGraph(float value, int color) {
		Com.Error(Defines.ERR_FATAL,"not implemented!");
	}
}
