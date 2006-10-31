/*
 * Main.java
 * Copyright (C) 2003
 *
 * $Id: Main.java,v 1.9 2006-10-31 13:06:32 salomo Exp $
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
import jake2.Globals;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.render.common.Base;
import jake2.render.common.Warp;
import jake2.util.Math3D;
import jake2.util.Vargs;

import java.awt.Dimension;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;

import net.java.games.jogl.GL;
import net.java.games.jogl.Version;

/**
 * Main
 * 
 * @author cwei
 */
public abstract class Main extends JoglBase {

	/*
	===============
	R_SetupFrame
	===============
	*/
	
	protected void R_SetupFrame() {
		int i;
		mleaf_t leaf;

		r_framecount++;

		//	build the transformation matrix for the given view angles
		Math3D.VectorCopy(r_newrefdef.vieworg, r_origin);

		Math3D.AngleVectors(r_newrefdef.viewangles, vpn, vright, vup);

		//	current viewcluster
		if ((r_newrefdef.rdflags & Defines.RDF_NOWORLDMODEL) == 0) {
			r_oldviewcluster = r_viewcluster;
			r_oldviewcluster2 = r_viewcluster2;
			leaf = Mod_PointInLeaf(r_origin, r_worldmodel);
			r_viewcluster = r_viewcluster2 = leaf.cluster;

			// check above and below so crossing solid water doesn't draw wrong
			if (leaf.contents == 0) { // look down a bit
				float[] temp = { 0, 0, 0 };
				Math3D.VectorCopy(r_origin, temp);
				temp[2] -= 16;
				leaf = Mod_PointInLeaf(temp, r_worldmodel);
				if ((leaf.contents & Defines.CONTENTS_SOLID) == 0 && (leaf.cluster != r_viewcluster2))
					r_viewcluster2 = leaf.cluster;
			}
			else { // look up a bit
				float[] temp = { 0, 0, 0 };
				Math3D.VectorCopy(r_origin, temp);
				temp[2] += 16;
				leaf = Mod_PointInLeaf(temp, r_worldmodel);
				if ((leaf.contents & Defines.CONTENTS_SOLID) == 0 && (leaf.cluster != r_viewcluster2))
					r_viewcluster2 = leaf.cluster;
			}
		}

		for (i = 0; i < 4; i++)
			v_blend[i] = r_newrefdef.blend[i];

		c_brush_polys = 0;
		c_alias_polys = 0;

		// clear out the portion of the screen that the NOWORLDMODEL defines
		if ((r_newrefdef.rdflags & Defines.RDF_NOWORLDMODEL) != 0) {
			gl.glEnable(GL.GL_SCISSOR_TEST);
			gl.glClearColor(0.3f, 0.3f, 0.3f, 1.0f);
			gl.glScissor(
				r_newrefdef.x,
				vid.height - r_newrefdef.height - r_newrefdef.y,
				r_newrefdef.width,
				r_newrefdef.height);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
			gl.glClearColor(1.0f, 0.0f, 0.5f, 0.5f);
			gl.glDisable(GL.GL_SCISSOR_TEST);
		}
	}


	protected boolean R_Init(int vid_xpos, int vid_ypos) {

		assert (Warp.SIN.length == 256) : "warpsin table bug";

		// fill r_turbsin
		for (int j = 0; j < 256; j++) {
			r_turbsin[j] = Warp.SIN[j] * 0.5f;
		}

		VID.Printf(Defines.PRINT_ALL, "ref_gl version: " + Base.REF_VERSION + '\n');

		Draw_GetPalette();

		R_Register();

		// set our "safe" modes
		gl_state.prev_mode = 3;

		// create the window and set up the context
		if (!R_SetMode()) {
			VID.Printf(Defines.PRINT_ALL,
					"ref_gl::R_Init() - could not R_SetMode()\n");
			return false;
		}
		return true;
	}

	protected boolean R_Init2() {
		VID.MenuInit();

		/*
		** get our various GL strings
		** get our various GL strings
		*/
		VID.Printf(Defines.PRINT_ALL, "JOGL_VERSION: " + Version.getVersion() + '\n');
		gl_config.vendor_string = gl.glGetString(GL.GL_VENDOR);
		VID.Printf(Defines.PRINT_ALL, "GL_VENDOR: " + gl_config.vendor_string + '\n');
		gl_config.renderer_string = gl.glGetString(GL.GL_RENDERER);
		VID.Printf(Defines.PRINT_ALL, "GL_RENDERER: " + gl_config.renderer_string + '\n');
		gl_config.version_string = gl.glGetString(GL.GL_VERSION);
		VID.Printf(Defines.PRINT_ALL, "GL_VERSION: " + gl_config.version_string + '\n');
		gl_config.extensions_string = gl.glGetString(GL.GL_EXTENSIONS);
		VID.Printf(Defines.PRINT_ALL, "GL_EXTENSIONS: " + gl_config.extensions_string + '\n');
		
		gl_config.parseOpenGLVersion();

		String renderer_buffer = gl_config.renderer_string.toLowerCase();
		String vendor_buffer = gl_config.vendor_string.toLowerCase();

		if (renderer_buffer.indexOf("voodoo") >= 0) {
			if (renderer_buffer.indexOf("rush") < 0)
				gl_config.renderer = Base.GL_RENDERER_VOODOO;
			else
				gl_config.renderer = Base.GL_RENDERER_VOODOO_RUSH;
		} else if (vendor_buffer.indexOf("sgi") >= 0)
			gl_config.renderer = Base.GL_RENDERER_SGI;
		else if (renderer_buffer.indexOf("permedia") >= 0)
			gl_config.renderer = Base.GL_RENDERER_PERMEDIA2;
		else if (renderer_buffer.indexOf("glint") >= 0)
			gl_config.renderer = Base.GL_RENDERER_GLINT_MX;
		else if (renderer_buffer.indexOf("glzicd") >= 0)
			gl_config.renderer = Base.GL_RENDERER_REALIZM;
		else if (renderer_buffer.indexOf("gdi") >= 0)
			gl_config.renderer = Base.GL_RENDERER_MCD;
		else if (renderer_buffer.indexOf("pcx2") >= 0)
			gl_config.renderer = Base.GL_RENDERER_PCX2;
		else if (renderer_buffer.indexOf("verite") >= 0)
			gl_config.renderer = Base.GL_RENDERER_RENDITION;
		else
			gl_config.renderer = Base.GL_RENDERER_OTHER;

		String monolightmap = gl_monolightmap.string.toUpperCase();
		if (monolightmap.length() < 2 || monolightmap.charAt(1) != 'F') {
			if (gl_config.renderer == Base.GL_RENDERER_PERMEDIA2) {
				Cvar.Set("gl_monolightmap", "A");
				VID.Printf(Defines.PRINT_ALL, "...using gl_monolightmap 'a'\n");
			}
			else if ((gl_config.renderer & Base.GL_RENDERER_POWERVR) != 0) {
				Cvar.Set("gl_monolightmap", "0");
			}
			else {
				Cvar.Set("gl_monolightmap", "0");
			}
		}

		// power vr can't have anything stay in the framebuffer, so
		// the screen needs to redraw the tiled background every frame
		if ((gl_config.renderer & Base.GL_RENDERER_POWERVR) != 0) {
			Cvar.Set("scr_drawall", "1");
		}
		else {
			Cvar.Set("scr_drawall", "0");
		}

		// MCD has buffering issues
		if (gl_config.renderer == Base.GL_RENDERER_MCD) {
			Cvar.SetValue("gl_finish", 1);
		}

		if ((gl_config.renderer & Base.GL_RENDERER_3DLABS) != 0) {
			if (gl_3dlabs_broken.value != 0.0f)
				gl_config.allow_cds = false;
			else
				gl_config.allow_cds = true;
		}
		else {
			gl_config.allow_cds = true;
		}

		if (gl_config.allow_cds)
			VID.Printf(Defines.PRINT_ALL, "...allowing CDS\n");
		else
			VID.Printf(Defines.PRINT_ALL, "...disabling CDS\n");

		/*
		** grab extensions
		*/
		if (gl_config.extensions_string.indexOf("GL_EXT_compiled_vertex_array") >= 0
			|| gl_config.extensions_string.indexOf("GL_SGI_compiled_vertex_array") >= 0) {
			VID.Printf(Defines.PRINT_ALL, "...enabling GL_EXT_compiled_vertex_array\n");
			//		 qglLockArraysEXT = ( void * ) qwglGetProcAddress( "glLockArraysEXT" );
			if (gl_ext_compiled_vertex_array.value != 0.0f)
				qglLockArraysEXT = true;
			else
				qglLockArraysEXT = false;
			//		 qglUnlockArraysEXT = ( void * ) qwglGetProcAddress( "glUnlockArraysEXT" );
			//qglUnlockArraysEXT = true;
		}
		else {
			VID.Printf(Defines.PRINT_ALL, "...GL_EXT_compiled_vertex_array not found\n");
			qglLockArraysEXT = false;
		}

		if (gl_config.extensions_string.indexOf("WGL_EXT_swap_control") >= 0) {
			qwglSwapIntervalEXT = true;
			VID.Printf(Defines.PRINT_ALL, "...enabling WGL_EXT_swap_control\n");
		} else {
			qwglSwapIntervalEXT = false;
			VID.Printf(Defines.PRINT_ALL, "...WGL_EXT_swap_control not found\n");
		}

		if (gl_config.extensions_string.indexOf("GL_EXT_point_parameters") >= 0) {
			if (gl_ext_pointparameters.value != 0.0f) {
				//			 qglPointParameterfEXT = ( void (APIENTRY *)( GLenum, GLfloat ) ) qwglGetProcAddress( "glPointParameterfEXT" );
				qglPointParameterfEXT = true;
				//			 qglPointParameterfvEXT = ( void (APIENTRY *)( GLenum, const GLfloat * ) ) qwglGetProcAddress( "glPointParameterfvEXT" );
				VID.Printf(Defines.PRINT_ALL, "...using GL_EXT_point_parameters\n");
			}
			else {
				VID.Printf(Defines.PRINT_ALL, "...ignoring GL_EXT_point_parameters\n");
			}
		}
		else {
			VID.Printf(Defines.PRINT_ALL, "...GL_EXT_point_parameters not found\n");
		}

		// #ifdef __linux__
		//	 if ( strstr( gl_config.extensions_string, "3DFX_set_global_palette" ))
		//	 {
		//		 if ( gl_ext_palettedtexture->value )
		//		 {
		//			 VID.Printf( Defines.PRINT_ALL, "...using 3DFX_set_global_palette\n" );
		//			 qgl3DfxSetPaletteEXT = ( void ( APIENTRY * ) (GLuint *) )qwglGetProcAddress( "gl3DfxSetPaletteEXT" );
		////			 qglColorTableEXT = Fake_glColorTableEXT;
		//		 }
		//		 else
		//		 {
		//			 VID.Printf( Defines.PRINT_ALL, "...ignoring 3DFX_set_global_palette\n" );
		//		 }
		//	 }
		//	 else
		//	 {
		//		 VID.Printf( Defines.PRINT_ALL, "...3DFX_set_global_palette not found\n" );
		//	 }
		// #endif

		if (!qglColorTableEXT
			&& gl_config.extensions_string.indexOf("GL_EXT_paletted_texture") >= 0
			&& gl_config.extensions_string.indexOf("GL_EXT_shared_texture_palette") >= 0) {
			if (gl_ext_palettedtexture.value != 0.0f) {
				VID.Printf(Defines.PRINT_ALL, "...using GL_EXT_shared_texture_palette\n");
				qglColorTableEXT = false; // true; TODO jogl bug
			}
			else {
				VID.Printf(Defines.PRINT_ALL, "...ignoring GL_EXT_shared_texture_palette\n");
				qglColorTableEXT = false;
			}
		}
		else {
			VID.Printf(Defines.PRINT_ALL, "...GL_EXT_shared_texture_palette not found\n");
		}

		if (gl_config.extensions_string.indexOf("GL_ARB_multitexture") >= 0) {
			VID.Printf(Defines.PRINT_ALL, "...using GL_ARB_multitexture\n");
			qglActiveTextureARB = true;
			GL_TEXTURE0 = GL.GL_TEXTURE0_ARB;
			GL_TEXTURE1 = GL.GL_TEXTURE1_ARB;
		}
		else {
			VID.Printf(Defines.PRINT_ALL, "...GL_ARB_multitexture not found\n");
		}

		if (!(qglActiveTextureARB))
			return false;

		GL_SetDefaultState();

		GL_InitImages();
		Mod_Init();
		R_InitParticleTexture();
		Draw_InitLocal();

		int err = gl.glGetError();
		if (err != GL.GL_NO_ERROR)
			VID.Printf(
				Defines.PRINT_ALL,
				"glGetError() = 0x%x\n\t%s\n",
				new Vargs(2).add(err).add("" + gl.glGetString(err)));

		return true;
	}

	/*
	@@@@@@@@@@@@@@@@@@@@@
	R_BeginFrame
	@@@@@@@@@@@@@@@@@@@@@
	*/
	protected void R_BeginFrame(float camera_separation) {

		gl_state.camera_separation = camera_separation;

		/*
		** change modes if necessary
		*/
		if (gl_mode.modified || vid_fullscreen.modified) {
			// FIXME: only restart if CDS is required
			cvar_t ref;

			ref = Cvar.Get("vid_ref", "fastjogl", 0);
			ref.modified = true;
		}

		if (gl_log.modified) {
			GLimp_EnableLogging((gl_log.value != 0.0f));
			gl_log.modified = false;
		}

		if (gl_log.value != 0.0f) {
			GLimp_LogNewFrame();
		}

		/*
		** update 3Dfx gamma -- it is expected that a user will do a vid_restart
		** after tweaking this value
		*/
		if (vid_gamma.modified) {
			vid_gamma.modified = false;

			if ((gl_config.renderer & Base.GL_RENDERER_VOODOO) != 0) {

				VID.Printf(Defines.PRINT_DEVELOPER,
						"gamma anpassung fuer VOODOO nicht gesetzt");
			}
		}

		GLimp_BeginFrame(camera_separation);

		/*
		** go into 2D mode
		*/
		gl.glViewport(0, 0, vid.width, vid.height);
		gl.glMatrixMode(GL.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0, vid.width, vid.height, 0, -99999, 99999);
		gl.glMatrixMode(GL.GL_MODELVIEW);
		gl.glLoadIdentity();
		gl.glDisable(GL.GL_DEPTH_TEST);
		gl.glDisable(GL.GL_CULL_FACE);
		gl.glDisable(GL.GL_BLEND);
		gl.glEnable(GL.GL_ALPHA_TEST);
		gl.glColor4f(1, 1, 1, 1);

		/*
		** draw buffer stuff
		*/
		if (gl_drawbuffer.modified) {
			gl_drawbuffer.modified = false;

			if (gl_state.camera_separation == 0 || !gl_state.stereo_enabled) {
				if (gl_drawbuffer.string.equalsIgnoreCase("GL_FRONT"))
					gl.glDrawBuffer(GL.GL_FRONT);
				else
					gl.glDrawBuffer(GL.GL_BACK);
			}
		}

		/*
		** texturemode stuff
		*/
		if (gl_texturemode.modified) {
			GL_TextureMode(gl_texturemode.string);
			gl_texturemode.modified = false;
		}

		if (gl_texturealphamode.modified) {
			GL_TextureAlphaMode(gl_texturealphamode.string);
			gl_texturealphamode.modified = false;
		}

		if (gl_texturesolidmode.modified) {
			GL_TextureSolidMode(gl_texturesolidmode.string);
			gl_texturesolidmode.modified = false;
		}

		/*
		** swapinterval stuff
		*/
		GL_UpdateSwapInterval();

		//
		// clear screen if desired
		//
		R_Clear();
	}

}
