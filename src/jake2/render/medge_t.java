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
// $Id: medge_t.java,v 1.1 2004-07-07 19:59:35 hzi Exp $

package jake2.render;

import jake2.Defines;

import java.nio.ByteBuffer;

public class medge_t {
	
	public static final int DISK_SIZE = 2 * Defines.SIZE_OF_SHORT;
	public static final int MEM_SIZE = 3 * Defines.SIZE_OF_INT;
	
	// unsigned short
	public int[] v = new int[2];
	public int cachededgeoffset;
	
	public medge_t(ByteBuffer b) {
		v[0] = b.getShort() & 0xFFFF;
		v[1] = b.getShort() & 0xFFFF;
	}
}
