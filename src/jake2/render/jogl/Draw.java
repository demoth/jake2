/*
 * Draw.java
 * Copyright (C) 2003
 *
 * $Id: Draw.java,v 1.3 2003-12-27 21:33:50 rst Exp $
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
package jake2.render.jogl;

import jake2.Globals;
import jake2.render.image_t;

import java.awt.Dimension;

import net.java.games.jogl.GL;
import net.java.games.jogl.util.GLUT;

/**
 * Draw
 * 
 * @author cwei
 */
public class Draw extends Image {
	/**
	 * 
	 */
	protected void Draw_GetPalette() {
		int r, g, b;
		int v;
		Dimension dim;
		byte[] pic; 
		byte[][] palette = new byte[1][]; //new byte[768];

		// get the palette

		pic = LoadPCX("pics/colormap.pcx", palette, dim = new Dimension());

		if (palette[0] == null)
			ri.Sys_Error(Globals.ERR_FATAL, "Couldn't load pics/colormap.pcx", null);

		byte[] pal = palette[0];

		for (int i = 0; i < 256; i++) {
			r = pal[i * 3 + 0];
			g = pal[i * 3 + 1];
			b = pal[i * 3 + 2];

			d_8to24table[i] = (255 << 24) + (r << 0) + (g << 8) + (b << 16);
		}

		d_8to24table[255] &= 0x00ffffff; // 255 is transparent
	}
	

 	
	/**
	 * @param name
	 * @return
	 */
	protected image_t Draw_FindPic(String name) {
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
	protected void Draw_GetPicSize(Dimension dim, String name) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param name
	 */
	protected void Draw_Pic(int x, int y, String name) {
		// TODO Auto-generated method stub
		
		//canvas.display();
		
		// TODO opengl display

		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		R_SetGL2D();
		
		
		// *** test *** /
		
		Dimension dim = new Dimension();
		byte[][] palette = new byte[1][];
		
		byte[] data;
		
		data = LoadPCX("pics/conback.pcx", palette, dim);
		
		//gl.glEnable(GL.GL_SHARED_TEXTURE_PALETTE_EXT);
		
		/*
		gl.glColorTableEXT(
			GL.GL_SHARED_TEXTURE_PALETTE_EXT,
			GL.GL_RGB,
			256,
			GL.GL_RGB,
			GL.GL_UNSIGNED_BYTE,
			palette[0]);
		*/
		gl.glWindowPos2i((vid.width-dim.width) / 2, (vid.height - dim.height) /2 );
//		gl.glRasterPos2f(0, 0);
		
		byte[] tmp = new byte[data.length * 3];
		int color = 0;
		for (int i=0; i < data.length; i++) {
			color =  d_8to24table[data[i] & 0xff];
			tmp[3*i + 0] = (byte) ((color >> 0)  & 0xff);
			tmp[3*i + 1] = (byte) ((color >> 8) & 0xff);
			tmp[3*i + 2] = (byte) ((color >> 16) & 0xff);
		}
		
		
		
//		gl.glDrawPixels(dim.width, dim.height, GL.GL_COLOR_INDEX, GL.GL_UNSIGNED_BYTE, tmp);
//		gl.glDrawPixels(100, 100, GL.GL_COLOR_INDEX, GL.GL_UNSIGNED_BYTE, tmp);
		gl.glDrawPixels(dim.width, dim.height, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, tmp);

		int font = GLUT.BITMAP_TIMES_ROMAN_24;
		
		// draw FPS information
		String text = (name == null) ? "jake2" : name;
		int length = glut.glutBitmapLength(font, text);
			
		gl.glColor3f(0f, 0.8f, 0f);
		gl.glWindowPos2i(x, y);
		glut.glutBitmapString(gl, font, text);

		
//		gl.glEnable(GL.GL_TEXTURE_2D);
//		
//		gl.glTexImage2D(
//			GL.GL_TEXTURE_2D,
//			0,
//			GL.GL_COLOR_INDEX,
//			dim.width,
//			dim.height,
//			0,
//			GL.GL_COLOR_INDEX,
//			GL.GL_UNSIGNED_BYTE,
//			data);
//	 
//	      gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
//	      gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
//		
//		
		
	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param name
	 */
	protected void Draw_StretchPic(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param num
	 */
	protected void Draw_Char(int x, int y, int num) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param name
	 */
	protected void Draw_TileClear(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param c
	 */
	protected void Draw_Fill(int x, int y, int w, int h, int c) {
		// TODO Auto-generated method stub

	}

	/**
	 * 
	 */
	protected void Draw_FadeScreen() {
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
	protected void Draw_StretchRaw(
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
