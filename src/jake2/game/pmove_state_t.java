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
// $Id: pmove_state_t.java,v 1.5 2003-12-28 19:52:35 rst Exp $
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
	
	public boolean equals(pmove_state_t p2)
	{
		if (
		pm_type == p2.pm_type &&
		origin[0] == p2.origin[0] &&
		origin[1] == p2.origin[1] &&
		origin[2] == p2.origin[2] &&
		
		velocity[0] == p2.velocity[0] &&
		velocity[1] == p2.velocity[1] &&
		velocity[2] == p2.origin[2] &&
		
		pm_flags == p2.pm_flags &&
		pm_time == p2.pm_time &&
		gravity == gravity &&
		
		
		delta_angles[0] == p2.delta_angles[0] &&
		delta_angles[1] == p2.delta_angles[1] &&
		delta_angles[2] == p2.origin[2]) return true;
		
		return false;
		
	}
}