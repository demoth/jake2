/*
 * Draw.java
 * Copyright (C) 2003
 *
 * $Id: Draw.java,v 1.4 2008-03-02 14:56:23 cawe Exp $
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
package jake2.client.render.fast;

import jake2.client.Console;
import jake2.client.render.image_t;
import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.util.Lib;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

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
		gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
	}

	public void Draw_Char(int x, int y, int num) {


		num &= 255; // fixme: change to `char`
	
		if ( (num&127) == 32 ) return; // space

        if (y <= -Console.CHAR_SIZE_PX) return; // totally off screen

		// find character place in the char sheet
		int row = num / Console.CON_CHAR_GRID_SIZE;
		int col = num % Console.CON_CHAR_GRID_SIZE;

		// character texture coordinates
		float frow = row / (float) Console.CON_CHAR_GRID_SIZE;
		float fcol = col / (float) Console.CON_CHAR_GRID_SIZE;
		// relative size of the character in the conchars.pcx sheet.
		float size = 1 / (float) Console.CON_CHAR_GRID_SIZE;

		GL_Bind(draw_chars.texnum);

		gl.glBegin (GL_QUADS);
		gl.glTexCoord2f (fcol, frow);
		gl.glVertex2f (x, y);
		gl.glTexCoord2f (fcol + size, frow);
		gl.glVertex2f (x+ Console.CHAR_SIZE_PX, y);
		gl.glTexCoord2f (fcol + size, frow + size);
		gl.glVertex2f (x+ Console.CHAR_SIZE_PX, y+ Console.CHAR_SIZE_PX);
		gl.glTexCoord2f (fcol, frow + size);
		gl.glVertex2f (x, y+ Console.CHAR_SIZE_PX);
		gl.glEnd ();
	}


	/*
	=============
	Draw_FindPic
	=============
	*/
	public image_t Draw_FindPic(String name) {
		if (!name.startsWith("/") && !name.startsWith("\\"))
		{
			return GL_FindImage(name, it_pic);
		} else {
			return GL_FindImage(name.substring(1), it_pic);
		}
	}


	/*
	=============
	Draw_GetPicSize
	=============
	*/
	public void Draw_GetPicSize(Dimension dim, String pic)	{

		image_t image = Draw_FindPic(pic);
		dim.width = (image != null) ? image.width : -1;
		dim.height = (image != null) ? image.height : -1;
	}

	/*
	=============
	Draw_StretchPic
	=============
	*/
	public void Draw_StretchPic (int x, int y, int w, int h, String pic) {
		
		image_t image;

		image = Draw_FindPic(pic);
		if (image == null)
		{
			Com.Printf (Defines.PRINT_ALL, "Can't find pic: " + pic +'\n');
			return;
		}

		if (scrap_dirty)
			Scrap_Upload();

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0) ) && !image.has_alpha)
			gl.glDisable(GL_ALPHA_TEST);

		GL_Bind(image.texnum);
		gl.glBegin (GL_QUADS);
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
			gl.glEnable(GL_ALPHA_TEST);
	}


	/*
	=============
	Draw_Pic
	=============
	*/
	public void Draw_Pic(int x, int y, String pic)
	{
		image_t image;

		image = Draw_FindPic(pic);
		if (image == null)
		{
			Com.Printf(Defines.PRINT_ALL, "Can't find pic: " +pic + '\n');
			return;
		}
		if (scrap_dirty)
			Scrap_Upload();

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) ) && !image.has_alpha)
			gl.glDisable (GL_ALPHA_TEST);

		GL_Bind(image.texnum);

		gl.glBegin (GL_QUADS);
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
			gl.glEnable (GL_ALPHA_TEST);
	}

	/*
	=============
	Draw_TileClear

	This repeats a 64*64 tile graphic to fill the screen around a sized down
	refresh window.
	=============
	*/
	public void Draw_TileClear(int x, int y, int w, int h, String pic) {
		image_t	image;

		image = Draw_FindPic(pic);
		if (image == null)
		{
			Com.Printf(Defines.PRINT_ALL, "Can't find pic: " + pic + '\n');
			return;
		}

		if ( ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) )  && !image.has_alpha)
			gl.glDisable(GL_ALPHA_TEST);

		GL_Bind(image.texnum);
		gl.glBegin (GL_QUADS);
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
			gl.glEnable(GL_ALPHA_TEST);
	}


	/*
	=============
	Draw_Fill

	Fills a box of pixels with a single color
	=============
	*/
	public void Draw_Fill(int x, int y, int w, int h, int colorIndex)	{

		if ( colorIndex > 255)
			Com.Error(Defines.ERR_FATAL, "Draw_Fill: bad color");

		gl.glDisable(GL_TEXTURE_2D);

		int color = d_8to24table[colorIndex]; 

		gl.glColor3ub(
			(byte)((color >> 0) & 0xff), // r
			(byte)((color >> 8) & 0xff), // g
			(byte)((color >> 16) & 0xff) // b
		);

		gl.glBegin (GL_QUADS);

		gl.glVertex2f(x,y);
		gl.glVertex2f(x+w, y);
		gl.glVertex2f(x+w, y+h);
		gl.glVertex2f(x, y+h);

		gl.glEnd();
		gl.glColor3f(1,1,1);
		gl.glEnable(GL_TEXTURE_2D);
	}

	//=============================================================================

	/*
	================
	Draw_FadeScreen
	================
	*/
	public void Draw_FadeScreen()	{
		gl.glEnable(GL_BLEND);
		gl.glDisable(GL_TEXTURE_2D);
		gl.glColor4f(0, 0, 0, 0.8f);
		gl.glBegin(GL_QUADS);

		gl.glVertex2f(0,0);
		gl.glVertex2f(vid.getWidth(), 0);
		gl.glVertex2f(vid.getWidth(), vid.getHeight());
		gl.glVertex2f(0, vid.getHeight());

		gl.glEnd();
		gl.glColor4f(1,1,1,1);
		gl.glEnable(GL_TEXTURE_2D);
		gl.glDisable(GL_BLEND);
	}

// ====================================================================

	IntBuffer image32=Lib.newIntBuffer(256*256);
	ByteBuffer image8=Lib.newByteBuffer(256*256);
	

	/*
	=============
	Draw_StretchRaw
	=============
	*/
	public void Draw_StretchRaw (int x, int y, int w, int h, int cols, int rows, byte[] data)
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
			//int[] image32 = new int[256*256];
			image32.clear();
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
					image32.put(destIndex + j, r_rawpalette[data[sourceIndex + (frac>>16)] & 0xff]);
					frac += fracstep;
				}
			}
			gl.glTexImage2D (GL_TEXTURE_2D, 0, gl_tex_solid_format, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, image32);
		}
		else
		{
			//byte[] image8 = new byte[256*256];
			image8.clear();
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
					image8.put(destIndex  + j, data[sourceIndex + (frac>>16)]);
					frac += fracstep;
				}
			}

			gl.glTexImage2D( GL_TEXTURE_2D, 
						   0, 
						   GL_COLOR_INDEX8_EXT, 
						   256, 256, 
						   0, 
						   GL_COLOR_INDEX, 
						   GL_UNSIGNED_BYTE, 
						   image8 );
		}
		gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

		if ( ( gl_config.renderer == GL_RENDERER_MCD ) || ( (gl_config.renderer & GL_RENDERER_RENDITION) != 0 ) ) 
			gl.glDisable (GL_ALPHA_TEST);

		gl.glBegin (GL_QUADS);
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
			gl.glEnable (GL_ALPHA_TEST);
	}

}
