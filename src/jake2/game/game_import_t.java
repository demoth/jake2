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
// $Id: game_import_t.java,v 1.3 2003-12-04 21:04:35 rst Exp $

package jake2.game;

//
//	functions provided by the main engine
//
public class game_import_t {

	// overload them like in the awt Adapter-Tricks

	// special messages
	public void bprintf(int printlevel, String s) {
	}
	public void dprintf(String s) {
	}
	public void cprintf(edict_t ent, int printlevel, String s) {
	}
	public void centerprintf(edict_t ent, String s) {
	}
	public void sound(
		edict_t ent,
		int channel,
		int soundindex,
		float volume,
		float attenuation,
		float timeofs) {
	}
	public void positioned_sound(
		float[] origin,
		edict_t ent[],
		int channel,
		int soundinedex,
		float volume,
		float attenuation,
		float timeofs) {
	}

	// config strings hold all the index strings, the lightstyles,
	// and misc data like the sky definition and cdtrack.
	// All of the current configstrings are sent to clients when
	// they connect, and changes are sent to all connected clients.
	public void configstring(int num, String string) {
	}
	public void error(String err) {
	}
	public void error(int level, String err) {
	}

	// the *index functions create configstrings and some internal server state
	public int modelindex(String name) {
		return 0;
	}
	public int soundindex(String name) {
		return 0;
	}
	public int imageindex(String name) {
		return 0;
	}
	void setmodel(edict_t ent, String name) {
	}

	// collision detection
	public trace_t trace(
		float[] start,
		float[] mins,
		float[] maxs,
		float[] end,
		edict_t passent,
		int contentmask) {
		return null;
	}
	public int pointcontents(float[] point) {
		return 0;
	}
	public boolean inPVS(float[] p1, float[] p2) {
		return false;
	}
	public boolean inPHS(float[] p1, float[] p2) {
		return false;
	}
	public void SetAreaPortalState(int portalnum, boolean open) {
	}
	public boolean AreasConnected(int area1, int area2) {
		return false;
	}

	// an entity will never be sent to a client or used for collision
	// if it is not passed to linkentity.  If the size, position, or
	// solidity changes, it must be relinked.
	public void linkentity(edict_t ent) {
	}
	public void unlinkentity(edict_t ent) {
	}
	// call before removing an interactive edict
	public int BoxEdicts(float[] mins, float[] maxs, edict_t list[], int maxcount, int areatype) {
		return 0;
	}
	public void Pmove(pmove_t pmove) {
	}
	// player movement code common with client prediction

	// network messaging
	public void multicast(float[] origin, int to) {
	}
	public void unicast(edict_t ent, boolean reliable) {
	}
	public void WriteChar(int c) {
	}
	public void WriteByte(int c) {
	}
	public void WriteShort(int c) {
	}
	public void WriteLong(int c) {
	}
	public void WriteFloat(float f) {
	}
	public void WriteString(String s) {
	}
	public void WritePosition(float[] pos) {
	} // some fractional bits
	public void WriteDir(float[] pos) {
	} // single byte encoded, very coarse
	public void WriteAngle(float f) {
	}

	// managed memory allocation
	public void TagMalloc(int size, int tag) {
	}
	public void TagFree(Object block) {
	}
	public void FreeTags(int tag) {
	}

	// console variable interaction
	public cvar_t cvar(String var_name, String value, int flags) {
		return null;
	}
	public cvar_t cvar_set(String var_name, String value) {
		return null;
	}
	public cvar_t cvar_forceset(String var_name, String value) {
		return null;
	}

	// ClientCommand and ServerCommand parameter access
	public int argc() {
		return 0;
	}
	public String argv(int n) {
		return null;
	}
	// concatenation of all argv >= 1

	public String args() {
		return null;
	}

	// add commands to the server console as if they were typed in
	// for map changing, etc
	public void AddCommandString(String text) {
	}

	public void DebugGraph(float value, int color) {
	}
}
