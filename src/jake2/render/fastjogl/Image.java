/*
 * Image.java
 * Copyright (C) 2003
 *
 * $Id: Image.java,v 1.9 2006-10-31 13:06:32 salomo Exp $
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
import jake2.client.particle_t;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.render.image_t;
import jake2.render.common.Image.glmode_t;
import jake2.render.common.Image.gltmode_t;
import jake2.util.Lib;
import jake2.util.Vargs;

import java.awt.Dimension;
import java.nio.*;
import java.util.Arrays;

import net.java.games.jogl.GL;

/**
 * Image
 * 
 * @author cwei
 */
public abstract class Image extends Main {
	
	protected void GlutWireCube() {
		glut.glutWireCube(gl, 20);
	}
	
	protected void GL_GetWorldMatrix() {
			gl.glGetFloatv(GL.GL_MODELVIEW_MATRIX, r_world_matrix);
	}

    protected void GL_LoadMatrix()
    {
    	gl.glLoadMatrixf(r_world_matrix);
    }
	
	protected void GL_SetTexturePalette(int[] palette) {

		assert (palette != null && palette.length == 256) : "int palette[256] bug";

		int i;
		byte[] temptable = new byte[768];

		if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f) {
			for (i = 0; i < 256; i++) {
				temptable[i * 3 + 0] = (byte) ((palette[i] >> 0) & 0xff);
				temptable[i * 3 + 1] = (byte) ((palette[i] >> 8) & 0xff);
				temptable[i * 3 + 2] = (byte) ((palette[i] >> 16) & 0xff);
			}

			gl.glColorTableEXT(GL.GL_SHARED_TEXTURE_PALETTE_EXT, GL.GL_RGB,
					256, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, temptable);
		}
	}
	

	/*
	 * ==================================================
	 * 
	 * PCX LOADING
	 * 
	 * ==================================================
	 */

	int[] scaled = new int[256 * 256];

	byte[] paletted_texture = new byte[256 * 256];

	IntBuffer tex = Lib.newIntBuffer(512 * 256, ByteOrder.LITTLE_ENDIAN);

	/*
	 * GL_Upload32
	 * 
	 * Returns has_alpha
	 */
	protected boolean GL_Upload32(int[] data, int width, int height,
			boolean mipmap) {
		int samples;
		int scaled_width, scaled_height;
		int i, c;
		int comp;

		Arrays.fill(scaled, 0);
		Arrays.fill(paletted_texture, (byte) 0);

		uploaded_paletted = false;

		for (scaled_width = 1; scaled_width < width; scaled_width <<= 1)
			;
		if (gl_round_down.value > 0.0f && scaled_width > width && mipmap)
			scaled_width >>= 1;
		for (scaled_height = 1; scaled_height < height; scaled_height <<= 1)
			;
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
			VID.Printf(Defines.PRINT_ALL,
					"Unknown number of texture components " + samples + '\n');
			comp = samples;
		}

		// simulates a goto
		try {
			if (scaled_width == width && scaled_height == height) {
				if (!mipmap) {
					if (qglColorTableEXT
							&& gl_ext_palettedtexture.value != 0.0f
							&& samples == gl_solid_format) {
						uploaded_paletted = true;
						GL_BuildPalettedTexture(paletted_texture, data,
								scaled_width, scaled_height);
						gl.glTexImage2D(GL.GL_TEXTURE_2D, 0,
								GL_COLOR_INDEX8_EXT, scaled_width,
								scaled_height, 0, GL.GL_COLOR_INDEX,
								GL.GL_UNSIGNED_BYTE, paletted_texture);
					} else {
						tex.rewind();
						tex.put(data, 0, scaled_width * scaled_height);
						gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, comp,
								scaled_width, scaled_height, 0, GL.GL_RGBA,
								GL.GL_UNSIGNED_BYTE, tex);
					}
					// goto done;
					throw new longjmpException();
				}
				System.arraycopy(data, 0, scaled, 0, scaled_width * scaled_height);
			} else
				GL_ResampleTexture(data, width, height, scaled, scaled_width, scaled_height);

			GL_LightScaleTexture(scaled, scaled_width, scaled_height, !mipmap);

			if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f
					&& (samples == gl_solid_format)) {
				uploaded_paletted = true;
				GL_BuildPalettedTexture(paletted_texture, scaled, scaled_width,
						scaled_height);
				gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL_COLOR_INDEX8_EXT,
						scaled_width, scaled_height, 0, GL.GL_COLOR_INDEX,
						GL.GL_UNSIGNED_BYTE, paletted_texture);
			} else {
				tex.rewind();
				tex.put(scaled, 0, scaled_width * scaled_height);
				gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, comp, scaled_width,
						scaled_height, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, tex);
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
					if (qglColorTableEXT
							&& gl_ext_palettedtexture.value != 0.0f
							&& samples == gl_solid_format) {
						uploaded_paletted = true;
						GL_BuildPalettedTexture(paletted_texture, scaled,
								scaled_width, scaled_height);
						gl.glTexImage2D(GL.GL_TEXTURE_2D, miplevel,
								GL_COLOR_INDEX8_EXT, scaled_width,
								scaled_height, 0, GL.GL_COLOR_INDEX,
								GL.GL_UNSIGNED_BYTE, paletted_texture);
					} else {
						tex.rewind();
						tex.put(scaled, 0, scaled_width * scaled_height);
						gl.glTexImage2D(GL.GL_TEXTURE_2D, miplevel, comp,
								scaled_width, scaled_height, 0, GL.GL_RGBA,
								GL.GL_UNSIGNED_BYTE, tex);
					}
				}
			}
			// label done:
		} catch (longjmpException e) {
			// replaces label done
		}

		if (mipmap) {
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER,
					gl_filter_min);
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER,
					gl_filter_max);
		} else {
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER,
					gl_filter_max);
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER,
					gl_filter_max);
		}

		return (samples == gl_alpha_format);
	}

	int[] trans = new int[512 * 256];

	/*
	 * GL_Upload8
	 * 
	 * Returns has_alpha
	 */
	protected boolean GL_Upload8(byte[] data, int width, int height,
			boolean mipmap, boolean is_sky) {

		Arrays.fill(trans, 0);

		int s = width * height;

		if (s > trans.length)
			Com.Error(Defines.ERR_DROP, "GL_Upload8: too large");

		if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f && is_sky) {
			gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL_COLOR_INDEX8_EXT, width,
					height, 0, GL.GL_COLOR_INDEX, GL.GL_UNSIGNED_BYTE, data);

			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER,
					gl_filter_max);
			gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER,
					gl_filter_max);

			return false;
		}

		int p;
		for (int i = 0; i < s; i++) {
			p = data[i] & 0xff;
			trans[i] = d_8to24table[p];

			if (p == 255) { // transparent, so scan around for another
				// color
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
				trans[i] = d_8to24table[p] & 0x00FFFFFF; // only
				// rgb
			}
		}

		return GL_Upload32(trans, width, height, mipmap);

	}

	/*
	 * GL_FreeUnusedImages
	 * 
	 * Any image that was not touched on this registration sequence will be
	 * freed.
	 */
	protected void GL_FreeUnusedImages() {
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
			gl.glDeleteTextures(1, new int[] { image.texnum });
			image.clear();
		}
	}

	/*
	 * GL_ShutdownImages
	 */
	protected void GL_ShutdownImages() {
		image_t image;

		for (int i = 0; i < numgltextures; i++) {
			image = gltextures[i];

			if (image.registration_sequence == 0)
				continue; // free image_t slot
			// free it
			gl.glDeleteTextures(1, new int[] { image.texnum });
			image.clear();
		}
	}
}