/*
 * Surf.java
 * Copyright (C) 2003
 *
 * $Id: Surf.java,v 1.12 2004-06-28 17:54:13 cwei Exp $
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
import jake2.client.*;
import jake2.game.cplane_t;
import jake2.render.*;
import jake2.util.Lib;
import jake2.util.Math3D;

import java.nio.*;
import java.util.Arrays;

import net.java.games.jogl.GL;
import net.java.games.jogl.util.BufferUtils;

/**
 * Surf
 *  
 * @author cwei
 */
public abstract class Surf extends Draw {

	// GL_RSURF.C: surface-related refresh code
	float[] modelorg = {0, 0, 0};		// relative to viewpoint

	msurface_t	r_alpha_surfaces;

	static final int DYNAMIC_LIGHT_WIDTH = 128;
	static final int DYNAMIC_LIGHT_HEIGHT = 128;

	static final int LIGHTMAP_BYTES = 4;

	static final int BLOCK_WIDTH = 128;
	static final int BLOCK_HEIGHT = 128;

	static final int MAX_LIGHTMAPS = 128;

	int c_visible_lightmaps;
	int c_visible_textures;

	static final int GL_LIGHTMAP_FORMAT = GL.GL_RGBA;

	static class gllightmapstate_t 
	{
		int internal_format;
		int current_lightmap_texture;

		msurface_t[] lightmap_surfaces = new msurface_t[MAX_LIGHTMAPS];
		int[] allocated = new int[BLOCK_WIDTH];

		// the lightmap texture data needs to be kept in
		// main memory so texsubimage can update properly
		//byte[] lightmap_buffer = new byte[4 * BLOCK_WIDTH * BLOCK_HEIGHT];
		IntBuffer lightmap_buffer = Lib.newIntBuffer(BLOCK_WIDTH * BLOCK_HEIGHT, ByteOrder.LITTLE_ENDIAN);
				
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

	gllightmapstate_t gl_lms = new gllightmapstate_t();

	// Model.java
	abstract byte[] Mod_ClusterPVS(int cluster, model_t model);
	// Warp.java
	abstract void R_DrawSkyBox();
	abstract void R_AddSkySurface(msurface_t surface);
	abstract void R_ClearSkyBox();
	abstract void EmitWaterPolys(msurface_t fa);
	// Light.java
	abstract void R_MarkLights (dlight_t light, int bit, mnode_t node);
	abstract void R_SetCacheState( msurface_t surf );
	abstract void R_BuildLightMap(msurface_t surf, IntBuffer dest, int stride);

	/*
	=============================================================

		BRUSH MODELS

	=============================================================
	*/

	/*
	===============
	R_TextureAnimation

	Returns the proper texture for a given time and base texture
	===============
	*/
	image_t R_TextureAnimation(mtexinfo_t tex)
	{
		int		c;

		if (tex.next == null)
			return tex.image;

		c = currententity.frame % tex.numframes;
		while (c != 0)
		{
			tex = tex.next;
			c--;
		}

		return tex.image;
	}

	/*
	================
	DrawGLPoly
	================
	*/
	void DrawGLPoly(glpoly_t p)
	{
		gl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
	}

	//	  ============
	//	  PGM
	/*
	================
	DrawGLFlowingPoly -- version of DrawGLPoly that handles scrolling texture
	================
	*/
	void DrawGLFlowingPoly(glpoly_t p)
	{
		int i;
		float scroll;

		scroll = -64 * ( (r_newrefdef.time / 40.0f) - (int)(r_newrefdef.time / 40.0f) );
		if(scroll == 0.0f)
			scroll = -64.0f;

		FloatBuffer texCoord = globalPolygonInterleavedBuf;
		float[][] v = p.verts;
		int index = p.pos * POLYGON_STRIDE;
		for (i=0 ; i<p.numverts ; i++) {
			texCoord.put(index, v[i][3] + scroll);
			index += POLYGON_STRIDE;
		}
		gl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
	}
	//	  PGM
	//	  ============

	/*
	** R_DrawTriangleOutlines
	*/
	void R_DrawTriangleOutlines()
	{
		int i, j;
		glpoly_t	p;

		if (gl_showtris.value == 0)
			return;

		gl.glDisable (GL.GL_TEXTURE_2D);
		gl.glDisable (GL.GL_DEPTH_TEST);
		gl.glColor4f (1,1,1,1);

		for (i=0 ; i<MAX_LIGHTMAPS ; i++)
		{
			msurface_t surf;

			for ( surf = gl_lms.lightmap_surfaces[i]; surf != null; surf = surf.lightmapchain )
			{
				p = surf.polys;
				for ( ; p != null ; p=p.chain)
				{
					for (j=2 ; j<p.numverts ; j++ )
					{
						gl.glBegin (GL.GL_LINE_STRIP);
						gl.glVertex3fv (p.verts[0]);
						gl.glVertex3fv (p.verts[j-1]);
						gl.glVertex3fv (p.verts[j]);
						gl.glVertex3fv (p.verts[0]);
						gl.glEnd ();
					}
				}
			}
		}

		gl.glEnable (GL.GL_DEPTH_TEST);
		gl.glEnable (GL.GL_TEXTURE_2D);
	}

	private IntBuffer temp2 = Lib.newIntBuffer(34 * 34, ByteOrder.LITTLE_ENDIAN);

	/*
	================
	R_RenderBrushPoly
	================
	*/
	void R_RenderBrushPoly(msurface_t fa)
	{
		int maps;
		image_t image;
		boolean is_dynamic = false;

		c_brush_polys++;

		image = R_TextureAnimation(fa.texinfo);

		if ((fa.flags & Defines.SURF_DRAWTURB) != 0)
		{	
			GL_Bind( image.texnum );

			// warp texture, no lightmaps
			GL_TexEnv( GL.GL_MODULATE );
			gl.glColor4f( gl_state.inverse_intensity, 
						gl_state.inverse_intensity,
						gl_state.inverse_intensity,
						1.0F );
			EmitWaterPolys (fa);
			GL_TexEnv( GL.GL_REPLACE );

			return;
		}
		else
		{
			GL_Bind( image.texnum );
			GL_TexEnv( GL.GL_REPLACE );
		}

		//	  ======
		//	  PGM
		if((fa.texinfo.flags & Defines.SURF_FLOWING) != 0)
			DrawGLFlowingPoly(fa.polys);
		else
			DrawGLPoly (fa.polys);
		//	  PGM
		//	  ======

		// ersetzt goto
		boolean gotoDynamic = false;
		/*
		** check for lightmap modification
		*/
		for ( maps = 0; maps < Defines.MAXLIGHTMAPS && fa.styles[maps] != (byte)255; maps++ )
		{
			if ( r_newrefdef.lightstyles[fa.styles[maps] & 0xFF].white != fa.cached_light[maps] ) {
				gotoDynamic = true;
				break;
			}
		}
		
		// this is a hack from cwei
		if (maps == 4) maps--;

		// dynamic this frame or dynamic previously
		if ( gotoDynamic || ( fa.dlightframe == r_framecount ) )
		{
			//	label dynamic:
			if ( gl_dynamic.value != 0 )
			{
				if (( fa.texinfo.flags & (Defines.SURF_SKY | Defines.SURF_TRANS33 | Defines.SURF_TRANS66 | Defines.SURF_WARP ) ) == 0)
				{
					is_dynamic = true;
				}
			}
		}

		if ( is_dynamic )
		{
			if ( ( (fa.styles[maps] & 0xFF) >= 32 || fa.styles[maps] == 0 ) && ( fa.dlightframe != r_framecount ) )
			{
				// ist ersetzt durch temp2:	unsigned	temp[34*34];
				int smax, tmax;

				smax = (fa.extents[0]>>4)+1;
				tmax = (fa.extents[1]>>4)+1;

				R_BuildLightMap( fa, temp2, smax);
				R_SetCacheState( fa );

				GL_Bind( gl_state.lightmap_textures + fa.lightmaptexturenum );

				gl.glTexSubImage2D( GL.GL_TEXTURE_2D, 0,
								  fa.light_s, fa.light_t, 
								  smax, tmax, 
								  GL_LIGHTMAP_FORMAT, 
								  GL.GL_UNSIGNED_BYTE, temp2 );

				fa.lightmapchain = gl_lms.lightmap_surfaces[fa.lightmaptexturenum];
				gl_lms.lightmap_surfaces[fa.lightmaptexturenum] = fa;
			}
			else
			{
				fa.lightmapchain = gl_lms.lightmap_surfaces[0];
				gl_lms.lightmap_surfaces[0] = fa;
			}
		}
		else
		{
			fa.lightmapchain = gl_lms.lightmap_surfaces[fa.lightmaptexturenum];
			gl_lms.lightmap_surfaces[fa.lightmaptexturenum] = fa;
		}
	}


	/*
	================
	R_DrawAlphaSurfaces

	Draw water surfaces and windows.
	The BSP tree is waled front to back, so unwinding the chain
	of alpha_surfaces will draw back to front, giving proper ordering.
	================
	*/
	void R_DrawAlphaSurfaces()
	{
		msurface_t s;
		float intens;

		//
		// go back to the world matrix
		//
		gl.glLoadMatrixf(r_world_matrix);

		gl.glEnable (GL.GL_BLEND);
		GL_TexEnv(GL.GL_MODULATE );
		

		// the textures are prescaled up for a better lighting range,
		// so scale it back down
		intens = gl_state.inverse_intensity;

		gl.glInterleavedArrays(GL.GL_T2F_V3F, POLYGON_BYTE_STRIDE, globalPolygonInterleavedBuf);

		for (s=r_alpha_surfaces ; s != null ; s=s.texturechain)
		{
			GL_Bind(s.texinfo.image.texnum);
			c_brush_polys++;
			if ((s.texinfo.flags & Defines.SURF_TRANS33) != 0)
				gl.glColor4f (intens, intens, intens, 0.33f);
			else if ((s.texinfo.flags & Defines.SURF_TRANS66) != 0)
				gl.glColor4f (intens, intens, intens, 0.66f);
			else
				gl.glColor4f (intens,intens,intens,1);
			if ((s.flags & Defines.SURF_DRAWTURB) != 0)
				EmitWaterPolys(s);
			else if((s.texinfo.flags & Defines.SURF_FLOWING) != 0)			// PGM	9/16/98
				DrawGLFlowingPoly(s.polys);							// PGM
			else
				DrawGLPoly(s.polys);
		}

		GL_TexEnv( GL.GL_REPLACE );
		gl.glColor4f (1,1,1,1);
		gl.glDisable (GL.GL_BLEND);

		r_alpha_surfaces = null;
	}

	/*
	================
	DrawTextureChains
	================
	*/
	void DrawTextureChains()
	{
		int i;
		msurface_t	s;
		image_t image;

		c_visible_textures = 0;

		for (i = 0; i < numgltextures ; i++)
		{
			image = gltextures[i];

			if (image.registration_sequence == 0)
				continue;
			if (image.texturechain == null)
				continue;
			c_visible_textures++;

			for ( s = image.texturechain; s != null ; s=s.texturechain)
			{
				if ( ( s.flags & Defines.SURF_DRAWTURB) == 0 )
					R_RenderBrushPoly(s);
			}
		}

		GL_EnableMultitexture( false );
		for (i = 0; i < numgltextures ; i++)
		{
			image = gltextures[i];

			if (image.registration_sequence == 0)
				continue;
			s = image.texturechain;
			if (s == null)
				continue;

			for ( ; s != null ; s=s.texturechain)
			{
				if ( (s.flags & Defines.SURF_DRAWTURB) != 0 )
					R_RenderBrushPoly(s);
			}

			image.texturechain = null;
		}

		GL_TexEnv( GL.GL_REPLACE );
	}

	// direct buffer
	private IntBuffer temp = Lib.newIntBuffer(128 * 128, ByteOrder.LITTLE_ENDIAN);
			
	void GL_RenderLightmappedPoly( msurface_t surf )
	{
		int i, nv = surf.polys.numverts;
		int map = 0;
		int index;
		float[][] v;
		FloatBuffer texCoord = globalPolygonInterleavedBuf;
		image_t image = R_TextureAnimation( surf.texinfo );
		boolean is_dynamic = false;
		int lmtex = surf.lightmaptexturenum;
		glpoly_t p;

		// ersetzt goto
		boolean gotoDynamic = false;

		for ( map = 0; map < Defines.MAXLIGHTMAPS && (surf.styles[map] != (byte)255); map++ )
		{
			if ( r_newrefdef.lightstyles[surf.styles[map] & 0xFF].white != surf.cached_light[map] ) {
				gotoDynamic = true;
				break;
			}
		}

		// this is a hack from cwei
		if (map == 4) map--;

		// dynamic this frame or dynamic previously
		if ( gotoDynamic || ( surf.dlightframe == r_framecount ) )
		{
			//	label dynamic:
			if ( gl_dynamic.value != 0 )
			{
				if ( (surf.texinfo.flags & (Defines.SURF_SKY | Defines.SURF_TRANS33 | Defines.SURF_TRANS66 | Defines.SURF_WARP )) == 0 )
				{
					is_dynamic = true;
				}
			}
		}

		if ( is_dynamic )
		{
			// ist raus gezogen worden int[] temp = new int[128*128];
			int smax, tmax;

			if ( ( (surf.styles[map] & 0xFF) >= 32 || surf.styles[map] == 0 ) && ( surf.dlightframe != r_framecount ) )
			{
				smax = (surf.extents[0]>>4)+1;
				tmax = (surf.extents[1]>>4)+1;

				R_BuildLightMap( surf, temp, smax);
				R_SetCacheState( surf );

				GL_MBind( GL_TEXTURE1, gl_state.lightmap_textures + surf.lightmaptexturenum );

				lmtex = surf.lightmaptexturenum;

				gl.glTexSubImage2D( GL.GL_TEXTURE_2D, 0,
								  surf.light_s, surf.light_t, 
								  smax, tmax, 
								  GL_LIGHTMAP_FORMAT, 
								  GL.GL_UNSIGNED_BYTE, temp );

			}
			else
			{
				smax = (surf.extents[0]>>4)+1;
				tmax = (surf.extents[1]>>4)+1;

				R_BuildLightMap( surf, temp, smax);

				GL_MBind( GL_TEXTURE1, gl_state.lightmap_textures + 0 );

				lmtex = 0;

				gl.glTexSubImage2D( GL.GL_TEXTURE_2D, 0,
								  surf.light_s, surf.light_t, 
								  smax, tmax, 
								  GL_LIGHTMAP_FORMAT, 
								  GL.GL_UNSIGNED_BYTE, temp );

			}

			c_brush_polys++;

			GL_MBind( GL_TEXTURE0, image.texnum );
			GL_MBind( GL_TEXTURE1, gl_state.lightmap_textures + lmtex );

			// ==========
			//	  PGM
			if ((surf.texinfo.flags & Defines.SURF_FLOWING) != 0)
			{
				float scroll;
		
				scroll = -64 * ( (r_newrefdef.time / 40.0f) - (int)(r_newrefdef.time / 40.0f) );
				if(scroll == 0.0f)
					scroll = -64.0f;

				for ( p = surf.polys; p != null; p = p.chain )
				{
					v = p.verts;
					index = p.pos * POLYGON_STRIDE;
					for (i=0 ; i<p.numverts ; i++) {
						texCoord.put(index, v[i][3] + scroll);
						index += POLYGON_STRIDE;
					}
					gl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
				}
			}
			else
			{
				for ( p = surf.polys; p != null; p = p.chain )
				{
					gl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
				}
			}
			// PGM
			// ==========
		}
		else
		{
			c_brush_polys++;

			GL_MBind( GL_TEXTURE0, image.texnum );
			GL_MBind( GL_TEXTURE1, gl_state.lightmap_textures + lmtex);
			
			// ==========
			//	  PGM
			if ((surf.texinfo.flags & Defines.SURF_FLOWING) != 0)
			{
				float scroll;
		
				scroll = -64 * ( (r_newrefdef.time / 40.0f) - (int)(r_newrefdef.time / 40.0f) );
				if(scroll == 0.0)
					scroll = -64.0f;

				for ( p = surf.polys; p != null; p = p.chain )
				{
					v = p.verts;
					index = p.pos * POLYGON_STRIDE;
					for (i=0 ; i<p.numverts ; i++) {
						texCoord.put(index, v[i][3] + scroll);
						index += POLYGON_STRIDE;
					}
					gl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
				}
			}
			else
			{
			// PGM
			//  ==========
				for ( p = surf.polys; p != null; p = p.chain )
				{
					gl.glDrawArrays(GL.GL_POLYGON, p.pos, p.numverts);
				}
				
			// ==========
			// PGM
			}
			// PGM
			// ==========
		}
	}

	/*
	=================
	R_DrawInlineBModel
	=================
	*/
	void R_DrawInlineBModel()
	{
		int i, k;
		cplane_t pplane;
		float dot;
		msurface_t	psurf;
		dlight_t	lt;

		// calculate dynamic lighting for bmodel
		if ( gl_flashblend.value == 0 )
		{
			for (k=0 ; k<r_newrefdef.num_dlights ; k++)
			{
				lt = r_newrefdef.dlights[k];
				R_MarkLights(lt, 1<<k, currentmodel.nodes[currentmodel.firstnode]);
			}
		}

		// psurf = &currentmodel->surfaces[currentmodel->firstmodelsurface];
		int psurfp = currentmodel.firstmodelsurface;
		msurface_t[] surfaces;
		surfaces = currentmodel.surfaces;
		//psurf = surfaces[psurfp];

		if ( (currententity.flags & Defines.RF_TRANSLUCENT) != 0 )
		{
			gl.glEnable (GL.GL_BLEND);
			gl.glColor4f (1,1,1,0.25f);
			GL_TexEnv( GL.GL_MODULATE );
		}

		//
		// draw texture
		//
		for (i=0 ; i<currentmodel.nummodelsurfaces ; i++)
		{
			psurf = surfaces[psurfp++];
			// find which side of the node we are on
			pplane = psurf.plane;

			dot = Math3D.DotProduct(modelorg, pplane.normal) - pplane.dist;

			// draw the polygon
			if (((psurf.flags & Defines.SURF_PLANEBACK) != 0 && (dot < -BACKFACE_EPSILON)) ||
				((psurf.flags & Defines.SURF_PLANEBACK) == 0 && (dot > BACKFACE_EPSILON)))
			{
				if ((psurf.texinfo.flags & (Defines.SURF_TRANS33 | Defines.SURF_TRANS66)) != 0 )
				{	// add to the translucent chain
					psurf.texturechain = r_alpha_surfaces;
					r_alpha_surfaces = psurf;
				}
				else if ( (psurf.flags & Defines.SURF_DRAWTURB) == 0 )
				{
					GL_RenderLightmappedPoly( psurf );
				}
				else
				{
					GL_EnableMultitexture( false );
					R_RenderBrushPoly( psurf );
					GL_EnableMultitexture( true );
				}
			}
		}
		
		if ( (currententity.flags & Defines.RF_TRANSLUCENT) != 0 ) {
			gl.glDisable (GL.GL_BLEND);
			gl.glColor4f (1,1,1,1);
			GL_TexEnv( GL.GL_REPLACE );
		}
	}

	/*
	=================
	R_DrawBrushModel
	=================
	*/
	void R_DrawBrushModel(entity_t e)
	{
		float[] mins = {0, 0, 0};
		float[] maxs = {0, 0, 0};
		int i;
		boolean rotated;

		if (currentmodel.nummodelsurfaces == 0)
			return;

		currententity = e;
		gl_state.currenttextures[0] = gl_state.currenttextures[1] = -1;

		if (e.angles[0] != 0 || e.angles[1] != 0 || e.angles[2] != 0)
		{
			rotated = true;
			for (i=0 ; i<3 ; i++)
			{
				mins[i] = e.origin[i] - currentmodel.radius;
				maxs[i] = e.origin[i] + currentmodel.radius;
			}
		}
		else
		{
			rotated = false;
			Math3D.VectorAdd(e.origin, currentmodel.mins, mins);
			Math3D.VectorAdd(e.origin, currentmodel.maxs, maxs);
		}

		if (R_CullBox(mins, maxs)) return;

		gl.glColor3f (1,1,1);
		
		// memset (gl_lms.lightmap_surfaces, 0, sizeof(gl_lms.lightmap_surfaces));
		
		// TODO wird beim multitexturing nicht gebraucht
		//gl_lms.clearLightmapSurfaces();
		
		Math3D.VectorSubtract (r_newrefdef.vieworg, e.origin, modelorg);
		if (rotated)
		{
			float[] temp = {0, 0, 0};
			float[] forward = {0, 0, 0};
			float[] right = {0, 0, 0};
			float[] up = {0, 0, 0};

			Math3D.VectorCopy (modelorg, temp);
			Math3D.AngleVectors (e.angles, forward, right, up);
			modelorg[0] = Math3D.DotProduct (temp, forward);
			modelorg[1] = -Math3D.DotProduct (temp, right);
			modelorg[2] = Math3D.DotProduct (temp, up);
		}

		gl.glPushMatrix();
		
		e.angles[0] = -e.angles[0];	// stupid quake bug
		e.angles[2] = -e.angles[2];	// stupid quake bug
		R_RotateForEntity(e);
		e.angles[0] = -e.angles[0];	// stupid quake bug
		e.angles[2] = -e.angles[2];	// stupid quake bug

		GL_EnableMultitexture( true );
		GL_SelectTexture(GL_TEXTURE0);
		GL_TexEnv( GL.GL_REPLACE );
		gl.glInterleavedArrays(GL.GL_T2F_V3F, POLYGON_BYTE_STRIDE, globalPolygonInterleavedBuf);
		GL_SelectTexture(GL_TEXTURE1);
		GL_TexEnv( GL.GL_MODULATE );
		gl.glTexCoordPointer(2, GL.GL_FLOAT, POLYGON_BYTE_STRIDE, globalPolygonTexCoord1Buf);
		gl.glEnableClientState(GL.GL_TEXTURE_COORD_ARRAY);

		R_DrawInlineBModel();

		gl.glClientActiveTextureARB(GL_TEXTURE1);
		gl.glDisableClientState(GL.GL_TEXTURE_COORD_ARRAY);

		GL_EnableMultitexture( false );

		gl.glPopMatrix();
	}

	/*
	=============================================================

		WORLD MODEL

	=============================================================
	*/

	/*
	================
	R_RecursiveWorldNode
	================
	*/
	void R_RecursiveWorldNode (mnode_t node)
	{
		int c, side, sidebit;
		cplane_t plane;
		msurface_t surf;
		msurface_t mark;
		mleaf_t pleaf;
		float dot = 0;
		image_t image;

		if (node.contents == Defines.CONTENTS_SOLID)
			return;		// solid
		
		if (node.visframe != r_visframecount)
			return;
			
		if (R_CullBox(node.mins, node.maxs))
			return;
	
		// if a leaf node, draw stuff
		if (node.contents != -1)
		{
			pleaf = (mleaf_t)node;

			// check for door connected areas
			if (r_newrefdef.areabits != null)
			{
				if ( ((r_newrefdef.areabits[pleaf.area >> 3] & 0xFF) & (1 << (pleaf.area & 7)) ) == 0 )
					return;		// not visible
			}

			int markp = 0;

			mark = pleaf.getMarkSurface(markp); // first marked surface
			c = pleaf.nummarksurfaces;

			if (c != 0)
			{
				do
				{
					mark.visframe = r_framecount;
					mark = pleaf.getMarkSurface(++markp); // next surface
				} while (--c != 0);
			}

			return;
		}

		// node is just a decision point, so go down the apropriate sides

		// find which side of the node we are on
		plane = node.plane;

		switch (plane.type)
		{
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

		if (dot >= 0.0f)
		{
			side = 0;
			sidebit = 0;
		}
		else
		{
			side = 1;
			sidebit = Defines.SURF_PLANEBACK;
		}

		// recurse down the children, front side first
		R_RecursiveWorldNode(node.children[side]);

		// draw stuff
		//for ( c = node.numsurfaces, surf = r_worldmodel.surfaces[node.firstsurface]; c != 0 ; c--, surf++)
		for ( c = 0; c < node.numsurfaces; c++)
		{
			surf = r_worldmodel.surfaces[node.firstsurface + c];
			if (surf.visframe != r_framecount)
				continue;

			if ( (surf.flags & Defines.SURF_PLANEBACK) != sidebit )
				continue;		// wrong side

			if ((surf.texinfo.flags & Defines.SURF_SKY) != 0)
			{	// just adds to visible sky bounds
				R_AddSkySurface(surf);
			}
			else if ((surf.texinfo.flags & (Defines.SURF_TRANS33 | Defines.SURF_TRANS66)) != 0)
			{	// add to the translucent chain
				surf.texturechain = r_alpha_surfaces;
				r_alpha_surfaces = surf;
			}
			else
			{
				if (  ( surf.flags & Defines.SURF_DRAWTURB) == 0 )
				{
					GL_RenderLightmappedPoly( surf );
				}
				else
				{
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


	/*
	=============
	R_DrawWorld
	=============
	*/
	void R_DrawWorld()
	{
		entity_t	ent = new entity_t();

		if (r_drawworld.value == 0)
			return;

		if ( (r_newrefdef.rdflags & Defines.RDF_NOWORLDMODEL) != 0 )
			return;

		currentmodel = r_worldmodel;

		Math3D.VectorCopy(r_newrefdef.vieworg, modelorg);

		// auto cycle the world frame for texture animation
		// memset (&ent, 0, sizeof(ent));
		ent.frame = (int)(r_newrefdef.time*2);
		currententity = ent;

		gl_state.currenttextures[0] = gl_state.currenttextures[1] = -1;

		gl.glColor3f (1,1,1);
		// memset (gl_lms.lightmap_surfaces, 0, sizeof(gl_lms.lightmap_surfaces));
		// TODO wird bei multitexture nicht gebraucht
		//gl_lms.clearLightmapSurfaces();
		
		R_ClearSkyBox();

		GL_EnableMultitexture( true );

		GL_SelectTexture( GL_TEXTURE0);
		GL_TexEnv( GL.GL_REPLACE );
		gl.glInterleavedArrays(GL.GL_T2F_V3F, POLYGON_BYTE_STRIDE, globalPolygonInterleavedBuf);
		GL_SelectTexture( GL_TEXTURE1);
		gl.glTexCoordPointer(2, GL.GL_FLOAT, POLYGON_BYTE_STRIDE, globalPolygonTexCoord1Buf);
		gl.glEnableClientState(GL.GL_TEXTURE_COORD_ARRAY);

		if ( gl_lightmap.value != 0)
			GL_TexEnv( GL.GL_REPLACE );
		else 
			GL_TexEnv( GL.GL_MODULATE );
				
		R_RecursiveWorldNode(r_worldmodel.nodes[0]); // root node
				
		gl.glClientActiveTextureARB(GL_TEXTURE1);
		gl.glDisableClientState(GL.GL_TEXTURE_COORD_ARRAY);

		GL_EnableMultitexture( false );

		DrawTextureChains();
		R_DrawSkyBox();
		R_DrawTriangleOutlines();
	}

	byte[] fatvis = new byte[Defines.MAX_MAP_LEAFS / 8];

	/*
	===============
	R_MarkLeaves

	Mark the leaves and nodes that are in the PVS for the current
	cluster
	===============
	*/
	void R_MarkLeaves()
	{
		byte[] vis;
		//byte[] fatvis = new byte[Defines.MAX_MAP_LEAFS / 8];
		
		Arrays.fill(fatvis, (byte)0);
		
		mnode_t node;
		int i, c;
		mleaf_t leaf;
		int cluster;

		if (r_oldviewcluster == r_viewcluster && r_oldviewcluster2 == r_viewcluster2 && r_novis.value == 0 && r_viewcluster != -1)
			return;

		// development aid to let you run around and see exactly where
		// the pvs ends
		if (gl_lockpvs.value != 0)
			return;

		r_visframecount++;
		r_oldviewcluster = r_viewcluster;
		r_oldviewcluster2 = r_viewcluster2;

		if (r_novis.value != 0 || r_viewcluster == -1 || r_worldmodel.vis == null)
		{
			// mark everything
			for (i=0 ; i<r_worldmodel.numleafs ; i++)
				r_worldmodel.leafs[i].visframe = r_visframecount;
			for (i=0 ; i<r_worldmodel.numnodes ; i++)
				r_worldmodel.nodes[i].visframe = r_visframecount;
			return;
		}

		vis = Mod_ClusterPVS(r_viewcluster, r_worldmodel);
		// may have to combine two clusters because of solid water boundaries
		if (r_viewcluster2 != r_viewcluster)
		{
			// memcpy (fatvis, vis, (r_worldmodel.numleafs+7)/8);
			System.arraycopy(vis, 0, fatvis, 0, (r_worldmodel.numleafs+7) / 8);
			vis = Mod_ClusterPVS(r_viewcluster2, r_worldmodel);
			c = (r_worldmodel.numleafs + 31) / 32;
			int k = 0;
			for (i=0 ; i<c ; i++) {
				fatvis[k] |= vis[k++];
				fatvis[k] |= vis[k++];
				fatvis[k] |= vis[k++];
				fatvis[k] |= vis[k++];
			}

			vis = fatvis;
		}
	
		for ( i=0; i < r_worldmodel.numleafs; i++)
		{
			leaf = r_worldmodel.leafs[i];
			cluster = leaf.cluster;
			if (cluster == -1)
				continue;
			if (((vis[cluster>>3] & 0xFF) & (1 << (cluster & 7))) != 0)
			{
				node = (mnode_t)leaf;
				do
				{
					if (node.visframe == r_visframecount)
						break;
					node.visframe = r_visframecount;
					node = node.parent;
				} while (node != null);
			}
		}
	}



	/*
	=============================================================================

	  LIGHTMAP ALLOCATION

	=============================================================================
	*/

	void LM_InitBlock()
	{
		Arrays.fill(gl_lms.allocated, 0);
	}

	void LM_UploadBlock( boolean dynamic )
	{
		int texture;
		int height = 0;

		if ( dynamic )
		{
			texture = 0;
		}
		else
		{
			texture = gl_lms.current_lightmap_texture;
		}

		GL_Bind( gl_state.lightmap_textures + texture );
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);

		if ( dynamic )
		{
			int i;

			for ( i = 0; i < BLOCK_WIDTH; i++ )
			{
				if ( gl_lms.allocated[i] > height )
					height = gl_lms.allocated[i];
			}

			gl.glTexSubImage2D( GL.GL_TEXTURE_2D, 
							  0,
							  0, 0,
							  BLOCK_WIDTH, height,
							  GL_LIGHTMAP_FORMAT,
							  GL.GL_UNSIGNED_BYTE,
							  gl_lms.lightmap_buffer );
		}
		else
		{
			gl.glTexImage2D( GL.GL_TEXTURE_2D, 
						   0, 
						   gl_lms.internal_format,
						   BLOCK_WIDTH, BLOCK_HEIGHT, 
						   0, 
						   GL_LIGHTMAP_FORMAT, 
						   GL.GL_UNSIGNED_BYTE, 
						   gl_lms.lightmap_buffer );
			if ( ++gl_lms.current_lightmap_texture == MAX_LIGHTMAPS )
				ri.Sys_Error( Defines.ERR_DROP, "LM_UploadBlock() - MAX_LIGHTMAPS exceeded\n" );
				
				
			//debugLightmap(gl_lms.lightmap_buffer, 128, 128, 4);

		}
	}

	// returns a texture number and the position inside it
	boolean LM_AllocBlock (int w, int h, pos_t pos)
	{
		int x = pos.x; 
		int y = pos.y;
		int i, j;
		int best, best2;

		best = BLOCK_HEIGHT;

		for (i=0 ; i<BLOCK_WIDTH-w ; i++)
		{
			best2 = 0;

			for (j=0 ; j<w ; j++)
			{
				if (gl_lms.allocated[i+j] >= best)
					break;
				if (gl_lms.allocated[i+j] > best2)
					best2 = gl_lms.allocated[i+j];
			}
			if (j == w)
			{	// this is a valid spot
				pos.x = x = i;
				pos.y = y = best = best2;
			}
		}

		if (best + h > BLOCK_HEIGHT)
			return false;

		for (i=0 ; i<w ; i++)
			gl_lms.allocated[x + i] = best + h;

		return true;
	}

	/*
	================
	GL_BuildPolygonFromSurface
	================
	*/
	void GL_BuildPolygonFromSurface(msurface_t fa)
	{
		int i, lindex, lnumverts;
		medge_t[] pedges;
		medge_t r_pedge;
		int vertpage;
		float[] vec;
		float s, t;
		glpoly_t	poly;
		float[] total = {0, 0, 0};

		// reconstruct the polygon
		pedges = currentmodel.edges;
		lnumverts = fa.numedges;
		vertpage = 0;

		Math3D.VectorClear(total);
		//
		// draw texture
		//
		// poly = Hunk_Alloc (sizeof(glpoly_t) + (lnumverts-4) * VERTEXSIZE*sizeof(float));
		poly = new glpoly_t(lnumverts);

		poly.next = fa.polys;
		poly.flags = fa.flags;
		fa.polys = poly;
		poly.numverts = lnumverts;

		for (i=0 ; i<lnumverts ; i++)
		{
			lindex = currentmodel.surfedges[fa.firstedge + i];

			if (lindex > 0)
			{
				r_pedge = pedges[lindex];
				vec = currentmodel.vertexes[r_pedge.v[0]].position;
			}
			else
			{
				r_pedge = pedges[-lindex];
				vec = currentmodel.vertexes[r_pedge.v[1]].position;
			}
			s = Math3D.DotProduct (vec, fa.texinfo.vecs[0]) + fa.texinfo.vecs[0][3];
			s /= fa.texinfo.image.width;

			t = Math3D.DotProduct (vec, fa.texinfo.vecs[1]) + fa.texinfo.vecs[1][3];
			t /= fa.texinfo.image.height;

			Math3D.VectorAdd (total, vec, total);
			Math3D.VectorCopy (vec, poly.verts[i]);
			poly.verts[i][3] = s;
			poly.verts[i][4] = t;

			//
			// lightmap texture coordinates
			//
			s = Math3D.DotProduct (vec, fa.texinfo.vecs[0]) + fa.texinfo.vecs[0][3];
			s -= fa.texturemins[0];
			s += fa.light_s*16;
			s += 8;
			s /= BLOCK_WIDTH*16; //fa.texinfo.texture.width;

			t = Math3D.DotProduct (vec, fa.texinfo.vecs[1]) + fa.texinfo.vecs[1][3];
			t -= fa.texturemins[1];
			t += fa.light_t*16;
			t += 8;
			t /= BLOCK_HEIGHT*16; //fa.texinfo.texture.height;

			poly.verts[i][5] = s;
			poly.verts[i][6] = t;
		}

		poly.numverts = lnumverts;
		
		precompilePolygon(poly);

	}

	/*
	========================
	GL_CreateSurfaceLightmap
	========================
	*/
	void GL_CreateSurfaceLightmap(msurface_t surf)
	{
		int smax, tmax;
		IntBuffer base;

		if ( (surf.flags & (Defines.SURF_DRAWSKY | Defines.SURF_DRAWTURB)) != 0)
			return;

		smax = (surf.extents[0]>>4)+1;
		tmax = (surf.extents[1]>>4)+1;
		
		pos_t lightPos = new pos_t(surf.light_s, surf.light_t);

		if ( !LM_AllocBlock( smax, tmax, lightPos ) )
		{
			LM_UploadBlock( false );
			LM_InitBlock();
			lightPos = new pos_t(surf.light_s, surf.light_t);
			if ( !LM_AllocBlock( smax, tmax, lightPos ) )
			{
				ri.Sys_Error( Defines.ERR_FATAL, "Consecutive calls to LM_AllocBlock(" + smax +"," + tmax +") failed\n");
			}
		}
		
		// kopiere die koordinaten zurueck
		surf.light_s = lightPos.x;
		surf.light_t = lightPos.y;

		surf.lightmaptexturenum = gl_lms.current_lightmap_texture;
		
		base = gl_lms.lightmap_buffer;
		base.position(surf.light_t * BLOCK_WIDTH + surf.light_s);

		R_SetCacheState( surf );
		R_BuildLightMap(surf, base.slice(), BLOCK_WIDTH);
	}

	lightstyle_t[] lightstyles;
	IntBuffer dummy = BufferUtils.newIntBuffer(128*128);

	/*
	==================
	GL_BeginBuildingLightmaps

	==================
	*/
	void GL_BeginBuildingLightmaps(model_t m)
	{
		// static lightstyle_t	lightstyles[MAX_LIGHTSTYLES];
		int i;

		// init lightstyles
		if ( lightstyles == null ) {
			lightstyles = new lightstyle_t[Defines.MAX_LIGHTSTYLES];
			for (i = 0; i < lightstyles.length; i++)
			{
				lightstyles[i] = new lightstyle_t();				
			}
		}

		// memset( gl_lms.allocated, 0, sizeof(gl_lms.allocated) );
		Arrays.fill(gl_lms.allocated, 0);

		r_framecount = 1;		// no dlightcache

		GL_EnableMultitexture( true );
		GL_SelectTexture( GL_TEXTURE1);

		/*
		** setup the base lightstyles so the lightmaps won't have to be regenerated
		** the first time they're seen
		*/
		for (i=0 ; i < Defines.MAX_LIGHTSTYLES ; i++)
		{
			lightstyles[i].rgb[0] = 1;
			lightstyles[i].rgb[1] = 1;
			lightstyles[i].rgb[2] = 1;
			lightstyles[i].white = 3;
		}
		r_newrefdef.lightstyles = lightstyles;

		if (gl_state.lightmap_textures == 0)
		{
			gl_state.lightmap_textures = TEXNUM_LIGHTMAPS;
		}

		gl_lms.current_lightmap_texture = 1;

		/*
		** if mono lightmaps are enabled and we want to use alpha
		** blending (a,1-a) then we're likely running on a 3DLabs
		** Permedia2.  In a perfect world we'd use a GL_ALPHA lightmap
		** in order to conserve space and maximize bandwidth, however 
		** this isn't a perfect world.
		**
		** So we have to use alpha lightmaps, but stored in GL_RGBA format,
		** which means we only get 1/16th the color resolution we should when
		** using alpha lightmaps.  If we find another board that supports
		** only alpha lightmaps but that can at least support the GL_ALPHA
		** format then we should change this code to use real alpha maps.
		*/
		
		char format = gl_monolightmap.string.toUpperCase().charAt(0);
		
		if ( format == 'A' )
		{
			gl_lms.internal_format = gl_tex_alpha_format;
		}
		/*
		** try to do hacked colored lighting with a blended texture
		*/
		else if ( format == 'C' )
		{
			gl_lms.internal_format = gl_tex_alpha_format;
		}
		else if ( format == 'I' )
		{
			gl_lms.internal_format = GL.GL_INTENSITY8;
		}
		else if ( format == 'L' ) 
		{
			gl_lms.internal_format = GL.GL_LUMINANCE8;
		}
		else
		{
			gl_lms.internal_format = gl_tex_solid_format;
		}

		/*
		** initialize the dynamic lightmap texture
		*/
		GL_Bind( gl_state.lightmap_textures + 0 );
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
		gl.glTexImage2D( GL.GL_TEXTURE_2D, 
					   0, 
					   gl_lms.internal_format,
					   BLOCK_WIDTH, BLOCK_HEIGHT, 
					   0, 
					   GL_LIGHTMAP_FORMAT, 
					   GL.GL_UNSIGNED_BYTE, 
					   dummy );
	}

	/*
	=======================
	GL_EndBuildingLightmaps
	=======================
	*/
	void GL_EndBuildingLightmaps()
	{
		LM_UploadBlock( false );
		GL_EnableMultitexture( false );
	}
	
	/*
	 * new functions for vertex array handling
	 */
	static final int POLYGON_BUFFER_SIZE = 120000;
	static final int POLYGON_STRIDE = 7;
	static final int POLYGON_BYTE_STRIDE = POLYGON_STRIDE * BufferUtils.SIZEOF_FLOAT;

	static FloatBuffer globalPolygonInterleavedBuf = BufferUtils.newFloatBuffer(POLYGON_BUFFER_SIZE * 7);
	static FloatBuffer globalPolygonTexCoord1Buf = null;

	static {
	 	globalPolygonInterleavedBuf.position(POLYGON_STRIDE - 2);
	 	globalPolygonTexCoord1Buf = globalPolygonInterleavedBuf.slice();
		globalPolygonInterleavedBuf.position(0);
	 };

	void precompilePolygon(glpoly_t p) {
		
		p.pos = globalPolygonInterleavedBuf.position() / POLYGON_STRIDE;
		
		float[] v;
		FloatBuffer buffer = globalPolygonInterleavedBuf;
		
		for (int i = 0; i < p.verts.length; i++) {
			v = p.verts[i];
			// textureCoord0
			buffer.put(v[3]);
			buffer.put(v[4]);
			
			// vertex
			buffer.put(v[0]);
			buffer.put(v[1]);
			buffer.put(v[2]);

			// textureCoord1
			buffer.put(v[5]);
			buffer.put(v[6]);
		}
	}

	public static void resetPolygonArrays() {
		globalPolygonInterleavedBuf.rewind();
	}

	//ImageFrame frame;
	
//	void debugLightmap(byte[] buf, int w, int h, float scale) {
//		IntBuffer pix = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
//		
//		int[] pixel = new int[w * h];
//		
//		pix.get(pixel);
//		
//		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
//		image.setRGB(0,  0, w, h, pixel, 0, w);
//		AffineTransformOp op = new AffineTransformOp(AffineTransform.getScaleInstance(scale, scale), AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
//		BufferedImage tmp = op.filter(image, null);
//		
//		if (frame == null) {
//			frame = new ImageFrame(null);
//			frame.show();
//		} 
//		frame.showImage(tmp);
//		
//	}

}
