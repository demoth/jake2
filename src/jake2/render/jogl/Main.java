/*
 * Main.java
 * Copyright (C) 2003
 *
 * $Id: Main.java,v 1.2 2003-12-27 19:36:22 cwei Exp $
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

import net.java.games.jogl.GL;
import net.java.games.jogl.GLU;
import net.java.games.jogl.util.GLUT;
import jake2.client.entity_t;
import jake2.client.refdef_t;
import jake2.client.refimport_t;
import jake2.client.viddef_t;
import jake2.game.cplane_t;
import jake2.game.cvar_t;
import jake2.render.*;

/**
 * Main
 * 
 * @author cwei
 */
public class Main {
	protected GL gl;
	protected GLU glu;
	protected GLUT glut = new GLUT();

	protected refimport_t ri = null;

	protected viddef_t vid = new viddef_t();

	model_t r_worldmodel;

	float gldepthmin, gldepthmax;

	protected glconfig_t gl_config = new glconfig_t();

	protected glstate_t gl_state = new glstate_t();

	image_t r_notexture; // use for bad textures
	image_t r_particletexture; // little dot for particles

	entity_t currententity;
	model_t currentmodel;

	cplane_t frustum[] = new cplane_t[4];

	int r_visframecount; // bumped when going to a new PVS
	int r_framecount; // used for dlight push checking

	int c_brush_polys, c_alias_polys;

	float v_blend[] = { 0, 0, 0, 0 }; // final blending color

	//
	//	   view origin
	//
	float[] vup = { 0, 0, 0 };
	float[] vpn = { 0, 0, 0 };
	float[] vright = { 0, 0, 0 };
	float[] r_origin = { 0, 0, 0 };

	float r_world_matrix[] = new float[16];
	float r_base_world_matrix[] = new float[16];

	//
	//	   screen size info
	//
	refdef_t r_newrefdef;

	int r_viewcluster, r_viewcluster2, r_oldviewcluster, r_oldviewcluster2;

	cvar_t r_norefresh;
	cvar_t r_drawentities;
	cvar_t r_drawworld;
	cvar_t r_speeds;
	cvar_t r_fullbright;
	cvar_t r_novis;
	cvar_t r_nocull;
	cvar_t r_lerpmodels;
	cvar_t r_lefthand;

	cvar_t r_lightlevel;
	// FIXME: This is a HACK to get the client's light level

	cvar_t gl_nosubimage;
	cvar_t gl_allow_software;

	cvar_t gl_vertex_arrays;

	cvar_t gl_particle_min_size;
	cvar_t gl_particle_max_size;
	cvar_t gl_particle_size;
	cvar_t gl_particle_att_a;
	cvar_t gl_particle_att_b;
	cvar_t gl_particle_att_c;

	cvar_t gl_ext_swapinterval;
	cvar_t gl_ext_palettedtexture;
	cvar_t gl_ext_multitexture;
	cvar_t gl_ext_pointparameters;
	cvar_t gl_ext_compiled_vertex_array;

	cvar_t gl_log;
	cvar_t gl_bitdepth;
	cvar_t gl_drawbuffer;
	cvar_t gl_driver;
	cvar_t gl_lightmap;
	cvar_t gl_shadows;
	protected cvar_t gl_mode = new cvar_t();
	cvar_t gl_dynamic;
	cvar_t gl_monolightmap;
	cvar_t gl_modulate;
	cvar_t gl_nobind;
	cvar_t gl_round_down;
	cvar_t gl_picmip;
	cvar_t gl_skymip;
	cvar_t gl_showtris;
	cvar_t gl_ztrick;
	cvar_t gl_finish;
	cvar_t gl_clear;
	cvar_t gl_cull;
	cvar_t gl_polyblend;
	cvar_t gl_flashblend;
	cvar_t gl_playermip;
	cvar_t gl_saturatelighting;
	cvar_t gl_swapinterval;
	cvar_t gl_texturemode;
	cvar_t gl_texturealphamode;
	cvar_t gl_texturesolidmode;
	cvar_t gl_lockpvs;

	cvar_t gl_3dlabs_broken;

	protected cvar_t vid_fullscreen = new cvar_t();
	cvar_t vid_gamma;
	protected cvar_t vid_ref;

	
	protected void R_SetGL2D() {
		// set 2D virtual screen size
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
	}
	

}
