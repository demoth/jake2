/*
 * Draw.java
 * Copyright (C) 2003
 *
 * $Id: Draw.java,v 1.3 2004-07-16 10:11:35 cawe Exp $
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
package jake2.render.fastjogl;

import jake2.Defines;
import jake2.client.VID;
import jake2.qcommon.Com;
import jake2.render.image_t;

import java.awt.Dimension;

import net.java.games.jogl.GL;

/**
 * Draw
 * (gl_draw.c)
 * 
 * @author cwei
 */
public abstract class Draw extends Image {

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
			image = GL_FindImage(fullname, it_pic);
		} else {
			image = GL_FindImage(name.substring(1), it_pic);
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
	protected void Draw_StretchPic (int x, int y, int w, int h, String pic) {
		
		image_t image;

		image = Draw_FindPic(pic);
		if (image == null)
		{
			VID.Printf (Defines.PRINT_ALL, "Can't find pic: " + pic +'\n');
			return;
		}

		if (scrap_dirty)
			Scrap_Upload();

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0) ) && !image.has_alpha)
			gl.glDisable(GL.GL_ALPHA_TEST);

		GL_Bind(image.texnum);
		gl.glBegin (GL.GL_QUADS);
		gl.glTexCoord2f (image.sl, image.tl);
		gl.glVertex2f (x, y);
		gl.glTexCoord2f (image.sh, image.tl);
		gl.glVertex2f (x+w, y);
		gl.glTexCoord2f (image.sh, image.th);
		gl.glVertex2f (x+w, y+h);
		gl.glTexCoord2f (image.sl, image.th);
		gl.glVertex2f (x, y+h);
		gl.glEnd ();

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) !=0 ) ) && !image.has_alpha)
			gl.glEnable(GL.GL_ALPHA_TEST);
	}


	/*
	=============
	Draw_Pic
	=============
	*/
	protected void Draw_Pic(int x, int y, String pic)
	{
		image_t image;

		image = Draw_FindPic(pic);
		if (image == null)
		{
			VID.Printf(Defines.PRINT_ALL, "Can't find pic: " +pic + '\n');
			return;
		}
		if (scrap_dirty)
			Scrap_Upload();

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) ) && !image.has_alpha)
			gl.glDisable (GL.GL_ALPHA_TEST);

		GL_Bind(image.texnum);

		gl.glBegin (GL.GL_QUADS);
		gl.glTexCoord2f (image.sl, image.tl);
		gl.glVertex2f (x, y);
		gl.glTexCoord2f (image.sh, image.tl);
		gl.glVertex2f (x+image.width, y);
		gl.glTexCoord2f (image.sh, image.th);
		gl.glVertex2f (x+image.width, y+image.height);
		gl.glTexCoord2f (image.sl, image.th);
		gl.glVertex2f (x, y+image.height);
		gl.glEnd ();

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) )  && !image.has_alpha)
			gl.glEnable (GL.GL_ALPHA_TEST);
	}

	/*
	=============
	Draw_TileClear

	This repeats a 64*64 tile graphic to fill the screen around a sized down
	refresh window.
	=============
	*/
	protected void Draw_TileClear(int x, int y, int w, int h, String pic) {
		image_t	image;

		image = Draw_FindPic(pic);
		if (image == null)
		{
			VID.Printf(Defines.PRINT_ALL, "Can't find pic: " + pic + '\n');
			return;
		}

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) )  && !image.has_alpha)
			gl.glDisable(GL.GL_ALPHA_TEST);

		GL_Bind(image.texnum);
		gl.glBegin (GL.GL_QUADS);
		gl.glTexCoord2f(x/64.0f, y/64.0f);
		gl.glVertex2f (x, y);
		gl.glTexCoord2f( (x+w)/64.0f, y/64.0f);
		gl.glVertex2f(x+w, y);
		gl.glTexCoord2f( (x+w)/64.0f, (y+h)/64.0f);
		gl.glVertex2f(x+w, y+h);
		gl.glTexCoord2f( x/64.0f, (y+h)/64.0f );
		gl.glVertex2f (x, y+h);
		gl.glEnd ();

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) )  && !image.has_alpha)
			gl.glEnable(GL.GL_ALPHA_TEST);
	}


	/*
	=============
	Draw_Fill

	Fills a box of pixels with a single color
	=============
	*/
	protected void Draw_Fill(int x, int y, int w, int h, int colorIndex)	{

		if ( colorIndex > 255)
			Com.Error(Defines.ERR_FATAL, "Draw_Fill: bad color");

		gl.glDisable(GL.GL_TEXTURE_2D);

		int color = d_8to24table[colorIndex]; 

		gl.glColor3ub(
			(byte)((color >> 0) & 0xff), // r
			(byte)((color >> 8) & 0xff), // g
			(byte)((color >> 16) & 0xff) // b
		);

		gl.glBegin (GL.GL_QUADS);

		gl.glVertex2f(x,y);
		gl.glVertex2f(x+w, y);
		gl.glVertex2f(x+w, y+h);
		gl.glVertex2f(x, y+h);

		gl.glEnd();
		gl.glColor3f(1,1,1);
		gl.glEnable(GL.GL_TEXTURE_2D);
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

// ====================================================================


	/*
	=============
	Draw_StretchRaw
	=============
	*/
	protected void Draw_StretchRaw (int x, int y, int w, int h, int cols, int rows, byte[] data)
	{
		int i, j, trows;
		int sourceIndex;
		int frac, fracstep;
		float hscale;
		int row;
		float t;

		GL_Bind(0);

		if (rows<=256)
		{
			hscale = 1;
			trows = rows;
		}
		else
		{
			hscale = rows/256.0f;
			trows = 256;
		}
		t = rows*hscale / 256;

		if ( !qglColorTableEXT )
		{
			int[] image32 = new int[256*256];
			int destIndex = 0;

			for (i=0 ; i<trows ; i++)
			{
				row = (int)(i*hscale);
				if (row > rows)
					break;
				sourceIndex = cols*row;
				destIndex = i*256;
				fracstep = cols*0x10000/256;
				frac = fracstep >> 1;
				for (j=0 ; j<256 ; j++)
				{
					image32[destIndex + j] = r_rawpalette[data[sourceIndex + (frac>>16)] & 0xff];
					frac += fracstep;
				}
			}
			gl.glTexImage2D (GL.GL_TEXTURE_2D, 0, gl_tex_solid_format, 256, 256, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, image32);
		}
		else
		{
			byte[] image8 = new byte[256*256];
			int destIndex = 0;;

			for (i=0 ; i<trows ; i++)
			{
				row = (int)(i*hscale);
				if (row > rows)
					break;
				sourceIndex = cols*row;
				destIndex = i*256;
				fracstep = cols*0x10000/256;
				frac = fracstep >> 1;
				for (j=0 ; j<256 ; j++)
				{
					image8[destIndex  + j] = data[sourceIndex + (frac>>16)];
					frac += fracstep;
				}
			}

			gl.glTexImage2D( GL.GL_TEXTURE_2D, 
						   0, 
						   GL_COLOR_INDEX8_EXT, 
						   256, 256, 
						   0, 
						   GL.GL_COLOR_INDEX, 
						   GL.GL_UNSIGNED_BYTE, 
						   image8 );
		}
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);

		if ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) ) 
			gl.glDisable (GL.GL_ALPHA_TEST);

		gl.glBegin (GL.GL_QUADS);
		gl.glTexCoord2f (0, 0);
		gl.glVertex2f (x, y);
		gl.glTexCoord2f (1, 0);
		gl.glVertex2f (x+w, y);
		gl.glTexCoord2f (1, t);
		gl.glVertex2f (x+w, y+h);
		gl.glTexCoord2f (0, t);
		gl.glVertex2f (x, y+h);
		gl.glEnd ();

		if ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) ) 
			gl.glEnable (GL.GL_ALPHA_TEST);
	}

}
