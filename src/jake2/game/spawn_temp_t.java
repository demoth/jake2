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
// $Id: spawn_temp_t.java,v 1.1 2004-07-07 19:59:26 hzi Exp $

package jake2.game;

import jake2.util.*;

public class spawn_temp_t {
	// world vars
	public String sky="";
	public float skyrotate;
	public float[] skyaxis = { 0, 0, 0 };
	
	public String nextmap="";

	public int lip;
	public int distance;
	public int height;
	
	public String noise="";
	public float pausetime;

	public String item="";
	public String gravity="";

	public float minyaw;
	public float maxyaw;
	public float minpitch;
	public float maxpitch;

	public boolean set(String key, String value) {
		if (key.equals("lip")) {
			lip=Lib.atoi(value);
			return true;
		} // F_INT, FFL_SPAWNTEMP),
		
		if (key.equals("distance")) {
			distance=Lib.atoi(value);
			return true;
		} // F_INT, FFL_SPAWNTEMP),
		
		if (key.equals("height")) {
			height=Lib.atoi(value);
			return true;
		} // F_INT, FFL_SPAWNTEMP),
		
		if (key.equals("noise")) {
			noise = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING, FFL_SPAWNTEMP),
		
		if (key.equals("pausetime")) {
			pausetime = Lib.atof(value);
			return true;
		} // F_FLOAT, FFL_SPAWNTEMP),
		
		if (key.equals("item")) {
			item = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING, FFL_SPAWNTEMP),
		
		if (key.equals("gravity")) {
			 gravity = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING, FFL_SPAWNTEMP),
		
		if (key.equals("sky")) {
			sky = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING, FFL_SPAWNTEMP),
		
		if (key.equals("skyrotate")) {
			skyrotate=Lib.atof(value);
			return true;
		} // F_FLOAT, FFL_SPAWNTEMP),
		
		if (key.equals("skyaxis")) {
			skyaxis=Lib.atov(value);
			return true;
		} // F_VECTOR, FFL_SPAWNTEMP),
		
		if (key.equals("minyaw")) {
			minyaw=Lib.atof(value);
			return true;
		} // F_FLOAT, FFL_SPAWNTEMP),
		
		if (key.equals("maxyaw")) {
			maxyaw=Lib.atof(value);
			return true;
		} // F_FLOAT, FFL_SPAWNTEMP),
		
		if (key.equals("minpitch")) {
			minpitch = Lib.atof(value);
			return true;
		} // F_FLOAT, FFL_SPAWNTEMP),
		
		if (key.equals("maxpitch")) {
			maxpitch = Lib.atof(value);
			return true;
		} // F_FLOAT, FFL_SPAWNTEMP),
		
		if (key.equals("nextmap")) {
			nextmap  = GameSpawn.ED_NewString(value);
			return true;
		} // F_LSTRING, FFL_SPAWNTEMP),

		return false;
	}
}
