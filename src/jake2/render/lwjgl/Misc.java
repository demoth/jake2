/*
 * Misc.java
 * Copyright (C) 2003
 *
 * $Id: Misc.java,v 1.4 2005-04-25 09:24:09 cawe Exp $
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
package jake2.render.lwjgl;

import jake2.Defines;
import jake2.client.VID;
import jake2.qcommon.FS;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTPointParameters;
import org.lwjgl.opengl.EXTSharedTexturePalette;
import org.lwjgl.opengl.GL11;

/**
 * Misc
 *  
 * @author cwei
 */
public abstract class Misc extends Mesh {

	/*
	==================
	R_InitParticleTexture
	==================
	*/
	byte[][] dottexture =
	{
		{0,0,0,0,0,0,0,0},
		{0,0,1,1,0,0,0,0},
		{0,1,1,1,1,0,0,0},
		{0,1,1,1,1,0,0,0},
		{0,0,1,1,0,0,0,0},
		{0,0,0,0,0,0,0,0},
		{0,0,0,0,0,0,0,0},
		{0,0,0,0,0,0,0,0},
	};

	void R_InitParticleTexture()
	{
		int		x,y;
		byte[] data = new byte[8 * 8 * 4];

		//
		// particle texture
		//
		for (x=0 ; x<8 ; x++)
		{
			for (y=0 ; y<8 ; y++)
			{
				data[y * 32 + x * 4 + 0] = (byte)255;
				data[y * 32 + x * 4 + 1] = (byte)255;
				data[y * 32 + x * 4 + 2] = (byte)255;
				data[y * 32 + x * 4 + 3] = (byte)(dottexture[x][y]*255);

			}
		}
		r_particletexture = GL_LoadPic("***particle***", data, 8, 8, it_sprite, 32);

		//
		// also use this for bad textures, but without alpha
		//
		for (x=0 ; x<8 ; x++)
		{
			for (y=0 ; y<8 ; y++)
			{
				data[y * 32 + x * 4 + 0] = (byte)(dottexture[x&3][y&3]*255);
				data[y * 32 + x * 4 + 1] = 0; // dottexture[x&3][y&3]*255;
				data[y * 32 + x * 4 + 2] = 0; //dottexture[x&3][y&3]*255;
				data[y * 32 + x * 4 + 3] = (byte)255;
			}
		}
		r_notexture = GL_LoadPic("***r_notexture***", data, 8, 8, it_wall, 32);
	}

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
	
	/* 
	================== 
	GL_ScreenShot_f
	================== 
	*/  
	void GL_ScreenShot_f() 
	{
	    FS.CreatePath(FS.Gamedir() + "/scrshot/jake00.tga");
	    File file = new File(FS.Gamedir() + "/scrshot/jake00.tga");
	    // find a valid file name
	    int i = 0;
	    while (file.exists() && i++ < 100) {
	        file = new File(FS.Gamedir() + "/scrshot/jake" + (i/10) + (i%10) + ".tga");
        }
	    if (i == 100) {
		    VID.Printf(Defines.PRINT_ALL, "Couldn't create a new screenshot file\n");
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
	        
	        // read the RGB values into the image buffer
	        gl.glReadPixels(0, 0, vid.width, vid.height, GL11.GL_RGB,
	                GL11.GL_UNSIGNED_BYTE, image);
	        
	        // flip RGB to BGR
	        byte tmp;
	        for (i = TGA_HEADER_SIZE; i < fileLength; i += 3) {
	            tmp = image.get(i);
	            image.put(i, image.get(i + 2));
	            image.put(i + 2, tmp);
	        }
	        // close the file channel
	        ch.close();
	    } catch (Exception e) {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
	    }

	    VID.Printf(Defines.PRINT_ALL, "Wrote " + file + '\n');
 	} 

	/*
	** GL_Strings_f
	*/
	void GL_Strings_f()	{
		VID.Printf(Defines.PRINT_ALL, "GL_VENDOR: " + gl_config.vendor_string + '\n');
		VID.Printf(Defines.PRINT_ALL, "GL_RENDERER: " + gl_config.renderer_string + '\n');
		VID.Printf(Defines.PRINT_ALL, "GL_VERSION: " + gl_config.version_string + '\n');
		VID.Printf(Defines.PRINT_ALL, "GL_EXTENSIONS: " + gl_config.extensions_string + '\n');
	}

	/*
	** GL_SetDefaultState
	*/
	void GL_SetDefaultState()
	{
		gl.glClearColor(1f,0f, 0.5f , 0.5f); // original quake2
		//gl.glClearColor(0, 0, 0, 0); // replaced with black
		gl.glCullFace(GL11.GL_FRONT);
		gl.glEnable(GL11.GL_TEXTURE_2D);

		gl.glEnable(GL11.GL_ALPHA_TEST);
		gl.glAlphaFunc(GL11.GL_GREATER, 0.666f);

		gl.glDisable (GL11.GL_DEPTH_TEST);
		gl.glDisable (GL11.GL_CULL_FACE);
		gl.glDisable (GL11.GL_BLEND);

		gl.glColor4f (1,1,1,1);

		gl.glPolygonMode (GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
		gl.glShadeModel (GL11.GL_FLAT);

		GL_TextureMode( gl_texturemode.string );
		GL_TextureAlphaMode( gl_texturealphamode.string );
		GL_TextureSolidMode( gl_texturesolidmode.string );

		gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, gl_filter_min);
		gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, gl_filter_max);

		gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

		gl.glBlendFunc (GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		GL_TexEnv( GL11.GL_REPLACE );

		if ( qglPointParameterfEXT )
		{
			// float[] attenuations = { gl_particle_att_a.value, gl_particle_att_b.value, gl_particle_att_c.value };
			FloatBuffer att_buffer=BufferUtils.createFloatBuffer(4);
			att_buffer.put(0,gl_particle_att_a.value);
			att_buffer.put(1,gl_particle_att_b.value);
			att_buffer.put(2,gl_particle_att_c.value);
			
			gl.glEnable( GL11.GL_POINT_SMOOTH );
			gl.glPointParameterfEXT( EXTPointParameters.GL_POINT_SIZE_MIN_EXT, gl_particle_min_size.value );
			gl.glPointParameterfEXT( EXTPointParameters.GL_POINT_SIZE_MAX_EXT, gl_particle_max_size.value );
			gl.glPointParameterEXT( EXTPointParameters.GL_DISTANCE_ATTENUATION_EXT, att_buffer );
		}

		if ( qglColorTableEXT && gl_ext_palettedtexture.value != 0.0f )
		{
			gl.glEnable( EXTSharedTexturePalette.GL_SHARED_TEXTURE_PALETTE_EXT );

			GL_SetTexturePalette( d_8to24table );
		}

		GL_UpdateSwapInterval();
		
		/*
		 * vertex array extension
		 */
		gl.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		gl.glClientActiveTextureARB(GL_TEXTURE0);
		gl.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
	}

	void GL_UpdateSwapInterval()
	{
		if ( gl_swapinterval.modified )
		{
			gl_swapinterval.modified = false;
			if ( !gl_state.stereo_enabled ) 
			{
				if (qwglSwapIntervalEXT) {
					// ((WGL)gl).wglSwapIntervalEXT((int)gl_swapinterval.value);
				}
			}
		}
	}
}
