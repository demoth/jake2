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
// $Id: spawn_temp_t.java,v 1.4 2003-11-29 13:28:28 rst Exp $

package jake2.game;

public class spawn_temp_t {
	// world vars
	//TODO: is String ok here ?
	String sky;
	float skyrotate;
	float[] skyaxis= { 0, 0, 0 };
	//TODO: is String ok here ?
	String nextmap;

	int lip;
	int distance;
	int height;
	//TODO: does String work here ?
	String noise;
	float pausetime;
	//TODO: does String work here ?
	String item;
	//TODO: does String work here ?
	String gravity;

	float minyaw;
	float maxyaw;
	float minpitch;
	float maxpitch;
}
