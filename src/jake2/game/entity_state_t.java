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


package jake2.game;

public class entity_state_t
{
	//	entity_state_t is the information conveyed from the server
	//	in an update message about entities that the client will
	//	need to render in some way

	int number; // edict index

	float [] origin = {0,0,0};
	float [] angles= {0,0,0};
	float [] old_origin= {0,0,0}; // for lerping
	int modelindex;
	int modelindex2, modelindex3, modelindex4; // weapons, CTF flags, etc.
	int frame;
	int skinnum;
	int effects; // PGM - we're filling it, so it needs to be unsigned
	int renderfx;
	int solid; 
	// for client side prediction, 8*(bits 0-4) is x/y radius
	// 8*(bits 5-9) is z down distance, 8(bits10-15) is z up
	// gi.linkentity sets this properly
	int sound; // for looping sounds, to guarantee shutoff
	int event; // impulse events -- muzzle flashes, footsteps, etc
	// events only go out for a single frame, they
	// are automatically cleared each frame

}
