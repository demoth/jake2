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

package jake2.game;

//
//	functions provided by the main engine
//
public class game_import_t {

	// overload them like in the awt Adapter-Tricks

	// special messages
	void bprintf(int printlevel, String s) {
	}
	void dprintf(String s) {
	}
	void cprintf(edict_t ent, int printlevel, String s) {
	}
	void centerprintf(edict_t ent, String s) {
	}
	void sound(
		edict_t ent,
		int channel,
		int soundindex,
		float volume,
		float attenuation,
		float timeofs) {
	}
	void positioned_sound(
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
	void configstring(int num, String string) {
	}
	void error(String err) {
	}
	void error(int level, String err) {
	}

	// the *index functions create configstrings and some internal server state
	int modelindex(String name) {
		return 0;
	}
	int soundindex(String name) {
		return 0;
	}
	int imageindex(String name) {
		return 0;
	}
	void setmodel(edict_t ent, String name) {
	}

	// collision detection
	trace_t trace(
		float[] start,
		float[] mins,
		float[] maxs,
		float[] end,
		edict_t passent,
		int contentmask) {
		return null;
	}
	int pointcontents(float[] point) {
		return 0;
	}
	boolean inPVS(float[] p1, float[] p2) {
		return false;
	}
	boolean inPHS(float[] p1, float[] p2) {
		return false;
	}
	void SetAreaPortalState(int portalnum, boolean open) {
	}
	boolean AreasConnected(int area1, int area2) {
		return false;
	}

	// an entity will never be sent to a client or used for collision
	// if it is not passed to linkentity.  If the size, position, or
	// solidity changes, it must be relinked.
	void linkentity(edict_t ent) {
	}
	void unlinkentity(edict_t ent) {
	}
	// call before removing an interactive edict
	int BoxEdicts(float[] mins, float[] maxs, edict_t list[], int maxcount, int areatype) {
		return 0;
	}
	void Pmove(pmove_t pmove) {
	}
	// player movement code common with client prediction

	// network messaging
	void multicast(float[] origin, int to) {
	}
	void unicast(edict_t ent, boolean reliable) {
	}
	void WriteChar(int c) {
	}
	void WriteByte(int c) {
	}
	void WriteShort(int c) {
	}
	void WriteLong(int c) {
	}
	void WriteFloat(float f) {
	}
	void WriteString(String s) {
	}
	void WritePosition(float[] pos) {
	} // some fractional bits
	void WriteDir(float[] pos) {
	} // single byte encoded, very coarse
	void WriteAngle(float f) {
	}

	// managed memory allocation
	void TagMalloc(int size, int tag) {
	}
	void TagFree(Object block) {
	}
	void FreeTags(int tag) {
	}

	// console variable interaction
	cvar_t cvar(String var_name, String value, int flags) {
		return null;
	}
	cvar_t cvar_set(String var_name, String value) {
		return null;
	}
	cvar_t cvar_forceset(String var_name, String value) {
		return null;
	}

	// ClientCommand and ServerCommand parameter access
	int argc() {
		return 0;
	}
	String argv(int n) {
		return null;
	}
	// concatenation of all argv >= 1

	String args() {
		return null;
	}

	// add commands to the server console as if they were typed in
	// for map changing, etc
	void AddCommandString(String text) {
	}

	void DebugGraph(float value, int color) {
	}
}
