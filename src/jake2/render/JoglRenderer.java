/*
 * JoglRenderer.java
 * Copyright (C) 2003
 *
 * $Id: JoglRenderer.java,v 1.13 2003-12-27 16:24:25 cwei Exp $
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
package jake2.render;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;


import jake2.render.jogl.*;

import net.java.games.jogl.*;

import jake2.Defines;
import jake2.client.refdef_t;
import jake2.client.refexport_t;
import jake2.client.refimport_t;
import jake2.qcommon.Cvar;

/**
 * JoglRenderer
 * 
 * @author cwei
 */
final class JoglRenderer extends Draw implements Ref, GLEventListener {

	static final String DRIVER_NAME = "jogl";

	static {
		Renderer.register(new JoglRenderer());
	};

	GLCanvas canvas;
	JFrame window;


	private JoglRenderer() {
	}

	// ============================================================================
	// public interface for Renderer implementations
	//
	// refexport_t (ref.h)
	// ============================================================================
	//
	/** 
	 * @see jake2.client.refexport_t#Init()
	 */
	public boolean Init() {
		// TODO remove gl_mode hack
		gl_mode.value = 3.0f;
		return R_Init();
	}

	/** 
	 * @see jake2.client.refexport_t#Shutdown()
	 */
	public void Shutdown() {
		R_Shutdown();
	}

	/** 
	 * @see jake2.client.refexport_t#BeginRegistration(java.lang.String)
	 */
	public void BeginRegistration(String map) {
		R_BeginRegistration(map);
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterModel(java.lang.String)
	 */
	public model_t RegisterModel(String name) {
		return R_RegisterModel(name);
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterSkin(java.lang.String)
	 */
	public image_t RegisterSkin(String name) {
		return R_RegisterSkin(name);
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterPic(java.lang.String)
	 */
	public image_t RegisterPic(String name) {
		return Draw_FindPic(name);
	}

	/** 
	 * @see jake2.client.refexport_t#SetSky(java.lang.String, float, float[])
	 */
	public void SetSky(String name, float rotate, float[] axis) {
		R_SetSky(name, rotate, axis);
	}

	/** 
	 * @see jake2.client.refexport_t#EndRegistration()
	 */
	public void EndRegistration() {
		R_EndRegistration();
	}

	/** 
	 * @see jake2.client.refexport_t#RenderFrame(jake2.client.refdef_t)
	 */
	public void RenderFrame(refdef_t fd) {
		R_RenderFrame(fd);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawGetPicSize(java.awt.Dimension, java.lang.String)
	 */
	public void DrawGetPicSize(Dimension dim, String name) {
		Draw_GetPicSize(dim, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawPic(int, int, java.lang.String)
	 */
	public void DrawPic(int x, int y, String name) {
		Draw_Pic(x, y, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawStretchPic(int, int, int, int, java.lang.String)
	 */
	public void DrawStretchPic(int x, int y, int w, int h, String name) {
		Draw_StretchPic(x, y, w, h, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawChar(int, int, int)
	 */
	public void DrawChar(int x, int y, int num) {
		Draw_Char(x, y, num);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawTileClear(int, int, int, int, java.lang.String)
	 */
	public void DrawTileClear(int x, int y, int w, int h, String name) {
		Draw_TileClear(x, y, w, h, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawFill(int, int, int, int, int)
	 */
	public void DrawFill(int x, int y, int w, int h, int c) {
		Draw_Fill(x, y, w, h, c);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawFadeScreen()
	 */
	public void DrawFadeScreen() {
		Draw_FadeScreen();
	}

	/** 
	 * @see jake2.client.refexport_t#DrawStretchRaw(int, int, int, int, int, int, byte[])
	 */
	public void DrawStretchRaw(int x, int y, int w, int h, int cols, int rows, byte[] data) {
		Draw_StretchRaw(x, y, w, h, cols, rows, data);
	}

	/** 
	 * @see jake2.client.refexport_t#CinematicSetPalette(byte[])
	 */
	public void CinematicSetPalette(byte[] palette) {
		R_SetPalette(palette);
	}

	/** 
	 * @see jake2.client.refexport_t#BeginFrame(float)
	 */
	public void BeginFrame(float camera_separation) {
		R_BeginFrame(camera_separation);
	}

	/** 
	 * @see jake2.client.refexport_t#EndFrame()
	 */
	public void EndFrame() {
		GLimp_EndFrame();
	}

	/** 
	 * @see jake2.client.refexport_t#AppActivate(boolean)
	 */
	public void AppActivate(boolean activate) {
		GLimp_AppActivate(activate);
	}

	// ============================================================================
	// Ref interface
	// ============================================================================

	public String getName() {
		return DRIVER_NAME;
	}

	public String toString() {
		return DRIVER_NAME;
	}

	public refexport_t GetRefAPI(refimport_t rimp) {
		this.ri = rimp;
		return this;
	}

	// ============================================================================
	// to port from gl_rmain.c, ...
	// ============================================================================
	/**
	 * @return
	 */
	private boolean R_Init() {
		//		/*
		//		===============
		//		R_Init
		//		===============
		//		*/
		//			char renderer_buffer[1000];
		//			char vendor_buffer[1000];
		//			int		err;
		//			int		j;
		//			extern float r_turbsin[256];
		//
		//			for ( j = 0; j < 256; j++ )
		//			{
		//				r_turbsin[j] *= 0.5;
		//			}
		//
		ri.Con_Printf(Defines.PRINT_ALL, "ref_gl version: " + REF_VERSION + '\n');
		//
		Draw_GetPalette();
		//
		R_Register();
		//
		//			// initialize our QGL dynamic bindings
		//			if ( !QGL_Init( gl_driver->string ) )
		//			{
		//				QGL_Shutdown();
		//				ri.Con_Printf (PRINT_ALL, "ref_gl::R_Init() - could not load \"%s\"\n", gl_driver->string );
		//				return -1;
		//			}
		//
		//			// initialize OS-specific parts of OpenGL
		if (!GLimp_Init()) {
			QGL_Shutdown();
			return false;
		}
		//
		//			// set our "safe" modes
		gl_state.prev_mode = 3;
		//
		//			// create the window and set up the context
		if (!R_SetMode()) {
			QGL_Shutdown();
			ri.Con_Printf(Defines.PRINT_ALL, "ref_gl::R_Init() - could not R_SetMode()\n");
			return false;
		}
		//
		ri.Vid_MenuInit();
		//
		//			/*
		//			** get our various GL strings
		//			*/
		//			gl_config.vendor_string = qglGetString (GL_VENDOR);
		//			ri.Con_Printf (PRINT_ALL, "GL_VENDOR: %s\n", gl_config.vendor_string );
		//			gl_config.renderer_string = qglGetString (GL_RENDERER);
		//			ri.Con_Printf (PRINT_ALL, "GL_RENDERER: %s\n", gl_config.renderer_string );
		//			gl_config.version_string = qglGetString (GL_VERSION);
		//			ri.Con_Printf (PRINT_ALL, "GL_VERSION: %s\n", gl_config.version_string );
		//			gl_config.extensions_string = qglGetString (GL_EXTENSIONS);
		//			ri.Con_Printf (PRINT_ALL, "GL_EXTENSIONS: %s\n", gl_config.extensions_string );
		//
		//			strcpy( renderer_buffer, gl_config.renderer_string );
		//			strlwr( renderer_buffer );
		//
		//			strcpy( vendor_buffer, gl_config.vendor_string );
		//			strlwr( vendor_buffer );
		//
		//			if ( strstr( renderer_buffer, "voodoo" ) )
		//			{
		//				if ( !strstr( renderer_buffer, "rush" ) )
		//					gl_config.renderer = GL_RENDERER_VOODOO;
		//				else
		//					gl_config.renderer = GL_RENDERER_VOODOO_RUSH;
		//			}
		//			else if ( strstr( vendor_buffer, "sgi" ) )
		//				gl_config.renderer = GL_RENDERER_SGI;
		//			else if ( strstr( renderer_buffer, "permedia" ) )
		//				gl_config.renderer = GL_RENDERER_PERMEDIA2;
		//			else if ( strstr( renderer_buffer, "glint" ) )
		//				gl_config.renderer = GL_RENDERER_GLINT_MX;
		//			else if ( strstr( renderer_buffer, "glzicd" ) )
		//				gl_config.renderer = GL_RENDERER_REALIZM;
		//			else if ( strstr( renderer_buffer, "gdi" ) )
		//				gl_config.renderer = GL_RENDERER_MCD;
		//			else if ( strstr( renderer_buffer, "pcx2" ) )
		//				gl_config.renderer = GL_RENDERER_PCX2;
		//			else if ( strstr( renderer_buffer, "verite" ) )
		//				gl_config.renderer = GL_RENDERER_RENDITION;
		//			else
		//				gl_config.renderer = GL_RENDERER_OTHER;
		//
		//			if ( toupper( gl_monolightmap->string[1] ) != 'F' )
		//			{
		//				if ( gl_config.renderer == GL_RENDERER_PERMEDIA2 )
		//				{
		//					ri.Cvar_Set( "gl_monolightmap", "A" );
		//					ri.Con_Printf( PRINT_ALL, "...using gl_monolightmap 'a'\n" );
		//				}
		//				else if ( gl_config.renderer & GL_RENDERER_POWERVR ) 
		//				{
		//					ri.Cvar_Set( "gl_monolightmap", "0" );
		//				}
		//				else
		//				{
		//					ri.Cvar_Set( "gl_monolightmap", "0" );
		//				}
		//			}
		//
		//			// power vr can't have anything stay in the framebuffer, so
		//			// the screen needs to redraw the tiled background every frame
		//			if ( gl_config.renderer & GL_RENDERER_POWERVR ) 
		//			{
		//				ri.Cvar_Set( "scr_drawall", "1" );
		//			}
		//			else
		//			{
		//				ri.Cvar_Set( "scr_drawall", "0" );
		//			}
		//
		//		#ifdef __linux__
		//			ri.Cvar_SetValue( "gl_finish", 1 );
		//		#endif
		//
		//			// MCD has buffering issues
		//			if ( gl_config.renderer == GL_RENDERER_MCD )
		//			{
		//				ri.Cvar_SetValue( "gl_finish", 1 );
		//			}
		//
		//			if ( gl_config.renderer & GL_RENDERER_3DLABS )
		//			{
		//				if ( gl_3dlabs_broken->value )
		//					gl_config.allow_cds = false;
		//				else
		//					gl_config.allow_cds = true;
		//			}
		//			else
		//			{
		//				gl_config.allow_cds = true;
		//			}
		//
		//			if ( gl_config.allow_cds )
		//				ri.Con_Printf( PRINT_ALL, "...allowing CDS\n" );
		//			else
		//				ri.Con_Printf( PRINT_ALL, "...disabling CDS\n" );
		//
		//			/*
		//			** grab extensions
		//			*/
		//			if ( strstr( gl_config.extensions_string, "GL_EXT_compiled_vertex_array" ) || 
		//				 strstr( gl_config.extensions_string, "GL_SGI_compiled_vertex_array" ) )
		//			{
		//				ri.Con_Printf( PRINT_ALL, "...enabling GL_EXT_compiled_vertex_array\n" );
		//				qglLockArraysEXT = ( void * ) qwglGetProcAddress( "glLockArraysEXT" );
		//				qglUnlockArraysEXT = ( void * ) qwglGetProcAddress( "glUnlockArraysEXT" );
		//			}
		//			else
		//			{
		//				ri.Con_Printf( PRINT_ALL, "...GL_EXT_compiled_vertex_array not found\n" );
		//			}
		//
		//		#ifdef _WIN32
		//			if ( strstr( gl_config.extensions_string, "WGL_EXT_swap_control" ) )
		//			{
		//				qwglSwapIntervalEXT = ( BOOL (WINAPI *)(int)) qwglGetProcAddress( "wglSwapIntervalEXT" );
		//				ri.Con_Printf( PRINT_ALL, "...enabling WGL_EXT_swap_control\n" );
		//			}
		//			else
		//			{
		//				ri.Con_Printf( PRINT_ALL, "...WGL_EXT_swap_control not found\n" );
		//			}
		//		#endif
		//
		//			if ( strstr( gl_config.extensions_string, "GL_EXT_point_parameters" ) )
		//			{
		//				if ( gl_ext_pointparameters->value )
		//				{
		//					qglPointParameterfEXT = ( void (APIENTRY *)( GLenum, GLfloat ) ) qwglGetProcAddress( "glPointParameterfEXT" );
		//					qglPointParameterfvEXT = ( void (APIENTRY *)( GLenum, const GLfloat * ) ) qwglGetProcAddress( "glPointParameterfvEXT" );
		//					ri.Con_Printf( PRINT_ALL, "...using GL_EXT_point_parameters\n" );
		//				}
		//				else
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...ignoring GL_EXT_point_parameters\n" );
		//				}
		//			}
		//			else
		//			{
		//				ri.Con_Printf( PRINT_ALL, "...GL_EXT_point_parameters not found\n" );
		//			}
		//
		//		#ifdef __linux__
		//			if ( strstr( gl_config.extensions_string, "3DFX_set_global_palette" ))
		//			{
		//				if ( gl_ext_palettedtexture->value )
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...using 3DFX_set_global_palette\n" );
		//					qgl3DfxSetPaletteEXT = ( void ( APIENTRY * ) (GLuint *) )qwglGetProcAddress( "gl3DfxSetPaletteEXT" );
		//					qglColorTableEXT = Fake_glColorTableEXT;
		//				}
		//				else
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...ignoring 3DFX_set_global_palette\n" );
		//				}
		//			}
		//			else
		//			{
		//				ri.Con_Printf( PRINT_ALL, "...3DFX_set_global_palette not found\n" );
		//			}
		//		#endif
		//
		//			if ( !qglColorTableEXT &&
		//				strstr( gl_config.extensions_string, "GL_EXT_paletted_texture" ) && 
		//				strstr( gl_config.extensions_string, "GL_EXT_shared_texture_palette" ) )
		//			{
		//				if ( gl_ext_palettedtexture->value )
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...using GL_EXT_shared_texture_palette\n" );
		//					qglColorTableEXT = ( void ( APIENTRY * ) ( int, int, int, int, int, const void * ) ) qwglGetProcAddress( "glColorTableEXT" );
		//				}
		//				else
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...ignoring GL_EXT_shared_texture_palette\n" );
		//				}
		//			}
		//			else
		//			{
		//				ri.Con_Printf( PRINT_ALL, "...GL_EXT_shared_texture_palette not found\n" );
		//			}
		//
		//			if ( strstr( gl_config.extensions_string, "GL_ARB_multitexture" ) )
		//			{
		//				if ( gl_ext_multitexture->value )
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...using GL_ARB_multitexture\n" );
		//					qglMTexCoord2fSGIS = ( void * ) qwglGetProcAddress( "glMultiTexCoord2fARB" );
		//					qglActiveTextureARB = ( void * ) qwglGetProcAddress( "glActiveTextureARB" );
		//					qglClientActiveTextureARB = ( void * ) qwglGetProcAddress( "glClientActiveTextureARB" );
		//					GL_TEXTURE0 = GL_TEXTURE0_ARB;
		//					GL_TEXTURE1 = GL_TEXTURE1_ARB;
		//				}
		//				else
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...ignoring GL_ARB_multitexture\n" );
		//				}
		//			}
		//			else
		//			{
		//				ri.Con_Printf( PRINT_ALL, "...GL_ARB_multitexture not found\n" );
		//			}
		//
		//			if ( strstr( gl_config.extensions_string, "GL_SGIS_multitexture" ) )
		//			{
		//				if ( qglActiveTextureARB )
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...GL_SGIS_multitexture deprecated in favor of ARB_multitexture\n" );
		//				}
		//				else if ( gl_ext_multitexture->value )
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...using GL_SGIS_multitexture\n" );
		//					qglMTexCoord2fSGIS = ( void * ) qwglGetProcAddress( "glMTexCoord2fSGIS" );
		//					qglSelectTextureSGIS = ( void * ) qwglGetProcAddress( "glSelectTextureSGIS" );
		//					GL_TEXTURE0 = GL_TEXTURE0_SGIS;
		//					GL_TEXTURE1 = GL_TEXTURE1_SGIS;
		//				}
		//				else
		//				{
		//					ri.Con_Printf( PRINT_ALL, "...ignoring GL_SGIS_multitexture\n" );
		//				}
		//			}
		//			else
		//			{
		//				ri.Con_Printf( PRINT_ALL, "...GL_SGIS_multitexture not found\n" );
		//			}
		//
		//			GL_SetDefaultState();
		//
		//			/*
		//			** draw our stereo patterns
		//			*/
		//			// commented out until H3D pays us the money they owe us
		//			// GL_DrawStereoPattern();
		//		
		//
		//			GL_InitImages ();
		//			Mod_Init ();
		//			R_InitParticleTexture ();
		//			Draw.InitLocal ();
		//
		//			err = qglGetError();
		//			if ( err != GL_NO_ERROR )
		//				ri.Con_Printf (PRINT_ALL, "glGetError() = 0x%x\n", err);
		//		}		return false;
		return true;
	}

	/**
	 * @return
	 */
	private boolean R_SetMode() {
		//		/*
		//		==================
		//		R_SetMode
		//		==================
		//		*/
		int err; //  enum rserr
		boolean fullscreen;

		if (vid_fullscreen.modified && !gl_config.allow_cds) {
			ri.Con_Printf(Defines.PRINT_ALL, "R_SetMode() - CDS not allowed with this driver\n");
			ri.Cvar_SetValue("vid_fullscreen", (vid_fullscreen.value > 0.0f) ? 0.0f : 1.0f);
			vid_fullscreen.modified = false;
		}
		//
		fullscreen = (vid_fullscreen.value > 0.0f);
		//
		vid_fullscreen.modified = false;
		gl_mode.modified = false;
		//
		
		Dimension dim = new Dimension(vid.width, vid.height);
		
		if ((err = GLimp_SetMode(dim, (int)gl_mode.value, fullscreen)) == rserr.ok) {
			gl_state.prev_mode = (int)gl_mode.value;
		} else {
			if (err == rserr.invalid_fullscreen) {
				ri.Cvar_SetValue("vid_fullscreen", 0);
				vid_fullscreen.modified = false;
				ri.Con_Printf(Defines.PRINT_ALL,	"ref_gl::R_SetMode() - fullscreen unavailable in this mode\n");
				if ((err = GLimp_SetMode(dim, (int)gl_mode.value, false)) == rserr.ok)
					return true;
			} else if (err == rserr.invalid_mode) {
				ri.Cvar_SetValue("gl_mode", gl_state.prev_mode);
				gl_mode.modified = false;
				ri.Con_Printf(Defines.PRINT_ALL,	"ref_gl::R_SetMode() - invalid mode\n");
			}
			//
			//				// try setting it back to something safe
			if ((err = GLimp_SetMode(dim, gl_state.prev_mode, false)) != rserr.ok) {
				ri.Con_Printf(Defines.PRINT_ALL, "ref_gl::R_SetMode() - could not revert to safe mode\n");
				return false;
			}
		}
		return true;
	}

	/**
	 * @param dim
	 * @param mode
	 * @param fullscreen
	 * @return
	 */
	private int GLimp_SetMode(Dimension dim, int mode, boolean fullscreen) {

		Dimension newDim = new Dimension();

		ri.Cvar_Get("r_fakeFullscreen", "0", Cvar.ARCHIVE);

		ri.Con_Printf(Defines.PRINT_ALL, "Initializing OpenGL display\n", null);

		if (fullscreen) {
			ri.Con_Printf(Defines.PRINT_ALL, "...setting fullscreen mode " + mode + ":");
		} else
			ri.Con_Printf(Defines.PRINT_ALL, "...setting mode " + mode + ":");

		if (!ri.Vid_GetModeInfo(newDim, mode)) {
			ri.Con_Printf(Defines.PRINT_ALL, " invalid mode\n");
			return rserr.invalid_mode;
		}
		
		ri.Con_Printf(Defines.PRINT_ALL,	" " + newDim.width + " " + newDim.height + '\n');

		// destroy the existing window
		GLimp_Shutdown();

		// TODO Open an JFrame	

		window = new JFrame("Jake2");
		GLCanvas canvas =
			GLDrawableFactory.getFactory().createGLCanvas(new GLCapabilities());

		// Use debug pipeline
		canvas.setGL(new DebugGL(canvas.getGL()));
		//canvas.setGL(canvas.getGL());

		//canvas.setRenderingThread(Thread.currentThread());

		canvas.setNoAutoRedrawMode(true);
		canvas.addGLEventListener(this);
		window.getContentPane().add(canvas);

		window.setSize(newDim.width, newDim.height);

		window.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		window.show();
		this.canvas = canvas;

		vid.width = newDim.width;
		vid.height = newDim.height;

		// let the sound and input subsystems know about the new window
		ri.Vid_NewWindow(vid.width, vid.height);
		
		canvas.display();
		
		return rserr.ok;
	}

	/**
	 * 
	 */
	private void GLimp_Shutdown() {
		if (this.window != null) {
			window.dispose();
		}
	}

	/**
	 * 
	 */
	private void R_Register() {
		// TODO Auto-generated method stub

	}


	/**
	 * 
	 */
	private void QGL_Shutdown() {
		// TODO Auto-generated method stub

	}

	/**
	 * @return true
	 */
	private boolean GLimp_Init() {
		// do nothing
		return true;
	}

	/**
	 * 
	 */
	private void R_Shutdown() {
		// TODO Auto-generated method stub

	}

	/**
	 * @param map
	 */
	private void R_BeginRegistration(String map) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param name
	 * @return
	 */
	private model_t R_RegisterModel(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param name
	 * @return
	 */
	private image_t R_RegisterSkin(String name) {
		// TODO Auto-generated method stub
		return null;
	}



	/**
	 * @param name
	 * @param rotate
	 * @param axis
	 */
	private void R_SetSky(String name, float rotate, float[] axis) {
		assert(axis.length == 3) : "vec3_t bug";
		// TODO Auto-generated method stub

	}

	/**
	 * 
	 */
	private void R_EndRegistration() {
		// TODO Auto-generated method stub

	}

	/**
	 * @param fd
	 */
	private void R_RenderFrame(refdef_t fd) {
		// TODO Auto-generated method stub

	}


	/**
	 * @param palette
	 */
	private void R_SetPalette(byte[] palette) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param camera_separation
	 */
	private void R_BeginFrame(float camera_separation) {
		// TODO Auto-generated method stub
	}

	/**
	 * 
	 */
	private void GLimp_EndFrame() {
		gl.glFlush();
		// swap buffer
		// but jogl has no method to swap
	}

	/**
	 * @param activate
	 */
	private void GLimp_AppActivate(boolean activate) {
		// TODO Auto-generated method stub

	}

	// ============================================================================
	// GLEventListener interface
	// ============================================================================

	/* 
	* @see net.java.games.jogl.GLEventListener#init(net.java.games.jogl.GLDrawable)
	*/
	public void init(GLDrawable drawable) {
		this.gl = drawable.getGL();
		this.glu = drawable.getGLU();

		/*  select clearing (background) color       */
			gl.glClearColor (0.0f, 0.0f, 0.0f, 0.0f);

		/*  initialize viewing values  */
//			gl.glMatrixMode(GL.GL_PROJECTION);
//			gl.glLoadIdentity();
//			gl.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
		   gl.glShadeModel(GL.GL_FLAT);
		   gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);

		// TODO opengl init
	}

	/* 
	 * @see net.java.games.jogl.GLEventListener#display(net.java.games.jogl.GLDrawable)
	 */
	public void display(GLDrawable drawable) {
		this.gl = drawable.getGL();
		this.glu = drawable.getGLU();
		
		ri.updateScreenCallback();		
	}
	
	/* 
	* @see net.java.games.jogl.GLEventListener#displayChanged(net.java.games.jogl.GLDrawable, boolean, boolean)
	*/
	public void displayChanged(
		GLDrawable drawable,
		boolean arg1,
		boolean arg2) {
		// do nothing
	}

	/* 
	* @see net.java.games.jogl.GLEventListener#reshape(net.java.games.jogl.GLDrawable, int, int, int, int)
	*/
	public void reshape(
		GLDrawable drawable,
		int arg1,
		int arg2,
		int arg3,
		int arg4) {
		// do nothing
	}

	/* 
	 * @see jake2.client.refexport_t#updateScreen()
	 */
	public void updateScreen() {
		if (canvas == null) {
			throw new IllegalStateException(
					"Refresh modul \"" + DRIVER_NAME + "\" have to be initialized.");
		}
		canvas.display();
	}
}