/*
 * Image.java
 * Copyright (C) 2003
 *
 * $Id: Image.java,v 1.5 2004-07-16 10:11:35 cawe Exp $
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

import jake2.Defines;
import jake2.client.VID;
import jake2.client.particle_t;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.qcommon.longjmpException;
import jake2.qcommon.qfiles;
import jake2.render.image_t;
import jake2.util.Lib;
import jake2.util.Vargs;

import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.util.Arrays;

import net.java.games.jogl.GL;

/**
 * Image
 * 
 * @author cwei
 */
public abstract class Image extends Main {

	image_t draw_chars;

	image_t[] gltextures = new image_t[MAX_GLTEXTURES];
	//Map gltextures = new Hashtable(MAX_GLTEXTURES); // image_t
	int numgltextures;
	int base_textureid; // gltextures[i] = base_textureid+i

	byte[] intensitytable = new byte[256];
	byte[] gammatable = new byte[256];

	cvar_t intensity;

	//
	//	qboolean GL_Upload8 (byte *data, int width, int height,  qboolean mipmap, qboolean is_sky );
	//	qboolean GL_Upload32 (unsigned *data, int width, int height,  qboolean mipmap);
	//

	int gl_solid_format = 3;
	int gl_alpha_format = 4;

	int gl_tex_solid_format = 3;
	int gl_tex_alpha_format = 4;

	int gl_filter_min = GL.GL_LINEAR_MIPMAP_NEAREST;
	int gl_filter_max = GL.GL_LINEAR;
	
	Image() {
		// init the texture cache
		for (int i = 0; i < gltextures.length; i++)
		{
			gltextures[i] = new image_t(i);
		}
		numgltextures = 0;
	}

	void GL_SetTexturePalette(int[] palette) {

		assert(palette != null && palette.length == 256) : "int palette[256] bug";

		int i;
		byte[] temptable = new byte[768];

		if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f) {
			for (i = 0; i < 256; i++) {
				temptable[i * 3 + 0] = (byte) ((palette[i] >> 0) & 0xff);
				temptable[i * 3 + 1] = (byte) ((palette[i] >> 8) & 0xff);
				temptable[i * 3 + 2] = (byte) ((palette[i] >> 16) & 0xff);
			}

			gl.glColorTableEXT(GL.GL_SHARED_TEXTURE_PALETTE_EXT, GL.GL_RGB, 256, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, temptable);
		}
	}

	void GL_EnableMultitexture(boolean enable) {
		if (!qglSelectTextureSGIS && !qglActiveTextureARB)
			return;

		if (enable) {
			GL_SelectTexture(GL_TEXTURE1);
			gl.glEnable(GL.GL_TEXTURE_2D);
			GL_TexEnv(GL.GL_REPLACE);
		}
		else {
			GL_SelectTexture(GL_TEXTURE1);
			gl.glDisable(GL.GL_TEXTURE_2D);
			GL_TexEnv(GL.GL_REPLACE);
		}
		GL_SelectTexture(GL_TEXTURE0);
		GL_TexEnv(GL.GL_REPLACE);
	}

	void GL_SelectTexture(int texture /* GLenum */) {
		int tmu;

		if (!qglSelectTextureSGIS && !qglActiveTextureARB)
			return;

		if (texture == GL_TEXTURE0) {
			tmu = 0;
		}
		else {
			tmu = 1;
		}

		if (tmu == gl_state.currenttmu) {
			return;
		}

		gl_state.currenttmu = tmu;

		if (qglSelectTextureSGIS) {
			// TODO handle this: gl.glSelectTextureSGIS(texture);
			gl.glActiveTexture(texture);
		}
		else if (qglActiveTextureARB) {
			gl.glActiveTextureARB(texture);
			gl.glClientActiveTextureARB(texture);
		}
	}

	int[] lastmodes = { -1, -1 };

	void GL_TexEnv(int mode /* GLenum */
	) {

		if (mode != lastmodes[gl_state.currenttmu]) {
			gl.glTexEnvi(GL.GL_TEXTURE_ENV, GL.GL_TEXTURE_ENV_MODE, mode);
			lastmodes[gl_state.currenttmu] = mode;
		}
	}

	void GL_Bind(int texnum) {

		if ((gl_nobind.value != 0) && (draw_chars != null)) {
			// performance evaluation option
			texnum = draw_chars.texnum;
		}
		if (gl_state.currenttextures[gl_state.currenttmu] == texnum)
			return;

		gl_state.currenttextures[gl_state.currenttmu] = texnum;
		gl.glBindTexture(GL.GL_TEXTURE_2D, texnum);
	}

	void GL_MBind(int target /* GLenum */
	, int texnum) {
		GL_SelectTexture(target);
		if (target == GL_TEXTURE0) {
			if (gl_state.currenttextures[0] == texnum)
				return;
		}
		else {
			if (gl_state.currenttextures[1] == texnum)
				return;
		}
		GL_Bind(texnum);
	}

	// glmode_t
	static class glmode_t {
		String name;
		int minimize, maximize;

		glmode_t(String name, int minimize, int maximze) {
			this.name = name;
			this.minimize = minimize;
			this.maximize = maximze;
		}
	}

	static final glmode_t modes[] =
		{
			new glmode_t("GL_NEAREST", GL.GL_NEAREST, GL.GL_NEAREST),
			new glmode_t("GL_LINEAR", GL.GL_LINEAR, GL.GL_LINEAR),
			new glmode_t("GL_NEAREST_MIPMAP_NEAREST", GL.GL_NEAREST_MIPMAP_NEAREST, GL.GL_NEAREST),
			new glmode_t("GL_LINEAR_MIPMAP_NEAREST", GL.GL_LINEAR_MIPMAP_NEAREST, GL.GL_LINEAR),
			new glmode_t("GL_NEAREST_MIPMAP_LINEAR", GL.GL_NEAREST_MIPMAP_LINEAR, GL.GL_NEAREST),
			new glmode_t("GL_LINEAR_MIPMAP_LINEAR", GL.GL_LINEAR_MIPMAP_LINEAR, GL.GL_LINEAR)};

	static final int NUM_GL_MODES = modes.length;

	// gltmode_t
	static class gltmode_t {
		String name;
		int mode;

		gltmode_t(String name, int mode) {
			this.name = name;
			this.mode = mode;
		}
	}

	static final gltmode_t[] gl_alpha_modes =
		{
			new gltmode_t("default", 4),
			new gltmode_t("GL_RGBA", GL.GL_RGBA),
			new gltmode_t("GL_RGBA8", GL.GL_RGBA8),
			new gltmode_t("GL_RGB5_A1", GL.GL_RGB5_A1),
			new gltmode_t("GL_RGBA4", GL.GL_RGBA4),
			new gltmode_t("GL_RGBA2", GL.GL_RGBA2),
			};

	static final int NUM_GL_ALPHA_MODES = gl_alpha_modes.length;

	static final gltmode_t[] gl_solid_modes =
		{
			new gltmode_t("default", 3),
			new gltmode_t("GL_RGB", GL.GL_RGB),
			new gltmode_t("GL_RGB8", GL.GL_RGB8),
			new gltmode_t("GL_RGB5", GL.GL_RGB5),
			new gltmode_t("GL_RGB4", GL.GL_RGB4),
			new gltmode_t("GL_R3_G3_B2", GL.GL_R3_G3_B2),
		//	#ifdef GL_RGB2_EXT
		new gltmode_t("GL_RGB2", GL.GL_RGB2_EXT)
		//	#endif
	};

	static final int NUM_GL_SOLID_MODES = gl_solid_modes.length;

	/*
	===============
	GL_TextureMode
	===============
	*/
	void GL_TextureMode(String string) {

		int i;
		for (i = 0; i < NUM_GL_MODES; i++) {
			if (modes[i].name.equalsIgnoreCase(string))
				break;
		}

		if (i == NUM_GL_MODES) {
			VID.Printf(Defines.PRINT_ALL, "bad filter name: [" + string + "]\n");
			return;
		}

		gl_filter_min = modes[i].minimize;
		gl_filter_max = modes[i].maximize;

		image_t glt;
		// change all the existing mipmap texture objects
		for (i = 0; i < numgltextures; i++) {
			glt = gltextures[i];

			if (glt.type != it_pic && glt.type != it_sky) {
				GL_Bind(glt.texnum);
				gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, gl_filter_min);
				gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, gl_filter_max);
			}
		}
	}

	/*
	===============
	GL_TextureAlphaMode
	===============
	*/
	void GL_TextureAlphaMode(String string) {

		int i;
		for (i = 0; i < NUM_GL_ALPHA_MODES; i++) {
			if (gl_alpha_modes[i].name.equalsIgnoreCase(string))
				break;
		}

		if (i == NUM_GL_ALPHA_MODES) {
			VID.Printf(Defines.PRINT_ALL, "bad alpha texture mode name: [" + string + "]\n");
			return;
		}

		gl_tex_alpha_format = gl_alpha_modes[i].mode;
	}

	/*
	===============
	GL_TextureSolidMode
	===============
	*/
	void GL_TextureSolidMode(String string) {
		int i;
		for (i = 0; i < NUM_GL_SOLID_MODES; i++) {
			if (gl_solid_modes[i].name.equalsIgnoreCase(string))
				break;
		}

		if (i == NUM_GL_SOLID_MODES) {
			VID.Printf(Defines.PRINT_ALL, "bad solid texture mode name: [" + string + "]\n");
			return;
		}

		gl_tex_solid_format = gl_solid_modes[i].mode;
	}

	/*
	===============
	GL_ImageList_f
	===============
	*/
	void GL_ImageList_f() {

		image_t image;
		int texels;
		final String[] palstrings = { "RGB", "PAL" };

		VID.Printf(Defines.PRINT_ALL, "------------------\n");
		texels = 0;

		for (int i = 0; i < numgltextures; i++) {
			image = gltextures[i];
			if (image.texnum <= 0)
				continue;

			texels += image.upload_width * image.upload_height;
			switch (image.type) {
				case it_skin :
					VID.Printf(Defines.PRINT_ALL, "M");
					break;
				case it_sprite :
					VID.Printf(Defines.PRINT_ALL, "S");
					break;
				case it_wall :
					VID.Printf(Defines.PRINT_ALL, "W");
					break;
				case it_pic :
					VID.Printf(Defines.PRINT_ALL, "P");
					break;
				default :
					VID.Printf(Defines.PRINT_ALL, " ");
					break;
			}

			VID.Printf(
				Defines.PRINT_ALL,
				" %3i %3i %s: %s\n",
				new Vargs(4).add(image.upload_width).add(image.upload_height).add(palstrings[(image.paletted) ? 1 : 0]).add(
					image.name));
		}
		VID.Printf(Defines.PRINT_ALL, "Total texel count (not counting mipmaps): " + texels + '\n');
	}

	/*
	=============================================================================
	
	  scrap allocation
	
	  Allocate all the little status bar objects into a single texture
	  to crutch up inefficient hardware / drivers
	
	=============================================================================
	*/

	static final int MAX_SCRAPS = 1;
	static final int BLOCK_WIDTH = 256;
	static final int BLOCK_HEIGHT = 256;

	int[][] scrap_allocated = new int[MAX_SCRAPS][BLOCK_WIDTH];
	byte[][] scrap_texels = new byte[MAX_SCRAPS][BLOCK_WIDTH * BLOCK_HEIGHT];
	boolean scrap_dirty;

	static class pos_t {
		int x, y;

		pos_t(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	// returns a texture number and the position inside it
	int Scrap_AllocBlock(int w, int h, pos_t pos) {
		int i, j;
		int best, best2;
		int texnum;

		for (texnum = 0; texnum < MAX_SCRAPS; texnum++) {
			best = BLOCK_HEIGHT;

			for (i = 0; i < BLOCK_WIDTH - w; i++) {
				best2 = 0;

				for (j = 0; j < w; j++) {
					if (scrap_allocated[texnum][i + j] >= best)
						break;
					if (scrap_allocated[texnum][i + j] > best2)
						best2 = scrap_allocated[texnum][i + j];
				}
				if (j == w) { // this is a valid spot
					pos.x = i;
					pos.y = best = best2;
				}
			}

			if (best + h > BLOCK_HEIGHT)
				continue;

			for (i = 0; i < w; i++)
				scrap_allocated[texnum][pos.x + i] = best + h;

			return texnum;
		}

		return -1;
		// Sys_Error ("Scrap_AllocBlock: full");
	}

	int scrap_uploads = 0;

	void Scrap_Upload() {
		scrap_uploads++;
		GL_Bind(TEXNUM_SCRAPS);
		GL_Upload8(scrap_texels[0], BLOCK_WIDTH, BLOCK_HEIGHT, false, false);
		scrap_dirty = false;
	}

	/*
	=================================================================
	
	PCX LOADING
	
	=================================================================
	*/

	/*
	==============
	LoadPCX
	==============
	*/
	byte[] LoadPCX(String filename, byte[][] palette, Dimension dim) {
		qfiles.pcx_t pcx;

		//
		// load the file
		//
		byte[] raw = FS.LoadFile(filename);

		if (raw == null) {
			VID.Printf(Defines.PRINT_DEVELOPER, "Bad pcx file " + filename + '\n');
			return null;
		}

		//
		// parse the PCX file
		//
		pcx = new qfiles.pcx_t(raw);

		if (pcx.manufacturer != 0x0a
			|| pcx.version != 5
			|| pcx.encoding != 1
			|| pcx.bits_per_pixel != 8
			|| pcx.xmax >= 640
			|| pcx.ymax >= 480) {

			VID.Printf(Defines.PRINT_ALL, "Bad pcx file " + filename + '\n');
			return null;
		}

		int width = pcx.xmax - pcx.xmin + 1;
		int height = pcx.ymax - pcx.ymin + 1;

		byte[] pix = new byte[width * height];

		if (palette != null) {
			palette[0] = new byte[768];
			System.arraycopy(raw, raw.length - 768, palette[0], 0, 768);
		}

		if (dim != null) {
			dim.width = width;
			dim.height = height;
		}

		//
		// decode pcx
		//
		int count = 0;
		byte dataByte = 0;
		int runLength = 0;
		int x, y;

		for (y = 0; y < height; y++) {
			for (x = 0; x < width;) {

				dataByte = pcx.data.get();

				if ((dataByte & 0xC0) == 0xC0) {
					runLength = dataByte & 0x3F;
					dataByte = pcx.data.get();
					// write runLength pixel
					while (runLength-- > 0) {
						pix[count++] = dataByte;
						x++;
					}
				}
				else {
					// write one pixel
					pix[count++] = dataByte;
					x++;
				}
			}
		}
		return pix;
	}

	//	/*
	//	=========================================================
	//
	//	TARGA LOADING
	//
	//	=========================================================
	//	*/
	/*
	=============
	LoadTGA
	=============
	*/
	byte[] LoadTGA(String name, Dimension dim) {
		int columns, rows, numPixels;
		int pixbuf; // index into pic
		int row, column;
		byte[] raw;
		ByteBuffer buf_p;
		int length;
		qfiles.tga_t targa_header;
		byte[] pic = null;

		//
		// load the file
		//
		raw = FS.LoadFile (name);
		
		if (raw == null)
		{
			VID.Printf(Defines.PRINT_DEVELOPER, "Bad tga file "+ name +'\n');
			return null;
		}
		
		targa_header = new qfiles.tga_t(raw);
		
		if (targa_header.image_type != 2 && targa_header.image_type != 10) 
			Com.Error(Defines.ERR_DROP, "LoadTGA: Only type 2 and 10 targa RGB images supported\n");
		
		if (targa_header.colormap_type != 0 || (targa_header.pixel_size != 32 && targa_header.pixel_size != 24))
			Com.Error (Defines.ERR_DROP, "LoadTGA: Only 32 or 24 bit images supported (no colormaps)\n");
		
		columns = targa_header.width;
		rows = targa_header.height;
		numPixels = columns * rows;
		
		if (dim != null) {
			dim.width = columns;
			dim.height = rows;
		}
		
		pic = new byte[numPixels * 4]; // targa_rgba;
		
		if (targa_header.id_length != 0)
			targa_header.data.position(targa_header.id_length);  // skip TARGA image comment
		
		buf_p = targa_header.data;
			
		byte red,green,blue,alphabyte;
		red = green = blue = alphabyte = 0;
		int packetHeader, packetSize, j;
		
		if (targa_header.image_type==2) {  // Uncompressed, RGB images
			for(row=rows-1; row>=0; row--) {
				
				pixbuf = row * columns * 4;
				
				for(column=0; column<columns; column++) {
					switch (targa_header.pixel_size) {
						case 24:
									
							blue = buf_p.get();
							green = buf_p.get();
							red = buf_p.get();
							pic[pixbuf++] = red;
							pic[pixbuf++] = green;
							pic[pixbuf++] = blue;
							pic[pixbuf++] = (byte)255;
							break;
						case 32:
							blue = buf_p.get();
							green = buf_p.get();
							red = buf_p.get();
							alphabyte = buf_p.get();
							pic[pixbuf++] = red;
							pic[pixbuf++] = green;
							pic[pixbuf++] = blue;
							pic[pixbuf++] = alphabyte;
							break;
					}
				}
			}
		}
		else if (targa_header.image_type==10) {   // Runlength encoded RGB images
			for(row=rows-1; row>=0; row--) {
				
				pixbuf = row * columns * 4;
				try {

					for(column=0; column<columns; ) {
					
						packetHeader= buf_p.get() & 0xFF;
						packetSize = 1 + (packetHeader & 0x7f);
					
						if ((packetHeader & 0x80) != 0) {        // run-length packet
							switch (targa_header.pixel_size) {
								case 24:
									blue = buf_p.get();
									green = buf_p.get();
									red = buf_p.get();
									alphabyte = (byte)255;
									break;
								case 32:
									blue = buf_p.get();
									green = buf_p.get();
									red = buf_p.get();
									alphabyte = buf_p.get();
									break;
							}
				
							for(j=0;j<packetSize;j++) {
								pic[pixbuf++]=red;
								pic[pixbuf++]=green;
								pic[pixbuf++]=blue;
								pic[pixbuf++]=alphabyte;
								column++;
								if (column==columns) { // run spans across rows
									column=0;
									if (row>0)
										row--;
									else
										// goto label breakOut;
										throw new longjmpException();
			
									pixbuf = row * columns * 4;
								}
							}
						}
						else { // non run-length packet
							for(j=0;j<packetSize;j++) {
								switch (targa_header.pixel_size) {
									case 24:
										blue = buf_p.get();
										green = buf_p.get();
										red = buf_p.get();
										pic[pixbuf++] = red;
										pic[pixbuf++] = green;
										pic[pixbuf++] = blue;
										pic[pixbuf++] = (byte)255;
										break;
									case 32:
										blue = buf_p.get();
										green = buf_p.get();
										red = buf_p.get();
										alphabyte = buf_p.get();
										pic[pixbuf++] = red;
										pic[pixbuf++] = green;
										pic[pixbuf++] = blue;
										pic[pixbuf++] = alphabyte;
										break;
								}
								column++;
								if (column==columns) { // pixel packet run spans across rows
									column=0;
									if (row>0)
										row--;
									else
										// goto label breakOut;
										throw new longjmpException();
			
									pixbuf = row * columns * 4;
								}						
							}
						}
					}
				} catch (longjmpException e){
					// label breakOut:
				}
			}
		}
		return pic;
	}

	/*
	====================================================================
	
	IMAGE FLOOD FILLING
	
	====================================================================
	*/

	/*
	=================
	Mod_FloodFillSkin
	
	Fill background pixels so mipmapping doesn't have haloes
	=================
	*/

	static class floodfill_t {
		short x, y;
	}

	// must be a power of 2
	static final int FLOODFILL_FIFO_SIZE = 0x1000;
	static final int FLOODFILL_FIFO_MASK = FLOODFILL_FIFO_SIZE - 1;
	//
	//	#define FLOODFILL_STEP( off, dx, dy ) \
	//	{ \
	//		if (pos[off] == fillcolor) \
	//		{ \
	//			pos[off] = 255; \
	//			fifo[inpt].x = x + (dx), fifo[inpt].y = y + (dy); \
	//			inpt = (inpt + 1) & FLOODFILL_FIFO_MASK; \
	//		} \
	//		else if (pos[off] != 255) fdc = pos[off]; \
	//	}

	//	void FLOODFILL_STEP( int off, int dx, int dy )
	//	{
	//		if (pos[off] == fillcolor)
	//		{
	//			pos[off] = 255;
	//			fifo[inpt].x = x + dx; fifo[inpt].y = y + dy;
	//			inpt = (inpt + 1) & FLOODFILL_FIFO_MASK;
	//		}
	//		else if (pos[off] != 255) fdc = pos[off];
	//	}
	static floodfill_t[] fifo = new floodfill_t[FLOODFILL_FIFO_SIZE];
	static {
		for (int j = 0; j < fifo.length; j++) {
			fifo[j] = new floodfill_t();
		}		
	}
	// TODO check this: R_FloodFillSkin( byte[] skin, int skinwidth, int skinheight)
	void R_FloodFillSkin(byte[] skin, int skinwidth, int skinheight) {
		//		byte				fillcolor = *skin; // assume this is the pixel to fill
		int fillcolor = skin[0] & 0xff;
		int inpt = 0, outpt = 0;
		int filledcolor = -1;
		int i;

		if (filledcolor == -1) {
			filledcolor = 0;
			// attempt to find opaque black
			for (i = 0; i < 256; ++i)
				// TODO check this
				if (d_8to24table[i]  == 0xFF000000) { // alpha 1.0
				//if (d_8to24table[i] == (255 << 0)) // alpha 1.0
					filledcolor = i;
					break;
				}
		}

		// can't fill to filled color or to transparent color (used as visited marker)
		if ((fillcolor == filledcolor) || (fillcolor == 255)) {
			return;
		}

		fifo[inpt].x = 0;
		fifo[inpt].y = 0;
		inpt = (inpt + 1) & FLOODFILL_FIFO_MASK;

		while (outpt != inpt) {
			int x = fifo[outpt].x;
			int y = fifo[outpt].y;
			int fdc = filledcolor;
			//			byte		*pos = &skin[x + skinwidth * y];
			int pos = x + skinwidth * y;
			//
			outpt = (outpt + 1) & FLOODFILL_FIFO_MASK;

			int off, dx, dy;

			if (x > 0) {
				// FLOODFILL_STEP( -1, -1, 0 );
				off = -1;
				dx = -1;
				dy = 0;
				if (skin[pos + off] == (byte) fillcolor) {
					skin[pos + off] = (byte) 255;
					fifo[inpt].x = (short) (x + dx);
					fifo[inpt].y = (short) (y + dy);
					inpt = (inpt + 1) & FLOODFILL_FIFO_MASK;
				}
				else if (skin[pos + off] != (byte) 255)
					fdc = skin[pos + off] & 0xff;
			}

			if (x < skinwidth - 1) {
				// FLOODFILL_STEP( 1, 1, 0 );
				off = 1;
				dx = 1;
				dy = 0;
				if (skin[pos + off] == (byte) fillcolor) {
					skin[pos + off] = (byte) 255;
					fifo[inpt].x = (short) (x + dx);
					fifo[inpt].y = (short) (y + dy);
					inpt = (inpt + 1) & FLOODFILL_FIFO_MASK;
				}
				else if (skin[pos + off] != (byte) 255)
					fdc = skin[pos + off] & 0xff;
			}

			if (y > 0) {
				// FLOODFILL_STEP( -skinwidth, 0, -1 );
				off = -skinwidth;
				dx = 0;
				dy = -1;
				if (skin[pos + off] == (byte) fillcolor) {
					skin[pos + off] = (byte) 255;
					fifo[inpt].x = (short) (x + dx);
					fifo[inpt].y = (short) (y + dy);
					inpt = (inpt + 1) & FLOODFILL_FIFO_MASK;
				}
				else if (skin[pos + off] != (byte) 255)
					fdc = skin[pos + off] & 0xff;
			}

			if (y < skinheight - 1) {
				// FLOODFILL_STEP( skinwidth, 0, 1 );
				off = skinwidth;
				dx = 0;
				dy = 1;
				if (skin[pos + off] == (byte) fillcolor) {
					skin[pos + off] = (byte) 255;
					fifo[inpt].x = (short) (x + dx);
					fifo[inpt].y = (short) (y + dy);
					inpt = (inpt + 1) & FLOODFILL_FIFO_MASK;
				}
				else if (skin[pos + off] != (byte) 255)
					fdc = skin[pos + off] & 0xff;

			}

			skin[x + skinwidth * y] = (byte) fdc;
		}
	}

	//	  =======================================================

	/*
	================
	GL_ResampleTexture
	================
	*/
	// cwei :-)
	void GL_ResampleTexture(int[] in, int inwidth, int inheight, int[] out, int outwidth, int outheight) {
		//		int		i, j;
		//		unsigned	*inrow, *inrow2;
		//		int frac, fracstep;
		//		int[] p1 = new int[1024];
		//		int[] p2 = new int[1024];
		//		

		// *** this source do the same ***
		BufferedImage image = new BufferedImage(inwidth, inheight, BufferedImage.TYPE_INT_ARGB);

		image.setRGB(0, 0, inwidth, inheight, in, 0, inwidth);

		AffineTransformOp op =
			new AffineTransformOp(
				AffineTransform.getScaleInstance(outwidth * 1.0 / inwidth, outheight * 1.0 / inheight),
				AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		BufferedImage tmp = op.filter(image, null);

		tmp.getRGB(0, 0, outwidth, outheight, out, 0, outwidth);

		// *** end ***

		//		byte		*pix1, *pix2, *pix3, *pix4;
		//
		//		fracstep = inwidth*0x10000/outwidth;
		//
		//		frac = fracstep>>2;
		//		for (i=0 ; i<outwidth ; i++)
		//		{
		//			p1[i] = 4*(frac>>16);
		//			frac += fracstep;
		//		}
		//		frac = 3*(fracstep>>2);
		//		for (i=0 ; i<outwidth ; i++)
		//		{
		//			p2[i] = 4*(frac>>16);
		//			frac += fracstep;
		//		}
		//
		//		for (i=0 ; i<outheight ; i++, out += outwidth)
		//		{
		//			inrow = in + inwidth*(int)((i+0.25)*inheight/outheight);
		//			inrow2 = in + inwidth*(int)((i+0.75)*inheight/outheight);
		//			frac = fracstep >> 1;
		//			for (j=0 ; j<outwidth ; j++)
		//			{
		//				pix1 = (byte *)inrow + p1[j];
		//				pix2 = (byte *)inrow + p2[j];
		//				pix3 = (byte *)inrow2 + p1[j];
		//				pix4 = (byte *)inrow2 + p2[j];
		//				((byte *)(out+j))[0] = (pix1[0] + pix2[0] + pix3[0] + pix4[0])>>2;
		//				((byte *)(out+j))[1] = (pix1[1] + pix2[1] + pix3[1] + pix4[1])>>2;
		//				((byte *)(out+j))[2] = (pix1[2] + pix2[2] + pix3[2] + pix4[2])>>2;
		//				((byte *)(out+j))[3] = (pix1[3] + pix2[3] + pix3[3] + pix4[3])>>2;
		//			}
		//		}
	}

	/*
	================
	GL_LightScaleTexture
	
	Scale up the pixel values in a texture to increase the
	lighting range
	================
	*/
	void GL_LightScaleTexture(int[] in, int inwidth, int inheight, boolean only_gamma) {
		if (only_gamma) {
			int i, c;
			int r, g, b, color;

			c = inwidth * inheight;
			for (i = 0; i < c; i++) {
				color = in[i];
				r = (color >> 0) & 0xFF;
				g = (color >> 8) & 0xFF;
				b = (color >> 16) & 0xFF;

				r = gammatable[r] & 0xFF;
				g = gammatable[g] & 0xFF;
				b = gammatable[b] & 0xFF;

				in[i] = (r << 0) | (g << 8) | (b << 16) | (color & 0xFF000000);
			}
		}
		else {
			int i, c;
			int r, g, b, color;

			c = inwidth * inheight;
			for (i = 0; i < c; i++) {
				color = in[i];
				r = (color >> 0) & 0xFF;
				g = (color >> 8) & 0xFF;
				b = (color >> 16) & 0xFF;

				r = gammatable[intensitytable[r] & 0xFF] & 0xFF;
				g = gammatable[intensitytable[g] & 0xFF] & 0xFF;
				b = gammatable[intensitytable[b] & 0xFF] & 0xFF;

				in[i] = (r << 0) | (g << 8) | (b << 16) | (color & 0xFF000000);
			}

		}
	}

	/*
	================
	GL_MipMap
	
	Operates in place, quartering the size of the texture
	================
	*/
	void GL_MipMap(int[] in, int width, int height) {
		int i, j;
		int[] out;

		out = in;

		int inIndex = 0;
		int outIndex = 0;

		int r, g, b, a;
		int p1, p2, p3, p4;

		for (i = 0; i < height; i += 2, inIndex += width) {
			for (j = 0; j < width; j += 2, outIndex += 1, inIndex += 2) {

				p1 = in[inIndex + 0];
				p2 = in[inIndex + 1];
				p3 = in[inIndex + width + 0];
				p4 = in[inIndex + width + 1];

				r = (((p1 >> 0) & 0xFF) + ((p2 >> 0) & 0xFF) + ((p3 >> 0) & 0xFF) + ((p4 >> 0) & 0xFF)) >> 2;
				g = (((p1 >> 8) & 0xFF) + ((p2 >> 8) & 0xFF) + ((p3 >> 8) & 0xFF) + ((p4 >> 8) & 0xFF)) >> 2;
				b = (((p1 >> 16) & 0xFF) + ((p2 >> 16) & 0xFF) + ((p3 >> 16) & 0xFF) + ((p4 >> 16) & 0xFF)) >> 2;
				a = (((p1 >> 24) & 0xFF) + ((p2 >> 24) & 0xFF) + ((p3 >> 24) & 0xFF) + ((p4 >> 24) & 0xFF)) >> 2;

				out[outIndex] = (r << 0) | (g << 8) | (b << 16) | (a << 24);
			}
		}
	}

	/*
	===============
	GL_Upload32
	
	Returns has_alpha
	===============
	*/
	void GL_BuildPalettedTexture(byte[] paletted_texture, int[] scaled, int scaled_width, int scaled_height) {

		int r, g, b, c;
		int size = scaled_width * scaled_height;

		for (int i = 0; i < size; i++) {

			r = (scaled[i] >> 3) & 31;
			g = (scaled[i] >> 10) & 63;
			b = (scaled[i] >> 19) & 31;

			c = r | (g << 5) | (b << 11);

			paletted_texture[i] = gl_state.d_16to8table[c];
		}
	}

	int upload_width, upload_height;
	boolean uploaded_paletted;

	/*
	===============
	GL_Upload32
	
	Returns has_alpha
	===============
	*/
	int[] scaled = new int[256 * 256];
	byte[] paletted_texture = new byte[256 * 256];
	IntBuffer tex = Lib.newIntBuffer(512 * 256, ByteOrder.LITTLE_ENDIAN);

	boolean GL_Upload32(int[] data, int width, int height, boolean mipmap) {
		int samples;
		int scaled_width, scaled_height;
		int i, c;
		int comp;

		Arrays.fill(scaled, 0);
		Arrays.fill(paletted_texture, (byte)0);

		uploaded_paletted = false;

		for (scaled_width = 1; scaled_width < width; scaled_width <<= 1);
		if (gl_round_down.value > 0.0f && scaled_width > width && mipmap)
			scaled_width >>= 1;
		for (scaled_height = 1; scaled_height < height; scaled_height <<= 1);
		if (gl_round_down.value > 0.0f && scaled_height > height && mipmap)
			scaled_height >>= 1;

		// let people sample down the world textures for speed
		if (mipmap) {
			scaled_width >>= (int) gl_picmip.value;
			scaled_height >>= (int) gl_picmip.value;
		}

		// don't ever bother with >256 textures
		if (scaled_width > 256)
			scaled_width = 256;
		if (scaled_height > 256)
			scaled_height = 256;

		if (scaled_width < 1)
			scaled_width = 1;
		if (scaled_height < 1)
			scaled_height = 1;

		upload_width = scaled_width;
		upload_height = scaled_height;

		if (scaled_width * scaled_height > 256 * 256)
			Com.Error(Defines.ERR_DROP, "GL_Upload32: too big");

		// scan the texture for any non-255 alpha
		c = width * height;
		samples = gl_solid_format;

		for (i = 0; i < c; i++) {
			if ((data[i] & 0xff000000) != 0xff000000) {
				samples = gl_alpha_format;
				break;
			}
		}

		if (samples == gl_solid_format)
			comp = gl_tex_solid_format;
		else if (samples == gl_alpha_format)
			comp = gl_tex_alpha_format;
		else {
			VID.Printf(Defines.PRINT_ALL, "Unknown number of texture components " + samples + '\n');
			comp = samples;
		}

		// simulates a goto
		try {
			if (scaled_width == width && scaled_height == height) {
				if (!mipmap) {
					if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f && samples == gl_solid_format) {
						uploaded_paletted = true;
						GL_BuildPalettedTexture(paletted_texture, data, scaled_width, scaled_height);
						gl.glTexImage2D(
							GL.GL_TEXTURE_2D,
							0,
							GL_COLOR_INDEX8_EXT,
							scaled_width,
							scaled_height,
							0,
							GL.GL_COLOR_INDEX,
							GL.GL_UNSIGNED_BYTE,
							paletted_texture);
					}
					else {
						tex.rewind(); tex.put(data);
						gl.glTexImage2D(
							GL.GL_TEXTURE_2D,
							0,
							comp,
							scaled_width,
							scaled_height,
							0,
							GL.GL_RGBA,
							GL.GL_UNSIGNED_BYTE,
							tex);
					}
					//goto done;
					throw new longjmpException();
				}
				//memcpy (scaled, data, width*height*4); were bytes
				System.arraycopy(data, 0, scaled, 0, width * height);

			}
			else
				GL_ResampleTexture(data, width, height, scaled, scaled_width, scaled_height);

			GL_LightScaleTexture(scaled, scaled_width, scaled_height, !mipmap);

			if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f && (samples == gl_solid_format)) {
				uploaded_paletted = true;
				GL_BuildPalettedTexture(paletted_texture, scaled, scaled_width, scaled_height);
				gl.glTexImage2D(
					GL.GL_TEXTURE_2D,
					0,
					GL_COLOR_INDEX8_EXT,
					scaled_width,
					scaled_height,
					0,
					GL.GL_COLOR_INDEX,
					GL.GL_UNSIGNED_BYTE,
					paletted_texture);
			}
			else {
				tex.rewind(); tex.put(scaled);
				gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, comp, scaled_width, scaled_height, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, tex);
			}

			if (mipmap) {
				int miplevel;
				miplevel = 0;
				while (scaled_width > 1 || scaled_height > 1) {
					GL_MipMap(scaled, scaled_width, scaled_height);
					scaled_width >>= 1;
					scaled_height >>= 1;
					if (scaled_width < 1)
						scaled_width = 1;
					if (scaled_height < 1)
						scaled_height = 1;

					miplevel++;
					if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f && samples == gl_solid_format) {
						uploaded_paletted = true;
						GL_BuildPalettedTexture(paletted_texture, scaled, scaled_width, scaled_height);
						gl.glTexImage2D(
							GL.GL_TEXTURE_2D,
							miplevel,
							GL_COLOR_INDEX8_EXT,
							scaled_width,
							scaled_height,
							0,
							GL.GL_COLOR_INDEX,
							GL.GL_UNSIGNED_BYTE,
							paletted_texture);
					}
					else {
						tex.rewind(); tex.put(scaled);
						gl.glTexImage2D(
							GL.GL_TEXTURE_2D,
							miplevel,
							comp,
							scaled_width,
							scaled_height,
							0,
							GL.GL_RGBA,
							GL.GL_UNSIGNED_BYTE,
							tex);
					}
				}
			}
			// label done:
		}
		catch (longjmpException e) {
			; // replaces label done
		}

		if (mipmap) {
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, gl_filter_min);
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, gl_filter_max);
		}
		else {
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, gl_filter_max);
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, gl_filter_max);
		}

		return (samples == gl_alpha_format);
	}

	/*
	===============
	GL_Upload8
	
	Returns has_alpha
	===============
	*/

	int[] trans = new int[512 * 256];

	boolean GL_Upload8(byte[] data, int width, int height, boolean mipmap, boolean is_sky) {
		
		Arrays.fill(trans, 0);

		int s = width * height;

		if (s > trans.length)
			Com.Error(Defines.ERR_DROP, "GL_Upload8: too large");

		if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f && is_sky) {
			gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL_COLOR_INDEX8_EXT, width, height, 0, GL.GL_COLOR_INDEX, GL.GL_UNSIGNED_BYTE, data);

			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, gl_filter_max);
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, gl_filter_max);

			// TODO check this
			return false;
		}
		else {
			int p;
			int rgb;
			for (int i = 0; i < s; i++) {
				p = data[i] & 0xff;
				trans[i] = d_8to24table[p];

				if (p == 255) { // transparent, so scan around for another color
					// to avoid alpha fringes
					// FIXME: do a full flood fill so mips work...
					if (i > width && (data[i - width] & 0xff) != 255)
						p = data[i - width] & 0xff;
					else if (i < s - width && (data[i + width] & 0xff) != 255)
						p = data[i + width] & 0xff;
					else if (i > 0 && (data[i - 1] & 0xff) != 255)
						p = data[i - 1] & 0xff;
					else if (i < s - 1 && (data[i + 1] & 0xff) != 255)
						p = data[i + 1] & 0xff;
					else
						p = 0;
					// copy rgb components

					// ((byte *)&trans[i])[0] = ((byte *)&d_8to24table[p])[0];
					// ((byte *)&trans[i])[1] = ((byte *)&d_8to24table[p])[1];
					// ((byte *)&trans[i])[2] = ((byte *)&d_8to24table[p])[2];

					trans[i] = d_8to24table[p] & 0x00FFFFFF; // only rgb
				}
			}

			return GL_Upload32(trans, width, height, mipmap);
		}
	}

	/*
	================
	GL_LoadPic
	
	This is also used as an entry point for the generated r_notexture
	================
	*/
	image_t GL_LoadPic(String name, byte[] pic, int width, int height, int type, int bits) {
		image_t image;
		int i;

		// find a free image_t
		for (i = 0; i<numgltextures ; i++)
		{
			image = gltextures[i];
			if (image.texnum == 0)
				break;
		}

		if (i == numgltextures)
		{
			if (numgltextures == MAX_GLTEXTURES)
				Com.Error (Defines.ERR_DROP, "MAX_GLTEXTURES");
			
			numgltextures++;
		}
		image = gltextures[i];

		if (name.length() > Defines.MAX_QPATH)
			Com.Error(Defines.ERR_DROP, "Draw_LoadPic: \"" + name + "\" is too long");

		image.name = name;
		image.registration_sequence = registration_sequence;

		image.width = width;
		image.height = height;
		image.type = type;


		if (type == it_skin && bits == 8)
			R_FloodFillSkin(pic, width, height);

		// load little pics into the scrap
		if (image.type == it_pic && bits == 8 && image.width < 64 && image.height < 64) {
			pos_t pos = new pos_t(0, 0);
			int j, k;

			int texnum = Scrap_AllocBlock(image.width, image.height, pos);

			if (texnum == -1) {
				// replace goto nonscrap

				image.scrap = false;
				
				image.texnum = TEXNUM_IMAGES + image.getId(); // image pos in array
				GL_Bind(image.texnum);

				if (bits == 8) {
					image.has_alpha =
						GL_Upload8(pic, width, height, (image.type != it_pic && image.type != it_sky), image.type == it_sky);
				}
				else {
					int[] tmp = new int[pic.length / 4];

					for (i = 0; i < tmp.length; i++) {
						tmp[i] = ((pic[4 * i + 0] & 0xFF) << 0); // & 0x000000FF;
						tmp[i] |= ((pic[4 * i + 1] & 0xFF) << 8); // & 0x0000FF00;
						tmp[i] |= ((pic[4 * i + 2] & 0xFF) << 16); // & 0x00FF0000;
						tmp[i] |= ((pic[4 * i + 3] & 0xFF) << 24); // & 0xFF000000;
					}

					image.has_alpha = GL_Upload32(tmp, width, height, (image.type != it_pic && image.type != it_sky));
				}

				image.upload_width = upload_width; // after power of 2 and scales
				image.upload_height = upload_height;
				image.paletted = uploaded_paletted;
				image.sl = 0;
				image.sh = 1;
				image.tl = 0;
				image.th = 1;

				return image;
			}

			scrap_dirty = true;

			// copy the texels into the scrap block
			k = 0;
			for (i = 0; i < image.height; i++)
				for (j = 0; j < image.width; j++, k++)
					scrap_texels[texnum][(pos.y + i) * BLOCK_WIDTH + pos.x + j] = pic[k];

			image.texnum = TEXNUM_SCRAPS + texnum;
			image.scrap = true;
			image.has_alpha = true;
			image.sl = (pos.x + 0.01f) / (float) BLOCK_WIDTH;
			image.sh = (pos.x + image.width - 0.01f) / (float) BLOCK_WIDTH;
			image.tl = (pos.y + 0.01f) / (float) BLOCK_WIDTH;
			image.th = (pos.y + image.height - 0.01f) / (float) BLOCK_WIDTH;

		}
		else {
			// this was label nonscrap

			image.scrap = false;

			image.texnum = TEXNUM_IMAGES + image.getId(); //image pos in array
			GL_Bind(image.texnum);

			if (bits == 8) {
				image.has_alpha = GL_Upload8(pic, width, height, (image.type != it_pic && image.type != it_sky), image.type == it_sky);
			}
			else {
				int[] tmp = new int[pic.length / 4];

				for (i = 0; i < tmp.length; i++) {
					tmp[i] = ((pic[4 * i + 0] & 0xFF) << 0); // & 0x000000FF;
					tmp[i] |= ((pic[4 * i + 1] & 0xFF) << 8); // & 0x0000FF00;
					tmp[i] |= ((pic[4 * i + 2] & 0xFF) << 16); // & 0x00FF0000;
					tmp[i] |= ((pic[4 * i + 3] & 0xFF) << 24); // & 0xFF000000;
				}

				image.has_alpha = GL_Upload32(tmp, width, height, (image.type != it_pic && image.type != it_sky));
			}
			image.upload_width = upload_width; // after power of 2 and scales
			image.upload_height = upload_height;
			image.paletted = uploaded_paletted;
			image.sl = 0;
			image.sh = 1;
			image.tl = 0;
			image.th = 1;
		}
		return image;
	}

	/*
	================
	GL_LoadWal
	================
	*/
	image_t GL_LoadWal(String name) {

		image_t image = null;

		byte[] raw = FS.LoadFile(name);
		if (raw == null) {
			VID.Printf(Defines.PRINT_ALL, "GL_FindImage: can't load " + name + '\n');
			return r_notexture;
		}

		qfiles.miptex_t mt = new qfiles.miptex_t(raw);

		byte[] pix = new byte[mt.width * mt.height];
		System.arraycopy(raw, mt.offsets[0], pix, 0, pix.length);

		image = GL_LoadPic(name, pix, mt.width, mt.height, it_wall, 8);

		return image;
	}

	/*
	===============
	GL_FindImage
	
	Finds or loads the given image
	===============
	*/
	image_t GL_FindImage(String name, int type) {
		image_t image = null;
		
		// TODO loest das grossschreibungs problem
		name = name.toLowerCase();
		// bughack for bad strings (fuck \0)
		int index = name.indexOf('\0');
		if (index != -1)
			name = name.substring(0, index);

		if (name == null || name.length() < 5)
			return null; //	Com.Error (ERR_DROP, "GL_FindImage: NULL name");
		//	Com.Error (ERR_DROP, "GL_FindImage: bad name: %s", name);

		// look for it
		for (int i = 0; i < numgltextures; i++)
		{
			image = gltextures[i];
			if (name.equals(image.name))
	        {
	             image.registration_sequence = registration_sequence;
	             return image;
	        }
		}

		//
		// load the pic from disk
		//
		byte[] pic = null;
		Dimension dim = new Dimension();

		if (name.endsWith(".pcx")) {

			pic = LoadPCX(name, null, dim);
			if (pic == null)
				return null;
			image = GL_LoadPic(name, pic, dim.width, dim.height, type, 8);

		}
		else if (name.endsWith(".wal")) {

			image = GL_LoadWal(name);

		}
		else if (name.endsWith(".tga")) {

			pic = LoadTGA(name, dim);

			if (pic == null)
				return null;

			image = GL_LoadPic(name, pic, dim.width, dim.height, type, 32);

		}
		else
			return null;

		return image;
	}

	/*
	===============
	R_RegisterSkin
	===============
	*/
	protected image_t R_RegisterSkin(String name) {
		return GL_FindImage(name, it_skin);
	}

	/*
	================
	GL_FreeUnusedImages
	
	Any image that was not touched on this registration sequence
	will be freed.
	================
	*/
	void GL_FreeUnusedImages() {

		// never free r_notexture or particle texture
		r_notexture.registration_sequence = registration_sequence;
		r_particletexture.registration_sequence = registration_sequence;

		image_t image = null;

		for (int i = 0; i < numgltextures; i++) {
			image = gltextures[i];
			// used this sequence
			if (image.registration_sequence == registration_sequence)
				continue;
			// free image_t slot
			if (image.registration_sequence == 0)
				continue;
			// don't free pics
			if (image.type == it_pic)
				continue;

			// free it
			// TODO jogl bug
			gl.glDeleteTextures(1, new int[] {image.texnum});
			image.clear();
		}
	}

	/*
	===============
	Draw_GetPalette
	===============
	*/
	protected void Draw_GetPalette() {
		int r, g, b;
		Dimension dim;
		byte[] pic;
		byte[][] palette = new byte[1][]; //new byte[768];

		// get the palette

		pic = LoadPCX("pics/colormap.pcx", palette, dim = new Dimension());

		if (palette[0] == null || palette[0].length != 768)
			Com.Error(Defines.ERR_FATAL, "Couldn't load pics/colormap.pcx");

		byte[] pal = palette[0];

		int j = 0;
		for (int i = 0; i < 256; i++) {
			r = pal[j++] & 0xFF;
			g = pal[j++] & 0xFF;
			b = pal[j++] & 0xFF;

			d_8to24table[i] = (255 << 24) | (b << 16) | (g << 8) | (r << 0);
		}

		d_8to24table[255] &= 0x00FFFFFF; // 255 is transparent
		
		particle_t.setColorPalette(d_8to24table);
	}

	/*
	===============
	GL_InitImages
	===============
	*/
	void GL_InitImages() {
		int i, j;
		float g = vid_gamma.value;

		registration_sequence = 1;

		// init intensity conversions
		intensity = Cvar.Get("intensity", "2", 0);

		if (intensity.value <= 1)
			Cvar.Set("intensity", "1");

		gl_state.inverse_intensity = 1 / intensity.value;

		Draw_GetPalette();

		if (qglColorTableEXT) {
			gl_state.d_16to8table = FS.LoadFile("pics/16to8.dat");
			if (gl_state.d_16to8table == null)
				Com.Error(Defines.ERR_FATAL, "Couldn't load pics/16to8.pcx");
		}

		if ((gl_config.renderer & (GL_RENDERER_VOODOO | GL_RENDERER_VOODOO2)) != 0) {
			g = 1.0F;
		}

		for (i = 0; i < 256; i++) {

			if (g == 1.0f) {
				gammatable[i] = (byte) i;
			}
			else {

				int inf = (int) (255.0f * Math.pow((i + 0.5) / 255.5, g) + 0.5);
				if (inf < 0)
					inf = 0;
				if (inf > 255)
					inf = 255;
				gammatable[i] = (byte) inf;
			}
		}

		for (i = 0; i < 256; i++) {
			j = (int) (i * intensity.value);
			if (j > 255)
				j = 255;
			intensitytable[i] = (byte) j;
		}
	}

	/*
	===============
	GL_ShutdownImages
	===============
	*/
	void GL_ShutdownImages() {
		image_t image;
		
		for (int i=0; i < numgltextures ; i++)
		{
			image = gltextures[i];
			
			if (image.registration_sequence == 0)
	   			continue; // free image_t slot
			// free it
			// TODO jogl bug
			gl.glDeleteTextures(1, new int[] {image.texnum});
	  		image.clear();
		}
	}

}
