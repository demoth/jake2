/*
 * Misc.java
 * Copyright (C) 2003
 *
 * $Id: Misc.java,v 1.8 2006-10-31 13:06:32 salomo Exp $
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
import jake2.qcommon.FS;
import jake2.qcommon.xcommand_t;
import jake2.render.common.Base;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import net.java.games.jogl.GL;
import net.java.games.jogl.WGL;

/**
 * Misc
 * 
 * @author cwei
 */
public abstract class Misc extends Image {

	//	/* 
	//	============================================================================== 
	// 
	//							SCREEN SHOTS 
	// 
	//	============================================================================== 
	//	*/ 
	//
	//	typedef struct _TargaHeader {
	//		unsigned char 	id_length, colormap_type, image_type;
	//		unsigned short	colormap_index, colormap_length;
	//		unsigned char	colormap_size;
	//		unsigned short	x_origin, y_origin, width, height;
	//		unsigned char	pixel_size, attributes;
	//	} TargaHeader;
	
	private final static int TGA_HEADER_SIZE = 18;

	/**
	 * GL_ScreenShot_f
	 */
	protected void GL_ScreenShot_f() {
		if (contextInUse) {
			screenshot_f();
		} else {
			updateScreen(screenshotCall);
		}
	}

	private xcommand_t screenshotCall = new xcommand_t() {
		public void execute() {
			screenshot_f();
		}
	};
	
	private void screenshot_f() {
	    StringBuffer sb = new StringBuffer(FS.Gamedir() + "/scrshot/jake00.tga");
	    FS.CreatePath(sb.toString());
	    File file = new File(sb.toString());
	    // find a valid file name
	    int i = 0; int offset = sb.length() - 6;
	    while (file.exists() && i++ < 100) {
	        sb.setCharAt(offset, (char) ((i/10) + '0'));
	        sb.setCharAt(offset + 1, (char) ((i%10) + '0'));
	        file = new File(sb.toString());
        }
	    if (i == 100) {
		    VID.Printf(Defines.PRINT_ALL, "Clean up your screenshots\n");
		    return;
	    }
	    
	    try {
	        RandomAccessFile out = new RandomAccessFile(file, "rw");
	        FileChannel ch = out.getChannel();
	        int fileLength = TGA_HEADER_SIZE + vid.width * vid.height * 3;
	        out.setLength(fileLength);
	        MappedByteBuffer image = ch.map(FileChannel.MapMode.READ_WRITE, 0,
	                fileLength);
	        
	        // write the TGA header
	        image.put(0, (byte) 0).put(1, (byte) 0);
	        image.put(2, (byte) 2); // uncompressed type
	        image.put(12, (byte) (vid.width & 0xFF)); // vid.width
	        image.put(13, (byte) (vid.width >> 8)); // vid.width
	        image.put(14, (byte) (vid.height & 0xFF)); // vid.height
	        image.put(15, (byte) (vid.height >> 8)); // vid.height
	        image.put(16, (byte) 24); // pixel size
	        
	        // go to image data position
	        image.position(TGA_HEADER_SIZE);
	        // jogl needs a sliced buffer
	        ByteBuffer rgb = image.slice();

	        // change pixel alignment for reading
	        if (vid.width % 4 != 0) {
	            gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1); 
	        }
	        
	        // OpenGL 1.2+ supports the GL_BGR color format
	        // check the GL_VERSION to use the TARGA BGR order if possible
	        // e.g.: 1.5.2 NVIDIA 66.29
	        if (gl_config.getOpenGLVersion() >= 1.2f) {
	            // read the BGR values into the image buffer
	            gl.glReadPixels(0, 0, vid.width, vid.height, GL.GL_BGR, GL.GL_UNSIGNED_BYTE, rgb);
	        } else {
	            // read the RGB values into the image buffer
	            gl.glReadPixels(0, 0, vid.width, vid.height, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, rgb);
		        // flip RGB to BGR
		        byte tmp;
		        for (i = TGA_HEADER_SIZE; i < fileLength; i += 3) {
		            tmp = image.get(i);
		            image.put(i, image.get(i + 2));
		            image.put(i + 2, tmp);
		        }
	        }
	        // reset to default alignment
	        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 4); 
	        // close the file channel
	        ch.close();
	    } catch (IOException e) {
		    VID.Printf(Defines.PRINT_ALL, e.getMessage() + '\n');
	    }

	    VID.Printf(Defines.PRINT_ALL, "Wrote " + file + '\n');
 	} 

	/*
	** GL_SetDefaultState
	*/
	protected void GL_SetDefaultState() {
		gl.glClearColor(1f, 0f, 0.5f, 0.5f); // original quake2
		//gl.glClearColor(0, 0, 0, 0); // replaced with black
		gl.glCullFace(GL.GL_FRONT);
		gl.glEnable(GL.GL_TEXTURE_2D);

		gl.glEnable(GL.GL_ALPHA_TEST);
		gl.glAlphaFunc(GL.GL_GREATER, 0.666f);

		gl.glDisable(GL.GL_DEPTH_TEST);
		gl.glDisable(GL.GL_CULL_FACE);
		gl.glDisable(GL.GL_BLEND);

		gl.glColor4f(1, 1, 1, 1);

		gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_FILL);
		gl.glShadeModel(GL.GL_FLAT);

		GL_TextureMode(gl_texturemode.string);
		GL_TextureAlphaMode(gl_texturealphamode.string);
		GL_TextureSolidMode(gl_texturesolidmode.string);

		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, gl_filter_min);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, gl_filter_max);

		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);

		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

		GL_TexEnv(GL.GL_REPLACE);

		if (qglPointParameterfEXT) {
			float[] attenuations = { gl_particle_att_a.value, gl_particle_att_b.value, gl_particle_att_c.value };

			gl.glEnable(GL.GL_POINT_SMOOTH);
			gl.glPointParameterfEXT(GL.GL_POINT_SIZE_MIN_EXT, gl_particle_min_size.value);
			gl.glPointParameterfEXT(GL.GL_POINT_SIZE_MAX_EXT, gl_particle_max_size.value);
			gl.glPointParameterfvEXT(GL.GL_DISTANCE_ATTENUATION_EXT, attenuations);
		}

		if (qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f) {
			gl.glEnable(GL.GL_SHARED_TEXTURE_PALETTE_EXT);

			GL_SetTexturePalette(d_8to24table);
		}

		GL_UpdateSwapInterval();

		/*
		 * vertex array extension
		 */
		gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
		gl.glClientActiveTextureARB(GL_TEXTURE0);
		gl.glEnableClientState(GL.GL_TEXTURE_COORD_ARRAY);
	}

	protected void GL_UpdateSwapInterval() {
		if (gl_swapinterval.modified) {
			gl_swapinterval.modified = false;
			if (!gl_state.stereo_enabled) {
				if (qwglSwapIntervalEXT) {
					((WGL) gl).wglSwapIntervalEXT((int) gl_swapinterval.value);
				}
			}
		}
	}
}
