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


// Created on 08.11.2003 by RST.
// $Id: entity_state_t.java,v 1.3 2003-11-29 19:26:33 rst Exp $

package jake2.game;

public class entity_state_t
{
	//	entity_state_t is the information conveyed from the server
	//	in an update message about entities that the client will
	//	need to render in some way

	public int number; // edict index

	public float [] origin = {0,0,0};
	public float [] angles= {0,0,0};
	public float [] old_origin= {0,0,0}; // for lerping
	public int modelindex;
	public int modelindex2, modelindex3, modelindex4; // weapons, CTF flags, etc.
	public int frame;
	public int skinnum;
	public int effects; // PGM - we're filling it, so it needs to be unsigned
	public int renderfx;
	public int solid; 
	// for client side prediction, 8*(bits 0-4) is x/y radius
	// 8*(bits 5-9) is z down distance, 8(bits10-15) is z up
	// gi.linkentity sets this properly
	public int sound; // for looping sounds, to guarantee shutoff
	public int event; // impulse events -- muzzle flashes, footsteps, etc
	// events only go out for a single frame, they
	// are automatically cleared each frame
}
