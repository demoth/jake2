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

// Created on 20.11.2003 by RST.

package jake2.client;

import jake2.game.*;

public class refimport_t {
	int		x, y, width, height;// in virtual screen coordinates
	float		fov_x, fov_y;
	float		vieworg[] ={0,0,0};
	float		viewangles[]={0,0,0};
	float		blend[]={0,0,0,0};			// rgba 0-1 full screen blend
	float		time;				// time is uesed to auto animate
	int		rdflags;			// RDF_UNDERWATER, etc

	byte		areabits[];			// if not NULL, only areas with set bits will be drawn

	lightstyle_t lightstyles;	// [MAX_LIGHTSTYLES]

	int		num_entities;
	entity_t	entities[];

	int		num_dlights;
	dlight_t	dlights;

	int		num_particles;
	particle_t	particles;

}
