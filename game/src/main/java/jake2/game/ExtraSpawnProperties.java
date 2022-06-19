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

import jake2.qcommon.util.Lib;

/**
 * spawn_temp_t
 * Used to hold entity field values that
 * can be set from the editor, but aren't actualy present
 * in edict_t during gameplay.
 * These values are used during the spawn function, after that it can be safely cleared.
 */
public class ExtraSpawnProperties {
	// world spawn vars
	public String sky="";
	public float skyrotate;
	public float[] skyaxis = { 0, 0, 0 };
	// can also be used by trigger
	public String gravity="";

	public String nextmap="";

	public int lip;
	public int distance;
	public int height;
	
	public String noise="";
	public float pausetime;

	public String item="";

	// Turret
	public float minyaw;
	public float maxyaw;
	public float minpitch;
	public float maxpitch;

	public boolean set(String key, String value) {
		switch (key) {
			case "lip":
				lip = Lib.atoi(value);
				return true;
			case "distance":
				distance = Lib.atoi(value);
				return true;
			case "height":
				height = Lib.atoi(value);
				return true;
			case "noise":
				noise = Lib.decodeBackslash(value);
				return true;
			case "pausetime":
				pausetime = Lib.atof(value);
				return true;
			case "item":
				item = Lib.decodeBackslash(value);
				return true;
			case "gravity":
				gravity = Lib.decodeBackslash(value);
				return true;
			case "sky":
				sky = Lib.decodeBackslash(value);
				return true;
			case "skyrotate":
				skyrotate = Lib.atof(value);
				return true;
			case "skyaxis":
				skyaxis = Lib.atov(value);
				return true;
			case "minyaw":
				minyaw = Lib.atof(value);
				return true;
			case "maxyaw":
				maxyaw = Lib.atof(value);
				return true;
			case "minpitch":
				minpitch = Lib.atof(value);
				return true;
			case "maxpitch":
				maxpitch = Lib.atof(value);
				return true;
			case "nextmap":
				nextmap = Lib.decodeBackslash(value);
				return true;
			default:
				return false;
		}
	}
}
