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
// $Id: msurface_t.java,v 1.5 2004-01-19 12:34:13 cwei Exp $

package jake2.render;

import jake2.game.*;
import jake2.*;

public class msurface_t
{

	public int visframe; // should be drawn when node is crossed

	public cplane_t plane;
	public int flags;

	public int firstedge; // look up in model->surfedges[], negative numbers
	public int numedges; // are backwards edges

	public short texturemins[] = { 0, 0 };
	public short extents[] = { 0, 0 };

	public int light_s, light_t; // gl lightmap coordinates
	public int dlight_s, dlight_t;
	// gl lightmap coordinates for dynamic lightmaps

	public glpoly_t polys; // multiple if warped
	public msurface_t texturechain;
	public msurface_t lightmapchain;

	public mtexinfo_t texinfo;

	// lighting info
	public int dlightframe;
	public int dlightbits;

	public int lightmaptexturenum;
	public byte styles[] = new byte[Defines.MAXLIGHTMAPS];
	public float cached_light[] = new float[Defines.MAXLIGHTMAPS];
	// values currently used in lightmap
	public byte samples[]; // [numstyles*surfsize]

}
