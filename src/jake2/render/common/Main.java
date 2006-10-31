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

// Created on 19.08.2006 by RST.

// $Id: Main.java,v 1.1 2006-10-31 13:06:32 salomo Exp $

package jake2.render.common;

import jake2.Defines;
import jake2.Globals;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.util.Math3D;
import jake2.util.Vargs;

import java.awt.Dimension;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public abstract class Main extends Base {

	// =================
	// abstract methods
	// =================
	protected abstract void Draw_GetPalette();
	protected abstract void Draw_InitLocal();
	
	protected abstract boolean GL_Upload32(int[] data, int width, int height, boolean mipmap);
	protected abstract boolean GL_Upload8(byte[] data, int width, int height, boolean mipmap, boolean is_sky);
	protected abstract void GL_Bind(int texnum);
	protected abstract void GL_SelectTexture(int target);
	protected abstract void GL_ImageList_f();
	protected abstract void GL_InitImages();
	protected abstract void GL_TextureAlphaMode(String name);
	protected abstract void GL_TextureSolidMode(String name);
	protected abstract void GL_ScreenShot_f();
	protected abstract void GL_SetTexturePalette(int[] palette);
	protected abstract void GL_Strings_f();
	protected abstract void GL_SetDefaultState();
	protected abstract void GL_ShutdownImages();
	protected abstract void GL_TexEnv(int mode);
	protected abstract void GL_TextureMode(String string);
	protected abstract void GL_UpdateSwapInterval();
	protected abstract image_t GL_FindImage(String name, int type);
	protected abstract void GL_FreeUnusedImages();
	protected abstract void GL_EndBuildingLightmaps();
	protected abstract void GL_BeginBuildingLightmaps(model_t model);
	protected abstract void GL_BuildPolygonFromSurface(msurface_t surface);
	protected abstract void GL_CreateSurfaceLightmap(msurface_t survace);
	protected abstract void GL_SubdivideSurface(msurface_t survace);

	
	protected abstract void R_InitParticleTexture(); // MIsc.java
	protected abstract void R_DrawAliasModel(entity_t e); // Mesh.java
	protected abstract void R_DrawBrushModel(entity_t e); // Surf.java
	protected abstract void R_LightPoint(float[] p, float[] color);
	protected abstract void R_PushDlights();
	protected abstract void R_MarkLeaves();
	protected abstract void R_DrawWorld();
	protected abstract void R_RenderDlights();
	protected abstract void R_DrawAlphaSurfaces();
	protected abstract void R_SetupFrame();
	
	protected abstract void Mod_Modellist_f();
	protected abstract void Mod_FreeAll();
	protected abstract void GLimp_Finish();
	protected abstract void GLimp_Shutdown();
	protected abstract int GLimp_SetMode(Dimension dim, int mode, boolean fullscreen);
	
	protected static int[] d_8to24table = new int[256];

	protected int c_visible_lightmaps;

	protected int c_visible_textures;

	protected int registration_sequence;

	// this a hack for function pointer test
	// default disabled
	protected boolean qglColorTableEXT = true;

	protected boolean qglActiveTextureARB = false;

	protected boolean qglPointParameterfEXT = false;

	protected boolean qglLockArraysEXT = false;

	protected boolean qwglSwapIntervalEXT = false;
	
	protected static final int NUM_BEAM_SEGS = 6;

	protected float[][] start_points = new float[NUM_BEAM_SEGS][3];

	// array of vec3_t
	protected float[][] end_points = new float[NUM_BEAM_SEGS][3]; // array of vec3_t

	/*
	 * ====================================================================
	 * 
	 * from gl_rmain.c
	 * 
	 * ====================================================================
	 */

	protected model_t r_worldmodel;

	protected float gldepthmin, gldepthmax;

	protected glconfig_t gl_config = new glconfig_t();

	protected glstate_t gl_state = new glstate_t();

	protected image_t r_notexture; // use for bad textures

	protected image_t r_particletexture; // little dot for particles

	protected entity_t currententity;

	protected model_t currentmodel;

	protected cplane_t frustum[] = { new cplane_t(), new cplane_t(), new cplane_t(),
			new cplane_t() };

	protected int r_visframecount; // bumped when going to a new PVS

	protected int r_framecount; // used for dlight push checking

	protected int c_brush_polys, c_alias_polys;

	protected float v_blend[] = { 0, 0, 0, 0 }; // final blending color

	//
	// view origin
	//
	protected float[] vup = { 0, 0, 0 };

	protected float[] vpn = { 0, 0, 0 };

	protected float[] vright = { 0, 0, 0 };

	protected float[] r_origin = { 0, 0, 0 };

	protected float r_world_matrix[] = new float[16];

	protected float r_base_world_matrix[] = new float[16];

	//
	// screen size info
	//
	protected refdef_t r_newrefdef = new refdef_t();

	protected int r_viewcluster, r_viewcluster2, r_oldviewcluster, r_oldviewcluster2;

	protected cvar_t r_norefresh;

	protected cvar_t r_drawentities;

	protected cvar_t r_drawworld;

	protected cvar_t r_speeds;

	protected cvar_t r_fullbright;

	protected cvar_t r_novis;

	protected cvar_t r_nocull;

	protected cvar_t r_lerpmodels;

	protected cvar_t r_lefthand;

	protected cvar_t r_lightlevel;

	protected cvar_t gl_nosubimage;

	protected cvar_t gl_allow_software;

	protected cvar_t gl_vertex_arrays;

	protected cvar_t gl_particle_min_size;

	protected cvar_t gl_particle_max_size;

	protected cvar_t gl_particle_size;

	protected cvar_t gl_particle_att_a;

	protected cvar_t gl_particle_att_b;

	protected cvar_t gl_particle_att_c;

	protected cvar_t gl_ext_swapinterval;

	protected cvar_t gl_ext_palettedtexture;

	protected cvar_t gl_ext_multitexture;

	protected cvar_t gl_ext_pointparameters;

	protected cvar_t gl_ext_compiled_vertex_array;

	protected cvar_t gl_log;

	protected cvar_t gl_bitdepth;

	protected cvar_t gl_drawbuffer;

	protected cvar_t gl_driver;

	protected cvar_t gl_lightmap;

	protected cvar_t gl_shadows;

	protected cvar_t gl_mode;

	protected cvar_t gl_dynamic;

	protected cvar_t gl_monolightmap;

	protected cvar_t gl_modulate;

	protected cvar_t gl_nobind;

	protected cvar_t gl_round_down;

	protected cvar_t gl_picmip;

	protected cvar_t gl_skymip;

	protected cvar_t gl_showtris;

	protected cvar_t gl_ztrick;

	protected cvar_t gl_finish;

	protected cvar_t gl_clear;

	protected cvar_t gl_cull;

	protected cvar_t gl_polyblend;

	protected cvar_t gl_flashblend;

	protected cvar_t gl_playermip;

	protected cvar_t gl_saturatelighting;

	protected cvar_t gl_swapinterval;

	protected cvar_t gl_texturemode;

	protected cvar_t gl_texturealphamode;

	protected cvar_t gl_texturesolidmode;

	protected cvar_t gl_lockpvs;

	protected cvar_t gl_3dlabs_broken;

	protected cvar_t vid_gamma;

	protected cvar_t vid_ref;
	
	protected int[] r_rawpalette = new int[256];
	
	protected float[] r_turbsin = new float[256];
	
	protected int trickframe = 0;
	
	protected cvar_t vid_fullscreen;
	
	// window position on the screen
	protected int window_xpos, window_ypos;
	protected viddef_t vid = new viddef_t();
	
	// enum rserr_t
	protected static final int rserr_ok = 0;
	protected static final int rserr_invalid_fullscreen = 1;
	protected static final int rserr_invalid_mode = 2;
	protected static final int rserr_unknown = 3;
	
	
		/**
	 * R_Register
	 */
	protected void R_Register() {
		r_lefthand = Cvar.Get("hand", "0", Globals.CVAR_USERINFO | Globals.CVAR_ARCHIVE);
		r_norefresh = Cvar.Get("r_norefresh", "0", 0);
		r_fullbright = Cvar.Get("r_fullbright", "0", 0);
		r_drawentities = Cvar.Get("r_drawentities", "1", 0);
		r_drawworld = Cvar.Get("r_drawworld", "1", 0);
		r_novis = Cvar.Get("r_novis", "0", 0);
		r_nocull = Cvar.Get("r_nocull", "0", 0);
		r_lerpmodels = Cvar.Get("r_lerpmodels", "1", 0);
		r_speeds = Cvar.Get("r_speeds", "0", 0);

		r_lightlevel = Cvar.Get("r_lightlevel", "1", 0);

		gl_nosubimage = Cvar.Get("gl_nosubimage", "0", 0);
		gl_allow_software = Cvar.Get("gl_allow_software", "0", 0);

		gl_particle_min_size = Cvar.Get("gl_particle_min_size", "2", Globals.CVAR_ARCHIVE);
		gl_particle_max_size = Cvar.Get("gl_particle_max_size", "40", Globals.CVAR_ARCHIVE);
		gl_particle_size = Cvar.Get("gl_particle_size", "40", Globals.CVAR_ARCHIVE);
		gl_particle_att_a = Cvar.Get("gl_particle_att_a", "0.01", Globals.CVAR_ARCHIVE);
		gl_particle_att_b = Cvar.Get("gl_particle_att_b", "0.0", Globals.CVAR_ARCHIVE);
		gl_particle_att_c = Cvar.Get("gl_particle_att_c", "0.01", Globals.CVAR_ARCHIVE);

		gl_modulate = Cvar.Get("gl_modulate", "1.5", Globals.CVAR_ARCHIVE);
		gl_log = Cvar.Get("gl_log", "0", 0);
		gl_bitdepth = Cvar.Get("gl_bitdepth", "0", 0);
		gl_mode = Cvar.Get("gl_mode", "3", Globals.CVAR_ARCHIVE); // 640x480
		gl_lightmap = Cvar.Get("gl_lightmap", "0", 0);
		gl_shadows = Cvar.Get("gl_shadows", "0", Globals.CVAR_ARCHIVE);
		gl_dynamic = Cvar.Get("gl_dynamic", "1", 0);
		gl_nobind = Cvar.Get("gl_nobind", "0", 0);
		gl_round_down = Cvar.Get("gl_round_down", "1", 0);
		gl_picmip = Cvar.Get("gl_picmip", "0", 0);
		gl_skymip = Cvar.Get("gl_skymip", "0", 0);
		gl_showtris = Cvar.Get("gl_showtris", "0", 0);
		gl_ztrick = Cvar.Get("gl_ztrick", "0", 0);
		gl_finish = Cvar.Get("gl_finish", "0", Globals.CVAR_ARCHIVE);
		gl_clear = Cvar.Get("gl_clear", "0", 0);
		gl_cull = Cvar.Get("gl_cull", "1", 0);
		gl_polyblend = Cvar.Get("gl_polyblend", "1", 0);
		gl_flashblend = Cvar.Get("gl_flashblend", "0", 0);
		gl_playermip = Cvar.Get("gl_playermip", "0", 0);
		gl_monolightmap = Cvar.Get("gl_monolightmap", "0", 0);
		gl_driver = Cvar.Get("gl_driver", "opengl32", Globals.CVAR_ARCHIVE);
		gl_texturemode = Cvar.Get("gl_texturemode", "GL_LINEAR_MIPMAP_NEAREST", Globals.CVAR_ARCHIVE);
		gl_texturealphamode = Cvar.Get("gl_texturealphamode", "default", Globals.CVAR_ARCHIVE);
		gl_texturesolidmode = Cvar.Get("gl_texturesolidmode", "default", Globals.CVAR_ARCHIVE);
		gl_lockpvs = Cvar.Get("gl_lockpvs", "0", 0);

		gl_vertex_arrays = Cvar.Get("gl_vertex_arrays", "1", Globals.CVAR_ARCHIVE);

		gl_ext_swapinterval = Cvar.Get("gl_ext_swapinterval", "1", Globals.CVAR_ARCHIVE);
		gl_ext_palettedtexture = Cvar.Get("gl_ext_palettedtexture", "0", Globals.CVAR_ARCHIVE);
		gl_ext_multitexture = Cvar.Get("gl_ext_multitexture", "1", Globals.CVAR_ARCHIVE);
		gl_ext_pointparameters = Cvar.Get("gl_ext_pointparameters", "1", Globals.CVAR_ARCHIVE);
		gl_ext_compiled_vertex_array = Cvar.Get("gl_ext_compiled_vertex_array", "1", Globals.CVAR_ARCHIVE);

		gl_drawbuffer = Cvar.Get("gl_drawbuffer", "GL_BACK", 0);
		gl_swapinterval = Cvar.Get("gl_swapinterval", "0", Globals.CVAR_ARCHIVE);

		gl_saturatelighting = Cvar.Get("gl_saturatelighting", "0", 0);

		gl_3dlabs_broken = Cvar.Get("gl_3dlabs_broken", "1", Globals.CVAR_ARCHIVE);

		vid_fullscreen = Cvar.Get("vid_fullscreen", "0", Globals.CVAR_ARCHIVE);
		vid_gamma = Cvar.Get("vid_gamma", "1.0", Globals.CVAR_ARCHIVE);
		vid_ref = Cvar.Get("vid_ref", "lwjgl", Globals.CVAR_ARCHIVE);

		Cmd.AddCommand("imagelist", new xcommand_t() {
			public void execute() {
				GL_ImageList_f();
			}
		});

		Cmd.AddCommand("screenshot", new xcommand_t() {
			public void execute() {
				GL_ScreenShot_f();
			}
		});
		Cmd.AddCommand("modellist", new xcommand_t() {
			public void execute() {
				Mod_Modellist_f();
			}
		});
		Cmd.AddCommand("gl_strings", new xcommand_t() {
			public void execute() {
				GL_Strings_f();
			}
		});
	}

	
	/**
	 * R_CullBox Returns true if the box is completely outside the frustum
	 */
	protected final boolean R_CullBox(float[] mins, float[] maxs) {
		assert (mins.length == 3 && maxs.length == 3) : "vec3_t bug";

		if (r_nocull.value != 0)
			return false;

		for (int i = 0; i < 4; i++) {
			if (Math3D.BoxOnPlaneSide(mins, maxs, frustum[i]) == 2)
				return true;
		}
		return false;
	}
	
		protected int SignbitsForPlane(cplane_t out) {
		// for fast box on planeside test
		int bits = 0;
		for (int j = 0; j < 3; j++) {
			if (out.normal[j] < 0)
				bits |= (1 << j);
		}
		return bits;
	}

	protected void R_SetFrustum() {
		// rotate VPN right by FOV_X/2 degrees
		Math3D.RotatePointAroundVector(frustum[0].normal, vup, vpn, -(90f - r_newrefdef.fov_x / 2f));
		// rotate VPN left by FOV_X/2 degrees
		Math3D.RotatePointAroundVector(frustum[1].normal, vup, vpn, 90f - r_newrefdef.fov_x / 2f);
		// rotate VPN up by FOV_X/2 degrees
		Math3D.RotatePointAroundVector(frustum[2].normal, vright, vpn, 90f - r_newrefdef.fov_y / 2f);
		// rotate VPN down by FOV_X/2 degrees
		Math3D.RotatePointAroundVector(frustum[3].normal, vright, vpn, -(90f - r_newrefdef.fov_y / 2f));

		for (int i = 0; i < 4; i++) {
			frustum[i].type = Defines.PLANE_ANYZ;
			frustum[i].dist = Math3D.DotProduct(r_origin, frustum[i].normal);
			frustum[i].signbits = (byte) SignbitsForPlane(frustum[i]);
		}
	}

	protected abstract void GL_GetWorldMatrix();
	
    /**
	 * R_SetupGL
	 */
	protected void R_SetupGL() {

		//
		// set up viewport
		//
		int x = r_newrefdef.x;
		int x2 = r_newrefdef.x + r_newrefdef.width;
		int y = vid.height - r_newrefdef.y;
		int y2 = vid.height - (r_newrefdef.y + r_newrefdef.height);

		int w = x2 - x;
		int h = y - y2;

		ggl.glViewport(x, y2, w, h);

		//
		// set up projection matrix
		//
		float screenaspect = (float) r_newrefdef.width / r_newrefdef.height;
		ggl.glMatrixMode(ggl.GL_PROJECTION);
		ggl.glLoadIdentity();
		MYgluPerspective(r_newrefdef.fov_y, screenaspect, 4, 4096);

		ggl.glCullFace(ggl.GL_FRONT);

		ggl.glMatrixMode(ggl.GL_MODELVIEW);
		ggl.glLoadIdentity();

		ggl.glRotatef(-90, 1, 0, 0); // put Z going up
		ggl.glRotatef(90, 0, 0, 1); // put Z going up
		ggl.glRotatef(-r_newrefdef.viewangles[2], 1, 0, 0);
		ggl.glRotatef(-r_newrefdef.viewangles[0], 0, 1, 0);
		ggl.glRotatef(-r_newrefdef.viewangles[1], 0, 0, 1);
		ggl.glTranslatef(-r_newrefdef.vieworg[0], -r_newrefdef.vieworg[1], -r_newrefdef.vieworg[2]);

		GL_GetWorldMatrix();

		//
		// set drawing parms
		//
		if (gl_cull.value != 0.0f)
			ggl.glEnable(ggl.GL_CULL_FACE);
		else
			ggl.glDisable(ggl.GL_CULL_FACE);

		ggl.glDisable(ggl.GL_BLEND);
		ggl.glDisable(ggl.GL_ALPHA_TEST);
		ggl.glEnable(ggl.GL_DEPTH_TEST);
	}

	/**
	 * R_Clear.
	 */

	protected void R_Clear() {
		if (gl_ztrick.value != 0.0f) {

			if (gl_clear.value != 0.0f) {
				ggl.glClear(ggl.GL_COLOR_BUFFER_BIT);
			}

			trickframe++;
			if ((trickframe & 1) != 0) {
				gldepthmin = 0;
				gldepthmax = 0.49999f;
				ggl.glDepthFunc(ggl.GL_LEQUAL);
			}
			else {
				gldepthmin = 1;
				gldepthmax = 0.5f;
				ggl.glDepthFunc(ggl.GL_GEQUAL);
			}
		}
		else {
			if (gl_clear.value != 0.0f)
				ggl.glClear(ggl.GL_COLOR_BUFFER_BIT | ggl.GL_DEPTH_BUFFER_BIT);
			else
				ggl.glClear(ggl.GL_DEPTH_BUFFER_BIT);

			gldepthmin = 0;
			gldepthmax = 1;
			ggl.glDepthFunc(ggl.GL_LEQUAL);
		}
		ggl.glDepthRange(gldepthmin, gldepthmax);
	}

	protected void R_SetGL2D() {
		// set 2D virtual screen size
		ggl.glViewport(0, 0, vid.width, vid.height);
		ggl.glMatrixMode(ggl.GL_PROJECTION);
		ggl.glLoadIdentity();
		ggl.glOrtho(0, vid.width, vid.height, 0, -99999, 99999);
		ggl.glMatrixMode(ggl.GL_MODELVIEW);
		ggl.glLoadIdentity();
		ggl.glDisable(ggl.GL_DEPTH_TEST);
		ggl.glDisable(ggl.GL_CULL_FACE);
		ggl.glDisable(ggl.GL_BLEND);
		ggl.glEnable(ggl.GL_ALPHA_TEST);
		ggl.glColor4f(1, 1, 1, 1);
	}
	
	

	/**
	 * R_RenderView.
	 * 
	 * r_newrefdef must be set before the first call
	 */
	protected void R_RenderView(refdef_t fd) {

		if (r_norefresh.value != 0.0f)
			return;

		r_newrefdef = fd;

		// included by cwei
		if (r_newrefdef == null) {
			Com.Error(Defines.ERR_DROP, "R_RenderView: refdef_t fd is null");
		}

		if (r_worldmodel == null
				&& (r_newrefdef.rdflags & Defines.RDF_NOWORLDMODEL) == 0)
			Com.Error(Defines.ERR_DROP, "R_RenderView: NULL worldmodel");

		if (r_speeds.value != 0.0f) {
			c_brush_polys = 0;
			c_alias_polys = 0;
		}

		R_PushDlights();

		if (gl_finish.value != 0.0f)
			GLimp_Finish();

		R_SetupFrame();

		R_SetFrustum();

		R_SetupGL();

		R_MarkLeaves(); // done here so we know if we're in water

		R_DrawWorld();

		R_DrawEntitiesOnList();

		R_RenderDlights();

		R_DrawParticles();

		R_DrawAlphaSurfaces();

		R_Flash();

		if (r_speeds.value != 0.0f) {
			VID.Printf(Defines.PRINT_ALL, "%4i wpoly %4i epoly %i tex %i lmaps\n", 
					new Vargs(4).add(c_brush_polys).add(c_alias_polys).add(c_visible_textures).add(c_visible_lightmaps));
		}
	}
	
	/**
	 * R_RenderFrame.
	 * 
	 */
	protected void R_RenderFrame(refdef_t fd) {
		R_RenderView(fd);
		R_SetLightLevel();
		R_SetGL2D();
	}

	
	/**
	 * R_Shutdown
	 */
	protected void R_Shutdown() {
		Cmd.RemoveCommand("modellist");
		Cmd.RemoveCommand("screenshot");
		Cmd.RemoveCommand("imagelist");
		Cmd.RemoveCommand("gl_strings");

		Mod_FreeAll();

		GL_ShutdownImages();

		/*
		 * shut down OS specific OpenGL stuff like contexts, etc.
		 */
		GLimp_Shutdown();
	}
	
	// stack variable
	private final float[] light = { 0, 0, 0 };

	/**
	 * R_SetLightLevel
	 */
	void R_SetLightLevel() {
		if ((r_newrefdef.rdflags & Defines.RDF_NOWORLDMODEL) != 0)
			return;

		// save off light value for server to look at (BIG HACK!)

		R_LightPoint(r_newrefdef.vieworg, light);

		// pick the greatest component, which should be the same
		// as the mono value returned by software
		if (light[0] > light[1]) {
			if (light[0] > light[2])
				r_lightlevel.value = 150 * light[0];
			else
				r_lightlevel.value = 150 * light[2];
		} else {
			if (light[1] > light[2])
				r_lightlevel.value = 150 * light[1];
			else
				r_lightlevel.value = 150 * light[2];
		}
	}
	
	/**
	 * R_Flash
	 */
	protected void R_Flash() {
		R_PolyBlend();
	}
	
	
	protected void MYgluPerspective(double fovy, double aspect, double zNear, double zFar) {
		double xmin, xmax, ymin, ymax;

		ymax = zNear * Math.tan(fovy * Math.PI / 360.0);
		ymin = -ymax;

		xmin = ymin * aspect;
		xmax = ymax * aspect;

		xmin += - (2 * gl_state.camera_separation) / zNear;
		xmax += - (2 * gl_state.camera_separation) / zNear;

		ggl.glFrustum(xmin, xmax, ymin, ymax, zNear, zFar);
	}
	
	
	/**
	 * R_SetMode
	 */
	protected boolean R_SetMode() {
		boolean fullscreen = (vid_fullscreen.value > 0.0f);

		vid_fullscreen.modified = false;
		gl_mode.modified = false;

		Dimension dim = new Dimension(vid.width, vid.height);

		int err; // enum rserr_t
		if ((err = GLimp_SetMode(dim, (int) gl_mode.value, fullscreen)) == rserr_ok) {
			gl_state.prev_mode = (int) gl_mode.value;
		} else {
			if (err == rserr_invalid_fullscreen) {
				Cvar.SetValue("vid_fullscreen", 0);
				vid_fullscreen.modified = false;
				VID.Printf(Defines.PRINT_ALL, "ref_gl::R_SetMode() - fullscreen unavailable in this mode\n");
				if ((err = GLimp_SetMode(dim, (int) gl_mode.value, false)) == rserr_ok)
					return true;
			} else if (err == rserr_invalid_mode) {
				Cvar.SetValue("gl_mode", gl_state.prev_mode);
				gl_mode.modified = false;
				VID.Printf(Defines.PRINT_ALL, "ref_gl::R_SetMode() - invalid mode\n");
			}

			// try setting it back to something safe
			if ((err = GLimp_SetMode(dim, gl_state.prev_mode, false)) != rserr_ok) {
				VID.Printf(Defines.PRINT_ALL, "ref_gl::R_SetMode() - could not revert to safe mode\n");
				return false;
			}
		}
		return true;
	}
	
	
		/*
	=============================================================
	
	   SPRITE MODELS
	
	=============================================================
	*/

	/*
	=================
	R_DrawSpriteModel
	
	=================
	*/
	void R_DrawSpriteModel(entity_t e) {
		float alpha = 1.0F;
		float[] point = { 0, 0, 0 };

		qfiles.dsprframe_t frame;
		qfiles.dsprite_t psprite;

		// don't even bother culling, because it's just a single
		// polygon without a surface cache

		psprite = (qfiles.dsprite_t) currentmodel.extradata;

		e.frame %= psprite.numframes;

		frame = psprite.frames[e.frame];

		if ((e.flags & Defines.RF_TRANSLUCENT) != 0)
			alpha = e.alpha;

		if (alpha != 1.0F)
			ggl.glEnable(ggl.GL_BLEND);

		ggl.glColor4f(1, 1, 1, alpha);

		GL_Bind(currentmodel.skins[e.frame].texnum);

		GL_TexEnv(ggl.GL_MODULATE);

		if (alpha == 1.0)
			ggl.glEnable(ggl.GL_ALPHA_TEST);
		else
			ggl.glDisable(ggl.GL_ALPHA_TEST);

		ggl.glBegin(ggl.GL_QUADS);

		ggl.glTexCoord2f(0, 1);
		Math3D.VectorMA(e.origin, -frame.origin_y, vup, point);
		Math3D.VectorMA(point, -frame.origin_x, vright, point);
		ggl.glVertex3f(point[0], point[1], point[2]);

		ggl.glTexCoord2f(0, 0);
		Math3D.VectorMA(e.origin, frame.height - frame.origin_y, vup, point);
		Math3D.VectorMA(point, -frame.origin_x, vright, point);
		ggl.glVertex3f(point[0], point[1], point[2]);

		ggl.glTexCoord2f(1, 0);
		Math3D.VectorMA(e.origin, frame.height - frame.origin_y, vup, point);
		Math3D.VectorMA(point, frame.width - frame.origin_x, vright, point);
		ggl.glVertex3f(point[0], point[1], point[2]);

		ggl.glTexCoord2f(1, 1);
		Math3D.VectorMA(e.origin, -frame.origin_y, vup, point);
		Math3D.VectorMA(point, frame.width - frame.origin_x, vright, point);
		ggl.glVertex3f(point[0], point[1], point[2]);

		ggl.glEnd();

		ggl.glDisable(ggl.GL_ALPHA_TEST);
		GL_TexEnv(ggl.GL_REPLACE);

		if (alpha != 1.0F)
			ggl.glDisable(ggl.GL_BLEND);

		ggl.glColor4f(1, 1, 1, 1);
	}

	/*
	=============
	R_DrawNullModel
	=============
	cwei :-)
	*/
	void R_DrawNullModel() {
		float[] shadelight = { 0, 0, 0 };

		if ((currententity.flags & Defines.RF_FULLBRIGHT) != 0) {
			// cwei wollte blau: shadelight[0] = shadelight[1] = shadelight[2] = 1.0F;
			shadelight[0] = shadelight[1] = shadelight[2] = 0.0F;
			shadelight[2] = 0.8F;
		}
		else {
			R_LightPoint(currententity.origin, shadelight);
		}

		ggl.glPushMatrix();
		R_RotateForEntity(currententity);

		ggl.glDisable(ggl.GL_TEXTURE_2D);
		ggl.glColor3f(shadelight[0], shadelight[1], shadelight[2]);

		// this replaces the TRIANGLE_FAN
		//GlutWireCube();
		ggl.glBegin(ggl.GL_TRIANGLE_FAN);
		ggl.glVertex3f(0, 0, -16);
		int i;
		for (i = 0; i <= 4; i++) {
			ggl.glVertex3f((float) (16.0f * Math.cos(i * Math.PI / 2)),
					(float) (16.0f * Math.sin(i * Math.PI / 2)), 0.0f);
		}
		ggl.glEnd();

		ggl.glBegin(ggl.GL_TRIANGLE_FAN);
		ggl.glVertex3f(0, 0, 16);
		for (i = 4; i >= 0; i--) {
			ggl.glVertex3f((float) (16.0f * Math.cos(i * Math.PI / 2)),
					(float) (16.0f * Math.sin(i * Math.PI / 2)), 0.0f);
		}
		ggl.glEnd();

		ggl.glColor3f(1, 1, 1);
		ggl.glPopMatrix();
		ggl.glEnable(ggl.GL_TEXTURE_2D);
	}

	protected abstract void R_RotateForEntity(entity_t currententity);

	
	/**
	 * R_DrawEntitiesOnList
	 */
	protected void R_DrawEntitiesOnList() {
		int i;

		if (r_drawentities.value == 0.0f)
			return;

		// draw non-transparent first
		for (i = 0; i < r_newrefdef.num_entities; i++) {
			currententity = r_newrefdef.entities[i];
			if ((currententity.flags & Defines.RF_TRANSLUCENT) != 0)
				continue; // solid

			if ((currententity.flags & Defines.RF_BEAM) != 0) {
				R_DrawBeam(currententity);
			}
			else {
				currentmodel = currententity.model;
				if (currentmodel == null) {
					R_DrawNullModel();
					continue;
				}
				switch (currentmodel.type) {
				case Base.mod_alias:
						R_DrawAliasModel(currententity);
						break;
				case Base.mod_brush:
						R_DrawBrushModel(currententity);
						break;
				case Base.mod_sprite:
						R_DrawSpriteModel(currententity);
						break;
				default:
					Com.Error(Defines.ERR_DROP, "Bad modeltype:" + currentmodel.type);
						break;
				}
			}
		}
		// draw transparent entities
		// we could sort these if it ever becomes a problem...
		ggl.glDepthMask(false); // no z writes
		for (i = 0; i < r_newrefdef.num_entities; i++) {
			currententity = r_newrefdef.entities[i];
			if ((currententity.flags & Defines.RF_TRANSLUCENT) == 0)
				continue; // solid

			if ((currententity.flags & Defines.RF_BEAM) != 0) {
				R_DrawBeam(currententity);
			}
			else {
				currentmodel = currententity.model;

				if (currentmodel == null) {
					R_DrawNullModel();
					continue;
				}
				switch (currentmodel.type) {
				case Base.mod_alias:
						R_DrawAliasModel(currententity);
						break;
				case Base.mod_brush:
						R_DrawBrushModel(currententity);
						break;
				case Base.mod_sprite:
						R_DrawSpriteModel(currententity);
						break;
					default :
						//Com.Error(Defines.ERR_DROP, "Bad modeltype");
						//this can happen sometimes because of a threading issue with jogl/lwjgl?
						//the fatal error was changed to a graceful return.
						ggl.glDepthMask(true);
						return;
						//break;
				}
			}
		}
		ggl.glDepthMask(true); // back to writing
	}
	
	/*
	** GL_DrawParticles
	**
	*/
	void GL_DrawParticles(int num_particles) {
		float[] up = { 0, 0, 0 };
		float[] right = { 0, 0, 0 };
		float scale;
		int color;

		float origin_x, origin_y, origin_z;

		Math3D.VectorScale(vup, 1.5f, up);
		Math3D.VectorScale(vright, 1.5f, right);
		
		GL_Bind(r_particletexture.texnum);
		ggl.glDepthMask(false); // no z buffering
		ggl.glEnable(ggl.GL_BLEND);
		GL_TexEnv(ggl.GL_MODULATE);
		
		ggl.glBegin(ggl.GL_TRIANGLES);

		FloatBuffer sourceVertices = particle_t.vertexArray;
		IntBuffer sourceColors = particle_t.colorArray;
		for (int j = 0, i = 0; i < num_particles; i++) {
			origin_x = sourceVertices.get(j++);
			origin_y = sourceVertices.get(j++);
			origin_z = sourceVertices.get(j++);

			// hack a scale up to keep particles from disapearing
			scale = (origin_x - r_origin[0]) * vpn[0] + (origin_y - r_origin[1]) * vpn[1] + (origin_z - r_origin[2]) * vpn[2];

			scale = (scale < 20) ? 1 : 1 + scale * 0.004f;

			color = sourceColors.get(i);

			ggl.glColor4ub((byte) ((color) & 0xFF),
									(byte) ((color >> 8) & 0xFF), 
									(byte) ((color >> 16) & 0xFF),
									(byte) ((color >>> 24)));
			
			// first vertex
			ggl.glTexCoord2f(0.0625f, 0.0625f);
			ggl.glVertex3f(origin_x, origin_y, origin_z);
			// second vertex
			ggl.glTexCoord2f(1.0625f, 0.0625f);
			ggl.glVertex3f(origin_x + up[0] * scale, origin_y + up[1] * scale, origin_z + up[2] * scale);
			// third vertex
			ggl.glTexCoord2f(0.0625f, 1.0625f);
			ggl.glVertex3f(origin_x + right[0] * scale, origin_y + right[1] * scale, origin_z + right[2] * scale);
		}
		ggl.glEnd();

		ggl.glDisable(ggl.GL_BLEND);
		ggl.glColor4f(1, 1, 1, 1);
		ggl.glDepthMask(true); // back to normal Z buffering
		GL_TexEnv(ggl.GL_REPLACE);
	}

	/**
	 * R_DrawParticles
	 */
	protected void R_DrawParticles() {

		if (gl_ext_pointparameters.value != 0.0f && qglPointParameterfEXT) {

			//ggl.glEnableClientState(ggl.GL_VERTEX_ARRAY);
			ggl.glVertexPointer(3, ggl.GL_FLOAT, 0, particle_t.vertexArray);
			ggl.glEnableClientState(ggl.GL_COLOR_ARRAY);
			ggl.glColorPointer(4, ggl.GL_UNSIGNED_BYTE, 0, particle_t.getColorAsByteBuffer());
			
			ggl.glDepthMask(false);
			ggl.glEnable(ggl.GL_BLEND);
			ggl.glDisable(ggl.GL_TEXTURE_2D);
			ggl.glPointSize(gl_particle_size.value);
			
			ggl.glDrawArrays(ggl.GL_POINTS, 0, r_newrefdef.num_particles);
			
			ggl.glDisableClientState(ggl.GL_COLOR_ARRAY);
			//ggl.glDisableClientState(ggl.GL_VERTEX_ARRAY);

			ggl.glDisable(ggl.GL_BLEND);
			ggl.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			ggl.glDepthMask(true);
			ggl.glEnable(ggl.GL_TEXTURE_2D);

		}
		else {
			GL_DrawParticles(r_newrefdef.num_particles);
		}
	}

	/*
	============
	R_PolyBlend
	============
	*/
	protected void R_PolyBlend() {
		if (gl_polyblend.value == 0.0f)
			return;

		if (v_blend[3] == 0.0f)
			return;

		ggl.glDisable(ggl.GL_ALPHA_TEST);
		ggl.glEnable(ggl.GL_BLEND);
		ggl.glDisable(ggl.GL_DEPTH_TEST);
		ggl.glDisable(ggl.GL_TEXTURE_2D);

		ggl.glLoadIdentity();

		// FIXME: get rid of these
		ggl.glRotatef(-90, 1, 0, 0); // put Z going up
		ggl.glRotatef(90, 0, 0, 1); // put Z going up

		ggl.glColor4f(v_blend[0], v_blend[1], v_blend[2], v_blend[3]);

		ggl.glBegin(ggl.GL_QUADS);

		ggl.glVertex3f(10, 100, 100);
		ggl.glVertex3f(10, -100, 100);
		ggl.glVertex3f(10, -100, -100);
		ggl.glVertex3f(10, 100, -100);
		ggl.glEnd();

		ggl.glDisable(ggl.GL_BLEND);
		ggl.glEnable(ggl.GL_TEXTURE_2D);
		ggl.glEnable(ggl.GL_ALPHA_TEST);

		ggl.glColor4f(1, 1, 1, 1);
	}
	
	
	/**
	 * R_SetPalette.
	 */
	protected void R_SetPalette(byte[] palette) {
		// 256 RGB values (768 bytes)
		// or null
		int i;
		int color = 0;

		if (palette != null) {
			int j =0;
			for (i = 0; i < 256; i++) {
				color = (palette[j++] & 0xFF) << 0;
				color |= (palette[j++] & 0xFF) << 8;
				color |= (palette[j++] & 0xFF) << 16;
				color |= 0xFF000000;
				r_rawpalette[i] = color;
			}
		}
		else {
			for (i = 0; i < 256; i++) {
				r_rawpalette[i] = d_8to24table[i] | 0xff000000;
			}
		}
		GL_SetTexturePalette(r_rawpalette);

		ggl.glClearColor(0, 0, 0, 0);
		ggl.glClear(ggl.GL_COLOR_BUFFER_BIT);
		ggl.glClearColor(1f, 0f, 0.5f, 0.5f);
	}


	/**
	* R_DrawBeam
	*/
	protected void R_DrawBeam(entity_t e) {

		int i;
		float r, g, b;

		float[] perpvec = { 0, 0, 0 }; // vec3_t
		float[] direction = { 0, 0, 0 }; // vec3_t
		float[] normalized_direction = { 0, 0, 0 }; // vec3_t

		float[] oldorigin = { 0, 0, 0 }; // vec3_t
		float[] origin = { 0, 0, 0 }; // vec3_t

		oldorigin[0] = e.oldorigin[0];
		oldorigin[1] = e.oldorigin[1];
		oldorigin[2] = e.oldorigin[2];

		origin[0] = e.origin[0];
		origin[1] = e.origin[1];
		origin[2] = e.origin[2];

		normalized_direction[0] = direction[0] = oldorigin[0] - origin[0];
		normalized_direction[1] = direction[1] = oldorigin[1] - origin[1];
		normalized_direction[2] = direction[2] = oldorigin[2] - origin[2];

		if (Math3D.VectorNormalize(normalized_direction) == 0.0f)
			return;

		Math3D.PerpendicularVector(perpvec, normalized_direction);
		Math3D.VectorScale(perpvec, e.frame / 2, perpvec);

		for (i = 0; i < 6; i++) {
			Math3D.RotatePointAroundVector(
				start_points[i],
				normalized_direction,
				perpvec,
				(360.0f / NUM_BEAM_SEGS) * i);

			Math3D.VectorAdd(start_points[i], origin, start_points[i]);
			Math3D.VectorAdd(start_points[i], direction, end_points[i]);
		}

		ggl.glDisable(ggl.GL_TEXTURE_2D);
		ggl.glEnable(ggl.GL_BLEND);
		ggl.glDepthMask(false);

		r = (d_8to24table[e.skinnum & 0xFF]) & 0xFF;
		g = (d_8to24table[e.skinnum & 0xFF] >> 8) & 0xFF;
		b = (d_8to24table[e.skinnum & 0xFF] >> 16) & 0xFF;

		r *= 1 / 255.0f;
		g *= 1 / 255.0f;
		b *= 1 / 255.0f;

		ggl.glColor4f(r, g, b, e.alpha);

		ggl.glBegin(ggl.GL_TRIANGLE_STRIP);
		
		float[] v;
		
		for (i = 0; i < jake2.render.common.Main.NUM_BEAM_SEGS; i++) {
			v = start_points[i];
			ggl.glVertex3f(v[0], v[1], v[2]);
			v = end_points[i];
			ggl.glVertex3f(v[0], v[1], v[2]);
			v = start_points[(i + 1) % jake2.render.common.Main.NUM_BEAM_SEGS];
			ggl.glVertex3f(v[0], v[1], v[2]);
			v = end_points[(i + 1) % jake2.render.common.Main.NUM_BEAM_SEGS];
			ggl.glVertex3f(v[0], v[1], v[2]);
		}
		ggl.glEnd();

		ggl.glEnable(ggl.GL_TEXTURE_2D);
		ggl.glDisable(ggl.GL_BLEND);
		ggl.glDepthMask(true);
	}
}
