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
// $Id: player_state_t.java,v 1.3 2004-07-09 06:50:49 hzi Exp $

package jake2.game;

import java.io.IOException;
import java.nio.ByteBuffer;

import jake2.*;
import jake2.*;
import jake2.qcommon.Com;
import jake2.util.Lib;
import jake2.util.Math3D;

public class player_state_t {
	//	player_state_t is the information needed in addition to pmove_state_t
	//	to rendered a view.  There will only be 10 player_state_t sent each second,
	//	but the number of pmove_state_t changes will be reletive to client
	//	frame rates

	public pmove_state_t pmove = new pmove_state_t(); // for prediction

	// these fields do not need to be communicated bit-precise
	public float[] viewangles = { 0, 0, 0 }; // for fixed views
	public float[] viewoffset = { 0, 0, 0 }; // add to pmovestate->origin
	public float[] kick_angles = { 0, 0, 0 }; // add to view direction to get render angles

	// set by weapon kicks, pain effects, etc
	public float[] gunangles = { 0, 0, 0 };
	public float[] gunoffset = { 0, 0, 0 };
	public int gunindex;
	public int gunframe;

	public float blend[] = new float[4]; // rgba full screen effect

	public float fov; // horizontal field of view

	public int rdflags; // refdef flags

	public short stats[] = new short[Defines.MAX_STATS];

	/**
	 * 
	 */
	private static player_state_t prototype = new  player_state_t();
	
	public void clear() {
		this.set(prototype);
	}
	
	public player_state_t getClone()
	{
		return new player_state_t().set(this);
	}
	
	public player_state_t set(player_state_t from)
	{		
		pmove.set(from.pmove);
		Math3D.VectorCopy(from.viewangles, viewangles);
		Math3D.VectorCopy(from.viewoffset,viewoffset);
		Math3D.VectorCopy(from.kick_angles, kick_angles);

		Math3D.VectorCopy(from.gunangles,gunangles);
		Math3D.VectorCopy(from.gunoffset, gunoffset);

		gunindex = from.gunindex;
		gunframe = from.gunframe;

		blend[0] = from.blend[0];
		blend[1] = from.blend[1];
		blend[2] = from.blend[2];
		blend[3] = from.blend[3];
		
		fov = from.fov;
		rdflags = from.rdflags;
		
		//stats = new short[Defines.MAX_STATS];
		System.arraycopy(from.stats, 0, stats,0, Defines.MAX_STATS);
		
		return this;
	}

	public void load(ByteBuffer bb) throws IOException {
		pmove.load(bb);

		viewangles[0] = bb.getFloat();
		viewangles[1] = bb.getFloat();
		viewangles[2] = bb.getFloat();

		viewoffset[0] = bb.getFloat();
		viewoffset[1] = bb.getFloat();
		viewoffset[2] = bb.getFloat();

		kick_angles[0] = bb.getFloat();
		kick_angles[1] = bb.getFloat();
		kick_angles[2] = bb.getFloat();

		gunangles[0] = bb.getFloat();
		gunangles[1] = bb.getFloat();
		gunangles[2] = bb.getFloat();

		gunoffset[0] = bb.getFloat();
		gunoffset[1] = bb.getFloat();
		gunoffset[2] = bb.getFloat();

		gunindex = bb.getInt();
		gunframe = bb.getInt();

		blend[0] = bb.getFloat();
		blend[1] = bb.getFloat();
		blend[2] = bb.getFloat();
		blend[3] = bb.getFloat();

		fov = bb.getFloat();

		rdflags = bb.getInt();

		for (int n = 0; n < Defines.MAX_STATS; n++)
			stats[n] = bb.getShort();

	}

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

		for (int n = 0; n < Defines.MAX_STATS; n++)
			System.out.println("stats[" + n + "]: " + stats[n]);
	}
}
