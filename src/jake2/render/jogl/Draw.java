/*
 * Draw.java
 * Copyright (C) 2003
 *
 * $Id: Draw.java,v 1.6 2003-12-29 06:00:49 cwei Exp $
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
//
//	extern	qboolean	scrap_dirty;
//	void Scrap_Upload (void);


	/*
	===============
	Draw_InitLocal
	===============
	*/
	void Draw_InitLocal() {
		// load console characters (don't bilerp characters)
		draw_chars = GL_FindImage("pics/conchars.pcx", it_pic);
		GL_Bind(draw_chars.texnum);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
	}

	/*
	================
	Draw_Char

	Draws one 8*8 graphics character with 0 being transparent.
	It can be clipped to the top of the screen to allow the console to be
	smoothly scrolled off.
	================
	*/
	protected void Draw_Char(int x, int y, int num) {

		num &= 255;
	
		if ( (num&127) == 32 ) return; // space

		if (y <= -8) return; // totally off screen

		int row = num>>4;
		int col = num&15;

		float frow = row*0.0625f;
		float fcol = col*0.0625f;
		float size = 0.0625f;

		GL_Bind(draw_chars.texnum);

		gl.glBegin (GL.GL_QUADS);
		gl.glTexCoord2f (fcol, frow);
		gl.glVertex2f (x, y);
		gl.glTexCoord2f (fcol + size, frow);
		gl.glVertex2f (x+8, y);
		gl.glTexCoord2f (fcol + size, frow + size);
		gl.glVertex2f (x+8, y+8);
		gl.glTexCoord2f (fcol, frow + size);
		gl.glVertex2f (x, y+8);
		gl.glEnd ();
	}


	/*
	=============
	Draw_FindPic
	=============
	*/
	protected image_t Draw_FindPic(String name) {
		image_t image = null;
		String fullname;

		if (!name.startsWith("/") && !name.startsWith("\\"))
		{
			fullname = "pics/" + name + ".pcx";
			image = GL_FindImage(fullname, Enum.it_pic);
		} else {
			image = GL_FindImage(name.substring(1), Enum.it_pic);
		}
		return image;
	}


	/*
	=============
	Draw_GetPicSize
	=============
	*/
	protected void Draw_GetPicSize(Dimension dim, String pic)	{

		image_t image = Draw_FindPic(pic);
		dim.width = (image != null) ? image.width : -1;
		dim.height = (image != null) ? image.height : -1;
	}

	/*
	=============
	Draw_StretchPic
	=============
	*/
	protected void Draw_StretchPic (int x, int y, int w, int h, String pic)
	{
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
//			gl.glDisable (GL_ALPHA_TEST);
//
//		GL_Bind (gl->texnum);
//		gl.glBegin (GL_QUADS);
//		gl.glTexCoord2f (gl->sl, gl->tl);
//		gl.glVertex2f (x, y);
//		gl.glTexCoord2f (gl->sh, gl->tl);
//		gl.glVertex2f (x+w, y);
//		gl.glTexCoord2f (gl->sh, gl->th);
//		gl.glVertex2f (x+w, y+h);
//		gl.glTexCoord2f (gl->sl, gl->th);
//		gl.glVertex2f (x, y+h);
//		gl.glEnd ();
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) ) && !gl->has_alpha)
//			gl.glEnable (GL_ALPHA_TEST);
	}
//
//
//	/*
//	=============
//	Draw_Pic
//	=============
//	*/
//	void Draw_Pic (int x, int y, String pic)
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
//			gl.glDisable (GL_ALPHA_TEST);
//
//		GL_Bind (gl->texnum);
//		gl.glBegin (GL_QUADS);
//		gl.glTexCoord2f (gl->sl, gl->tl);
//		gl.glVertex2f (x, y);
//		gl.glTexCoord2f (gl->sh, gl->tl);
//		gl.glVertex2f (x+gl->width, y);
//		gl.glTexCoord2f (gl->sh, gl->th);
//		gl.glVertex2f (x+gl->width, y+gl->height);
//		gl.glTexCoord2f (gl->sl, gl->th);
//		gl.glVertex2f (x, y+gl->height);
//		gl.glEnd ();
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) )  && !gl->has_alpha)
//			gl.glEnable (GL_ALPHA_TEST);
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
	protected void Draw_TileClear(int x, int y, int w, int h, String pic)
	{
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
//			gl.glDisable (GL_ALPHA_TEST);
//
//		GL_Bind (image->texnum);
//		gl.glBegin (GL_QUADS);
//		gl.glTexCoord2f (x/64.0, y/64.0);
//		gl.glVertex2f (x, y);
//		gl.glTexCoord2f ( (x+w)/64.0, y/64.0);
//		gl.glVertex2f (x+w, y);
//		gl.glTexCoord2f ( (x+w)/64.0, (y+h)/64.0);
//		gl.glVertex2f (x+w, y+h);
//		gl.glTexCoord2f ( x/64.0, (y+h)/64.0 );
//		gl.glVertex2f (x, y+h);
//		gl.glEnd ();
//
//		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) )  && !image->has_alpha)
//			gl.glEnable (GL_ALPHA_TEST);
	}


//	/*
//	=============
//	Draw_Fill
//
//	Fills a box of pixels with a single color
//	=============
//	*/
	protected void Draw_Fill(int x, int y, int w, int h, int c)
	{
//		union
//		{
//			unsigned	c;
//			byte		v[4];
//		} color;
//
//		if ( (unsigned)c > 255)
//			ri.Sys_Error (ERR_FATAL, "Draw_Fill: bad color");
//
//		gl.glDisable (GL_TEXTURE_2D);
//
//		color.c = d_8to24table[c];
//		gl.glColor3f (color.v[0]/255.0,
//			color.v[1]/255.0,
//			color.v[2]/255.0);
//
//		gl.glBegin (GL_QUADS);
//
//		gl.glVertex2f (x,y);
//		gl.glVertex2f (x+w, y);
//		gl.glVertex2f (x+w, y+h);
//		gl.glVertex2f (x, y+h);
//
//		gl.glEnd ();
//		gl.glColor3f (1,1,1);
//		gl.glEnable (GL_TEXTURE_2D);
	}

//	  =============================================================================

	/*
	================
	Draw_FadeScreen
	================
	*/
	protected void Draw_FadeScreen()	{
		gl.glEnable(GL.GL_BLEND);
		gl.glDisable(GL.GL_TEXTURE_2D);
		gl.glColor4f(0, 0, 0, 0.8f);
		gl.glBegin(GL.GL_QUADS);

		gl.glVertex2f(0,0);
		gl.glVertex2f(vid.width, 0);
		gl.glVertex2f(vid.width, vid.height);
		gl.glVertex2f(0, vid.height);

		gl.glEnd();
		gl.glColor4f(1,1,1,1);
		gl.glEnable(GL.GL_TEXTURE_2D);
		gl.glDisable(GL.GL_BLEND);
	}

//	  ====================================================================
//
//
//	/*
//	=============
//	Draw_StretchRaw
//	=============
//	*/
//	extern unsigned	r_rawpalette[256];
//
	protected void Draw_StretchRaw (int x, int y, int w, int h, int cols, int rows, byte[] data)
	{
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
//			gl.glTexImage2D (GL_TEXTURE_2D, 0, gl_tex_solid_format, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, image32);
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
//			gl.glTexImage2D( GL_TEXTURE_2D, 
//						   0, 
//						   GL_COLOR_INDEX8_EXT, 
//						   256, 256, 
//						   0, 
//						   GL_COLOR_INDEX, 
//						   GL_UNSIGNED_BYTE, 
//						   image8 );
//		}
//		gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
//		gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
//
//		if ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) ) 
//			gl.glDisable (GL_ALPHA_TEST);
//
//		gl.glBegin (GL_QUADS);
//		gl.glTexCoord2f (0, 0);
//		gl.glVertex2f (x, y);
//		gl.glTexCoord2f (1, 0);
//		gl.glVertex2f (x+w, y);
//		gl.glTexCoord2f (1, t);
//		gl.glVertex2f (x+w, y+h);
//		gl.glTexCoord2f (0, t);
//		gl.glVertex2f (x, y+h);
//		gl.glEnd ();
//
//		if ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( gl_config.renderer & GL_RENDERER_RENDITION ) ) 
//			gl.glEnable (GL_ALPHA_TEST);
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
		
		gl.glWindowPos2i((vid.width-dim.width) / 2, (vid.height - dim.height) /2 );
		
		byte[] tmp = new byte[data.length * 3];
		int color = 0;
		for (int i=0; i < data.length; i++) {
			color =  d_8to24table[data[i] & 0xff];
			tmp[3*i + 0] = (byte) ((color >> 0)  & 0xff);
			tmp[3*i + 1] = (byte) ((color >> 8) & 0xff);
			tmp[3*i + 2] = (byte) ((color >> 16) & 0xff);
		}
		
		gl.glDrawPixels(dim.width, dim.height, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, tmp);

		int font = GLUT.BITMAP_TIMES_ROMAN_24;
		
		String text = (name == null) ? "jake2" : name;
		int length = glut.glutBitmapLength(font, text);
			
		gl.glColor3f(0f, 0.8f, 0f);
		gl.glWindowPos2i(x, y);
		glut.glutBitmapString(gl, font, text);
	}

}
