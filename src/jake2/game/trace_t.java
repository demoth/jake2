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

//a trace is returned when a box is swept through the world
public class trace_t {
	boolean allsolid; // if true, plane is not valid
	boolean startsolid; // if true, the initial point was in a solid area
	float fraction; // time completed, 1.0 = didn't hit anything
	float[] endpos= new float[3]; // final position
	cplane_t plane; // surface normal at impact
	csurface_t surface; // surface hit
	int contents; // contents on other side of surface hit
	edict_t ent; // not set by CM_*() functions
}
