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
// $Id: image_t.java,v 1.6 2004-02-04 20:33:35 rst Exp $

package jake2.render;

import jake2.Defines;

public class image_t {
	
	public static final int MAX_NAME_SIZE = Defines.MAX_QPATH;
	
	public String name=""; // game path, including extension
	// enum imagetype_t
	public int type;
	public int width, height; // source image
	public int upload_width, upload_height; // after power of two and picmip
	public int registration_sequence; // 0 = free
	public msurface_t texturechain; // for sort-by-texture world drawing
	public int texnum; // gl texture binding
	public float sl, tl, sh, th; // 0,0 - 1,1 unless part of the scrap
	public boolean scrap;
	public boolean has_alpha;

	public boolean paletted;
	
	public String toString() {
		return name + ":" + texnum;
	}
}
