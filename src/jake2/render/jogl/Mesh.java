/*
 * Mesh.java
 * Copyright (C) 2003
 *
 * $Id: Mesh.java,v 1.10 2004-02-26 14:09:16 cwei Exp $
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

import java.nio.FloatBuffer;

import net.java.games.gluegen.runtime.BufferFactory;
import net.java.games.jogl.GL;
import net.java.games.jogl.util.BufferUtils;
import jake2.Defines;
import jake2.Globals;
import jake2.client.entity_t;
import jake2.qcommon.qfiles;
import jake2.render.image_t;
import jake2.util.Math3D;

/**
 * Mesh
 *  
 * @author cwei
 */
public abstract class Mesh extends Light {

	// g_mesh.c: triangle model functions

	/*
	=============================================================

	  ALIAS MODELS

	=============================================================
	*/

	static final int NUMVERTEXNORMALS =	162;

	float[][] r_avertexnormals = Anorms.VERTEXNORMALS;

	float[][] s_lerped = new float[qfiles.MAX_VERTS][4];


	float[] shadevector = {0, 0, 0};
	float[] shadelight = {0, 0, 0};

	// precalculated dot products for quantized angles
	static final int SHADEDOT_QUANT = 16;

	float[][]	r_avertexnormal_dots = Anorms.VERTEXNORMAL_DOTS;

	float[] shadedots = r_avertexnormal_dots[0];

	void GL_LerpVerts( int nverts, qfiles.dtrivertx_t[] v, qfiles.dtrivertx_t[] ov, qfiles.dtrivertx_t[] verts, float[][] lerp, float[] move, float[] frontv, float[] backv )
	{
		int i;
		int lerpIndex = 0;

		//PMM -- added RF_SHELL_DOUBLE, RF_SHELL_HALF_DAM
		if ( (currententity.flags & ( Defines.RF_SHELL_RED | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE | Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0 )
		{
			float[] normal;
			for (i=0 ; i < nverts; i++/* , v++, ov++, lerp+=4 */)
			{
				normal = r_avertexnormals[verts[i].lightnormalindex];

				lerp[i][0] = move[0] + ov[i].v[0]*backv[0] + v[i].v[0]*frontv[0] + normal[0] * Defines.POWERSUIT_SCALE;
				lerp[i][1] = move[1] + ov[i].v[1]*backv[1] + v[i].v[1]*frontv[1] + normal[1] * Defines.POWERSUIT_SCALE;
				lerp[i][2] = move[2] + ov[i].v[2]*backv[2] + v[i].v[2]*frontv[2] + normal[2] * Defines.POWERSUIT_SCALE; 
			}
		}
		else
		{
			for (i=0 ; i < nverts; i++ /* , v++, ov++, lerp+=4 */)
			{
				lerp[i][0] = move[0] + ov[i].v[0]*backv[0] + v[i].v[0]*frontv[0];
				lerp[i][1] = move[1] + ov[i].v[1]*backv[1] + v[i].v[1]*frontv[1];
				lerp[i][2] = move[2] + ov[i].v[2]*backv[2] + v[i].v[2]*frontv[2];
			}
		}
	}

	void GL_LerpVerts( int nverts, qfiles.dtrivertx_t[] v, qfiles.dtrivertx_t[] ov, qfiles.dtrivertx_t[] verts, FloatBuffer lerp, float[] move, float[] frontv, float[] backv )
	{
		int i;
		int lerpIndex = 0;
		lerp.position(0);

		//PMM -- added RF_SHELL_DOUBLE, RF_SHELL_HALF_DAM
		if ( (currententity.flags & ( Defines.RF_SHELL_RED | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE | Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0 )
		{
			float[] normal;
			for (i=0 ; i < nverts; i++/* , v++, ov++, lerp+=4 */)
			{
				normal = r_avertexnormals[verts[i].lightnormalindex];

				lerp.put(move[0] + ov[i].v[0]*backv[0] + v[i].v[0]*frontv[0] + normal[0] * Defines.POWERSUIT_SCALE);
				lerp.put(move[1] + ov[i].v[1]*backv[1] + v[i].v[1]*frontv[1] + normal[1] * Defines.POWERSUIT_SCALE);
				lerp.put(move[2] + ov[i].v[2]*backv[2] + v[i].v[2]*frontv[2] + normal[2] * Defines.POWERSUIT_SCALE); 
				lerp.get();
			}
		}
		else
		{
			for (i=0 ; i < nverts; i++ /* , v++, ov++, lerp+=4 */)
			{
				lerp.put(move[0] + ov[i].v[0]*backv[0] + v[i].v[0]*frontv[0]);
				lerp.put(move[1] + ov[i].v[1]*backv[1] + v[i].v[1]*frontv[1]);
				lerp.put(move[2] + ov[i].v[2]*backv[2] + v[i].v[2]*frontv[2]);
				lerp.get();
			}
		}
	}

	FloatBuffer colorArrayBuf = BufferUtils.newFloatBuffer(qfiles.MAX_VERTS * 4);
	FloatBuffer vertexArrayBuf = BufferUtils.newFloatBuffer(qfiles.MAX_VERTS * 4);
	boolean isFilled = false;
	float[] tmpVec = {0, 0, 0};
			
	/*
	=============
	GL_DrawAliasFrameLerp

	interpolates between two frames and origins
	FIXME: batch lerp all vertexes
	=============
	*/
	void GL_DrawAliasFrameLerp (qfiles.dmdl_t paliashdr, float backlerp)
	{
		float 	l;
		qfiles.daliasframe_t	frame, oldframe;
		qfiles.dtrivertx_t[]	v, ov, verts;

		int[] order;
		int orderIndex = 0;
		int count;

		float	frontlerp;
		float	alpha;

		float[] move = {0, 0, 0}; // vec3_t
		float[] delta = {0, 0, 0}; // vec3_t
		float[][] vectors = {
			{0, 0, 0}, {0, 0, 0}, {0, 0, 0} // 3 mal vec3_t
		};
		
		float[] frontv = {0, 0, 0}; // vec3_t
		float[] backv = {0, 0, 0}; // vec3_t

		int		i;
		int		index_xyz;
		//float[][]	lerp;

		frame = paliashdr.aliasFrames[currententity.frame];

		verts = v = frame.verts;

		oldframe = paliashdr.aliasFrames[currententity.oldframe];

		ov = oldframe.verts;

		order = paliashdr.glCmds;

		if ((currententity.flags & Defines.RF_TRANSLUCENT) != 0)
			alpha = currententity.alpha;
		else
			alpha = 1.0f;

		// PMM - added double shell
		if ( (currententity.flags & ( Defines.RF_SHELL_RED | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE | Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0)
			gl.glDisable( GL.GL_TEXTURE_2D );

		frontlerp = 1.0f - backlerp;

		// move should be the delta back to the previous frame * backlerp
		Math3D.VectorSubtract (currententity.oldorigin, currententity.origin, delta);
		Math3D.AngleVectors (currententity.angles, vectors[0], vectors[1], vectors[2]);

		move[0] = Math3D.DotProduct (delta, vectors[0]);	// forward
		move[1] = -Math3D.DotProduct (delta, vectors[1]);	// left
		move[2] = Math3D.DotProduct (delta, vectors[2]);	// up

		Math3D.VectorAdd (move, oldframe.translate, move);

		for (i=0 ; i<3 ; i++)
		{
			move[i] = backlerp*move[i] + frontlerp*frame.translate[i];
		}

		for (i=0 ; i<3 ; i++)
		{
			frontv[i] = frontlerp*frame.scale[i];
			backv[i] = backlerp*oldframe.scale[i];
		}

		if ( gl_vertex_arrays.value != 0.0f )
		{
			GL_LerpVerts( paliashdr.num_xyz, v, ov, verts, vertexArrayBuf, move, frontv, backv );

			gl.glEnableClientState( GL.GL_VERTEX_ARRAY );
			gl.glVertexPointer( 3, GL.GL_FLOAT, 16, vertexArrayBuf );	// padded for SIMD

			// PMM - added double damage shell
			if ( (currententity.flags & ( Defines.RF_SHELL_RED | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE | Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0)
			{
				gl.glColor4f( shadelight[0], shadelight[1], shadelight[2], alpha );
			}
			else
			{
				gl.glEnableClientState( GL.GL_COLOR_ARRAY );
				gl.glColorPointer( 3, GL.GL_FLOAT, 0, colorArrayBuf );

				//
				// pre light everything
				//
				colorArrayBuf.position(0);
				for ( i = 0; i < paliashdr.num_xyz; i++ )
				{
					l = shadedots[verts[i].lightnormalindex];
					colorArrayBuf.put(l * shadelight[0]).put(l * shadelight[1]).put(l * shadelight[2]);
				}
			}

			if ( qglLockArraysEXT )
				gl.glLockArraysEXT( 0, paliashdr.num_xyz );

			while (true)
			{
				// get the vertex count and primitive type
				count = order[orderIndex++];
				if (count == 0)
					break;		// done
				if (count < 0)
				{
					count = -count;
					gl.glBegin (GL.GL_TRIANGLE_FAN);
				}
				else
				{
					gl.glBegin (GL.GL_TRIANGLE_STRIP);
				}

				// PMM - added double damage shell
				if ( (currententity.flags & ( Defines.RF_SHELL_RED | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE | Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0 )
				{
					do
					{
						index_xyz = order[orderIndex + 2];
						orderIndex += 3;
						/*
						vertexArrayBuf.position(4 * index_xyz);
						vertexArrayBuf.get(tmpVec);
						gl.glVertex3fv( tmpVec );
						*/
						gl.glArrayElement( index_xyz );

					} while (--count != 0);
				}
				else
				{
					do
					{
						// texture coordinates come from the draw list
						gl.glTexCoord2f (Float.intBitsToFloat(order[orderIndex + 0]), Float.intBitsToFloat(order[orderIndex + 1]));

						index_xyz = order[orderIndex + 2];
						orderIndex += 3;

						// normals and vertexes come from the frame list
						gl.glArrayElement( index_xyz );

					} while (--count != 0);
				}
				gl.glEnd ();
			}

			if ( qglUnlockArraysEXT )
				gl.glUnlockArraysEXT();
		}
		else
		{
			GL_LerpVerts( paliashdr.num_xyz, v, ov, verts, s_lerped, move, frontv, backv );

			while (true)
			{
				// get the vertex count and primitive type
				count = order[orderIndex++];
				if (count == 0)
					break;		// done
				if (count < 0)
				{
					count = -count;
					gl.glBegin (GL.GL_TRIANGLE_FAN);
				}
				else
				{
					gl.glBegin (GL.GL_TRIANGLE_STRIP);
				}

				if ( (currententity.flags & ( Defines.RF_SHELL_RED | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE )) != 0 )
				{
					do
					{
						index_xyz = order[orderIndex + 2];
						orderIndex += 3;

						gl.glColor4f( shadelight[0], shadelight[1], shadelight[2], alpha);
						gl.glVertex3fv (s_lerped[index_xyz]);

					} while (--count != 0);
				}
				else
				{
					do
					{
						// texture coordinates come from the draw list
						// gl.glTexCoord2f (((float *)order)[0], ((float *)order)[1]);
						
						gl.glTexCoord2f (Float.intBitsToFloat(order[orderIndex + 0]), Float.intBitsToFloat(order[orderIndex + 1]));
						index_xyz = order[orderIndex + 2];
						orderIndex += 3;

						// normals and vertexes come from the frame list
						l = shadedots[verts[index_xyz].lightnormalindex];
					
						gl.glColor4f (l* shadelight[0], l*shadelight[1], l*shadelight[2], alpha);
						gl.glVertex3fv (s_lerped[index_xyz]);
					} while (--count != 0);
				}
				gl.glEnd ();
			}
		}

		// PMM - added double damage shell
		if ( (currententity.flags & ( Defines.RF_SHELL_RED | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE | Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0 )
			gl.glEnable( GL.GL_TEXTURE_2D );
	}


	/*
	=============
	GL_DrawAliasShadow
	=============
	*/
	void GL_DrawAliasShadow(qfiles.dmdl_t paliashdr, int posenum)
	{
		qfiles.dtrivertx_t[] verts;
		int[] order;
		float[] point = {0, 0, 0};
		float	height, lheight;
		int count;
		qfiles.daliasframe_t frame;

		lheight = currententity.origin[2] - lightspot[2];

		frame = paliashdr.aliasFrames[currententity.frame];

		verts = frame.verts;

		height = 0;

		order = paliashdr.glCmds;

		height = -lheight + 1.0f;
		
		int orderIndex = 0;

		while (true)
		{
			// get the vertex count and primitive type
			count = order[orderIndex++];
			if (count == 0)
				break;		// done
			if (count < 0)
			{
				count = -count;
				gl.glBegin (GL.GL_TRIANGLE_FAN);
			}
			else
				gl.glBegin (GL.GL_TRIANGLE_STRIP);

			do
			{
				// normals and vertexes come from the frame list
				/*
				point[0] = verts[order[2]].v[0] * frame.scale[0] + frame.translate[0];
				point[1] = verts[order[2]].v[1] * frame.scale[1] + frame.translate[1];
				point[2] = verts[order[2]].v[2] * frame.scale[2] + frame.translate[2];
				*/

				if ( gl_vertex_arrays.value != 0.0f )
				{
					vertexArrayBuf.position(order[orderIndex + 2] * 4);
					vertexArrayBuf.get(point);
				}
				else
				{
					System.arraycopy(s_lerped[order[orderIndex + 2]], 0, point, 0, 3);
				}
					
				point[0] -= shadevector[0]*(point[2]+lheight);
				point[1] -= shadevector[1]*(point[2]+lheight);
				point[2] = height;
				gl.glVertex3fv (point);

				orderIndex += 3;

			} while (--count != 0);

			gl.glEnd ();
		}	
	}


	/*
	** R_CullAliasModel
	*/
	boolean R_CullAliasModel( float[][] bbox, entity_t e )
	{
		int i;
		float[] mins = {0, 0, 0};
		float[] maxs = {0, 0, 0};
		
		qfiles.dmdl_t paliashdr;

		float[][] vectors = {
			{0, 0, 0},
			{0, 0, 0},
			{0, 0, 0}
		};

		float[] thismins = {0, 0, 0};
		float[] oldmins = {0, 0, 0};
		float[] thismaxs = {0, 0, 0};
		float[] oldmaxs = {0, 0, 0};
		qfiles.daliasframe_t pframe, poldframe;
		float[] angles = {0, 0, 0};

		paliashdr = (qfiles.dmdl_t)currentmodel.extradata;

		if ( ( e.frame >= paliashdr.num_frames ) || ( e.frame < 0 ) )
		{
			ri.Con_Printf (Defines.PRINT_ALL, "R_CullAliasModel " + currentmodel.name +": no such frame " + e.frame + '\n');
			e.frame = 0;
		}
		if ( ( e.oldframe >= paliashdr.num_frames ) || ( e.oldframe < 0 ) )
		{
			ri.Con_Printf (Defines.PRINT_ALL, "R_CullAliasModel " + currentmodel.name + ": no such oldframe " + e.oldframe + '\n');
			e.oldframe = 0;
		}

		pframe = paliashdr.aliasFrames[e.frame];
		poldframe = paliashdr.aliasFrames[e.oldframe];

		/*
		** compute axially aligned mins and maxs
		*/
		if ( pframe == poldframe )
		{
			for ( i = 0; i < 3; i++ )
			{
				mins[i] = pframe.translate[i];
				maxs[i] = mins[i] + pframe.scale[i]*255;
			}
		}
		else
		{
			for ( i = 0; i < 3; i++ )
			{
				thismins[i] = pframe.translate[i];
				thismaxs[i] = thismins[i] + pframe.scale[i]*255;

				oldmins[i]  = poldframe.translate[i];
				oldmaxs[i]  = oldmins[i] + poldframe.scale[i]*255;

				if ( thismins[i] < oldmins[i] )
					mins[i] = thismins[i];
				else
					mins[i] = oldmins[i];

				if ( thismaxs[i] > oldmaxs[i] )
					maxs[i] = thismaxs[i];
				else
					maxs[i] = oldmaxs[i];
			}
		}

		/*
		** compute a full bounding box
		*/
		for ( i = 0; i < 8; i++ )
		{
			float[] tmp = {0, 0, 0};

			if ( (i & 1) != 0 )
				tmp[0] = mins[0];
			else
				tmp[0] = maxs[0];

			if ( (i & 2) != 0)
				tmp[1] = mins[1];
			else
				tmp[1] = maxs[1];

			if ( (i & 4) != 0)
				tmp[2] = mins[2];
			else
				tmp[2] = maxs[2];

			Math3D.VectorCopy( tmp, bbox[i] );
		}

		/*
		** rotate the bounding box
		*/
		Math3D.VectorCopy( e.angles, angles );
		angles[YAW] = -angles[YAW];
		Math3D.AngleVectors( angles, vectors[0], vectors[1], vectors[2] );

		for ( i = 0; i < 8; i++ )
		{
			float[] tmp = {0, 0, 0};

			Math3D.VectorCopy( bbox[i], tmp );

			bbox[i][0] = Math3D.DotProduct( vectors[0], tmp );
			bbox[i][1] = -Math3D.DotProduct( vectors[1], tmp );
			bbox[i][2] = Math3D.DotProduct( vectors[2], tmp );

			Math3D.VectorAdd( e.origin, bbox[i], bbox[i] );
		}

		{
			int p, f;
			int aggregatemask = ~0; // 0xFFFFFFFF
			
			for ( p = 0; p < 8; p++ )
			{
				int mask = 0;

				for ( f = 0; f < 4; f++ )
				{
					float dp = Math3D.DotProduct( frustum[f].normal, bbox[p] );

					if ( ( dp - frustum[f].dist ) < 0 )
					{
						mask |= ( 1 << f );
					}
				}

				aggregatemask &= mask;
			}

			if ( aggregatemask != 0 )
			{
				return true;
			}

			return false;
		}
	}

	/*
	=================
	R_DrawAliasModel

	=================
	*/
	void R_DrawAliasModel(entity_t e)
	{
		int i;
		qfiles.dmdl_t paliashdr;
		float		an;

		// bounding box
		float[][] bbox = {
			{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
			{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}
		};
		image_t		skin;

		if ( ( e.flags & Defines.RF_WEAPONMODEL ) == 0)
		{
			if ( R_CullAliasModel( bbox, e ) )
				return;
		}

		if ( (e.flags & Defines.RF_WEAPONMODEL) != 0 )
		{
			if ( r_lefthand.value == 2.0f )
				return;
		}

		paliashdr = (qfiles.dmdl_t)currentmodel.extradata;

		//
		// get lighting information
		//
		// PMM - rewrote, reordered to handle new shells & mixing
		// PMM - 3.20 code .. replaced with original way of doing it to keep mod authors happy
		//
		if ( (currententity.flags & ( Defines.RF_SHELL_HALF_DAM | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_RED | Defines.RF_SHELL_BLUE | Defines.RF_SHELL_DOUBLE )) != 0 )
		{
			Math3D.VectorClear(shadelight);
			if ((currententity.flags & Defines.RF_SHELL_HALF_DAM) != 0)
			{
					shadelight[0] = 0.56f;
					shadelight[1] = 0.59f;
					shadelight[2] = 0.45f;
			}
			if ( (currententity.flags & Defines.RF_SHELL_DOUBLE) != 0 )
			{
				shadelight[0] = 0.9f;
				shadelight[1] = 0.7f;
			}
			if ( (currententity.flags & Defines.RF_SHELL_RED) != 0 )
				shadelight[0] = 1.0f;
			if ( (currententity.flags & Defines.RF_SHELL_GREEN) != 0 )
				shadelight[1] = 1.0f;
			if ( (currententity.flags & Defines.RF_SHELL_BLUE) != 0 )
				shadelight[2] = 1.0f;
		}

		else if ( (currententity.flags & Defines.RF_FULLBRIGHT) != 0 )
		{
			for (i=0 ; i<3 ; i++)
				shadelight[i] = 1.0f;
		}
		else
		{
			R_LightPoint (currententity.origin, shadelight);

			// player lighting hack for communication back to server
			// big hack!
			if ( (currententity.flags & Defines.RF_WEAPONMODEL) != 0 )
			{
				// pick the greatest component, which should be the same
				// as the mono value returned by software
				if (shadelight[0] > shadelight[1])
				{
					if (shadelight[0] > shadelight[2])
						r_lightlevel.value = 150*shadelight[0];
					else
						r_lightlevel.value = 150*shadelight[2];
				}
				else
				{
					if (shadelight[1] > shadelight[2])
						r_lightlevel.value = 150*shadelight[1];
					else
						r_lightlevel.value = 150*shadelight[2];
				}
			}
		
			if ( gl_monolightmap.string.charAt(0) != '0' )
			{
				float s = shadelight[0];

				if ( s < shadelight[1] )
					s = shadelight[1];
				if ( s < shadelight[2] )
					s = shadelight[2];

				shadelight[0] = s;
				shadelight[1] = s;
				shadelight[2] = s;
			}
		}

		if ( (currententity.flags & Defines.RF_MINLIGHT) != 0 )
		{
			for (i=0 ; i<3 ; i++)
				if (shadelight[i] > 0.1f)
					break;
			if (i == 3)
			{
				shadelight[0] = 0.1f;
				shadelight[1] = 0.1f;
				shadelight[2] = 0.1f;
			}
		}

		if ( (currententity.flags & Defines.RF_GLOW) != 0 )
		{	// bonus items will pulse with time
			float	scale;
			float	min;

			scale = (float)(0.1f * Math.sin(r_newrefdef.time*7));
			for (i=0 ; i<3 ; i++)
			{
				min = shadelight[i] * 0.8f;
				shadelight[i] += scale;
				if (shadelight[i] < min)
					shadelight[i] = min;
			}
		}

		// =================
		// PGM	ir goggles color override
		if ( (r_newrefdef.rdflags & Defines.RDF_IRGOGGLES) != 0 && (currententity.flags & Defines.RF_IR_VISIBLE) != 0)
		{
			shadelight[0] = 1.0f;
			shadelight[1] = 0.0f;
			shadelight[2] = 0.0f;
		}
		// PGM	
		// =================

		shadedots = r_avertexnormal_dots[((int)(currententity.angles[1] * (SHADEDOT_QUANT / 360.0))) & (SHADEDOT_QUANT - 1)];
	
		an = (float)(currententity.angles[1]/180*Math.PI);
		shadevector[0] = (float)Math.cos(-an);
		shadevector[1] = (float)Math.sin(-an);
		shadevector[2] = 1;
		Math3D.VectorNormalize(shadevector);

		//
		// locate the proper data
		//

		c_alias_polys += paliashdr.num_tris;

		//
		// draw all the triangles
		//
		if ( (currententity.flags & Defines.RF_DEPTHHACK) != 0) // hack the depth range to prevent view model from poking into walls
			gl.glDepthRange(gldepthmin, gldepthmin + 0.3*(gldepthmax-gldepthmin));

		if ( (currententity.flags & Defines.RF_WEAPONMODEL) != 0 && (r_lefthand.value == 1.0f) )
		{
			gl.glMatrixMode( GL.GL_PROJECTION );
			gl.glPushMatrix();
			gl.glLoadIdentity();
			gl.glScalef( -1, 1, 1 );
			MYgluPerspective( r_newrefdef.fov_y, ( float ) r_newrefdef.width / r_newrefdef.height,  4,  4096);
			gl.glMatrixMode( GL.GL_MODELVIEW );

			gl.glCullFace( GL.GL_BACK );
		}

		gl.glPushMatrix ();
		e.angles[PITCH] = -e.angles[PITCH];	// sigh.
		R_RotateForEntity (e);
		e.angles[PITCH] = -e.angles[PITCH];	// sigh.

		// select skin
		if (currententity.skin != null)
			skin = currententity.skin;	// custom player skin
		else
		{
			if (currententity.skinnum >= qfiles.MAX_MD2SKINS)
				skin = currentmodel.skins[0];
			else
			{
				skin = currentmodel.skins[currententity.skinnum];
				if (skin == null)
					skin = currentmodel.skins[0];
			}
		}
		if (skin == null)
			skin = r_notexture;	// fallback...
		GL_Bind(skin.texnum);

		// draw it

		gl.glShadeModel (GL.GL_SMOOTH);

		GL_TexEnv( GL.GL_MODULATE );
		if ( (currententity.flags & Defines.RF_TRANSLUCENT) != 0 )
		{
			gl.glEnable (GL.GL_BLEND);
		}


		if ( (currententity.frame >= paliashdr.num_frames) 
			|| (currententity.frame < 0) )
		{
			ri.Con_Printf (Defines.PRINT_ALL, "R_DrawAliasModel " + currentmodel.name +": no such frame " + currententity.frame + '\n');
			currententity.frame = 0;
			currententity.oldframe = 0;
		}

		if ( (currententity.oldframe >= paliashdr.num_frames)
			|| (currententity.oldframe < 0))
		{
			ri.Con_Printf (Defines.PRINT_ALL, "R_DrawAliasModel " + currentmodel.name +": no such oldframe " + currententity.oldframe + '\n');
			currententity.frame = 0;
			currententity.oldframe = 0;
		}

		if ( r_lerpmodels.value == 0.0f)
			currententity.backlerp = 0;
		GL_DrawAliasFrameLerp (paliashdr, currententity.backlerp);

		GL_TexEnv( GL.GL_REPLACE );
		gl.glShadeModel (GL.GL_FLAT);

		gl.glPopMatrix ();

		if ( ( currententity.flags & Defines.RF_WEAPONMODEL ) != 0 && ( r_lefthand.value == 1.0F ) )
		{
			gl.glMatrixMode( GL.GL_PROJECTION );
			gl.glPopMatrix();
			gl.glMatrixMode( GL.GL_MODELVIEW );
			gl.glCullFace( GL.GL_FRONT );
		}

		if ( (currententity.flags & Defines.RF_TRANSLUCENT) != 0 )
		{
			gl.glDisable (GL.GL_BLEND);
		}

		if ( (currententity.flags & Defines.RF_DEPTHHACK) != 0)
			gl.glDepthRange (gldepthmin, gldepthmax);

		if ( gl_shadows.value != 0.0f && (currententity.flags & (Defines.RF_TRANSLUCENT | Defines.RF_WEAPONMODEL)) == 0)
		{
			gl.glPushMatrix ();
			R_RotateForEntity (e);
			gl.glDisable (GL.GL_TEXTURE_2D);
			gl.glEnable (GL.GL_BLEND);
			gl.glColor4f (0,0,0,0.5f);
			GL_DrawAliasShadow (paliashdr, currententity.frame );
			gl.glEnable (GL.GL_TEXTURE_2D);
			gl.glDisable (GL.GL_BLEND);
			gl.glPopMatrix ();
		}
		gl.glColor4f (1,1,1,1);
	}

}
