/*
 * Draw.java
 * Copyright (C) 2003
 *
 * $Id: Draw.java,v 1.5 2003-12-29 01:57:00 cwei Exp $
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

import jake2.Enum;
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
public abstract class Draw extends Image {

////	   draw.c
//
//	#include "gl_local.h"
//
//	image_t		*draw_chars;
//
//	extern	qboolean	scrap_dirty;
//	void Scrap_Upload (void);
//
//
//	/*
//	===============
//	Draw_InitLocal
//	===============
//	*/
//	void Draw_InitLocal (void)
//	{
//		// load console characters (don't bilerp characters)
//		draw_chars = GL_FindImage ("pics/conchars.pcx", it_pic);
//		GL_Bind( draw_chars->texnum );
//		qglTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
//		qglTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
//	}
//
//
//
//	/*
//	================
//	Draw_Char
//
//	Draws one 8*8 graphics character with 0 being transparent.
//	It can be clipped to the top of the screen to allow the console to be
//	smoothly scrolled off.
//	================
//	*/
//	void Draw_Char (int x, int y, int num)
//	{
//		int				row, col;
//		float			frow, fcol, size;
//
//		num &= 255;
//	
//		if ( (num&127) == 32 )
//			return;		// space
//
//		if (y <= -8)
//			return;			// totally off screen
//
//		row = num>>4;
//		col = num&15;
//
//		frow = row*0.0625;
//		fcol = col*0.0625;
//		size = 0.0625;
//
//		GL_Bind (draw_chars->texnum);
//
//		qglBegin (GL_QUADS);
//		qglTexCoord2f (fcol, frow);
//		qglVertex2f (x, y);
//		qglTexCoord2f (fcol + size, frow);
//		qglVertex2f (x+8, y);
//		qglTexCoord2f (fcol + size, frow + size);
//		qglVertex2f (x+8, y+8);
//		qglTexCoord2f (fcol, frow + size);
//		qglVertex2f (x, y+8);
//		qglEnd ();
//	}
//
//	/*
//	=============
//	Draw_FindPic
//	=============
//	*/
//	image_t	*Draw_FindPic (char *name)
//	{
//		image_t *gl;
//		char	fullname[MAX_QPATH];
//
//		if (name[0] != '/' && name[0] != '\\')
//		{
//			Com_sprintf (fullname, sizeof(fullname), "pics/%s.pcx", name);
//			gl = GL_FindImage (fullname, it_pic);
//		}
//		else
//			gl = GL_FindImage (name+1, it_pic);
//
//		return gl;
//	}
//
//	/*
//	=============
//	Draw_GetPicSize
//	=============
//	*/
//	void Draw_GetPicSize (int *w, int *h, char *pic)
//	{
//		image_t *gl;
//
//		gl = Draw_FindPic (pic);
//		if (!gl)
//		{
//			*w = *h = -1;
//			return;
//		}
//		*w = gl->width;
//		*h = gl->height;
//	}
//
//	/*
//	=============
//	Draw_StretchPic
//	=============
//	*/
//	void Draw_StretchPic (int x, int y, int w, int h, char *pic)
//	{
//		image_t *gl;
//
//		gl = Draw_FindPic (pic);
//		if (!gl)
//		{
//			ri.Con_Printf (PRINT_ALL, "Can't find pic: %s\n", pic);
//			return;
//		}
//
//		if (scrap_dirty)
//			Scrap_Upload ();
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) ) && !gl->has_alpha)
//			qglDisable (GL_ALPHA_TEST);
//
//		GL_Bind (gl->texnum);
//		qglBegin (GL_QUADS);
//		qglTexCoord2f (gl->sl, gl->tl);
//		qglVertex2f (x, y);
//		qglTexCoord2f (gl->sh, gl->tl);
//		qglVertex2f (x+w, y);
//		qglTexCoord2f (gl->sh, gl->th);
//		qglVertex2f (x+w, y+h);
//		qglTexCoord2f (gl->sl, gl->th);
//		qglVertex2f (x, y+h);
//		qglEnd ();
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) ) && !gl->has_alpha)
//			qglEnable (GL_ALPHA_TEST);
//	}
//
//
//	/*
//	=============
//	Draw_Pic
//	=============
//	*/
//	void Draw_Pic (int x, int y, char *pic)
//	{
//		image_t *gl;
//
//		gl = Draw_FindPic (pic);
//		if (!gl)
//		{
//			ri.Con_Printf (PRINT_ALL, "Can't find pic: %s\n", pic);
//			return;
//		}
//		if (scrap_dirty)
//			Scrap_Upload ();
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) ) && !gl->has_alpha)
//			qglDisable (GL_ALPHA_TEST);
//
//		GL_Bind (gl->texnum);
//		qglBegin (GL_QUADS);
//		qglTexCoord2f (gl->sl, gl->tl);
//		qglVertex2f (x, y);
//		qglTexCoord2f (gl->sh, gl->tl);
//		qglVertex2f (x+gl->width, y);
//		qglTexCoord2f (gl->sh, gl->th);
//		qglVertex2f (x+gl->width, y+gl->height);
//		qglTexCoord2f (gl->sl, gl->th);
//		qglVertex2f (x, y+gl->height);
//		qglEnd ();
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) )  && !gl->has_alpha)
//			qglEnable (GL_ALPHA_TEST);
//	}
//
//	/*
//	=============
//	Draw_TileClear
//
//	This repeats a 64*64 tile graphic to fill the screen around a sized down
//	refresh window.
//	=============
//	*/
//	void Draw_TileClear (int x, int y, int w, int h, char *pic)
//	{
//		image_t	*image;
//
//		image = Draw_FindPic (pic);
//		if (!image)
//		{
//			ri.Con_Printf (PRINT_ALL, "Can't find pic: %s\n", pic);
//			return;
//		}
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) )  && !image->has_alpha)
//			qglDisable (GL_ALPHA_TEST);
//
//		GL_Bind (image->texnum);
//		qglBegin (GL_QUADS);
//		qglTexCoord2f (x/64.0, y/64.0);
//		qglVertex2f (x, y);
//		qglTexCoord2f ( (x+w)/64.0, y/64.0);
//		qglVertex2f (x+w, y);
//		qglTexCoord2f ( (x+w)/64.0, (y+h)/64.0);
//		qglVertex2f (x+w, y+h);
//		qglTexCoord2f ( x/64.0, (y+h)/64.0 );
//		qglVertex2f (x, y+h);
//		qglEnd ();
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) )  && !image->has_alpha)
//			qglEnable (GL_ALPHA_TEST);
//	}
//
//
//	/*
//	=============
//	Draw_Fill
//
//	Fills a box of pixels with a single color
//	=============
//	*/
//	void Draw_Fill (int x, int y, int w, int h, int c)
//	{
//		union
//		{
//			unsigned	c;
//			byte		v[4];
//		} color;
//
//		if ( (unsigned)c > 255)
//			ri.Sys_Error (ERR_FATAL, "Draw_Fill: bad color");
//
//		qglDisable (GL_TEXTURE_2D);
//
//		color.c = d_8to24table[c];
//		qglColor3f (color.v[0]/255.0,
//			color.v[1]/255.0,
//			color.v[2]/255.0);
//
//		qglBegin (GL_QUADS);
//
//		qglVertex2f (x,y);
//		qglVertex2f (x+w, y);
//		qglVertex2f (x+w, y+h);
//		qglVertex2f (x, y+h);
//
//		qglEnd ();
//		qglColor3f (1,1,1);
//		qglEnable (GL_TEXTURE_2D);
//	}
//
////	  =============================================================================
//
//	/*
//	================
//	Draw_FadeScreen
//
//	================
//	*/
//	void Draw_FadeScreen (void)
//	{
//		qglEnable (GL_BLEND);
//		qglDisable (GL_TEXTURE_2D);
//		qglColor4f (0, 0, 0, 0.8);
//		qglBegin (GL_QUADS);
//
//		qglVertex2f (0,0);
//		qglVertex2f (vid.width, 0);
//		qglVertex2f (vid.width, vid.height);
//		qglVertex2f (0, vid.height);
//
//		qglEnd ();
//		qglColor4f (1,1,1,1);
//		qglEnable (GL_TEXTURE_2D);
//		qglDisable (GL_BLEND);
//	}
//
//
////	  ====================================================================
//
//
//	/*
//	=============
//	Draw_StretchRaw
//	=============
//	*/
//	extern unsigned	r_rawpalette[256];
//
//	void Draw_StretchRaw (int x, int y, int w, int h, int cols, int rows, byte *data)
//	{
//		unsigned	image32[256*256];
//		unsigned char image8[256*256];
//		int			i, j, trows;
//		byte		*source;
//		int			frac, fracstep;
//		float		hscale;
//		int			row;
//		float		t;
//
//		GL_Bind (0);
//
//		if (rows<=256)
//		{
//			hscale = 1;
//			trows = rows;
//		}
//		else
//		{
//			hscale = rows/256.0;
//			trows = 256;
//		}
//		t = rows*hscale / 256;
//
//		if ( !qglColorTableEXT )
//		{
//			unsigned *dest;
//
//			for (i=0 ; i<trows ; i++)
//			{
//				row = (int)(i*hscale);
//				if (row > rows)
//					break;
//				source = data + cols*row;
//				dest = &image32[i*256];
//				fracstep = cols*0x10000/256;
//				frac = fracstep >> 1;
//				for (j=0 ; j<256 ; j++)
//				{
//					dest[j] = r_rawpalette[source[frac>>16]];
//					frac += fracstep;
//				}
//			}
//
//			qglTexImage2D (GL_TEXTURE_2D, 0, gl_tex_solid_format, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, image32);
//		}
//		else
//		{
//			unsigned char *dest;
//
//			for (i=0 ; i<trows ; i++)
//			{
//				row = (int)(i*hscale);
//				if (row > rows)
//					break;
//				source = data + cols*row;
//				dest = &image8[i*256];
//				fracstep = cols*0x10000/256;
//				frac = fracstep >> 1;
//				for (j=0 ; j<256 ; j++)
//				{
//					dest[j] = source[frac>>16];
//					frac += fracstep;
//				}
//			}
//
//			qglTexImage2D( GL_TEXTURE_2D, 
//						   0, 
//						   GL_COLOR_INDEX8_EXT, 
//						   256, 256, 
//						   0, 
//						   GL_COLOR_INDEX, 
//						   GL_UNSIGNED_BYTE, 
//						   image8 );
//		}
//		qglTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
//		qglTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
//
//		if ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) ) 
//			qglDisable (GL_ALPHA_TEST);
//
//		qglBegin (GL_QUADS);
//		qglTexCoord2f (0, 0);
//		qglVertex2f (x, y);
//		qglTexCoord2f (1, 0);
//		qglVertex2f (x+w, y);
//		qglTexCoord2f (1, t);
//		qglVertex2f (x+w, y+h);
//		qglTexCoord2f (0, t);
//		qglVertex2f (x, y+h);
//		qglEnd ();
//
//		if ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) ) 
//			qglEnable (GL_ALPHA_TEST);
//	}
//



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

		if (palette[0] == null || palette[0].length != 768)
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
		image_t image = null;
		String fullname;

		if (!name.startsWith("/") && !name.startsWith("\\"))
		{
			fullname = "pics/" + name + ".pcx";
			// TODO cwei
			// image = GL_FindImage(fullname, Enum.it_pic);
		} else {
			// TODO cwei
			// image = GL_FindImage(name.substring(1), Enum.it_pic);
		}
		return image;
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
		// TODO impl Draw_Pic(int x, int y, String name)
		
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
