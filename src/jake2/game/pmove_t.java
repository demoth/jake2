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
// $Id: pmove_t.java,v 1.3 2003-11-29 13:28:29 rst Exp $

package jake2.game;


public class pmove_t
{
	// state (in / out)
	pmove_state_t s;

	// command (in)
	usercmd_t cmd;
	boolean snapinitial; // if s has been changed outside pmove

	// results (out)
	int numtouch;
	edict_t touchents[]= new edict_t[gamedefs.MAXTOUCH];

	float []  viewangles ={0,0,0}; // clamped
	float viewheight;

	float []  mins ={0,0,0}, maxs ={0,0,0}; // bounding box size

	edict_t groundentity;
	int watertype;
	int waterlevel;

	// callbacks to test the world
	trace_t trace(float []  start, float []  mins, float []  maxs, float []  end)
	{
		return null;
	}
	int pointcontents(float []  point)
	{
		return 0;
	}
}
