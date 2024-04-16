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
// $Id: image_t.java,v 1.1 2004-07-07 19:59:35 hzi Exp $

package jake2.client.render;

import jake2.qcommon.Defines;

public class image_t {
	
	public static final int MAX_NAME_SIZE = Defines.MAX_QPATH;
	
	// used to get the pos in array
	// added by cwei
	private int id;
	
	// quake 2 variables
	public String name=""; // game path, including extension
	// enum imagetype_t
	public int type;
	public int width, height; // source image
	public int upload_width, upload_height; // after power of two and picmip
	public int registration_sequence; // 0 = free
	public msurface_t texturechain; // for sort-by-texture world drawing
	public int texnum; // gl texture binding
	public float sl, tl, sh, th; // texture coordinates, 0,0 - 1,1 unless part of the scrap
	public boolean scrap;
	public boolean has_alpha;

	public boolean paletted;
	
	public image_t(int id) {
		this.id = id;
	}
	
	public void clear() {
		// don't clear the id
		// wichtig !!!
		name = "";
		type = 0;
		width = height = 0;
		upload_width = upload_height = 0;
		registration_sequence = 0; // 0 = free
		texturechain = null;
		texnum = 0; // gl texture binding
		sl =  tl = sh = th = 0;
		scrap = false;
		has_alpha = false;
		paletted = false;
	}

	public int getId() {
		return id;
	}
	
	public String toString() {
		return name + ":" + texnum;
	}
}
