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

package jake2.qcommon;

import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.io.IOException;
import java.io.RandomAccessFile;

/** 
 	Player_state_t is the information needed in addition to pmove_state_t
	to rendered a view.  There will only be 10 player_state_t sent each second,
	but the number of pmove_state_t changes will be relative to client
	frame rates.
*/

public class player_state_t {

	public pmove_state_t pmove= new pmove_state_t(); // for prediction

	// these fields do not need to be communicated bit-precise
	public float[] viewangles= { 0, 0, 0 }; // for fixed views
	public float[] viewoffset= { 0, 0, 0 }; // add to pmovestate->origin
	public float[] kick_angles= { 0, 0, 0 }; // add to view direction to get render angles

	// set by weapon kicks, pain effects, etc
	public float[] gunangles= { 0, 0, 0 };
	public float[] gunoffset= { 0, 0, 0 };
	public int gunindex;
	public int gunframe;

	public float blend[]= new float[4]; // rgba full screen effect

	public float fov; // horizontal field of view

	public int rdflags; // refdef flags

	public short stats[]= new short[Defines.MAX_STATS];

	/** Lets cleverly reset the structure. */
	private static player_state_t prototype= new player_state_t();

	/** Clears the player_state.*/
	public void clear() {
		this.set(prototype);
	}

	/** Clones the object.*/
	public player_state_t getClone() {
		return new player_state_t().set(this);
	}

	/** Copies the player state data. */
	public player_state_t set(player_state_t from) {
		pmove.set(from.pmove);
		Math3D.VectorCopy(from.viewangles, viewangles);
		Math3D.VectorCopy(from.viewoffset, viewoffset);
		Math3D.VectorCopy(from.kick_angles, kick_angles);

		Math3D.VectorCopy(from.gunangles, gunangles);
		Math3D.VectorCopy(from.gunoffset, gunoffset);

		gunindex= from.gunindex;
		gunframe= from.gunframe;

		blend[0]= from.blend[0];
		blend[1]= from.blend[1];
		blend[2]= from.blend[2];
		blend[3]= from.blend[3];

		fov= from.fov;
		rdflags= from.rdflags;

		System.arraycopy(from.stats, 0, stats, 0, Defines.MAX_STATS);

		return this;
	}

	/** Reads a player_state from a file.*/
	public void load(RandomAccessFile f) throws IOException {
		pmove.load(f);

		viewangles[0]= f.readFloat();
		viewangles[1]= f.readFloat();
		viewangles[2]= f.readFloat();

		viewoffset[0]= f.readFloat();
		viewoffset[1]= f.readFloat();
		viewoffset[2]= f.readFloat();

		kick_angles[0]= f.readFloat();
		kick_angles[1]= f.readFloat();
		kick_angles[2]= f.readFloat();

		gunangles[0]= f.readFloat();
		gunangles[1]= f.readFloat();
		gunangles[2]= f.readFloat();

		gunoffset[0]= f.readFloat();
		gunoffset[1]= f.readFloat();
		gunoffset[2]= f.readFloat();

		gunindex= f.readInt();
		gunframe= f.readInt();

		blend[0]= f.readFloat();
		blend[1]= f.readFloat();
		blend[2]= f.readFloat();
		blend[3]= f.readFloat();

		fov= f.readFloat();

		rdflags= f.readInt();

		for (int n= 0; n < Defines.MAX_STATS; n++)
			stats[n]= f.readShort();
	}

	/** Writes a player_state to a file.*/
	public void write(RandomAccessFile f) throws IOException {
		pmove.write(f);

		f.writeFloat(viewangles[0]);
		f.writeFloat(viewangles[1]);
		f.writeFloat(viewangles[2]);

		f.writeFloat(viewoffset[0]);
		f.writeFloat(viewoffset[1]);
		f.writeFloat(viewoffset[2]);

		f.writeFloat(kick_angles[0]);
		f.writeFloat(kick_angles[1]);
		f.writeFloat(kick_angles[2]);

		f.writeFloat(gunangles[0]);
		f.writeFloat(gunangles[1]);
		f.writeFloat(gunangles[2]);

		f.writeFloat(gunoffset[0]);
		f.writeFloat(gunoffset[1]);
		f.writeFloat(gunoffset[2]);

		f.writeInt(gunindex);
		f.writeInt(gunframe);

		f.writeFloat(blend[0]);
		f.writeFloat(blend[1]);
		f.writeFloat(blend[2]);
		f.writeFloat(blend[3]);

		f.writeFloat(fov);

		f.writeInt(rdflags);

		for (int n= 0; n < Defines.MAX_STATS; n++)
			f.writeShort(stats[n]);
	}

	/** Prints the player state. */
	public void dump() {
		pmove.dump();

		Lib.printv("viewangles", viewangles);
		Lib.printv("viewoffset", viewoffset);
		Lib.printv("kick_angles", kick_angles);
		Lib.printv("gunangles", gunangles);
		Lib.printv("gunoffset", gunoffset);

		Com.Println("gunindex: " + gunindex);
		Com.Println("gunframe: " + gunframe);

		Lib.printv("blend", blend);

		Com.Println("fov: " + fov);

		Com.Println("rdflags: " + rdflags);

		for (int n= 0; n < Defines.MAX_STATS; n++)
			System.out.println("stats[" + n + "]: " + stats[n]);
	}
}
