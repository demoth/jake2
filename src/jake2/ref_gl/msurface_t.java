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

package jake2.ref_gl;

import jake2.game.*;
import jake2.qcommon.*;

public class msurface_t {

	int		visframe;		// should be drawn when node is crossed

	cplane_t	plane;
	int		flags;

	int		firstedge;	// look up in model->surfedges[], negative numbers
	int		numedges;	// are backwards edges
	
	short		texturemins[] ={0,0};
	short		extents[]={0,0};

	int		light_s, light_t;	// gl lightmap coordinates
	int		dlight_s, dlight_t; // gl lightmap coordinates for dynamic lightmaps

	glpoly_t	polys;				// multiple if warped
	msurface_t	texturechain;
	msurface_t	lightmapchain;

	mtexinfo_t	texinfo;
	
// lighting info
	int			dlightframe;
	int			dlightbits;

	int			lightmaptexturenum;
	byte		styles[]=new byte[constants.MAXLIGHTMAPS];
	float		cached_light[]= new float[constants.MAXLIGHTMAPS];	// values currently used in lightmap
	byte		samples[];		// [numstyles*surfsize]

}
