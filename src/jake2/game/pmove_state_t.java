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
// $Id: pmove_state_t.java,v 1.4 2003-12-28 16:53:00 rst Exp $
package jake2.game;

public class pmove_state_t {
	//	this structure needs to be communicated bit-accurate
	//	from the server to the client to guarantee that
	//	prediction stays in sync, so no floats are used.
	//	if any part of the game code modifies this struct, it
	//	will result in a prediction error of some degree.

	public int pm_type;
 
	public short origin[]= {0,0,0}; // 12.3
	public short velocity[]= {0,0,0}; // 12.3
	public byte pm_flags; // ducked, jump_held, etc
	public byte pm_time; // each unit = 8 ms
	public short gravity;
	public short delta_angles[]= {0,0,0}; // add to command angles to get view direction
	// changed by spawns, rotating objects, and teleporters
}