/*
 * Misc.java
 * Copyright (C) 2003
 *
 * $Id: Misc.java,v 1.2 2004-12-14 12:56:59 cawe Exp $
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

import java.nio.FloatBuffer;

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


	/* 
	================== 
	GL_ScreenShot_f
	================== 
	*/  
	void GL_ScreenShot_f() 
	{
//		byte		*buffer;
//		char		picname[80]; 
//		char		checkname[MAX_OSPATH];
//		int			i, c, temp;
//		FILE		*f;
//
//		// create the scrnshots directory if it doesn't exist
//		Com_sprintf (checkname, sizeof(checkname), "%s/scrnshot", ri.FS_Gamedir());
//		Sys_Mkdir (checkname);
//
//// 
////	   find a file name to save it to 
//// 
//		strcpy(picname,"quake00.tga");
//
//		for (i=0 ; i<=99 ; i++) 
//		{ 
//			picname[5] = i/10 + '0'; 
//			picname[6] = i%10 + '0'; 
//			Com_sprintf (checkname, sizeof(checkname), "%s/scrnshot/%s", ri.FS_Gamedir(), picname);
//			f = fopen (checkname, "r");
//			if (!f)
//				break;	// file doesn't exist
//			fclose (f);
//		} 
//		if (i==100) 
//		{
//			VID.Printf (PRINT_ALL, "SCR_ScreenShot_f: Couldn't create a file\n"); 
//			return;
//		}
//
//
//		buffer = malloc(vid.width*vid.height*3 + 18);
//		memset (buffer, 0, 18);
//		buffer[2] = 2;		// uncompressed type
//		buffer[12] = vid.width&255;
//		buffer[13] = vid.width>>8;
//		buffer[14] = vid.height&255;
//		buffer[15] = vid.height>>8;
//		buffer[16] = 24;	// pixel size
//
//		qglReadPixels (0, 0, vid.width, vid.height, GL_RGB, GL_UNSIGNED_BYTE, buffer+18 ); 
//
//		// swap rgb to bgr
//		c = 18+vid.width*vid.height*3;
//		for (i=18 ; i<c ; i+=3)
//		{
//			temp = buffer[i];
//			buffer[i] = buffer[i+2];
//			buffer[i+2] = temp;
//		}
//
//		f = fopen (checkname, "rw");
//		fwrite (buffer, 1, c, f);
//		fclose (f);
//
//		free (buffer);
//		VID.Printf (PRINT_ALL, "Wrote %s\n", picname);
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
