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

//	plane_t structure
public class cplane_t
{
	// Fixme: just for testuing... both representations of a vector normal, vec3t and float[]
	float normal[]= new float[3];
	float dist;
	byte type; // for fast side tests
	byte signbits; // signx + (signy<<1) + (signz<<1)
	byte pad[]= new byte[2];

}
