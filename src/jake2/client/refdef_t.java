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
// $Id: refdef_t.java,v 1.2 2004-07-09 06:50:50 hzi Exp $

package jake2.client;

public class refdef_t {
	public int		x, y, width, height;// in virtual screen coordinates
	public float		fov_x, fov_y;
	public float		vieworg[] ={0,0,0};
	public float		viewangles[]={0,0,0};
	public float		blend[]={0,0,0,0};			// rgba 0-1 full screen blend
	public float		time;				// time is uesed to auto animate
	public int		rdflags;			// RDF_UNDERWATER, etc

	public byte		areabits[];			// if not NULL, only areas with set bits will be drawn

	public lightstyle_t	lightstyles[];	// [MAX_LIGHTSTYLES]

	public int		num_entities;
	public entity_t	entities[];

	public int		num_dlights;
	public dlight_t	dlights[];

	public int		num_particles;
	//public particle_t	particles[];
}
