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

// Created on 20.08.2006 by RST.

// $Id: Surf.java,v 1.1 2006-10-31 13:06:32 salomo Exp $

package jake2.render.common;


import java.nio.*;
import java.util.Arrays;

import org.lwjgl.opengl.GL11;

import net.java.games.jogl.GL;
import net.java.games.jogl.util.BufferUtils;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.render.common.Image.pos_t;
import jake2.server.*;
import jake2.util.*;


public abstract class Surf extends Draw {

	// GL_RSURF.C: surface-related refresh code
	protected float[] modelorg = { 0, 0, 0 }; // relative to viewpoint

	protected msurface_t r_alpha_surfaces;

	protected static final int DYNAMIC_LIGHT_WIDTH = 128;

	protected static final int DYNAMIC_LIGHT_HEIGHT = 128;

	protected static final int LIGHTMAP_BYTES = 4;

	protected static final int LM_BLOCK_WIDTH = 128;

	protected static final int LM_BLOCK_HEIGHT = 128;

	protected int c_visible_lightmaps;

	protected int c_visible_textures;

	protected gllightmapstate_t gl_lms = new gllightmapstate_t();

	protected static final int GL_LIGHTMAP_FORMAT = ggl.GL_RGBA;
		
	public static final int MAX_LIGHTMAPS = 128;
		
	protected byte[] fatvis = new byte[Defines.MAX_MAP_LEAFS / 8];

	protected lightstyle_t[] lightstyles;
		
	public static class gllightmapstate_t {
		
		public int internal_format;

		public int current_lightmap_texture;

		public msurface_t[] lightmap_surfaces = new msurface_t[MAX_LIGHTMAPS];

		public int[] allocated = new int[LM_BLOCK_WIDTH];

		// the lightmap texture data needs to be kept in
		// main memory so texsubimage can update properly
		// byte[] lightmap_buffer = new byte[4 * BLOCK_WIDTH * BLOCK_HEIGHT];
		public IntBuffer lightmap_buffer = Lib.newIntBuffer(
				LM_BLOCK_WIDTH  * LM_BLOCK_HEIGHT, ByteOrder.LITTLE_ENDIAN);

		public gllightmapstate_t() {
			for (int i = 0; i < MAX_LIGHTMAPS; i++)
				lightmap_surfaces[i] = new msurface_t();
		}

		public void clearLightmapSurfaces() {
			for (int i = 0; i < MAX_LIGHTMAPS; i++)
				// TODO lightmap_surfaces[i].clear();
				lightmap_surfaces[i] = new msurface_t();
		}
	}

	
	/**
	 * LM_InitBlock
	 */
	protected void LM_InitBlock() {
		Arrays.fill(gl_lms.allocated, 0);
	}
	
	
	/**
	 * LM_AllocBlock.
	 *
	 */
	protected boolean LM_AllocBlock(int w, int h, pos_t pos) {
		int best = LM_BLOCK_HEIGHT;
		int x = pos.x;

		int best2;
		int i, j;
		for (i = 0; i < LM_BLOCK_WIDTH - w; i++) {
			best2 = 0;

			for (j = 0; j < w; j++) {
				if (gl_lms.allocated[i + j] >= best)
					break;
				if (gl_lms.allocated[i + j] > best2)
					best2 = gl_lms.allocated[i + j];
			}
			if (j == w) { // this is a valid spot
				pos.x = x = i;
				pos.y = best = best2;
			}
		}

		if (best + h > LM_BLOCK_HEIGHT)
			return false;

		for (i = 0; i < w; i++)
			gl_lms.allocated[x + i] = best + h;

		return true;
	}
	
	/** 
	 * R_TextureAnimation
	 * 
	 * Returns the proper texture for a given time and base texture
	 */
	protected image_t R_TextureAnimation(mtexinfo_t tex) {
		int c;

		if (tex.next == null)
			return tex.image;

		c = currententity.frame % tex.numframes;
		while (c != 0) {
			tex = tex.next;
			c--;
		}

		return tex.image;
	}
	
	/**
	 * DrawGLPoly.
	 */
	protected void DrawGLPoly(glpoly_t p) {
		ggl.glDrawArrays(ggl.GL_POLYGON, p.pos, p.numverts);
	}
	
	/**
	 * DrawGLFlowingPoly version that handles scrolling texture
	 */
	protected void DrawGLFlowingPoly(glpoly_t p) {
		float scroll = -64 * ((r_newrefdef.time / 40.0f) - (int) (r_newrefdef.time / 40.0f));
		if (scroll == 0.0f)
			scroll = -64.0f;
		p.beginScrolling(scroll);
		ggl.glDrawArrays(ggl.GL_POLYGON, p.pos, p.numverts);
		p.endScrolling();
	}
	
	protected abstract void GL_LoadMatrix();
	
	/**
	 * R_DrawAlphaSurfaces Draw water surfaces and windows. The BSP tree is
	 * waled front to back, so unwinding the chain of alpha_surfaces will draw
	 * back to front, giving proper ordering.
	 */
	protected void R_DrawAlphaSurfaces() {

		GL_LoadMatrix();
		
		ggl.glEnable(ggl.GL_BLEND);
		GL_TexEnv(ggl.GL_MODULATE);

		// the textures are prescaled up for a better lighting range,
		// so scale it back down
		float intens = gl_state.inverse_intensity;

		ggl.glInterleavedArrays(ggl.GL_T2F_V3F, Polygon.BYTE_STRIDE, globalPolygonInterleavedBuf);

		for (msurface_t s = r_alpha_surfaces; s != null; s = s.texturechain) {
			GL_Bind(s.texinfo.image.texnum);
			c_brush_polys++;
			if ((s.texinfo.flags & Defines.SURF_TRANS33) != 0)
				ggl.glColor4f(intens, intens, intens, 0.33f);
			else if ((s.texinfo.flags & Defines.SURF_TRANS66) != 0)
				ggl.glColor4f(intens, intens, intens, 0.66f);
			else
				ggl.glColor4f(intens, intens, intens, 1);
			if ((s.flags & Defines.SURF_DRAWTURB) != 0)
				EmitWaterPolys(s);
			else if ((s.texinfo.flags & Defines.SURF_FLOWING) != 0) // PGM
																	// 9/16/98
				DrawGLFlowingPoly(s.polys); // PGM
			else
				DrawGLPoly(s.polys);
		}

		GL_TexEnv(ggl.GL_REPLACE);
		ggl.glColor4f(1, 1, 1, 1);
		ggl.glDisable(ggl.GL_BLEND);

		r_alpha_surfaces = null;
	}
		
	/**
	 *  R_DrawTriangleOutlines
	 */
	protected void R_DrawTriangleOutlines() {
		 if (gl_showtris.value == 0)
			return;

		ggl.glDisable(ggl.GL_TEXTURE_2D);
		ggl.glDisable(ggl.GL_DEPTH_TEST);
		ggl.glColor4f(1, 1, 1, 1);

		for (int i = 0; i < MAX_LIGHTMAPS; i++) {
			 for (msurface_t surf = gl_lms.lightmap_surfaces[i]; surf != null; surf = surf.lightmapchain) {
				for (glpoly_t p = surf.polys; p != null; p = p.chain) {
					for (int j = 2; j < p.numverts; j++) {
						ggl.glBegin(ggl.GL_LINE_STRIP);
						ggl.glVertex3f(p.x(0), p.y(0), p.z(0));
						ggl.glVertex3f(p.x(j-1), p.y(j-1), p.z(j-1));
						ggl.glVertex3f(p.x(j), p.y(j), p.z(j));
						ggl.glVertex3f(p.x(0), p.y(0), p.z(0));
						ggl.glEnd();
					}
				}
			}
		}

		ggl.glEnable(ggl.GL_DEPTH_TEST);
		ggl.glEnable(ggl.GL_TEXTURE_2D);
	}

	/**
	 * R_MarkLeaves -
	 * 
	 * Mark the leaves and nodes that are in the PVS for the current cluster.
	 */
	protected void R_MarkLeaves() {

		mnode_t node;
		int i, c;
		mleaf_t leaf;
		int cluster;

		if (r_oldviewcluster == r_viewcluster
				&& r_oldviewcluster2 == r_viewcluster2 && r_novis.value == 0
				&& r_viewcluster != -1)
			return;

		// development aid to let you run around and see exactly where
		// the pvs ends
		if (gl_lockpvs.value != 0)
			return;

		r_visframecount++;
		r_oldviewcluster = r_viewcluster;
		r_oldviewcluster2 = r_viewcluster2;

		if (r_novis.value != 0 || r_viewcluster == -1
				|| r_worldmodel.vis == null) {
			// mark everything
			for (i = 0; i < r_worldmodel.numleafs; i++)
				r_worldmodel.leafs[i].visframe = r_visframecount;
			for (i = 0; i < r_worldmodel.numnodes; i++)
				r_worldmodel.nodes[i].visframe = r_visframecount;
			return;
		}

		byte[] vis = Mod_ClusterPVS(r_viewcluster, r_worldmodel);
		
		// may have to combine two clusters because of solid water boundaries
		if (r_viewcluster2 != r_viewcluster) {
			System	.arraycopy(vis, 0, fatvis, 0, (r_worldmodel.numleafs + 7) >> 3);
			vis = Mod_ClusterPVS(r_viewcluster2, r_worldmodel);
			c = (r_worldmodel.numleafs + 31) >> 5;
			c <<= 2;
			
			for (int k = 0; k < c; k+=4) {
				fatvis[k] |= vis[k];
				fatvis[k + 1] |= vis[k + 1];
				fatvis[k + 2] |= vis[k + 2];
				fatvis[k + 3] |= vis[k + 3];
			}

			vis = fatvis;
		}

		for (i = 0; i < r_worldmodel.numleafs; i++) {
			leaf = r_worldmodel.leafs[i];
			cluster = leaf.cluster;
			if (cluster == -1)
				continue;
			if (((vis[cluster >> 3] & 0xFF) & (1 << (cluster & 7))) != 0) {
				node = (mnode_t) leaf;
				do {
					if (node.visframe == r_visframecount)
						break;
					node.visframe = r_visframecount;
					node = node.parent;
				} while (node != null);
			}
		}
	}

	// direct buffer
	protected IntBuffer temp = Lib.newIntBuffer(128 * 128,ByteOrder.LITTLE_ENDIAN);
	
	protected IntBuffer temp2 = Lib.newIntBuffer(34 * 34, ByteOrder.LITTLE_ENDIAN);	
	
	/*
	 * new buffers for vertex array handling
	 */
	protected static FloatBuffer globalPolygonInterleavedBuf = Polygon.getInterleavedBuffer();

	protected static FloatBuffer globalPolygonTexCoord1Buf = null;

	static {
		globalPolygonInterleavedBuf.position(Polygon.STRIDE - 2);
		globalPolygonTexCoord1Buf = globalPolygonInterleavedBuf.slice();
		globalPolygonInterleavedBuf.position(0);
	};
	
	protected abstract void EmitWaterPolys(msurface_t s);
	protected abstract void R_BuildLightMap(msurface_t fa, IntBuffer temp22, int smax);
	protected abstract void R_SetCacheState(msurface_t fa);
	protected abstract void R_MarkLights(dlight_t lt, int i, mnode_t mnode_t);
	protected abstract void R_AddSkySurface(msurface_t s);
	protected abstract void R_DrawSkyBox();
	protected abstract void R_ClearSkyBox();

	/**
	 * R_RotateForEntity
	 */
	protected final void R_RotateForEntity(entity_t e) {
		ggl.glTranslatef(e.origin[0], e.origin[1], e.origin[2]);

		ggl.glRotatef(e.angles[1], 0, 0, 1);
		ggl.glRotatef(-e.angles[0], 0, 1, 0);
		ggl.glRotatef(-e.angles[2], 1, 0, 0);
	}

	
	/**
	 *R_RenderBrushPoly ================
	 */
	protected void R_RenderBrushPoly(msurface_t fa) {
		int maps;
		image_t image;
		boolean is_dynamic = false;

		c_brush_polys++;

		image = R_TextureAnimation(fa.texinfo);

		if ((fa.flags & Defines.SURF_DRAWTURB) != 0) {
			GL_Bind(image.texnum);

			// warp texture, no lightmaps
			GL_TexEnv(ggl.GL_MODULATE);
			ggl.glColor4f(gl_state.inverse_intensity, gl_state.inverse_intensity, gl_state.inverse_intensity, 1.0F);
			EmitWaterPolys(fa);
			GL_TexEnv(ggl.GL_REPLACE);

			return;
		} else {
			GL_Bind(image.texnum);
			GL_TexEnv(ggl.GL_REPLACE);
		}

		//	  ======
		//	  PGM
		if ((fa.texinfo.flags & Defines.SURF_FLOWING) != 0)
			DrawGLFlowingPoly(fa.polys);
		else
			DrawGLPoly(fa.polys);
		//	  PGM
		//	  ======

		// ersetzt goto
		boolean gotoDynamic = false;
		
		// check for lightmap modification
		for (maps = 0; maps < Defines.MAXLIGHTMAPS
				&& fa.styles[maps] != (byte) 255; maps++) {
			if (r_newrefdef.lightstyles[fa.styles[maps] & 0xFF].white != fa.cached_light[maps]) {
				gotoDynamic = true;
				break;
			}
		}

		// this is a hack from cwei
		if (maps == 4)
			maps--;

		// dynamic this frame or dynamic previously
		if (gotoDynamic || (fa.dlightframe == r_framecount)) {
			//	label dynamic:
			if (gl_dynamic.value != 0) {
				if ((fa.texinfo.flags & (Defines.SURF_SKY
						| Defines.SURF_TRANS33 | Defines.SURF_TRANS66 | Defines.SURF_WARP)) == 0) {
					is_dynamic = true;
				}
			}
		}

		if (is_dynamic) {
			if (((fa.styles[maps] & 0xFF) >= 32 || fa.styles[maps] == 0)
					&& (fa.dlightframe != r_framecount)) {
				// ist ersetzt durch temp2: unsigned temp[34*34];
				int smax, tmax;

				smax = (fa.extents[0] >> 4) + 1;
				tmax = (fa.extents[1] >> 4) + 1;

				R_BuildLightMap(fa, temp2, smax);
				R_SetCacheState(fa);

				GL_Bind(gl_state.lightmap_textures + fa.lightmaptexturenum);

				ggl.glTexSubImage2D(ggl.GL_TEXTURE_2D, 0, fa.light_s, fa.light_t,
						smax, tmax, GL_LIGHTMAP_FORMAT, ggl.GL_UNSIGNED_BYTE,
						temp2);

				fa.lightmapchain = gl_lms.lightmap_surfaces[fa.lightmaptexturenum];
				gl_lms.lightmap_surfaces[fa.lightmaptexturenum] = fa;
			} else {
				fa.lightmapchain = gl_lms.lightmap_surfaces[0];
				gl_lms.lightmap_surfaces[0] = fa;
			}
		} else {
			fa.lightmapchain = gl_lms.lightmap_surfaces[fa.lightmaptexturenum];
			gl_lms.lightmap_surfaces[fa.lightmaptexturenum] = fa;
		}
	}

	IntBuffer dummy = BufferUtils.newIntBuffer(128 * 128);

	/**
	 *  GL_BeginBuildingLightmaps.
	 */
	protected void GL_BeginBuildingLightmaps(model_t m) {

		if (lightstyles == null) {
			lightstyles = new lightstyle_t[Defines.MAX_LIGHTSTYLES];
			for (int i = 0; i < lightstyles.length; i++) {
				lightstyles[i] = new lightstyle_t();
			}
		}

		Arrays.fill(gl_lms.allocated, 0);

		r_framecount = 1; // no dlightcache

		GL_EnableMultitexture(true);
		GL_SelectTexture(GL_TEXTURE1);

		/*
		 * setup the base lightstyles so the lightmaps won't have to be
		 * regenerated the first time they're seen
		 */
		for (int i = 0; i < Defines.MAX_LIGHTSTYLES; i++) {
			lightstyles[i].rgb[0] = 1;
			lightstyles[i].rgb[1] = 1;
			lightstyles[i].rgb[2] = 1;
			lightstyles[i].white = 3;
		}
		r_newrefdef.lightstyles = lightstyles;

		if (gl_state.lightmap_textures == 0) {
			gl_state.lightmap_textures = Base.TEXNUM_LIGHTMAPS;
		}

		gl_lms.current_lightmap_texture = 1;

		/*
		 * if mono lightmaps are enabled and we want to use alpha 
		 * blending (a,1-a) then we're likely running on a 3DLabs
		 * Permedia2. In a perfect world we'd use a GL_ALPHA lightmap
		 * in order to conserve space and maximize bandwidth, however 
		 * this isn't a perfect world. 
		 * 
		 * So we have to use alpha lightmaps, but stored in GL_RGBA format, 
		 * which means we only get 1/16th the color resolution we should when 
		 * using alpha lightmaps. If we find another board that supports 
		 * only alpha lightmaps but that can at least support the GL_ALPHA 
		 * format then we should change this code to use real alpha maps.
		 */

		char format = gl_monolightmap.string.toUpperCase().charAt(0);

		if (format == 'A') {
			gl_lms.internal_format = gl_tex_alpha_format;
		}
		
		/*
		 * * try to do hacked colored lighting with a blended texture
		 */
		else if (format == 'C') {
			gl_lms.internal_format = gl_tex_alpha_format;
		} else if (format == 'I') {
			gl_lms.internal_format = ggl.GL_INTENSITY8;
		} else if (format == 'L') {
			gl_lms.internal_format = ggl.GL_LUMINANCE8;
		} else {
			gl_lms.internal_format = gl_tex_solid_format;
		}

		/*
		 * * initialize the dynamic lightmap texture
		 */
		GL_Bind(gl_state.lightmap_textures + 0);
		ggl.glTexParameterf(ggl.GL_TEXTURE_2D, ggl.GL_TEXTURE_MIN_FILTER, ggl.GL_LINEAR);
		ggl.glTexParameterf(ggl.GL_TEXTURE_2D, ggl.GL_TEXTURE_MAG_FILTER, ggl.GL_LINEAR);
		ggl.glTexImage2D(ggl.GL_TEXTURE_2D, 0, gl_lms.internal_format,
				LM_BLOCK_WIDTH, LM_BLOCK_HEIGHT, 0, GL_LIGHTMAP_FORMAT,
				ggl.GL_UNSIGNED_BYTE, dummy);
	}

	
	/**
	 * GL_EndBuildingLightmaps.
	 */
	protected void GL_EndBuildingLightmaps() {
		LM_UploadBlock(false);
		GL_EnableMultitexture(false);
	}

	/**
	 * DrawTextureChains.
	 */
	protected void DrawTextureChains() {
		int i;
		msurface_t s;
		image_t image;

		c_visible_textures = 0;

		for (i = 0; i < numgltextures; i++) {
			image = gltextures[i];

			if (image.registration_sequence == 0)
				continue;
			if (image.texturechain == null)
				continue;
			c_visible_textures++;

			for (s = image.texturechain; s != null; s = s.texturechain) {
				if ((s.flags & Defines.SURF_DRAWTURB) == 0)
					R_RenderBrushPoly(s);
			}
		}

		GL_EnableMultitexture(false);
		for (i = 0; i < numgltextures; i++) {
			image = gltextures[i];

			if (image.registration_sequence == 0)
				continue;
			s = image.texturechain;
			if (s == null)
				continue;

			for (; s != null; s = s.texturechain) {
				if ((s.flags & Defines.SURF_DRAWTURB) != 0)
					R_RenderBrushPoly(s);
			}

			image.texturechain = null;
		}

		GL_TexEnv(GL.GL_REPLACE);
	}
	
	protected void GL_RenderLightmappedPoly(msurface_t surf) {
		int map = 0;

		image_t image = R_TextureAnimation(surf.texinfo);
		boolean is_dynamic = false;
		int lmtex = surf.lightmaptexturenum;
		glpoly_t p;

		// ersetzt goto
		boolean gotoDynamic = false;

		for (map = 0; map < Defines.MAXLIGHTMAPS
				&& (surf.styles[map] != (byte) 255); map++) {
			if (r_newrefdef.lightstyles[surf.styles[map] & 0xFF].white != surf.cached_light[map]) {
				gotoDynamic = true;
				break;
			}
		}

		// this is a hack from cwei
		if (map == 4)
			map--;

		// dynamic this frame or dynamic previously
		if (gotoDynamic || (surf.dlightframe == r_framecount)) {
			//	label dynamic:
			if (gl_dynamic.value != 0) {
				if ((surf.texinfo.flags & (Defines.SURF_SKY
						| Defines.SURF_TRANS33 | Defines.SURF_TRANS66 | Defines.SURF_WARP)) == 0) {
					is_dynamic = true;
				}
			}
		}

		if (is_dynamic) {
			// ist raus gezogen worden int[] temp = new int[128*128];
			int smax, tmax;

			if (((surf.styles[map] & 0xFF) >= 32 || surf.styles[map] == 0)
					&& (surf.dlightframe != r_framecount)) {
				smax = (surf.extents[0] >> 4) + 1;
				tmax = (surf.extents[1] >> 4) + 1;

				R_BuildLightMap(surf, temp, smax);
				R_SetCacheState(surf);

				GL_MBind(GL_TEXTURE1, gl_state.lightmap_textures
						+ surf.lightmaptexturenum);

				lmtex = surf.lightmaptexturenum;

				ggl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, surf.light_s,
						surf.light_t, smax, tmax, GL_LIGHTMAP_FORMAT,
						GL.GL_UNSIGNED_BYTE, temp);

			} else {
				smax = (surf.extents[0] >> 4) + 1;
				tmax = (surf.extents[1] >> 4) + 1;

				R_BuildLightMap(surf, temp, smax);

				GL_MBind(GL_TEXTURE1, gl_state.lightmap_textures + 0);

				lmtex = 0;

				ggl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, surf.light_s,
						surf.light_t, smax, tmax, GL_LIGHTMAP_FORMAT,
						GL.GL_UNSIGNED_BYTE, temp);

			}

			c_brush_polys++;

			GL_MBind(GL_TEXTURE0, image.texnum);
			GL_MBind(GL_TEXTURE1, gl_state.lightmap_textures + lmtex);

			// ==========
			//	  PGM
			if ((surf.texinfo.flags & Defines.SURF_FLOWING) != 0) {
				float scroll;

				scroll = -64
						* ((r_newrefdef.time / 40.0f) - (int) (r_newrefdef.time / 40.0f));
				if (scroll == 0.0f)
					scroll = -64.0f;

				for (p = surf.polys; p != null; p = p.chain) {
					p.beginScrolling(scroll);
					ggl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
					p.endScrolling();
				}
			} else {
				for (p = surf.polys; p != null; p = p.chain) {
					ggl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
				}
			}
			// PGM
			// ==========
		} else {
			c_brush_polys++;

			GL_MBind(GL_TEXTURE0, image.texnum);
			GL_MBind(GL_TEXTURE1, gl_state.lightmap_textures + lmtex);

			// ==========
			//	  PGM
			if ((surf.texinfo.flags & Defines.SURF_FLOWING) != 0) {
				float scroll;

				scroll = -64
						* ((r_newrefdef.time / 40.0f) - (int) (r_newrefdef.time / 40.0f));
				if (scroll == 0.0)
					scroll = -64.0f;

				for (p = surf.polys; p != null; p = p.chain) {
					p.beginScrolling(scroll);
					ggl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
					p.endScrolling();
				}
			} else {
				// PGM
				//  ==========
				for (p = surf.polys; p != null; p = p.chain) {
					ggl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
				}

				// ==========
				// PGM
			}
			// PGM
			// ==========
		}
	}

	/**
	 * R_DrawInlineBModel
	 */
	void R_DrawInlineBModel() {
		int i, k;
		cplane_t pplane;
		float dot;
		msurface_t psurf;
		dlight_t lt;

		// calculate dynamic lighting for bmodel
		if (gl_flashblend.value == 0) {
			for (k = 0; k < r_newrefdef.num_dlights; k++) {
				lt = r_newrefdef.dlights[k];
				R_MarkLights(lt, 1 << k, currentmodel.nodes[currentmodel.firstnode]);
			}
		}

		int psurfp = currentmodel.firstmodelsurface;
		msurface_t[] surfaces;
		surfaces = currentmodel.surfaces;

		if ((currententity.flags & Defines.RF_TRANSLUCENT) != 0) {
			ggl.glEnable(ggl.GL_BLEND);
			ggl.glColor4f(1, 1, 1, 0.25f);
			GL_TexEnv(ggl.GL_MODULATE);
		}

		//
		// draw texture
		//
		for (i = 0; i < currentmodel.nummodelsurfaces; i++) {
			psurf = surfaces[psurfp++];
			// find which side of the node we are on
			pplane = psurf.plane;

			dot = Math3D.DotProduct(modelorg, pplane.normal) - pplane.dist;

			// draw the polygon
			if (((psurf.flags & Defines.SURF_PLANEBACK) != 0 && (dot < -Base.BACKFACE_EPSILON))
					|| ((psurf.flags & Defines.SURF_PLANEBACK) == 0 && (dot > Base.BACKFACE_EPSILON))) {
				if ((psurf.texinfo.flags & (Defines.SURF_TRANS33 | Defines.SURF_TRANS66)) != 0) {
					// add to the translucent chain
					psurf.texturechain = r_alpha_surfaces;
					r_alpha_surfaces = psurf;
				} else if ((psurf.flags & Defines.SURF_DRAWTURB) == 0) {
					GL_RenderLightmappedPoly(psurf);
				} else {
					GL_EnableMultitexture(false);
					R_RenderBrushPoly(psurf);
					GL_EnableMultitexture(true);
				}
			}
		}

		if ((currententity.flags & Defines.RF_TRANSLUCENT) != 0) {
			ggl.glDisable(ggl.GL_BLEND);
			ggl.glColor4f(1, 1, 1, 1);
			GL_TexEnv(ggl.GL_REPLACE);
		}
	}

	/**
	 * R_DrawBrushModel.
	 */
	protected void R_DrawBrushModel(entity_t e) {
		if (currentmodel.nummodelsurfaces == 0)
			return;

		currententity = e;
		gl_state.currenttextures[0] = gl_state.currenttextures[1] = -1;

		float[] mins = Vec3Cache.get();
		float[] maxs = Vec3Cache.get();
		boolean rotated;
		if (e.angles[0] != 0 || e.angles[1] != 0 || e.angles[2] != 0) {
			rotated = true;
			for (int i = 0; i < 3; i++) {
				mins[i] = e.origin[i] - currentmodel.radius;
				maxs[i] = e.origin[i] + currentmodel.radius;
			}
		} else {
			rotated = false;
			Math3D.VectorAdd(e.origin, currentmodel.mins, mins);
			Math3D.VectorAdd(e.origin, currentmodel.maxs, maxs);
		}

		if (R_CullBox(mins, maxs)) {
			Vec3Cache.release(2); // mins, maxs
			return;
		}

		Vec3Cache.release(2); // mins, maxs

		ggl.glColor3f(1, 1, 1);

		Math3D.VectorSubtract(r_newrefdef.vieworg, e.origin, modelorg);
		if (rotated) {
			float[] temp = Vec3Cache.get();
			float[] forward =  Vec3Cache.get();
			float[] right =  Vec3Cache.get();
			float[] up =  Vec3Cache.get();

			Math3D.VectorCopy(modelorg, temp);
			Math3D.AngleVectors(e.angles, forward, right, up);
			modelorg[0] = Math3D.DotProduct(temp, forward);
			modelorg[1] = -Math3D.DotProduct(temp, right);
			modelorg[2] = Math3D.DotProduct(temp, up);
			
			Vec3Cache.release(4); // temp, forward, right, up
		}

		ggl.glPushMatrix();

		e.angles[0] = -e.angles[0]; // stupid quake bug
		e.angles[2] = -e.angles[2]; // stupid quake bug
		R_RotateForEntity(e);
		e.angles[0] = -e.angles[0]; // stupid quake bug
		e.angles[2] = -e.angles[2]; // stupid quake bug

		GL_EnableMultitexture(true);
		GL_SelectTexture(GL_TEXTURE0);
		GL_TexEnv(ggl.GL_REPLACE);
		ggl.glInterleavedArrays(ggl.GL_T2F_V3F, Polygon.BYTE_STRIDE,
				globalPolygonInterleavedBuf);
		GL_SelectTexture(GL_TEXTURE1);
		GL_TexEnv(ggl.GL_MODULATE);
		ggl.glTexCoordPointer(2, ggl.GL_FLOAT, Polygon.BYTE_STRIDE,
				globalPolygonTexCoord1Buf);
		ggl.glEnableClientState(ggl.GL_TEXTURE_COORD_ARRAY);

		R_DrawInlineBModel();

		ggl.glClientActiveTextureARB(GL_TEXTURE1);
		ggl.glDisableClientState(ggl.GL_TEXTURE_COORD_ARRAY);

		GL_EnableMultitexture(false);

		ggl.glPopMatrix();
	}

	/*
	 * =============================================================
	 * 
	 * WORLD MODEL
	 * 
	 * =============================================================
	 */

	/**
	 * R_RecursiveWorldNode.
	 */
	void R_RecursiveWorldNode(mnode_t node) {
		int c, side, sidebit;
		cplane_t plane;
		msurface_t surf;
		msurface_t mark;
		mleaf_t pleaf;
		float dot = 0;
		image_t image;

		if (node.contents == Defines.CONTENTS_SOLID)
			return; // solid

		if (node.visframe != r_visframecount)
			return;

		if (R_CullBox(node.mins, node.maxs))
			return;

		// if a leaf node, draw stuff
		if (node.contents != -1) {
			pleaf = (mleaf_t) node;

			// check for door connected areas
			if (r_newrefdef.areabits != null) {
				if (((r_newrefdef.areabits[pleaf.area >> 3] & 0xFF) & (1 << (pleaf.area & 7))) == 0)
					return; // not visible
			}

			int markp = 0;

			mark = pleaf.getMarkSurface(markp); // first marked surface
			c = pleaf.nummarksurfaces;

			if (c != 0) {
				do {
					mark.visframe = r_framecount;
					mark = pleaf.getMarkSurface(++markp); // next surface
				} while (--c != 0);
			}

			return;
		}

		// node is just a decision point, so go down the apropriate sides

		// find which side of the node we are on
		plane = node.plane;

		switch (plane.type) {
		case Defines.PLANE_X:
			dot = modelorg[0] - plane.dist;
			break;
		case Defines.PLANE_Y:
			dot = modelorg[1] - plane.dist;
			break;
		case Defines.PLANE_Z:
			dot = modelorg[2] - plane.dist;
			break;
		default:
			dot = Math3D.DotProduct(modelorg, plane.normal) - plane.dist;
			break;
		}

		if (dot >= 0.0f) {
			side = 0;
			sidebit = 0;
		} else {
			side = 1;
			sidebit = Defines.SURF_PLANEBACK;
		}

		// recurse down the children, front side first
		R_RecursiveWorldNode(node.children[side]);

		// draw stuff
		for (c = 0; c < node.numsurfaces; c++) {
			surf = r_worldmodel.surfaces[node.firstsurface + c];
			if (surf.visframe != r_framecount)
				continue;

			if ((surf.flags & Defines.SURF_PLANEBACK) != sidebit)
				continue; // wrong side

			if ((surf.texinfo.flags & Defines.SURF_SKY) != 0) { // just adds to
																// visible sky
																// bounds
				R_AddSkySurface(surf);
			} else if ((surf.texinfo.flags & (Defines.SURF_TRANS33 | Defines.SURF_TRANS66)) != 0) {
				// add to the translucent chain
				surf.texturechain = r_alpha_surfaces;
				r_alpha_surfaces = surf;
			} else {
				if ((surf.flags & Defines.SURF_DRAWTURB) == 0) {
					GL_RenderLightmappedPoly(surf);
				} else {
					// the polygon is visible, so add it to the texture
					// sorted chain
					// FIXME: this is a hack for animation
					image = R_TextureAnimation(surf.texinfo);
					surf.texturechain = image.texturechain;
					image.texturechain = surf;
				}
			}
		}

		// recurse down the back side
		R_RecursiveWorldNode(node.children[1 - side]);
	}

	/**
	 * R_DrawWorld.
	 */
	private final entity_t worldEnt = new entity_t();
	
	protected void R_DrawWorld() {

		if (r_drawworld.value == 0)
			return;

		if ((r_newrefdef.rdflags & Defines.RDF_NOWORLDMODEL) != 0)
			return;

		currentmodel = r_worldmodel;

		Math3D.VectorCopy(r_newrefdef.vieworg, modelorg);
		
		entity_t ent = worldEnt;
		// auto cycle the world frame for texture animation
		ent.clear();
		ent.frame = (int) (r_newrefdef.time * 2);
		currententity = ent;

		gl_state.currenttextures[0] = gl_state.currenttextures[1] = -1;

		ggl.glColor3f(1, 1, 1);


		// TODO wird bei multitexture nicht gebraucht
		//gl_lms.clearLightmapSurfaces();

		R_ClearSkyBox();

		GL_EnableMultitexture(true);

		GL_SelectTexture(GL_TEXTURE0);
		GL_TexEnv(ggl.GL_REPLACE);
		ggl.glInterleavedArrays(ggl.GL_T2F_V3F, Polygon.BYTE_STRIDE,
				globalPolygonInterleavedBuf);
		GL_SelectTexture(GL_TEXTURE1);
		ggl.glTexCoordPointer(2, ggl.GL_FLOAT, Polygon.BYTE_STRIDE,
				globalPolygonTexCoord1Buf);
		ggl.glEnableClientState(ggl.GL_TEXTURE_COORD_ARRAY);

		if (gl_lightmap.value != 0)
			GL_TexEnv(ggl.GL_REPLACE);
		else
			GL_TexEnv(ggl.GL_MODULATE);

		R_RecursiveWorldNode(r_worldmodel.nodes[0]); // root node

		ggl.glClientActiveTextureARB(GL_TEXTURE1);
		ggl.glDisableClientState(ggl.GL_TEXTURE_COORD_ARRAY);

		GL_EnableMultitexture(false);

		DrawTextureChains();
		R_DrawSkyBox();
		R_DrawTriangleOutlines();
	}
	


 
	/**
	 * LIGHTMAP ALLOCATION
	 * 
	 */

	protected void LM_UploadBlock(boolean dynamic) {
		int texture;
		int height = 0;

		if (dynamic) {
			texture = 0;
		} else {
			texture = gl_lms.current_lightmap_texture;
		}

		GL_Bind(gl_state.lightmap_textures + texture);
		ggl.glTexParameterf(ggl.GL_TEXTURE_2D, ggl.GL_TEXTURE_MIN_FILTER,
				ggl.GL_LINEAR);
		ggl.glTexParameterf(ggl.GL_TEXTURE_2D, ggl.GL_TEXTURE_MAG_FILTER,
				ggl.GL_LINEAR);
		
		gl_lms.lightmap_buffer.rewind();
		if (dynamic) {
			int i;

			for (i = 0; i < LM_BLOCK_WIDTH; i++) {
				if (gl_lms.allocated[i] > height)
					height = gl_lms.allocated[i];
			}

			ggl.glTexSubImage2D(ggl.GL_TEXTURE_2D, 0, 0, 0, LM_BLOCK_WIDTH, height,
					GL_LIGHTMAP_FORMAT, ggl.GL_UNSIGNED_BYTE,
					gl_lms.lightmap_buffer);
		} else {
			ggl.glTexImage2D(ggl.GL_TEXTURE_2D, 0, gl_lms.internal_format,
					LM_BLOCK_WIDTH, LM_BLOCK_HEIGHT, 0, GL_LIGHTMAP_FORMAT,
					ggl.GL_UNSIGNED_BYTE, gl_lms.lightmap_buffer);
			if (++gl_lms.current_lightmap_texture == MAX_LIGHTMAPS)
				Com.Error(Defines.ERR_DROP,
						"LM_UploadBlock() - MAX_LIGHTMAPS exceeded\n");

			//debugLightmap(gl_lms.lightmap_buffer, 128, 128, 4);
		}
	}


	/**
	 * GL_BuildPolygonFromSurface.
	 */
	protected void GL_BuildPolygonFromSurface(msurface_t fa) {
		int i, lindex, lnumverts;
		medge_t[] pedges;
		medge_t r_pedge;
		float[] vec;
		float s, t;
		 // reconstruct the polygon
		pedges = currentmodel.edges;
		lnumverts = fa.numedges;

		//
		// draw texture
		//
		glpoly_t poly = Polygon.create(lnumverts);

		poly.next = fa.polys;
		poly.flags = fa.flags;
		fa.polys = poly;

		for (i = 0; i < lnumverts; i++) {
			lindex = currentmodel.surfedges[fa.firstedge + i];

			if (lindex > 0) {
				r_pedge = pedges[lindex];
				vec = currentmodel.vertexes[r_pedge.v[0]].position;
			} else {
				r_pedge = pedges[-lindex];
				vec = currentmodel.vertexes[r_pedge.v[1]].position;
			}
			s = Math3D.DotProduct(vec, fa.texinfo.vecs[0]) + fa.texinfo.vecs[0][3];
			s /= fa.texinfo.image.width;

			t = Math3D.DotProduct(vec, fa.texinfo.vecs[1]) + fa.texinfo.vecs[1][3];
			t /= fa.texinfo.image.height;

 			poly.x(i, vec[0]);
			poly.y(i, vec[1]);
			poly.z(i, vec[2]);
			poly.s1(i, s);
			poly.t1(i, t);

			//
			// lightmap texture coordinates
			//
			s = Math3D.DotProduct(vec, fa.texinfo.vecs[0])
					+ fa.texinfo.vecs[0][3];
			s -= fa.texturemins[0];
			s += fa.light_s * 16;
			s += 8;
			s /= LM_BLOCK_WIDTH * 16; // fa.texinfo.texture.width;

			t = Math3D.DotProduct(vec, fa.texinfo.vecs[1])
					+ fa.texinfo.vecs[1][3];
			t -= fa.texturemins[1];
			t += fa.light_t * 16;
			t += 8;
			t /= LM_BLOCK_HEIGHT * 16; // fa.texinfo.texture.height;

			poly.s2(i, s);
			poly.t2(i, t);
		}
	}

	/**
	 *  GL_CreateSurfaceLightmap.
	 */
	protected void GL_CreateSurfaceLightmap(msurface_t surf) {
		int smax, tmax;
		IntBuffer base;

		if ((surf.flags & (Defines.SURF_DRAWSKY | Defines.SURF_DRAWTURB)) != 0)
			return;

		smax = (surf.extents[0] >> 4) + 1;
		tmax = (surf.extents[1] >> 4) + 1;

		pos_t lightPos = new pos_t(surf.light_s, surf.light_t);

		if (!LM_AllocBlock(smax, tmax, lightPos)) {
			LM_UploadBlock(false);
			LM_InitBlock();
			lightPos = new pos_t(surf.light_s, surf.light_t);
			if (!LM_AllocBlock(smax, tmax, lightPos)) {
				Com.Error(Defines.ERR_FATAL,
						"Consecutive calls to LM_AllocBlock(" + smax + ","
								+ tmax + ") failed\n");
			}
		}

		// kopiere die koordinaten zurueck
		surf.light_s = lightPos.x;
		surf.light_t = lightPos.y;

		surf.lightmaptexturenum = gl_lms.current_lightmap_texture;

		base = gl_lms.lightmap_buffer;
		base.position(surf.light_t * LM_BLOCK_WIDTH + surf.light_s);

		R_SetCacheState(surf);
		R_BuildLightMap(surf, base.slice(), LM_BLOCK_WIDTH);
	}	
}
