/*
 * Draw.java
 * Copyright (C) 2003
 *
 * $Id: Draw.java,v 1.2 2003-12-24 01:19:22 cwei Exp $
 */ 
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
package jake2.render;

import java.awt.Dimension;

/**
 * Draw
 * 
 * @author cwei
 */
public class Draw {
	
	/**
	 * 
	 */
	static void GetPalette() {
		// TODO Auto-generated method stub

	}
	
	/**
	 * @param name
	 * @return
	 */
	static image_t FindPic(String name) {
		image_t gl = null;
		String fullname;

		if (!name.startsWith("/") && !name.startsWith("\\"))
		{
			fullname = "pics/" + name + ".pcx";
			// gl = GL_FindImage(fullname, it.pic);
		} else {
			//gl = GL_FindImage(name.substring(1), it.pic);
		}
		return gl;
	}
	
	/**
	 * @param dim
	 * @param name
	 */
	static void GetPicSize(Dimension dim, String name) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param name
	 */
	static void Pic(int x, int y, String name) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param name
	 */
	static void StretchPic(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param num
	 */
	static void Char(int x, int y, int num) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param name
	 */
	static void TileClear(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param c
	 */
	static void Fill(int x, int y, int w, int h, int c) {
		// TODO Auto-generated method stub

	}

	/**
	 * 
	 */
	static void FadeScreen() {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param cols
	 * @param rows
	 * @param data
	 */
	static void StretchRaw(
		int x,
		int y,
		int w,
		int h,
		int cols,
		int rows,
		byte[] data) {
		// TODO Auto-generated method stub

	}


}
